package com.example.dashpet.engine

import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.MockSignalSource
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
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
    fun completedTripIsPublishedAtTripEndAndClearedOnNewSession() = runTest {
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.IMMEDIATE), this)
        assertEquals(null, engine.completedTrip.value)
        engine.start()
        advanceUntilIdle()
        val trip = engine.completedTrip.value!!
        assertEquals(94, trip.score) // 데모 시나리오의 기대 점수
        assertEquals(EventType.TRIP_END, trip.events.last().type)
        engine.stop()
        engine.start() // 새 세션은 이전 주행 기록을 지운다
        assertEquals(null, engine.completedTrip.value)
        engine.stop()
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
        // 틱 없는 소스: 비교 기준인 runScenario 는 시나리오 신호만 넣는다.
        val engine = DrivingEngine(MockSignalSource(DemoScenarios.demoCommute, Pacing.IMMEDIATE, tickIntervalMs = 0), this)
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
        // 틱이 있으면 놀람이 마지막 틱 전에 만료되므로 틱 없는 소스로 확인한다.
        val engine = DrivingEngine(MockSignalSource(small, Pacing.IMMEDIATE, tickIntervalMs = 0), this)
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

    // ---- injectRestriction (002 R13) ----

    /** 테스트가 신호를 직접 밀어 넣는 소스. */
    private class ManualSource : VehicleSignalSource {
        private val channel = Channel<RawSignal>(Channel.UNLIMITED)
        override val signals: Flow<RawSignal> = channel.receiveAsFlow()
        fun send(signal: RawSignal) { channel.trySend(signal) }
        override fun start() = Unit
        override fun stop() = Unit
    }

    @Test
    fun injectBeforeStartIsProcessedAtTimeZeroAfterStart() = runTest {
        val engine = DrivingEngine(ManualSource(), this)
        engine.injectRestriction(RestrictionState.NONE)
        assertEquals(MotionTier.STATIC, engine.output.value.tier) // start() 전에는 처리하지 않는다
        engine.start()
        advanceUntilIdle()
        assertEquals(MotionTier.FULL, engine.output.value.tier)
        assertEquals(0L, engine.output.value.atMs)
        engine.stop()
    }

    @Test
    fun injectWhilePlayingUsesLastProcessedSignalTimeAndChangesTier() = runTest {
        val source = ManualSource()
        val engine = DrivingEngine(source, this)
        engine.start()
        source.send(Speed(1_500, 0f))
        advanceUntilIdle()
        engine.injectRestriction(RestrictionState.NONE)
        advanceUntilIdle()
        assertEquals(MotionTier.FULL, engine.output.value.tier)
        assertEquals(1_500L, engine.output.value.atMs)

        engine.injectRestriction(RestrictionState.ACTIVE)
        advanceUntilIdle()
        assertEquals(MotionTier.STATIC, engine.output.value.tier)

        engine.injectRestriction(RestrictionState.NONE)
        engine.injectRestriction(RestrictionState.UNKNOWN)
        advanceUntilIdle()
        assertEquals(MotionTier.STATIC, engine.output.value.tier)
        engine.stop()
    }

    @Test
    fun injectedAndSourceSignalsAreProcessedInOrderOnOnePath() = runTest {
        val source = ManualSource()
        val engine = DrivingEngine(source, this)
        val seen = mutableListOf<PetOutput>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) { engine.output.collect { seen += it } }
        engine.start()
        source.send(Speed(1_000, 0f))
        advanceUntilIdle()
        engine.injectRestriction(RestrictionState.NONE) // 시각 1000 으로 처리
        advanceUntilIdle()
        source.send(Speed(2_000, 0f))
        source.send(Restriction(2_500, RestrictionState.ACTIVE))
        advanceUntilIdle()
        // 주입은 1000 시점, 다음 시나리오 제한 신호는 2500 시점에 처리되어 순서가 유지된다.
        assertEquals(listOf(MotionTier.STATIC, MotionTier.FULL, MotionTier.STATIC), seen.map { it.tier })
        assertEquals(listOf(0L, 1_000L, 2_500L), seen.map { it.atMs })
        engine.stop()
        collector.cancel()
    }

    @Test
    fun manualValueStaysUntilNextScenarioRestrictionSignal() = runTest {
        val source = ManualSource()
        val engine = DrivingEngine(source, this)
        engine.start()
        engine.injectRestriction(RestrictionState.NONE)
        source.send(Speed(1_000, 0f))
        source.send(Speed(2_000, 0f))
        advanceUntilIdle()
        assertEquals(MotionTier.FULL, engine.output.value.tier)
        source.send(Restriction(3_000, RestrictionState.ACTIVE))
        advanceUntilIdle()
        assertEquals(MotionTier.STATIC, engine.output.value.tier)
        engine.stop()
    }

    @Test
    fun stopDiscardsPendingInjectionAndStartBeginsNewSession() = runTest {
        val engine = DrivingEngine(ManualSource(), this)
        engine.injectRestriction(RestrictionState.NONE) // 대기 중
        engine.stop()
        engine.start()
        advanceUntilIdle()
        assertEquals(PetOutput.INITIAL, engine.output.value) // 버려졌다
        engine.injectRestriction(RestrictionState.NONE)
        advanceUntilIdle()
        assertEquals(MotionTier.FULL, engine.output.value.tier)
        engine.stop()
        engine.start() // 새 세션: 수동 값도 사라진다
        advanceUntilIdle()
        assertEquals(PetOutput.INITIAL, engine.output.value)
        engine.stop()
    }

    @Test
    fun injectWorksAcrossRealSourceWithTicks() = runTest {
        val engine = DrivingEngine(MockSignalSource(small, Pacing.REAL_TIME), this)
        engine.start()
        advanceTimeBy(1_050)
        runCurrent()
        engine.injectRestriction(RestrictionState.NONE)
        runCurrent()
        assertEquals(MotionTier.FULL, engine.output.value.tier)
        assertTrue(engine.output.value.atMs in 1_000L..1_100L)
        engine.stop()
    }
}
