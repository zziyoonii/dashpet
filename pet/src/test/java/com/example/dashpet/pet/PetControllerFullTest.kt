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

/** T024. contracts/pet-view-api.md 의 이벤트 매핑 표(풀 모션). */
class PetControllerFullTest {
    private fun out(
        mood: Mood = Mood.IDLE,
        tier: MotionTier = MotionTier.FULL,
        dir: Direction4? = null,
        turn: TurnState = TurnState.OFF,
    ) = PetOutput(mood, 0.5f, dir, tier, turn, 0)

    private fun full(seed: Int = 1, mood: Mood = Mood.IDLE): PetController =
        PetController(random = Random(seed)).also {
            it.setViewport(360f, 420f)
            it.applyOutput(out(mood))
        }

    private fun ev(type: EventType, dir: Direction4?, s: Float = 0.6f) = DrivingEvent(type, 0, s, dir)
    private val PetController.phys get() = renderState.physics!!

    @Test
    fun startsStaticUntilFullTierArrives() {
        val c = PetController()
        assertFalse(c.needsFrames)
        assertNull(c.renderState.physics)
        c.applyOutput(out(tier = MotionTier.FULL))
        assertTrue(c.needsFrames)
        assertNotNull(c.renderState.physics)
        c.applyOutput(out(tier = MotionTier.STATIC))
        assertFalse(c.needsFrames)
    }

    @Test
    fun hardCornerRightIsPlusOne() {
        val c = full()
        c.onEvent(ev(EventType.HARD_CORNER, Direction4.RIGHT, 0.6f))
        assertEquals(1, c.phys.lat!!.dir)
        assertEquals(0.6f, c.phys.lat!!.s, 0f)
    }

    @Test
    fun hardCornerLeftIsMinusOne() {
        val c = full()
        c.onEvent(ev(EventType.HARD_CORNER, Direction4.LEFT, 0.9f))
        assertEquals(-1, c.phys.lat!!.dir)
        assertEquals(0.9f, c.phys.lat!!.s, 0f)
    }

    @Test
    fun hardBrakeFrontIsSurgePlusOneAndAccelBackIsMinusOne() {
        val c = full()
        c.onEvent(ev(EventType.HARD_BRAKE, Direction4.FRONT, 0.8f))
        assertEquals(1, c.phys.lon!!.dir)
        assertEquals(0.8f, c.phys.lon!!.s, 0f)
        val d = full()
        d.onEvent(ev(EventType.HARD_ACCEL, Direction4.BACK, 0.4f))
        assertEquals(-1, d.phys.lon!!.dir)
        assertEquals(0.4f, d.phys.lon!!.s, 0f)
    }

    @Test
    fun otherEventTypesDoNotReact() {
        val c = full()
        EventType.entries.filter { it !in setOf(EventType.HARD_CORNER, EventType.HARD_BRAKE, EventType.HARD_ACCEL) }
            .forEach { t -> Direction4.entries.forEach { c.onEvent(ev(t, it)) }; c.onEvent(ev(t, null)) }
        assertNull(c.phys.lat)
        assertNull(c.phys.lon)
        assertEquals(0f, c.phys.vx, 0f)
        assertEquals(0f, c.phys.vz, 0f)
    }

    @Test
    fun eventWithoutDirectionDoesNothing() {
        val c = full()
        c.onEvent(ev(EventType.HARD_CORNER, null))
        c.onEvent(ev(EventType.HARD_BRAKE, null))
        assertNull(c.phys.lat)
        assertNull(c.phys.lon)
    }

    @Test
    fun happyEntryJumpsByPersonalityAndSameMoodDoesNotRepeat() {
        val c = full()
        c.applyOutput(out(Mood.HAPPY))
        assertEquals(650f, c.phys.vy, 0f)
        val l = full().also { it.setAppearance(PetAppearance(personality = Personality.LIVELY)) }
        l.applyOutput(out(Mood.HAPPY))
        assertEquals(820f, l.phys.vy, 0f)
        // 같은 감정이 이어지는 출력(방향 등만 바뀜)에는 다시 점프하지 않는다.
        c.step(1f / 60f)
        val vy = c.phys.vy
        c.applyOutput(out(Mood.HAPPY, dir = Direction4.LEFT))
        assertEquals(vy, c.phys.vy, 0f)
    }

    @Test
    fun happyRejumpsAfter760msOnlyIfStillHappy() {
        fun maxVyAfter(clockSec: Float, keepHappy: Boolean): Float {
            val c = full()
            c.applyOutput(out(Mood.HAPPY))
            var m = -1e9f
            while (c.phys.clockSec < 1.2f) {
                c.step(1f / 60f)
                if (!keepHappy && c.phys.clockSec > 0.3f) c.applyOutput(out(Mood.IDLE))
                if (c.phys.clockSec > clockSec) m = maxOf(m, c.phys.vy)
            }
            return m
        }
        assertTrue(maxVyAfter(0.76f, keepHappy = true) > 400f)   // 재점프 480 에서 시작
        assertTrue(maxVyAfter(0.76f, keepHappy = false) < 100f)  // 이미 지나간 감정이면 없음
        val l = full().also { it.setAppearance(PetAppearance(personality = Personality.LIVELY)) }
        l.applyOutput(out(Mood.HAPPY))
        var m = -1e9f
        while (l.phys.clockSec < 1.2f) { l.step(1f / 60f); if (l.phys.clockSec > 0.8f) m = maxOf(m, l.phys.vy) }
        assertTrue(m > 400f) // 활발은 620(0.8초 뒤에도 450 근처), 기본 480 이면 350 근처
    }

