package com.qmxz.pilotbot.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun testSanitizeChineseParentheses() {
        val input = "（皱眉看导航）别盯着位置看，注意左侧有车。（语气加重）看路比看位置重要。"
        val expected = "别盯着位置看，注意左侧有车。看路比看位置重要。"
        assertEquals(expected, TextSanitizer.sanitizeForSpeech(input))
    }

    @Test
    fun testSanitizeEnglishParenthesesAndAsterisks() {
        val input = "(looks at navi) *sighs* Ahead 3km traffic jam."
        val expected = "Ahead 3km traffic jam."
        assertEquals(expected, TextSanitizer.sanitizeForSpeech(input))
    }

    @Test
    fun testSanitizeBrackets() {
        val input = "[沉稳地点点头] 嗯，前面有个急弯，慢点打方向。"
        val expected = "嗯，前面有个急弯，慢点打方向。"
        assertEquals(expected, TextSanitizer.sanitizeForSpeech(input))
    }
}
