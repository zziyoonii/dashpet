package com.example.dashpet.engine.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.speech.SpeechCommand.Speak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 이벤트가 말하지 못한 이유가 로그로 남는다(연타했을 때 4번째 이후 이벤트가 말하지 않던 일의 원인 추적). 문장 본문은 남기지 않는다(FR-019, FR-020). */
class SpeechDirectorSkipLogTest {
    private val config = SpeechConfig()
    private val logs = mutableListOf<String>()
    private fun director() = SpeechDirector(config, log = { logs += it })
    private fun ev(type: EventType, at: Long = 0) = DrivingEvent(type, at)
    private fun SpeechDirector.say(type: EventType, now: Long, tier: MotionTier = MotionTier.FULL) = onEvent(ev(type, now), tier, now)
    private fun idOf(cmds: List<SpeechCommand>) = cmds.filterIsInstance<Speak>().single().id

    @Test fun cooldownIsLoggedWithRemainingTime() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        assertEquals(emptyList<SpeechCommand>(), d.say(EventType.HARD_ACCEL, 2_000))
        // 경고 쿨다운은 8000 ms 라서 2000 ms 뒤에는 6000 ms 가 남는다
        assertEquals(listOf("skip event=HARD_ACCEL reason=cooldown remainingMs=${config.cooldownFor(SpeechPriority.WARN) - 2_000}"), logs)
    }

    @Test fun tripEndIsOnlySpokenOnce() {
        val d = director()
        val id = idOf(d.say(EventType.TRIP_END, 0))
        d.onSpeechEnded(id, 500)
        d.say(EventType.TRIP_END, 600_000)
        assertEquals(listOf("skip event=TRIP_END reason=already-spoken"), logs)
    }

    @Test fun eventWhileSpeakingIsLoggedAsQueued() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        d.say(EventType.HARD_BRAKE, 100)
        assertEquals(listOf("queued event=HARD_BRAKE behind=HARD_ACCEL"), logs)
    }

    @Test fun lowerPriorityThanTheQueuedOneIsDropped() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        d.say(EventType.HARD_BRAKE, 100) // 경고가 대기
        logs.clear()
        d.say(EventType.SEATBELT_OK, 200) // 칭찬은 경고보다 낮다
        assertEquals(listOf("skip event=SEATBELT_OK reason=lower-than-queued queued=HARD_BRAKE"), logs)
    }

    @Test fun newerEventOfSameRankReplacesTheQueuedOne() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        d.say(EventType.HARD_BRAKE, 100)
        logs.clear()
        d.say(EventType.HARD_CORNER, 200)
        assertEquals(
            listOf("skip event=HARD_BRAKE reason=replaced-in-queue by=HARD_CORNER", "queued event=HARD_CORNER behind=HARD_ACCEL"),
            logs,
        )
    }

    @Test fun preemptionIsLoggedAndTheQueuedEventIsDropped() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        d.say(EventType.HARD_BRAKE, 100)
        logs.clear()
        d.say(EventType.COLLISION_RISK, 200)
        assertEquals(
            listOf("skip event=HARD_BRAKE reason=dropped-by-preemption by=COLLISION_RISK", "preempt event=COLLISION_RISK over=HARD_ACCEL"),
            logs,
        )
    }

    @Test fun preemptionWithNothingQueuedOnlyLogsThePreemption() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        logs.clear()
        d.say(EventType.COLLISION_RISK, 200)
        assertEquals(listOf("preempt event=COLLISION_RISK over=HARD_ACCEL"), logs)
    }

    @Test fun staleQueuedEventIsLoggedWithTheWaitingTime() {
        val d = director()
        val id = idOf(d.say(EventType.HARD_ACCEL, 0))
        d.say(EventType.HARD_BRAKE, 100)
        logs.clear()
        // 대기 한도(2000 ms)를 1 ms 넘겨서 끝난다
        assertEquals(emptyList<SpeechCommand>(), d.onSpeechEnded(id, 100 + config.queueMaxAgeMs + 1))
        assertEquals(listOf("skip event=HARD_BRAKE reason=queue-expired waitedMs=${config.queueMaxAgeMs + 1}"), logs)
    }

    @Test fun freshQueuedEventSpeaksAndLogsNothingMore() {
        val d = director()
        val id = idOf(d.say(EventType.HARD_ACCEL, 0))
        d.say(EventType.HARD_BRAKE, 100)
        logs.clear()
        val next = d.onSpeechEnded(id, 500)
        assertTrue(next.single() is Speak)
        assertEquals(emptyList<String>(), logs)
    }

    @Test fun nothingIsLoggedWhenTheEventSpeaksRightAway() {
        val d = director()
        d.say(EventType.HARD_ACCEL, 0)
        assertEquals(emptyList<String>(), logs)
    }

    @Test fun logsNeverContainSentenceText() {
        val d = director()
        val id = idOf(d.say(EventType.HARD_ACCEL, 0))
        d.say(EventType.HARD_BRAKE, 100)
        d.say(EventType.HARD_CORNER, 150)
        d.say(EventType.SEATBELT_OK, 160)
        d.say(EventType.COLLISION_RISK, 200)
        d.say(EventType.HARD_ACCEL, 300)
        d.onSpeechEnded(id, 5_000)
        val catalog = SpeechCatalog()
        for (type in EventType.entries) for (line in catalog.variants(type)) {
            for (log in logs) {
                assertFalse("로그에 문장이 있다: $log", log.contains(line.text) || log.contains(line.shortText))
            }
        }
        assertTrue(logs.isNotEmpty())
    }

    @Test fun directorWithoutALoggerBehavesTheSame() {
        val quiet = SpeechDirector(config)
        val logged = director()
        for (type in listOf(EventType.HARD_ACCEL, EventType.HARD_BRAKE, EventType.COLLISION_RISK, EventType.HARD_ACCEL)) {
            assertEquals(quiet.onEvent(ev(type, 0), MotionTier.FULL, 0), logged.onEvent(ev(type, 0), MotionTier.FULL, 0))
        }
    }
}
