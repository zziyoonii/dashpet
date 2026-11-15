package com.example.dashpet.engine

import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaneChangeEventDetectorTest {
    private fun detector() = LaneChangeEventDetector(EngineConfig())

    @Test
    fun sameDirectionSignalWithinLookbackIsGood() {
        val d = detector()
        d.onTurnSignal(1_000, TurnState.RIGHT)
        val e = d.onLaneChange(2_000, Side.RIGHT)!!
        assertEquals(EventType.GOOD_LANE_CHANGE, e.type)
        assertEquals(1, e.scoreDelta)
        assertEquals(Direction4.LEFT, e.direction) // 우측 차선 변경 -> 몸은 왼쪽. [확인 필요] 방향 규칙
    }

    @Test
    fun noSignalIsNoSignalLaneChange() {
        val e = detector().onLaneChange(2_000, Side.LEFT)!!
        assertEquals(EventType.NO_SIGNAL_LANE_CHANGE, e.type)
        assertEquals(-3, e.scoreDelta)
        assertEquals(Direction4.RIGHT, e.direction)
    }

    @Test
    fun oppositeDirectionSignalIsNoSignalLaneChange() {
        val d = detector()
        d.onTurnSignal(1_000, TurnState.LEFT)
        assertEquals(EventType.NO_SIGNAL_LANE_CHANGE, d.onLaneChange(2_000, Side.RIGHT)!!.type)
    }

    @Test
    fun signalTurnedOffRightAfterLaneChangeStillCounts() {
        // 데모: 30초 켬, 32초 차선 변경, 33초 꺼짐
        val d = detector()
        d.onTurnSignal(30_000, TurnState.RIGHT)
        assertEquals(EventType.GOOD_LANE_CHANGE, d.onLaneChange(32_000, Side.RIGHT)!!.type)
        d.onTurnSignal(33_000, TurnState.OFF)
    }

    @Test
    fun signalTurnedOffJustBeforeLaneChangeCountsWithinLookback() {
        fun eval(changeAt: Long): EventType {
            val d = detector()
            d.onTurnSignal(0, TurnState.RIGHT)
            d.onTurnSignal(1_000, TurnState.OFF)
            return d.onLaneChange(changeAt, Side.RIGHT)!!.type
        }
        assertEquals(EventType.GOOD_LANE_CHANGE, eval(4_000)) // 꺼진 지 3초
        assertEquals(EventType.NO_SIGNAL_LANE_CHANGE, eval(4_001))
    }

    @Test
    fun signalStillOnFromLongAgoCounts() {
        val d = detector()
        d.onTurnSignal(0, TurnState.RIGHT)
        assertEquals(EventType.GOOD_LANE_CHANGE, d.onLaneChange(60_000, Side.RIGHT)!!.type)
    }

    @Test
    fun switchingSignalDirectionTracksEachSide() {
        val d = detector()
        d.onTurnSignal(0, TurnState.LEFT)
        d.onTurnSignal(500, TurnState.RIGHT)
        assertEquals(EventType.GOOD_LANE_CHANGE, d.onLaneChange(1_000, Side.LEFT)!!.type) // 왼쪽은 0.5초 전까지 켜짐
    }

    @Test
    fun evaluationsWithin5SecondsAreRecognizedOnce() {
        val d = detector()
        d.onTurnSignal(0, TurnState.RIGHT)
        assertEquals(EventType.GOOD_LANE_CHANGE, d.onLaneChange(1_000, Side.RIGHT)!!.type)
        assertNull(d.onLaneChange(5_999, Side.RIGHT))
        assertEquals(EventType.GOOD_LANE_CHANGE, d.onLaneChange(6_000, Side.RIGHT)!!.type)
    }

    @Test
    fun cooldownIsSharedBetweenGoodAndNoSignal() {
        val d = detector()
        d.onLaneChange(1_000, Side.LEFT)
        d.onTurnSignal(1_500, TurnState.RIGHT)
        assertNull(d.onLaneChange(2_000, Side.RIGHT))
    }
}
