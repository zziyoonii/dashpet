package com.example.dashpet.signal.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.delay

/**
 * 같은 [AudioAttributes] 로 무음 PCM 을 먼저 흘려 오디오 경로를 연다(AGENTS.md 7절: 재생 지연만으로 처리하지 않는다).
 * SDK 에 PCM 을 직접 넣을 수 없어 이 방식을 쓴다. 첫 음절 잘림을 실제로 막는지는 확인이 필요하다 `[확인 필요]`.
 */
internal class SilenceTrackPlayer(private val attributes: AudioAttributes) : SilencePlayer {
    override suspend fun play(ms: Int) {
        val sampleRate = SAMPLE_RATE
        val samples = sampleRate * ms / 1000
        val bytes = samples * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            track.write(ByteArray(bytes), 0, bytes)
            track.play()
            delay(ms.toLong())
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
