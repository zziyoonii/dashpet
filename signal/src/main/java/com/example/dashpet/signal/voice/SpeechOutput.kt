package com.example.dashpet.signal.voice

import android.media.AudioAttributes
import android.media.AudioManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** 음성 출력 쪽에서 올라오는 사건. [id] 는 `speak` 에 넘긴 번호다. */
sealed interface SpeechState {
    val id: Long

    data class Started(override val id: Long) : SpeechState
    data class Finished(override val id: Long) : SpeechState

    /** 중지됨(우리가 멈췄거나 포커스를 잃어서). */
    data class Stopped(override val id: Long) : SpeechState
    data class Failed(override val id: Long, val reason: FailReason) : SpeechState
}

enum class FailReason { NO_FOCUS, ENGINE_ERROR, TIMEOUT, BUSY }

/** 한번 [UNAVAILABLE] 이 되면 그 인스턴스에서는 다시 시도하지 않는다. */
enum class VoiceAvailability { UNKNOWN, READY, UNAVAILABLE }

/** 음성 출력 한 겹. 구현은 [SpeechPlayer] 와 [NoOpSpeechOutput]. 호출은 한 스레드에서 한다. */
interface SpeechOutput {
    val states: Flow<SpeechState>
    val availability: StateFlow<VoiceAvailability>

    /** 한 번에 한 문장만. 재생 중이면 `Failed(BUSY)`. */
    fun speak(id: Long, text: String)

    /** 재생 중인 말을 멈춘다(없으면 아무것도 하지 않는다). */
    fun stop()

    /** 해제한 인스턴스는 다시 쓰지 않는다. */
    fun release()
}

/** Android 상수는 `:signal` 안에서만 쓴다. 모두 `[확인 필요]` 초안이다(specs/004 research R4, R5). */
data class VoiceConfig(
    /** `USAGE_ASSISTANT` 가 아닌 알림 용도로 둔 초안. 오디오 상호작용 매트릭스가 원문에 이미지뿐이라 확인이 필요하다. */
    val audioUsage: Int = AudioAttributes.USAGE_NOTIFICATION,
    val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH,
    val focusGain: Int = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
    val locale: String = "ko-KR",
    /** 선행 무음(ms). 값은 `SpeechConfig.preSilenceMs` 와 같게 넘긴다. */
    val preSilenceMs: Int = 180,
    val speakWatchdogMs: Long = 8_000L,
)

/** 음성을 쓸 수 없는 환경이나 테스트용. 항상 불가이며 `speak` 는 `Failed(ENGINE_ERROR)` 를 한 번 올린다. */
class NoOpSpeechOutput : SpeechOutput {
    private val _states = kotlinx.coroutines.channels.Channel<SpeechState>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    override val states: Flow<SpeechState> = _states.receiveAsFlow()
    override val availability: StateFlow<VoiceAvailability> =
        kotlinx.coroutines.flow.MutableStateFlow(VoiceAvailability.UNAVAILABLE)

    override fun speak(id: Long, text: String) {
        _states.trySend(SpeechState.Failed(id, FailReason.ENGINE_ERROR))
    }

    override fun stop() = Unit
    override fun release() = Unit
}
