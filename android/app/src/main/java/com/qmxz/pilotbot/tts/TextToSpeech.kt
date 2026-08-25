package com.qmxz.pilotbot.tts

/** Minimal streaming TTS contract for the copilot loop. */
interface TextToSpeech {
    /** Queues [text] for playback after the engine is ready. */
    suspend fun speak(text: String)

    /** Immediately stops current playback (navigation broadcast takes priority). */
    fun interrupt()

    /** Releases the underlying engine. */
    fun shutdown()

    /** Fires once the playback queue drains to empty (used to resume listening in half-duplex). */
    fun setOnIdle(callback: () -> Unit)

    /** True when the platform TTS engine initialized; false degrades gracefully to text-only. */
    val isAvailable: Boolean
}

data class SentenceExtractionResult(
    val sentences: List<String>,
    val consumedLength: Int,
)

/**
 * Extracts complete sentences from a streaming text buffer.
 * [consumedLength] indicates exactly how many characters from the start of [text] were processed,
 * allowing the streaming cursor to advance without character drift or duplication.
 */
fun extractCompleteSentences(text: String): SentenceExtractionResult {
    if (text.isEmpty()) return SentenceExtractionResult(emptyList(), 0)
    var lastDelimiterIndex = -1
    for (i in text.indices) {
        val ch = text[i]
        if (ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '\n' || ch == '；' || ch == ';') {
            lastDelimiterIndex = i
        }
    }
    if (lastDelimiterIndex < 0) {
        return SentenceExtractionResult(emptyList(), 0)
    }

    val consumedLength = lastDelimiterIndex + 1
    val chunk = text.substring(0, consumedLength)
    val sentences = splitSentences(chunk)
    return SentenceExtractionResult(sentences, consumedLength)
}

/**
 * Splits [text] into complete sentences (ending with 。！？!?\n；;); a trailing partial sentence is
 * left for the next delta or flushed by the caller.
 */
fun splitSentences(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<String>()
    val current = StringBuilder()
    for (ch in text) {
        current.append(ch)
        if (ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '\n' || ch == '；' || ch == ';') {
            val sentence = current.toString().trim()
            if (sentence.isNotEmpty()) out.add(sentence)
            current.clear()
        }
    }
    return out
}
