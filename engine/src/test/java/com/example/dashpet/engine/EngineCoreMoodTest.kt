package com.example.dashpet.engine

import com.example.dashpet.signal.WarningType
import com.example.dashpet.signal.scenario.scenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** US2 시나리오 1~4. */
class EngineCoreMoodTest {
    private fun core(): EngineCore = EngineCore().also {
        it.process(com.example.dashpet.signal.ParkedState(0, false))
    }

    @Test
    fun laneDepartureMakesDizzyThenReturnsAfter3Seconds() {
        val core = core()
        val step = core.process(com.example.dashpet.signal.VehicleWarning(1_000, WarningType.LANE_DEPARTURE, true))
        assertEquals(listOf(EventType.LANE_DEPARTURE), step.events.map { it.type })
        assertEquals(Mood.DIZZY, step.output.mood)
        assertEquals(Mood.DIZZY, core.advanceTo(3_999).output.mood)
        assertEquals(Mood.IDLE, core.advanceTo(4_000).output.mood)
    }

    @Test
    fun collisionDuringDizzyMakesStartledImmediately() {
        val core = core()
        core.process(com.example.dashpet.signal.VehicleWarning(1_000, WarningType.LANE_DEPARTURE, true))
        val step = core.process(com.example.dashpet.signal.VehicleWarning(1_500, WarningType.COLLISION, true))
        assertEquals(Mood.STARTLED, step.output.mood)
    }

    @Test
    fun lowerPriorityEventDuringStartledIsDroppedFromMoodButStillReported() {
        val steps = runScenario(
            scenario("startled-then-brake") {
                at(0) { parked(false); brakePedal(true) }
                at(1_000) { speedRamp(50, 0, 2_000) }
                at(1_100) { warning(WarningType.COLLISION, true) }
            },
        )
        val brake = steps.eventsOf(EventType.HARD_BRAKE).single()
        assertTrue(brake.atMs > 1_100)
        val atBrake = steps.first { it.events.any { e -> e.type == EventType.HARD_BRAKE } }
        assertEquals(Mood.STARTLED, atBrake.output.mood)
    }

    @Test
    fun droppedReactionIsNotQueued() {
        val core = core()
        core.process(com.example.dashpet.signal.VehicleWarning(1_000, WarningType.COLLISION, true))
        // 깜짝 도중 찌그러짐 이벤트는 버려지고, 깜짝이 끝나면 곧바로 IDLE
        core.process(com.example.dashpet.signal.Speed(1_100, 20f))
        core.process(com.example.dashpet.signal.BrakePedal(1_100, true))
        core.process(com.example.dashpet.signal.Speed(1_200, 19f))
        core.process(com.example.dashpet.signal.Speed(1_300, 18f))
        assertEquals(Mood.STARTLED, core.advanceTo(1_400).output.mood)
        assertEquals(Mood.IDLE, core.advanceTo(4_000).output.mood)
    }

    @Test
    fun sameStrengthMoodRestartsDuration() {
        val core = core()
        core.process(com.example.dashpet.signal.VehicleWarning(0, WarningType.LANE_DEPARTURE, true))
        core.process(com.example.dashpet.signal.VehicleWarning(100, WarningType.LANE_DEPARTURE, false))
        // 10초 재발 간격이 지난 뒤 다시 켜지면 지속 시간이 새로 시작된다
        core.process(com.example.dashpet.signal.VehicleWarning(10_000, WarningType.LANE_DEPARTURE, true))
        assertEquals(Mood.DIZZY, core.advanceTo(12_999).output.mood)
        assertEquals(Mood.IDLE, core.advanceTo(13_000).output.mood)
    }

    @Test
    fun outputCarriesEventIntensityAndDirection() {
        val steps = runScenario(
            scenario("corner") {
                at(0) { parked(false); speed(50 / 3.6); heading(90) }
                at(1_000) { headingTurn(30, 1_000) }
            },
        )
        val at = steps.first { it.events.isNotEmpty() }
        assertEquals(Mood.SQUASHED, at.output.mood)
        assertEquals(Direction4.LEFT, at.output.direction)
        assertEquals(at.events.single().intensity, at.output.intensity, 0f)
    }
}
