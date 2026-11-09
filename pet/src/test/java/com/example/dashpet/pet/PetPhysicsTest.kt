package com.example.dashpet.pet

import com.example.dashpet.engine.Mood
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T022. research.md R6 물리 수식. 고정 dt 와 시드 난수. */
class PetPhysicsTest {
    private val dt = 1f / 60f
    private fun physics(w: Float = 360f, h: Float = 420f) =
        PetPhysics(PetPhysicsConfig(), Random(1)).also { it.setViewport(w, h) }

    private fun PetPhysics.run(sec: Float, mood: Mood = Mood.IDLE, onStep: (PetPhysics) -> Unit = {}) {
        val n = (sec / dt).toInt()
        repeat(n) { step(dt, mood); onStep(this) }
    }

    // ---- 좌우 ----

    @Test
    fun cornerRightPushesTowardRightWall() {
        val p = physics()
        p.corner(+1, 0.7f)
        assertEquals(300f, p.state.vx, 1e-3f) // vx += dir * 300
        p.run(0.3f)
        assertTrue(p.state.ox > 0f)
        val q = physics()
        q.corner(-1, 0.7f)
        q.run(0.3f)
        assertTrue(q.state.ox < 0f)
    }

    @Test
    fun cornerForceAndDurationFollowStrength() {
        val p = physics()
        p.corner(+1, 0.5f)
        val lat = p.state.lat!!
        assertEquals(2600f + 3400f * 0.5f, lat.force, 1e-3f)
        assertEquals(0.5f + 0.8f * 0.5f, lat.untilSec - p.state.clockSec, 1e-5f)
        // 지속 시간 동안은 자세를 유지하고, 지나면 해제된다.
        p.run(0.85f)
        assertNotNull(p.state.lat)
        p.run(0.1f)
        assertNull(p.state.lat)
    }

    @Test
    fun strongerCornerPenetratesMoreAndLastsLonger() {
        fun maxPen(s: Float): Float {
            val p = physics(); p.corner(+1, s)
            var m = 0f
            p.run(2f) { m = max(m, it.state.pen) }
            return m
        }
        assertTrue(maxPen(1f) > maxPen(0.3f) + 1f)
        val a = physics().also { it.corner(1, 0.3f) }.state.lat!!
        val b = physics().also { it.corner(1, 1f) }.state.lat!!
        assertTrue(b.force > a.force)
        assertTrue(b.untilSec > a.untilSec)
    }

    @Test
    fun wallContactDistanceMatchesFormula() {
        val p = physics(360f, 420f)
        val r = min(360f, 420f) * 0.21f
        val rxMax = r * 1f * 1.05f * 1.13f
        assertEquals(max(20f, 360f / 2 - 12f - rxMax), p.contactDistance(), 1e-3f)
        // 아주 좁으면 최소 20.
        assertEquals(20f, physics(100f, 100f).contactDistance(), 1e-3f)
    }

    @Test
    fun bodyStaysInsideWallLimit() {
        val p = physics()
        p.corner(+1, 1f)
        p.run(2f) { assertTrue(abs(it.state.ox) <= it.contactDistance() + it.state.pen + 3f) }
    }

    @Test
    fun cornerReleaseKicksBackAgainstTheWall() {
        val p = physics()
        val s = 0.6f
        p.corner(+1, s)
        val dtSmall = 0.001f
        while (p.state.lat != null) p.step(dtSmall, Mood.IDLE)
        // 해제된 바로 그 step: vx = -dir * (170 + 200 s) (이후 스프링으로 약간 변함).
        assertEquals(-(170f + 200f * s), p.state.vx, 12f)
        assertTrue(p.state.vsx > 1f) // vsx += 2.4
    }

    // ---- 앞뒤 ----

    @Test
    fun hardBrakeReachesGlassAndPresses() {
        val p = physics()
        p.surge(+1, 1f)
        val lon = p.state.lon!!
        assertEquals(0.45f + 0.75f * 1f, lon.untilSec - p.state.clockSec, 1e-5f)
        var bursts = 0
        var maxZ = 0f
        var maxPress = 0f
        p.run(1.0f) {
            if (it.state.splatBurst) { bursts++; it.state.splatBurst = false }
            maxZ = max(maxZ, it.state.z); maxPress = max(maxPress, it.state.press)
        }
        assertEquals(1f, maxZ, 1e-6f)
        assertTrue(maxPress > 0.7f)
        assertEquals("glass impact must happen once", 1, bursts)
    }

    @Test
    fun brakeReleaseLeavesSmudgeThatFadesAfterOnePointEightSeconds() {
        val p = physics()
        p.surge(+1, 1f)
        p.run(1.3f) // 지속 1.2초 뒤 해제
        val sm = p.state.smudge
        assertNotNull(sm)
        assertTrue(p.state.vz < 0f) // 해제 시 vz = -(2.2 + 1.6 s) 로 되돌아감
        p.run(1.4f)
        assertNotNull(p.state.smudge) // 해제 뒤 약 1.5초
        p.run(0.5f)
        assertNull(p.state.smudge)
    }

