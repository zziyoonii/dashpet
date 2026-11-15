package com.example.dashpet.signal.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 합성기가 올리는 사건. 어느 스레드에서 와도 된다. */
sealed interface SynthEvent {
    data object Started : SynthEvent
    data object Done : SynthEvent
    data object Stopped : SynthEvent
    data object Error : SynthEvent
}

/** 음성 합성기 한 겹(실제는 Pleos TTS SDK). */
interface Synthesizer {
    val availability: StateFlow<VoiceAvailability>
    val events: Flow<SynthEvent>
    fun speak(text: String)
    fun stop()
    fun release()
}

/** 오디오 데이터 레벨 무음(AGENTS.md 7절). 재생이 끝날 때까지 중단된다. */
interface SilencePlayer {
    suspend fun play(ms: Int)
}

/**
 * 포커스 요청 → 선행 무음 → 합성 → 포커스 반납의 순서와, 포커스 변경 4가지, 오류, 감시 시간을 맡는다
 * (specs/004 contracts/speech.md 3절). 모든 입력을 한 채널로 모아 한 코루틴에서 처리하므로 콜백 스레드와
 * 호출 스레드가 달라도 상태가 꼬이지 않는다. 어떤 경로로 끝나도 포커스는 정확히 한 번 반납한다.
 * [scope] 는 부모 범위이고 플레이어는 그 아래 자기 작업을 두며 `release()` 때 모두 취소한다.
 */
class SpeechPlayer(
    private val synth: Synthesizer,
    private val focus: AudioFocusGateway,
    private val silence: SilencePlayer,
    scope: CoroutineScope,
    private val preSilenceMs: Int = 180,
    private val watchdogMs: Long = 8_000L,
    private val log: (String) -> Unit = {},
) : SpeechOutput {
    // 상태 보고는 잃어버리면 안 된다(규칙이 말하기 끝을 기다린다). 한 곳에서만 구독한다.
    private val _states = Channel<SpeechState>(Channel.UNLIMITED)
    override val states: Flow<SpeechState> = _states.receiveAsFlow()
    override val availability: StateFlow<VoiceAvailability> get() = synth.availability

    private sealed interface Msg {
        class Speak(val id: Long, val text: String) : Msg
        data object Stop : Msg
        class Focus(val change: FocusChange) : Msg
        class Synth(val event: SynthEvent) : Msg
        class SilenceDone(val id: Long) : Msg
        class Watchdog(val id: Long) : Msg
        data object Release : Msg
    }

    private enum class Phase { SILENCE, SPEAKING }

    private class Active(val id: Long, val text: String, var phase: Phase, val jobs: MutableList<Job> = mutableListOf())

    private val inbox = Channel<Msg>(Channel.UNLIMITED)
    private var current: Active? = null
    private var released = false

    /** 이 플레이어만의 작업 범위. [release] 때 함께 취소해 호출한 쪽의 범위에 코루틴이 남지 않는다. */
    private val job = SupervisorJob(scope.coroutineContext[Job])
    private val workScope = CoroutineScope(scope.coroutineContext + job)

    init {
        workScope.launch { focus.changes.collect { inbox.trySend(Msg.Focus(it)) } }
        workScope.launch { synth.events.collect { inbox.trySend(Msg.Synth(it)) } }
        workScope.launch {
            for (msg in inbox) {
                try {
                    handle(msg)
                } catch (e: Throwable) {
                    // 합성기나 포커스 쪽 예외가 밖으로 새지 않는다(FR-015). 진행 중인 말은 실패로 끝낸다.
                    log("player error: ${e.javaClass.simpleName}")
                    current?.let { finish(SpeechState.Failed(it.id, FailReason.ENGINE_ERROR)) }
                }
                if (released) break
            }
        }
    }

    override fun speak(id: Long, text: String) {
        inbox.trySend(Msg.Speak(id, text))
    }

    override fun stop() {
        inbox.trySend(Msg.Stop)
    }

    override fun release() {
        inbox.trySend(Msg.Release)
    }

    private fun handle(msg: Msg) {
        when (msg) {
            is Msg.Speak -> onSpeak(msg)
            Msg.Stop -> current?.let { halt(it, SpeechState.Stopped(it.id)) }
            is Msg.Focus -> when (msg.change) {
                FocusChange.GAIN -> Unit // 재생 중이 아니면 아무것도 하지 않는다. 자동 재개 없음
                FocusChange.LOSS, FocusChange.LOSS_TRANSIENT, FocusChange.LOSS_TRANSIENT_CAN_DUCK -> {
                    log("focus change=${msg.change}")
                    current?.let { halt(it, SpeechState.Stopped(it.id)) }
                }
            }
            is Msg.Synth -> onSynth(msg.event)
            is Msg.SilenceDone -> {
                val a = current
                if (a != null && a.id == msg.id && a.phase == Phase.SILENCE) {
                    a.phase = Phase.SPEAKING
                    synth.speak(a.text)
                }
            }
            is Msg.Watchdog -> current?.takeIf { it.id == msg.id }?.let {
                log("watchdog timeout")
                halt(it, SpeechState.Failed(it.id, FailReason.TIMEOUT))
            }
            Msg.Release -> {
                current?.let { halt(it, SpeechState.Stopped(it.id)) }
                released = true
                synth.release()
                inbox.close()
                job.cancel() // 처리 중인 이 코루틴도 여기서 끝난다
            }
        }
    }

    private fun onSpeak(msg: Msg.Speak) {
        if (released) return
        if (synth.availability.value != VoiceAvailability.READY) {
            _states.trySend(SpeechState.Failed(msg.id, FailReason.ENGINE_ERROR))
            return
        }
        if (current != null) {
            _states.trySend(SpeechState.Failed(msg.id, FailReason.BUSY))
            return
        }
        val granted = focus.request()
        log("focus request granted=$granted")
        if (!granted) {
            focus.abandon() // 요청과 반납을 짝으로 맞춘다
            _states.trySend(SpeechState.Failed(msg.id, FailReason.NO_FOCUS))
            return
        }
        val a = Active(msg.id, msg.text, Phase.SILENCE)
        current = a
        a.jobs += workScope.launch {
            try {
                silence.play(preSilenceMs)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 무음 재생에 실패해도 말은 계속한다(첫 음절이 잘릴 수 있을 뿐이다)
            }
            inbox.trySend(Msg.SilenceDone(msg.id))
        }
        a.jobs += workScope.launch {
            delay(watchdogMs)
            inbox.trySend(Msg.Watchdog(msg.id))
        }
    }

    private fun onSynth(event: SynthEvent) {
        val a = current ?: return
        if (a.phase != Phase.SPEAKING) return
        when (event) {
            SynthEvent.Started -> _states.trySend(SpeechState.Started(a.id))
            SynthEvent.Done -> finish(SpeechState.Finished(a.id))
            SynthEvent.Stopped -> finish(SpeechState.Stopped(a.id))
            SynthEvent.Error -> finish(SpeechState.Failed(a.id, FailReason.ENGINE_ERROR))
        }
    }

    /** 합성을 멈추고 끝낸다. */
    private fun halt(a: Active, state: SpeechState) {
        if (a.phase == Phase.SPEAKING) runCatching { synth.stop() }
        finish(state)
    }

    /** 포커스를 한 번 반납하고 결과를 올린다. [current] 가 이미 비었으면 아무것도 하지 않는다. */
    private fun finish(state: SpeechState) {
        val a = current ?: return
        if (a.id != state.id) return
        current = null
        a.jobs.forEach { it.cancel() }
        focus.abandon()
        log("focus abandon")
        _states.trySend(state)
    }
}
