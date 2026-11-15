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
    private val demo = scenario("mini") {
        at(0) { parked(true); speed(0) }
        at(1_000) { parked(false) }
        at(2_000) { speedRamp(0, 36, 500) }
        at(5_000) { warning(WarningType.LANE_DEPARTURE, true) }
    }

    @Test
    fun immediateEmitsExpandedSignalsInOrder() = runTest {
        val source = MockSignalSource(demo, Pacing.IMMEDIATE)
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
        val source = MockSignalSource(demo, Pacing.REAL_TIME)
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
        val source = MockSignalSource(demo, Pacing.REAL_TIME)
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
        val source = MockSignalSource(demo, Pacing.IMMEDIATE)
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
        val source = MockSignalSource(demo, Pacing.REAL_TIME)
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
        val source = MockSignalSource(demo, Pacing.IMMEDIATE)
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
            val source = MockSignalSource(demo, pacing)
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
}
