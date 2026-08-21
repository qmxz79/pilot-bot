package com.qmxz.pilotbot.llm

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One OpenAI-compatible SSE client covering DeepSeek / Qwen / Zhipu / Kimi / MiniMax et al.
 * Base URL, key and model are supplied per call through [LlmEndpoint].
 *
 * Cancellation of the calling coroutine aborts the connection via [Call.cancel], so a newer
 * navigation broadcast can reliably cut off an in-flight generation.
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient = defaultClient(),
) : LlmProvider {

    override val supportsStreaming: Boolean = true

    override suspend fun streamChat(
        endpoint: LlmEndpoint,
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onDelta: (String) -> Unit,
    ): ChatResult = suspendCancellableCoroutine { cont ->
        val request = buildRequest(endpoint, messages, config)
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body ?: throw IOException("空响应体")
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${body.string().take(200)}")
                    }
                    val full = StringBuilder()
                    var finishReason: String? = null
                    body.source().use { source ->
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val delta = parseSseDelta(line) ?: continue
                            delta.content?.let {
                                full.append(it)
                                onDelta(it)
                            }
                            delta.finishReason?.let { finishReason = it }
                        }
                    }
                    cont.resume(ChatResult(full.toString(), finishReason))
                } catch (e: Exception) {
                    if (!cont.isCancelled) cont.resumeWithException(e)
                }
            }
        })
    }

    private fun buildRequest(
        endpoint: LlmEndpoint,
        messages: List<ChatMessage>,
        config: GenerationConfig,
    ): Request {
        val payload = JSONObject().apply {
            put("model", endpoint.model)
            put("stream", true)
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokens)
            put("messages", JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().put("role", msg.role.name.lowercase()).put("content", msg.content))
                }
            })
            if (config.stopSequences.isNotEmpty()) put("stop", JSONArray(config.stopSequences))
        }
        return Request.Builder()
            .url(endpoint.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer ${endpoint.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** One SSE `data:` delta from the stream; null for heartbeats / [DONE] / non-data lines. */
data class SseDelta(
    val content: String?,
    val finishReason: String?,
)

/**
 * Pure parser: `data: {...}` -> choices[0].delta.content / choices[0].finish_reason.
 * Returns null for keep-alive lines, `data: [DONE]`, or malformed JSON.
 */
fun parseSseDelta(line: String): SseDelta? {
    if (!line.startsWith("data:")) return null
    val payload = line.removePrefix("data:").trim()
    if (payload.isEmpty() || payload == "[DONE]") return null
    val json = try {
        JSONObject(payload)
    } catch (_: Exception) {
        return null
    }
    val choices = json.optJSONArray("choices") ?: return null
    if (choices.length() == 0) return null
    val first = choices.getJSONObject(0)
    val delta = first.optJSONObject("delta") ?: return null
    return SseDelta(
        content = delta.optString("content").takeIf { it.isNotEmpty() },
        finishReason = first.optString("finish_reason").takeIf { it.isNotEmpty() },
    )
}
