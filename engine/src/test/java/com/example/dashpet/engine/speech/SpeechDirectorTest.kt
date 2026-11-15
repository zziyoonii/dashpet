package com.example.dashpet.engine.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.speech.SpeechCommand.HideBubble
import com.example.dashpet.engine.speech.SpeechCommand.Speak
import com.example.dashpet.engine.speech.SpeechCommand.StopSpeaking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDirectorTest {
    private val config = SpeechConfig()
    private fun director() = SpeechDirector(config)
    private fun ev(type: EventType, at: Long = 0) = DrivingEvent(type, at)

    private fun SpeechDirector.speakOf(cmds: List<SpeechCommand>) = cmds.filterIsInstance<Speak>().single()

    @Test fun fullTierSpeaksWithBubble() {
        val d = director()
        val s = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        assertTrue(s.showBubble); assertFalse(s.useShortText)
        assertEquals("출발이 조금 급했어요. 부드럽게 가요.", s.spokenText)
    }

    @Test fun deterministic() {
        assertEquals(
            director().onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 5),
            director().onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 5),
        )
    }

    @Test fun staticTierShowsFullBubbleAndSpeaksShortText() {
        val d = director()
        val s = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.STATIC, 0))
        assertTrue(s.showBubble); assertTrue(s.useShortText) // 말풍선은 전체 문장, 음성만 짧게
        assertEquals("출발이 조금 급했어요. 부드럽게 가요.", s.utterance.text)
        assertEquals("부드럽게 가요.", s.spokenText)
    }

    @Test fun oneShotTierShowsBubbleAndSpeaksShortText() {
        val d = director()
        val s = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.ONE_SHOT, 0))
        assertTrue(s.showBubble); assertTrue(s.useShortText)
    }

    @Test fun leavingFullDoesNotHideTheBubble() {
        val d = director()
        d.onOutput(MotionTier.FULL, 0)
        d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 3)
        assertEquals(emptyList<SpeechCommand>(), d.onOutput(MotionTier.STATIC, 4)) // 제한이 걸려도 지우지 않는다
    }

    @Test fun cooldownSuppressesSameType() {
        val d = director()
        val first = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        d.onSpeechEnded(first.id, 1000)
        assertEquals(emptyList<SpeechCommand>(), d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 7_999))
        assertEquals(1, d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 8_000).filterIsInstance<Speak>().size)
    }

    @Test fun cooldownPerPriority() {
        val d = director()
        val danger = d.speakOf(d.onEvent(ev(EventType.COLLISION_RISK), MotionTier.FULL, 0))
        d.onSpeechEnded(danger.id, 100)
        assertEquals(emptyList<SpeechCommand>(), d.onEvent(ev(EventType.COLLISION_RISK), MotionTier.FULL, 9_999))
        assertEquals(1, d.onEvent(ev(EventType.COLLISION_RISK), MotionTier.FULL, 10_000).filterIsInstance<Speak>().size)
    }

    @Test fun alternatesVariantAfterCooldown() {
        val d = director()
        val a = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        d.onSpeechEnded(a.id, 100)
        val b = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 20_000))
        assertNotEquals(a.variantIndex, b.variantIndex)
        assertNotEquals(a.spokenText, b.spokenText)
    }

    @Test fun higherPriorityPreemptsAndLowerQueues() {
        val d = director()
        val praise = d.speakOf(d.onEvent(ev(EventType.GOOD_LANE_CHANGE), MotionTier.FULL, 0))
        val cmds = d.onEvent(ev(EventType.COLLISION_RISK), MotionTier.FULL, 100)
        assertEquals(StopSpeaking(StopReason.PREEMPTED), cmds[0])
        val danger = cmds[1] as Speak
        assertEquals(EventType.COLLISION_RISK, danger.utterance.eventType)
        // 끊긴 말의 종료 보고는 무시된다
        assertEquals(emptyList<SpeechCommand>(), d.onSpeechEnded(praise.id, 150))
        // 같거나 낮은 우선순위는 대기
        assertEquals(emptyList<SpeechCommand>(), d.onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 200))
        val next = d.onSpeechEnded(danger.id, 500).filterIsInstance<Speak>().single()
        assertEquals(EventType.HARD_BRAKE, next.utterance.eventType)
    }

    @Test fun queueKeepsHigherThenMoreRecent() {
        val d = director()
        val first = d.speakOf(d.onEvent(ev(EventType.COLLISION_RISK), MotionTier.FULL, 0))
        d.onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 10)
        d.onEvent(ev(EventType.GOOD_LANE_CHANGE), MotionTier.FULL, 20) // 더 낮음: 버림
        d.onEvent(ev(EventType.HARD_CORNER), MotionTier.FULL, 30) // 같은 우선순위, 더 최근: 교체
        val next = d.onSpeechEnded(first.id, 40).filterIsInstance<Speak>().single()
        assertEquals(EventType.HARD_CORNER, next.utterance.eventType)
    }

    @Test fun staleQueuedUtteranceDropped() {
        val d = director()
        val first = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        d.onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 100)
        assertEquals(emptyList<SpeechCommand>(), d.onSpeechEnded(first.id, 100 + config.queueMaxAgeMs + 1))
    }

    @Test fun queuedUtteranceUsesCurrentTier() {
        val d = director()
        val first = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        d.onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 10)
        d.onOutput(MotionTier.STATIC, 20)
        val next = d.onSpeechEnded(first.id, 30).filterIsInstance<Speak>().single()
        assertTrue(next.showBubble) // 말풍선은 제한 중에도 보인다
        assertTrue(next.useShortText) // 대기하는 사이 제한이 걸리면 음성은 짧은 문장
    }

    @Test fun tripEndSpokenOnce() {
        val d = director()
        val a = d.speakOf(d.onEvent(ev(EventType.TRIP_END), MotionTier.FULL, 0))
        d.onSpeechEnded(a.id, 10)
        assertEquals(emptyList<SpeechCommand>(), d.onEvent(ev(EventType.TRIP_END), MotionTier.FULL, 100_000))
    }

    @Test fun tripEndIsSpokenAgainOnTheNextTrip() {
        val d = director()
        val a = d.speakOf(d.onEvent(ev(EventType.TRIP_END), MotionTier.FULL, 0))
        d.onSpeechEnded(a.id, 10)
        val start = d.speakOf(d.onEvent(ev(EventType.TRIP_START), MotionTier.STATIC, 1_000)) // 다음 주행이 시작됐다
        d.onSpeechEnded(start.id, 1_500)
        assertEquals(1, d.onEvent(ev(EventType.TRIP_END), MotionTier.FULL, 400_000).filterIsInstance<Speak>().size)
    }

    @Test fun resetClearsEverything() {
        val d = director()
        val a = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        d.onEvent(ev(EventType.HARD_BRAKE), MotionTier.FULL, 10)
        assertEquals(listOf(HideBubble, StopSpeaking(StopReason.RESET)), d.reset())
        assertEquals(emptyList<SpeechCommand>(), d.onSpeechEnded(a.id, 20)) // 대기가 남지 않음
        // 쿨다운도 초기화
        assertEquals(1, d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 30).filterIsInstance<Speak>().size)
        assertEquals(emptyList<SpeechCommand>(), director().reset())
    }

    @Test fun bubbleExpiredClearsVisibility() {
        val d = director()
        val a = d.speakOf(d.onEvent(ev(EventType.HARD_ACCEL), MotionTier.FULL, 0))
        d.onSpeechEnded(a.id, 1)
        d.onBubbleExpired()
        assertEquals(emptyList<SpeechCommand>(), d.reset()) // 이미 사라진 말풍선은 다시 지우지 않음
    }
}
