package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.Direction4
import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T038. FR-005~FR-009: 정적 모드에서는 어떤 호출도 물리, 파티클, 번짐, 전환 효과를 만들지 않는다. */
class PetControllerStaticTest {
    private fun out(
        mood: Mood = Mood.IDLE,
        tier: MotionTier = MotionTier.STATIC,
        dir: Direction4? = null,
        turn: TurnState = TurnState.OFF,
    ) = PetOutput(mood, 0.6f, dir, tier, turn, 0)

    private fun controller() = PetController(random = Random(7)).also { it.setViewport(360f, 420f) }

    private fun ev(type: EventType, dir: Direction4?) = DrivingEvent(type, 0, 0.8f, dir)

    @Test
    fun startsStaticWithDefaultPose() {
        val c = controller()
        assertFalse(c.needsFrames)
        val s = c.renderState
        assertEquals(MotionMode.STATIC, s.motion)
        assertNull(s.physics)
        assertTrue(s.particles.isEmpty())
        val pose = s.staticPose
        assertNotNull(pose)
        assertEquals(PetAppearance.DEFAULT_BODY_COLOR, pose!!.bodyColor)
    }

    @Test
    fun stepDoesNothingInStatic() {
        val c = controller()
        c.applyOutput(out(Mood.HAPPY))
        val version = c.stateVersion
        repeat(120) { c.step(1f / 60f) }
        assertEquals(0, c.renderState.frame)
        assertEquals(version, c.stateVersion)
        assertNull(c.renderState.physics)
    }

    @Test
    fun eventsAreIgnoredInStatic() {
        val c = controller()
        c.applyOutput(out(Mood.SQUASHED, dir = Direction4.FRONT))
        val version = c.stateVersion
        for (t in listOf(EventType.HARD_BRAKE, EventType.HARD_CORNER, EventType.HARD_ACCEL)) {
            for (d in Direction4.entries) c.onEvent(ev(t, d))
        }
        assertEquals(version, c.stateVersion)
        assertFalse(c.needsFrames)
        assertNull(c.renderState.physics)
        assertTrue(c.renderState.particles.isEmpty())
    }

    @Test
    fun moodEntryEffectsAreIgnoredInStatic() {
        val c = controller()
        c.applyOutput(out(Mood.HAPPY))
        c.applyOutput(out(Mood.STARTLED))
        // 풀 모션으로 전환하면 물리는 새로 시작한다: 정적이던 동안의 점프와 충격은 남아 있지 않다.
        c.applyOutput(out(Mood.STARTLED, tier = MotionTier.FULL))
        val p = c.renderState.physics!!
        assertEquals(0f, p.oy, 0f)
        assertEquals(0f, p.vy, 0f)
        assertEquals(0f, p.vsx, 0f)
        assertTrue(p.rejumpAtSec.isNaN())
    }

    @Test
    fun oneShotBehavesLikeStatic() {
        val c = controller()
        c.applyOutput(out(Mood.HAPPY, tier = MotionTier.ONE_SHOT))
        assertFalse(c.needsFrames)
        assertNull(c.renderState.physics)
        assertEquals(MotionMode.STATIC, c.renderState.motion)
        val version = c.stateVersion
        c.onEvent(ev(EventType.HARD_BRAKE, Direction4.FRONT))
        c.step(0.016f)
        assertEquals(version, c.stateVersion)
        assertEquals(0, c.renderState.frame)
    }

    @Test
    fun moodChangeUpdatesPoseWithoutAdvancingFrame() {
        val c = controller()
        val before = c.stateVersion
        c.applyOutput(out(Mood.HAPPY))
        assertEquals(before + 1, c.stateVersion)
        assertEquals(0, c.renderState.frame)
        assertEquals(
            mixColor(PetAppearance.DEFAULT_BODY_COLOR, Color(0xFF3DDC84), 0.9f),
            c.renderState.staticPose!!.bodyColor,
        )
        c.applyOutput(out(Mood.STARTLED))
        assertEquals(0, c.renderState.frame)
        assertEquals(
            mixColor(PetAppearance.DEFAULT_BODY_COLOR, Color(0xFFFF5A5A), 0.85f),
            c.renderState.staticPose!!.bodyColor,
        )
    }

    @Test
    fun directionAndTurnSignalChangeThePose() {
        val c = controller()
        c.applyOutput(out(Mood.SQUASHED, dir = Direction4.RIGHT, turn = TurnState.LEFT))
        val pose = c.renderState.staticPose!!
        assertEquals(10f, pose.tiltDeg, 0f)
        assertEquals(Side.LEFT, pose.closedEye)
        assertEquals(Side.LEFT, pose.amberBrow)
    }

    @Test
    fun appearanceChangeUpdatesPoseColor() {
        val c = controller()
        c.setAppearance(PetAppearance(bodyColor = Color(0xFFD03030)))
        assertEquals(Color(0xFFD03030), c.renderState.staticPose!!.bodyColor)
    }

    @Test
    fun sameOutputDoesNotInvalidate() {
        val c = controller()
        c.applyOutput(out(Mood.HAPPY))
        val version = c.stateVersion
        c.applyOutput(out(Mood.HAPPY))
        c.applyOutput(out(Mood.HAPPY).copy(atMs = 5000, intensity = 0.1f))
        assertEquals(version, c.stateVersion)
    }

    @Test
    fun sameViewportAndAppearanceDoNotInvalidate() {
        val c = controller()
        val version = c.stateVersion
        c.setViewport(360f, 420f)
        c.setAppearance(PetAppearance())
        assertEquals(version, c.stateVersion)
        c.setViewport(400f, 420f)
        assertNotEquals(version, c.stateVersion)
    }

    @Test
    fun staticPoseIsAbsentInFullMode() {
        val c = controller()
        c.applyOutput(out(tier = MotionTier.FULL))
        assertNull(c.renderState.staticPose)
        assertNotNull(c.renderState.physics)
    }

    @Test
    fun deterministicForSameCalls() {
        fun run(): List<Any?> {
            val c = controller()
            c.applyOutput(out(Mood.SQUASHED, dir = Direction4.LEFT, turn = TurnState.RIGHT))
            c.onEvent(ev(EventType.HARD_CORNER, Direction4.LEFT))
            c.step(0.016f)
            return c.renderState.staticPose!!.let { listOf(it, c.stateVersion, c.renderState.frame) }
        }
        assertEquals(run(), run())
    }
}
