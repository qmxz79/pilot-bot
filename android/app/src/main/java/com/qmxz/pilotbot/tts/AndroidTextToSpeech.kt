package com.qmxz.pilotbot.tts

import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * System TTS wrapper (no extra dependency). [speak] is fire-and-forget from the copilot's view:
 * QUEUE_ADD preserves utterance order, and each call only speaks complete sentences — trailing
 * partial sentences are left for the next delta or flushed by the caller.
 */
class AndroidTextToSpeech(context: Context) : TextToSpeech {
    private val ready = CompletableDeferred<Unit>()
    private val tts: AndroidTts
    private val pending = mutableSetOf<String>()
    private var idleCallback: (() -> Unit)? = null
    private val utteranceCounter = AtomicLong(0)

    init {
        tts = AndroidTts(context.applicationContext) { status ->
            if (status == AndroidTts.SUCCESS) {
                tts.language = Locale.CHINA
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { pending.remove(it) }
                        maybeIdle()
                    }

                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { pending.remove(it) }
                        maybeIdle()
                    }
                })
                ready.complete(Unit)
            } else {
                ready.completeExceptionally(IllegalStateException("TTS 初始化失败"))
            }
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        ready.await()
        val id = "copilot-${utteranceCounter.incrementAndGet()}"
        withContext(Dispatchers.Main) {
            pending.add(id)
            tts.speak(text, AndroidTts.QUEUE_ADD, null, id)
        }
    }

    override fun interrupt() {
        pending.clear()
        tts.stop()
        maybeIdle()
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    override fun setOnIdle(callback: () -> Unit) {
        idleCallback = callback
    }

    private fun maybeIdle() {
        if (pending.isEmpty()) idleCallback?.invoke()
    }
}
