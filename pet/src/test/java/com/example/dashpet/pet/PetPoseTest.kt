package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.Direction4
import com.example.dashpet.engine.Mood
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T037. research.md R8: 정적 포즈는 시간 입력이 없는 순수 함수다. 기울기, 배율, 혼합비는 시안에 근거 없는 초안이다. */
class PetPoseTest {
    private val body = Color(0xFF4F7CFF)
    private val look = PetAppearance(bodyColor = body)

    private fun pose(
        mood: Mood = Mood.IDLE,
        dir: Direction4? = null,
        turn: TurnState = TurnState.OFF,
        ambient: AmbientMood? = null,
        appearance: PetAppearance = look,
    ) = petPose(mood, dir, turn, appearance, ambient)

    @Test
    fun idleKeepsBodyColorAndUprightShape() {
        val p = pose(Mood.IDLE)
        assertEquals(body, p.bodyColor)
        assertEquals(0f, p.tiltDeg, 0f)
        assertEquals(1f, p.scaleX, 0f)
        assertEquals(1f, p.scaleY, 0f)
        assertNull(p.closedEye)
        assertNull(p.amberBrow)
        assertFalse(p.hood)
    }

    @Test
    fun ambientMoodBehavesLikeIdle() {
        assertEquals(pose(Mood.IDLE), pose(Mood.AMBIENT))
    }

    @Test
    fun happyMixesGreenAt0_9() {
        assertEquals(mixColor(body, Color(0xFF3DDC84), 0.9f), pose(Mood.HAPPY).bodyColor)
    }

    @Test
    fun dizzyMixesAmberAt0_8() {
        assertEquals(mixColor(body, Color(0xFFF5A524), 0.8f), pose(Mood.DIZZY).bodyColor)
    }

    @Test
    fun startledMixesRedAt0_85WithoutBlink() {
        assertEquals(mixColor(body, Color(0xFFFF5A5A), 0.85f), pose(Mood.STARTLED).bodyColor)
    }

    @Test
    fun squashedKeepsBodyColor() {
        for (d in Direction4.entries) assertEquals(body, pose(Mood.SQUASHED, d).bodyColor)
    }

    @Test
    fun squashedLeansTowardDirection() {
        val right = pose(Mood.SQUASHED, Direction4.RIGHT)
        assertEquals(10f, right.tiltDeg, 0f)
        assertEquals(1f, right.scaleX, 0f)
        val left = pose(Mood.SQUASHED, Direction4.LEFT)
        assertEquals(-10f, left.tiltDeg, 0f)
        val front = pose(Mood.SQUASHED, Direction4.FRONT)
        assertEquals(0f, front.tiltDeg, 0f)
        assertEquals(1.08f, front.scaleX, 0f)
        assertEquals(1.08f, front.scaleY, 0f)
        val back = pose(Mood.SQUASHED, Direction4.BACK)
        assertEquals(0.9f, back.scaleX, 0f)
        assertEquals(0.9f, back.scaleY, 0f)
    }

    @Test
    fun squashedWithoutDirectionIsUpright() {
        val p = pose(Mood.SQUASHED, null)
        assertEquals(0f, p.tiltDeg, 0f)
        assertEquals(1f, p.scaleX, 0f)
        assertEquals(1f, p.scaleY, 0f)
    }

    @Test
    fun directionIsIgnoredForOtherMoods() {
        for (m in listOf(Mood.IDLE, Mood.HAPPY, Mood.DIZZY, Mood.STARTLED)) {
            val p = pose(m, Direction4.RIGHT)
            assertEquals(0f, p.tiltDeg, 0f)
            assertEquals(1f, p.scaleX, 0f)
        }
    }

    @Test
    fun turnSignalClosesSameSideEyeAndShowsAmberBrow() {
        val l = pose(turn = TurnState.LEFT)
        assertEquals(Side.LEFT, l.closedEye)
        assertEquals(Side.LEFT, l.amberBrow)
        val r = pose(turn = TurnState.RIGHT)
        assertEquals(Side.RIGHT, r.closedEye)
        assertEquals(Side.RIGHT, r.amberBrow)
        val off = pose(turn = TurnState.OFF)
        assertNull(off.closedEye)
        assertNull(off.amberBrow)
    }

    @Test
    fun turnSignalCombinesWithAnyMood() {
        for (m in Mood.entries) assertEquals(Side.RIGHT, pose(m, turn = TurnState.RIGHT).closedEye)
    }

    @Test
    fun hoodOnlyWhenRaining() {
        assertTrue(pose(ambient = AmbientMood.RAIN).hood)
        for (a in listOf(null, AmbientMood.HOT, AmbientMood.COLD, AmbientMood.QUIET)) assertFalse(pose(ambient = a).hood)
    }

    @Test
    fun usesAppearanceBodyColor() {
        val red = PetAppearance(bodyColor = Color(0xFFD03030))
        assertEquals(red.bodyColor, pose(appearance = red).bodyColor)
    }

    @Test
    fun sameInputGivesSameValue() {
        for (m in Mood.entries) for (d in Direction4.entries) {
            assertEquals(pose(m, d, TurnState.LEFT, AmbientMood.RAIN), pose(m, d, TurnState.LEFT, AmbientMood.RAIN))
        }
    }
}
