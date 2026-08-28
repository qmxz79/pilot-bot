package com.qmxz.pilotbot.voice

import com.qmxz.pilotbot.asr.SmartSpeechToText
import com.qmxz.pilotbot.asr.SpeechToText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.copilot.CopilotEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Coordinates voice interaction according to [ConversationMode].
 * - Push-to-talk: mic button listens once then stops.
 * - Continuous: hands-free half-duplex (pauses mic while copilot speaks, resumes after reply).
 * - Wake word: hands-free with wake word prefix required (with 10-second active turn-taking window).
 * - Full duplex: continuous listening with mic remaining active while copilot speaks,
 *   hardware AEC/NS + software [EchoFilter] feedback suppression, and millisecond-level barge-in.
 */
class VoiceController(
    private val config: AppConfig,
    private val speechToText: SpeechToText,
    private val copilot: CopilotEngine,
    val echoFilter: EchoFilter = EchoFilter(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val onListeningState: (Boolean) -> Unit = {},
    private val onStatusUpdate: (String) -> Unit = {},
    private val onUserText: (String) -> Unit = {},
    private val onListenError: (String) -> Unit = {},
    private val onInterrupt: (() -> Unit)? = null,
    private val onUtterance: ((String) -> Unit)? = null,
) {
    companion object {
        /** Duration of the active multi-turn window where wake word is not required (10 seconds). */
        const val TURN_TAKING_WINDOW_MS = 10_000L
    }

    private var handsFreeEnabled = false
    private var listening = false
    private var turnTakingDeadline: Long = 0L

    val isListening: Boolean get() = listening

    val isHandsFreeEnabled: Boolean get() = handsFreeEnabled

    val isTurnTakingActive: Boolean
        get() = System.currentTimeMillis() < turnTakingDeadline

    /** Automatically activates hands-free listening when in Continuous / WakeWord / FullDuplex modes. */
    fun startHandsFreeIfConfigured() {
        if (config.conversationMode != ConversationMode.PUSH_TO_TALK) {
            handsFreeEnabled = true
            resumeListening()
        } else {
            stopListening()
        }
    }

    /** Mic button: one-shot in push-to-talk, a hands-free on/off toggle in continuous/duplex modes. */
    fun toggleMic() {
        when (config.conversationMode) {
            ConversationMode.PUSH_TO_TALK -> pushToTalk()
            ConversationMode.CONTINUOUS,
            ConversationMode.WAKE_WORD,
            ConversationMode.FULL_DUPLEX -> {
                handsFreeEnabled = !handsFreeEnabled
                if (handsFreeEnabled) resumeListening() else stopListening()
            }
        }
    }

    /**
     * Records copilot speech text into [EchoFilter] so subsequent acoustic reflections
     * can be detected and dropped.
     */
    fun onCopilotText(text: String) {
        echoFilter.recordSpeaking(text)
    }

    fun recordCopilotSpeech(text: String) {
        echoFilter.recordSpeaking(text)
    }

    /**
     * Copilot began speaking/playing TTS:
     * - In [ConversationMode.FULL_DUPLEX]: microphone stays OPEN;
     * - In [ConversationMode.CONTINUOUS] / [ConversationMode.WAKE_WORD]: pause mic to prevent self-triggering.
     */
    fun onCopilotSpeakingStart(spokenText: String? = null) {
        if (!spokenText.isNullOrBlank()) {
            echoFilter.recordSpeaking(spokenText)
        }

        if (config.conversationMode != ConversationMode.FULL_DUPLEX) {
            if (listening) pauseListening()
        }
    }

    /**
     * Copilot finished speaking:
     * - Refreshes the 10-second active turn-taking window;
     * - Resumes listening if hands-free is enabled and mic was paused.
     */
    fun onCopilotSpeakingEnd() {
        turnTakingDeadline = System.currentTimeMillis() + TURN_TAKING_WINDOW_MS

        if (handsFreeEnabled && !listening) {
            resumeListening()
        }
    }

    fun stopListening() {
        handsFreeEnabled = false
        turnTakingDeadline = 0L
        pauseListening()
    }

    fun shutdown() {
        stopListening()
        echoFilter.clear()
        speechToText.shutdown()
        scope.cancel()
    }

    private fun pushToTalk() {
        if (listening) {
            listening = false
            onListeningState(false)
            onStatusUpdate("⏳ 正在识别文字...")
            (speechToText as? SmartSpeechToText)?.stopListeningNow()
            return
        }
        copilot.interrupt()
        listening = true
        onListeningState(true)
        onStatusUpdate("👂 正在倾听，请说话...")
        scope.launch {
            try {
                val text = speechToText.listenOnce()
                onStatusUpdate("")
                val trimmed = text.trim()
                if (trimmed.isNotBlank()) {
                    if (echoFilter.isEcho(trimmed)) {
                        onListenError("检测到副驾自身回音，已忽略")
                        return@launch
                    }
                    turnTakingDeadline = System.currentTimeMillis() + TURN_TAKING_WINDOW_MS
                    dispatchUtterance(trimmed)
                } else {
                    onListenError("未检测到有效语音，请重试")
                }
            } catch (e: Exception) {
                onStatusUpdate("")
                onListenError(e.message ?: "识别失败")
            } finally {
                listening = false
                onListeningState(false)
            }
        }
    }

    private fun resumeListening() {
        listening = true
        onListeningState(true)
        val statusText = when (config.conversationMode) {
            ConversationMode.FULL_DUPLEX -> "⚡ 全双工实时倾听中 (随时说话/随时打断)..."
            ConversationMode.WAKE_WORD -> "🎙️ 唤醒模式倾听中 (呼唤「${config.wakeWord.ifBlank { "小伴" }}」)..."
            ConversationMode.CONTINUOUS -> "👂 连续对话倾听中 (免唤醒)..."
            ConversationMode.PUSH_TO_TALK -> "👂 正在倾听..."
        }
        onStatusUpdate(statusText)

        speechToText.startContinuous(
            onResult = { text -> handleResult(text) },
            onSpeechStart = { handleBargeIn() },
        )
    }

    private fun pauseListening() {
        listening = false
        onListeningState(false)
        onStatusUpdate("")
        speechToText.cancel()
    }

    /**
     * Millisecond-level Barge-in:
     * When user voice onset / energy is detected, immediately interrupt copilot generation and TTS.
     */
    private fun handleBargeIn() {
        if (copilot.isSpeaking) {
            copilot.interrupt()
            onInterrupt?.invoke()
        } else {
            copilot.interrupt()
        }
    }

    private fun handleResult(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // Acoustic Echo Cancellation / Echo Filter check:
        // Silently drop if transcribed text matches copilot's own recent speech (> 75% similarity)
        if (echoFilter.isEcho(trimmed)) {
            return
        }

        val targetWake = if (config.conversationMode == ConversationMode.WAKE_WORD) {
            config.wakeWord.trim().ifBlank { "小伴" }
        } else null

        val message = if (config.conversationMode == ConversationMode.WAKE_WORD && !targetWake.isNullOrEmpty()) {
            val clean = trimmed.replace(Regex("^[，,：:!！?？~\\s]+"), "")
            val wakePatterns = listOf(
                targetWake,
                "你好$targetWake",
                "嗨$targetWake",
                "呼叫$targetWake",
                "${targetWake}${targetWake}",
            )
            val matched = wakePatterns.firstOrNull { clean.startsWith(it, ignoreCase = true) }
            if (matched != null) {
                clean.substring(matched.length).replace(Regex("^[，,：:!！?？~\\s]+"), "").trim().ifBlank { "你好！" }
            } else if (isTurnTakingActive) {
                // In active 10s turn-taking window: exempt from wake word requirement
                clean
            } else {
                // Outside active turn-taking window without wake word -> ignore
                return
            }
        } else {
            trimmed
        }

        if (message.isBlank()) return

        // Update active turn-taking deadline upon successful user input
        turnTakingDeadline = System.currentTimeMillis() + TURN_TAKING_WINDOW_MS
        dispatchUtterance(message)
    }

    private fun dispatchUtterance(message: String) {
        onUserText(message)
        if (onUtterance != null) {
            onUtterance.invoke(message)
        } else {
            copilot.chat(message)
        }
    }
}
