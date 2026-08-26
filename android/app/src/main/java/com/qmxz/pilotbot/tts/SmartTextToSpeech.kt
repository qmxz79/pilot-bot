package com.qmxz.pilotbot.tts

import android.content.Context
import com.qmxz.pilotbot.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Text-To-Speech engine.
 * Transparently falls back to Cloud TTS (CosyVoice / OpenAI TTS) if device System TTS is unavailable.
 */
class SmartTextToSpeech(
    context: Context,
    private val config: AppConfig,
    private val systemTts: AndroidTextToSpeech = AndroidTextToSpeech(context),
    private val cloudTts: CloudTextToSpeech = CloudTextToSpeech(context, config),
) : TextToSpeech {

    var onStatusChanged: ((String) -> Unit)? = null

    init {
        systemTts.onStatusChanged = {
            onStatusChanged?.invoke(status())
        }
    }

    override val isAvailable: Boolean
        get() = systemTts.isAvailable || cloudTts.isAvailable

    fun status(): String {
        return when {
            systemTts.isAvailable -> "语音:就绪"
            cloudTts.isAvailable -> "语音:云端就绪"
            else -> systemTts.status()
        }
    }

    override suspend fun speak(text: String) {
        if (systemTts.isAvailable) {
            systemTts.speak(text)
        } else if (cloudTts.isAvailable) {
            cloudTts.speak(text)
        }
    }

    override fun interrupt() {
        systemTts.interrupt()
        cloudTts.interrupt()
    }

    override fun shutdown() {
        systemTts.shutdown()
        cloudTts.shutdown()
    }

    override fun setOnIdle(callback: () -> Unit) {
        systemTts.setOnIdle(callback)
        cloudTts.setOnIdle(callback)
    }
}
