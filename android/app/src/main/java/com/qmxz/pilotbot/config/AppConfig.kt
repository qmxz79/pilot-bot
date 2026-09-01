package com.qmxz.pilotbot.config

import android.content.Context
import android.content.SharedPreferences
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.persona.Persona
import com.qmxz.pilotbot.persona.PersonaStore
import com.qmxz.pilotbot.voice.ConversationMode

enum class MapProvider {
    AMAP,
    GOOGLE,
    GOOGLE_MAPS
}

/** Runtime-editable app config backed by SharedPreferences (no rebuild needed to change keys). */
class AppConfig(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("pilot_bot_config", Context.MODE_PRIVATE)
    )

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

    var asrBaseUrl: String
        get() = prefs.getString(KEY_ASR_BASE_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ASR_BASE_URL, value).apply()

    var asrApiKey: String
        get() = prefs.getString(KEY_ASR_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ASR_API_KEY, value).apply()

    var asrModel: String
        get() = prefs.getString(KEY_ASR_MODEL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ASR_MODEL, value).apply()

    var mapProvider: MapProvider
        get() = runCatching {
            MapProvider.valueOf(prefs.getString(KEY_MAP_PROVIDER, MapProvider.AMAP.name).orEmpty())
        }.getOrDefault(MapProvider.AMAP)
        set(value) = prefs.edit().putString(KEY_MAP_PROVIDER, value.name).apply()

    var googleMapsApiKey: String
        get() = prefs.getString(KEY_GOOGLE_MAPS_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_GOOGLE_MAPS_API_KEY, value).apply()

    var avatarGender: com.qmxz.pilotbot.avatar.state.AvatarGender
        get() = runCatching {
            com.qmxz.pilotbot.avatar.state.AvatarGender.valueOf(
                prefs.getString(KEY_AVATAR_GENDER, com.qmxz.pilotbot.avatar.state.AvatarGender.FEMALE.name).orEmpty()
            )
        }.getOrDefault(com.qmxz.pilotbot.avatar.state.AvatarGender.FEMALE)
        set(value) = prefs.edit().putString(KEY_AVATAR_GENDER, value.name).apply()

    var ttsVoice: String
        get() = prefs.getString(KEY_TTS_VOICE, "auto").orEmpty()
        set(value) = prefs.edit().putString(KEY_TTS_VOICE, value).apply()

    /** True only on the very first launch; flips the flag so it cannot fire twice. */
    fun consumeFirstLaunch(): Boolean {
        val first = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        if (first) prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        return first
    }

    private companion object {
        const val KEY_BASE_URL = "llm_base_url"
        const val KEY_API_KEY = "llm_api_key"
        const val KEY_MODEL = "llm_model"
        const val KEY_ASR_BASE_URL = "asr_base_url"
        const val KEY_ASR_API_KEY = "asr_api_key"
        const val KEY_ASR_MODEL = "asr_model"
        const val KEY_NAME = "persona_name"
        const val KEY_TONE = "persona_tone"
        const val KEY_CATCHPHRASE = "persona_catchphrase"
        const val KEY_PERSONA_ID = "persona_id"
        const val KEY_MODE = "conversation_mode"
        const val KEY_WAKE_WORD = "wake_word"
        const val KEY_FIRST_LAUNCH = "first_launch"
        const val KEY_MAP_PROVIDER = "map_provider"
        const val KEY_GOOGLE_MAPS_API_KEY = "google_maps_api_key"
        const val KEY_AVATAR_GENDER = "avatar_gender"
        const val KEY_TTS_VOICE = "tts_voice"
        const val DEFAULT_NAME = "小伴"
        const val DEFAULT_TONE = "轻松、活泼、像朋友"
        const val DEFAULT_WAKE_WORD = "小伴"
    }
}
