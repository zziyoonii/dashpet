package com.example.dashpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T043. research.md R4, R10, R11. 정책은 순수 Kotlin 이고 Android 를 읽지 않는다. */
class MotionPolicyTest {
    private val config = PetPhysicsConfig()

    @Test
    fun loopRunsOnlyForFullMotionWhileStarted() {
        assertTrue(MotionPolicy.shouldRunLoop(MotionMode.FULL, lifecycleStarted = true))
        assertFalse(MotionPolicy.shouldRunLoop(MotionMode.FULL, lifecycleStarted = false))
        assertFalse(MotionPolicy.shouldRunLoop(MotionMode.STATIC, lifecycleStarted = true))
        assertFalse(MotionPolicy.shouldRunLoop(MotionMode.STATIC, lifecycleStarted = false))
    }

    @Test
    fun firstFrameAfterStartOrResumeHasZeroDt() {
        assertEquals(0f, MotionPolicy.frameDt(isFirstFrame = true, elapsedSec = 5f), 0f)
        assertEquals(0.016f, MotionPolicy.frameDt(isFirstFrame = false, elapsedSec = 0.016f), 0f)
    }

    @Test
    fun invalidElapsedBecomesZero() {
        assertEquals(0f, MotionPolicy.frameDt(false, -1f), 0f)
        assertEquals(0f, MotionPolicy.frameDt(false, Float.NaN), 0f)
        assertEquals(0f, MotionPolicy.frameDt(false, 0f), 0f)
    }

    @Test
    fun dtIsClampedToOneThirtieth() {
        val max = 1f / 30f
        assertEquals(max, MotionPolicy.clampDt(1f, config), 0f)
        assertEquals(max, MotionPolicy.clampDt(max, config), 0f)
        assertEquals(0.01f, MotionPolicy.clampDt(0.01f, config), 0f)
        assertEquals(0f, MotionPolicy.clampDt(0f, config), 0f)
        assertEquals(0f, MotionPolicy.clampDt(-0.5f, config), 0f)
        assertEquals(0f, MotionPolicy.clampDt(Float.NaN, config), 0f)
    }

    @Test
    fun normalCorrectionKeepsFullMotion() {
        val c = MotionPolicy.correction(reduced = false, config = config)
        assertEquals(5f, c.softBodyC, 0f)
        assertEquals(9f, c.scaleC, 0f)
        assertEquals(1f, c.kickFactor, 0f)
        assertTrue(c.particles)
        assertTrue(c.speedLines)
        assertTrue(c.bodyWobble)
    }

    @Test
    fun reducedMotionCorrection() {
        val c = MotionPolicy.correction(reduced = true, config = config)
        assertEquals(16f, c.softBodyC, 0f)
        assertEquals(18f, c.scaleC, 0f)
        assertEquals(0.4f, c.kickFactor, 0f)
        assertFalse(c.particles)
        assertFalse(c.speedLines)
        assertFalse(c.bodyWobble)
    }
}
