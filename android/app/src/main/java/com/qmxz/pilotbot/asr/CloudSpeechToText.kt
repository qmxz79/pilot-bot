package com.qmxz.pilotbot.asr

import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.llm.normalizeChatCompletionsUrl
import kotlinx.coroutines.Dispatchers
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
 */
class CloudSpeechToText(
    private val config: AppConfig,
    private val client: OkHttpClient = defaultClient(),
) {
    /**
     * Sends WAV audio bytes to the cloud speech recognition endpoint and returns the transcribed text.
     */
    suspend fun transcribe(wavBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val asrKey = config.asrApiKey.trim().ifBlank { config.endpoint.apiKey.trim() }
        val asrBaseUrl = config.asrBaseUrl.trim().ifBlank { config.endpoint.baseUrl.trim() }

        if (asrKey.isBlank()) {
            throw IllegalStateException("未配置 API Key，请在「设置」中填入 API Key 开启语音识别")
        }

        val lowerBase = asrBaseUrl.lowercase()
        if (config.asrApiKey.isBlank() && (lowerBase.contains("deepseek") || lowerBase.contains("moonshot") || lowerBase.contains("bigmodel"))) {
            throw IllegalStateException("DeepSeek/Kimi 官方未提供语音转写接口。推荐在「设置」中选择「⚡ 硅基流动」（包含 DeepSeek-V3 且支持免费极速语音识别），或单独填入语音 Key！")
        }

        val transcriptionUrl = resolveTranscriptionUrl(asrBaseUrl)
        val model = if (config.asrModel.isNotBlank()) config.asrModel.trim() else resolveAsrModel(asrBaseUrl)

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
                throw IOException("语音识别服务响应错误 (${resp.code}): $errorMsg")
            }

            try {
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

            // If user passed a full path ending with /chat/completions, replace it with /audio/transcriptions
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

        fun resolveAsrModel(baseUrl: String): String {
            val lower = baseUrl.lowercase()
            return when {
                lower.contains("siliconflow") -> "FunAudioLLM/SenseVoiceSmall"
                lower.contains("groq") -> "whisper-large-v3"
                else -> "FunAudioLLM/SenseVoiceSmall" // SenseVoiceSmall is standard on SiliconFlow, fallback whisper-1
            }
        }

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
