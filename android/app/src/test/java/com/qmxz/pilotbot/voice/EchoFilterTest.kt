package com.qmxz.pilotbot.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EchoFilterTest {

    private lateinit var echoFilter: EchoFilter

    @Before
    fun setUp() {
        echoFilter = EchoFilter(maxCapacity = 10, defaultEchoThreshold = 0.75)
    }

    @Test
    fun testExactMatchIsEcho() {
        echoFilter.recordSpeaking("前方五百米请向右转进入辅路")
        assertTrue(echoFilter.isEcho("前方五百米请向右转进入辅路"))
    }

    @Test
    fun testPunctuationAndWhitespaceIgnored() {
        echoFilter.recordSpeaking("好的，马上为您规划最优路线！")
        assertTrue(echoFilter.isEcho("好的马上为您规划最优路线"))
        assertTrue(echoFilter.isEcho("好的，马上为您规划最优路线。"))
    }

    @Test
    fun testSubstringContainedInSpokenIsEcho() {
        echoFilter.recordSpeaking("前方五百米请向右转进入辅路，注意避让行人和非机动车")
        // ASR captures only the beginning or middle
        assertTrue(echoFilter.isEcho("前方五百米请向右转"))
        assertTrue(echoFilter.isEcho("向右转进入辅路"))
        assertTrue(echoFilter.isEcho("注意避让行人和非机动车"))
    }

    @Test
    fun testSlightlyDistortedAsrIsEcho() {
        echoFilter.recordSpeaking("正在为您重新规划路线，避开拥堵路段")
        // 1-2 character homophone or minor transcription deviation (> 75% similarity)
        assertTrue(echoFilter.isEcho("正在为您重新规划路线避开拥堵路"))
        assertTrue(echoFilter.isEcho("正为您重新规划路线避开拥堵路段"))
    }

    @Test
    fun testDistinctUserUtteranceIsNotEcho() {
        echoFilter.recordSpeaking("前方五百米请向右转进入辅路")
        assertFalse(echoFilter.isEcho("帮我放一首周杰伦的晴天"))
        assertFalse(echoFilter.isEcho("把空调温度调到二十四度"))
        assertFalse(echoFilter.isEcho("附近有什么好吃的川菜馆"))
    }

    @Test
    fun testUserCommandWithShortPrefixOverlapIsNotEcho() {
        echoFilter.recordSpeaking("好的")
        // User starts with "好的" but speaks a much longer intent command
        assertFalse(echoFilter.isEcho("好的请帮我导航到北京大兴机场"))
        assertFalse(echoFilter.isEcho("好的把车窗打开一点"))
    }

    @Test
    fun testUserAskingQuestionQuotingTopicIsNotEcho() {
        echoFilter.recordSpeaking("前方右转")
        // User asks a full clarifying question quoting the keyword
        assertFalse(echoFilter.isEcho("前方右转是去哪个方向啊"))
    }

    @Test
    fun testEmptyAndBlankInputs() {
        echoFilter.recordSpeaking("正在为您规划路线")
        assertFalse(echoFilter.isEcho(""))
        assertFalse(echoFilter.isEcho("   "))
        assertFalse(echoFilter.isEcho("，。！？"))
    }

    @Test
    fun testCompoundSentenceSplitting() {
        echoFilter.recordSpeaking("没问题。已为您切换为经济路线。预计节约十分钟。")
        // Sub-clauses recorded separately
        assertTrue(echoFilter.isEcho("已为您切换为经济路线"))
        assertTrue(echoFilter.isEcho("预计节约十分钟"))
    }

    @Test
    fun testRingBufferCapacityAndEviction() {
        val filter = EchoFilter(maxCapacity = 3)
        filter.recordSpeaking("前方进入隧道请开启大灯")
        filter.recordSpeaking("右侧车道变窄请注意礼让")
        filter.recordSpeaking("保持安全车距请减速慢行")

        assertTrue(filter.isEcho("前方进入隧道请开启大灯"))
        assertTrue(filter.isEcho("右侧车道变窄请注意礼让"))
        assertTrue(filter.isEcho("保持安全车距请减速慢行"))

        // Add 4th -> 1st should be evicted
        filter.recordSpeaking("注意横风路段请握紧方向盘")
        assertFalse(filter.isEcho("前方进入隧道请开启大灯"))
        assertTrue(filter.isEcho("右侧车道变窄请注意礼让"))
        assertTrue(filter.isEcho("保持安全车距请减速慢行"))
        assertTrue(filter.isEcho("注意横风路段请握紧方向盘"))
    }

    @Test
    fun testClear() {
        echoFilter.recordSpeaking("前方进入隧道请开启大灯")
        assertTrue(echoFilter.isEcho("前方进入隧道请开启大灯"))

        echoFilter.clear()
        assertEquals(0, echoFilter.size)
        assertFalse(echoFilter.isEcho("前方进入隧道请开启大灯"))
    }

    @Test
    fun testCustomThreshold() {
        echoFilter.recordSpeaking("今天北京天气晴朗最高气温二十八度")
        // Overlap around ~60%
        val testText = "今天北京天气晴朗"
        assertTrue(echoFilter.isEcho(testText, threshold = 0.5))
        // Default threshold is 0.75; testText is a substring of candidate so calculateSimilarity yields 1.0 (isEcho = true)
        assertTrue(echoFilter.isEcho(testText, threshold = 0.75))

        // When testText contains candidate plus extra words:
        val longUserText = "今天北京天气晴朗最高气温二十八度另外明天会下雨吗"
        // candidate length (17) / input length (26) = ~0.654
        assertTrue(echoFilter.isEcho(longUserText, threshold = 0.60))
        assertFalse(echoFilter.isEcho(longUserText, threshold = 0.75))
    }
}
