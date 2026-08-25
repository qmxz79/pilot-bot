package com.qmxz.pilotbot.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Low-level PCM/WAV audio recorder using Android [AudioRecord].
 * Captures 16000 Hz, 16-bit Mono audio and converts it to standard WAV format.
 */
class AudioRecorder {
    private val isRecording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null

    val recording: Boolean get() = isRecording.get()

    /**
     * Records audio until [stop] is called or silence/timeout is detected.
     * @param onRmsDb Callback providing audio energy in dB (useful for mic level indicators).
     * @param maxDurationMs Maximum duration in milliseconds before auto-stop.
     * @return WAV formatted byte array.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordWav(
        onRmsDb: ((Float) -> Unit)? = null,
        maxDurationMs: Long = 15000L,
        onSpeechStart: (() -> Unit)? = null,
    ): ByteArray = withContext(Dispatchers.IO) {
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize,
        )
        audioRecord = record

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord 初始化失败，请检查麦克风权限或是否被占用")
        }

        val pcmOut = ByteArrayOutputStream()
        val buffer = ShortArray(bufferSize / 2)
        val byteBuffer = ByteArray(bufferSize)

        isRecording.set(true)
        record.startRecording()

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
                    pcmOut.write(byteBuffer, 0, shortsRead * 2)
                    totalRead += shortsRead * 2

                    val rms = sqrt(sum / shortsRead)
                    val db = if (rms > 1) (20 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    onRmsDb?.invoke(db)

                    // Voice Activity Detection (VAD) heuristic
                    if (db > -35f) {
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
                }
            }
        } finally {
            isRecording.set(false)
            runCatching {
                record.stop()
                record.release()
            }
            audioRecord = null
        }

        val pcmBytes = pcmOut.toByteArray()
        if (pcmBytes.isEmpty()) {
            throw IllegalStateException("未录制到有效音频")
        }

        val wavOut = ByteArrayOutputStream(44 + pcmBytes.size)
        writeWavHeader(wavOut, pcmBytes.size, sampleRate, 1, 16)
        wavOut.write(pcmBytes)
        wavOut.toByteArray()
    }

    fun stop() {
        isRecording.set(false)
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
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        // 'data' chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLength and 0xff).toByte()
        header[41] = ((pcmDataLength shr 8) and 0xff).toByte()
        header[42] = ((pcmDataLength shr 16) and 0xff).toByte()
        header[43] = ((pcmDataLength shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
