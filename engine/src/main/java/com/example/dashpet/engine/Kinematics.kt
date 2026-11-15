package com.example.dashpet.engine

/**
 * 속도와 방위각 샘플에서 평활화된 앞뒤/옆 가속도를 계산한다(research.md R3).
 * 시계를 읽지 않고 호출자가 넘기는 시각만 쓴다.
 */
internal class Kinematics(private val config: EngineConfig) {
    private var speedAtMs: Long? = null
    private var speedSmoothed = 0.0

    private var headingAtMs: Long? = null
    private var headingRaw = 0.0
    private var headingUnwrapped = 0.0
    private var headingSmoothed = 0.0

    /** 가장 최근 평활 속도(m/s). 아직 속도를 받지 못했으면 null. */
    var latestSpeedMps: Double? = null
        private set

    /**
     * 앞뒤 가속도(m/s², 가속이 양수). 첫 샘플, 간격이 [EngineConfig.maxSampleGapMs] 를 넘는 샘플,
     * 같은 시각 샘플은 null 이다. 간격이 넘으면 이 샘플이 새 기준선이 된다.
     */
    fun onSpeed(atMs: Long, speedMps: Double): Double? {
        val prevAt = speedAtMs
        if (prevAt == null || atMs - prevAt > config.maxSampleGapMs) {
            speedAtMs = atMs
            speedSmoothed = speedMps
            latestSpeedMps = speedMps
            return null
        }
        val dtMs = atMs - prevAt
        if (dtMs <= 0) return null
        val prev = speedSmoothed
        speedSmoothed = config.smoothingAlpha * speedMps + (1 - config.smoothingAlpha) * prev
        speedAtMs = atMs
        latestSpeedMps = speedSmoothed
        return (speedSmoothed - prev) / (dtMs / 1000.0)
    }

    /**
     * 옆 가속도(m/s², 방위각 증가 = 우회전이 양수) = 방위 변화율(rad/s) × 최신 평활 속도.
     * 속도 정보가 없으면 샘플을 버리고 기준선을 초기화한다.
     */
    fun onHeading(atMs: Long, headingDeg: Double): Double? {
        val speed = latestSpeedMps
        if (speed == null) {
            headingAtMs = null
            return null
        }
        val prevAt = headingAtMs
        if (prevAt == null || atMs - prevAt > config.maxSampleGapMs) {
            headingAtMs = atMs
            headingRaw = headingDeg
            headingUnwrapped = headingDeg
            headingSmoothed = headingDeg
            return null
        }
        val dtMs = atMs - prevAt
        if (dtMs <= 0) return null
        headingUnwrapped += angleDiffDeg(headingRaw, headingDeg)
        headingRaw = headingDeg
        val prev = headingSmoothed
        headingSmoothed = config.smoothingAlpha * headingUnwrapped + (1 - config.smoothingAlpha) * prev
        headingAtMs = atMs
        val rateRadPerSec = Math.toRadians((headingSmoothed - prev) / (dtMs / 1000.0))
        return rateRadPerSec * speed
    }

    companion object {
        /** [from] 에서 [to] 로 가는 최단 각도 차를 −180 초과 180 이하로 돌려준다. */
        fun angleDiffDeg(from: Double, to: Double): Double {
            var d = (to - from) % 360.0
            if (d > 180.0) d -= 360.0
            if (d <= -180.0) d += 360.0
            return d
        }
    }
}

internal fun clamp01(x: Double): Float = if (x.isNaN()) 0f else x.coerceIn(0.0, 1.0).toFloat()
