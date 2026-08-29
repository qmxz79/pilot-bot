package com.qmxz.pilotbot.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun testResolveCandidateModels() {
        val siliconCandidates = CloudSpeechToText.resolveCandidateModels("https://api.siliconflow.cn/v1", "")
        assertTrue(siliconCandidates.contains("FunAudioLLM/SenseVoiceSmall"))
        assertTrue(siliconCandidates.contains("openai/whisper-large-v3-turbo"))

        val groqCandidates = CloudSpeechToText.resolveCandidateModels("https://api.groq.com/openai/v1", "")
        assertTrue(groqCandidates.contains("whisper-large-v3-turbo"))

        val customCandidates = CloudSpeechToText.resolveCandidateModels("https://api.siliconflow.cn/v1", "my-custom-model")
        assertEquals("my-custom-model", customCandidates.first())
    }
}
