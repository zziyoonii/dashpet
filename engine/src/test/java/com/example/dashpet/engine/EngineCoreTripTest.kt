package com.example.dashpet.engine

import com.example.dashpet.signal.Side
import com.example.dashpet.signal.WarningType
import com.example.dashpet.signal.scenario.scenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** US4 시나리오 1~4. */
class EngineCoreTripTest {
    @Test
    fun laneChangeWithoutSignalKeepsMoodAndOnlyRecordsAndScores() {
        val core = EngineCore()
        core.process(com.example.dashpet.signal.ParkedState(0, false))
        val step = core.process(com.example.dashpet.signal.LaneChange(2_000, Side.LEFT))
        assertEquals(EventType.NO_SIGNAL_LANE_CHANGE, step.events.single().type)
        assertEquals(Mood.IDLE, step.output.mood)
        val trip = core.currentTrip()!!
        assertEquals(97, trip.score)
        assertEquals(EventType.NO_SIGNAL_LANE_CHANGE, trip.events.last().type)
        assertEquals(-3, trip.events.last().scoreDelta)
    }

    @Test
    fun noSignalLaneChangeDoesNotInterruptCurrentMood() {
        val core = EngineCore()
        core.process(com.example.dashpet.signal.ParkedState(0, false))
        core.process(com.example.dashpet.signal.VehicleWarning(1_000, WarningType.LANE_DEPARTURE, true))
        val step = core.process(com.example.dashpet.signal.LaneChange(1_500, Side.LEFT))
        assertEquals(Mood.DIZZY, step.output.mood)
        assertEquals(Mood.IDLE, core.advanceTo(4_000).output.mood)
    }

    @Test
    fun eventsWithoutTripAreNotReported() {
        val core = EngineCore()
        val step = core.process(com.example.dashpet.signal.VehicleWarning(1_000, WarningType.LANE_DEPARTURE, true))
        assertTrue(step.events.isEmpty())
        assertEquals(Mood.IDLE, step.output.mood)
        assertNull(core.currentTrip())
    }

    @Test
    fun tripEndWithScoreAtLeast80IsHappy() {
        val steps = runScenario(
            scenario("happy-end") {
                at(0) { parked(true); speed(0) }
                at(1_000) { parked(false) }
                at(20_000) { parked(true) }
            },
        )
        val end = steps.last()
        assertEquals(EventType.TRIP_END, end.events.single().type)
        assertEquals(Mood.HAPPY, end.output.mood)
    }

    @Test
    fun tripEndWithScoreBelow80IsNotHappy() {
        // 재발 간격을 0 으로 해서 차선 이탈 -5 를 5번(점수 75) 쌓는다
        val config = EngineConfig(cooldownWarningMs = 0)
        val steps = runScenario(
            scenario("low-score") {
                at(0) { parked(true); speed(0) }
                at(1_000) { parked(false) }
                for (i in 0 until 5) {
                    at(2_000L + i * 200) { warning(WarningType.LANE_DEPARTURE, true) }
                    at(2_100L + i * 200) { warning(WarningType.LANE_DEPARTURE, false) }
                }
                at(20_000) { parked(true) }
            },
            config,
        )
        val end = steps.last()
        assertEquals(EventType.TRIP_END, end.events.single().type)
        assertEquals(75, EngineCoreTripHelper.lastScore(steps))
        assertEquals(Mood.IDLE, end.output.mood)
    }

    @Test
    fun currentAndCompletedTrips() {
        val core = EngineCore()
        assertNull(core.currentTrip())
        core.process(com.example.dashpet.signal.ParkedState(0, true))
        core.process(com.example.dashpet.signal.Speed(0, 0f))
        core.process(com.example.dashpet.signal.ParkedState(1_000, false))
        val running = core.currentTrip()!!
        assertEquals(1_000L, running.startedAtMs)
        assertNull(running.endedAtMs)
        assertEquals(listOf(EventType.TRIP_START), running.events.map { it.type })
        assertTrue(core.completedTrips().isEmpty())

        core.process(com.example.dashpet.signal.ParkedState(5_000, true))
        assertNull(core.currentTrip())
        val done = core.completedTrips().single()
        assertEquals(5_000L, done.endedAtMs)
        assertEquals(listOf(EventType.TRIP_START, EventType.TRIP_END), done.events.map { it.type })
    }

