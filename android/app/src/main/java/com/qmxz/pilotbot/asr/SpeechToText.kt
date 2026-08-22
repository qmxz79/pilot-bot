package com.qmxz.pilotbot.asr

/** Speech-to-text facade so the voice modes can be tested against different backends. */
interface SpeechToText {
    /**
     * One-shot recognition (push-to-talk). Suspends until a result or error;
     * throws on recognition failure so the caller can fall back gracefully.
     */
    suspend fun listenOnce(): String

    /**
     * Continuous recognition for the hands-free modes. [onResult] fires per recognized utterance;
     * [onSpeechStart] fires as soon as the user begins speaking (used to interrupt TTS/LLM).
     * Keeps listening until [cancel] is called.
     */
    fun startContinuous(onResult: (String) -> Unit, onSpeechStart: () -> Unit)

    fun cancel()

    fun shutdown()
}
