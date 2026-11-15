package com.example.dashpet.engine.report

import com.example.dashpet.engine.speech.SpeechLimits
import com.example.dashpet.engine.speech.SpeechTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {
    @Test fun batchimDecidesParticle() {
        assertEquals("장수야", Greetings.withYa("장수"))
        assertEquals("콩이야", Greetings.withYa("콩"))
        assertEquals("장수예요", Greetings.withYeyo("장수"))
        assertEquals("콩이에요", Greetings.withYeyo("콩"))
    }

    @Test fun nonHangulEndingUsesNoBatchim() {
        assertEquals("Max야", Greetings.withYa("Max"))
        assertEquals("Max예요", Greetings.withYeyo("Max"))
    }

    @Test fun firstGreetingNamesThePetInEveryTone() {
        val lines = SpeechTone.entries.map { Greetings.first("장수", it) }
        assertEquals(3, lines.toSet().size)
        lines.forEach { assertTrue(it, it.contains("장수") && it.length <= SpeechLimits.MAX_CHARS) }
        assertEquals("나 이제 장수야! 오래오래 같이 신나게 달리자!", Greetings.first("장수", SpeechTone.LIVELY))
    }

    @Test fun blankNameFallsBackToDefault() {
        assertTrue(Greetings.first("   ", SpeechTone.STURDY).contains("내 차"))
    }
}
