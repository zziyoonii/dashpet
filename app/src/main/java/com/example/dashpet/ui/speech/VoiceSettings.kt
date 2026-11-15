package com.example.dashpet.ui.speech

import android.content.Context

/** 음성 켜기/끄기 설정. 저장하는 것은 이 불리언 하나뿐이다. */
interface VoiceSettings {
    var voiceEnabled: Boolean
}

class MemoryVoiceSettings(override var voiceEnabled: Boolean = true) : VoiceSettings

class SharedPrefsVoiceSettings(context: Context) : VoiceSettings {
    private val prefs = context.applicationContext.getSharedPreferences("dashpet_voice", Context.MODE_PRIVATE)

    override var voiceEnabled: Boolean
        get() = prefs.getBoolean(KEY, true)
        set(value) { prefs.edit().putBoolean(KEY, value).apply() }

    private companion object {
        const val KEY = "voice_enabled"
    }
}
