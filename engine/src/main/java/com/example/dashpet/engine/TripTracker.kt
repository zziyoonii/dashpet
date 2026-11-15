package com.example.dashpet.engine

/** 한 번의 주행 기록. 메모리에만 있고 위치 값은 담지 않는다. */
data class Trip(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val events: List<DrivingEvent>,
    val score: Int,
)

internal enum class TripPhase { UNKNOWN, PARKED, IN_TRIP, ARRIVING }

/**
 * 주행 수명주기(출발, 벨트 체결, 도착)와 기록. 수명주기: UNKNOWN -> IN_TRIP(이벤트 없음),
 * PARKED -> IN_TRIP(TRIP_START), IN_TRIP -> ARRIVING -> PARKED(속도 0 이 되면 TRIP_END).
 * 주행이 없을 때 들어온 이벤트는 기록하지 않는다.
 */
internal class TripTracker(private val config: EngineConfig) {
    private class Active(val id: String, val startedAtMs: Long, val keeper: ScoreKeeper) {
        val events = ArrayList<DrivingEvent>()
        var seatbeltWarned = false
        var seatbeltOk = false
    }

    private val completed = ArrayList<Trip>()
    private var active: Active? = null
    private var tripCount = 0

    var phase: TripPhase = TripPhase.UNKNOWN
        private set

    /** 마지막으로 기록한 이벤트까지 반영한 점수. */
    var lastScore: Int = 100
        private set

    /** 주차 상태 변화를 반영한다. 출발 또는 (이미 정지해 있을 때) 도착 이벤트를 돌려줄 수 있다. */
    fun onParked(parked: Boolean, atMs: Long, speedMps: Float?): DrivingEvent? {
        when (phase) {
            TripPhase.UNKNOWN ->
                if (parked) phase = TripPhase.PARKED else startTrip(atMs)
            TripPhase.PARKED -> if (!parked) {
                startTrip(atMs)
                return DrivingEvent(EventType.TRIP_START, atMs, scoreDelta = config.scoreWeight(EventType.TRIP_START))
            }
            TripPhase.IN_TRIP -> if (parked) {
                phase = TripPhase.ARRIVING
                // 주차 상태여도 속도가 0 이 아니면(또는 모르면) 도착이 아니다.
                if (speedMps == 0f) return tripEnd(atMs)
            }
            TripPhase.ARRIVING -> if (!parked) phase = TripPhase.IN_TRIP
        }
        return null
    }

    fun onSpeed(speedMps: Float, atMs: Long): DrivingEvent? =
        if (phase == TripPhase.ARRIVING && speedMps == 0f) tripEnd(atMs) else null

    /** 출발 이후 벨트 경고가 켜졌다가 꺼질 때만 주행당 한 번 `SEATBELT_OK`. */
    fun onSeatbelt(isActive: Boolean, atMs: Long): DrivingEvent? {
        val trip = active ?: return null
        if (isActive) {
            trip.seatbeltWarned = true
            return null
        }
        if (!trip.seatbeltWarned || trip.seatbeltOk) return null
        trip.seatbeltOk = true
        return DrivingEvent(EventType.SEATBELT_OK, atMs, scoreDelta = config.scoreWeight(EventType.SEATBELT_OK))
    }

    /** 이벤트에 점수를 반영해 주행 기록에 남긴다. 주행 중이 아니면 null(무시). */
    fun record(event: DrivingEvent): DrivingEvent? {
        val trip = active ?: return null
        val applied = trip.keeper.apply(event)
        trip.events += applied
        lastScore = trip.keeper.score
        if (applied.type == EventType.TRIP_END) {
            completed += snapshot(trip, endedAtMs = applied.atMs)
            active = null
            phase = TripPhase.PARKED
        }
        return applied
    }

    fun currentTrip(): Trip? = active?.let { snapshot(it, endedAtMs = null) }

    fun completedTrips(): List<Trip> = completed.toList()

    private fun startTrip(atMs: Long) {
        tripCount++
        active = Active("trip-$tripCount", atMs, ScoreKeeper(config))
        phase = TripPhase.IN_TRIP
    }

    private fun tripEnd(atMs: Long) =
        DrivingEvent(EventType.TRIP_END, atMs, scoreDelta = config.scoreWeight(EventType.TRIP_END))

    private fun snapshot(trip: Active, endedAtMs: Long?) =
        Trip(trip.id, trip.startedAtMs, endedAtMs, trip.events.toList(), trip.keeper.score)
}
