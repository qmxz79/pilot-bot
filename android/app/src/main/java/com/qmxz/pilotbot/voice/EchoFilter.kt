package com.qmxz.pilotbot.voice

import java.util.ArrayDeque
import kotlin.math.max

/**
 * Filter to eliminate acoustic feedback / echo where the copilot's own voice playback
 * is picked up by the vehicle's microphone and transcribed by ASR.
 *
 * Maintains a circular sliding window of recent copilot utterances and evaluates
 * whether incoming ASR transcription is an echo using character overlap,
 * Longest Common Subsequence (LCS), and Levenshtein edit distance.
 */
class EchoFilter(
    private val maxCapacity: Int = 20,
    private val defaultEchoThreshold: Double = 0.75,
) {
    private val lock = Any()
    private val historyWindow = ArrayDeque<String>()

    /**
     * Records a spoken utterance from the copilot into the sliding window.
     * Also splits compound sentences so partial ASR matches against individual
     * spoken clauses are accurately detected.
     */
    fun recordSpeaking(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        synchronized(lock) {
            pushEntry(trimmed)

            // Split into sub-sentences / clauses for fine-grained phrase matching
            val clauses = trimmed.split(Regex("[。！？；，\n,.!?;\r]+"))
                .map { it.trim() }
                .filter { it.length >= 2 }

            for (clause in clauses) {
                if (clause != trimmed) {
                    pushEntry(clause)
                }
            }
        }
    }

    /**
     * Determines whether the given ASR recognized text is an echo of copilot's own speech.
     * Returns true if similarity / character overlap against any recent utterance exceeds [threshold] (default > 75%).
     */
    fun isEcho(text: String, threshold: Double = defaultEchoThreshold): Boolean {
        val normalizedInput = normalize(text)
        if (normalizedInput.isEmpty()) return false

        val candidates = synchronized(lock) {
            historyWindow.toList()
        }

        for (candidate in candidates) {
            val normalizedCandidate = normalize(candidate)
            if (normalizedCandidate.isEmpty()) continue

            val similarity = calculateSimilarity(normalizedInput, normalizedCandidate)
            if (similarity > threshold) {
                return true
            }
        }
        return false
    }

    /**
     * Clears all recorded utterances from the sliding window.
     */
    fun clear() {
        synchronized(lock) {
            historyWindow.clear()
        }
    }

    /** Current number of items in the history window. */
    val size: Int
        get() = synchronized(lock) { historyWindow.size }

    /**
     * Calculates the similarity ratio between normalized input [s1] and candidate [s2].
     * Returns a score between 0.0 (completely distinct) and 1.0 (exact match / full containment).
     */
    fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0

        // If candidate contains input completely (e.g. ASR captured a subset of copilot speech)
        if (s2.contains(s1)) {
            return 1.0
        }

        // If input contains candidate completely
        if (s1.contains(s2)) {
            val lengthRatio = s2.length.toDouble() / s1.length
            val lcs = longestCommonSubsequenceLength(s1, s2).toDouble() / s1.length
            return max(lengthRatio, lcs)
        }

        // Global Levenshtein distance similarity
        val levDist = levenshteinDistance(s1, s2)
        val maxLen = max(s1.length, s2.length)
        val levSim = 1.0 - (levDist.toDouble() / maxLen)

        // Longest Common Subsequence ratio relative to input length
        val lcsLen = longestCommonSubsequenceLength(s1, s2)
        val lcsRatio = lcsLen.toDouble() / s1.length

        // Character multiset overlap ratio relative to input length
        val charOverlap = characterOverlapRatio(s1, s2)

        return maxOf(levSim, lcsRatio, charOverlap)
    }

    private fun pushEntry(entry: String) {
        if (historyWindow.size >= maxCapacity) {
            historyWindow.removeFirst()
        }
        historyWindow.addLast(entry)
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFFEF]"), "")
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) {
                    prev
                } else {
                    minOf(prev + 1, dp[j] + 1, dp[j - 1] + 1)
                }
                prev = temp
            }
        }
        return dp[s2.length]
    }

    private fun longestCommonSubsequenceLength(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun characterOverlapRatio(s1: String, s2: String): Double {
        val counts = mutableMapOf<Char, Int>()
        for (c in s2) {
            counts[c] = (counts[c] ?: 0) + 1
        }
        var common = 0
        for (c in s1) {
            val count = counts[c] ?: 0
            if (count > 0) {
                common++
                counts[c] = count - 1
            }
        }
        return common.toDouble() / s1.length
    }
}
