package com.qmxz.pilotbot.asr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** System [SpeechRecognizer] wrapper (no dependency); recognizer errors are surfaced as exceptions. */
class AndroidSpeechToText(context: Context) : SpeechToText {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var continuousRecognizer: SpeechRecognizer? = null

    override suspend fun listenOnce(): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    recognizer.destroy()
                    if (!cont.isCancelled) cont.resume(text)
                }

                override fun onError(error: Int) {
                    recognizer.destroy()
                    if (!cont.isCancelled) cont.resumeWithException(RecognitionException(error))
                }

                override fun onBeginningOfSpeech() {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onRmsChanged(rmsdB: Float) {}
            })
            cont.invokeOnCancellation {
                mainHandler.post { recognizer.destroy() }
            }
            recognizer.startListening(intent())
        }
    }

    override fun startContinuous(onResult: (String) -> Unit, onSpeechStart: () -> Unit) {
        stopInternal()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        continuousRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onResult(text)
                restartListening(recognizer, onResult, onSpeechStart)
            }

            override fun onBeginningOfSpeech() {
                onSpeechStart()
            }

            override fun onError(error: Int) {
                // Retryable: silence/timeout/busy. Fatal ones (permission, recognizer gone) stop the loop.
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                    error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                ) {
                    return
                }
                restartListening(recognizer, onResult, onSpeechStart)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onRmsChanged(rmsdB: Float) {}
        })
        recognizer.startListening(intent())
    }

    /** Re-arms the same recognizer unless a newer session has replaced it. */
    private fun restartListening(
        recognizer: SpeechRecognizer,
        onResult: (String) -> Unit,
        onSpeechStart: () -> Unit,
    ) {
        if (continuousRecognizer !== recognizer) return
        recognizer.startListening(intent())
    }

    override fun cancel() {
        stopInternal()
    }

    override fun shutdown() {
        stopInternal()
    }

    private fun stopInternal() {
        val rec = continuousRecognizer
        continuousRecognizer = null
        if (rec != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                rec.cancel()
                rec.destroy()
            } else {
                mainHandler.post {
                    rec.cancel()
                    rec.destroy()
                }
            }
        }
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
}

class RecognitionException(errorCode: Int) :
    IllegalStateException("语音识别失败，错误码 $errorCode")
