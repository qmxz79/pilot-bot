package com.qmxz.pilotbot.tts

/**
 * Strips bracketed stage directions, emotional cues, action notes, and markdown asterisks from AI responses.
 * Examples stripped: (皱眉看导航), （语气加重）, (沉稳地点点头), [笑], *叹了口气*
 */
object TextSanitizer {
    private val BRACKET_REGEX = Regex("[(（\\[【][^()（）\\[\\]【】]*[)）\\]】]")
    private val ASTERISK_ACTION_REGEX = Regex("\\*[^*]+\\*")
    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cn}]")

    /**
     * Cleans text before speaking so only conversational dialogue is read by the voice engine.
     */
    fun sanitizeForSpeech(raw: String): String {
        var clean = raw
        clean = BRACKET_REGEX.replace(clean, "")
        clean = ASTERISK_ACTION_REGEX.replace(clean, "")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    /**
     * Cleans text for display in the speech bubble to look clean without verbose script brackets.
     */
    fun sanitizeForDisplay(raw: String): String {
        var clean = raw
        clean = BRACKET_REGEX.replace(clean, "")
        clean = ASTERISK_ACTION_REGEX.replace(clean, "")
        return clean.trim()
    }
}
