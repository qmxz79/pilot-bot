package com.qmxz.pilotbot.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun testSplitSentencesWithVariousPunctuation() {
        val text = "前方左转。注意避让行人！\n前方2公里拥堵？请减速慢行；到达目的地。"
        val sentences = splitSentences(text)
        assertEquals(5, sentences.size)
        assertEquals("前方左转。", sentences[0])
        assertEquals("注意避让行人！", sentences[1])
        assertEquals("前方2公里拥堵？", sentences[2])
        assertEquals("请减速慢行；", sentences[3])
        assertEquals("到达目的地。", sentences[4])
    }

    @Test
    fun testSplitSentencesEmptyAndNoPunctuation() {
        assertTrue(splitSentences("").isEmpty())
        assertTrue(splitSentences("前方五百米").isEmpty())
    }

    @Test
    fun testExtractCompleteSentencesSingleChunk() {
        val text = "你好！"
        val result = extractCompleteSentences(text)
        assertEquals(1, result.sentences.size)
        assertEquals("你好！", result.sentences[0])
        assertEquals(3, result.consumedLength)
    }

    @Test
    fun testExtractCompleteSentencesPartialChunk() {
        val text = "前方五百米右转。然后直行"
        val result = extractCompleteSentences(text)
        assertEquals(1, result.sentences.size)
        assertEquals("前方五百米右转。", result.sentences[0])
        assertEquals(8, result.consumedLength)
    }

    @Test
    fun testStreamingSimulationCursorAccuracy() {
        // Simulate streaming deltas from LLM
        val deltas = listOf(
            "前方",
            "500米右转。\n",
            "注意避让",
            "行人！",
            "保持车距。",
        )

        val fullText = StringBuilder()
        var spokenUpTo = 0
        val spokenSentences = mutableListOf<String>()

        for (delta in deltas) {
            fullText.append(delta)
            val segment = fullText.substring(spokenUpTo)
            val extraction = extractCompleteSentences(segment)
            if (extraction.sentences.isNotEmpty()) {
                spokenUpTo += extraction.consumedLength
                spokenSentences.addAll(extraction.sentences)
            }
        }

        // Flush remaining if any
        val rest = fullText.substring(spokenUpTo).trim()
        if (rest.isNotEmpty()) {
            spokenSentences.add(rest)
        }

        assertEquals(3, spokenSentences.size)
        assertEquals("前方500米右转。", spokenSentences[0])
        assertEquals("注意避让行人！", spokenSentences[1])
        assertEquals("保持车距。", spokenSentences[2])
    }
}
