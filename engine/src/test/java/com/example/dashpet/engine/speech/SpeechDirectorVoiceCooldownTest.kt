package com.example.dashpet.engine.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.speech.SpeechCommand.Speak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 심사 문서: "같은 종류의 펫 음성은 5분에 한 번까지만 내보냅니다." 말풍선은 짧은 간격만 둔다(specs/005). */
class SpeechDirectorVoiceCooldownTest {
    private val logs = mutableListOf<String>()
    private fun director() = SpeechDirector(SpeechConfig(), log = { logs += it })
    private fun ev(type: EventType, at: Long = 0) = DrivingEvent(type, at)
    private fun SpeechDirector.speak(type: EventType, tier: MotionTier, now: Long): Speak? =
        onEvent(ev(type, now), tier, now).filterIsInstance<Speak>().singleOrNull()?.also { onSpeechEnded(it.id, now + 1) }

    @Test fun defaultVoiceCooldownIsFiveMinutes() {
        assertEquals(5 * 60 * 1000L, SpeechConfig().voiceCooldownMs)
    }

    @Test fun firstEventSpeaksWithVoice() {
        val s = director().speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)!!
        assertTrue(s.voice); assertTrue(s.showBubble)
    }

    @Test fun sameTypeWithinFiveMinutesKeepsBubbleButNoVoice() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        val again = d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 20_000)!!
        assertFalse(again.voice)
        assertTrue(again.showBubble)
    }

    @Test fun voiceReturnsExactlyAtFiveMinutes() {
        val before = director()
        before.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        assertFalse(before.speak(EventType.HARD_ACCEL, MotionTier.FULL, 299_999)!!.voice)
        val atLimit = director()
        atLimit.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        assertTrue(atLimit.speak(EventType.HARD_ACCEL, MotionTier.FULL, 300_000)!!.voice)
    }

    @Test fun staticTierKeepsBubbleSilentlyWhenVoiceIsCoolingDown() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.STATIC, 0)
        val again = d.speak(EventType.HARD_ACCEL, MotionTier.STATIC, 20_000)!!
        assertFalse(again.voice)
        assertTrue(again.showBubble) // 제한 중에도 말풍선은 보인다
    }

    @Test fun otherTypesAreIndependent() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        assertTrue(d.speak(EventType.HARD_BRAKE, MotionTier.FULL, 1_000)!!.voice)
    }

    @Test fun shortBubbleGapStillAppliesBeforeVoiceWindow() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        assertEquals(null, d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 1_000)) // 우선순위별 간격(8초) 안
    }

    @Test fun resetClearsVoiceCooldown() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        d.reset()
        assertTrue(d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 20_000)!!.voice)
    }

    @Test fun clearCooldownLetsDebugSpeakAgain() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.STATIC, 0)
        d.clearCooldown(EventType.HARD_ACCEL)
        assertTrue(d.speak(EventType.HARD_ACCEL, MotionTier.STATIC, 1_000)!!.voice)
    }

    @Test fun bubbleOnlySpeakDoesNotBlockQueue() {
        val d = director()
        d.speak(EventType.HARD_ACCEL, MotionTier.FULL, 0)
        val bubbleOnly = d.onEvent(ev(EventType.HARD_ACCEL, 20_000), MotionTier.FULL, 20_000).filterIsInstance<Speak>().single()
        assertFalse(bubbleOnly.voice)
        // 소리가 없으므로 호출자가 바로 끝났다고 알리면 다음 말이 이어진다.
        d.onSpeechEnded(bubbleOnly.id, 20_001)
        assertTrue(d.speak(EventType.HARD_BRAKE, MotionTier.FULL, 20_100)!!.voice)
    }

    @Test fun toneCatalogIsUsed() {
        val lively = SpeechDirector(SpeechConfig(), SpeechCatalog.forTone(SpeechTone.LIVELY))
        val s = lively.speak(EventType.HARD_CORNER, MotionTier.FULL, 0)!!
        assertEquals("으앗! 커브가 너무 급했어! 속도를 줄이자!", s.utterance.text)
    }
}
