package com.example.dashpet.pet

import org.junit.Assert.assertEquals
import org.junit.Test

/** research.md R6: 시안 상수를 값으로 고정한다. 바꾸려면 시안과 R6 을 먼저 확인한다. */
class PetPhysicsConfigTest {
    private val c = PetPhysicsConfig()

    @Test
    fun softBodyConstants() {
        assertEquals(22, c.softBodyPoints)
        assertEquals(110f, c.softBodyK, 0f)
        assertEquals(5f, c.softBodyC, 0f)
        assertEquals(16f, c.softBodyCReduced, 0f)
        assertEquals(240f, c.softBodyKn, 0f)
        assertEquals(3, c.softBodySubSteps)
        assertEquals(-0.32f, c.displacementMin, 0f)
        assertEquals(0.42f, c.displacementMax, 0f)
        assertEquals(0.4f, c.kickAmpReducedFactor, 0f)
    }

    @Test
    fun scaleSpringConstants() {
        assertEquals(230f, c.scaleK, 0f)
        assertEquals(9f, c.scaleC, 0f)
        assertEquals(18f, c.scaleCReduced, 0f)
        assertEquals(0.55f, c.scaleXMin, 0f)
        assertEquals(1.7f, c.scaleXMax, 0f)
        assertEquals(0.5f, c.scaleYMin, 0f)
        assertEquals(1.6f, c.scaleYMax, 0f)
    }

    @Test
    fun lateralConstants() {
        assertEquals(2600f, c.cornerForceBase, 0f)
        assertEquals(3400f, c.cornerForcePerStrength, 0f)
        assertEquals(500f, c.cornerDurationBaseMs, 0f)
        assertEquals(800f, c.cornerDurationPerStrengthMs, 0f)
    }

    @Test
    fun longitudinalDurations() {
        assertEquals(450f, c.brakeDurationBaseMs, 0f)
        assertEquals(750f, c.brakeDurationPerStrengthMs, 0f)
        assertEquals(400f, c.accelDurationBaseMs, 0f)
        assertEquals(650f, c.accelDurationPerStrengthMs, 0f)
    }

    @Test
    fun jumpAndParticles() {
        assertEquals(2600f, c.gravity, 0f)
        assertEquals(820f, c.jumpVelocityLively, 0f)
        assertEquals(650f, c.jumpVelocityDefault, 0f)
        assertEquals(60, c.maxParticles)
    }

    @Test
    fun jumpVelocityDependsOnPersonality() {
        assertEquals(820f, c.jumpVelocity(Personality.LIVELY), 0f)
        assertEquals(650f, c.jumpVelocity(Personality.STURDY), 0f)
        assertEquals(650f, c.jumpVelocity(Personality.CUTE), 0f)
    }

    @Test
    fun draftValuesAreMarkedAndHeldHere() { // [확인 필요] 초안 값. 시안 근거 없음(R8, R9)
        assertEquals(10f, c.staticTiltDeg, 0f)
        assertEquals(1.08f, c.staticFrontScale, 0f)
        assertEquals(0.9f, c.staticBackScale, 0f)
        assertEquals(24f, c.particleThrottleFrameMs, 0f)
        assertEquals(30, c.particleThrottleFrames)
    }
}
