package com.example.dashpet.pet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T021. research.md R6 소프트바디. */
class SoftBodyTest {
    private val dt = 1f / 60f

    private fun energy(b: SoftBody): Double =
        b.d.sumOf { (it * it).toDouble() } + b.u.sumOf { (it * it).toDouble() }

    @Test
    fun hasTwentyTwoPointsStartingAtTop() {
        val b = SoftBody()
        assertEquals(22, b.n)
        assertEquals(22, b.d.size)
        assertEquals(22, b.u.size)
        assertEquals(22, b.angles.size)
        assertEquals((-PI / 2).toFloat(), b.angles[0], 1e-6f)
        assertEquals((22f / 22f * 2 * PI - PI / 2 - 2 * PI / 22).toFloat(), b.angles[21], 1e-5f)
    }

    @Test
    fun kickWobblesThenDecays() {
        val b = SoftBody()
        b.kick(2, 1f)
        var peak = 0.0
        repeat(12) { b.step(dt); peak = max(peak, b.d.maxOf { abs(it) }.toDouble()) }
        assertTrue("kick should move the points", peak > 0.02)
        val mid = energy(b)
        repeat(180) { b.step(dt) }
        assertTrue("wobble should decay", energy(b) < mid * 0.01)
        assertTrue(energy(b) < 1e-3)
    }

    @Test
    fun displacementIsClamped() {
        val b = SoftBody()
        repeat(300) {
            b.kick(0, 80f)
            b.step(1f / 30f)
            assertTrue(b.d.all { it >= -0.32f - 1e-6f && it <= 0.42f + 1e-6f })
        }
        assertEquals(0.42f, b.d.max(), 1e-6f)
        val c = SoftBody()
        repeat(300) { c.kick(0, -80f); c.step(1f / 30f) }
        assertEquals(-0.32f, c.d.min(), 1e-6f)
    }

    @Test
    fun reducedMotionSettlesFasterAndKicksSmaller() {
        val normal = SoftBody()
        val reduced = SoftBody().also { it.reducedMotion = true }
        normal.kick(2, 1f)
        reduced.kick(2, 1f)
        for (i in 0 until normal.n) assertEquals(0.4f * normal.u[i], reduced.u[i], 1e-6f)
        repeat(30) { normal.step(dt); reduced.step(dt) }
        assertTrue(energy(reduced) < energy(normal))
    }

    @Test
    fun kickModeOnePushesTowardPhase() {
        val b = SoftBody()
        b.kick(1, 1f, 0f)
        // 각도 0(오른쪽)에 가장 가까운 점은 양, 반대쪽(π)은 음의 속도.
        val right = b.angles.indices.minByOrNull { abs(b.angles[it]) }!!
        val left = b.angles.indices.minByOrNull { abs(abs(b.angles[it]) - PI.toFloat()) }!!
        assertTrue(b.u[right] > 0.9f)
        assertTrue(b.u[left] < -0.9f)
    }

    @Test
    fun sameInputGivesSameResult() {
        fun run(): FloatArray {
            val b = SoftBody()
            b.kick(2, 0.5f); b.kick(3, -0.3f, 1f)
            repeat(50) { b.step(dt) }
            return b.d.copyOf()
        }
        assertArrayEquals(run(), run(), 0f)
    }
}
