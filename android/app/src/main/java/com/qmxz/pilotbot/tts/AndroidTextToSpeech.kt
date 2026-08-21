package com.qmxz.pilotbot.tts

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * System TTS wrapper (no extra dependency). [speak] is fire-and-forget from the copilot's view:
 * QUEUE_ADD preserves utterance order, and each call only speaks complete sentences — trailing
 * partial sentences are left for the next delta or flushed by the caller.
 */
class AndroidTextToSpeech(context: Context) : TextToSpeech {
    private val ready = CompletableDeferred<Unit>()
    private val tts: AndroidTts

    init {
        tts = AndroidTts(context.applicationContext) { status ->
            if (status == AndroidTts.SUCCESS) {
                tts.language = Locale.CHINA
                ready.complete(Unit)
            } else {
                ready.completeExceptionally(IllegalStateException("TTS 初始化失败"))
            }
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        ready.await()
        // Queue on the main thread; the engine serializes QUEUE_ADD itself.
        withContext(Dispatchers.Main) {
            tts.speak(text, AndroidTts.QUEUE_ADD, null, "copilot")
        }
    }

    override fun interrupt() {
        tts.stop()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
