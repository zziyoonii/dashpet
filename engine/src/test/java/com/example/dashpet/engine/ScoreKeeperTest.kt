package com.example.dashpet.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreKeeperTest {
    private fun ev(type: EventType, at: Long = 0) = DrivingEvent(type, at)

    private fun keeper() = ScoreKeeper(EngineConfig())

    @Test
    fun startsAt100() {
        assertEquals(100, keeper().score)
    }

    @Test
    fun demoCommuteSumsTo94() {
        val k = keeper()
        listOf(
            EventType.TRIP_START, EventType.SEATBELT_OK, EventType.HARD_CORNER, EventType.GOOD_LANE_CHANGE,
            EventType.LANE_DEPARTURE, EventType.HARD_BRAKE, EventType.TRIP_END,
        ).forEach { k.apply(ev(it)) }
        assertEquals(94, k.score) // 100 + 2 + 1 - 2 - 5 - 2
    }

    @Test
    fun weightsTable() {
        val expected = mapOf(
            EventType.LANE_DEPARTURE to -5, EventType.COLLISION_RISK to -5, EventType.NO_SIGNAL_LANE_CHANGE to -3,
            EventType.HARD_ACCEL to -2, EventType.HARD_BRAKE to -2, EventType.HARD_CORNER to -2,
            EventType.GOOD_LANE_CHANGE to 1, EventType.SEATBELT_OK to 2,
            EventType.TRIP_START to 0, EventType.TRIP_END to 0,
        )
        for ((type, delta) in expected) {
            assertEquals(type.name, delta, keeper().apply(ev(type)).scoreDelta)
        }
    }

    @Test
    fun goodLaneChangeBonusIsCappedAtFivePerTrip() {
        val k = keeper()
        val deltas = (1..7).map { k.apply(ev(EventType.GOOD_LANE_CHANGE, it * 1000L)).scoreDelta }
        assertEquals(listOf(1, 1, 1, 1, 1, 0, 0), deltas) // 이벤트는 모두 돌려주고 초과분은 0점
    }

    @Test
    fun capIsNotUsedUpBySeatbelt() {
        val k = keeper()
        k.apply(ev(EventType.SEATBELT_OK))
        val deltas = (1..6).map { k.apply(ev(EventType.GOOD_LANE_CHANGE)).scoreDelta }
        assertEquals(listOf(1, 1, 1, 1, 1, 0), deltas)
    }

    @Test
    fun capMattersBelow100() {
        val k = keeper()
        k.apply(ev(EventType.LANE_DEPARTURE))
        k.apply(ev(EventType.LANE_DEPARTURE)) // 90
        repeat(7) { k.apply(ev(EventType.GOOD_LANE_CHANGE)) }
        assertEquals(95, k.score) // 상한이 없었다면 97
    }

    @Test
    fun clampsOnlyWhenQueried_high() {
        val k = keeper()
        k.apply(ev(EventType.SEATBELT_OK)) // 합계 102
        assertEquals(100, k.score)
        k.apply(ev(EventType.HARD_BRAKE)) // 합계 100: 합산 중에 잘랐다면 98
        assertEquals(100, k.score)
        k.apply(ev(EventType.HARD_BRAKE)) // 98
        assertEquals(98, k.score)
    }

    @Test
    fun clampsOnlyWhenQueried_low() {
        val k = keeper()
        repeat(21) { k.apply(ev(EventType.LANE_DEPARTURE)) } // 합계 -5
        assertEquals(-5, k.rawScore)
        assertEquals(0, k.score)
        k.apply(ev(EventType.SEATBELT_OK))
        k.apply(ev(EventType.SEATBELT_OK)) // 합계 -1: 합산 중에 잘랐다면 4
        assertEquals(0, k.score)
        repeat(2) { k.apply(ev(EventType.SEATBELT_OK)) } // 합계 3
        assertEquals(3, k.score)
    }

    @Test
    fun weightsAndCapComeFromConfig() {
        val k = ScoreKeeper(EngineConfig(scoreWeights = mapOf(EventType.HARD_BRAKE to -10), goodLaneChangeBonusCap = 0))
        assertEquals(-10, k.apply(ev(EventType.HARD_BRAKE)).scoreDelta)
        assertEquals(90, k.score)
        assertEquals(0, k.apply(ev(EventType.GOOD_LANE_CHANGE)).scoreDelta)
    }
}
