package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType

/** 한 번에 전하는 한 문장. 문장은 항상 [SpeechCatalog] 의 고정 문장이다(개인정보, 점수 숫자 없음). */
data class Utterance(
    val eventType: EventType,
    val priority: SpeechPriority,
    /** 말풍선에 보이는 문장. 제한 없음일 때는 음성도 이 문장을 읽는다. */
    val text: String,
    /** 제한 중 음성이 읽는 더 짧은 한 문장(말풍선에는 쓰지 않는다). */
    val shortText: String,
    val atMs: Long,
)

data class SpeechLine(val text: String, val shortText: String)

enum class StopReason { PREEMPTED, RESET }

/** 규칙이 내리는 지시. */
sealed interface SpeechCommand {
    /**
     * 말하라. [id] 는 출력의 상태 보고와 짝을 맞추는 번호다.
     * [showBubble] 은 제한 중에도 true 다(전체 문장, 2026-11-15 결정). [useShortText] 는 음성에만 쓰이며
     * 모션 등급이 `FULL` 이 아니면 true 라서 제한 중에는 짧은 한 문장을 읽는다.
     */
    data class Speak(
        val id: Long,
        val utterance: Utterance,
        val variantIndex: Int,
        val showBubble: Boolean,
        val useShortText: Boolean,
        /** 소리를 낼지. 같은 종류의 음성 간격 안이면 false 이고 말풍선만 보인다. */
        val voice: Boolean = true,
    ) : SpeechCommand {
        /** 음성으로 읽을 문장. */
        val spokenText: String get() = if (useShortText) utterance.shortText else utterance.text
    }

    data class StopSpeaking(val reason: StopReason) : SpeechCommand

    data object HideBubble : SpeechCommand
}
