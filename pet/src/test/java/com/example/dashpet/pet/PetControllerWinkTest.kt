package com.example.dashpet.pet

import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T053. 풀 모션의 방향지시등 윙크: 380ms 주기, 시계는 step 으로만 진행(결정적). */
class PetControllerWinkTest {
    private fun out(turn: TurnState, mood: Mood = Mood.IDLE, tier: MotionTier = MotionTier.FULL) =
        PetOutput(mood, 0.5f, null, tier, turn, 0)

    private fun full(): PetController = PetController(random = Random(1)).also {
        it.setViewport(360f, 420f)
        it.applyOutput(out(TurnState.OFF))
    }

    /** 시계를 [sec] 초만큼 진행한다(0.01 초씩, 1/30 초 자르기보다 작다). */
    private fun PetController.advance(sec: Float) {
        repeat((sec / 0.01f).toInt()) { step(0.01f) }
    }

    @Test
    fun rightSignalClosesRightEyeFirstThenAlternatesEvery380ms() {
        val c = full()
        c.applyOutput(out(TurnState.RIGHT))
        assertTrue(c.winkClosed(Side.RIGHT)) // 시작하면 먼저 감는다
        c.advance(0.2f)
        assertTrue(c.winkClosed(Side.RIGHT)) // 0.2 초
        c.advance(0.2f)
        assertFalse(c.winkClosed(Side.RIGHT)) // 0.4 초: 두 번째 구간(뜸)
        c.advance(0.4f)
        assertTrue(c.winkClosed(Side.RIGHT)) // 0.8 초: 세 번째 구간(감음)
    }

    @Test
    fun leftSignalWinksLeftEyeOnly() {
        val c = full()
        c.applyOutput(out(TurnState.LEFT))
        assertTrue(c.winkClosed(Side.LEFT))
        assertFalse(c.winkClosed(Side.RIGHT)) // 다른 쪽 눈은 현재 감정 그대로
    }

    @Test
    fun rightSignalLeavesLeftEyeAlone() {
        val c = full()
        c.applyOutput(out(TurnState.RIGHT))
        assertFalse(c.winkClosed(Side.LEFT))
    }

    @Test
    fun turningOffRemovesWinkImmediately() {
        val c = full()
        c.applyOutput(out(TurnState.RIGHT))
        assertTrue(c.winkClosed(Side.RIGHT))
        c.applyOutput(out(TurnState.OFF))
        assertFalse(c.winkClosed(Side.RIGHT))
        assertFalse(c.winkClosed(Side.LEFT))
        assertNull(c.renderState.wink)
    }

    @Test
    fun cycleStartsWhenTheSignalTurnsOnNotAtZero() {
        val c = full()
        c.advance(1.0f) // 신호가 없던 시간
        c.applyOutput(out(TurnState.RIGHT))
        assertTrue(c.winkClosed(Side.RIGHT)) // 켜진 순간부터 센다
        c.advance(0.4f)
        assertFalse(c.winkClosed(Side.RIGHT))
    }

    @Test
    fun switchingSidesRestartsTheCycle() {
        val c = full()
        c.applyOutput(out(TurnState.RIGHT))
        c.advance(0.4f)
        assertFalse(c.winkClosed(Side.RIGHT))
        c.applyOutput(out(TurnState.LEFT))
        assertTrue(c.winkClosed(Side.LEFT))
    }

    @Test
    fun winkStaysWithReducedMotion() {
        val c = full()
        c.setReducedMotion(true)
        c.applyOutput(out(TurnState.RIGHT))
        assertTrue(c.winkClosed(Side.RIGHT))
        c.advance(0.4f)
        assertFalse(c.winkClosed(Side.RIGHT))
        c.advance(0.4f)
        assertTrue(c.winkClosed(Side.RIGHT))
    }

    @Test
    fun otherEyeKeepsTheCurrentMood() {
        val c = full()
        c.applyOutput(out(TurnState.RIGHT, Mood.HAPPY))
        assertEquals(Mood.HAPPY, c.renderState.mood) // 감정은 그대로, 윙크는 눈 한 쪽에만
        assertEquals(Side.RIGHT, c.renderState.wink)
    }

    @Test
    fun staticModeHasNoTimedWink() {
        val c = PetController()
        c.applyOutput(out(TurnState.RIGHT, tier = MotionTier.STATIC))
        assertFalse(c.winkClosed(Side.RIGHT)) // 정적은 점멸 없이 선으로 감긴 눈(StaticPose)
        assertNull(c.renderState.wink)
        assertEquals(Side.RIGHT, c.renderState.staticPose!!.closedEye)
    }

    @Test
    fun noRandomnessIsConsumedByWink() {
        // 같은 입력과 같은 시드라면 윙크 유무와 상관없이 물리 시계가 같다(결정적)
        val a = full()
        val b = full()
        a.applyOutput(out(TurnState.RIGHT))
        a.advance(1f)
        b.advance(1f)
        assertEquals(b.renderState.physics!!.clockSec, a.renderState.physics!!.clockSec, 1e-4f)
    }
}
