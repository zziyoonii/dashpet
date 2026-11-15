package com.example.dashpet.engine

/**
 * 우선순위 기반 감정 상태 머신. 새 이벤트의 우선순위가 현재 이상이면 즉시 전환하고 지속 시간을 다시 시작하며,
 * 낮으면 버린다(대기열 없음). 시간은 [advanceTo] 와 이벤트 시각으로만 진행하고 되돌리지 않는다.
 */
internal class MoodMachine(private val config: EngineConfig) {
    private var base: PetState = PetState.IDLE
    private var nowMs = Long.MIN_VALUE

    var state: PetState = PetState.IDLE
        private set

    /** 지속 시간이 끝나면 돌아갈 배경 상태를 정한다. 진행 중인 감정이 없으면 바로 적용한다. */
    fun setBackground(mood: Mood) {
        base = if (mood == Mood.IDLE) PetState.IDLE else PetState(mood, 0f, null, null)
        if (state.untilMs == null) state = base
    }

    fun advanceTo(timeMs: Long) {
        if (timeMs < nowMs) return
        nowMs = timeMs
        val until = state.untilMs
        if (until != null && nowMs >= until) state = base
    }

    /**
     * 이벤트를 반영한다. [scoreAfter] 는 이 이벤트까지 반영한 주행 점수(`TRIP_END` 의 기쁨 판정용).
     * 감정이 바뀌었으면 true.
     */
    fun onEvent(event: DrivingEvent, scoreAfter: Int = 100): Boolean {
        advanceTo(event.atMs)
        val mood = moodFor(event.type, scoreAfter) ?: return false
        if (mood.priority < state.mood.priority) return false
        state = PetState(mood, event.intensity, event.direction, event.atMs + durationMs(mood))
        return true
    }

    private fun moodFor(type: EventType, scoreAfter: Int): Mood? = when (type) {
        EventType.HARD_ACCEL, EventType.HARD_BRAKE, EventType.HARD_CORNER -> Mood.SQUASHED
        EventType.LANE_DEPARTURE -> Mood.DIZZY
        EventType.COLLISION_RISK -> Mood.STARTLED
        EventType.SEATBELT_OK, EventType.GOOD_LANE_CHANGE -> Mood.HAPPY
        EventType.TRIP_END -> if (scoreAfter >= config.happyScoreThreshold) Mood.HAPPY else null
        EventType.TRIP_START, EventType.SEATBELT_WARN, EventType.NO_SIGNAL_LANE_CHANGE -> null
    }

    private fun durationMs(mood: Mood): Long = when (mood) {
        Mood.STARTLED -> config.startledDurationMs
        Mood.DIZZY -> config.dizzyDurationMs
        Mood.SQUASHED -> config.squashedDurationMs
        Mood.HAPPY -> config.happyDurationMs
        Mood.IDLE, Mood.AMBIENT -> 0L
    }
}