    @Test
    fun parkedWhileMovingArrivesOnlyWhenSpeedReachesZero() {
        val core = EngineCore()
        core.process(com.example.dashpet.signal.ParkedState(0, false))
        core.process(com.example.dashpet.signal.Speed(100, 5f))
        val parked = core.process(com.example.dashpet.signal.ParkedState(200, true))
        assertTrue(parked.events.isEmpty())
        val stopped = core.process(com.example.dashpet.signal.Speed(300, 0f))
        assertEquals(EventType.TRIP_END, stopped.events.single().type)
    }

    // ---- 출발 전 안전벨트 검문 ----

    @Test
    fun departureWithSeatbeltWarningOnSaysSeatbeltFirstThenOkWhenItTurnsOff() {
        val steps = runScenario(
            scenario("departure-check") {
                at(0) { parked(true); speed(0) }
                at(500) { warning(WarningType.SEATBELT, true) } // 주차 중에 켜진 경고는 이벤트가 아니다
                at(1_000) { parked(false) }
                at(3_000) { warning(WarningType.SEATBELT, false) }
            },
        )
        assertEquals(listOf(EventType.TRIP_START, EventType.SEATBELT_WARN, EventType.SEATBELT_OK), steps.allEvents().map { it.type })
        assertTrue(steps.first { it.output.atMs == 500L }.events.isEmpty())
        // 출발과 같은 시각에 난다.
        val departure = steps.first { it.events.any { e -> e.type == EventType.TRIP_START } }
        assertEquals(listOf(EventType.TRIP_START, EventType.SEATBELT_WARN), departure.events.map { it.type })
    }

    @Test
    fun departureWithoutSeatbeltWarningHasNoCheck() {
        val steps = runScenario(
            scenario("departure-clean") {
                at(0) { parked(true); speed(0); warning(WarningType.SEATBELT, false) }
                at(1_000) { parked(false) }
            },
        )
        assertEquals(listOf(EventType.TRIP_START), steps.allEvents().map { it.type })
    }

    @Test
    fun departureWithoutAnySeatbeltValueHasNoCheck() { // 벨트 경고를 지원하지 않는 차량(에뮬레이터)
        val steps = runScenario(
            scenario("departure-unsupported") {
                at(0) { parked(true); speed(0) }
                at(1_000) { parked(false) }
            },
        )
        assertEquals(listOf(EventType.TRIP_START), steps.allEvents().map { it.type })
    }

    @Test
    fun seatbeltWarningThatTurnsOnAfterDepartureStillGivesOnlyOk() { // 기존 동작 유지
        val steps = runScenario(
            scenario("warn-after") {
                at(0) { parked(true); speed(0) }
                at(1_000) { parked(false) }
                at(2_000) { warning(WarningType.SEATBELT, true) }
                at(3_000) { warning(WarningType.SEATBELT, false) }
            },
        )
        assertEquals(listOf(EventType.TRIP_START, EventType.SEATBELT_OK), steps.allEvents().map { it.type })
    }

    @Test
    fun departureCheckHappensOncePerTripAndDoesNotChangeTheScore() {
        val steps = runScenario(
            scenario("two-trips") {
                at(0) { parked(true); speed(0); warning(WarningType.SEATBELT, true) }
                at(1_000) { parked(false) }
                at(2_000) { parked(true); speed(0) }
                at(3_000) { parked(false) } // 벨트 경고가 아직 켜져 있으면 다음 출발에도 검문한다
            },
        )
        assertEquals(2, steps.allEvents().count { it.type == EventType.SEATBELT_WARN })
        assertEquals(0, steps.allEvents().first { it.type == EventType.SEATBELT_WARN }.scoreDelta)
    }

    @Test
    fun seatbeltFlowGivesHappyMoodAndPoints() {
        val steps = runScenario(
            scenario("seatbelt") {
                at(0) { parked(true); speed(0) }
                at(1_000) { parked(false) }
                at(1_000) { warning(WarningType.SEATBELT, true) }
                at(3_000) { warning(WarningType.SEATBELT, false) }
            },
        )
        val ok = steps.last()
        assertEquals(EventType.SEATBELT_OK, ok.events.single().type)
        assertEquals(Mood.HAPPY, ok.output.mood)
    }
}

private object EngineCoreTripHelper {
    /** TRIP_END 시점까지의 점수 합(100 + scoreDelta). */
    fun lastScore(steps: List<EngineStep>): Int = 100 + steps.allEvents().sumOf { it.scoreDelta }
}
