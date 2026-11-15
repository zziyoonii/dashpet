package com.example.dashpet.ui.profile

import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.speech.SpeechTone
import com.example.dashpet.pet.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetProfileTest {
    @Test fun cleanNameTrimsCollapsesAndLimits() {
        assertEquals("장수", ProfileRules.cleanName("  장수  "))
        assertEquals("우리 차", ProfileRules.cleanName("우리    차"))
        assertEquals(10, ProfileRules.cleanName("가".repeat(30)).length)
        assertEquals("", ProfileRules.cleanName("   "))
    }

    @Test fun controlCharactersAreRemoved() {
        assertEquals("장수", ProfileRules.cleanName("장\n수\u0007"))
    }

    @Test fun limitNameKeepsTrailingSpaceWhileTyping() {
        assertEquals("우리 ", ProfileRules.limitName("우리 "))
        assertEquals(10, ProfileRules.limitName("a".repeat(40)).length)
    }

    @Test fun emojiCountsAsOneCharacterAndNeverSplits() {
        val s = ProfileRules.cleanName("😀".repeat(12))
        assertEquals(10, s.codePointCount(0, s.length))
        assertTrue(s.all { true }) // 깨진 서로게이트가 없어야 한다
        assertEquals(s, String(s.toCharArray()))
    }

    @Test fun cleanMeaningLimitsTo30() {
        assertEquals(30, ProfileRules.cleanMeaning("가".repeat(100)).length)
        assertEquals("", ProfileRules.cleanMeaning("  "))
    }

    @Test fun validName() {
        assertTrue(ProfileRules.isValidName("장수"))
        assertFalse(ProfileRules.isValidName("   "))
        assertFalse(ProfileRules.isValidName(""))
    }

    @Test fun personalityMapsToToneOneToOne() {
        assertEquals(SpeechTone.STURDY, Personality.STURDY.toTone())
        assertEquals(SpeechTone.LIVELY, Personality.LIVELY.toTone())
        assertEquals(SpeechTone.CUTE, Personality.CUTE.toTone())
    }

    @Test fun profileBecomesAppearance() {
        val a = PetProfile("장수", Personality.CUTE, 0xFF3DBE7AL, consented = true).toAppearance()
        assertEquals("장수", a.name)
        assertEquals(Personality.CUTE, a.personality)
        assertEquals(Color(0xFF3DBE7A), a.bodyColor)
    }

    @Test fun memoryStoreRoundTripsAndClears() {
        val store = MemoryProfileStore()
        assertNull(store.load())
        val p = PetProfile("콩이", Personality.LIVELY, 0xFFFF9F43L, consented = true)
        store.save(p)
        assertEquals(p, store.load())
        store.lastScore = 88
        assertEquals(88, store.lastScore)
        store.clear()
        assertNull(store.load())
        assertEquals(88, store.lastScore) // 이름을 다시 지어도 점수 기록은 남긴다
    }

    @Test fun paletteHasDistinctOpaqueColorsWithNames() {
        val colors = OnboardingPalette.colors
        assertEquals(8, colors.size)
        assertEquals(colors.size, colors.map { it.argb }.toSet().size)
        assertEquals(colors.size, colors.map { it.name }.toSet().size)
        colors.forEach { assertEquals("불투명", 0xFFL, it.argb ushr 24) }
        assertTrue(colors.any { it.argb == OnboardingPalette.DEFAULT_ARGB })
    }
}
