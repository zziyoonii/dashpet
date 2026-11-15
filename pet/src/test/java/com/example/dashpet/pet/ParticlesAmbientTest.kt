package com.example.dashpet.pet

import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T063. 배경 감정 파티클: 더위 땀, 비 빗줄기, 조용 Z, 추위 입김. 정적과 모션 줄이기에서는 만들지 않고 상한은 60. */
class ParticlesAmbientTest {
    private fun out(tier: MotionTier = MotionTier.FULL) = PetOutput(Mood.IDLE, 0.5f, null, tier, TurnState.OFF, 0)

    private fun full(ambient: AmbientMood?): PetController = PetController(random = Random(3)).also {
        it.setViewport(360f, 420f)
        it.applyOutput(out())
        it.setAmbient(ambient)
    }

    /** [sec] 초 동안 60Hz 로 진행하며 나온 종류를 모은다. */
    private fun PetController.run(sec: Float): Set<ParticleType> {
        val seen = mutableSetOf<ParticleType>()
        repeat((sec * 60f).toInt()) {
            step(1f / 60f)
            renderState.particles.forEach { seen += it.type }
        }
        return seen
    }

    @Test
    fun hotMakesSweatOnly() {
        assertEquals(setOf(ParticleType.SWEAT), full(AmbientMood.HOT).run(20f))
    }

    @Test
    fun rainMakesRainDropsOnly() {
        assertEquals(setOf(ParticleType.RAIN), full(AmbientMood.RAIN).run(5f))
    }

    @Test
    fun quietMakesZOnly() {
        assertEquals(setOf(ParticleType.Z), full(AmbientMood.QUIET).run(20f))
    }

    @Test
    fun coldMakesBreathPuffsOnly() {
        assertEquals(setOf(ParticleType.PUFF), full(AmbientMood.COLD).run(20f))
    }

    @Test
    fun noAmbientMeansNoAmbientParticles() {
        assertTrue(full(null).run(20f).isEmpty())
    }

    @Test
    fun rainFallsAboutFiftyFivePerSecondWhileParked() {
        val p = Particles()
        val g = petGeometry(PetPhysicsState(SoftBody(PetPhysicsConfig())), 360f, 420f, PetPhysicsConfig())
        val random = Random(5)
        repeat(60) { p.emitAmbient(AmbientMood.RAIN, 1f / 60f, g, 360f, random) }
        // 상한 60 때문에 55 개를 넘어도 60 에서 멈춘다. 기대값 55 에서 크게 벗어나지 않아야 한다
        assertTrue("${p.count}", p.count in 35..60)
    }

    @Test
    fun poolNeverExceedsSixty() {
        val c = full(AmbientMood.RAIN)
        repeat(600) {
            c.step(1f / 60f)
            assertTrue(c.renderState.particles.size <= 60)
        }
    }

    @Test
    fun staticModeMakesNoParticles() {
        val c = PetController(random = Random(3))
        c.applyOutput(out(MotionTier.STATIC))
        c.setAmbient(AmbientMood.RAIN)
        repeat(60) { c.step(1f / 60f) }
        assertTrue(c.renderState.particles.isEmpty())
    }

    @Test
    fun reducedMotionMakesNoParticles() {
        val c = full(AmbientMood.RAIN).also { it.setReducedMotion(true) }
        assertTrue(c.run(5f).isEmpty())
    }

    @Test
    fun stopsWhileThrottledForSlowFrames() {
        val p = Particles()
        val config = PetPhysicsConfig()
        repeat(config.particleThrottleFrames) { p.reportFrameTime(0.05f) }
        assertTrue(p.throttled)
        val g = petGeometry(PetPhysicsState(SoftBody(config)), 360f, 420f, config)
        repeat(60) { p.emitAmbient(AmbientMood.HOT, 1f / 60f, g, 360f, Random(1)) }
        assertEquals(0, p.count)
    }
}
