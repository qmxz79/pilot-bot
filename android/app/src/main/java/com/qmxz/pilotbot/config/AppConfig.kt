package com.qmxz.pilotbot.config

import android.content.Context
import android.content.SharedPreferences
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.persona.Persona
import com.qmxz.pilotbot.persona.PersonaStore
import com.qmxz.pilotbot.voice.ConversationMode

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

    /** The custom persona's own fields (used only when [personaId] is the custom slot). */
    var persona: Persona
        get() = Persona(
            id = PersonaStore.CUSTOM_ID,
            name = prefs.getString(KEY_NAME, DEFAULT_NAME).orEmpty(),
            tone = prefs.getString(KEY_TONE, DEFAULT_TONE).orEmpty(),
            catchphrase = prefs.getString(KEY_CATCHPHRASE, "").orEmpty(),
        )
        set(value) = prefs.edit()
            .putString(KEY_NAME, value.name)
            .putString(KEY_TONE, value.tone)
            .putString(KEY_CATCHPHRASE, value.catchphrase)
            .apply()

    var personaId: String
        get() = prefs.getString(KEY_PERSONA_ID, PersonaStore.CUSTOM_ID).orEmpty()
        set(value) = prefs.edit().putString(KEY_PERSONA_ID, value).apply()

    /** Resolves the active persona: a built-in preset by id, or the custom slot. */
    fun currentPersona(): Persona = PersonaStore.find(personaId) ?: persona

    var conversationMode: ConversationMode
        get() = runCatching {
            ConversationMode.valueOf(prefs.getString(KEY_MODE, ConversationMode.PUSH_TO_TALK.name).orEmpty())
        }.getOrDefault(ConversationMode.PUSH_TO_TALK)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var wakeWord: String
        get() = prefs.getString(KEY_WAKE_WORD, DEFAULT_WAKE_WORD).orEmpty()
        set(value) = prefs.edit().putString(KEY_WAKE_WORD, value).apply()

    private companion object {
        const val KEY_BASE_URL = "llm_base_url"
        const val KEY_API_KEY = "llm_api_key"
        const val KEY_MODEL = "llm_model"
        const val KEY_NAME = "persona_name"
        const val KEY_TONE = "persona_tone"
        const val KEY_CATCHPHRASE = "persona_catchphrase"
        const val KEY_PERSONA_ID = "persona_id"
        const val KEY_MODE = "conversation_mode"
        const val KEY_WAKE_WORD = "wake_word"
        const val DEFAULT_NAME = "小伴"
        const val DEFAULT_TONE = "轻松、活泼、像朋友"
        const val DEFAULT_WAKE_WORD = "小伴"
    }
}
