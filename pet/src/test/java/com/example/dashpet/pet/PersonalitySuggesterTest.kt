package com.example.dashpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 이름과 뜻을 읽어 성격을 추천하는 규칙 기반 사전(LLM 없음). 사용자가 언제든 바꿀 수 있다(specs/005). */
class PersonalitySuggesterTest {
    private fun suggest(name: String, meaning: String = "") = PersonalitySuggester.suggest(name, meaning)

    @Test fun sturdyKeywordsInMeaning() {
        val s = suggest("장수", "튼튼하고 오래가라는 뜻")
        assertEquals(Personality.STURDY, s.personality)
        assertTrue(s.matched.containsAll(listOf("튼튼", "오래")))
        assertNotNull(s.reason)
    }

    @Test fun livelyKeywordsInName() {
        assertEquals(Personality.LIVELY, suggest("번개").personality)
        assertEquals(Personality.LIVELY, suggest("로켓", "쌩쌩 달려").personality)
    }

    @Test fun cuteKeywordsInName() {
        assertEquals(Personality.CUTE, suggest("콩이").personality)
        assertEquals(Personality.CUTE, suggest("몽실이", "귀엽고 사랑스러워서").personality)
    }

    @Test fun noKeywordDefaultsToSturdyWithoutReason() {
        val s = suggest("차돌", "")
        assertEquals(Personality.STURDY, s.personality)
        assertNull(s.reason)
        assertTrue(s.matched.isEmpty())
    }

    @Test fun mostMatchesWins() {
        assertEquals(Personality.LIVELY, suggest("번개", "신나게 질주하고 가끔 든든하게").personality)
    }

    @Test fun tieGoesToSturdy() {
        assertEquals(Personality.STURDY, suggest("든든이", "귀여운").personality)
    }

    @Test fun blankAndWeirdInputDoNotThrow() {
        assertEquals(Personality.STURDY, suggest("", "").personality)
        assertEquals(Personality.STURDY, suggest("   ", "   ").personality)
        suggest("😀".repeat(50), "\n\t")
    }

    @Test fun labelsAreKorean() {
        assertEquals("듬직", Personality.STURDY.label)
        assertEquals("활발", Personality.LIVELY.label)
        assertEquals("애교", Personality.CUTE.label)
        Personality.entries.forEach { assertTrue(it.description.isNotBlank()) }
    }
}
