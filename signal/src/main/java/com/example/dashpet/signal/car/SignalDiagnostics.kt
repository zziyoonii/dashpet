package com.example.dashpet.signal.car

import com.example.dashpet.signal.RestrictionState

/** 차량 서비스와의 연결 상태. 수신을 멈추면 항상 [IDLE] 이다. */
enum class CarLinkState { IDLE, CONNECTING, CONNECTED, UNAVAILABLE, LOST }

/** 개발자가 보는 신호 진단 값(불변). 필드 의미는 `data-model.md` 의 "진단" 표를 따른다. */
data class SignalDiagnostics(
    val link: CarLinkState,
    /** 마지막으로 엔진에 보낸 제한. 아직 보내지 않았으면 [RestrictionState.UNKNOWN]. */
    val restriction: RestrictionState,
    /** 마지막으로 받은 `activeRestrictions` 비트. 없으면 null. */
    val activeRestrictions: Int?,
    val speedMps: Float?,
    val parked: Boolean?,
    val speedAvailability: SignalAvailability,
    val gearAvailability: SignalAvailability,
    val restrictionAvailability: SignalAvailability,
    /** 마지막으로 값을 받은 시각(소스 시작 기준 ms). 제한이 변하지 않는 동안에도 연결이 정상임을 보이는 용도. */
    val lastUpdateMs: Long?,
) {
    companion object {
        val INITIAL = SignalDiagnostics(
            link = CarLinkState.IDLE,
            restriction = RestrictionState.UNKNOWN,
            activeRestrictions = null,
            speedMps = null,
            parked = null,
            speedAvailability = SignalAvailability.UNKNOWN,
            gearAvailability = SignalAvailability.UNKNOWN,
            restrictionAvailability = SignalAvailability.UNKNOWN,
            lastUpdateMs = null,
        )
    }
}
