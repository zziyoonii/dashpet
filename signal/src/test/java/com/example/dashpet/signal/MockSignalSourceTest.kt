package com.example.dashpet.signal

import com.example.dashpet.signal.scenario.scenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockSignalSourceTest {
    /** 틱을 끈(tickIntervalMs = 0) 소스로 만든 기존 동작의 기준 열. */
    private fun RawSignal.isTick() = this is Tick

    private val demo = scenario("mini") {
        at(0) { parked(true); speed(0) }
        at(1_000) { parked(false) }
        at(2_000) { speedRamp(0, 36, 500) }
        at(5_000) { warning(WarningType.LANE_DEPARTURE, true) }
    }

    @Test
    fun immediateEmitsExpandedSignalsInOrder() = runTest {
        val source = MockSignalSource(demo, Pacing.IMMEDIATE, tickIntervalMs = 0)
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        runCurrent()
        source.start()
        advanceUntilIdle()
        assertEquals(demo.expand(), got)
        assertEquals(0L, currentTime) // 기다리지 않음
        job.cancel()
    }

    @Test
    fun realTimeEmitsSameContentAndOrderOnVirtualTime() = runTest {
        val source = MockSignalSource(demo, Pacing.REAL_TIME, tickIntervalMs = 0)
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        source.start()
        advanceUntilIdle()
        assertEquals(demo.expand(), got)
        assertEquals(5_000L, currentTime) // 마지막 신호 시각만큼 기다림
        job.cancel()
    }

    @Test
    fun realTimeWaitsForSignalTimes() = runTest {
        val source = MockSignalSource(demo, Pacing.REAL_TIME, tickIntervalMs = 0)
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        source.start()
        runCurrent()
        assertEquals(2, got.size) // 0초 신호 두 개
        advanceTimeBy(999)
        assertEquals(2, got.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, got.size)
        job.cancel()
    }

    @Test
    fun noEmissionBeforeStart() = runTest {
        val source = MockSignalSource(demo, Pacing.IMMEDIATE, tickIntervalMs = 0)
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        advanceUntilIdle()
        assertTrue(got.isEmpty())
        source.start()
        advanceUntilIdle()
        assertTrue(got.isNotEmpty())
        job.cancel()
    }

    @Test
    fun stopHaltsEmissionAndStartRestartsFromBeginning() = runTest {
        val source = MockSignalSource(demo, Pacing.REAL_TIME, tickIntervalMs = 0)
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        source.start()
        advanceTimeBy(1_500)
        runCurrent()
        val partial = got.toList()
        assertEquals(3, partial.size)
        source.stop()
        advanceUntilIdle()
        assertEquals(partial, got) // 멈춘 뒤에는 방출 없음

        source.start()
        advanceUntilIdle()
        assertEquals(partial + demo.expand(), got) // 처음부터 다시
        job.cancel()
    }

    @Test
    fun startAfterCompletionReplays() = runTest {
        val source = MockSignalSource(demo, Pacing.IMMEDIATE, tickIntervalMs = 0)
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        source.start()
        advanceUntilIdle()
        source.start()
        advanceUntilIdle()
        assertEquals(demo.expand() + demo.expand(), got)
        job.cancel()
    }

    @Test
    fun sameScenarioGivesSameStream() = runTest {
        suspend fun collect(pacing: Pacing): List<RawSignal> {
            val source = MockSignalSource(demo, pacing, tickIntervalMs = 0)
            val got = mutableListOf<RawSignal>()
            val job = launch { source.signals.collect { got += it } }
            source.start()
            advanceUntilIdle()
            job.cancel()
            return got
        }
        assertEquals(collect(Pacing.IMMEDIATE), collect(Pacing.REAL_TIME))
        assertEquals(collect(Pacing.IMMEDIATE), collect(Pacing.IMMEDIATE))
    }

    // ---- 틱 (002 R12) ----

    private suspend fun kotlinx.coroutines.test.TestScope.collectAll(source: MockSignalSource): List<RawSignal> {
        val got = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { got += it } }
        source.start()
        advanceUntilIdle()
        job.cancel()
        return got
    }

    @Test
    fun defaultsAre100msIntervalAnd3000msTail() = runTest {
        val got = collectAll(MockSignalSource(demo, Pacing.IMMEDIATE))
        val ticks = got.filter { it.isTick() }.map { it.timestampMs }
        assertEquals((0L..8_000L step 100L).toList(), ticks) // 첫 신호 0, 마지막 신호 5000 + tail 3000
    }

    @Test
    fun ticksStartAtFirstSignalAndEndAtLastSignalPlusTail() = runTest {
        val late = scenario("late") {
            at(1_000) { parked(true) }
            at(1_450) { speed(1) }
        }
        val got = collectAll(MockSignalSource(late, Pacing.IMMEDIATE, tickIntervalMs = 200, tailMs = 500))
        val ticks = got.filter { it.isTick() }.map { it.timestampMs }
        assertEquals(listOf(1_000L, 1_200L, 1_400L, 1_600L, 1_800L), ticks) // 1450 + 500 = 1950 이하
    }

    @Test
    fun scenarioSignalsComeBeforeTickAtTheSameTime() = runTest {
        val got = collectAll(MockSignalSource(demo, Pacing.IMMEDIATE))
        // 0초: 신호 두 개(ParkedState, Speed) 다음에 Tick
        assertEquals(listOf(false, false, true), got.take(3).map { it.isTick() })
        assertEquals(0L, got[2].timestampMs)
        // 모든 시각에서 Tick 은 같은 시각의 신호보다 뒤에 있다.
        got.groupBy { it.timestampMs }.forEach { (t, group) ->
            val firstTick = group.indexOfFirst { it.isTick() }
            if (firstTick >= 0) assertTrue("t=$t", group.drop(firstTick).all { it.isTick() })
        }
    }

    @Test
    fun mergedStreamKeepsScenarioSignalsAndNeverDecreasesInTime() = runTest {
        val got = collectAll(MockSignalSource(demo, Pacing.IMMEDIATE))
        assertEquals(demo.expand(), got.filterNot { it.isTick() })
        assertTrue(got.zipWithNext().all { (a, b) -> a.timestampMs <= b.timestampMs })
    }

    @Test
    fun realTimeAndImmediateGiveTheSameStreamWithTicks() = runTest {
        val immediate = collectAll(MockSignalSource(demo, Pacing.IMMEDIATE))
        val realTime = collectAll(MockSignalSource(demo, Pacing.REAL_TIME))
        assertEquals(immediate, realTime)
    }

    @Test
    fun realTimeWaitsUntilLastTick() = runTest {
        collectAll(MockSignalSource(demo, Pacing.REAL_TIME))
        assertEquals(8_000L, currentTime) // 마지막 신호 5000 + tail 3000
    }

    @Test
    fun zeroTickIntervalMeansNoTicks() = runTest {
        val got = collectAll(MockSignalSource(demo, Pacing.IMMEDIATE, tickIntervalMs = 0))
        assertEquals(demo.expand(), got)
    }

    @Test
    fun sameScenarioWithTicksGivesSameStream() = runTest {
        assertEquals(
            collectAll(MockSignalSource(demo, Pacing.IMMEDIATE)),
            collectAll(MockSignalSource(demo, Pacing.IMMEDIATE)),
        )
    }

    @Test
    fun emptyScenarioEmitsNothing() = runTest {
        val got = collectAll(MockSignalSource(scenario("empty") { }, Pacing.IMMEDIATE))
        assertTrue(got.isEmpty())
    }
}
