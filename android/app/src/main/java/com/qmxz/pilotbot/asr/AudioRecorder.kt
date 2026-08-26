package com.qmxz.pilotbot.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Universal Android microphone PCM recorder with hardware acoustic enhancements.
 * Records 16kHz 16-bit Mono audio using [AudioRecord] without third-party native libraries.
 *
 * Automatically attaches hardware-level [AcousticEchoCanceler] and [NoiseSuppressor]
 * when supported by the underlying device chipset.
 * Supports both WAV batch capture and streaming chunk-based audio reading.
 */
class AudioRecorder {
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    val recording: Boolean
        get() = isRecording.get()

    /**
     * Records audio from the microphone until silence is detected, [stop] is called, or timeout.
     * Invokes [onChunk] on each read PCM chunk, [onRmsDb] with calculated volume in dB,
     * and [onSpeechStart] when voice onset activity is detected.
     * Returns standard WAV encoded audio bytes.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordWav(
        onRmsDb: ((Float) -> Unit)? = null,
        maxDurationMs: Long = 15000L,
        onSpeechStart: (() -> Unit)? = null,
        onChunk: ((ByteArray) -> Unit)? = null,
    ): ByteArray = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        val record = createAudioRecord(sampleRate, channelConfig, audioFormat, bufferSize)
        audioRecord = record

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("麦克风初始化失败，请在手机系统设置中授予「录音权限」")
        }

        // Enable hardware Acoustic Echo Canceler and Noise Suppressor if supported
        attachAudioEffects(record.audioSessionId)

        val pcmOut = ByteArrayOutputStream()
        val buffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        isRecording.set(true)
        try {
            record.startRecording()
        } catch (e: Exception) {
            releaseAudioEffects()
            throw IllegalStateException("启动麦克风失败（${e.message}），请检查是否有其他应用正在占用麦克风")
        }

        var totalRead = 0L
        val startTime = System.currentTimeMillis()
        var speechStarted = false
        var silenceStartTime = 0L

        try {
            while (isRecording.get() && (System.currentTimeMillis() - startTime) < maxDurationMs) {
                val shortsRead = record.read(buffer, 0, buffer.size)
                if (shortsRead > 0) {
                    var sum = 0.0
                    for (i in 0 until shortsRead) {
                        val sample = buffer[i]
                        sum += sample * sample
                        // Little-endian PCM16 byte conversion
                        byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }
                    val bytesRead = shortsRead * 2
                    pcmOut.write(byteBuffer, 0, bytesRead)
                    totalRead += bytesRead

                    onChunk?.invoke(byteBuffer.copyOf(bytesRead))

                    val rms = sqrt(sum / shortsRead)
                    val db = if (rms > 1) (20 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    onRmsDb?.invoke(db)

                    // Voice Activity Detection (VAD) heuristic
                    if (db > -48f) {
                        if (!speechStarted) {
                            speechStarted = true
                            onSpeechStart?.invoke()
                        }
                        silenceStartTime = 0L
                    } else if (speechStarted) {
                        if (silenceStartTime == 0L) {
                            silenceStartTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartTime > 1600L && totalRead > 16000 * 2) {
                            // 1.6s of silence after speech detected -> auto stop
                            break
                        }
                    }
                } else if (shortsRead < 0) {
                    break
                } else {
                    delay(10)
                }
            }
        } finally {
            isRecording.set(false)
            releaseAudioEffects()
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
            audioRecord = null
        }

        val pcmBytes = pcmOut.toByteArray()
        if (pcmBytes.isEmpty()) {
            throw IllegalStateException("未录制到有效音频，请重试")
        }

        val wavOut = ByteArrayOutputStream(44 + pcmBytes.size)
        writeWavHeader(wavOut, pcmBytes.size, sampleRate, 1, 16)
        wavOut.write(pcmBytes)
        wavOut.toByteArray()
    }

    /**
     * Reads streaming PCM chunks continuously until [stop] is called or timeout.
     * Used for real-time full-duplex streaming ASR.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordStream(
        onChunk: (ByteArray, Int) -> Unit,
        onRmsDb: ((Float) -> Unit)? = null,
        onSpeechStart: (() -> Unit)? = null,
        maxDurationMs: Long = Long.MAX_VALUE,
    ) = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        val record = createAudioRecord(sampleRate, channelConfig, audioFormat, bufferSize)
        audioRecord = record

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("麦克风初始化失败，请在手机系统设置中授予「录音权限」")
        }

        attachAudioEffects(record.audioSessionId)

        val buffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        isRecording.set(true)
        try {
            record.startRecording()
        } catch (e: Exception) {
            releaseAudioEffects()
            throw IllegalStateException("启动麦克风失败（${e.message}），请检查是否有其他应用正在占用麦克风")
        }

        val startTime = System.currentTimeMillis()
        var speechStarted = false

        try {
            while (isRecording.get() && (System.currentTimeMillis() - startTime) < maxDurationMs) {
                val shortsRead = record.read(buffer, 0, buffer.size)
                if (shortsRead > 0) {
                    var sum = 0.0
                    for (i in 0 until shortsRead) {
                        val sample = buffer[i]
                        sum += sample * sample
                        byteBuffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }
                    val bytesRead = shortsRead * 2
                    onChunk(byteBuffer, bytesRead)

                    val rms = sqrt(sum / shortsRead)
                    val db = if (rms > 1) (20 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    onRmsDb?.invoke(db)

                    if (db > -48f) {
                        if (!speechStarted) {
                            speechStarted = true
                            onSpeechStart?.invoke()
                        }
                    } else if (db < -55f) {
                        speechStarted = false
                    }
                } else if (shortsRead < 0) {
                    break
                } else {
                    delay(10)
                }
            }
        } finally {
            isRecording.set(false)
            releaseAudioEffects()
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
            audioRecord = null
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        }
        runCatching {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        }
    }

    private fun releaseAudioEffects() {
        runCatching {
            echoCanceler?.enabled = false
            echoCanceler?.release()
        }
        echoCanceler = null
        runCatching {
            noiseSuppressor?.enabled = false
            noiseSuppressor?.release()
        }
        noiseSuppressor = null
    }

    fun stop() {
        isRecording.set(false)
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int,
    ): AudioRecord {
        // Try dedicated VOICE_RECOGNITION first (with hardware noise suppression)
        val rec1 = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (_: Exception) { null }

        if (rec1 != null && rec1.state == AudioRecord.STATE_INITIALIZED) {
            return rec1
        }
        rec1?.release()

        // Fallback to standard MIC source
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )
    }

    private fun writeWavHeader(
        out: OutputStream,
        pcmDataLength: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xFF).toByte()
        header[5] = ((totalDataLen shr 8) and 0xFF).toByte()
        header[6] = ((totalDataLen shr 16) and 0xFF).toByte()
        header[7] = ((totalDataLen shr 24) and 0xFF).toByte()
        // Format
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLength and 0xFF).toByte()
        header[41] = ((pcmDataLength shr 8) and 0xFF).toByte()
        header[42] = ((pcmDataLength shr 16) and 0xFF).toByte()
        header[43] = ((pcmDataLength shr 24) and 0xFF).toByte()

        out.write(header, 0, 44)
    }
}
