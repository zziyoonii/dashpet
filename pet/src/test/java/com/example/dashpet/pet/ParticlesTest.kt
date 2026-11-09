package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T023. R9 파티클: 상한 60, 프레임 시간 24ms 초과 30프레임 연속이면 생성 중단, 회복되면 재개. */
class ParticlesTest {
    private val config = PetPhysicsConfig()
    private val geometry = PetGeometry(cx = 180f, cy = 250f, rx = 80f, ry = 70f, r = 75f, rf = 75f, lean = 0f, e = 0f, groundX = 180f, groundRx = 80f, groundY = 336f)

    private fun happyController(seed: Int = 7, reduced: Boolean = false): PetController {
        val c = PetController(random = Random(seed))
        c.setViewport(360f, 420f)
        c.setReducedMotion(reduced)
        c.applyOutput(PetOutput(Mood.HAPPY, 0.5f, null, MotionTier.FULL, TurnState.OFF, 0))
        return c
    }

    @Test
    fun fullMotionHappySpawnsSparks() {
        val c = happyController()
        repeat(120) { c.step(1f / 60f) }
        val parts = c.renderState.particles
        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.type == ParticleType.SPARK })
    }

    @Test
    fun reducedMotionSpawnsNothing() {
        val c = happyController(reduced = true)
        repeat(300) { c.step(1f / 60f) }
        assertTrue(c.renderState.particles.isEmpty())
    }

    @Test
    fun idleSpawnsNoSparks() {
        val c = PetController(random = Random(1))
        c.setViewport(360f, 420f)
        c.applyOutput(PetOutput(Mood.IDLE, 0f, null, MotionTier.FULL, TurnState.OFF, 0))
        repeat(300) { c.step(1f / 60f) }
        assertTrue(c.renderState.particles.isEmpty())
    }

    @Test
    fun poolNeverExceedsSixty() {
        val p = Particles(config)
        var accepted = 0
        repeat(200) { if (p.spawn(ParticleType.SPARK, 0f, 0f, 0f, 0f, 10f, 4f)) accepted++ }
        assertEquals(60, p.count)
        assertEquals(60, accepted)
        // 생성기를 오래 돌려도 상한을 넘지 않는다.
        val q = Particles(config)
        val r = Random(3)
        repeat(2000) { q.emitHappySparks(1f / 30f, geometry, r); assertTrue(q.count <= 60) }
    }

    @Test
    fun throttlesAfterThirtyConsecutiveSlowFramesAndResumesOnRecovery() {
        val p = Particles(config)
        repeat(29) { p.reportFrameTime(0.030f) }
        assertFalse(p.throttled)
        p.reportFrameTime(0.030f) // 30번째
        assertTrue(p.throttled)
        val r = Random(1)
        repeat(200) { p.emitHappySparks(1f / 30f, geometry, r) }
        p.emitGlassBurst(360f, 420f, Color.Blue, r)
        assertEquals(0, p.count)
        p.reportFrameTime(0.016f) // 회복
        assertFalse(p.throttled)
        repeat(200) { p.emitHappySparks(1f / 30f, geometry, r) }
        assertTrue(p.count > 0)
    }

    @Test
    fun fastFrameInTheMiddleResetsTheCount() {
        val p = Particles(config)
        repeat(20) { p.reportFrameTime(0.030f) }
        p.reportFrameTime(0.010f)
        repeat(20) { p.reportFrameTime(0.030f) }
        assertFalse(p.throttled) // 연속이 아니므로
    }

    @Test
    fun exactlyAtThresholdIsNotSlow() {
        val p = Particles(config)
        repeat(100) { p.reportFrameTime(0.024f) }
        assertFalse(p.throttled)
    }

    @Test
    fun particlesAreRemovedWhenLifeEnds() {
        val p = Particles(config)
        p.spawn(ParticleType.SPARK, 10f, 10f, 0f, 0f, 0.5f, 4f)
        p.spawn(ParticleType.SPARK, 10f, 10f, 0f, 0f, 1.0f, 4f)
        repeat(20) { p.update(1f / 30f, 420f) } // 0.667 초
        assertEquals(1, p.count)
        repeat(20) { p.update(1f / 30f, 420f) }
        assertEquals(0, p.count)
    }

    @Test
    fun particlesBelowTheViewAreRemoved() {
        val p = Particles(config)
        p.spawn(ParticleType.GLASS_DROP, 10f, 430f, 0f, 100f, 5f, 4f)
        p.update(1f / 30f, 400f)
        assertEquals(0, p.count)
    }

    @Test
    fun glassBurstSpawnsTwelveDropsOnTheGlassEdge() {
        val p = Particles(config)
        p.emitGlassBurst(360f, 420f, Color.Red, Random(5))
        assertEquals(12, p.count)
        assertTrue(p.items.all { it.type == ParticleType.GLASS_DROP && it.color == Color.Red })
    }

    @Test
    fun sameSeedGivesSameParticles() {
        fun run(): List<List<Float>> {
            val c = happyController(seed = 11)
            repeat(200) { c.step(1f / 60f) }
            return c.renderState.particles.map { listOf(it.x, it.y, it.vx, it.vy, it.life, it.size) }
        }
        assertEquals(run(), run())
        assertTrue(run().isNotEmpty())
    }

    @Test
    fun controllerStopsSpawningWhenFramesAreSlowAndResumes() {
        val c = happyController()
        repeat(30) { c.step(0.030f) } // 30프레임 연속 30ms
        val before = c.renderState.particles.size
        repeat(60) { c.step(0.030f) }
        // 생성 중단: 기존 파티클은 수명이 끝나며 줄기만 한다(새로 생기지 않는다).
        assertTrue(c.renderState.particles.size <= before)
        repeat(90) { c.step(0.030f) }
        assertEquals(0, c.renderState.particles.size)
        repeat(200) { c.step(0.010f) } // 회복
        assertTrue(c.renderState.particles.isNotEmpty())
    }
}