    @Test
    fun startledEntryKicksUpAndShrinks() {
        val c = full()
        c.applyOutput(out(Mood.STARTLED))
        assertEquals(420f, c.phys.vy, 0f)
        assertEquals(-1.5f, c.phys.vsx, 1e-6f)
        assertEquals(-1f, c.phys.vsy, 1e-6f)
    }

    @Test
    fun enteringFullWhileAlreadyHappyDoesNotReplayEntryEffect() {
        val c = PetController(random = Random(1))
        c.setViewport(360f, 420f)
        c.applyOutput(out(Mood.HAPPY, tier = MotionTier.STATIC))
        c.applyOutput(out(Mood.HAPPY, tier = MotionTier.FULL))
        assertEquals(0f, c.phys.vy, 0f)
    }

    @Test
    fun sameMoodEventsStillSurgeEveryTime() {
        val c = full()
        c.applyOutput(out(Mood.SQUASHED, dir = Direction4.LEFT))
        c.onEvent(ev(EventType.HARD_CORNER, Direction4.LEFT, 0.5f))
        val first = c.phys.vx
        c.step(1f / 60f)
        c.onEvent(ev(EventType.HARD_CORNER, Direction4.LEFT, 0.5f)) // 같은 감정, 출력은 그대로
        assertEquals(-1, c.phys.lat!!.dir)
        assertTrue(c.phys.vx < first - 100f) // vx -= 300 가 다시 적용됨
    }

    @Test
    fun stepAdvancesClockByDtAndClampsToThirtieth() {
        val c = full()
        c.step(0.016f)
        assertEquals(0.016f, c.phys.clockSec, 1e-6f)
        c.step(1f) // 큰 dt 는 1/30 으로
        assertEquals(0.016f + 1f / 30f, c.phys.clockSec, 1e-5f)
        val t = c.phys.clockSec
        c.step(0f); c.step(-1f); c.step(Float.NaN)
        assertEquals(t, c.phys.clockSec, 0f)
    }

    @Test
    fun staticModeIgnoresEventsAndSteps() {
        val c = PetController(random = Random(1))
        c.setViewport(360f, 420f)
        c.applyOutput(out(Mood.HAPPY, tier = MotionTier.STATIC))
        val before = c.renderState.frame
        c.onEvent(ev(EventType.HARD_CORNER, Direction4.RIGHT))
        c.step(0.016f)
        assertEquals(before, c.renderState.frame)
        assertNull(c.renderState.physics)
        assertTrue(c.renderState.particles.isEmpty())
    }

    @Test
    fun goingStaticResetsPhysicsAndParticles() {
        val c = full(mood = Mood.HAPPY)
        c.onEvent(ev(EventType.HARD_CORNER, Direction4.RIGHT))
        repeat(60) { c.step(1f / 60f) }
        c.applyOutput(out(Mood.HAPPY, tier = MotionTier.STATIC))
        assertFalse(c.needsFrames)
        assertTrue(c.renderState.particles.isEmpty())
        c.applyOutput(out(Mood.IDLE, tier = MotionTier.FULL))
        assertNull(c.phys.lat)
        assertEquals(0f, c.phys.clockSec, 0f)
    }

    @Test
    fun sameSeedAndSameCallsGiveSameRenderState() {
        fun run(): Triple<List<Float>, Int, List<Float>> {
            val c = full(seed = 42)
            c.applyOutput(out(Mood.HAPPY))
            c.onEvent(ev(EventType.HARD_CORNER, Direction4.RIGHT, 0.9f))
            repeat(100) { c.step(1f / 60f) }
            c.onEvent(ev(EventType.HARD_BRAKE, Direction4.FRONT, 1f))
            c.applyOutput(out(Mood.STARTLED))
            repeat(200) { c.step(1f / 60f) }
            val rs = c.renderState
            return Triple(rs.physics!!.toList(), rs.frame, rs.particles.map { it.x + it.y })
        }
        assertEquals(run(), run())
    }

    @Test
    fun differentSeedsCanDiffer() {
        fun run(seed: Int): List<Float> {
            val c = full(seed)
            c.applyOutput(out(Mood.STARTLED))
            repeat(30) { c.step(1f / 60f) }
            return c.renderState.physics!!.toList()
        }
        assertFalse(run(1) == run(2)) // 깜짝 떨림 오프셋에 난수가 쓰인다
    }

    @Test
    fun startledJitterIsDecidedByControllerNotPainter() {
        val c = full(mood = Mood.STARTLED)
        repeat(5) { c.step(1f / 60f) }
        val p = c.phys
        assertTrue(p.jitterX != 0f || p.jitterY != 0f)
        assertTrue(kotlin.math.abs(p.jitterX) <= 1.5f && kotlin.math.abs(p.jitterY) <= 1f)
    }

    @Test
    fun reducedMotionSkipsJitter() {
        val c = full(mood = Mood.STARTLED)
        c.setReducedMotion(true)
        repeat(5) { c.step(1f / 60f) }
        assertEquals(0f, c.phys.jitterX, 0f)
        assertEquals(0f, c.phys.jitterY, 0f)
        assertTrue(c.renderState.reducedMotion)
    }
}
