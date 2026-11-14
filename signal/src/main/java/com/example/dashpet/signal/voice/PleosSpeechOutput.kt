package com.example.dashpet.signal.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Pleos TTS SDK(`ON_DEVICE`) 어댑터. **이 파일만 SDK 를 import 한다**(헌장 II).
 * `ON_DEVICE` 라서 Client ID/Secret 이 필요 없다. 수명주기는 생성 → `initialize` → 리스너 등록 → `speak` →
 * 리스너 제거 → `release` 이고 해제한 인스턴스는 다시 쓰지 않는다(AGENTS.md 3절).
 * 필요한 권한(`pleos.car.permission.TTS_SERVICE`)이 없거나 서비스가 없으면 [VoiceAvailability.UNAVAILABLE] 로 떨어지고
 * 앱은 음성 없이 동작한다. SDK 가 던지는 예외는 밖으로 내보내지 않는다.
 * 콜백이 오는 스레드는 문서에 없다 `[확인 필요]`. 모두 버퍼 있는 흐름으로 넘기고 처리는 [SpeechPlayer] 가 한다.
 */
internal class PleosSynthesizer(
    context: Context,
    attributes: AudioAttributes,
    private val locale: String,
    private val log: (String) -> Unit,
) : Synthesizer {
    private val _availability = MutableStateFlow(VoiceAvailability.UNKNOWN)
    override val availability: StateFlow<VoiceAvailability> = _availability

    private val _events = MutableSharedFlow<SynthEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val events: Flow<SynthEvent> = _events

    @Volatile private var tts: TextToSpeech? = null
    private var released = false

    private val listener = object : EventListener {
        override fun onStart() { _events.tryEmit(SynthEvent.Started) }
        override fun onReady() = Unit
        override fun onDone() { _events.tryEmit(SynthEvent.Done) }
        override fun onStop() { _events.tryEmit(SynthEvent.Stopped) }
        override fun onUpdatedRms(rms: Double) = Unit // 자주 오는 값이라 쓰지 않는다
        override fun onError(errorType: ErrorType) {
            log("tts error=$errorType")
            when (errorType) {
                ErrorType.PERMISSION_DENIED, ErrorType.NOT_SUPPORTED_API -> _availability.value = VoiceAvailability.UNAVAILABLE
                else -> Unit // 나머지는 이번 말만 실패로 본다
            }
            _events.tryEmit(SynthEvent.Error)
        }
    }

    init {
        try {
            val engine = TextToSpeech(context, Mode.ON_DEVICE)
            tts = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    try {
                        if (released) return
                        engine.addEventListener(listener)
                        engine.setLocale(Locale.forLanguageTag(locale))
                        engine.setAudioAttributes(attributes)
                        _availability.value = VoiceAvailability.READY
                        log("voice=READY")
                    } catch (e: Throwable) {
                        markUnavailable("setup failed: ${e.javaClass.simpleName}")
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    markUnavailable("initialize failed: $errorType")
                }
            })
        } catch (e: Throwable) {
            // 권한 없음, 서비스 없음, 클래스 없음 등. 앱은 음성 없이 동작한다.
            markUnavailable("init exception: ${e.javaClass.simpleName}")
        }
    }

    private fun markUnavailable(reason: String) {
        _availability.value = VoiceAvailability.UNAVAILABLE
        log("voice=UNAVAILABLE reason=$reason")
    }

    override fun speak(text: String) {
        val engine = tts
        if (engine == null) {
            _events.tryEmit(SynthEvent.Error)
            return
        }
        try {
            engine.speak(text, true) // 한 문장이므로 isFinal = true
        } catch (e: Throwable) {
            log("speak failed: ${e.javaClass.simpleName}")
            _events.tryEmit(SynthEvent.Error)
        }
    }

    override fun stop() {
        try { tts?.stop() } catch (e: Throwable) { log("stop failed: ${e.javaClass.simpleName}") }
    }

    override fun release() {
        if (released) return
        released = true
        val engine = tts ?: return
        tts = null
        // 해제 순서: 재생 중지 → 리스너 제거 → release
        try { engine.stop() } catch (_: Throwable) { }
        try { engine.removeEventListener(listener) } catch (_: Throwable) { }
        try { engine.release() } catch (e: Throwable) { log("release failed: ${e.javaClass.simpleName}") }
        _availability.value = VoiceAvailability.UNAVAILABLE
    }
}

/**
 * 실제 음성 출력을 만든다(Pleos TTS + AudioManager 포커스 + 무음 트랙). 호출한 쪽이 `release()` 를 부른다.
 * [scope] 는 이 인스턴스 전용으로 만들어 해제할 때 함께 취소한다.
 */
fun createPleosSpeechOutput(
    context: Context,
    scope: CoroutineScope,
    config: VoiceConfig = VoiceConfig(),
    log: (String) -> Unit = {},
): SpeechOutput {
    val app = context.applicationContext
    val attributes = AudioAttributes.Builder()
        .setUsage(config.audioUsage)
        .setContentType(config.contentType)
        .build()
    val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return SpeechPlayer(
        synth = PleosSynthesizer(app, attributes, config.locale, log),
        focus = AndroidAudioFocusGateway(audioManager, attributes, config.focusGain, log),
        silence = SilenceTrackPlayer(attributes),
        scope = scope,
        preSilenceMs = config.preSilenceMs,
        watchdogMs = config.speakWatchdogMs,
        log = log,
    )
}
