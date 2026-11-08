package com.example.dashpet.engine

import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.WarningType
import com.example.dashpet.signal.scenario.ScenarioBuilder
import com.example.dashpet.signal.scenario.scenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCoreMotionTest {
    @Test
    fun initialRestrictionIsUnknownSoOutputIsStatic() {
        val core = EngineCore()
        assertEquals(MotionTier.STATIC, core.advanceTo(0).output.tier)
        assertEquals(MotionTier.STATIC, core.process(com.example.dashpet.signal.Speed(10, 5f)).output.tier)
    }

    @Test
    fun restrictionSignalChangesOutputEvenWithoutEvents() {
        val core = EngineCore()
        val none = core.process(Restriction(100, RestrictionState.NONE))
        assertTrue(none.events.isEmpty())
        assertEquals(MotionTier.FULL, none.output.tier)
        val active = core.process(Restriction(200, RestrictionState.ACTIVE))
        assertTrue(active.events.isEmpty())
        assertEquals(MotionTier.STATIC, active.output.tier)
        val unknown = core.process(Restriction(300, RestrictionState.UNKNOWN))
        assertEquals(MotionTier.STATIC, unknown.output.tier)
    }

    @Test
    fun fullMotionBecomesStaticFromNextOutputWhenRestricted() {
        val core = EngineCore()
        core.process(Restriction(0, RestrictionState.NONE))
        core.process(com.example.dashpet.signal.ParkedState(0, false))
        val warn = core.process(com.example.dashpet.signal.VehicleWarning(100, WarningType.LANE_DEPARTURE, true))
        assertEquals(Mood.DIZZY, warn.output.mood)
        assertEquals(MotionTier.FULL, warn.output.tier)
        // 같은 감정이 이어지는 중에 제한이 걸리면 바로 다음 출력부터 STATIC
        val restricted = core.process(Restriction(200, RestrictionState.ACTIVE))
        assertEquals(Mood.DIZZY, restricted.output.mood)
        assertEquals(MotionTier.STATIC, restricted.output.tier)
        assertEquals(MotionTier.STATIC, core.advanceTo(300).output.tier)
    }

    /** 이벤트 종류별로 그 이벤트를 만드는 신호를 5초 근처에 넣는다. 0~1초에 주행을 시작한다. */
    private fun ScenarioBuilder.trigger(type: EventType) {
        when (type) {
            EventType.TRIP_START -> Unit // 아래 prefix 의 주차 -> 주차 아님 이 만든다
            EventType.SEATBELT_OK -> {
                at(2_000) { warning(WarningType.SEATBELT, true) }
                at(3_000) { warning(WarningType.SEATBELT, false) }
            }
            EventType.HARD_ACCEL -> at(5_000) { speedRamp(0, 50, 2_000) }
            EventType.HARD_BRAKE -> {
                at(4_000) { brakePedal(true) }
                at(5_000) { speedRamp(50, 0, 2_000) }
            }
            EventType.HARD_CORNER -> {
                at(4_000) { speed(14); heading(90) }
                at(5_000) { headingTurn(30, 1_000) }
            }
            EventType.LANE_DEPARTURE -> at(5_000) { warning(WarningType.LANE_DEPARTURE, true) }
            EventType.COLLISION_RISK -> at(5_000) { warning(WarningType.COLLISION, true) }
            EventType.GOOD_LANE_CHANGE -> {
                at(4_000) { turnSignal(TurnState.RIGHT) }
                at(5_000) { laneChange(Side.RIGHT) }
            }
            EventType.NO_SIGNAL_LANE_CHANGE -> at(5_000) { laneChange(Side.LEFT) }
            EventType.TRIP_END -> at(5_000) { parked(true) }
        }
    }

    private fun triggerScenario(type: EventType, restriction: RestrictionState?) = scenario("trigger-$type") {
        at(0) {
            restriction?.let { restriction(it) }
            parked(true)
            speed(0)
            brakePedal(false)
        }
        at(1_000) { parked(false) }
        trigger(type)
    }

    private val producible = EventType.entries.toList() // 10종 모두

    @Test
    fun fullMotionNeverAppearsWhileRestrictedOrUnknown() {
        // SC-003: 모든 이벤트 종류를 ACTIVE 와 UNKNOWN(제한 신호 없음, 명시적 UNKNOWN) 에서 발생시킨다
        for (restriction in listOf(RestrictionState.ACTIVE, RestrictionState.UNKNOWN, null)) {
            for (type in producible) {
                val steps = runScenario(triggerScenario(type, restriction))
                assertTrue("$type must occur under $restriction", steps.eventsOf(type).isNotEmpty())
                assertTrue(
                    "$type under $restriction produced FULL",
                    steps.none { it.output.tier == MotionTier.FULL },
                )
            }
        }
    }

    @Test
    fun controlRunWithoutRestrictionDoesProduceFullMotion() {
        for (type in producible) {
            val steps = runScenario(triggerScenario(type, RestrictionState.NONE))
            assertTrue("$type under NONE", steps.eventsOf(type).isNotEmpty())
            val at = steps.first { it.events.any { e -> e.type == type } }
            assertEquals(MotionTier.FULL, at.output.tier)
        }
    }

    @Test
    fun oneShotIsNotOutputWithDefaultConfigEvenWhenRestricted() {
        for (type in producible) {
            val steps = runScenario(triggerScenario(type, RestrictionState.ACTIVE))
            assertTrue(steps.none { it.output.tier == MotionTier.ONE_SHOT })
        }
    }

    @Test
    fun oneShotAppearsOnlyOnEventStepsWhenEnabledAndActive() {
        val config = EngineConfig(allowOneShot = true)
        val steps = runScenario(triggerScenario(EventType.LANE_DEPARTURE, RestrictionState.ACTIVE), config)
        assertTrue(steps.any { it.output.tier == MotionTier.ONE_SHOT })
        assertTrue(steps.filter { it.output.tier == MotionTier.ONE_SHOT }.all { it.events.isNotEmpty() })
        val unknown = runScenario(triggerScenario(EventType.LANE_DEPARTURE, RestrictionState.UNKNOWN), config)
        assertTrue(unknown.none { it.output.tier != MotionTier.STATIC })
    }
}
