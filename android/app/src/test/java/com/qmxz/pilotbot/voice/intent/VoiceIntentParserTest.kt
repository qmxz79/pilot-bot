package com.qmxz.pilotbot.voice.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceIntentParserTest {

    @Test
    fun testNavigateTo() {
        val cases = mapOf(
            "导航到北京天安门" to "北京天安门",
            "带我去东方明珠" to "东方明珠",
            "我要去首都机场" to "首都机场",
            "开车去杭州东站怎么走" to "杭州东站",
            "导航去成都双流国际机场" to "成都双流国际机场",
            "规划去广州塔的路线" to "广州塔",
            "去上海虹桥火车站" to "上海虹桥火车站",
            "带我去天府广场怎么走？" to "天府广场",
        )

        for ((utterance, expectedDest) in cases) {
            val intent = VoiceIntentParser.parse(utterance)
            assertTrue("Expected NavigateTo for '$utterance', got $intent", intent is VoiceIntent.NavigateTo)
            assertEquals(expectedDest, (intent as VoiceIntent.NavigateTo).destination)
        }
    }

    @Test
    fun testSearchNearby() {
        val cases = mapOf(
            "附近有什么加油站" to "加油站",
            "搜索附近的充电桩" to "充电桩",
            "查一下周围的公厕在哪里" to "公厕",
            "周边哪里有停车场" to "停车场",
            "附近好吃的" to "好吃的",
            "周边充电桩推荐" to "充电桩",
            "帮我搜一下附近的麦当劳" to "麦当劳",
            "附近加油站" to "加油站",
            "查一下附近的洗车店！" to "洗车店",
        )

        for ((utterance, expectedKeyword) in cases) {
            val intent = VoiceIntentParser.parse(utterance)
            assertTrue("Expected SearchNearby for '$utterance', got $intent", intent is VoiceIntent.SearchNearby)
            assertEquals(expectedKeyword, (intent as VoiceIntent.SearchNearby).keyword)
        }
    }

    @Test
    fun testGoHome() {
        val utterances = listOf(
            "回家",
            "我要回家",
            "导航回家",
            "带我回家",
            "帮我导航回家",
            "开车回家",
            "我想回家。",
            "送我回家",
            "导航去我家",
        )

        for (utterance in utterances) {
            val intent = VoiceIntentParser.parse(utterance)
            assertEquals("Expected GoHome for '$utterance'", VoiceIntent.GoHome, intent)
        }
    }

    @Test
    fun testGoCompany() {
        val utterances = listOf(
            "去公司",
            "导航去公司",
            "带我去单位",
            "我要去上班",
            "导航到公司",
            "送我去公司",
            "回公司！",
            "去单位",
            "导航去上班",
        )

        for (utterance in utterances) {
            val intent = VoiceIntentParser.parse(utterance)
            assertEquals("Expected GoCompany for '$utterance'", VoiceIntent.GoCompany, intent)
        }
    }

    @Test
    fun testRememberFact() {
        val cases = mapOf(
            "记住我喜欢吃辣" to "我喜欢吃辣",
            "记住我叫老李" to "我叫老李",
            "帮我记一下：我的车牌号是京A88888" to "我的车牌号是京A88888",
            "请记下明天下午两点去洗车" to "明天下午两点去洗车",
            "牢记我不吃香菜" to "我不吃香菜",
        )

        for ((utterance, expectedFact) in cases) {
            val intent = VoiceIntentParser.parse(utterance)
            assertTrue("Expected RememberFact for '$utterance', got $intent", intent is VoiceIntent.RememberFact)
            assertEquals(expectedFact, (intent as VoiceIntent.RememberFact).fact)
        }
    }

    @Test
    fun testWhereAmI() {
        val utterances = listOf(
            "我现在在哪里",
            "我现在在哪",
            "这是哪里",
            "这是哪儿",
            "当前位置",
            "我现在在什么地方",
            "请问我们在哪儿呢",
        )
        for (u in utterances) {
            val intent = VoiceIntentParser.parse(u)
            assertEquals("Expected WhereAmI for '$u'", VoiceIntent.WhereAmI, intent)
        }
    }

    @Test
    fun testChatFallback() {
        val utterances = listOf(
            "今天天气怎么样",
            "讲个笑话",
            "你觉得特斯拉怎么样",
            "我想听周杰伦的晴天",
            "你好小助手",
            "",
        )

        for (utterance in utterances) {
            val intent = VoiceIntentParser.parse(utterance)
            assertTrue("Expected Chat for '$utterance', got $intent", intent is VoiceIntent.Chat)
        }
    }
}
