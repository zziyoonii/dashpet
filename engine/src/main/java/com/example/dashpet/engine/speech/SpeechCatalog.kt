package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType

/**
 * 이벤트 10종 x 변형 2의 고정 문장(specs/004 contracts/speech.md 1절). 말투는 차분한 존댓말이고
 * 점수, 이름, 위치, 숫자를 넣지 않는다. 문장 표현은 초안이며 바꿔도 `SpeechCatalogTest` 를 통과해야 한다.
 */
class SpeechCatalog(private val lines: Map<EventType, List<SpeechLine>> = DEFAULT_LINES) {
    init {
        require(EventType.entries.all { lines[it]?.size == 2 }) { "every EventType needs exactly 2 variants" }
    }

    fun variants(type: EventType): List<SpeechLine> = lines.getValue(type)

    /** 직전([lastVariantIndex], 없으면 null)과 다른 변형 번호. 난수를 쓰지 않는다. */
    fun pick(type: EventType, lastVariantIndex: Int?): Int {
        val size = variants(type).size
        return if (lastVariantIndex == null) 0 else (lastVariantIndex + 1) % size
    }

    companion object {
        private fun l(text: String, short: String) = SpeechLine(text, short)

        val DEFAULT_LINES: Map<EventType, List<SpeechLine>> = mapOf(
            EventType.TRIP_START to listOf(l("출발해요. 오늘도 안전하게 가요.", "안전하게 가요."), l("시동 걸었어요. 천천히 출발해요.", "천천히 출발해요.")),
            EventType.SEATBELT_OK to listOf(l("안전벨트를 확인했어요.", "벨트 확인했어요."), l("벨트가 잘 채워졌어요.", "벨트 좋아요.")),
            EventType.HARD_ACCEL to listOf(l("출발이 조금 급했어요. 부드럽게 가요.", "부드럽게 가요."), l("가속이 빨랐어요. 천천히 밟아요.", "천천히 밟아요.")),
            EventType.HARD_BRAKE to listOf(l("급하게 멈췄어요. 거리를 더 두어요.", "거리를 두어요."), l("갑자기 멈췄어요. 여유를 가져요.", "여유를 가져요.")),
            EventType.HARD_CORNER to listOf(l("커브가 급했어요. 천천히 돌아요.", "천천히 돌아요."), l("회전이 빨랐어요. 속도를 줄여요.", "속도를 줄여요.")),
            EventType.LANE_DEPARTURE to listOf(l("차선을 벗어났어요. 앞을 봐 주세요.", "차선을 확인해요."), l("차선이 흔들려요. 중앙을 맞춰요.", "중앙을 맞춰요.")),
            EventType.COLLISION_RISK to listOf(l("앞차와 가까워요. 거리를 두세요.", "거리를 두세요."), l("충돌 위험이 있어요. 속도를 줄이세요.", "속도를 줄이세요.")),
            EventType.GOOD_LANE_CHANGE to listOf(l("차선 변경이 깔끔했어요.", "깔끔했어요."), l("좋은 차선 변경이에요.", "차선 변경 좋아요.")),
            EventType.NO_SIGNAL_LANE_CHANGE to listOf(l("깜빡이를 켜고 차선을 바꿔요.", "깜빡이를 켜요."), l("차선 변경 전에 깜빡이를 켜요.", "깜빡이를 먼저 켜요.")),
            EventType.TRIP_END to listOf(l("도착했어요. 수고하셨어요.", "도착했어요."), l("무사히 도착했어요. 고마워요.", "무사히 도착했어요.")),
        )
    }
}

/** 화면 문장 길이 한도(AGENTS.md 4절). */
object SpeechLimits {
    const val MAX_CHARS = 120
    const val MAX_WORDS = 24
    const val MAX_LINE_CHARS = 80
    const val MAX_LINES = 3

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
}
