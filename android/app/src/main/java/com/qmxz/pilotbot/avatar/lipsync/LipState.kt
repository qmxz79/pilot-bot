package com.qmxz.pilotbot.avatar.lipsync

/**
 * Five-tier mouth opening phoneme states for real-time lip-sync animation:
 * - CLOSED: Rest / Silence / Bilabial consonants (B, P, M)
 * - SLIGHT: Slight mouth opening / Soft consonants
 * - HALF_OPEN: Medium mouth opening / Vowels (E, I, U)
 * - WIDE_AO: Wide mouth opening / Open vowels (A, O)
 * - FLAT_EI: Wide smile / Spread vowels (EE, AI)
 */
enum class LipState(val openness: Float, val widthScale: Float) {
    CLOSED(0.0f, 1.0f),
    SLIGHT(0.25f, 1.05f),
    HALF_OPEN(0.55f, 1.1f),
    WIDE_AO(1.0f, 0.95f),
    FLAT_EI(0.65f, 1.25f);

    companion object {
        /**
         * Maps audio volume normalized energy (0.0f to 1.0f) and spectral hint to [LipState].
         */
        fun fromNormalizedEnergy(energy: Float): LipState {
            return when {
                energy < 0.08f -> CLOSED
                energy < 0.28f -> SLIGHT
                energy < 0.58f -> HALF_OPEN
                energy < 0.85f -> FLAT_EI
                else -> WIDE_AO
            }
        }
    }
}
