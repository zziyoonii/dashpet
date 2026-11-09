package com.example.dashpet.pet

import com.example.dashpet.engine.DrivingEngine
import com.example.dashpet.engine.Mood
import com.example.dashpet.signal.MockSignalSource
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.scenario.DemoScenarios
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T025. collectFrom 과 데모 시나리오. 데모는 8초부터 제한이 걸려 STATIC 이므로 수동 해제(injectRestriction)로 풀 모션을 만든다. */
@OptIn(ExperimentalCoroutinesApi::class)
class PetControllerEngineTest {
    @Test
    fun demoCornerAndBrakeSurgeAndMoodOrderMatchesEngine() = runTest {
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.REAL_TIME), this)
        val controller = PetController(random = Random(1)).also { it.setViewport(360f, 420f) }
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { controller.collectFrom(engine) }
        runCurrent()
        engine.start()

        val engineMoods = mutableListOf<Mood>()
        val petMoods = mutableListOf<Mood>()
        fun sample() {
            runCurrent()
            val e = engine.output.value.mood
            if (engineMoods.lastOrNull() != e) engineMoods += e
            val p = controller.renderState.mood
            if (petMoods.lastOrNull() != p) petMoods += p
        }
        fun advanceTo(ms: Long, from: Long): Long {
            var t = from
            while (t < ms) { advanceTimeBy(100); t += 100; sample() }
            return t
        }

        var t = advanceTo(10_500, 0)
        engine.injectRestriction(RestrictionState.NONE) // 제한 해제: 풀 모션
        t = advanceTo(21_900, t)
        assertTrue(controller.needsFrames)
        assertNull(controller.renderState.physics!!.lat)
        t = advanceTo(22_600, t) // 22초 급커브(우회전 -> 몸은 왼쪽)
        assertEquals(-1, controller.renderState.physics!!.lat!!.dir)
        assertNull(controller.renderState.physics!!.lon)
        t = advanceTo(45_900, t)
        assertNull(controller.renderState.physics!!.lon)
        t = advanceTo(46_600, t) // 46초 급제동
        assertEquals(1, controller.renderState.physics!!.lon!!.dir)
        advanceTo(56_000, t)
        advanceUntilIdle()
        sample()

        assertEquals(engineMoods, petMoods)
        assertTrue(Mood.SQUASHED in petMoods && Mood.HAPPY in petMoods && Mood.DIZZY in petMoods)
        job.cancel()
        engine.stop()
    }

    @Test
    fun staticTierFromDemoScenarioIgnoresEvents() = runTest {
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.IMMEDIATE), this)
        val controller = PetController(random = Random(1)).also { it.setViewport(360f, 420f) }
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { controller.collectFrom(engine) }
        runCurrent()
        engine.start()
        advanceUntilIdle()
        // 8~52초는 제한 상태라 정적이었고 이벤트(급커브, 급제동)는 무시됐다. 52초 해제로 풀 모션이 되면 물리는 새로 시작한다.
        val phys = controller.renderState.physics!!
        assertNull(phys.lat)
        assertNull(phys.lon)
        job.cancel()
        engine.stop()
    }

    @Test
    fun cancellingStopsBothCollectors() = runTest {
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.REAL_TIME), this)
        val controller = PetController(random = Random(1)).also { it.setViewport(360f, 420f) }
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { controller.collectFrom(engine) }
        runCurrent()
        engine.start()
        advanceTimeBy(10_500); runCurrent()
        engine.injectRestriction(RestrictionState.NONE)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(controller.needsFrames)
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        advanceTimeBy(13_000); runCurrent() // 22.5초: 급커브
        assertNull(controller.renderState.physics!!.lat) // 취소 뒤라 이벤트가 반영되지 않는다
        engine.stop()
    }
}
