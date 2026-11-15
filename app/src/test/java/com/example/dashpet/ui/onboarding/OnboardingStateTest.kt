package com.example.dashpet.ui.onboarding

import com.example.dashpet.pet.Personality
import com.example.dashpet.ui.profile.OnboardingPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    private fun named(name: String = "장수", meaning: String = "") =
        OnboardingState().agree().withName(name).next().withMeaning(meaning)

    @Test fun startsAtConsentWithoutConsent() {
        val s = OnboardingState()
        assertEquals(OnboardingStep.CONSENT, s.step)
        assertFalse(s.consented)
        assertNull(s.toProfile())
    }

    @Test fun nextOnConsentDoesNothingUntilAgreed() {
        assertEquals(OnboardingStep.CONSENT, OnboardingState().next().step)
        assertEquals(OnboardingStep.NAME, OnboardingState().agree().step)
    }

    @Test fun blankNameBlocksNext() {
        val s = OnboardingState().agree().withName("   ")
        assertFalse(s.canGoNext)
        assertEquals(OnboardingStep.NAME, s.next().step)
        assertTrue(s.withName("장수").canGoNext)
    }

    @Test fun nameIsLimitedWhileTyping() {
        assertEquals(10, OnboardingState().agree().withName("가".repeat(40)).name.length)
    }

    @Test fun meaningCanBeSkipped() {
        val s = OnboardingState().agree().withName("장수").next().skipMeaning()
        assertEquals(OnboardingStep.PERSONALITY, s.step)
        assertEquals("", s.meaning)
    }

    @Test fun enteringPersonalitySuggestsFromNameAndMeaning() {
        val s = named("장수", "튼튼하고 오래가라는 뜻").next()
        assertEquals(OnboardingStep.PERSONALITY, s.step)
        assertEquals(Personality.STURDY, s.personality)
        assertTrue(s.suggestion.reason != null)
        assertEquals(Personality.LIVELY, named("번개").next().personality)
        assertEquals(Personality.CUTE, named("콩이").next().personality)
    }

    @Test fun userChoiceIsNotOverwrittenWhenGoingBackAndForth() {
        var s = named("번개").next()
        s = s.choosePersonality(Personality.CUTE)
        s = s.back().next()
        assertEquals(Personality.CUTE, s.personality)
    }

    @Test fun untouchedChoiceFollowsNewSuggestion() {
        var s = named("번개").next()
        assertEquals(Personality.LIVELY, s.personality)
        s = s.back().back().withName("콩이").next().next()
        assertEquals(Personality.CUTE, s.personality)
    }

    @Test fun backStopsAtName() {
        val s = OnboardingState().agree()
        assertEquals(OnboardingStep.NAME, s.back().step)
    }

    @Test fun fullFlowProducesProfile() {
        val s = named("장수", "튼튼").next().choosePersonality(Personality.STURDY).next()
        assertEquals(OnboardingStep.COLOR, s.step)
        val done = s.chooseColor(OnboardingPalette.colors[2].argb)
        val p = done.toProfile()!!
        assertEquals("장수", p.name)
        assertEquals(Personality.STURDY, p.personality)
        assertEquals(OnboardingPalette.colors[2].argb, p.colorArgb)
        assertTrue(p.consented)
    }

    @Test fun profileNeedsEveryRequiredPiece() {
        assertNull(OnboardingState().agree().toProfile()) // 이름과 성격 없음
        assertNull(named("   ").toProfile())
    }

    @Test fun profileNameIsCleaned() {
        val p = named("  우리   차  ").next().next().toProfile()!!
        assertEquals("우리 차", p.name)
    }

    @Test fun lastStepNextStaysAndIsComplete() {
        val s = named().next().next()
        assertEquals(OnboardingStep.COLOR, s.step)
        assertEquals(OnboardingStep.COLOR, s.next().step)
        assertTrue(s.isLastStep)
    }
}
