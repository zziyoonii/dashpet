package com.example.dashpet.signal.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSynthesizer(initial: VoiceAvailability = VoiceAvailability.READY) : Synthesizer {
    override val availability: MutableStateFlow<VoiceAvailability> = MutableStateFlow(initial)
    private val _events = MutableSharedFlow<SynthEvent>(extraBufferCapacity = 8)
    override val events: Flow<SynthEvent> = _events
    val log = mutableListOf<String>()
    var released = false

    override fun speak(text: String) { log += "speak:$text" }
    override fun stop() { log += "stop" }
    override fun release() { released = true; log += "release" }
    fun emit(e: SynthEvent) { _events.tryEmit(e) }
}

/** 호출 순서를 기록하려고 합성기와 포커스, 무음이 같은 목록을 공유한다. */
class FakeSilence(private val order: MutableList<String>) : SilencePlayer {
    override suspend fun play(ms: Int) {
        order += "silence:$ms"
        kotlinx.coroutines.delay(ms.toLong())
    }
}
