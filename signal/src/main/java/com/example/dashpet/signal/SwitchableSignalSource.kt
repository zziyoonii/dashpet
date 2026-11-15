package com.example.dashpet.signal

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 현재 선택된 [VehicleSignalSource] 의 신호만 내보내는 래퍼. 엔진과 화면은 소스가 바뀌어도 그대로 쓴다.
 * [select] 는 이전 소스를 `stop()` 하고 수집을 새 소스로 옮긴다. 새 소스의 `start()` 는 호출하는 쪽의 책임이다
 * (`contracts/car-signal-source.md` 4절). `start()`/`stop()` 은 현재 소스에 위임한다.
 */
class SwitchableSignalSource(initial: VehicleSignalSource) : VehicleSignalSource {
    private val current = MutableStateFlow(initial)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val signals: Flow<RawSignal> = current.flatMapLatest { it.signals }

    /** 같은 소스를 다시 고르면 아무것도 하지 않는다. */
    fun select(source: VehicleSignalSource) {
        val previous = current.value
        if (previous === source) return
        previous.stop()
        current.value = source
    }

    override fun start() = current.value.start()

    override fun stop() = current.value.stop()
}
