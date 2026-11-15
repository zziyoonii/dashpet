package com.example.dashpet.signal.scenario

import com.example.dashpet.signal.BrakePedal
import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.Speed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScenarioExpandTest {
    @Test
    fun speedRampExpandsAt100msIncludingBothEnds() {
        val signals = scenario("ramp") { at(1_000) { speedRamp(0, 36, 1_000) } }.expand()
        assertEquals(11, signals.size)
        assertEquals((1_000L..2_000L step 100).toList(), signals.map { it.timestampMs })
        val speeds = signals.map { (it as Speed).speedMps }
        assertEquals(0f, speeds.first(), 1e-5f)
        assertEquals(10f, speeds.last(), 1e-5f) // 36 km/h = 10 m/s
        assertEquals(5f, speeds[5], 1e-5f)
    }

    @Test
    fun headingTurnIsRelativeToPreviousHeading() {
        val signals = scenario("turn") {
            at(0) { heading(90) }
            at(1_000) { headingTurn(30, 1_000) }
        }.expand()
        val headings = signals.map { (it as Heading) }
        assertEquals(12, headings.size)
        assertEquals(90f, headings[1].headingDeg, 1e-4f)
        assertEquals(120f, headings.last().headingDeg, 1e-4f)
        assertEquals(105f, headings[6].headingDeg, 1e-4f)
    }

    @Test
    fun consecutiveTurnsAccumulateAndHeadingIsNormalized() {
        val signals = scenario("wrap") {
            at(0) { heading(350) }
            at(1_000) { headingTurn(20, 100) }
            at(2_000) { headingTurn(-30, 100) }
        }.expand()
        val last = signals.last() as Heading
        assertEquals(340f, last.headingDeg, 1e-3f) // 350 -> 10 -> 340
        assertTrue(signals.filterIsInstance<Heading>().all { it.headingDeg in 0f..359.999f })
    }

    @Test
    fun sameTimestampKeepsInputOrder() {
        val signals = scenario("order") {
            at(500) {
                parked(false)
                brakePedal(true)
                parked(true)
            }
        }.expand()
        assertEquals(
            listOf(ParkedState(500, false), BrakePedal(500, true), ParkedState(500, true)),
            signals,
        )
    }

    @Test
    fun rampSamplesInterleaveWithLaterStepsByTime() {
        val signals = scenario("mix") {
            at(0) { speedRamp(0, 36, 1_000) }
            at(500) { brakePedal(true) }
        }.expand()
        assertEquals(signals.map { it.timestampMs }, signals.map { it.timestampMs }.sorted())
        val idx = signals.indexOfFirst { it is BrakePedal }
        // ramp sample at 500 was defined first, so it precedes the pedal signal
        assertTrue(signals[idx - 1] is Speed && signals[idx - 1].timestampMs == 500L)
    }

    @Test
    fun expandIsRepeatable() {
        val s = scenario("rep") {
            at(0) { heading(0) }
            at(100) { speedRamp(0, 50, 800); headingTurn(15, 500) }
        }
        assertEquals(s.expand(), s.expand())
    }

    @Test
    fun rejectsDecreasingStepTimes() {
        assertThrows(IllegalArgumentException::class.java) {
            scenario("bad") {
                at(1_000) { parked(true) }
                at(999) { parked(false) }
            }
        }
    }

    @Test
    fun rejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException::class.java) {
            scenario("bad") { at(0) { speedRamp(0, 10, 0) } }
        }
        assertThrows(IllegalArgumentException::class.java) {
            scenario("bad") { at(0) { headingTurn(10, -5) } }
        }
    }
}
