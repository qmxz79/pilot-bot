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
 * - Continuous / Wake word: hands-free continuous recognition loop (pauses while the copilot speaks, resumes
 * when the reply finishes, so TTS output cannot re-trigger recognition).
 */
class VoiceController(
    private val config: AppConfig,
    private val speechToText: SpeechToText,
    private val copilot: CopilotEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val onListeningState: (Boolean) -> Unit = {},
    private val onStatusUpdate: (String) -> Unit = {},
    private val onUserText: (String) -> Unit = {},
    private val onListenError: (String) -> Unit = {},
    private val onUtterance: ((String) -> Unit)? = null,
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
                if (text.isNotBlank()) {
                    onUserText(text)
                    if (onUtterance != null) {
                        onUtterance.invoke(text)
                    } else {
                        copilot.chat(text)
                    }
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
        onStatusUpdate("👂 连续对话已开启...")
        val wakeWord = if (config.conversationMode == ConversationMode.WAKE_WORD) config.wakeWord else null
        speechToText.startContinuous(
            onResult = { text -> handleResult(wakeWord, text) },
            onSpeechStart = { copilot.interrupt() },
        )
    }

    private fun pauseListening() {
        listening = false
        onListeningState(false)
        onStatusUpdate("")
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
        if (onUtterance != null) {
            onUtterance.invoke(message)
        } else {
            copilot.chat(message)
        }
    }
}
