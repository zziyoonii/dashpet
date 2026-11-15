package com.example.dashpet.pet

/** 화면에 보이는 성격 이름. */
val Personality.label: String
    get() = when (this) {
        Personality.STURDY -> "듬직"
        Personality.LIVELY -> "활발"
        Personality.CUTE -> "애교"
    }

/** 성격을 고를 때 보이는 한 줄 설명. */
val Personality.description: String
    get() = when (this) {
        Personality.STURDY -> "믿음직하고 느긋해요"
        Personality.LIVELY -> "씩씩하고 신나요"
        Personality.CUTE -> "다정하고 사랑스러워요"
    }

/**
 * 추천 결과. [reason] 은 근거가 되는 낱말이 있을 때만 있고, 없으면 기본(듬직)을 쓴 것이다.
 * 이름과 뜻은 이 객체에 담지 않는다(낱말 일부만 [matched] 에 남는다).
 */
data class PersonalitySuggestion(
    val personality: Personality,
    val matched: List<String>,
    val reason: String?,
)

/**
 * 이름과 뜻에서 성격을 추천하는 규칙 기반 사전. 심사 문서의 "LLM 이 성격 키워드를 제안"을 대신한다
 * (LLM SDK 응답 확인 전까지 `[확인 필요]`). 같은 입력에는 같은 결과가 나온다. 어떤 입력도 예외를 던지지 않는다.
 * 낱말 목록은 초안이며 기획자가 고칠 수 있다.
 */
object PersonalitySuggester {
    private val keywords: Map<Personality, List<String>> = mapOf(
        Personality.STURDY to listOf("튼튼", "든든", "듬직", "믿음", "오래", "장수", "느긋", "묵직", "단단", "안전", "차분", "편안", "우직", "안정", "바위", "태산"),
        Personality.LIVELY to listOf("번개", "질주", "신나", "활기", "활발", "씩씩", "에너지", "날쌘", "점프", "로켓", "스피드", "쌩쌩", "불꽃", "힘차", "용감", "호랑"),
        Personality.CUTE to listOf("귀여", "귀엽", "사랑", "하트", "애교", "아기", "콩이", "별이", "별빛", "행복", "복덩", "몽실", "몽이", "뽀짝", "솜", "구름", "달콤", "예쁘", "초코"),
    )

    private val adjective = mapOf(
        Personality.STURDY to "듬직한",
        Personality.LIVELY to "활발한",
        Personality.CUTE to "애교 있는",
    )

    fun suggest(name: String, meaning: String): PersonalitySuggestion {
        val text = "$name $meaning"
        val hits = Personality.entries.associateWith { p -> keywords.getValue(p).filter { text.contains(it) } }
        // 가장 많이 맞은 성격. 동점이면 기본값(듬직) 쪽이 먼저 오도록 열거 순서를 쓴다.
        val best = Personality.entries.maxByOrNull { hits.getValue(it).size } ?: Personality.STURDY
        val matched = hits.getValue(best)
        if (matched.isEmpty()) return PersonalitySuggestion(Personality.STURDY, emptyList(), null)
        val reason = "‘${matched.first()}’ 에서 ${adjective.getValue(best)} 느낌이 나요."
        return PersonalitySuggestion(best, matched, reason)
    }
}
