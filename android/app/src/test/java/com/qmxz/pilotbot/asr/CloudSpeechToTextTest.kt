package com.qmxz.pilotbot.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSpeechToTextTest {

    @Test
    fun testResolveTranscriptionUrl() {
        assertEquals(
            "https://api.siliconflow.cn/v1/audio/transcriptions",
            CloudSpeechToText.resolveTranscriptionUrl("https://api.siliconflow.cn/v1"),
        )
        assertEquals(
            "https://api.siliconflow.cn/v1/audio/transcriptions",
            CloudSpeechToText.resolveTranscriptionUrl("https://api.siliconflow.cn/v1/chat/completions"),
        )
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            CloudSpeechToText.resolveTranscriptionUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.groq.com/openai/v1/audio/transcriptions",
            CloudSpeechToText.resolveTranscriptionUrl("https://api.groq.com/openai/v1"),
        )
    }

    @Test
    fun testResolveAsrModel() {
        assertEquals(
            "FunAudioLLM/SenseVoiceSmall",
            CloudSpeechToText.resolveAsrModel("https://api.siliconflow.cn/v1"),
        )
        assertEquals(
            "whisper-large-v3",
            CloudSpeechToText.resolveAsrModel("https://api.groq.com/openai/v1"),
        )
    }
}
