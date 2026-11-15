package com.example.dashpet.engine

import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState

/**
 * 차선 변경 시점 직전 [EngineConfig.laneChangeLookbackMs] 안에 같은 방향 방향지시등이 켜져 있었는지로
 * 좋은 차선 변경과 깜빡이 없는 차선 변경을 구분한다. 평가 재발 간격은 두 종류가 함께 쓴다.
 */
internal class LaneChangeEventDetector(private val config: EngineConfig) {
    private var onSide: Side? = null
    private val lastOffMs = HashMap<Side, Long>()
    private var lastEvalMs: Long? = null

    fun onTurnSignal(atMs: Long, state: TurnState) {
        val next = when (state) {
            TurnState.OFF -> null
            TurnState.LEFT -> Side.LEFT
            TurnState.RIGHT -> Side.RIGHT
        }
        val prev = onSide
        if (prev != null && prev != next) lastOffMs[prev] = atMs
        onSide = next
    }

    fun onLaneChange(atMs: Long, side: Side): DrivingEvent? {
        val last = lastEvalMs
        if (last != null && atMs - last < config.cooldownLaneEvalMs) return null
        lastEvalMs = atMs
        val signaled = onSide == side ||
            lastOffMs[side]?.let { atMs - it <= config.laneChangeLookbackMs } == true
        val type = if (signaled) EventType.GOOD_LANE_CHANGE else EventType.NO_SIGNAL_LANE_CHANGE
        // 몸이 쏠리는 쪽: 우측 차선 변경 -> LEFT, 좌측 -> RIGHT (급커브 규칙과 같게). [확인 필요] 방향 규칙
        val direction = if (side == Side.RIGHT) Direction4.LEFT else Direction4.RIGHT
        return DrivingEvent(type, atMs, direction = direction, scoreDelta = config.scoreWeight(type))
    }
}
