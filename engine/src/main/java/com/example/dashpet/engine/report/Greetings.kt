package com.example.dashpet.engine.report

import com.example.dashpet.engine.speech.SpeechTone

/** 온보딩이 끝난 뒤 펫이 하는 첫 인사. 이름이 들어가므로 이벤트 대사 목록과 따로 둔다. */
object Greetings {
    const val DEFAULT_NAME = "내 차"

    fun first(name: String, tone: SpeechTone): String {
        val n = name.trim().ifEmpty { DEFAULT_NAME }
        return when (tone) {
            SpeechTone.STURDY -> "나 이제 ${withYeyo(n)}. 오래오래 같이 달려요."
            SpeechTone.LIVELY -> "나 이제 ${withYa(n)}! 오래오래 같이 신나게 달리자!"
            SpeechTone.CUTE -> "나 이제 ${withYeyo(n)}~ 오래오래 같이 달려요!"
        }
    }

    /** "장수" -> "장수야", "콩" -> "콩이야". */
    fun withYa(name: String): String = name + if (hasBatchim(name)) "이야" else "야"

    /** "장수" -> "장수예요", "콩" -> "콩이에요". */
    fun withYeyo(name: String): String = name + if (hasBatchim(name)) "이에요" else "예요"

    /** 마지막 글자가 받침 있는 한글이면 true. 한글이 아니면 받침이 없는 것으로 본다. */
    private fun hasBatchim(name: String): Boolean {
        val c = name.lastOrNull() ?: return false
        if (c !in '가'..'힣') return false
        return (c - '가') % 28 != 0
    }
}
