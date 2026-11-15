package com.example.dashpet.signal.scenario

import com.example.dashpet.signal.BrakePedal
import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.LaneChange
import com.example.dashpet.signal.ParkedState
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Restriction
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.TurnSignal
import com.example.dashpet.signal.TurnState
import com.example.dashpet.signal.VehicleWarning
import com.example.dashpet.signal.WarningType

@DslMarker
annotation class ScenarioDsl

fun scenario(name: String, build: ScenarioBuilder.() -> Unit): Scenario =
    ScenarioBuilder(name).apply(build).build()

@ScenarioDsl
class ScenarioBuilder(private val name: String) {
    private val steps = mutableListOf<ScenarioStep>()

    /** [ms] 시각의 단계들을 적는다. 같은 시각 안에서는 적은 순서가 처리 순서다. */
    fun at(ms: Long, block: StepBuilder.() -> Unit) {
        StepBuilder(ms, steps).block()
    }

    fun build(): Scenario = Scenario(name, steps.toList())
}

@ScenarioDsl
class StepBuilder internal constructor(
    private val atMs: Long,
    private val steps: MutableList<ScenarioStep>,
) {
    private fun signal(s: RawSignal) {
        steps += SignalStep(atMs, s)
    }

    fun parked(parked: Boolean) = signal(ParkedState(atMs, parked))

    fun brakePedal(pressed: Boolean) = signal(BrakePedal(atMs, pressed))

    fun turnSignal(state: TurnState) = signal(TurnSignal(atMs, state))

    fun warning(type: WarningType, active: Boolean) = signal(VehicleWarning(atMs, type, active))

    fun laneChange(side: Side) = signal(LaneChange(atMs, side))

    fun restriction(state: RestrictionState) = signal(Restriction(atMs, state))

    /** 속도(m/s). */
    fun speed(mps: Number) = signal(Speed(atMs, mps.toFloat()))

    /** 방위각(도). */
    fun heading(deg: Number) = signal(Heading(atMs, deg.toFloat()))

    fun speedRamp(fromKmh: Number, toKmh: Number, durationMs: Long) {
        steps += SpeedRampStep(atMs, fromKmh.toDouble(), toKmh.toDouble(), durationMs)
    }

    /** 그 시점의 직전 방위각에 [deltaDeg] 를 더하는 상대 회전. */
    fun headingTurn(deltaDeg: Number, durationMs: Long) {
        steps += HeadingTurnStep(atMs, deltaDeg.toDouble(), durationMs)
    }
}
