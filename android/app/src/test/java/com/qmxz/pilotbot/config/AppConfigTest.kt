package com.qmxz.pilotbot.config

import android.content.SharedPreferences
import com.qmxz.pilotbot.llm.LlmEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppConfigTest {
    @Test fun migratesLegacyLlmKeyIntoSecretStore() {
        val prefs = TestPrefs().apply { edit().putString("llm_api_key", "legacy-key").apply() }
        val secrets = TestSecrets()
        val config = AppConfig(prefs, secrets)

        assertEquals("legacy-key", config.endpoint.apiKey)
        assertEquals("legacy-key", secrets.get("llm_api_key"))
        assertFalse(prefs.contains("llm_api_key"))
    }

    @Test fun keepsLlmAsrAndTtsCredentialsIndependent() {
        val config = AppConfig(TestPrefs(), TestSecrets())
        config.endpoint = LlmEndpoint("https://llm.example/v1", "llm-key", "chat")
        config.asrEndpoint = ServiceEndpoint("https://asr.example/v1", "asr-key", "whisper")
        config.ttsEndpoint = ServiceEndpoint("https://tts.example/v1", "tts-key", "voice")

        assertEquals("llm-key", config.endpoint.apiKey)
        assertEquals("asr-key", config.asrEndpoint.apiKey)
        assertEquals("tts-key", config.ttsEndpoint.apiKey)
        assertEquals("whisper", config.asrEndpoint.model)
        assertEquals("voice", config.ttsEndpoint.model)
    }

    @Test fun migratesGoogleAndAsrKeysWithoutLeavingPlaintext() {
        val prefs = TestPrefs().apply {
            edit().putString("google_maps_api_key", "maps-legacy").putString("asr_api_key", "asr-legacy").apply()
        }
        val secrets = TestSecrets()
        val config = AppConfig(prefs, secrets)

        assertEquals("maps-legacy", config.googleMapsApiKey)
        assertEquals("asr-legacy", config.asrApiKey)
        assertEquals("maps-legacy", secrets.get("google_maps_api_key"))
        assertEquals("asr-legacy", secrets.get("asr_api_key"))
        assertFalse(prefs.contains("google_maps_api_key"))
        assertFalse(prefs.contains("asr_api_key"))
    }

    @Test fun legacyLlmEndpointRemainsAsrAndTtsFallbackUntilConfigured() {
        val config = AppConfig(TestPrefs(), TestSecrets())
        config.endpoint = LlmEndpoint("https://legacy.example/v1", "legacy-key", "chat")

        assertEquals("legacy-key", config.asrEndpoint.apiKey)
        assertEquals("legacy-key", config.ttsEndpoint.apiKey)
    }

    private class TestSecrets : SecretStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private class TestPrefs : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, def: String?) = values[key] as? String ?: def
        override fun getStringSet(key: String?, def: MutableSet<String>?) = values[key] as? MutableSet<String> ?: def
        override fun getInt(key: String?, def: Int) = values[key] as? Int ?: def
        override fun getLong(key: String?, def: Long) = values[key] as? Long ?: def
        override fun getFloat(key: String?, def: Float) = values[key] as? Float ?: def
        override fun getBoolean(key: String?, def: Boolean) = values[key] as? Boolean ?: def
        override fun contains(key: String?) = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>(); private val removals = mutableSetOf<String>()
            override fun putString(k: String?, v: String?) = apply { k?.let { pending[it] = v } }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { k?.let { pending[it] = v } }
            override fun putInt(k: String?, v: Int) = apply { k?.let { pending[it] = v } }
            override fun putLong(k: String?, v: Long) = apply { k?.let { pending[it] = v } }
            override fun putFloat(k: String?, v: Float) = apply { k?.let { pending[it] = v } }
            override fun putBoolean(k: String?, v: Boolean) = apply { k?.let { pending[it] = v } }
            override fun remove(k: String?) = apply { k?.let(removals::add) }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() { removals.forEach(values::remove); pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v } }
        }
    }
}
