package com.qmxz.pilotbot.voice

import com.qmxz.pilotbot.asr.SpeechToText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.copilot.CopilotEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Drives the ASR backend according to the selected [ConversationMode] and keeps it in sync with
 * the copilot's speaking window (half-duplex: the mic is paused while the copilot talks, resumed
 * when the reply finishes, so TTS output cannot re-trigger recognition).
 */
class VoiceController(
    private val config: AppConfig,
    private val speechToText: SpeechToText,
    private val copilot: CopilotEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val onListeningState: (Boolean) -> Unit = {},
    private val onUserText: (String) -> Unit = {},
) {
    private var handsFreeEnabled = false
    private var listening = false

    val isListening: Boolean get() = listening

    /** Mic button: one-shot in push-to-talk, a hands-free on/off switch otherwise. */
    fun toggleMic() {
        when (config.conversationMode) {
            ConversationMode.PUSH_TO_TALK -> pushToTalk()
            ConversationMode.CONTINUOUS, ConversationMode.WAKE_WORD -> {
                handsFreeEnabled = !handsFreeEnabled
                if (handsFreeEnabled) resumeListening() else stopListening()
            }
            ConversationMode.FULL_DUPLEX -> { /* 后期支持：需流式 ASR + 回声消除 */ }
        }
    }

    /** Copilot began replying: pause the mic so its own voice is not picked up. */
    fun onCopilotSpeakingStart() {
        if (listening) pauseListening()
    }

    /** Copilot finished: resume listening if hands-free is still on. */
    fun onCopilotSpeakingEnd() {
        if (handsFreeEnabled && !listening) resumeListening()
    }

    fun stopListening() {
        handsFreeEnabled = false
        pauseListening()
    }

    fun shutdown() {
        stopListening()
        speechToText.shutdown()
        scope.cancel()
    }

    private fun pushToTalk() {
        copilot.interrupt()
        scope.launch {
            val text = runCatching { speechToText.listenOnce() }.getOrNull()
            if (!text.isNullOrBlank()) {
                onUserText(text)
                copilot.chat(text)
            }
        }
    }

    private fun resumeListening() {
        listening = true
        onListeningState(true)
        val wakeWord = if (config.conversationMode == ConversationMode.WAKE_WORD) config.wakeWord else null
        speechToText.startContinuous(
            onResult = { text -> handleResult(wakeWord, text) },
            onSpeechStart = { copilot.interrupt() },
        )
    }

    private fun pauseListening() {
        listening = false
        onListeningState(false)
        speechToText.cancel()
    }

    private fun handleResult(wakeWord: String?, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val message = if (wakeWord.isNullOrBlank()) {
            trimmed
        } else if (trimmed.startsWith(wakeWord)) {
            trimmed.removePrefix(wakeWord).trim()
        } else {
            return // not addressed to the copilot
        }
        onUserText(message)
        copilot.chat(message)
    }
}
