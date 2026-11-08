package com.example.dashpet.engine

import com.example.dashpet.signal.WarningType

/**
 * 차선 이탈과 충돌 위험 경고를 이벤트로 바꾼다. 경고가 꺼지거나 이미 켜져 있는 경고의 반복은 이벤트가 아니다.
 * `SEATBELT` 는 주행 수명주기와 묶여 있어 [TripTracker] 가 다룬다.
 */
internal class WarningEventDetector(private val config: EngineConfig) {
    private val cooldown = Cooldown()
    private val active = HashSet<WarningType>()

    fun onWarning(atMs: Long, type: WarningType, isActive: Boolean): DrivingEvent? {
        val eventType = when (type) {
            WarningType.LANE_DEPARTURE -> EventType.LANE_DEPARTURE
            WarningType.COLLISION, WarningType.PROXIMITY -> EventType.COLLISION_RISK
            WarningType.SEATBELT -> return null
        }
        if (!isActive) {
            active -= type
            return null
        }
        if (!active.add(type)) return null // 이미 켜져 있음
        if (!cooldown.tryAcquire(eventType, atMs, config.cooldownWarningMs)) return null
        return DrivingEvent(eventType, atMs, scoreDelta = config.scoreWeight(eventType))
    }
}
