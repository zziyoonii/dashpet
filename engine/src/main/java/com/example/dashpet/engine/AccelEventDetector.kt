package com.example.dashpet.engine

import kotlin.math.abs

/** 급가속, 급제동, 급커브를 판정한다. 임계값을 연속 [EngineConfig.confirmSamples] 회 넘으면 확정한다. */
internal class AccelEventDetector(private val config: EngineConfig) {
    private val kinematics = Kinematics(config)
    private val cooldown = Cooldown()
    private var brakePressed = false

    private var accelCount = 0
    private var brakeCount = 0
    private var cornerCount = 0
    private var cornerSign = 0

    fun onBrakePedal(pressed: Boolean) {
        brakePressed = pressed
    }

    fun onSpeed(atMs: Long, speedMps: Float): List<DrivingEvent> {
        val a = kinematics.onSpeed(atMs, speedMps.toDouble())
        if (a == null) {
            accelCount = 0
            brakeCount = 0
            return emptyList()
        }
        accelCount = if (a >= config.hardAccelThresholdMps2) accelCount + 1 else 0
        brakeCount = if (a <= -config.hardBrakeThresholdMps2) brakeCount + 1 else 0
        val out = ArrayList<DrivingEvent>(1)
        if (accelCount >= config.confirmSamples) {
            confirm(EventType.HARD_ACCEL, atMs, a, config.hardAccelThresholdMps2, Direction4.BACK)?.let(out::add)
        }
        // 급제동은 확정 시점에 브레이크 페달이 눌려 있어야 한다.
        if (brakeCount >= config.confirmSamples && brakePressed) {
            confirm(EventType.HARD_BRAKE, atMs, a, config.hardBrakeThresholdMps2, Direction4.FRONT)?.let(out::add)
        }
        return out
    }

    fun onHeading(atMs: Long, headingDeg: Float): List<DrivingEvent> {
        val lateral = kinematics.onHeading(atMs, headingDeg.toDouble())
        val speed = kinematics.latestSpeedMps
        if (lateral == null || speed == null) {
            cornerCount = 0
            return emptyList()
        }
        val sign = if (lateral > 0) 1 else -1
        val over = abs(lateral) >= config.hardCornerThresholdMps2 && speed >= config.cornerMinSpeedMps
        cornerCount = when {
            !over -> 0
            sign == cornerSign -> cornerCount + 1
            else -> 1 // 꺾는 방향이 바뀌면 다시 센다
        }
        cornerSign = if (over) sign else 0
        if (cornerCount < config.confirmSamples) return emptyList()
        // 몸이 쏠리는 쪽: 우회전(방위각 증가) -> LEFT, 좌회전(방위각 감소) -> RIGHT.
        // [확인 필요] 방위 부호와 데모 영상 기대 동작(research.md 미결 항목 1)
        val direction = if (sign > 0) Direction4.LEFT else Direction4.RIGHT
        return listOfNotNull(confirm(EventType.HARD_CORNER, atMs, lateral, config.hardCornerThresholdMps2, direction))
    }

    private fun confirm(
        type: EventType,
        atMs: Long,
        accel: Double,
        threshold: Double,
        direction: Direction4,
    ): DrivingEvent? {
        if (!cooldown.tryAcquire(type, atMs, config.cooldownHardMs)) return null
        val intensity = clamp01((abs(accel) - threshold) / (config.intensityMaxMps2 - threshold))
        return DrivingEvent(type, atMs, intensity, direction, config.scoreWeight(type))
    }
}
