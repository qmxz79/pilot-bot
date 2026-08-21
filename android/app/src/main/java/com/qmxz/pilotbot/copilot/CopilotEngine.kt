package com.qmxz.pilotbot.copilot

import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.context.ContextBuilder
import com.qmxz.pilotbot.context.SimpleContextBuilder
import com.qmxz.pilotbot.llm.ChatMessage
import com.qmxz.pilotbot.llm.GenerationConfig
import com.qmxz.pilotbot.llm.LlmProvider
import com.qmxz.pilotbot.llm.Role
import com.qmxz.pilotbot.tts.TextToSpeech
import com.qmxz.pilotbot.tts.splitSentences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M1 core loop: navigation broadcast -> structured event -> LLM streaming rewrite -> streaming TTS.
 *
 * A newer broadcast cancels the in-flight generation (LLM connection aborts via the provider's
 * cancellation) and interrupts TTS, per the navigation-first rule.
 */
class CopilotEngine(
    private val config: AppConfig,
    private val llm: LlmProvider,
    private val tts: TextToSpeech,
    private val contextBuilder: ContextBuilder = SimpleContextBuilder(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val onCopilotText: (String) -> Unit = {},
) {
    private var generation: Job? = null

    /** Rewrites one navigation broadcast into copilot speech, cutting off any previous reply. */
    fun speakAbout(naviText: String) {
        val endpoint = config.endpoint
        if (endpoint.baseUrl.isBlank() || endpoint.apiKey.isBlank() || endpoint.model.isBlank()) {
            postText("还没配置模型，去「设置」填一下 base_url / api_key / model")
            return
        }

        generation?.cancel()
        tts.interrupt()

        val messages = listOf(
            ChatMessage(Role.SYSTEM, config.persona.buildSystemPrompt()),
            ChatMessage(Role.USER, contextBuilder.buildContextBlock(contextBuilder.buildEvent(naviText))),
        )

        val fullText = StringBuilder()
        // Byte/char offset into fullText already handed to TTS; used to speak only new sentences.
        var spokenUpTo = 0

        generation = scope.launch {
            withContext(Dispatchers.IO) {
                llm.streamChat(endpoint, messages, GenerationConfig()) { delta ->
                    fullText.append(delta)
                    postText(fullText.toString())
                    val segment = fullText.substring(spokenUpTo)
                    val sentences = splitSentences(segment)
                    if (sentences.isNotEmpty()) {
                        val spoken = sentences.joinToString("")
                        spokenUpTo += spoken.length
                        scope.launch { tts.speak(spoken) }
                    }
                }
            }
            val rest = fullText.substring(spokenUpTo).trim()
            if (rest.isNotEmpty()) scope.launch { tts.speak(rest) }
        }
    }

    fun close() {
        generation?.cancel()
        scope.cancel()
    }

    private fun postText(text: String) {
        scope.launch { onCopilotText(text) }
    }
}
