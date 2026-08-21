package com.qmxz.pilotbot.llm

/** A user-configurable OpenAI-compatible model endpoint. */
data class LlmEndpoint(
    /** e.g. "https://api.deepseek.com/v1" (without the trailing /chat/completions). */
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)
