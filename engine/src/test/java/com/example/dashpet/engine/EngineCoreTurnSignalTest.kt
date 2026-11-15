package com.example.dashpet.engine

import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.TurnSignal
import com.example.dashpet.signal.TurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** US6 시나리오 1~2 (FR-019). */
class EngineCoreTurnSignalTest {
    @Test
    fun initialTurnSignalIsOff() {
        assertEquals(TurnState.OFF, EngineCore().advanceTo(0).output.turnSignal)
    }

    @Test
    fun rightSignalIsReportedWithoutLaneChange() {
        val core = EngineCore()
        val on = core.process(TurnSignal(1_000, TurnState.RIGHT))
        assertTrue(on.events.isEmpty()) // 이벤트 없이도 새 출력
        assertEquals(TurnState.RIGHT, on.output.turnSignal)
        val off = core.process(TurnSignal(2_000, TurnState.OFF))
        assertTrue(off.events.isEmpty())
        assertEquals(TurnState.OFF, off.output.turnSignal)
    }

    @Test
    fun leftSignalAndSwitchingAreReported() {
        val core = EngineCore()
        assertEquals(TurnState.LEFT, core.process(TurnSignal(0, TurnState.LEFT)).output.turnSignal)
        assertEquals(TurnState.RIGHT, core.process(TurnSignal(100, TurnState.RIGHT)).output.turnSignal)
    }

    @Test
    fun turnSignalIsIndependentOfMoodAndRestriction() {
        val core = EngineCore()
        core.process(Restriction(0, RestrictionState.ACTIVE))
        val step = core.process(TurnSignal(100, TurnState.LEFT))
        assertEquals(TurnState.LEFT, step.output.turnSignal)
        assertEquals(MotionTier.STATIC, step.output.tier)
        // 다른 신호가 와도 방향지시등 상태는 유지된다
        assertEquals(TurnState.LEFT, core.process(com.example.dashpet.signal.Speed(200, 3f)).output.turnSignal)
    }
}
