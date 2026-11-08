package com.example.dashpet.engine

import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.scenario.scenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** US1 시나리오 1~4 를 EngineCore 수준에서 확인한다. */
class EngineCoreAccelTest {
    @Test
    fun hardBrakeScenario() {
        val steps = runScenario(
            scenario("brake") {
                at(0) { parked(false) }
                at(100) { brakePedal(true) }
                at(100) { speedRamp(50, 0, 2_000) }
            },
        )
        val e = steps.eventsOf(EventType.HARD_BRAKE).single()
        assertEquals(Direction4.FRONT, e.direction)
        assertTrue(e.intensity in 0f..1f)
        assertTrue(steps.eventsOf(EventType.HARD_ACCEL).isEmpty())
    }

    @Test
    fun hardCornerScenario() {
        for ((delta, dir) in listOf(30 to Direction4.LEFT, -30 to Direction4.RIGHT)) {
            val steps = runScenario(
                scenario("corner") {
                    at(0) { parked(false); speed(50 / 3.6); heading(90) }
                    at(1_000) { headingTurn(delta, 1_000) }
                },
            )
            assertEquals(dir, steps.eventsOf(EventType.HARD_CORNER).single().direction)
        }
    }

    @Test
    fun gentleAccelerationAndSlowTurnProduceNoEvents() {
        val gentle = runScenario(
            scenario("gentle") {
                at(0) { parked(false) }
                at(0) { speedRamp(0, 50, 8_000) }
            },
        )
        assertTrue(gentle.eventsOf(EventType.HARD_ACCEL).isEmpty())

        val slow = runScenario(
            scenario("slow") {
                at(0) { parked(false); speed(10 / 3.6); heading(0) }
                at(1_000) { headingTurn(90, 1_000) }
            },
        )
        assertTrue(slow.eventsOf(EventType.HARD_CORNER).isEmpty())
    }

    @Test
    fun outOfOrderSignalIsIgnoredWithoutException() {
        val core = EngineCore()
        core.process(Speed(1_000, 10f))
        val step = core.process(Speed(500, 50f))
        assertTrue(step.events.isEmpty())
        assertEquals(1_000L, step.output.atMs)
    }

    @Test
    fun hardAccelScenario() {
        val steps = runScenario(
            scenario("accel") {
                at(0) { parked(false) }
                at(0) { speedRamp(0, 50, 2_000) }
            },
        )
        val e = steps.eventsOf(EventType.HARD_ACCEL).single()
        assertEquals(Direction4.BACK, e.direction)
    }
}