    @Test
    fun accelPushesBackAndLeans() {
        val p = physics()
        p.surge(-1, 1f)
        assertEquals(0.40f + 0.65f, p.state.lon!!.untilSec - p.state.clockSec, 1e-5f)
        var minZ = 0f
        p.run(0.9f) { minZ = min(minZ, it.state.z) }
        assertEquals(-0.75f, minZ, 1e-6f)
        val g = petGeometry(p.state, 360f, 420f, PetPhysicsConfig())
        assertTrue(g.lean > 0.9f)
        // 낮게 꺼진다: 스케일 목표 sx 1.13, sy 0.85
        p.run(0.2f)
        assertTrue(p.state.sy < 0.95f)
    }

    // ---- 점프와 스케일 ----

    @Test
    fun jumpFallsUnderGravityAndLandingSquashesScale() {
        val p = physics()
        p.jump(650f)
        var peak = 0f
        var minSy = 10f
        var landed = false
        p.run(1f) {
            peak = max(peak, it.state.oy)
            minSy = min(minSy, it.state.sy)
            if (peak > 0f && it.state.oy == 0f) landed = true
        }
        assertEquals(650f * 650f / (2 * 2600f), peak, 6f) // v^2 / 2g
        assertTrue(landed)
        assertTrue("landing impact should squash sy", minSy < 0.95f)
    }

    @Test
    fun scaleIsClampedToLimits() {
        val p = physics()
        p.state.vsx = 1e5f; p.state.vsy = 1e5f
        p.step(dt, Mood.IDLE)
        assertEquals(1.7f, p.state.sx, 1e-6f)
        assertEquals(1.6f, p.state.sy, 1e-6f)
        p.state.vsx = -1e5f; p.state.vsy = -1e5f
        p.step(dt, Mood.IDLE)
        assertEquals(0.55f, p.state.sx, 1e-6f)
        assertEquals(0.5f, p.state.sy, 1e-6f)
    }

    @Test
    fun idleBreathesWithSinTwoPointTwo() {
        val p = physics()
        var maxSy = 0f; var minSy = 9f
        p.run(3f) { maxSy = max(maxSy, it.state.sy); minSy = min(minSy, it.state.sy) }
        assertTrue(maxSy > 1.01f && maxSy < 1.04f)
        assertTrue(minSy < 0.99f && minSy > 0.96f)
    }

    @Test
    fun startledMoodTargetsSmallScaleAndDizzyStaggers() {
        val s = physics()
        s.run(2f, Mood.STARTLED)
        assertEquals(0.86f, s.state.sx, 0.02f)
        assertEquals(0.9f, s.state.sy, 0.02f)
        val d = physics()
        var maxOx = 0f
        d.run(3f, Mood.DIZZY) { maxOx = max(maxOx, abs(it.state.ox)) }
        assertTrue(maxOx > 360f.coerceAtMost(420f) * 0.21f * 0.1f)
    }

    @Test
    fun reducedMotionDampsHarder() {
        fun swing(reduced: Boolean): Float {
            val p = physics(); p.reducedMotion = reduced
            p.state.vsx = 5f
            var m = 0f
            p.run(0.5f) { m = max(m, abs(it.state.sx - 1f)) }
            return m
        }
        assertTrue(swing(true) < swing(false))
    }

    // ---- 범위 ----

    @Test
    fun strengthIsClampedToZeroOne() {
        val p = physics()
        p.corner(+1, 5f)
        assertEquals(1f, p.state.lat!!.s, 0f)
        p.corner(+1, -3f)
        assertEquals(0f, p.state.lat!!.s, 0f)
        p.corner(+1, Float.NaN)
        assertEquals(0f, p.state.lat!!.s, 0f)
        p.surge(-1, 9f)
        assertEquals(1f, p.state.lon!!.s, 0f)
    }

    @Test
    fun startleSetsUpwardVelocityAndScaleImpulse() {
        val p = physics()
        p.startle()
        assertEquals(420f, p.state.vy, 0f)
        assertEquals(-1.5f, p.state.vsx, 1e-6f)
        assertEquals(-1f, p.state.vsy, 1e-6f)
    }

    @Test
    fun sameSeedAndCallsGiveSameState() {
        fun run(): List<Float> {
            val p = physics()
            p.corner(1, 0.8f); p.surge(1, 1f); p.jump(650f)
            p.run(3f, Mood.IDLE)
            return p.state.toList()
        }
        assertEquals(run(), run())
    }

    @Test
    fun blinkIsScheduledEvery2point6To5point2Seconds() {
        val p = physics()
        val starts = mutableListOf<Float>()
        var last = -1f
        p.run(30f) { if (it.state.blinkStartSec != last) { last = it.state.blinkStartSec; starts += last } }
        assertTrue(starts.size >= 6)
        starts.zipWithNext().drop(1).forEach { (a, b) -> assertTrue(b - a in 2.55f..5.3f) }
        assertFalse(starts.isEmpty())
    }
}
