package com.qmxz.pilotbot.llm

enum class Role { SYSTEM, USER, ASSISTANT }

data class ChatMessage(
    val role: Role,
    val content: String,
)

data class GenerationConfig(
    val temperature: Double = 0.8,
    val maxTokens: Int = 512,
    val stopSequences: List<String> = emptyList(),
)

data class ChatResult(
    val fullText: String,
    val finishReason: String?,
)

/** Streams a chat completion. The [endpoint] is supplied per call so it can change at runtime. */
interface LlmProvider {
    val supportsStreaming: Boolean

    /**
     * Streams deltas of the assistant reply through [onDelta]. Cancelling the calling coroutine
     * aborts the connection.
     */
    suspend fun streamChat(
        endpoint: LlmEndpoint,
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onDelta: (String) -> Unit,
    ): ChatResult
}
