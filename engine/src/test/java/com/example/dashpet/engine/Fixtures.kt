package com.example.dashpet.engine

import com.example.dashpet.signal.BrakePedal
import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Speed
import com.example.dashpet.signal.scenario.Scenario

/** 시나리오를 expand() 해서 EngineCore 에 순서대로 넣고 모든 단계를 모은다. */
fun runScenario(scenario: Scenario, config: EngineConfig = EngineConfig()): List<EngineStep> =
    runSignals(scenario.expand(), config)

fun runSignals(signals: List<RawSignal>, config: EngineConfig = EngineConfig()): List<EngineStep> {
    val core = EngineCore(config)
    return signals.map { core.process(it) }
}

fun List<EngineStep>.allEvents(): List<DrivingEvent> = flatMap { it.events }

fun List<EngineStep>.eventsOf(type: EventType): List<DrivingEvent> = allEvents().filter { it.type == type }

fun List<DrivingEvent>.ofType(type: EventType): List<DrivingEvent> = filter { it.type == type }

/** AccelEventDetector 에 신호를 순서대로 넣고 나온 이벤트를 모은다. */
internal fun AccelEventDetector.feed(signals: List<RawSignal>): List<DrivingEvent> = signals.flatMap {
    when (it) {
        is Speed -> onSpeed(it.timestampMs, it.speedMps)
        is Heading -> onHeading(it.timestampMs, it.headingDeg)
        is BrakePedal -> { onBrakePedal(it.pressed); emptyList() }
        else -> emptyList()
    }
}

/** (시각 ms, 속도 m/s) 쌍을 Speed 신호로 만든다. */
fun speeds(vararg samples: Pair<Long, Float>): List<RawSignal> = samples.map { Speed(it.first, it.second) }
