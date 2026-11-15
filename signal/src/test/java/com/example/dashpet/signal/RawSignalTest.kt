package com.example.dashpet.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RawSignalTest {
    @Test
    fun speedAcceptsZeroAndPositive() {
        assertEquals(0f, Speed(0, 0f).speedMps, 0f)
        assertEquals(13.9f, Speed(0, 13.9f).speedMps, 0f)
    }

    @Test
    fun speedRejectsNegativeAndNaN() {
        assertThrows(IllegalArgumentException::class.java) { Speed(0, -0.1f) }
        assertThrows(IllegalArgumentException::class.java) { Speed(0, Float.NaN) }
    }

    @Test
    fun headingKeepsValuesInRange() {
        assertEquals(0f, Heading(0, 0f).headingDeg, 0f)
        assertEquals(359.5f, Heading(0, 359.5f).headingDeg, 0f)
    }

    @Test
    fun headingNormalizesOutOfRangeValues() {
        assertEquals(0f, Heading(0, 360f).headingDeg, 0f)
        assertEquals(10f, Heading(0, 370f).headingDeg, 0f)
        assertEquals(350f, Heading(0, -10f).headingDeg, 0f)
        assertEquals(350f, Heading(0, 710f).headingDeg, 0f)
    }

    @Test
    fun headingHasValueEquality() {
        assertEquals(Heading(5, 370f), Heading(5, 10f))
    }

    @Test
    fun everySignalHasTimestamp() {
        val signals: List<RawSignal> = listOf(
            Speed(1, 1f), Heading(2, 1f), ParkedState(3, true), BrakePedal(4, true),
            TurnSignal(5, TurnState.LEFT), VehicleWarning(6, WarningType.SEATBELT, true),
            LaneChange(7, Side.RIGHT), Restriction(8, RestrictionState.ACTIVE),
        )
        assertEquals((1L..8L).toList(), signals.map { it.timestampMs })
    }

    @Test
    fun tickCarriesOnlyATimestamp() {
        val tick: RawSignal = Tick(1_500)
        assertEquals(1_500L, tick.timestampMs)
        assertEquals(Tick(1_500), tick)
        assertNotEquals(Tick(1_600), tick)
    }

    @Test
    fun tickFollowsTheNonDecreasingTimeRuleWithOtherSignals() {
        // 같은 시각이면 신호가 먼저, Tick 이 나중. 시각은 감소하지 않는다.
        val stream: List<RawSignal> = listOf(Speed(0, 1f), Tick(0), Tick(100), Speed(100, 2f), Tick(100), Tick(200))
        assertEquals(stream.map { it.timestampMs }.sorted(), stream.map { it.timestampMs })
    }
}
