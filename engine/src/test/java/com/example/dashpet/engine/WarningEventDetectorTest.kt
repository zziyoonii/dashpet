package com.example.dashpet.engine

import com.example.dashpet.signal.WarningType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarningEventDetectorTest {
    private fun detector() = WarningEventDetector(EngineConfig())

    @Test
    fun laneDepartureWarningOnProducesEvent() {
        val e = detector().onWarning(1_000, WarningType.LANE_DEPARTURE, true)!!
        assertEquals(EventType.LANE_DEPARTURE, e.type)
        assertEquals(1_000L, e.atMs)
        assertEquals(-5, e.scoreDelta)
    }

    @Test
    fun collisionAndProximityBothMeanCollisionRisk() {
        assertEquals(EventType.COLLISION_RISK, detector().onWarning(0, WarningType.COLLISION, true)!!.type)
        assertEquals(EventType.COLLISION_RISK, detector().onWarning(0, WarningType.PROXIMITY, true)!!.type)
    }

    @Test
    fun repeatedWithin10SecondsIsRecognizedOnce() {
        val d = detector()
        assertEquals(EventType.LANE_DEPARTURE, d.onWarning(0, WarningType.LANE_DEPARTURE, true)!!.type)
        d.onWarning(1_000, WarningType.LANE_DEPARTURE, false)
        assertNull(d.onWarning(5_000, WarningType.LANE_DEPARTURE, true))
        d.onWarning(6_000, WarningType.LANE_DEPARTURE, false)
        assertEquals(EventType.LANE_DEPARTURE, d.onWarning(10_000, WarningType.LANE_DEPARTURE, true)!!.type)
    }

    @Test
    fun collisionAndProximityShareTheSameInterval() {
        val d = detector()
        d.onWarning(0, WarningType.COLLISION, true)
        d.onWarning(500, WarningType.COLLISION, false)
        assertNull(d.onWarning(1_000, WarningType.PROXIMITY, true))
    }

    @Test
    fun warningTurningOffProducesNoEvent() {
        val d = detector()
        d.onWarning(0, WarningType.LANE_DEPARTURE, true)
        assertNull(d.onWarning(1_000, WarningType.LANE_DEPARTURE, false))
    }

    @Test
    fun seatbeltWarningIsNotHandledHere() {
        assertNull(detector().onWarning(0, WarningType.SEATBELT, true))
    }

    @Test
    fun differentWarningKindsDoNotBlockEachOther() {
        val d = detector()
        d.onWarning(0, WarningType.LANE_DEPARTURE, true)
        assertEquals(EventType.COLLISION_RISK, d.onWarning(100, WarningType.COLLISION, true)!!.type)
    }
}
