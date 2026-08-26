package com.qmxz.pilotbot.tts

import android.content.Context
import android.media.MediaPlayer
import com.qmxz.pilotbot.audio.AudioFocusManager
import com.qmxz.pilotbot.config.AppConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Cloud Text-to-Speech client that synthesizes speech via OpenAI / SiliconFlow `/v1/audio/speech` endpoint.
 * Plays high quality human voice through Android [MediaPlayer] with [AudioFocusManager] ducking.
 */
class CloudTextToSpeech(
    context: Context,
    private val config: AppConfig,
    private val audioFocusManager: AudioFocusManager = AudioFocusManager(context),
    private val client: OkHttpClient = defaultClient(),
) : TextToSpeech {
    private val appContext = context.applicationContext
    private var currentPlayer: MediaPlayer? = null
    private val pendingUtterances = ConcurrentHashMap.newKeySet<String>()
    private val utteranceCounter = AtomicLong(0)
    private val playLock = Mutex()
    @Volatile
    private var idleCallback: (() -> Unit)? = null

    override val isAvailable: Boolean
        get() = config.endpoint.apiKey.isNotBlank()

    override suspend fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val apiKey = config.endpoint.apiKey.trim()
        if (apiKey.isBlank()) return

        val utteranceId = "cloud-tts-${utteranceCounter.incrementAndGet()}"
        pendingUtterances.add(utteranceId)
        audioFocusManager.requestDuckFocus()

        try {
            val audioBytes = fetchSpeechAudio(trimmed, apiKey)
            playLock.withLock {
                playAudioBytes(audioBytes, utteranceId)
            }
        } catch (_: Exception) {
            pendingUtterances.remove(utteranceId)
            maybeIdle()
        }
    }

    private suspend fun fetchSpeechAudio(text: String, apiKey: String): ByteArray = withContext(Dispatchers.IO) {
        val baseUrl = config.endpoint.baseUrl.trim()
        val speechUrl = resolveSpeechUrl(baseUrl)
        val model = resolveTtsModel(baseUrl)
        val voice = resolveTtsVoice(baseUrl, config.personaId)

        val payload = JSONObject().apply {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", "mp3")
        }

        val authHeader = if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey"
        val request = Request.Builder()
            .url(speechUrl)
            .addHeader("Authorization", authHeader)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("Cloud TTS HTTP ${resp.code}: $err")
            }
            resp.body?.bytes() ?: throw IOException("Empty TTS audio response")
        }
    }

    private suspend fun playAudioBytes(audioBytes: ByteArray, utteranceId: String) = withContext(Dispatchers.Main) {
        val tempFile = File.createTempFile("copilot_speech_", ".mp3", appContext.cacheDir)
        tempFile.deleteOnExit()
        withContext(Dispatchers.IO) {
            tempFile.writeBytes(audioBytes)
        }

        val player = MediaPlayer()
        currentPlayer = player
        val completion = CompletableDeferred<Unit>()

        player.setDataSource(tempFile.absolutePath)
        player.setOnCompletionListener {
            player.release()
            if (currentPlayer === player) currentPlayer = null
            tempFile.delete()
            pendingUtterances.remove(utteranceId)
            maybeIdle()
            completion.complete(Unit)
        }
        player.setOnErrorListener { _, _, _ ->
            player.release()
            if (currentPlayer === player) currentPlayer = null
            tempFile.delete()
            pendingUtterances.remove(utteranceId)
            maybeIdle()
            completion.complete(Unit)
            true
        }
        player.prepare()
        player.start()

        completion.await()
    }

    override fun interrupt() {
        pendingUtterances.clear()
        currentPlayer?.let {
            runCatching {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        currentPlayer = null
        maybeIdle()
    }

    override fun shutdown() {
        interrupt()
        audioFocusManager.abandonDuckFocus()
    }

    override fun setOnIdle(callback: () -> Unit) {
        idleCallback = callback
    }

    private fun maybeIdle() {
        if (pendingUtterances.isEmpty()) {
            audioFocusManager.abandonDuckFocus()
            idleCallback?.invoke()
        }
    }

    companion object {
        fun resolveSpeechUrl(baseUrl: String): String {
            var url = baseUrl.trim()
            if (url.isEmpty()) url = "https://api.siliconflow.cn/v1"
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            url = url.trimEnd('/')
            if (url.endsWith("/chat/completions", ignoreCase = true)) {
                return url.removeSuffix("/chat/completions") + "/audio/speech"
            }
            if (url.endsWith("/v1", ignoreCase = true) || url.endsWith("/compatible-mode", ignoreCase = true)) {
                return "$url/audio/speech"
            }
            return "$url/v1/audio/speech"
        }

        fun resolveTtsModel(baseUrl: String): String {
            val lower = baseUrl.lowercase()
            return when {
                lower.contains("siliconflow") -> "FunAudioLLM/CosyVoice2-0.5B"
                else -> "tts-1"
            }
        }

        fun resolveTtsVoice(baseUrl: String, personaId: String = ""): String {
            val lower = baseUrl.lowercase()
            return when {
                lower.contains("siliconflow") -> when (personaId) {
                    "cheerful" -> "FunAudioLLM/CosyVoice2-0.5B:anna" // 活泼灵动闺蜜女声
                    "calm" -> "FunAudioLLM/CosyVoice2-0.5B:benjamin" // 沉稳浑厚成熟老哥男声
                    "sarcastic" -> "FunAudioLLM/CosyVoice2-0.5B:charles" // 幽默磁性损友男声
                    else -> "FunAudioLLM/CosyVoice2-0.5B:anna"
                }
                else -> when (personaId) {
                    "cheerful" -> "nova"
                    "calm" -> "onyx" // Deep male voice
                    "sarcastic" -> "echo" // Confident male voice
                    else -> "alloy"
                }
            }
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
