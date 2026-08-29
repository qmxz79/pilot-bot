package com.qmxz.pilotbot.asr

import android.content.Context
import android.speech.SpeechRecognizer
import com.qmxz.pilotbot.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Universal Speech-to-Text implementation.
 * Automatically chooses the best recognition strategy:
 * 1. Cloud ASR (AudioRecord + Cloud Speech-to-Text) which works universally on all Android ROMs (Xiaomi/Huawei/Vivo/Oppo etc.)
 * 2. System SpeechRecognizer if available and cloud is not configured.
 */
class SmartSpeechToText(
    context: Context,
    private val config: AppConfig,
    private val audioRecorder: AudioRecorder = AudioRecorder(),
    private val cloudStt: CloudSpeechToText = CloudSpeechToText(config),
    private val systemStt: AndroidSpeechToText = AndroidSpeechToText(context),
) : SpeechToText {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var continuousJob: Job? = null

    val isSystemAsrAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    val isCloudAsrAvailable: Boolean
        get() = config.endpoint.apiKey.isNotBlank()

    /** Human-readable status for UI badge. */
    fun status(): String {
        return when {
            isCloudAsrAvailable -> "识别:云端就绪"
            isSystemAsrAvailable -> "识别:系统就绪"
            else -> "识别:未配置Key"
        }
    }

    override suspend fun listenOnce(): String {
        val apiKey = config.endpoint.apiKey.trim()
        if (apiKey.isNotBlank()) {
            try {
                // Universal Cloud ASR with AudioRecord
                val wavBytes = audioRecorder.recordWav(maxDurationMs = 12000L)
                return cloudStt.transcribe(wavBytes)
            } catch (e: Exception) {
                if (isSystemAsrAvailable) {
                    try {
                        return systemStt.listenOnce()
                    } catch (_: Exception) {
                        throw e
                    }
                }
                throw e
            }
        }

        if (isSystemAsrAvailable) {
            return systemStt.listenOnce()
        }

        throw IllegalStateException("请在「设置」中填入 API Key，副驾将自动开启高精度云端语音识别（支持普通话与方言）！")
    }

    fun stopListeningNow() {
        audioRecorder.stop()
        systemStt.cancel()
    }

    override fun startContinuous(onResult: (String) -> Unit, onSpeechStart: () -> Unit) {
        cancel()
        continuousJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val apiKey = config.endpoint.apiKey.trim()
                if (apiKey.isNotBlank()) {
                    try {
                        val wavBytes = audioRecorder.recordWav(
                            maxDurationMs = 12000L,
                            silenceTimeoutMs = 3000L,
                            onSpeechStart = onSpeechStart,
                        )
                        if (wavBytes.isNotEmpty()) {
                            val text = cloudStt.transcribe(wavBytes)
                            if (text.isNotBlank()) {
                                launch(Dispatchers.Main) { onResult(text) }
                            }
                        }
                    } catch (_: Exception) {
                        delay(300L)
                    }
                } else if (isSystemAsrAvailable) {
                    launch(Dispatchers.Main) {
                        systemStt.startContinuous(onResult, onSpeechStart)
                    }
                    break
                } else {
                    delay(1000L)
                }
            }
        }
    }

    override fun cancel() {
        continuousJob?.cancel()
        continuousJob = null
        audioRecorder.stop()
        systemStt.cancel()
    }

    override fun shutdown() {
        cancel()
        systemStt.shutdown()
        scope.cancel()
    }
}
