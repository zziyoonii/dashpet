package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType

/** 말하기 우선순위. 큰 값이 우선한다(specs/004 data-model.md). */
enum class SpeechPriority(val rank: Int) {
    INFO(0),
    PRAISE(1),
    WARN(2),
    DANGER(3),
}

/** 이벤트 11종 모두 정확히 하나의 우선순위를 가진다. `else` 를 두지 않아 새 이벤트가 생기면 컴파일이 실패한다. */
fun EventType.speechPriority(): SpeechPriority = when (this) {
    EventType.COLLISION_RISK, EventType.LANE_DEPARTURE -> SpeechPriority.DANGER
    EventType.HARD_ACCEL, EventType.HARD_BRAKE, EventType.HARD_CORNER, EventType.NO_SIGNAL_LANE_CHANGE, EventType.SEATBELT_WARN -> SpeechPriority.WARN
    EventType.GOOD_LANE_CHANGE, EventType.SEATBELT_OK -> SpeechPriority.PRAISE
    EventType.TRIP_START, EventType.TRIP_END -> SpeechPriority.INFO
}
