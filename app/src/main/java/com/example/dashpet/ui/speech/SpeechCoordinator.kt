package com.example.dashpet.ui.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.engine.speech.SpeechCatalog
import com.example.dashpet.engine.speech.SpeechCommand
import com.example.dashpet.engine.speech.SpeechConfig
import com.example.dashpet.engine.speech.SpeechDirector
import com.example.dashpet.engine.speech.SpeechTone
import com.example.dashpet.signal.voice.SpeechOutput
import com.example.dashpet.signal.voice.SpeechState
import com.example.dashpet.signal.voice.VoiceAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 엔진 이벤트와 출력을 [SpeechDirector] 에 넣고, 지시를 음성 출력과 말풍선 상태로 옮긴다(specs/004 contracts/speech.md 4절).
 * 모든 호출은 [scope] 의 한 스레드(메인)에서 한다. 말풍선은 제한 중에도 전체 문장으로 뜨고(애니메이션 없음), 음성만 제한 중에 짧은 한 문장이 된다.
 * 음성 출력은 화면에 보이는 동안에만 만들어 두고, 사라지면 해제한다(해제한 인스턴스는 다시 쓰지 않고 새로 만든다).
 * 로그에는 말하기 시작/건너뜀과 이유만 남기고 문장 본문은 남기지 않는다(FR-019, FR-020).
 */
