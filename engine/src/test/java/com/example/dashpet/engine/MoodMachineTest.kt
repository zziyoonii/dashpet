package com.example.dashpet.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodMachineTest {
    private fun ev(type: EventType, atMs: Long, intensity: Float = 0f, direction: Direction4? = null) =
        DrivingEvent(type, atMs, intensity, direction)

    private fun machine() = MoodMachine(EngineConfig())

    @Test
    fun laneDepartureMakesDizzyThenIdleAfter3Seconds() {
        val m = machine()
        assertTrue(m.onEvent(ev(EventType.LANE_DEPARTURE, 1_000)))
        assertEquals(Mood.DIZZY, m.state.mood)
        assertEquals(4_000L, m.state.untilMs)
        m.advanceTo(3_999)
        assertEquals(Mood.DIZZY, m.state.mood)
        m.advanceTo(4_000)
        assertEquals(PetState.IDLE, m.state)
    }

    @Test
    fun higherPriorityInterruptsImmediately() {
        val m = machine()
        m.onEvent(ev(EventType.LANE_DEPARTURE, 0))
        assertTrue(m.onEvent(ev(EventType.COLLISION_RISK, 500)))
        assertEquals(Mood.STARTLED, m.state.mood)
    }

    @Test
    fun lowerPriorityIsDroppedAndNotQueued() {
        val m = machine()
        m.onEvent(ev(EventType.COLLISION_RISK, 0))
        assertFalse(m.onEvent(ev(EventType.HARD_BRAKE, 100)))
        assertEquals(Mood.STARTLED, m.state.mood)
        m.advanceTo(3_000)
        assertEquals(Mood.IDLE, m.state.mood) // 대기열에 쌓였다면 SQUASHED 가 나왔을 것
    }

    @Test
    fun samePriorityTransitionsAndRestartsDuration() {
        val m = machine()
        m.onEvent(ev(EventType.HARD_BRAKE, 0))
        assertEquals(1_200L, m.state.untilMs)
        assertTrue(m.onEvent(ev(EventType.HARD_ACCEL, 1_000, 0.7f, Direction4.BACK)))
        assertEquals(Mood.SQUASHED, m.state.mood)
        assertEquals(2_200L, m.state.untilMs)
        assertEquals(0.7f, m.state.intensity, 0f)
        assertEquals(Direction4.BACK, m.state.direction)
    }

    @Test
    fun durationsFollowConfig() {
        fun until(type: EventType) = machine().also { it.onEvent(ev(type, 100)) }.state.untilMs
        assertEquals(3_100L, until(EventType.COLLISION_RISK))
        assertEquals(3_100L, until(EventType.LANE_DEPARTURE))
        assertEquals(1_300L, until(EventType.HARD_CORNER))
        assertEquals(2_100L, until(EventType.SEATBELT_OK))
        val custom = MoodMachine(EngineConfig(dizzyDurationMs = 500))
        custom.onEvent(ev(EventType.LANE_DEPARTURE, 100))
        assertEquals(600L, custom.state.untilMs)
    }

    @Test
    fun eventToMoodTable() {
        val expected = mapOf(
            EventType.HARD_ACCEL to Mood.SQUASHED,
            EventType.HARD_BRAKE to Mood.SQUASHED,
            EventType.HARD_CORNER to Mood.SQUASHED,
            EventType.LANE_DEPARTURE to Mood.DIZZY,
            EventType.COLLISION_RISK to Mood.STARTLED,
            EventType.SEATBELT_OK to Mood.HAPPY,
            EventType.GOOD_LANE_CHANGE to Mood.HAPPY,
        )
        for ((type, mood) in expected) {
            val m = machine()
            m.onEvent(ev(type, 0))
            assertEquals(type.name, mood, m.state.mood)
        }
    }

    @Test
    fun eventsWithoutMoodLeaveStateUnchanged() {
        val m = machine()
        assertFalse(m.onEvent(ev(EventType.NO_SIGNAL_LANE_CHANGE, 0)))
        assertFalse(m.onEvent(ev(EventType.TRIP_START, 0)))
        assertEquals(PetState.IDLE, m.state)
        m.onEvent(ev(EventType.LANE_DEPARTURE, 0))
        assertFalse(m.onEvent(ev(EventType.NO_SIGNAL_LANE_CHANGE, 10)))
        assertEquals(3_000L, m.state.untilMs) // 지속 시간도 그대로
    }

    @Test
    fun tripEndIsHappyOnlyWhenScoreAtLeast80() {
        val low = machine().also { it.onEvent(ev(EventType.TRIP_END, 0), scoreAfter = 79) }
        assertEquals(Mood.IDLE, low.state.mood)
        val ok = machine().also { it.onEvent(ev(EventType.TRIP_END, 0), scoreAfter = 80) }
        assertEquals(Mood.HAPPY, ok.state.mood)
    }

    @Test
    fun timeNeverGoesBack() {
        val m = machine()
        m.advanceTo(5_000)
        m.onEvent(ev(EventType.LANE_DEPARTURE, 5_000))
        m.advanceTo(100) // 무시되어야 함
        m.advanceTo(7_999)
        assertEquals(Mood.DIZZY, m.state.mood)
        m.advanceTo(8_000)
        assertEquals(Mood.IDLE, m.state.mood)
    }

    @Test
    fun returnsToBackgroundStateWhenPresent() {
        val m = machine()
        m.setBackground(Mood.AMBIENT)
        assertEquals(Mood.AMBIENT, m.state.mood)
        m.onEvent(ev(EventType.SEATBELT_OK, 0))
        assertEquals(Mood.HAPPY, m.state.mood)
        m.advanceTo(2_000)
        assertEquals(Mood.AMBIENT, m.state.mood)
        m.setBackground(Mood.IDLE)
        assertEquals(Mood.IDLE, m.state.mood)
    }

    @Test
    fun moodStateStaysIdleWithoutEvents() {
        val m = machine()
        m.advanceTo(10_000)
        assertEquals(PetState.IDLE, m.state)
    }
}
