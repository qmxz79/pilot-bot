package com.qmxz.pilotbot.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * System TTS wrapper (no extra dependency). [speak] is fire-and-forget from the copilot's view:
 * QUEUE_ADD preserves utterance order, and each call only speaks complete sentences — trailing
 * partial sentences are left for the next delta or flushed by the caller.
 *
 * If the platform TTS engine is missing/broken, [isAvailable] is false and [speak] becomes a
 * no-op instead of throwing — the copilot degrades to text-only rather than crashing.
 */
class AndroidTextToSpeech(context: Context) : TextToSpeech {
    private val ready = CompletableDeferred<Unit>()
    private val appContext = context.applicationContext
    private lateinit var tts: AndroidTts
    private val pending = mutableSetOf<String>()
    private var idleCallback: (() -> Unit)? = null
    private val utteranceCounter = AtomicLong(0)

    @Volatile
    private var available = false

    @Volatile
    private var statusText = "语音初始化中…"

    override val isAvailable: Boolean
        get() = available

    /** Human-readable TTS state for the UI: 可用 / 语言受限 / 初始化失败(状态码) / 初始化中. */
    fun status(): String = statusText

    init {
        initWithRetry(attemptsLeft = 3)
    }

    /**
     * Creates the engine, retrying a few times because init can fail transiently while the engine
     * service warms up. The init callback is deferred onto the main loop (Handler.post) so it
     * always runs after the instance is captured — this is what makes referencing [instance] inside
     * its own constructor safe.
     */
    private fun initWithRetry(attemptsLeft: Int) {
        // Holder indirection: the callback cannot reference `instance` (its own initializer), so
        // it captures the holder and reads the instance at run time.
        val holder = AtomicReference<AndroidTts>()
        val instance = AndroidTts(appContext) { status ->
            Handler(Looper.getMainLooper()).post {
                holder.get()?.let { handleInitResult(status, it, attemptsLeft) }
            }
        }
        holder.set(instance)
        tts = instance
    }

    private fun handleInitResult(status: Int, instance: AndroidTts, attemptsLeft: Int) {
        if (status == AndroidTts.SUCCESS) {
            configureLanguage(instance)
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
            available = true
            ready.complete(Unit)
        } else if (attemptsLeft > 1) {
            runCatching { instance.shutdown() }
            Handler(Looper.getMainLooper()).postDelayed({ initWithRetry(attemptsLeft - 1) }, 1000L)
        } else {
            statusText = "语音不可用(初始化失败, 状态 $status)"
            ready.complete(Unit)
        }
    }

    /** Prefers Chinese; falls back to the device default so engines without a zh voice still speak. */
    private fun configureLanguage(instance: AndroidTts) {
        val r = instance.setLanguage(Locale.CHINA)
        statusText = if (r == AndroidTts.LANG_MISSING_DATA || r == AndroidTts.LANG_NOT_SUPPORTED) {
            instance.setLanguage(Locale.getDefault())
            "语音可用(中文声包缺失, 已用系统默认语言)"
        } else {
            "语音可用"
        }
    }

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        ready.await()
        if (!available) return
        val id = "copilot-${utteranceCounter.incrementAndGet()}"
        withContext(Dispatchers.Main) {
            pending.add(id)
            tts.speak(text, AndroidTts.QUEUE_ADD, null, id)
        }
    }

    override fun interrupt() {
        if (!available) return
        pending.clear()
        tts.stop()
        maybeIdle()
    }

    override fun shutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    override fun setOnIdle(callback: () -> Unit) {
        idleCallback = callback
    }

    private fun maybeIdle() {
        if (pending.isEmpty()) idleCallback?.invoke()
    }
}
