package com.example.dashpet.signal

import com.example.dashpet.signal.scenario.scenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchableSignalSourceTest {
    /** 신호를 직접 넣을 수 있는 소스. 호출 횟수를 센다. */
    private class FakeSource : VehicleSignalSource {
        val flow = MutableSharedFlow<RawSignal>(extraBufferCapacity = 64)
        var started = 0
        var stopped = 0
        override val signals: Flow<RawSignal> get() = flow
        override fun start() { started++ }
        override fun stop() { stopped++ }
        fun emit(speedMps: Float) { flow.tryEmit(Speed(0, speedMps)) }
    }

    @Test
    fun emitsOnlyTheSelectedSource() = runTest {
        val a = FakeSource()
        val b = FakeSource()
        val source = SwitchableSignalSource(a)
        val got = mutableListOf<RawSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.signals.collect { got += it } }

        a.emit(1f)
        b.emit(2f) // 선택되지 않은 소스의 신호는 나오지 않는다
        assertEquals(listOf<RawSignal>(Speed(0, 1f)), got)
    }

    @Test
    fun selectStopsPreviousAndIgnoresItsLaterSignals() = runTest {
        val a = FakeSource()
        val b = FakeSource()
        val source = SwitchableSignalSource(a)
        val got = mutableListOf<RawSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.signals.collect { got += it } }

        source.select(b)
        assertEquals(1, a.stopped)
        a.emit(1f) // 이전 소스가 더 내보내도 나오지 않는다
        b.emit(2f)
        assertEquals(listOf<RawSignal>(Speed(0, 2f)), got)
    }

    @Test
    fun alternatingTenTimesNeverMixesSources() = runTest {
        val a = FakeSource()
        val b = FakeSource()
        val source = SwitchableSignalSource(a)
        val got = mutableListOf<Speed>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.signals.collect { got += it as Speed }
        }

        repeat(10) { i ->
            val (current, other) = if (i % 2 == 0) b to a else a to b
            source.select(current)
            other.emit(99f) // 섞여 나오면 안 되는 값
            current.emit(i.toFloat())
        }
        assertEquals((0 until 10).map { it.toFloat() }, got.map { it.speedMps })
        assertTrue(got.none { it.speedMps == 99f })
    }

    @Test
    fun selectingTheSameSourceDoesNothing() = runTest {
        val a = FakeSource()
        val source = SwitchableSignalSource(a)
        source.select(a)
        assertEquals(0, a.stopped)
    }

    @Test
    fun startAndStopDelegateToTheCurrentSource() = runTest {
        val a = FakeSource()
        val b = FakeSource()
        val source = SwitchableSignalSource(a)
        source.start()
        source.stop()
        assertEquals(1, a.started)
        assertEquals(1, a.stopped)
        source.select(b) // a 는 select 에서 한 번 더 stop 된다
        source.start()
        assertEquals(1, b.started)
        assertEquals(1, a.started)
    }

    @Test
    fun emitsNothingBeforeStart() = runTest {
        val demo = scenario("mini") { at(0) { speed(0) } }
        val source = SwitchableSignalSource(MockSignalSource(demo, Pacing.IMMEDIATE, tickIntervalMs = 0))
        val got = mutableListOf<RawSignal>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.signals.collect { got += it } }
        advanceUntilIdle()
        assertTrue(got.isEmpty())
        source.start()
        advanceUntilIdle()
        assertEquals(demo.expand(), got)
    }
}
