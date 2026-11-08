package com.example.dashpet.engine

import com.example.dashpet.signal.BrakePedal
import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.LaneChange
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.TurnSignal
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.VehicleWarning
import com.example.dashpet.signal.WarningType

/** 한 번의 호출 결과. [events] 는 이 호출에서 확정된 이벤트, [output] 은 호출 직후의 출력. */
data class EngineStep(
    val events: List<DrivingEvent>,
    val output: PetOutput,
)

/**
 * 신호를 받아 이벤트를 판정하고 펫 출력을 정하는 동기 코어. 시계를 읽지 않으며
 * 시간은 신호의 timestampMs 와 [advanceTo] 로만 진행한다. 스레드 안전하지 않다.
 * 입력 때문에 예외를 던지지 않는다(잘못된 값은 그 신호를 버린다).
 */
class EngineCore(private val config: EngineConfig = EngineConfig()) {
    private val accel = AccelEventDetector(config)
    private val warnings = WarningEventDetector(config)
    private val laneChanges = LaneChangeEventDetector(config)
    private val trips = TripTracker(config)
    private val moods = MoodMachine(config)
    private val gate = MotionGate(config)

    private var nowMs = Long.MIN_VALUE
    private var lastSpeedMps: Float? = null
    private var restriction = RestrictionState.UNKNOWN // 입력 전에는 제한이 있는 것으로 취급
    private var turnSignal = TurnState.OFF

    fun process(signal: RawSignal): EngineStep {
        // 시간은 되돌리지 않는다. 직전보다 작은 시각의 신호는 무시한다.
        if (signal.timestampMs < nowMs) return EngineStep(emptyList(), output(oneShotCandidate = false))
        nowMs = signal.timestampMs
        moods.advanceTo(nowMs)

        val candidates = ArrayList<DrivingEvent>()
        val t = signal.timestampMs
        when (signal) {
            is Speed -> {
                if (!(signal.speedMps >= 0f)) return EngineStep(emptyList(), output(false)) // 음수, NaN 은 버림
                lastSpeedMps = signal.speedMps
                candidates += accel.onSpeed(t, signal.speedMps)
                trips.onSpeed(signal.speedMps, t)?.let(candidates::add)
            }
            is Heading -> candidates += accel.onHeading(t, signal.headingDeg)
            is BrakePedal -> accel.onBrakePedal(signal.pressed)
            is ParkedState -> trips.onParked(signal.parked, t, lastSpeedMps)?.let(candidates::add)
            is VehicleWarning ->
                if (signal.type == WarningType.SEATBELT) {
                    trips.onSeatbelt(signal.active, t)?.let(candidates::add)
                } else {
                    warnings.onWarning(t, signal.type, signal.active)?.let(candidates::add)
                }
            is TurnSignal -> {
                turnSignal = signal.state
                laneChanges.onTurnSignal(t, signal.state)
            }
            is LaneChange -> laneChanges.onLaneChange(t, signal.direction)?.let(candidates::add)
            is Restriction -> restriction = signal.state
        }
        val events = candidates.mapNotNull(::commit)
        return EngineStep(events, output(oneShotCandidate = events.isNotEmpty()))
    }

    fun advanceTo(timeMs: Long): EngineStep {
        if (timeMs >= nowMs) {
            nowMs = timeMs
            moods.advanceTo(nowMs)
        }
        return EngineStep(emptyList(), output(oneShotCandidate = false))
    }

    fun currentTrip(): Trip? = trips.currentTrip()

    fun completedTrips(): List<Trip> = trips.completedTrips()

    /** 주행 기록과 점수에 반영하고 감정에 전달한다. 주행 중이 아니면 이벤트를 버린다. */
    private fun commit(candidate: DrivingEvent): DrivingEvent? {
        val event = trips.record(candidate) ?: return null
        moods.onEvent(event, trips.lastScore)
        return event
    }

    private fun output(oneShotCandidate: Boolean): PetOutput {
        val pet = moods.state
        return PetOutput(
            mood = pet.mood,
            intensity = pet.intensity,
            direction = pet.direction,
            tier = gate.tier(restriction, oneShotCandidate),
            turnSignal = turnSignal,
            atMs = if (nowMs == Long.MIN_VALUE) 0 else nowMs,
        )
    }
}
