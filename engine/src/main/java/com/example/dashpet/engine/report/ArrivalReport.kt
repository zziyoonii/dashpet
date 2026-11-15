package com.example.dashpet.engine.report

import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Trip
import com.example.dashpet.engine.speech.SpeechTone
import kotlin.math.abs

/**
 * 도착 리포트(P단 뒤에 보이는 요약). 점수, 지난 주행과의 차이, 눈에 띄는 장면 글, 한 마디를 담는다.
 * 위치, 시각, 이름 같은 개인정보는 담지 않는다. 하이라이트 리플레이는 이번 범위가 아니며 [highlights] 는 글이다.
 */
data class ArrivalReport(
    val score: Int,
    /** 지난 주행 점수가 있을 때 점수 차이(이번 - 지난). 없으면 null. */
    val delta: Int?,
    val deltaLine: String,
    val highlights: List<String>,
    val oneLiner: String,
)

object ArrivalReports {
    const val MAX_HIGHLIGHTS = 3
    const val BAND_GREAT = 90
    const val BAND_GOOD = 70

    private val labels = listOf(
        EventType.COLLISION_RISK to "충돌 위험",
        EventType.LANE_DEPARTURE to "차선 이탈",
        EventType.HARD_BRAKE to "급제동",
        EventType.HARD_CORNER to "급커브",
        EventType.HARD_ACCEL to "급가속",
        EventType.NO_SIGNAL_LANE_CHANGE to "깜빡이 없는 차선 변경",
        EventType.GOOD_LANE_CHANGE to "깜빡이 켠 차선 변경",
        EventType.SEATBELT_OK to "벨트 확인",
    )

    fun build(trip: Trip, previousScore: Int?, tone: SpeechTone): ArrivalReport {
        val score = trip.score.coerceIn(0, 100)
        val delta = previousScore?.let { score - it }
        val counts = trip.events.groupingBy { it.type }.eachCount()
        val highlights = labels.mapNotNull { (type, label) -> counts[type]?.let { "$label ${it}회" } }
            .take(MAX_HIGHLIGHTS)
            .ifEmpty { listOf("부드럽게 달렸어요") }
        return ArrivalReport(score, delta, deltaLine(delta), highlights, oneLiner(score, tone))
    }

    private fun deltaLine(delta: Int?): String = when {
        delta == null -> "첫 주행 기록이에요."
        delta > 0 -> "지난 주행보다 ${delta}점 올랐어요."
        delta < 0 -> "지난 주행보다 ${abs(delta)}점 낮아졌어요."
        else -> "지난 주행과 같아요."
    }

    /** 점수 구간과 말투로 고른 고정 한 마디. 숫자를 넣지 않고 비난하지 않는다("내 차가 힘들어한다"에서 멈춘다). */
    private fun oneLiner(score: Int, tone: SpeechTone): String {
        val band = when {
            score >= BAND_GREAT -> 0
            score >= BAND_GOOD -> 1
            else -> 2
        }
        return LINES.getValue(tone)[band]
    }

    private val LINES: Map<SpeechTone, List<String>> = mapOf(
        SpeechTone.STURDY to listOf(
            "오늘 정말 안정적으로 달렸어요. 수고하셨어요.",
            "무난하게 달렸어요. 급한 구간만 조금 더 부드럽게 가요.",
            "오늘은 조금 힘들었어요. 다음엔 여유 있게 가요.",
        ),
        SpeechTone.LIVELY to listOf(
            "와, 오늘 완전 최고였어! 이 느낌 그대로 가자!",
            "오늘 꽤 좋았어! 급한 구간만 살살 가면 완벽해!",
            "으아, 오늘은 좀 흔들렸어! 내일은 같이 천천히 가자!",
        ),
        SpeechTone.CUTE to listOf(
            "오늘 너무 잘했어요~ 칭찬해 줄래요!",
            "오늘도 수고했어요~ 급한 곳만 살살 가요!",
            "오늘은 조금 무서웠어요. 내일은 살살 달려요~",
        ),
    )
}
