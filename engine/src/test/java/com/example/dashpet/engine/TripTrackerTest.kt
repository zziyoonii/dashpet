package com.example.dashpet.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrackerTest {
    private fun tracker() = TripTracker(EngineConfig())

    @Test
    fun parkedToDrivingProducesTripStart() {
        val t = tracker()
        assertNull(t.onParked(true, 0, 0f))
        assertNull(t.currentTrip())
        val e = t.onParked(false, 1_000, 0f)!!
        assertEquals(EventType.TRIP_START, e.type)
        assertEquals(1_000L, e.atMs)
        assertEquals(1_000L, t.currentTrip()!!.startedAtMs)
    }

    @Test
    fun firstInputDrivingStartsTripWithoutEvents() {
        val t = tracker()
        assertNull(t.onParked(false, 500, 10f))
        assertNotNull(t.currentTrip())
        // 출발 이벤트가 없으니 벨트 경고가 켜졌다 꺼져도 ... 출발 이후이므로 체결로 친다
        assertNull(t.onSeatbelt(true, 600))
        assertEquals(EventType.SEATBELT_OK, t.onSeatbelt(false, 700)!!.type)
    }

    @Test
    fun seatbeltOkOnlyWhenWarningTurnsOnAndOffAfterDeparture() {
        val t = tracker()
        t.onParked(true, 0, 0f)
        // 출발 전 해제는 체결로 치지 않는다
        assertNull(t.onSeatbelt(false, 100))
        t.onSeatbelt(true, 200) // 출발 전 켜짐
        t.onParked(false, 1_000, 0f)
        assertNull(t.onSeatbelt(false, 1_100)) // 출발 이후 켜진 적 없이 꺼짐
        assertNull(t.onSeatbelt(true, 1_200))
        assertEquals(EventType.SEATBELT_OK, t.onSeatbelt(false, 1_300)!!.type)
        // 주행당 1회
        t.onSeatbelt(true, 1_400)
        assertNull(t.onSeatbelt(false, 1_500))
    }

    @Test
    fun seatbeltIgnoredWithoutTrip() {
        val t = tracker()
        t.onParked(true, 0, 0f)
        assertNull(t.onSeatbelt(true, 1))
        assertNull(t.onSeatbelt(false, 2))
    }

    @Test
    fun arrivalWaitsUntilSpeedIsZero() {
        val t = tracker()
        t.onParked(false, 0, 0f)
        assertNull(t.onParked(true, 1_000, 5f)) // 주차 상태여도 속도가 0 이 아니면 도착이 아니다
        assertEquals(TripPhase.ARRIVING, t.phase)
        assertNull(t.onSpeed(2f, 1_100))
        val end = t.onSpeed(0f, 1_200)!!
        assertEquals(EventType.TRIP_END, end.type)
        assertEquals(1_200L, end.atMs)
    }

    @Test
    fun arrivalIsImmediateWhenAlreadyStopped() {
        val t = tracker()
        t.onParked(false, 0, 0f)
        assertEquals(EventType.TRIP_END, t.onParked(true, 1_000, 0f)!!.type)
    }

    @Test
    fun arrivalWithUnknownSpeedWaitsForSpeed() {
        val t = tracker()
        t.onParked(false, 0, null)
        assertNull(t.onParked(true, 1_000, null))
        assertEquals(EventType.TRIP_END, t.onSpeed(0f, 1_100)!!.type)
    }

    @Test
    fun drivingAgainWhileArrivingCancelsArrivalWithoutNewTrip() {
        val t = tracker()
        t.onParked(false, 0, 0f)
        t.onParked(true, 1_000, 5f)
        assertNull(t.onParked(false, 1_100, 5f))
        assertEquals(TripPhase.IN_TRIP, t.phase)
        assertNull(t.onSpeed(0f, 1_200)) // 더는 도착 대기가 아님
    }

    @Test
    fun eventsWithoutTripAreIgnored() {
        val t = tracker()
        assertNull(t.record(DrivingEvent(EventType.HARD_ACCEL, 0)))
        t.onParked(true, 0, 0f)
        assertNull(t.record(DrivingEvent(EventType.HARD_ACCEL, 10)))
        assertNull(t.currentTrip())
        assertTrue(t.completedTrips().isEmpty())
    }

    @Test
    fun tripRecordsAllEventsWithScoreAndIsCompleted() {
        val t = tracker()
        t.onParked(true, 0, 0f)
        val start = t.record(t.onParked(false, 1_000, 0f)!!)!!
        val brake = t.record(DrivingEvent(EventType.HARD_BRAKE, 2_000, 0.5f, Direction4.FRONT))!!
        val lane = t.record(DrivingEvent(EventType.NO_SIGNAL_LANE_CHANGE, 3_000))!!
        assertEquals(listOf(0, -2, -3), listOf(start, brake, lane).map { it.scoreDelta })
        val running = t.currentTrip()!!
        assertNull(running.endedAtMs)
        assertEquals(95, running.score)
        assertEquals(listOf(EventType.TRIP_START, EventType.HARD_BRAKE, EventType.NO_SIGNAL_LANE_CHANGE),
            running.events.map { it.type })
        assertEquals(0.5f, running.events[1].intensity, 0f)

        val end = t.record(t.onParked(true, 4_000, 0f)!!)!!
        assertEquals(EventType.TRIP_END, end.type)
        assertNull(t.currentTrip())
        assertEquals(TripPhase.PARKED, t.phase)
        val done = t.completedTrips().single()
        assertEquals(4_000L, done.endedAtMs)
        assertEquals(95, done.score)
        assertEquals(4, done.events.size)
        assertEquals(95, t.lastScore)
    }

    @Test
    fun secondTripGetsNewIdAndFreshScore() {
        val t = tracker()
        t.onParked(true, 0, 0f)
        t.record(t.onParked(false, 100, 0f)!!)
        t.record(DrivingEvent(EventType.LANE_DEPARTURE, 200))
        t.record(t.onParked(true, 300, 0f)!!)
        t.record(t.onParked(false, 400, 0f)!!)
        val second = t.currentTrip()!!
        val first = t.completedTrips().single()
        assertNotEquals(first.id, second.id)
        assertEquals(95, first.score)
        assertEquals(100, second.score)
    }
}
