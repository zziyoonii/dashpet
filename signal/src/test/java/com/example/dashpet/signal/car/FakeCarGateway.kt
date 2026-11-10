package com.example.dashpet.signal.car

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * 테스트용 가짜 게이트웨이. [events] 를 수집하기 시작할 때 [openCount], 끝날 때 [closeCount] 를 올린다.
 * 테스트가 `Ready` 직후의 초기값을 직접 [emit] 해 실제 게이트웨이를 흉내 낸다.
 */
class FakeCarGateway : CarGateway {
    private val flow = MutableSharedFlow<CarEvent>(extraBufferCapacity = 4096)

    var openCount = 0
        private set
    var closeCount = 0
        private set

    /** 지금 열려 있는 수집이 있는가. */
    val isOpen: Boolean get() = openCount > closeCount

    /** [fail] 이 내보내는 표식. 수집 중에 이 이벤트를 만나면 예외로 끝난다. */
    private val failMarker = CarEvent.GearChanged(Int.MIN_VALUE)

    override fun events(): Flow<CarEvent> = flow
        .map { if (it === failMarker) throw IllegalStateException("fake gateway failed") else it }
        .onStart { openCount++ }
        .onCompletion { closeCount++ }

    /** 열려 있는 수집이 없으면 이벤트는 버려진다(실제 차량도 연결 전 이벤트는 없다). */
    fun emit(event: CarEvent) {
        check(flow.tryEmit(event)) { "buffer full" }
    }

    /** 수집 중인 흐름을 예외로 끝낸다(차량 서비스 접근이 실행 도중 실패하는 상황). */
    fun fail() {
        emit(failMarker)
    }
}
