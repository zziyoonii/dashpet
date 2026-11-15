package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T057. 성격과 외형이 펫에 반영되는 규칙(FR-011, FR-021). 점프 속도 820/650, 재점프 620/480 값 자체는 PetPhysicsConfigTest 와 PetControllerFullTest 가 확인한다. */
class PetControllerAppearanceTest {
    private fun out(mood: Mood) = PetOutput(mood, 0.5f, null, MotionTier.FULL, TurnState.OFF, 0)

    private fun full(): PetController = PetController(random = Random(1)).also {
        it.setViewport(360f, 420f)
        it.applyOutput(out(Mood.IDLE))
    }

    @Test
    fun livelyJumpsHigherThanSturdyOnHappy() {
        val lively = full().also { it.setAppearance(PetAppearance(personality = Personality.LIVELY)) }
        val sturdy = full().also { it.setAppearance(PetAppearance(personality = Personality.STURDY)) }
        lively.applyOutput(out(Mood.HAPPY))
        sturdy.applyOutput(out(Mood.HAPPY))
        assertEquals(820f, lively.renderState.physics!!.vy, 0f)
        assertEquals(650f, sturdy.renderState.physics!!.vy, 0f)
        assertTrue(lively.renderState.physics!!.vy > sturdy.renderState.physics!!.vy)
    }

    @Test
    fun rejumpVelocitiesFollowPersonality() {
        val config = PetPhysicsConfig()
        assertEquals(620f, config.rejumpVelocity(Personality.LIVELY), 0f)
        assertEquals(480f, config.rejumpVelocity(Personality.STURDY), 0f)
        assertEquals(480f, config.rejumpVelocity(Personality.CUTE), 0f)
    }

    @Test
    fun cuteAlwaysHasCheeksWhateverTheMood() {
        for (mood in Mood.entries) {
            assertTrue("CUTE, $mood", hasCheeks(Personality.CUTE, mood))
        }
    }

    @Test
    fun otherPersonalitiesHaveCheeksOnlyWhenHappy() {
        for (p in listOf(Personality.STURDY, Personality.LIVELY)) {
            for (mood in Mood.entries) {
                assertEquals("$p, $mood", mood == Mood.HAPPY, hasCheeks(p, mood))
            }
        }
    }

    @Test
    fun missingAppearanceFallsBackToTheDefault() {
        val c = PetController()
        val a = c.renderState.appearance
        assertEquals(PetAppearance(), a)
        assertEquals("내 차", a.displayName)
        assertEquals(PetAppearance.DEFAULT_BODY_COLOR, a.bodyColor)
        assertEquals(Personality.STURDY, a.personality)
    }

    @Test
    fun blankNameShowsTheDefaultName() {
        assertEquals("내 차", PetAppearance(name = "").displayName)
        assertEquals("내 차", PetAppearance(name = "   ").displayName)
        assertEquals("장수", PetAppearance(name = "장수").displayName)
    }

    @Test
    fun changingAppearanceDoesNotTouchPhysics() {
        val c = full()
        repeat(30) { c.step(0.016f) }
        val before = c.renderState.physics!!
        val snapshot = listOf(before.ox, before.oy, before.vx, before.vy, before.sx, before.sy, before.clockSec)
        c.setAppearance(PetAppearance(name = "장수", bodyColor = Color(0xFFE5484D), personality = Personality.CUTE))
        val after = c.renderState.physics!!
        assertEquals(snapshot, listOf(after.ox, after.oy, after.vx, after.vy, after.sx, after.sy, after.clockSec))
        assertEquals(Color(0xFFE5484D), c.renderState.appearance.bodyColor)
    }

    @Test
    fun sameAppearanceIsIgnored() {
        val c = full()
        val a = PetAppearance(name = "장수")
        c.setAppearance(a)
        val v = c.stateVersion
        c.setAppearance(a.copy())
        assertEquals(v, c.stateVersion) // 같은 값이면 다시 그리지 않는다
    }
}
