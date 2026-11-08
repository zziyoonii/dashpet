package com.example.dashpet.engine

import com.example.dashpet.signal.TurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ValueTypesTest {
    @Test
    fun engineConfigDefaultsMatchDataModelTable() {
        val c = EngineConfig()
        assertEquals(2.9, c.hardAccelThresholdMps2, 0.0)
        assertEquals(3.9, c.hardBrakeThresholdMps2, 0.0)
        assertEquals(3.9, c.hardCornerThresholdMps2, 0.0)
        assertEquals(15.0 / 3.6, c.cornerMinSpeedMps, 1e-12)
        assertEquals(7.8, c.intensityMaxMps2, 0.0)
        assertEquals(0.4, c.smoothingAlpha, 0.0)
        assertEquals(2, c.confirmSamples)
        assertEquals(2000L, c.maxSampleGapMs)
        assertEquals(3000L, c.cooldownHardMs)
        assertEquals(10000L, c.cooldownWarningMs)
        assertEquals(5000L, c.cooldownLaneEvalMs)
        assertEquals(3000L, c.laneChangeLookbackMs)
        assertEquals(listOf(3000L, 3000L, 1200L, 2000L),
            listOf(c.startledDurationMs, c.dizzyDurationMs, c.squashedDurationMs, c.happyDurationMs))
        assertEquals(5, c.goodLaneChangeBonusCap)
        assertEquals(80, c.happyScoreThreshold)
        assertFalse(c.allowOneShot)
        assertEquals(
            mapOf(
                EventType.LANE_DEPARTURE to -5, EventType.COLLISION_RISK to -5,
                EventType.NO_SIGNAL_LANE_CHANGE to -3, EventType.HARD_ACCEL to -2,
                EventType.HARD_BRAKE to -2, EventType.HARD_CORNER to -2,
                EventType.GOOD_LANE_CHANGE to 1, EventType.SEATBELT_OK to 2,
            ),
            c.scoreWeights,
        )
    }

    @Test
    fun eventTypesAreTheTenFromFr001() {
        assertEquals(10, EventType.entries.size)
    }

    @Test
    fun drivingEventIntensityMustBeInUnitRange() {
        DrivingEvent(EventType.HARD_BRAKE, 0, 0f)
        DrivingEvent(EventType.HARD_BRAKE, 0, 1f)
        for (bad in listOf(-0.01f, 1.01f, Float.NaN)) {
            assertThrows(IllegalArgumentException::class.java) { DrivingEvent(EventType.HARD_BRAKE, 0, bad) }
        }
    }

    @Test
    fun moodPrioritiesFollowFr009() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            listOf(Mood.IDLE, Mood.AMBIENT, Mood.HAPPY, Mood.SQUASHED, Mood.DIZZY, Mood.STARTLED).map { it.priority },
        )
    }

    @Test
    fun sameStateIgnoresOnlyTimestamp() {
        val a = PetOutput(Mood.IDLE, 0f, null, MotionTier.STATIC, TurnState.OFF, 10)
        assertTrue(a.sameStateAs(a.copy(atMs = 99)))
        assertFalse(a.sameStateAs(a.copy(tier = MotionTier.FULL)))
        assertFalse(a.sameStateAs(a.copy(turnSignal = TurnState.LEFT)))
        assertFalse(a.sameStateAs(a.copy(mood = Mood.HAPPY)))
    }
}
