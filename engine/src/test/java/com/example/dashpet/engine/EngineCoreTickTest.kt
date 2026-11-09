package com.example.dashpet.engine

import com.example.dashpet.signal.MockSignalSource
import com.example.dashpet.signal.Pacing
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Tick
import com.example.dashpet.signal.VehicleWarning
import com.example.dashpet.signal.WarningType
import com.example.dashpet.signal.scenario.DemoScenarios
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 002 R12: Tick 은 시간만 진행한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineCoreTickTest {
    private fun drivingCore(): EngineCore = EngineCore().also { it.process(ParkedState(0, false)) }

    @Test
    fun tickProducesNoEventsAndOnlyAdvancesTime() {
        val core = drivingCore()
        val step = core.process(Tick(500))
        assertTrue(step.events.isEmpty())
        assertEquals(500L, step.output.atMs)
    }

    @Test
    fun ticksAloneReturnMoodToIdleAfterItsDuration() {
        val core = drivingCore()
        val start = core.process(VehicleWarning(1_000, WarningType.LANE_DEPARTURE, true))
        assertEquals(Mood.DIZZY, start.output.mood)
        assertEquals(Mood.DIZZY, core.process(Tick(3_900)).output.mood)
        assertEquals(Mood.IDLE, core.process(Tick(4_000)).output.mood) // dizzyDurationMs = 3000
    }

    @Test
    fun tickWithEarlierTimeIsIgnoredWithoutError() {
        val core = drivingCore()
        core.process(Tick(2_000))
        val step = core.process(Tick(1_000))
        assertTrue(step.events.isEmpty())
        assertEquals(2_000L, step.output.atMs)
        assertEquals(2_000L, core.process(Tick(2_000)).output.atMs)
    }

    @Test
    fun ticksDoNotAffectScoreOrTripRecords() {
        val signals = DemoScenarios.demoCommute.expand()
        val plain = EngineCore().also { core -> signals.forEach { core.process(it) } }
        val ticked = EngineCore().also { core ->
            signals.forEach { core.process(it); core.process(Tick(it.timestampMs)) }
            core.process(Tick(signals.last().timestampMs + 3_000))
        }
        assertEquals(plain.completedTrips(), ticked.completedTrips())
        assertEquals(94, ticked.completedTrips().single().score)
    }

    @Test
    fun demoWithTicksKeepsSevenEventsAndScore94AndSquashedReturnsToIdle() = runTest {
        val source = MockSignalSource(DemoScenarios.demoCommute, Pacing.IMMEDIATE)
        val signals = mutableListOf<RawSignal>()
        val job = launch { source.signals.collect { signals += it } }
        source.start()
        advanceUntilIdle()
        job.cancel()
        assertTrue(signals.any { it is Tick })

        val core = EngineCore()
        val steps = signals.map { core.process(it) }
        assertEquals(
            listOf(
                EventType.TRIP_START, EventType.SEATBELT_OK, EventType.HARD_CORNER, EventType.GOOD_LANE_CHANGE,
                EventType.LANE_DEPARTURE, EventType.HARD_BRAKE, EventType.TRIP_END,
            ),
            steps.allEvents().map { it.type },
        )
        assertEquals(94, core.completedTrips().single().score)

        // 22초 급커브의 SQUASHED 는 틱으로 만료되어 늦어도 24초 안에 IDLE 이 된다.
        val corner = steps.first { s -> s.events.any { it.type == EventType.HARD_CORNER } }
        assertEquals(Mood.SQUASHED, corner.output.mood)
        val back = steps.first { it.output.atMs > corner.output.atMs && it.output.mood == Mood.IDLE }
        assertTrue("IDLE at ${back.output.atMs}", back.output.atMs <= 24_000L)
    }
}
