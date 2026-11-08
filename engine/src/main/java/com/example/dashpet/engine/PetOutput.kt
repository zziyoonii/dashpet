package com.example.dashpet.engine

import com.example.dashpet.signal.TurnState

enum class MotionTier { STATIC, ONE_SHOT, FULL }

/**
 * 엔진 출력. 제한 상태나 방향지시등이 바뀌면 이벤트가 없어도 새 값이 나간다.
 * `STATIC` 이면 소비하는 쪽은 반복 모션, 물리 반응, 파티클을 쓰지 않는다(헌장 I).
 */
data class PetOutput(
    val mood: Mood,
    val intensity: Float,
    val direction: Direction4?,
    val tier: MotionTier,
    val turnSignal: TurnState,
    val atMs: Long,
) {
    /** 시각([atMs])을 빼고 같은 상태인지 비교한다. 래퍼가 값이 바뀔 때만 내보내는 데 쓴다. */
    fun sameStateAs(other: PetOutput): Boolean = copy(atMs = other.atMs) == other

    companion object {
        /** 신호가 오기 전의 출력. 제한 상태를 모르므로 정적이다. */
        val INITIAL = PetOutput(Mood.IDLE, 0f, null, MotionTier.STATIC, TurnState.OFF, 0)
    }
}
