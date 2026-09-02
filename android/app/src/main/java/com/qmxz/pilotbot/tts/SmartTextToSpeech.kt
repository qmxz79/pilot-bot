package com.qmxz.pilotbot.tts

import android.content.Context
import com.qmxz.pilotbot.config.AppConfig

/**
 * Universal Text-To-Speech engine.
 * Prioritizes ultra-realistic Cloud TTS (CosyVoice2 / OpenAI TTS) with natural human timbre and breath,
 * and seamlessly falls back to local Android TTS when offline.
 */
class SmartTextToSpeech(
    context: Context,
    private val config: AppConfig,
    private val systemTts: AndroidTextToSpeech = AndroidTextToSpeech(context),
    private val cloudTts: CloudTextToSpeech = CloudTextToSpeech(context, config),
) : TextToSpeech {

    var onStatusChanged: ((String) -> Unit)? = null
    /** Called when cloud speech failed and the engine falls back to device TTS. */
    var onCloudFallback: ((Throwable) -> Unit)? = null

    init {
        systemTts.onStatusChanged = {
            onStatusChanged?.invoke(status())
        }
    }

    override val isAvailable: Boolean
        get() = cloudTts.isAvailable || systemTts.isAvailable

    fun status(): String {
        return when {
            cloudTts.isAvailable -> "语音:真人拟人音(CosyVoice)"
            systemTts.isAvailable -> "语音:系统就绪"
            else -> systemTts.status()
        }
    }

    override suspend fun speak(text: String) {
        val clean = TextSanitizer.sanitizeForSpeech(text)
        if (clean.isBlank()) return

        if (cloudTts.isAvailable) {
            try {
                cloudTts.speak(clean)
                return
            } catch (error: Exception) {
                // If cloud TTS network fails, gracefully fallback to local system TTS
                onCloudFallback?.invoke(error)
            }
        }

        if (systemTts.isAvailable) {
            systemTts.speak(clean)
        }
    }

    override fun interrupt() {
        cloudTts.interrupt()
        systemTts.interrupt()
    }

    override fun shutdown() {
        cloudTts.shutdown()
        systemTts.shutdown()
    }

    override fun setOnIdle(callback: () -> Unit) {
        cloudTts.setOnIdle(callback)
        systemTts.setOnIdle(callback)
    }
}
