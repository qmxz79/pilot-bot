package com.qmxz.pilotbot.voice

import android.content.SharedPreferences
import com.qmxz.pilotbot.asr.SpeechToText
import com.qmxz.pilotbot.config.AppConfig
import com.qmxz.pilotbot.copilot.CopilotEngine
import com.qmxz.pilotbot.llm.ChatMessage
import com.qmxz.pilotbot.llm.ChatResult
import com.qmxz.pilotbot.llm.GenerationConfig
import com.qmxz.pilotbot.llm.LlmEndpoint
import com.qmxz.pilotbot.llm.LlmProvider
import com.qmxz.pilotbot.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FullDuplexStateTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var appConfig: AppConfig
    private lateinit var fakeStt: FakeSpeechToText
    private lateinit var fakeTts: FakeTextToSpeech
    private lateinit var fakeLlm: FakeLlmProvider
    private lateinit var copilot: CopilotEngine
    private lateinit var voiceController: VoiceController

    private val userTexts = mutableListOf<String>()
    private val statusUpdates = mutableListOf<String>()
    private val listenErrors = mutableListOf<String>()
    private var listeningState = false

    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        appConfig = AppConfig(fakePrefs)
        appConfig.endpoint = LlmEndpoint(baseUrl = "https://api.example.com", apiKey = "test-key", model = "test-model")
        appConfig.wakeWord = "小伴小伴"

        fakeStt = FakeSpeechToText()
        fakeTts = FakeTextToSpeech()
        fakeLlm = FakeLlmProvider()

        userTexts.clear()
        statusUpdates.clear()
        listenErrors.clear()
        listeningState = false

        copilot = CopilotEngine(
            config = appConfig,
            llm = fakeLlm,
            tts = fakeTts,
            scope = testScope,
            onCopilotText = {},
            onCopilotDone = {},
        )

        voiceController = VoiceController(
            config = appConfig,
            speechToText = fakeStt,
            copilot = copilot,
            scope = testScope,
            onListeningState = { listeningState = it },
            onStatusUpdate = { statusUpdates.add(it) },
            onUserText = { userTexts.add(it) },
            onListenError = { listenErrors.add(it) },
        )
    }

    @Test
    fun testFullDuplexKeepsMicListeningWhileCopilotSpeaks() {
        appConfig.conversationMode = ConversationMode.FULL_DUPLEX
        voiceController.toggleMic()

        assertTrue(voiceController.isListening)
        assertTrue(voiceController.isHandsFreeEnabled)

        // In FULL_DUPLEX mode, copilot speaking does NOT pause listening
        voiceController.onCopilotSpeakingStart("前方五百米请向右转进入辅路")
        assertTrue("Microphone must remain active during copilot speech in full-duplex", voiceController.isListening)

        voiceController.onCopilotSpeakingEnd()
        assertTrue(voiceController.isListening)
    }

    @Test
    fun testContinuousPausesMicWhileCopilotSpeaks() {
        appConfig.conversationMode = ConversationMode.CONTINUOUS
        voiceController.toggleMic()

        assertTrue(voiceController.isListening)

        // In half-duplex CONTINUOUS mode, copilot speaking pauses listening to avoid loopback
        voiceController.onCopilotSpeakingStart("正在为您重新规划路线")
        assertFalse("Microphone must pause during copilot speech in half-duplex", voiceController.isListening)

        // Resumes after copilot finishes speaking
        voiceController.onCopilotSpeakingEnd()
        assertTrue("Microphone must resume after copilot speech ends", voiceController.isListening)
    }

    @Test
    fun testBargeInOnSpeechStartInterruptsCopilot() {
        appConfig.conversationMode = ConversationMode.FULL_DUPLEX
        voiceController.toggleMic()

        val initialInterruptCount = fakeTts.interruptCount

        // User starts speaking (energy detected) -> barge-in triggered
        fakeStt.triggerSpeechStart()

        assertTrue("Barge-in must immediately interrupt TTS", fakeTts.interruptCount > initialInterruptCount)
    }

    @Test
    fun testEchoFilterSilentlyDropsCopilotSpeechInFullDuplex() {
        appConfig.conversationMode = ConversationMode.FULL_DUPLEX
        voiceController.toggleMic()

        // Copilot speaks a navigation instruction
        voiceController.onCopilotText("前方五百米请向右转进入辅路")

        // ASR picks up copilot's own voice playback (echo)
        fakeStt.triggerResult("五百米请向右转进入辅路")

        // Echo must be dropped silently without dispatching to user texts or copilot
        assertTrue("Echoed text must be dropped silently", userTexts.isEmpty())
    }

    @Test
    fun testValidUserSpeechDispatchedInFullDuplex() {
        appConfig.conversationMode = ConversationMode.FULL_DUPLEX
        voiceController.toggleMic()

        voiceController.onCopilotText("前方五百米请向右转进入辅路")

        // User speaks an actual command
        fakeStt.triggerResult("帮我把车内温度调到二十四度")

        assertEquals(1, userTexts.size)
        assertEquals("帮我把车内温度调到二十四度", userTexts[0])
    }

    @Test
    fun testTurnTakingWindowExemptsWakeWordInWakeWordMode() {
        appConfig.conversationMode = ConversationMode.WAKE_WORD
        voiceController.toggleMic()

        // 1. Outside turn-taking window, utterance without wake word is ignored
        fakeStt.triggerResult("今天天气怎么样")
        assertTrue("Utterance without wake word must be ignored initially", userTexts.isEmpty())

        // 2. Utterance with wake word prefix is processed
        fakeStt.triggerResult("小伴小伴 今天天气怎么样")
        assertEquals(1, userTexts.size)
        assertEquals("今天天气怎么样", userTexts[0])
        assertTrue("Turn-taking window should be active after successful turn", voiceController.isTurnTakingActive)

        // 3. Follow-up utterance within 10s active window is processed WITHOUT wake word
        fakeStt.triggerResult("那明天会下雨吗")
        assertEquals(2, userTexts.size)
        assertEquals("那明天会下雨吗", userTexts[1])
    }

    @Test
    fun testStopListeningAndShutdown() {
        appConfig.conversationMode = ConversationMode.FULL_DUPLEX
        voiceController.toggleMic()

        assertTrue(voiceController.isListening)
        assertTrue(voiceController.isHandsFreeEnabled)

        voiceController.stopListening()
        assertFalse(voiceController.isListening)
        assertFalse(voiceController.isHandsFreeEnabled)
        assertFalse(voiceController.isTurnTakingActive)

        voiceController.shutdown()
        assertTrue(fakeStt.isShutdown)
    }

    // --- Test Doubles / Fakes ---

    private class FakeSpeechToText : SpeechToText {
        var onResultCallback: ((String) -> Unit)? = null
        var onSpeechStartCallback: (() -> Unit)? = null
        var isContinuousRunning = false
        var isShutdown = false

        override suspend fun listenOnce(): String = ""

        override fun startContinuous(onResult: (String) -> Unit, onSpeechStart: () -> Unit) {
            onResultCallback = onResult
            onSpeechStartCallback = onSpeechStart
            isContinuousRunning = true
        }

        override fun cancel() {
            isContinuousRunning = false
        }

        override fun shutdown() {
            cancel()
            isShutdown = true
        }

        fun triggerSpeechStart() {
            onSpeechStartCallback?.invoke()
        }

        fun triggerResult(text: String) {
            onResultCallback?.invoke(text)
        }
    }

    private class FakeTextToSpeech : TextToSpeech {
        var interruptCount = 0
        var spokenTexts = mutableListOf<String>()
        var onIdleCallback: (() -> Unit)? = null

        override suspend fun speak(text: String) {
            spokenTexts.add(text)
        }

        override fun interrupt() {
            interruptCount++
        }

        override fun shutdown() {}

        override fun setOnIdle(callback: () -> Unit) {
            onIdleCallback = callback
        }

        override val isAvailable: Boolean = true
    }

    private class FakeLlmProvider : LlmProvider {
        override val supportsStreaming: Boolean = true

        override suspend fun streamChat(
            endpoint: LlmEndpoint,
            messages: List<ChatMessage>,
            config: GenerationConfig,
            onDelta: (String) -> Unit,
        ): ChatResult {
            onDelta("好的，马上处理。")
            return ChatResult("好的，马上处理。", finishReason = "stop")
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data

        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (data[key] as? MutableSet<String>) ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        private inner class FakeEditor : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                key?.let { temp[it] = values }
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                key?.let { temp[it] = value }
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let { removed.add(it) }
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clear = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) {
                    data.clear()
                }
                removed.forEach { data.remove(it) }
                temp.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
