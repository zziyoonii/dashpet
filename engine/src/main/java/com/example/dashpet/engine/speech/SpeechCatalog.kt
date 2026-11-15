package com.example.dashpet.engine.speech

import com.example.dashpet.engine.EventType

/**
 * 펫 성격에 따른 말투(specs/005). 듬직은 차분한 존댓말, 활발은 기운찬 반말, 애교는 다정한 존댓말이다.
 * `:pet` 의 `Personality` 와 같은 세 값이며, `:engine` 은 `:pet` 을 모르므로 `:app` 이 짝을 맞춘다.
 */
enum class SpeechTone { STURDY, LIVELY, CUTE }

/**
 * 이벤트 11종 x 변형 2의 고정 문장(specs/004 contracts/speech.md 1절)을 말투 3종으로 갖춘 것.
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

        /** 성격별 문장 세트. 듬직이 기본이다. */
        fun forTone(tone: SpeechTone): SpeechCatalog = SpeechCatalog(
            when (tone) {
                SpeechTone.STURDY -> DEFAULT_LINES
                SpeechTone.LIVELY -> LIVELY_LINES
                SpeechTone.CUTE -> CUTE_LINES
            },
        )

        /** 듬직: 차분한 존댓말. */
        val DEFAULT_LINES: Map<EventType, List<SpeechLine>> = mapOf(
            EventType.TRIP_START to listOf(l("출발해요. 오늘도 안전하게 가요.", "안전하게 가요."), l("시동 걸었어요. 천천히 출발해요.", "천천히 출발해요.")),
            EventType.SEATBELT_WARN to listOf(l("출발 전에 안전벨트부터 매요.", "벨트부터 매요."), l("벨트를 먼저 확인해요. 그다음 출발해요.", "벨트를 확인해요.")),
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

        /** 활발: 기운찬 반말. 짧은 문장은 느낌표 하나로 끝낸다. */
        val LIVELY_LINES: Map<EventType, List<SpeechLine>> = mapOf(
            EventType.TRIP_START to listOf(l("출발이다! 오늘도 신나게, 안전하게 가자!", "신나게 안전하게!"), l("시동 걸렸다! 씩씩하게 출발하자!", "씩씩하게 출발!")),
            EventType.SEATBELT_WARN to listOf(l("잠깐! 벨트부터 매자! 그다음에 출발이야!", "벨트부터!"), l("앗, 벨트 아직이야! 딸깍 하고 출발하자!", "벨트 먼저!")),
            EventType.SEATBELT_OK to listOf(l("벨트 확인 완료! 좋았어, 출발 준비 끝!", "벨트 좋았어!"), l("딸깍 소리 들었어! 벨트 최고야!", "벨트 최고야!")),
            EventType.HARD_ACCEL to listOf(l("우와, 출발이 너무 빨랐어! 살살 가자!", "살살 가자!"), l("가속이 세다! 조금만 부드럽게 가자!", "부드럽게 가자!")),
            EventType.HARD_BRAKE to listOf(l("으앗, 급정거! 앞차랑 거리를 더 두자!", "거리를 두자!"), l("끼익! 갑자기 멈췄어! 여유를 갖자!", "여유를 갖자!")),
            EventType.HARD_CORNER to listOf(l("으앗! 커브가 너무 급했어! 속도를 줄이자!", "속도를 줄이자!"), l("핑그르르! 커브는 천천히 돌자!", "천천히 돌자!")),
            EventType.LANE_DEPARTURE to listOf(l("앗, 차선 벗어났어! 앞을 보고 중앙으로!", "중앙으로 가자!"), l("차선이 흔들려! 정신 바짝 차리자!", "정신 바짝 차려!")),
            EventType.COLLISION_RISK to listOf(l("위험해! 앞차가 너무 가까워, 속도를 줄여!", "속도를 줄여!"), l("충돌 위험이야! 거리를 두고 천천히!", "거리를 두자!")),
            EventType.GOOD_LANE_CHANGE to listOf(l("깜빡이 켜고 차선 변경, 완벽했어!", "완벽했어!"), l("우와, 깔끔한 차선 변경이야!", "깔끔했어!")),
            EventType.NO_SIGNAL_LANE_CHANGE to listOf(l("깜빡이 먼저! 켜고 차선을 바꾸자!", "깜빡이 먼저!"), l("어이쿠, 깜빡이 깜빡했네! 다음엔 먼저 켜자!", "먼저 켜자!")),
            EventType.TRIP_END to listOf(l("도착! 오늘도 수고했어, 최고였어!", "도착했어, 수고했어!"), l("무사히 도착! 고마워, 또 달리자!", "무사히 도착했어!")),
        )

        /** 애교: 다정한 존댓말. 짧은 문장은 느낌표 하나로 끝낸다(물결표는 본문에만 쓴다). */
        val CUTE_LINES: Map<EventType, List<SpeechLine>> = mapOf(
            EventType.TRIP_START to listOf(l("출발이에요~ 오늘도 나랑 안전하게 가요!", "나랑 안전하게 가요!"), l("시동 걸었어요! 살살 출발해 줘요~", "살살 출발해요!")),
            EventType.SEATBELT_WARN to listOf(l("벨트부터 매 줘요~ 그래야 같이 달려요!", "벨트부터요!"), l("앗, 벨트가 아직이에요~ 먼저 매 줘요!", "벨트 먼저요!")),
            EventType.SEATBELT_OK to listOf(l("벨트 매 줘서 고마워요~ 든든해요!", "고마워요, 든든해요!"), l("딸깍! 벨트 잘 맸네요, 잘했어요~", "벨트 잘했어요!")),
            EventType.HARD_ACCEL to listOf(l("앗, 몸이 확 쏠렸어요. 살살 밟아 줘요~", "살살 밟아 줘요!"), l("출발이 너무 빨라서 놀랐어요. 천천히요~", "천천히 가요!")),
            EventType.HARD_BRAKE to listOf(l("깜짝이야, 갑자기 멈췄어요. 여유 있게 가요~", "여유 있게 가요!"), l("앗, 급브레이크예요. 거리를 조금만 더 둬요~", "거리를 더 둬요!")),
            EventType.HARD_CORNER to listOf(l("어지러워요. 커브는 살살 돌아요~", "살살 돌아요!"), l("몸이 휙 쏠렸어요. 커브에선 천천히요~", "천천히 돌아요!")),
            EventType.LANE_DEPARTURE to listOf(l("어지러워요. 차선 가운데로 돌아와 줘요~", "가운데로 와 줘요!"), l("차선이 흔들려서 무서워요. 앞을 봐 줘요~", "앞을 봐 줘요!")),
            EventType.COLLISION_RISK to listOf(l("앗, 앞차가 너무 가까워요! 거리를 둬 줘요!", "거리를 둬 줘요!"), l("무서워요, 충돌 위험이에요! 속도를 줄여요!", "속도를 줄여요!")),
            EventType.GOOD_LANE_CHANGE to listOf(l("깜빡이 켜고 바꿨네요, 잘했어요~ 멋져요!", "잘했어요, 멋져요!"), l("차선 변경이 부드러웠어요~ 최고예요!", "최고예요!")),
            EventType.NO_SIGNAL_LANE_CHANGE to listOf(l("깜빡이 켜는 걸 깜빡했어요~ 다음엔 먼저요!", "깜빡이 먼저요!"), l("차선 바꾸기 전엔 깜빡이를 켜 줘요~ 약속이에요!", "깜빡이를 켜 줘요!")),
            EventType.TRIP_END to listOf(l("도착했어요~ 오늘도 수고했어요, 고마워요!", "수고했어요, 고마워요!"), l("무사히 도착해서 기뻐요~ 또 같이 가요!", "무사히 도착했어요!")),
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
