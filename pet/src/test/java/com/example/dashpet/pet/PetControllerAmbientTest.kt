package com.example.dashpet.pet

import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T062. 배경 감정 4종(FR-019): 풀 모션에서만 몸 모양, 눈 깜빡임, 떨림에 반영되고 엔진 감정이 우선한다. */
class PetControllerAmbientTest {
    private fun out(mood: Mood = Mood.IDLE, tier: MotionTier = MotionTier.FULL) =
        PetOutput(mood, 0.5f, null, tier, TurnState.OFF, 0)

    private fun full(mood: Mood = Mood.IDLE): PetController = PetController(random = Random(1)).also {
        it.setViewport(360f, 420f)
        it.applyOutput(out(mood))
    }

    /**
     * 용수철이 가라앉도록 진행한다(60Hz 로 [frames] 프레임). 1/30 초로 진행하면 바닥에 놓인 채로도 프레임마다 낙하 충격
     * (`vy = -2600 * dt < -60`)이 생겨 목표값에서 벗어나므로, 실제 앱의 프레임 간격(약 1/60 초)을 쓴다.
     */
    private fun PetController.settle(frames: Int = 300) = repeat(frames) { step(1f / 60f) }

    private val PetController.phys get() = renderState.physics!!

    @Test
    fun hotMeltsLowAndWide() {
        val c = full().also { it.setAmbient(AmbientMood.HOT) }
        c.settle()
        assertEquals(1.24f, c.phys.sx, 0.02f)
        assertEquals(0.74f, c.phys.sy, 0.02f)
    }

    @Test
    fun coldShrinksSlightly() {
        val c = full().also { it.setAmbient(AmbientMood.COLD) }
        c.settle()
        assertEquals(0.94f, c.phys.sx, 0.02f)
        assertEquals(0.95f, c.phys.sy, 0.02f)
    }

    @Test
    fun quietIsWideAndLowWithABreath() {
        val c = full().also { it.setAmbient(AmbientMood.QUIET) }
        c.settle()
        val t = c.phys.clockSec
        assertEquals(1.06f, c.phys.sx, 0.02f)
        assertEquals(0.9f + 0.02f * sin(t * 2.2f), c.phys.sy, 0.03f)
    }

    @Test
    fun rainKeepsTheNormalBreathingShape() {
        val c = full().also { it.setAmbient(AmbientMood.RAIN) }
        c.settle()
        assertEquals(1f, c.phys.sx, 0.03f)
        assertEquals(1f, c.phys.sy, 0.04f)
    }

    @Test
    fun startledScaleIsNotChangedByAmbient() {
        // 깜짝 0.86/0.90 는 엔진 감정의 몫이고 배경 감정이 있어도 같다
        val c = full(Mood.STARTLED).also { it.setAmbient(AmbientMood.HOT) }
        c.settle()
        assertEquals(0.86f, c.phys.sx, 0.02f)
        assertEquals(0.90f, c.phys.sy, 0.02f)
    }

    @Test
    fun ambientShowsOnlyWhileTheEngineMoodIsIdle() {
        val c = full().also { it.setAmbient(AmbientMood.HOT) }
        assertEquals(AmbientMood.HOT, c.renderState.ambient)
        c.applyOutput(out(Mood.HAPPY))
        assertNull(c.renderState.ambient) // 기쁨이 우선
        c.applyOutput(out(Mood.IDLE))
        assertEquals(AmbientMood.HOT, c.renderState.ambient) // 편안으로 돌아오면 다시 보인다
        c.applyOutput(out(Mood.AMBIENT))
        assertEquals(AmbientMood.HOT, c.renderState.ambient)
    }

    @Test
    fun settingNullReleasesTheAmbientMood() {
        val c = full().also { it.setAmbient(AmbientMood.HOT) }
        c.settle()
        c.setAmbient(null)
        assertNull(c.renderState.ambient)
        c.settle()
        assertEquals(1f, c.phys.sx, 0.03f)
        assertEquals(1f, c.phys.sy, 0.04f)
    }

    @Test
    fun blinkOnlyWhenIdleOrRaining() {
        for ((ambient, blinks) in listOf(
            null to true, AmbientMood.RAIN to true,
            AmbientMood.HOT to false, AmbientMood.COLD to false, AmbientMood.QUIET to false,
        )) {
            val c = full().also { it.setAmbient(ambient) }
            c.settle(20)
            assertEquals("$ambient", blinks, c.phys.blinkStartSec >= 0f)
        }
    }

    @Test
    fun staticModeIgnoresAmbientExceptRainHood() {
        val c = PetController()
        c.applyOutput(out(tier = MotionTier.STATIC))
        val base = c.renderState.staticPose!!
        for (a in listOf(AmbientMood.HOT, AmbientMood.COLD, AmbientMood.QUIET)) {
            c.setAmbient(a)
            assertEquals("$a", base, c.renderState.staticPose)
            assertNull(c.renderState.ambient) // 정적에서는 배경 감정을 그리지 않는다
        }
        c.setAmbient(AmbientMood.RAIN)
        assertTrue(c.renderState.staticPose!!.hood)
    }

    @Test
    fun coldShiversWithTheClockUnlessMotionIsReduced() {
        val c = full().also { it.setAmbient(AmbientMood.COLD) }
        c.settle(6)
        assertTrue(abs(c.phys.jitterX) > 0f)
        assertTrue(abs(c.phys.jitterX) <= 1.6f)
        val r = full().also { it.setReducedMotion(true); it.setAmbient(AmbientMood.COLD) }
        r.settle(6)
        assertEquals(0f, r.phys.jitterX, 0f) // 모션 줄이기에서는 떨지 않는다
    }

    @Test
    fun ambientDoesNotConsumeRandomnessAtStep() {
        // 같은 시드에서 배경 감정이 없는 컨트롤러와 물리 시계가 같다
        val a = full().also { it.setAmbient(AmbientMood.QUIET) }
        val b = full()
        a.settle(30)
        b.settle(30)
        assertEquals(b.phys.clockSec, a.phys.clockSec, 1e-4f)
    }
}
