package com.example.dashpet.signal.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeAudioFocusGateway(var grant: Boolean = true) : AudioFocusGateway {
    private val _changes = MutableSharedFlow<FocusChange>(extraBufferCapacity = 8)
    override val changes: Flow<FocusChange> = _changes
    override var requestCount = 0
        private set
    override var abandonCount = 0
        private set

    override fun request(): Boolean { requestCount++; return grant }
    override fun abandon() { abandonCount++ }
    fun emit(change: FocusChange) { _changes.tryEmit(change) }
}
