package com.qmxz.pilotbot.tts

/** Minimal streaming TTS contract for the copilot loop. */
interface TextToSpeech {
    /** Queues [text] for playback after the engine is ready. */
    suspend fun speak(text: String)

    /** Immediately stops current playback (navigation broadcast takes priority). */
    fun interrupt()

    /** Releases the underlying engine. */
    fun shutdown()
}

/**
 * Splits [text] into complete sentences (ending with 。！？!?\n); a trailing partial sentence is
 * left for the next delta or flushed by the caller.
 */
fun splitSentences(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    val current = StringBuilder()
    for (ch in text) {
        current.append(ch)
        if (ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '\n') {
            val sentence = current.toString().trim()
            if (sentence.isNotEmpty()) out.add(sentence)
            current.clear()
        }
    }
    return out
}
