package com.qmxz.pilotbot.config

import android.content.Context
import android.content.SharedPreferences
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.persona.Persona

/** Runtime-editable app config backed by SharedPreferences (no rebuild needed to change keys). */
class AppConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pilot_bot_config", Context.MODE_PRIVATE)

    var endpoint: LlmEndpoint
        get() = LlmEndpoint(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, "").orEmpty(),
        )
        set(value) = prefs.edit()
            .putString(KEY_BASE_URL, value.baseUrl)
            .putString(KEY_API_KEY, value.apiKey)
            .putString(KEY_MODEL, value.model)
            .apply()

    var persona: Persona
        get() = Persona(
            name = prefs.getString(KEY_NAME, DEFAULT_NAME).orEmpty(),
            tone = prefs.getString(KEY_TONE, DEFAULT_TONE).orEmpty(),
            catchphrase = prefs.getString(KEY_CATCHPHRASE, "").orEmpty(),
        )
        set(value) = prefs.edit()
            .putString(KEY_NAME, value.name)
            .putString(KEY_TONE, value.tone)
            .putString(KEY_CATCHPHRASE, value.catchphrase)
            .apply()

    private companion object {
        const val KEY_BASE_URL = "llm_base_url"
        const val KEY_API_KEY = "llm_api_key"
        const val KEY_MODEL = "llm_model"
        const val KEY_NAME = "persona_name"
        const val KEY_TONE = "persona_tone"
        const val KEY_CATCHPHRASE = "persona_catchphrase"
        const val DEFAULT_NAME = "小伴"
        const val DEFAULT_TONE = "轻松、活泼、像朋友"
    }
}
