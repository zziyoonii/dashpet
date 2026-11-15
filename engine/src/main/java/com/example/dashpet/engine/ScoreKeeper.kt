package com.example.dashpet.engine

/**
 * 주행 한 번의 점수. 100 에서 시작해 [DrivingEvent.scoreDelta] 를 모두 더하고 조회할 때만 0~100 으로 자른다.
 * `GOOD_LANE_CHANGE` 가점은 주행당 합이 [EngineConfig.goodLaneChangeBonusCap] 를 넘지 않으며,
 * 넘는 분은 이벤트는 그대로 두되 점수 변화를 0 으로 한다.
 */
internal class ScoreKeeper(private val config: EngineConfig) {
    private var goodLaneBonus = 0

    /** 자르기 전의 합계. */
    var rawScore: Int = 100
        private set

    /** 조회용 점수(0~100). */
    val score: Int get() = rawScore.coerceIn(0, 100)

    /** 가감점 표를 적용하고, 실제 반영된 점수 변화를 담은 이벤트를 돌려준다. */
    fun apply(event: DrivingEvent): DrivingEvent {
        var delta = config.scoreWeight(event.type)
        if (event.type == EventType.GOOD_LANE_CHANGE && delta > 0) {
            delta = delta.coerceAtMost((config.goodLaneChangeBonusCap - goodLaneBonus).coerceAtLeast(0))
            goodLaneBonus += delta
        }
        rawScore += delta
        return event.copy(scoreDelta = delta)
    }
}
