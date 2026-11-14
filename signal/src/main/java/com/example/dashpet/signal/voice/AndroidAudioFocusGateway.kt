package com.example.dashpet.signal.voice

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * [AudioManager] 로 오디오 포커스를 요청하고 반납한다(AGENTS.md 7절). 변경 4가지를 [FocusChange] 로 올린다.
 * JVM 에서 검증할 수 없으므로 에뮬레이터 확인으로 대체한다(specs/004 quickstart 3절).
 */
internal class AndroidAudioFocusGateway(
    private val audioManager: AudioManager,
    private val attributes: AudioAttributes,
    private val gain: Int,
    private val log: (String) -> Unit = {},
) : AudioFocusGateway {
    private val _changes = MutableSharedFlow<FocusChange>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: Flow<FocusChange> = _changes

    @Volatile override var requestCount = 0
        private set
    @Volatile override var abandonCount = 0
        private set

    private var request: AudioFocusRequest? = null

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val mapped = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LOSS_TRANSIENT_CAN_DUCK
            else -> null
        }
        if (mapped != null) _changes.tryEmit(mapped)
    }

    override fun request(): Boolean {
        requestCount++
        val req = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(listener)
            .build()
        request = req
        val granted = try {
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Throwable) {
            log("focus request failed: ${e.javaClass.simpleName}")
            false
        }
        return granted
    }

    override fun abandon() {
        abandonCount++
        val req = request ?: return
        request = null
        try {
            audioManager.abandonAudioFocusRequest(req)
        } catch (e: Throwable) {
            log("focus abandon failed: ${e.javaClass.simpleName}")
        }
    }
}
