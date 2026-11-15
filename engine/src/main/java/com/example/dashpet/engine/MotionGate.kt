package com.example.dashpet.engine

import com.example.dashpet.signal.RestrictionState

/**
 * 주행 제한 상태 -> 모션 등급. 등급 판단은 이곳 한 곳에서만 한다(헌장 I).
 * 제한 없음만 FULL 이고 제한 있음/알 수 없음은 STATIC 이다. ONE_SHOT 은 설정으로 켠 경우에만,
 * 제한 있음 상태에서 새 이벤트가 난 출력에 한해 낼 수 있다(기본 꺼짐, UNKNOWN 은 항상 STATIC).
 */
internal class MotionGate(private val config: EngineConfig) {
    fun tier(restriction: RestrictionState, oneShotCandidate: Boolean = false): MotionTier = when (restriction) {
        RestrictionState.NONE -> MotionTier.FULL
        RestrictionState.ACTIVE ->
            if (config.allowOneShot && oneShotCandidate) MotionTier.ONE_SHOT else MotionTier.STATIC
        RestrictionState.UNKNOWN -> MotionTier.STATIC
    }
}
