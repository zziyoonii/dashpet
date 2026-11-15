package com.example.dashpet.engine

enum class EventType {
    TRIP_START,
    /** 출발하는 순간 안전벨트 경고가 켜져 있었다("벨트부터!"). 이어서 경고가 꺼지면 [SEATBELT_OK] 가 난다. */
    SEATBELT_WARN,
    SEATBELT_OK,
    HARD_ACCEL,
    HARD_BRAKE,
    HARD_CORNER,
    LANE_DEPARTURE,
    COLLISION_RISK,
    GOOD_LANE_CHANGE,
    NO_SIGNAL_LANE_CHANGE,
    TRIP_END,
}

/** 몸이 쏠리는 쪽. */
enum class Direction4 { FRONT, BACK, LEFT, RIGHT }

/**
 * 판정 결과. [intensity] 는 0 이상 1 이하이며 가속도 이벤트만 의미가 있다(나머지는 0).
 * [scoreDelta] 는 실제로 점수에 반영된 변화량이다(가점 상한을 넘으면 0).
 */
data class DrivingEvent(
    val type: EventType,
    val atMs: Long,
    val intensity: Float = 0f,
    val direction: Direction4? = null,
    val scoreDelta: Int = 0,
) {
    init {
        require(intensity in 0f..1f) { "intensity must be in 0..1: $intensity" }
    }
}
