package com.example.dashpet.signal.scenario

import com.example.dashpet.signal.Heading
import com.example.dashpet.signal.RawSignal
import com.example.dashpet.signal.Speed

/** 시나리오 단계. 시각(ms)과 다음 중 하나: 단일 신호, 속도 램프, 방향 회전. */
sealed interface ScenarioStep {
    val atMs: Long
}

data class SignalStep(override val atMs: Long, val signal: RawSignal) : ScenarioStep {
    init {
        require(signal.timestampMs == atMs) { "signal timestamp must equal step time" }
    }
}

data class SpeedRampStep(
    override val atMs: Long,
    val fromKmh: Double,
    val toKmh: Double,
    val durationMs: Long,
) : ScenarioStep {
    init {
        require(durationMs > 0) { "durationMs must be positive: $durationMs" }
        require(fromKmh >= 0 && toKmh >= 0) { "speeds must be >= 0" }
    }
}

data class HeadingTurnStep(
    override val atMs: Long,
    val deltaDeg: Double,
    val durationMs: Long,
) : ScenarioStep {
    init {
        require(durationMs > 0) { "durationMs must be positive: $durationMs" }
    }
}

/** 이름과 시간순 단계 목록. [expand] 는 부수 효과 없는 순수 함수다. */
data class Scenario(val name: String, val steps: List<ScenarioStep>) {
    init {
        steps.zipWithNext().forEach { (a, b) ->
            require(b.atMs >= a.atMs) { "step times must not decrease: ${a.atMs} -> ${b.atMs}" }
        }
    }

    /**
     * 램프와 회전을 [SAMPLE_INTERVAL_MS] 간격(양 끝 포함) 신호로 풀어 시간순으로 돌려준다.
     * 같은 시각이면 단계 입력 순서를 유지한다(안정 정렬).
     */
    fun expand(): List<RawSignal> {
        val out = ArrayList<RawSignal>()
        // headingTurn 의 기준 방위각. 이전 방위 정보가 없으면 0도에서 시작한다.
        var heading = 0.0
        for (step in steps) {
            when (step) {
                is SignalStep -> {
                    out += step.signal
                    (step.signal as? Heading)?.let { heading = it.headingDeg.toDouble() }
                }
                is SpeedRampStep -> sampleTimes(step.atMs, step.durationMs).forEach { t ->
                    val kmh = lerp(step.fromKmh, step.toKmh, t - step.atMs, step.durationMs)
                    out += Speed(t, (kmh / 3.6).toFloat())
                }
                is HeadingTurnStep -> {
                    val from = heading
                    sampleTimes(step.atMs, step.durationMs).forEach { t ->
                        val deg = lerp(from, from + step.deltaDeg, t - step.atMs, step.durationMs)
                        out += Heading(t, deg.toFloat())
                    }
                    heading = ((from + step.deltaDeg) % 360.0 + 360.0) % 360.0
                }
            }
        }
        return out.sortedBy { it.timestampMs }
    }

    private fun sampleTimes(startMs: Long, durationMs: Long): List<Long> {
        val times = ArrayList<Long>()
        var t = startMs
        while (t < startMs + durationMs) {
            times += t
            t += SAMPLE_INTERVAL_MS
        }
        times += startMs + durationMs
        return times
    }

    private fun lerp(from: Double, to: Double, elapsedMs: Long, durationMs: Long): Double =
        from + (to - from) * elapsedMs / durationMs

    companion object {
        const val SAMPLE_INTERVAL_MS = 100L
    }
}
