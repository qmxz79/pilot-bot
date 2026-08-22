package com.qmxz.pilotbot.copilot

import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.context.ContextBuilder
import com.qmxz.pilotbot.context.SimpleContextBuilder
import com.qmxz.pilotbot.llm.ChatHistory
import com.qmxz.pilotbot.llm.ChatMessage
import com.qmxz.pilotbot.llm.GenerationConfig
import com.qmxz.pilotbot.llm.LlmProvider
import com.qmxz.pilotbot.llm.Role
import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.safety.DrivingLoadEstimator
import com.qmxz.pilotbot.safety.DrivingLoadLevel
import com.qmxz.pilotbot.safety.SimpleDrivingLoadEstimator
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
 * M2 copilot: navigation broadcast rewriting plus multi-turn chat, both streamed to TTS.
 *
 * Two interrupt sources converge on [interrupt] (cancel generation + stop TTS): a newer navigation
 * broadcast (navigation-first rule) and the user beginning to speak. New input always carries
 * multi-turn [ChatHistory] so the copilot can be redirected mid-conversation.
 */
class CopilotEngine(
    private val config: AppConfig,
    private val llm: LlmProvider,
    private val tts: TextToSpeech,
    private val contextBuilder: ContextBuilder = SimpleContextBuilder(),
    private val loadEstimator: DrivingLoadEstimator = SimpleDrivingLoadEstimator(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val onCopilotText: (String) -> Unit = {},
    private val onCopilotDone: (String) -> Unit = {},
    private val onSpeakingStart: () -> Unit = {},
    private val onSpeakingEnd: () -> Unit = {},
) {
    private val history = ChatHistory()
    private var generation: Job? = null
    private var latestState: NaviState? = null
    private var latestNaviText: String? = null
    // Speaking-window state (both only touched on the main thread).
    private var speaking = false
    private var generationFinished = false

    init {
        // Resume listening (half-duplex voice modes) once the reply has finished playing.
        // An idle fired by interrupt() while the old generation is being replaced must NOT close
        // the new window, hence the generationFinished gate.
        tts.setOnIdle { scope.launch { maybeEndSpeaking() } }
    }

    /** Updates the latest navigation snapshot used for driving-load estimation. */
    fun updateNaviState(state: NaviState) {
        latestState = state
    }

    /**
     * Rewrites one navigation broadcast into copilot speech. When the load estimator says L0
     * (heavy traffic / tight manoeuvres), the copilot stays quiet and only the SDK's own
     * broadcast is heard. User chat is unaffected.
     */
    fun speakAbout(naviText: String) {
        latestNaviText = naviText
        if (loadEstimator.estimate(latestState) == DrivingLoadLevel.L0_SILENT) return

        val messages = listOf(
            ChatMessage(Role.SYSTEM, config.currentPersona().buildSystemPrompt()),
            ChatMessage(Role.USER, contextBuilder.buildContextBlock(contextBuilder.buildEvent(naviText))),
        )
        generate(messages, onDone = {})
    }

    /** Sends a user utterance into the multi-turn conversation, cutting off any previous reply. */
    fun chat(userText: String) {
        interrupt()
        val text = userText.trim()
        if (text.isEmpty()) return

        val messages = buildList {
            add(ChatMessage(Role.SYSTEM, config.currentPersona().buildSystemPrompt()))
            latestNaviText?.let { add(ChatMessage(Role.SYSTEM, "当前驾驶情境：$it")) }
            addAll(history.messages())
            add(ChatMessage(Role.USER, text))
        }
        history.append(ChatMessage(Role.USER, text))

        generate(messages) { full ->
            history.append(ChatMessage(Role.ASSISTANT, full))
        }
    }

    /** Cancels the in-flight generation and stops TTS. Call on user speech start or new navi text. */
    fun interrupt() {
        generation?.cancel()
        tts.interrupt()
    }

    fun close() {
        generation?.cancel()
        scope.cancel()
    }

    private fun generate(messages: List<ChatMessage>, onDone: (String) -> Unit) {
        val endpoint = config.endpoint
        if (endpoint.baseUrl.isBlank() || endpoint.apiKey.isBlank() || endpoint.model.isBlank()) {
            postText("还没配置模型，去「设置」填一下 base_url / api_key / model")
            return
        }

        // Cut off the previous generation/TTS, then open a fresh speaking window. interrupt() may
        // fire a stale TTS-idle, but generationFinished is still false so it cannot close the new
        // window (see init).
        interrupt()
        speaking = true
        generationFinished = false
        onSpeakingStart()

        val fullText = StringBuilder()
        // Offset into fullText already handed to TTS; used to speak only new sentences.
        var spokenUpTo = 0
        var spokenAnything = false

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
                        spokenAnything = true
                        scope.launch { tts.speak(spoken) }
                    }
                }
            }
            val rest = fullText.substring(spokenUpTo).trim()
            if (rest.isNotEmpty()) {
                spokenAnything = true
                scope.launch { tts.speak(rest) }
            }
            val final = fullText.toString()
            onCopilotDone(final)
            onDone(final)
            // Close the speaking window: immediately if nothing was queued, else when TTS drains.
            generationFinished = true
            if (!spokenAnything) maybeEndSpeaking()
        }
    }

    /** Closes the speaking window once the reply has finished AND the TTS queue is empty. */
    private fun maybeEndSpeaking() {
        if (speaking && generationFinished) {
            speaking = false
            onSpeakingEnd()
        }
    }

    private fun postText(text: String) {
        scope.launch { onCopilotText(text) }
    }
}
