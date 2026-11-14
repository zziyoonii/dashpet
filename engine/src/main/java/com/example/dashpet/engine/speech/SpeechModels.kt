package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType

/** 한 번에 전하는 한 문장. 문장은 항상 [SpeechCatalog] 의 고정 문장이다(개인정보, 점수 숫자 없음). */
data class Utterance(
    val eventType: EventType,
    val priority: SpeechPriority,
    /** 제한 없음일 때 쓰는 문장(말풍선과 음성 공통). */
    val text: String,
    /** 제한 중(음성만)에 쓰는 더 짧은 한 문장. */
    val shortText: String,
    val atMs: Long,
)

data class SpeechLine(val text: String, val shortText: String)

enum class StopReason { PREEMPTED, RESET }

/** 규칙이 내리는 지시. */
sealed interface SpeechCommand {
    /**
     * 말하라. [id] 는 출력의 상태 보고와 짝을 맞추는 번호다.
     * [showBubble] 은 모션 등급이 `FULL` 일 때만 true 이고 [useShortText] 는 그 반대다.
     */
    data class Speak(
        val id: Long,
        val utterance: Utterance,
        val variantIndex: Int,
        val showBubble: Boolean,
        val useShortText: Boolean,
    ) : SpeechCommand {
        init {
            require(showBubble == !useShortText) { "showBubble and useShortText must be opposite" }
        }

        /** 음성으로 읽을 문장. */
        val spokenText: String get() = if (useShortText) utterance.shortText else utterance.text
    }

    data class StopSpeaking(val reason: StopReason) : SpeechCommand

    data object HideBubble : SpeechCommand
}
