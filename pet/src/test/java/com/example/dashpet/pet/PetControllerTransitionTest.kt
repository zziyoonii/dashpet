package com.example.dashpet.pet

import com.example.dashpet.engine.Direction4
import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T044. contracts/pet-view-api.md 규칙 2, 3: 정적 전환은 같은 호출 안에서 즉시, 풀 전환은 새로 시작. */
class PetControllerTransitionTest {
    private fun out(mood: Mood, tier: MotionTier, dir: Direction4? = null) =
        PetOutput(mood, 0.7f, dir, tier, TurnState.OFF, 0)

    private fun controller(seed: Int = 3) = PetController(random = Random(seed)).also { it.setViewport(360f, 420f) }

    private fun PetController.advance(sec: Float) = repeat((sec / (1f / 60f)).toInt()) { step(1f / 60f) }

    /** 급제동 번짐이 진행 중인 풀 모션 상태. */
    private fun PetController.smudging(): PetController {
        applyOutput(out(Mood.SQUASHED, MotionTier.FULL, Direction4.FRONT))
        onEvent(DrivingEvent(EventType.HARD_BRAKE, 0, 1f, Direction4.FRONT))
        advance(0.6f)
        return this
    }

    @Test
    fun smudgeInProgressPreconditionHolds() {
        val p = controller().smudging().renderState.physics!!
        assertTrue(p.press > 0f || p.onGlass)
        assertNotNull(p.smudge ?: p.lon)
    }

    @Test
    fun fullToStaticClearsEverythingInTheSameCall() {
        val c = controller()
        c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
        c.applyOutput(out(Mood.HAPPY, MotionTier.FULL)) // 반짝임 파티클
        c.advance(0.8f)
        assertTrue(c.renderState.particles.isNotEmpty())
        c.applyOutput(out(Mood.SQUASHED, MotionTier.FULL, Direction4.FRONT))
        c.onEvent(DrivingEvent(EventType.HARD_BRAKE, 0, 1f, Direction4.FRONT))
        c.advance(0.6f)

        c.applyOutput(out(Mood.SQUASHED, MotionTier.STATIC, Direction4.FRONT))

        assertFalse(c.needsFrames)
        val s = c.renderState
        assertNull(s.physics)
        assertTrue(s.particles.isEmpty())
        assertNotNull(s.staticPose)
        val frame = s.frame
        c.step(0.016f)
        assertEquals(frame, c.renderState.frame)
    }

    @Test
    fun staticToFullStartsFreshPhysics() {
        val c = controller().smudging()
        c.applyOutput(out(Mood.IDLE, MotionTier.STATIC))
        c.onEvent(DrivingEvent(EventType.HARD_CORNER, 0, 1f, Direction4.LEFT)) // 정적 동안의 이벤트
        c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
        assertEquals(freshState(), c.renderState.physics!!.toList())
        assertTrue(c.renderState.particles.isEmpty())
    }

    @Test
    fun rejumpScheduleDoesNotSurviveStaticDetour() {
        val c = controller()
        c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
        c.applyOutput(out(Mood.HAPPY, MotionTier.FULL)) // 점프와 재점프 예약
        assertFalse(c.renderState.physics!!.rejumpAtSec.isNaN())
        c.applyOutput(out(Mood.HAPPY, MotionTier.STATIC))
        c.applyOutput(out(Mood.HAPPY, MotionTier.FULL))
        c.advance(1.5f)
        val p = c.renderState.physics!!
        assertTrue(p.rejumpAtSec.isNaN())
        assertEquals(0f, p.oy, 0.5f) // 점프도 재점프도 없이 바닥
    }

    @Test
    fun repeatedFullStaticFullDoesNotLeakState() {
        val c = controller()
        repeat(4) {
            c.smudging()
            c.applyOutput(out(Mood.IDLE, MotionTier.STATIC))
            assertNull(c.renderState.physics)
            assertTrue(c.renderState.particles.isEmpty())
            c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
            val p = c.renderState.physics!!
            assertEquals(freshState(), p.toList())
            assertFalse(p.onGlass)
            assertFalse(p.onSeat)
            assertNull(p.smudge)
            assertTrue(p.rejumpAtSec.isNaN())
        }
    }

    @Test
    fun reducedMotionSurvivesTransitions() {
        val c = controller()
        c.setReducedMotion(true)
        c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
        c.applyOutput(out(Mood.IDLE, MotionTier.STATIC))
        c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
        assertTrue(c.renderState.reducedMotion)
        assertTrue(c.renderState.physics!!.softBody.reducedMotion)
    }

    private fun freshState(): List<Float> {
        val c = controller()
        c.applyOutput(out(Mood.IDLE, MotionTier.FULL))
        return c.renderState.physics!!.toList()
    }
}
