package com.qmxz.pilotbot.asr

import com.qmxz.pilotbot.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cloud Speech-to-Text client compatible with OpenAI / SiliconFlow / Groq / DashScope / FastWhisper audio transcription endpoints.
 * Features automatic multi-model failover on 503 (Service Unavailable) / 502 / 504 / 429 / 500 errors.
 */
class CloudSpeechToText(
    private val config: AppConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    /**
     * Sends WAV audio bytes to the cloud speech recognition endpoint and returns the transcribed text.
     */
    suspend fun transcribe(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val endpoint = config.asrEndpoint
        val asrKey = endpoint.apiKey.trim()
        val asrBaseUrl = endpoint.baseUrl.trim()

        if (asrKey.isBlank()) {
            throw IllegalStateException("未配置 API Key，请在「设置」中填入 API Key 开启语音识别")
        }

        val lowerBase = asrBaseUrl.lowercase()
        if (config.asrApiKey.isBlank() && (lowerBase.contains("deepseek") || lowerBase.contains("moonshot") || lowerBase.contains("bigmodel"))) {
            throw IllegalStateException("DeepSeek/Kimi 官方未提供语音转写接口。推荐在「设置」中选择「⚡ 硅基流动」（包含 DeepSeek-V3 且支持免费极速语音识别），或单独填入语音 Key！")
        }

        val transcriptionUrl = resolveTranscriptionUrl(asrBaseUrl)
        val candidateModels = resolveCandidateModels(asrBaseUrl, endpoint.model.trim())

        var lastError: Exception? = null

        for (model in candidateModels) {
            // Try up to 2 attempts per candidate model
            for (attempt in 1..2) {
                try {
                    val result = executeTranscribeRequest(transcriptionUrl, asrKey, model, wavBytes)
                    if (result.isNotBlank()) {
                        return@withContext result
                    }
                } catch (e: Exception) {
                    lastError = e
                    // If error is transient (503/502/504/429/timeout), brief pause and retry/fallback
                    val msg = e.message.orEmpty()
                    if (msg.contains("503") || msg.contains("502") || msg.contains("504") || msg.contains("429") || msg.contains("timeout")) {
                        delay(250L * attempt)
                    } else {
                        break // Non-transient error on this model, try next candidate model
                    }
                }
            }
        }

        throw lastError ?: IOException("语音识别服务暂时不可用，请稍后重试")
    }

    private fun executeTranscribeRequest(
        transcriptionUrl: String,
        asrKey: String,
        model: String,
        wavBytes: ByteArray,
    ): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "audio.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType()),
            )
            .addFormDataPart("model", model)
            .addFormDataPart("language", "zh")
            .build()

        val authHeader = if (asrKey.startsWith("Bearer ", ignoreCase = true)) asrKey else "Bearer $asrKey"
        val request = Request.Builder()
            .url(transcriptionUrl)
            .addHeader("Authorization", authHeader)
            .post(requestBody)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("语音识别网络请求失败: ${e.message}", e)
        }

        response.use { resp ->
            val bodyString = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val errorMsg = try {
                    JSONObject(bodyString).optJSONObject("error")?.optString("message") ?: bodyString
                } catch (_: Exception) {
                    bodyString
                }
                throw IOException("语音识别服务响应错误 (${resp.code}) [model: $model]: $errorMsg")
            }

            return try {
                val json = JSONObject(bodyString)
                json.optString("text").trim()
            } catch (e: Exception) {
                throw IOException("解析语音识别结果失败: ${e.message}, 返回内容: $bodyString", e)
            }
        }
    }

    companion object {
        fun resolveTranscriptionUrl(baseUrl: String): String {
            var url = baseUrl.trim()
            if (url.isEmpty()) {
                url = "https://api.siliconflow.cn/v1"
            }
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                url = "https://$url"
            }
            url = url.trimEnd('/')

            if (url.endsWith("/chat/completions", ignoreCase = true)) {
                return url.removeSuffix("/chat/completions") + "/audio/transcriptions"
            }
            if (url.endsWith("/v1", ignoreCase = true) || url.endsWith("/compatible-mode", ignoreCase = true)) {
                return "$url/audio/transcriptions"
            }
            val uri = try { java.net.URI(url) } catch (_: Exception) { null }
            if (uri != null && (uri.path.isNullOrEmpty() || uri.path == "/")) {
                return "$url/v1/audio/transcriptions"
            }
            return "$url/audio/transcriptions"
        }

        fun resolveCandidateModels(baseUrl: String, userCustomModel: String): List<String> {
            if (userCustomModel.isNotBlank()) {
                return listOf(userCustomModel, "FunAudioLLM/SenseVoiceSmall", "openai/whisper-large-v3-turbo")
            }
            val lower = baseUrl.lowercase()
            return when {
                lower.contains("siliconflow") -> listOf(
                    "FunAudioLLM/SenseVoiceSmall",
                    "openai/whisper-large-v3-turbo",
                    "Tele-AI/TeleSpeech-ASR",
                )
                lower.contains("groq") -> listOf(
                    "whisper-large-v3-turbo",
                    "whisper-large-v3",
                )
                else -> listOf(
                    "FunAudioLLM/SenseVoiceSmall",
                    "openai/whisper-large-v3-turbo",
                    "whisper-1",
                )
            }
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
