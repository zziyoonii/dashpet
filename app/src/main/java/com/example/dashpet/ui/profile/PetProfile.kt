package com.example.dashpet.ui.profile

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.speech.SpeechTone
import com.example.dashpet.pet.Personality
import com.example.dashpet.pet.PetAppearance

/**
 * 사용자가 온보딩에서 정한 펫 정보. 이 기기에만 저장하고 서버로 보내지 않는다(specs/005).
 * [consented] 는 첫 실행의 동의 화면에서 "동의하고 시작"을 눌렀다는 뜻이다(다른 동작은 동의가 아니다, 헌장 V).
 */
data class PetProfile(
    val name: String,
    val personality: Personality,
    val colorArgb: Long,
    val consented: Boolean,
)

fun PetProfile.toAppearance(): PetAppearance =
    PetAppearance(name = name, bodyColor = Color(colorArgb.toInt()), personality = personality)

/** `:pet` 의 성격과 `:engine` 의 말투는 같은 세 값이다. */
fun Personality.toTone(): SpeechTone = when (this) {
    Personality.STURDY -> SpeechTone.STURDY
    Personality.LIVELY -> SpeechTone.LIVELY
    Personality.CUTE -> SpeechTone.CUTE
}

/** 이름과 뜻 입력 규칙. 어떤 입력도 예외를 던지지 않는다. */
object ProfileRules {
    const val NAME_MAX = 10
    const val MEANING_MAX = 30

    /** 입력 중에 쓰는 정리: 제어 문자를 지우고 글자 수만 제한한다(끝의 공백은 남긴다). */
    fun limitName(raw: String): String = limit(raw.filterNot { it.isISOControl() }, NAME_MAX)

    /** 저장할 이름: 제어 문자 제거, 연속 공백을 하나로, 앞뒤 공백 제거, 최대 [NAME_MAX] 글자. */
    fun cleanName(raw: String): String = limit(collapse(raw), NAME_MAX).trim()

    fun cleanMeaning(raw: String): String = limit(collapse(raw), MEANING_MAX).trim()

    fun limitMeaning(raw: String): String = limit(raw.filterNot { it.isISOControl() }, MEANING_MAX)

    fun isValidName(clean: String): Boolean = clean.isNotBlank()

    private fun collapse(raw: String): String =
        raw.filterNot { it.isISOControl() }.trim().replace(Regex("\\s+"), " ")

    /** 코드 포인트 기준으로 자른다(이모지 같은 보충 문자를 반으로 가르지 않는다). */
    private fun limit(s: String, max: Int): String {
        if (s.codePointCount(0, s.length) <= max) return s
        return s.substring(0, s.offsetByCodePoints(0, max))
    }
}

data class NamedColor(val name: String, val argb: Long)

/** 온보딩에서 고르는 몸 색. 외형은 색만 고른다(차종 실루엣은 다음 단계). */
object OnboardingPalette {
    /** 시안 기본색 #4F7CFF. */
    const val DEFAULT_ARGB = 0xFF4F7CFFL

    val colors: List<NamedColor> = listOf(
        NamedColor("파랑", DEFAULT_ARGB),
        NamedColor("하늘", 0xFF4CC9F0L),
        NamedColor("초록", 0xFF3DBE7AL),
        NamedColor("주황", 0xFFFF9F43L),
        NamedColor("분홍", 0xFFFF7EB6L),
        NamedColor("보라", 0xFF8B6CFFL),
        NamedColor("빨강", 0xFFF25C54L),
        NamedColor("은색", 0xFF9AA5B1L),
    )
}

/** 프로필 저장소. 점수 기록은 이름을 다시 지어도 남긴다. */
interface ProfileStore {
    fun load(): PetProfile?
    fun save(profile: PetProfile)
    fun clear()

    /** 지난 주행 점수(도착 리포트에서 비교). 점수 하나만 저장한다. */
    var lastScore: Int?
}

class MemoryProfileStore : ProfileStore {
    private var profile: PetProfile? = null
    override var lastScore: Int? = null
    override fun load() = profile
    override fun save(profile: PetProfile) { this.profile = profile }
    override fun clear() { profile = null }
}

class SharedPrefsProfileStore(context: Context) : ProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("dashpet_profile", Context.MODE_PRIVATE)

    override fun load(): PetProfile? {
        if (!prefs.getBoolean(K_DONE, false)) return null
        val name = prefs.getString(K_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val personality = runCatching { Personality.valueOf(prefs.getString(K_PERSONALITY, "") ?: "") }.getOrNull() ?: return null
        return PetProfile(name, personality, prefs.getLong(K_COLOR, OnboardingPalette.DEFAULT_ARGB), prefs.getBoolean(K_CONSENT, false))
    }

    override fun save(profile: PetProfile) {
        prefs.edit()
            .putBoolean(K_DONE, true)
            .putString(K_NAME, profile.name)
            .putString(K_PERSONALITY, profile.personality.name)
            .putLong(K_COLOR, profile.colorArgb)
            .putBoolean(K_CONSENT, profile.consented)
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(K_DONE).remove(K_NAME).remove(K_PERSONALITY).remove(K_COLOR).remove(K_CONSENT).apply()
    }

    override var lastScore: Int?
        get() = if (prefs.contains(K_LAST_SCORE)) prefs.getInt(K_LAST_SCORE, 0) else null
        set(value) {
            prefs.edit().apply { if (value == null) remove(K_LAST_SCORE) else putInt(K_LAST_SCORE, value) }.apply()
        }

    private companion object {
        const val K_DONE = "done"
        const val K_NAME = "name"
        const val K_PERSONALITY = "personality"
        const val K_COLOR = "color"
        const val K_CONSENT = "consent"
        const val K_LAST_SCORE = "last_score"
    }
}
