package com.example.dashpet.engine

/**
 * 엔진의 모든 임계값, 재발 간격, 지속 시간, 가중치(FR-023). 기본값은 data-model.md 의 초안이며
 * 테스트 주행 데이터를 본 뒤 조정한다. 테스트는 값을 바꿔서 주입할 수 있다.
 */
data class EngineConfig(
    // 판정 임계값 (m/s²)
    val hardAccelThresholdMps2: Double = 2.9,
    val hardBrakeThresholdMps2: Double = 3.9,
    val hardCornerThresholdMps2: Double = 3.9,
    /** 급커브는 이 속도 이상일 때만 판정한다. 15 km/h. */
    val cornerMinSpeedMps: Double = 15.0 / 3.6,
    /** 세기 정규화 상한 A_MAX. [확인 필요] 초안 값이며 캐릭터 표현 튜닝 때 조정한다(research.md R4). */
    val intensityMaxMps2: Double = 7.8,
    // 평활화와 확정
    val smoothingAlpha: Double = 0.4,
    val confirmSamples: Int = 2,
    val maxSampleGapMs: Long = 2000,
    // 재발 간격 (ms)
    val cooldownHardMs: Long = 3000,
    val cooldownWarningMs: Long = 10000,
    val cooldownLaneEvalMs: Long = 5000,
    val laneChangeLookbackMs: Long = 3000,
    // 감정 지속 시간 (ms)
    val startledDurationMs: Long = 3000,
    val dizzyDurationMs: Long = 3000,
    val squashedDurationMs: Long = 1200,
    val happyDurationMs: Long = 2000,
    // 점수
    val scoreWeights: Map<EventType, Int> = mapOf(
        EventType.LANE_DEPARTURE to -5,
        EventType.COLLISION_RISK to -5,
        EventType.NO_SIGNAL_LANE_CHANGE to -3,
        EventType.HARD_ACCEL to -2,
        EventType.HARD_BRAKE to -2,
        EventType.HARD_CORNER to -2,
        EventType.GOOD_LANE_CHANGE to 1,
        EventType.SEATBELT_OK to 2,
    ),
    val goodLaneChangeBonusCap: Int = 5,
    val happyScoreThreshold: Int = 80,
    // 모션
    val allowOneShot: Boolean = false,
) {
    init {
        require(smoothingAlpha > 0.0 && smoothingAlpha <= 1.0) { "smoothingAlpha must be in (0, 1]" }
        require(confirmSamples >= 1) { "confirmSamples must be >= 1" }
        require(intensityMaxMps2 > 0.0) { "intensityMaxMps2 must be positive" }
    }

    fun scoreWeight(type: EventType): Int = scoreWeights[type] ?: 0
}