class SpeechCoordinator(
    private val scope: CoroutineScope,
    private val events: Flow<DrivingEvent>,
    private val output: StateFlow<PetOutput>,
    private val speechFactory: (CoroutineScope) -> SpeechOutput,
    private val settings: VoiceSettings,
    private val config: SpeechConfig = SpeechConfig(),
    private val clock: () -> Long,
    private val log: (String) -> Unit = {},
    tone: SpeechTone = SpeechTone.STURDY,
) {
    private var director = SpeechDirector(config, SpeechCatalog.forTone(tone), log)
    private val _bubble = MutableStateFlow<BubbleState?>(null)
    val bubble: StateFlow<BubbleState?> = _bubble.asStateFlow()
    private val _voiceEnabled = MutableStateFlow(settings.voiceEnabled)
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private var speech: SpeechOutput? = null
    private var statesJob: Job? = null
    private var bubbleJob: Job? = null
    private var foreground = false
    private var started = false

    /** 이벤트와 출력 구독을 건다. 구독 전의 이벤트는 받지 않으므로 세션 시작과 함께 부른다. 두 번 불러도 한 번만 건다. */
    fun start() {
        if (started) return
        started = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                if (!foreground) {
                    log("skip event=${event.type} reason=background")
                    return@collect
                }
                apply(director.onEvent(event, output.value.tier, clock()))
            }
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            output.collect { apply(director.onOutput(it.tier, clock())) }
        }
    }

    /**
     * 확인용(디버그 전용): 엔진을 거치지 않고 이벤트 하나를 규칙에 넣는다. 말풍선과 제한 중 규칙은 실제 이벤트와 같다
     * (현재 `output.tier` 를 그대로 쓴다). 화면에 보이지 않으면 아무것도 하지 않는다.
     */
    fun debugSpeak(type: EventType) {
        if (!foreground) return
        director.clearCooldown(type) // 확인용이므로 쿨다운(소리 5분)에 막히지 않는다
        val now = clock()
        apply(director.onEvent(DrivingEvent(type, now), output.value.tier, now))
    }

    /** 성격(말투)을 바꾼다. 떠 있는 말풍선과 대기 중인 음성을 정리하고 새 문장 세트로 시작한다. */
    fun setTone(tone: SpeechTone) {
        apply(director.reset())
        clearBubble()
        director = SpeechDirector(config, SpeechCatalog.forTone(tone), log)
    }

    /**
     * 이름이 들어가는 한 마디(온보딩 뒤 첫 인사). 모션 등급이 `FULL`(제한 없음)이고 화면에 보일 때만 말한다.
     * 말풍선과 같은 문장을 음성으로도 읽는다(음성이 꺼져 있으면 말풍선만). 문장은 호출한 쪽이 고정 규칙으로 만든다.
     * 이벤트 규칙(쿨다운, 대기)과 섞이지 않도록 음수 번호를 쓴다.
     */
    fun say(text: String): Boolean {
        if (!foreground || output.value.tier != MotionTier.FULL) return false
        _bubble.value = BubbleState(text, EventType.TRIP_START)
        bubbleJob?.cancel()
        bubbleJob = scope.launch {
            delay(config.bubbleDurationMs)
            _bubble.value = null
        }
        val out = speech
        if (settings.voiceEnabled && out != null) {
            log("say bubble=true")
            val id = nextSayId--
            // 방금 화면에 돌아왔다면 음성 엔진이 아직 준비 중일 수 있다(준비 전에 부르면 ENGINE_ERROR). 잠시 기다렸다 말하고, 준비되지 않으면 말풍선만 남긴다.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (out.availability.value != VoiceAvailability.READY) {
                    withTimeoutOrNull(SAY_WAIT_MS) { out.availability.first { it == VoiceAvailability.READY } } ?: return@launch
                }
                if (speech === out) out.speak(id, text)
            }
        }
        return true
    }

    private var nextSayId = -1L

    private companion object {
        const val SAY_WAIT_MS = 3_000L
    }

    /** 화면에 보이기 시작. 음성 출력을 새로 만든다(이미 있으면 아무것도 하지 않는다). */
    fun onForeground() {
        if (foreground) return
        foreground = true
        val out = speechFactory(scope)
        speech = out
        statesJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            out.states.collect { state ->
                when (state) {
                    is SpeechState.Started -> Unit
                    is SpeechState.Finished, is SpeechState.Stopped -> apply(director.onSpeechEnded(state.id, clock()))
                    is SpeechState.Failed -> {
                        log("speech failed reason=${state.reason}")
                        apply(director.onSpeechEnded(state.id, clock()))
                    }
                }
            }
        }
        log("voice availability=${out.availability.value}")
    }

    /** 화면에서 사라짐. 재생 중지, 포커스 반납, SDK 해제, 말풍선 지움. 돌아와도 이어 말하지 않는다. */
    fun onBackground() {
        if (!foreground) return
        apply(director.reset())
        foreground = false
        statesJob?.cancel()
        statesJob = null
        speech?.stop()
        speech?.release()
        speech = null
        clearBubble()
    }

    /** 새 세션(모드 전환). 말풍선과 대기 중인 음성, 쿨다운이 남지 않는다. */
    fun reset() {
        apply(director.reset())
        clearBubble()
    }

    /**
     * 음성 켜기/끄기. 모션 등급이 `FULL`(제한 없음)일 때만 바꾸고 저장한다. 아니면 무시하고 false 를 돌려준다(FR-016).
     * 끄면 재생 중인 음성을 멈춘다.
     */
    fun setVoiceEnabled(on: Boolean): Boolean {
        if (output.value.tier != MotionTier.FULL) return false
        settings.voiceEnabled = on
        _voiceEnabled.value = on
        if (!on) speech?.stop()
        return true
    }

    private fun apply(commands: List<SpeechCommand>) {
        commands.forEach { cmd ->
            when (cmd) {
                is SpeechCommand.Speak -> onSpeak(cmd)
                is SpeechCommand.StopSpeaking -> speech?.stop()
                SpeechCommand.HideBubble -> clearBubble()
            }
        }
    }

    private fun onSpeak(cmd: SpeechCommand.Speak) {
        val type = cmd.utterance.eventType
        if (cmd.showBubble) { // 제한 중에도 전체 문장을 그대로 보인다(애니메이션 없음)
            _bubble.value = BubbleState(cmd.utterance.text, type)
            bubbleJob?.cancel()
            bubbleJob = scope.launch {
                delay(config.bubbleDurationMs)
                director.onBubbleExpired()
                _bubble.value = null
            }
        } else {
            clearBubble()
        }
        val out = speech
        if (cmd.voice && settings.voiceEnabled && out != null) {
            log("speak event=$type bubble=${cmd.showBubble} tier=${output.value.tier}")
            out.speak(cmd.id, cmd.spokenText)
        } else {
            val reason = when {
                !cmd.voice -> "voice-cooldown"
                out == null -> "no-output"
                else -> "voice-off"
            }
            log("silent event=$type reason=$reason")
            apply(director.onSpeechEnded(cmd.id, clock())) // 소리가 없으니 바로 끝난 것으로 본다
        }
    }

    private fun clearBubble() {
        bubbleJob?.cancel()
        bubbleJob = null
        _bubble.value = null
    }
}
