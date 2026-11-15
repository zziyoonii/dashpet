package com.example.dashpet.signal

import com.example.dashpet.signal.scenario.Scenario
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

enum class Pacing {
    /** 신호 시각 차이만큼 기다린 뒤 방출한다(데모, 영상 촬영). */
    REAL_TIME,

    /** 기다리지 않고 순서대로 방출한다(테스트). */
    IMMEDIATE,
}

/**
 * 시나리오를 재생하는 신호 소스. 어느 [Pacing] 이든 내용과 순서는 같다.
 * 시나리오의 첫 신호 시각부터 마지막 신호 시각 + [tailMs] 까지 [tickIntervalMs] 간격으로 [Tick] 을 섞어 방출한다.
 * 같은 시각이면 시나리오 신호가 먼저, [Tick] 이 나중이다. [tickIntervalMs] 가 0 이하이면 틱을 내지 않는다.
 * `start()` 전에는 방출하지 않고, `stop()` 뒤에는 멈추며, 다시 `start()` 하면 처음부터 시작한다.
 */
class MockSignalSource(
    private val scenario: Scenario,
    private val pacing: Pacing = Pacing.REAL_TIME,
    private val tickIntervalMs: Long = DEFAULT_TICK_INTERVAL_MS,
    private val tailMs: Long = DEFAULT_TAIL_MS,
) : VehicleSignalSource {
    private data class Run(val running: Boolean, val epoch: Int)

    private val state = MutableStateFlow(Run(running = false, epoch = 0))
    private val script: List<RawSignal> by lazy { withTicks(scenario.expand()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val signals: Flow<RawSignal> = state.transformLatest { run ->
        if (!run.running) return@transformLatest
        var previousMs = 0L
        for (signal in script) {
            if (pacing == Pacing.REAL_TIME) delay(signal.timestampMs - previousMs)
            previousMs = signal.timestampMs
            emit(signal)
        }
        // 끝까지 재생했으면 다음 start() 가 처음부터 다시 시작할 수 있게 한다.
        state.compareAndSet(run, run.copy(running = false))
    }

    /** 신호 목록에 [Tick] 을 시각순으로 합친다. 같은 시각이면 신호가 먼저(안정, 결정적). */
    private fun withTicks(signals: List<RawSignal>): List<RawSignal> {
        if (tickIntervalMs <= 0L || signals.isEmpty()) return signals
        val endMs = signals.last().timestampMs + tailMs.coerceAtLeast(0L)
        val out = ArrayList<RawSignal>(signals.size + ((endMs - signals.first().timestampMs) / tickIntervalMs).toInt() + 1)
        var i = 0
        var tickMs = signals.first().timestampMs
        while (tickMs <= endMs) {
            while (i < signals.size && signals[i].timestampMs <= tickMs) out += signals[i++]
            out += Tick(tickMs)
            tickMs += tickIntervalMs
        }
        while (i < signals.size) out += signals[i++] // 마지막 틱 뒤 남은 신호(tail 이 음수인 경우 등)
        return out
    }

    override fun start() {
        state.update { if (it.running) it else Run(running = true, epoch = it.epoch + 1) }
    }

    override fun stop() {
        state.update { it.copy(running = false) }
    }

    companion object {
        const val DEFAULT_TICK_INTERVAL_MS = 100L
        const val DEFAULT_TAIL_MS = 3_000L
    }
}
