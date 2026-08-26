package com.qmxz.pilotbot.voice

/** Selectable voice-interaction modes; device testing decides which survive. */
enum class ConversationMode {
    /** Tap the mic, speak one utterance. */
    PUSH_TO_TALK,

    /** Hands-free half-duplex: listen -> reply -> listen again, mic pauses while the copilot speaks. */
    CONTINUOUS,

    /** Hands-free, but only utterances starting with the configured wake word reach the copilot. */
    WAKE_WORD,

    /** Hands-free full duplex: continuous listening with hardware AEC/NS, software echo filtering, and millisecond barge-in. */
    FULL_DUPLEX,
}
