package com.qmxz.pilotbot.copilot

import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.context.ContextBuilder
import com.qmxz.pilotbot.context.SimpleContextBuilder
import com.qmxz.pilotbot.llm.ChatHistory
import com.qmxz.pilotbot.llm.ChatMessage
import com.qmxz.pilotbot.llm.GenerationConfig
import com.qmxz.pilotbot.llm.LlmProvider
import com.qmxz.pilotbot.llm.Role
import com.qmxz.pilotbot.memory.MemoryStore
import com.qmxz.pilotbot.navi.NaviState
import com.qmxz.pilotbot.safety.DrivingLoadEstimator
import com.qmxz.pilotbot.safety.DrivingLoadLevel
import com.qmxz.pilotbot.safety.FatigueMonitor
import com.qmxz.pilotbot.safety.SimpleDrivingLoadEstimator
import com.qmxz.pilotbot.tts.TextToSpeech
import com.qmxz.pilotbot.tts.extractCompleteSentences
import kotlin.coroutines.cancellation.CancellationException
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
    private val memoryStore: MemoryStore? = null,
    private val contextBuilder: ContextBuilder = SimpleContextBuilder(),
    private val loadEstimator: DrivingLoadEstimator = SimpleDrivingLoadEstimator(),
    private val fatigueMonitor: FatigueMonitor = FatigueMonitor(),
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
    private var locationDesc: String? = null
    private var latestLoad: DrivingLoadLevel = DrivingLoadLevel.L3_ACTIVE
    // Speaking-window state (both only touched on the main thread).
    private var speaking = false
    private var generationFinished = false

    init {
        // Resume listening (half-duplex voice modes) once the reply has finished playing.
        // An idle fired by interrupt() while the old generation is being replaced must NOT close
        // the new window, hence the generationFinished gate.
        tts.setOnIdle { scope.launch { maybeEndSpeaking() } }

        fatigueMonitor.onFatigueWarning = { minutes ->
            narrate(
                "车主已经连续驾驶了 $minutes 分钟。作为副驾好友，用关心但轻松自然的口吻提醒车主注意休息，建议进服务区喝口水或活动一下。",
                bypassLoadGate = true,
            )
        }
        fatigueMonitor.onNightCare = {
            narrate(
                "现在是深夜行车时段。作为副驾好友，主动关心一下车主，提醒夜间行车注意视线和安全，给车主提提神。",
                bypassLoadGate = true,
            )
        }
        fatigueMonitor.onCongestionBoredom = {
            narrate(
                "当前路段严重拥堵已经超过 10 分钟了。作为副驾好友，主动开口帮车主解解闷，聊聊天或安慰一下路况，缓解堵车的烦躁。",
                bypassLoadGate = true,
            )
        }
    }

    /** Builds the complete system prompt including persona configuration and long-term memory. */
    private fun buildSystemPrompt(): String {
        val memoryPrompt = memoryStore?.buildMemoryPrompt().orEmpty()
        return config.currentPersona().buildSystemPrompt(memoryPrompt)
    }

    /** Updates the latest navigation snapshot and re-estimates driving load. */
    fun updateNaviState(state: NaviState, isNavigating: Boolean = true) {
        latestState = state
        latestLoad = loadEstimator.estimate(state)
        fatigueMonitor.updateState(state, isNavigating)
    }

    /** Resets fatigue and session tracking when navigation ends. */
    fun resetFatigueMonitor() {
        fatigueMonitor.reset()
    }

    /** Feeds the current location description so chat can answer "where am I". */
    fun updateLocation(description: String) {
        locationDesc = description
    }

    /**
     * Rewrites one navigation broadcast into copilot speech. Under L0/L1 (crawl / tight
     * manoeuvre) the copilot stays quiet and only the SDK's own broadcast is heard. User chat is
     * unaffected.
     */
    fun speakAbout(naviText: String) {
        latestNaviText = naviText
        if (latestLoad == DrivingLoadLevel.L0_SILENT || latestLoad == DrivingLoadLevel.L1_RESTRAINED) return

        val messages = listOf(
            ChatMessage(Role.SYSTEM, buildSystemPrompt()),
            ChatMessage(Role.USER, contextBuilder.buildContextBlock(contextBuilder.buildEvent(naviText))),
        )
        generate(messages, onDone = {})
    }

    /**
     * Proactive narration (e.g. crossing an administrative boundary, fatigue care).
     * When [bypassLoadGate] is false (default), the copilot stays quiet during demanding driving (L0/L1).
     * For high-priority care alerts, [bypassLoadGate] allows delivering the care message.
     */
    fun narrate(situation: String, bypassLoadGate: Boolean = false) {
        if (!bypassLoadGate && (latestLoad == DrivingLoadLevel.L0_SILENT || latestLoad == DrivingLoadLevel.L1_RESTRAINED)) return
        if (situation.isBlank()) return

        val messages = listOf(
            ChatMessage(Role.SYSTEM, buildSystemPrompt()),
            ChatMessage(Role.USER, situation),
        )
        generate(messages, onDone = {})
    }

    /** Directly speaks a short message without LLM generation (e.g. system confirmations, memory acknowledgments). */
    fun speakDirect(text: String) {
        interrupt()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        postText(trimmed)
        onCopilotDone(trimmed)
        speaking = true
        generationFinished = true
        onSpeakingStart()
        scope.launch {
            if (tts.isAvailable) {
                tts.speak(trimmed)
            } else {
                maybeEndSpeaking()
            }
        }
    }

    /** Sends a user utterance into the multi-turn conversation, cutting off any previous reply. */
    fun chat(userText: String) {
        interrupt()
        val text = userText.trim()
        if (text.isEmpty()) return

        val messages = buildList {
            add(ChatMessage(Role.SYSTEM, buildSystemPrompt()))
            latestNaviText?.let { add(ChatMessage(Role.SYSTEM, "当前驾驶情境：$it")) }
            locationDesc?.let { add(ChatMessage(Role.SYSTEM, "你当前位置：$it")) }
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
            postText("⚠️ 还没配置大模型，请点击此气泡或前往「设置」填入 API Key！")
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
            try {
                withContext(Dispatchers.IO) {
                    llm.streamChat(endpoint, messages, GenerationConfig()) { delta ->
                        fullText.append(delta)
                        postText(fullText.toString())
                        val segment = fullText.substring(spokenUpTo)
                        val extraction = extractCompleteSentences(segment)
                        if (extraction.sentences.isNotEmpty()) {
                            spokenUpTo += extraction.consumedLength
                            if (tts.isAvailable) {
                                spokenAnything = true
                                for (sentence in extraction.sentences) {
                                    scope.launch { tts.speak(sentence) }
                                }
                            }
                        }
                    }
                }
                val rest = fullText.substring(spokenUpTo).trim()
                if (rest.isNotEmpty() && tts.isAvailable) {
                    spokenAnything = true
                    scope.launch { tts.speak(rest) }
                }
                val final = fullText.toString()
                onCopilotDone(final)
                onDone(final)
                // Close the speaking window: immediately if nothing was queued, else when TTS drains.
                generationFinished = true
                if (!spokenAnything) maybeEndSpeaking()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface LLM/network failures with actionable diagnosis.
                val errorMsg = when {
                    e.message?.contains("401") == true -> "❌ 认证失败(401)：请在「设置」中核对 API Key 是否正确"
                    e.message?.contains("404") == true -> "❌ 接口不存在(404)：请在「设置」中检查 base_url 和模型名"
                    e.message?.contains("429") == true -> "❌ 额度受限(429)：账户余额不足或请求频率过高"
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "❌ 网络异常：无法连接到服务器，请检查手机网络"
                    else -> "❌ 连接失败：${e.message ?: e.javaClass.simpleName}"
                }
                postText(errorMsg)
                generationFinished = true
                if (!spokenAnything) maybeEndSpeaking()
            }
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
