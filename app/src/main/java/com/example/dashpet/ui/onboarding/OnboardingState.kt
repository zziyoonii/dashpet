package com.example.dashpet.ui.onboarding

import com.example.dashpet.pet.Personality
import com.example.dashpet.pet.PersonalitySuggester
import com.example.dashpet.pet.PersonalitySuggestion
import com.example.dashpet.ui.profile.OnboardingPalette
import com.example.dashpet.ui.profile.PetProfile
import com.example.dashpet.ui.profile.ProfileRules

enum class OnboardingStep { CONSENT, NAME, MEANING, PERSONALITY, COLOR }

/**
 * 이름 짓기 온보딩의 상태(순수 값, Android 를 모른다). 화면은 이 값을 그리고 버튼으로 새 값을 만든다.
 * 동의 -> 이름 -> 뜻(건너뛸 수 있음) -> 성격(규칙 기반 추천, 직접 선택) -> 색. STT 입력은 공개 뒤에 추가한다.
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.CONSENT,
    val consented: Boolean = false,
    val name: String = "",
    val meaning: String = "",
    val personality: Personality? = null,
    /** 사용자가 성격을 직접 골랐는가. 고르지 않았으면 추천을 따라간다. */
    val personalityTouched: Boolean = false,
    val colorArgb: Long = OnboardingPalette.DEFAULT_ARGB,
) {
    val suggestion: PersonalitySuggestion
        get() = PersonalitySuggester.suggest(ProfileRules.cleanName(name), ProfileRules.cleanMeaning(meaning))

    val isLastStep: Boolean get() = step == OnboardingStep.COLOR

    /** 지금 단계에서 "다음"을 누를 수 있는가. 동의 단계는 [agree] 로만 넘어간다. */
    val canGoNext: Boolean
        get() = when (step) {
            OnboardingStep.CONSENT -> consented
            OnboardingStep.NAME -> ProfileRules.isValidName(ProfileRules.cleanName(name))
            else -> true
        }

    /** "동의하고 시작"을 눌렀다. 이 동작만 동의로 취급한다. */
    fun agree(): OnboardingState = copy(consented = true, step = OnboardingStep.NAME)

    fun withName(raw: String): OnboardingState = copy(name = ProfileRules.limitName(raw))

    fun withMeaning(raw: String): OnboardingState = copy(meaning = ProfileRules.limitMeaning(raw))

    fun skipMeaning(): OnboardingState = copy(meaning = "").next()

    fun choosePersonality(p: Personality): OnboardingState = copy(personality = p, personalityTouched = true)

    fun chooseColor(argb: Long): OnboardingState = copy(colorArgb = argb)

    fun next(): OnboardingState {
        if (!canGoNext) return this
        return when (step) {
            OnboardingStep.CONSENT -> this
            OnboardingStep.NAME -> copy(step = OnboardingStep.MEANING)
            OnboardingStep.MEANING -> enterPersonality()
            OnboardingStep.PERSONALITY -> copy(step = OnboardingStep.COLOR)
            OnboardingStep.COLOR -> this
        }
    }

    fun back(): OnboardingState = when (step) {
        OnboardingStep.CONSENT, OnboardingStep.NAME -> this // 동의 화면으로는 돌아가지 않는다
        OnboardingStep.MEANING -> copy(step = OnboardingStep.NAME)
        OnboardingStep.PERSONALITY -> copy(step = OnboardingStep.MEANING)
        OnboardingStep.COLOR -> copy(step = OnboardingStep.PERSONALITY)
    }

    /** 이름과 뜻이 모두 정해졌으니 추천을 계산한다. 사용자가 이미 골랐으면 그대로 둔다. */
    private fun enterPersonality(): OnboardingState = copy(
        step = OnboardingStep.PERSONALITY,
        personality = if (personalityTouched) personality else suggestion.personality,
    )

    /** 필수 값이 모두 있으면 저장할 프로필. 하나라도 없으면 null. */
    fun toProfile(): PetProfile? {
        val clean = ProfileRules.cleanName(name)
        val p = personality ?: return null
        if (!consented || !ProfileRules.isValidName(clean)) return null
        return PetProfile(clean, p, colorArgb, consented = true)
    }
}
