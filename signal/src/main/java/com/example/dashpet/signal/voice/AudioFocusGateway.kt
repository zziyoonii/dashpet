package com.example.dashpet.signal.voice

import kotlinx.coroutines.flow.Flow

/** 오디오 포커스 변경 4가지(AGENTS.md 7절). */
enum class FocusChange { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

/** 오디오 포커스 한 겹. 요청과 반납을 짝으로 세어 누수가 없는지 확인할 수 있게 한다(SC-009). */
interface AudioFocusGateway {
    val changes: Flow<FocusChange>

    /** 얻었으면 true. 얻지 못해도 [abandon] 으로 짝을 맞춘다. */
    fun request(): Boolean
    fun abandon()
    val requestCount: Int
    val abandonCount: Int
}
