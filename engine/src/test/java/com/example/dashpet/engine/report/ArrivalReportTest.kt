package com.example.dashpet.engine.report

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Trip
import com.example.dashpet.engine.speech.SpeechTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 도착 리포트: P단 뒤 점수, 지난 주행과의 차이, 눈에 띄는 장면, 한 마디(specs/005). 리플레이는 이번 범위가 아니다. */
class ArrivalReportTest {
    private fun trip(score: Int, vararg types: EventType) = Trip(
        id = "t", startedAtMs = 0, endedAtMs = 1000,
        events = types.mapIndexed { i, t -> DrivingEvent(t, i * 100L) }, score = score,
    )

    private fun build(t: Trip, prev: Int? = null, tone: SpeechTone = SpeechTone.STURDY) = ArrivalReports.build(t, prev, tone)

    @Test fun scoreIsTheTripScore() {
        assertEquals(92, build(trip(92)).score)
    }

    @Test fun scoreIsClampedTo0To100() {
        assertEquals(100, build(trip(120)).score)
        assertEquals(0, build(trip(-5)).score)
    }

    @Test fun deltaLines() {
        assertEquals("지난 주행보다 4점 올랐어요.", build(trip(92), prev = 88).deltaLine)
        assertEquals("지난 주행보다 6점 낮아졌어요.", build(trip(82), prev = 88).deltaLine)
        assertEquals("지난 주행과 같아요.", build(trip(88), prev = 88).deltaLine)
        assertEquals("첫 주행 기록이에요.", build(trip(88), prev = null).deltaLine)
    }

    @Test fun highlightsCountEventsAndPutProblemsFirst() {
        val r = build(trip(80, EventType.GOOD_LANE_CHANGE, EventType.HARD_CORNER, EventType.HARD_CORNER, EventType.LANE_DEPARTURE))
        assertEquals(listOf("차선 이탈 1회", "급커브 2회", "깜빡이 켠 차선 변경 1회"), r.highlights)
    }

    @Test fun highlightsAreLimitedToThree() {
        val r = build(trip(60, EventType.COLLISION_RISK, EventType.LANE_DEPARTURE, EventType.HARD_BRAKE, EventType.HARD_CORNER, EventType.HARD_ACCEL))
        assertEquals(3, r.highlights.size)
        assertEquals("충돌 위험 1회", r.highlights.first())
    }

    @Test fun tripStartAndEndAreNotHighlights() {
        val r = build(trip(100, EventType.TRIP_START, EventType.TRIP_END))
        assertEquals(listOf("부드럽게 달렸어요"), r.highlights)
    }

    @Test fun oneLinerDependsOnScoreBandAndTone() {
        val lines = SpeechTone.entries.flatMap { tone -> listOf(95, 80, 50).map { build(trip(it), tone = tone).oneLiner } }
        assertEquals("말투 3종 x 구간 3개가 모두 달라야 한다", 9, lines.toSet().size)
        lines.forEach { assertTrue(it, it.isNotBlank() && it.length <= 60) }
    }

    @Test fun bandBoundaries() {
        assertEquals(build(trip(90)).oneLiner, build(trip(100)).oneLiner)
        assertNotEquals(build(trip(90)).oneLiner, build(trip(89)).oneLiner)
        assertEquals(build(trip(70)).oneLiner, build(trip(89)).oneLiner)
        assertNotEquals(build(trip(70)).oneLiner, build(trip(69)).oneLiner)
    }

    @Test fun lowScoreWordingDoesNotBlameTheDriver() {
        SpeechTone.entries.forEach { tone ->
            val line = build(trip(40), tone = tone).oneLiner
            listOf("못", "나쁜", "실패", "벌점", "혼").forEach { bad -> assertTrue("$tone: $line", !line.contains(bad)) }
        }
    }

    @Test fun noNumbersInOneLinerSoItNeverContradictsTheScore() {
        SpeechTone.entries.forEach { tone ->
            listOf(95, 80, 50).forEach { assertTrue(build(trip(it), tone = tone).oneLiner.none { c -> c.isDigit() }) }
        }
    }

    @Test fun previousScoreNullGivesNullDeltaNumber() {
        assertNull(build(trip(80), prev = null).delta)
        assertEquals(4, build(trip(92), prev = 88).delta)
    }
}
