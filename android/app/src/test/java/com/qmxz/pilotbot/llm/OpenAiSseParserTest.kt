package com.qmxz.pilotbot.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiSseParserTest {

    @Test
    fun testParseStandardContentDelta() {
        val line = """data: {"choices":[{"delta":{"content":"你好，我是副驾"},"index":0}]}"""
        val delta = parseSseDelta(line)
        assertEquals("你好，我是副驾", delta?.content)
        assertNull(delta?.finishReason)
    }

    @Test
    fun testParseFinishReasonDelta() {
        val line = """data: {"choices":[{"delta":{},"finish_reason":"stop","index":0}]}"""
        val delta = parseSseDelta(line)
        assertNull(delta?.content)
        assertEquals("stop", delta?.finishReason)
    }

    @Test
    fun testParseDoneLine() {
        val line = "data: [DONE]"
        val delta = parseSseDelta(line)
        assertNull(delta)
    }

    @Test
    fun testParseCommentOrEmptyLine() {
        assertNull(parseSseDelta(": keep-alive"))
        assertNull(parseSseDelta(""))
        assertNull(parseSseDelta("data:   "))
    }

    @Test
    fun testParseMalformedJson() {
        assertNull(parseSseDelta("data: {invalid json}"))
    }

    @Test
    fun testParseNullContentDelta() {
        val line = """data: {"choices":[{"delta":{"role":"assistant","content":null},"index":0}]}"""
        val delta = parseSseDelta(line)
        assertNull("delta.content should be null when JSON content is null", delta?.content)
    }

    @Test
    fun testParseReasoningChunkDelta() {
        val line = """data: {"choices":[{"delta":{"reasoning_content":"思考中..."},"index":0}]}"""
        val delta = parseSseDelta(line)
        assertNull("delta.content should be null for reasoning only chunks", delta?.content)
    }
}
