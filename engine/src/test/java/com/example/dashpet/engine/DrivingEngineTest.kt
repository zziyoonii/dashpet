package com.example.dashpet.engine

import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.MockSignalSource
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.VehicleSignalSource
import com.example.dashpet.signal.WarningType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import com.example.dashpet.signal.scenario.DemoScenarios
import com.example.dashpet.signal.scenario.scenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrivingEngineTest {
    private val small = scenario("small") {
        at(0) { parked(true); speed(0) }
        at(1_000) { parked(false) }
        at(2_000) { warning(WarningType.LANE_DEPARTURE, true) }
        at(5_000) { warning(WarningType.COLLISION, true) }
    }

    @Test
    fun eventsFlowMatchesCoreResults() = runTest {
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.IMMEDIATE), this)
        val got = mutableListOf<DrivingEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { got += it } }
        engine.start()
        advanceUntilIdle()
        val expected = runScenario(DemoScenarios.demoCommute).allEvents()
        assertEquals(expected, got)
        assertEquals(7, got.size)
        engine.stop()
        collector.cancel()
    }

    @Test
    fun outputIsUpdatedOnlyWhenValueChanges() = runTest {
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.IMMEDIATE), this)
        val seen = mutableListOf<PetOutput>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.output.collect { seen += it } }
        engine.start()
        advanceUntilIdle()

        val steps = runScenario(DemoScenarios.demoCommute)
        val distinct = mutableListOf(PetOutput.INITIAL)
        steps.forEach { if (!it.output.sameStateAs(distinct.last())) distinct += it.output }
        assertEquals(distinct, seen)
        assertTrue(seen.size < steps.size) // 매 신호마다 갱신되지 않는다
        assertTrue(seen.zipWithNext().none { (a, b) -> a.sameStateAs(b) })
        assertEquals(distinct.last(), engine.output.value)
        engine.stop()
        collector.cancel()
    }

    @Test
    fun outputStateFlowReplaysLastValueToLateSubscribers() = runTest {
        val engine = DrivingEngine(MockSignalSource(small, Pacing.IMMEDIATE), this)
        assertEquals(PetOutput.INITIAL, engine.output.value)
        engine.start()
        advanceUntilIdle()
        assertEquals(Mood.STARTLED, engine.output.value.mood)
        engine.stop()
    }

    @Test
    fun eventsBeforeSubscriptionAreNotReceived() = runTest {
        val engine = DrivingEngine(MockSignalSource(small, Pacing.IMMEDIATE), this)
        engine.start()
        advanceUntilIdle()
        val got = mutableListOf<DrivingEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { got += it } }
        runCurrent()
        assertTrue(got.isEmpty())
        engine.stop()
        collector.cancel()
    }

    @Test
    fun stopHaltsProcessingAndStartRunsAgainFromScratch() = runTest {
        val engine = DrivingEngine(MockSignalSource(small, Pacing.REAL_TIME), this)
        val got = mutableListOf<DrivingEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { got += it } }
        engine.start()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(listOf(EventType.TRIP_START, EventType.LANE_DEPARTURE), got.map { it.type })
        engine.stop()
        advanceUntilIdle()
        assertEquals(2, got.size) // 멈춘 뒤에는 더 처리하지 않음(5초의 충돌 위험 없음)

        engine.start() // 새 세션: 소스도 처음부터, 엔진 상태도 새로
        advanceUntilIdle()
        assertEquals(
            listOf(EventType.TRIP_START, EventType.LANE_DEPARTURE) +
                listOf(EventType.TRIP_START, EventType.LANE_DEPARTURE, EventType.COLLISION_RISK),
            got.map { it.type },
        )
        engine.stop()
        collector.cancel()
    }

    /** MockSignalSource 가 아닌 다른 소스 구현. 엔진은 소스 종류를 알지 못한다(FR-022, SC-006). */
    private class ListSource(private val list: List<RawSignal>) : VehicleSignalSource {
        private val channel = Channel<RawSignal>(Channel.UNLIMITED)
        override val signals: Flow<RawSignal> = channel.receiveAsFlow()
        override fun start() { list.forEach { channel.trySend(it) } }
        override fun stop() = Unit
    }

    @Test
    fun worksWithAnyVehicleSignalSource() = runTest {
        val engine = DrivingEngine(ListSource(DemoScenarios.demoCommute.expand()), this)
        val got = mutableListOf<DrivingEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { got += it } }
        engine.start()
        advanceUntilIdle()
        assertEquals(runScenario(DemoScenarios.demoCommute).allEvents(), got)
        engine.stop()
        collector.cancel()
    }

    @Test
    fun startIsIdempotentWhileRunning() = runTest {
        val engine = DrivingEngine(MockSignalSource(small, Pacing.REAL_TIME), this)
        val got = mutableListOf<DrivingEvent>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.collect { got += it } }
        engine.start()
        engine.start()
        advanceUntilIdle()
        assertEquals(3, got.size)
        engine.stop()
        collector.cancel()
    }
}
