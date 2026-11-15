package com.example.dashpet.ui.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.engine.speech.SpeechCommand
import com.example.dashpet.engine.speech.SpeechConfig
import com.example.dashpet.engine.speech.SpeechDirector
import com.example.dashpet.signal.voice.SpeechOutput
import com.example.dashpet.signal.voice.SpeechState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 엔진 이벤트와 출력을 [SpeechDirector] 에 넣고, 지시를 음성 출력과 말풍선 상태로 옮긴다(specs/004 contracts/speech.md 4절).
 * 모든 호출은 [scope] 의 한 스레드(메인)에서 한다. 말풍선 글자는 모션 등급이 `FULL` 일 때만 생긴다.
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
) {
    private val director = SpeechDirector(config)
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
        val now = clock()
        apply(director.onEvent(DrivingEvent(type, now), output.value.tier, now))
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
        if (cmd.showBubble) {
            _bubble.value = BubbleState(cmd.utterance.text, type)
            bubbleJob?.cancel()
            bubbleJob = scope.launch {
                delay(config.bubbleDurationMs)
                director.onBubbleExpired()
                _bubble.value = null
            }
        } else {
            clearBubble() // 제한 중에는 글자를 두지 않는다
        }
        val out = speech
        if (settings.voiceEnabled && out != null) {
            log("speak event=$type bubble=${cmd.showBubble} tier=${output.value.tier}")
            out.speak(cmd.id, cmd.spokenText)
        } else {
            log("silent event=$type reason=${if (out == null) "no-output" else "voice-off"}")
            apply(director.onSpeechEnded(cmd.id, clock())) // 소리가 없으니 바로 끝난 것으로 본다
        }
    }

    private fun clearBubble() {
        bubbleJob?.cancel()
        bubbleJob = null
        _bubble.value = null
    }
}
