package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color

/** 펫의 성격. 몸 물리와 표정 규칙에 영향을 준다(data-model.md). */
enum class Personality {
    /** 듬직. */
    STURDY,

    /** 활발. 기쁨의 점프 초기 속도가 더 크다. */
    LIVELY,

    /** 애교. 볼터치를 항상 그린다. */
    CUTE,
}

/** 엔진 출력과 별개로 넣는 배경 감정(FR-019). */
enum class AmbientMood { HOT, COLD, RAIN, QUIET }

/**
 * 펫 외형. 값이 없으면 기본값을 쓴다(FR-021). 어떤 입력도 예외를 던지지 않는다.
 * 이름이 비어 있으면 [displayName] 이 기본 이름을 돌려준다.
 */
data class PetAppearance(
    val name: String = DEFAULT_NAME,
    val bodyColor: Color = DEFAULT_BODY_COLOR,
    val personality: Personality = Personality.STURDY,
) {
    /** 화면에 표시할 이름. 비어 있거나 공백뿐이면 [DEFAULT_NAME]. */
    val displayName: String get() = name.ifBlank { DEFAULT_NAME }

    companion object {
        const val DEFAULT_NAME = "내 차"

        /** 시안 기본색 #4F7CFF. */
        val DEFAULT_BODY_COLOR = Color(0xFF4F7CFF)
    }
}
