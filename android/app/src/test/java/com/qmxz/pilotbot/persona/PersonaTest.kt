package com.qmxz.pilotbot.persona

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {

    @Test
    fun testBuildSystemPromptWithCatchphrase() {
        val persona = Persona(
            id = "cheerful",
            name = "小伴",
            tone = "活泼开朗",
            catchphrase = "出发啦！",
        )
        val prompt = persona.buildSystemPrompt()
        assertTrue(prompt.contains("小伴"))
        assertTrue(prompt.contains("活泼开朗"))
        assertTrue(prompt.contains("出发啦！"))
    }

    @Test
    fun testBuildSystemPromptWithoutCatchphrase() {
        val persona = Persona(
            id = "custom",
            name = "老张",
            tone = "沉稳",
            catchphrase = "",
        )
        val prompt = persona.buildSystemPrompt()
        assertTrue(prompt.contains("老张"))
        assertTrue(prompt.contains("沉稳"))
        assertTrue(!prompt.contains("口头禅"))
    }

    @Test
    fun testBuildSystemPromptWithMemoryPrompt() {
        val persona = Persona(
            id = "calm",
            name = "老哥",
            tone = "沉稳",
            catchphrase = "稳着开。",
        )
        val memoryPrompt = "【老朋友的记忆】\n- 车主称呼：老王"
        val prompt = persona.buildSystemPrompt(memoryPrompt)
        assertTrue(prompt.contains("老哥"))
        assertTrue(prompt.contains("【老朋友的记忆】"))
        assertTrue(prompt.contains("车主称呼：老王"))
    }

    @Test
    fun testJsonSerializationRoundTrip() {
        val original = Persona(
            id = "custom",
            name = "探险家",
            tone = "热情有活力",
            catchphrase = "随时准备！",
        )
        val json = original.toJson()
        val parsed = Persona.fromJson(json)

        assertNotNull(parsed)
        assertEquals("探险家", parsed?.name)
        assertEquals("热情有活力", parsed?.tone)
        assertEquals("随时准备！", parsed?.catchphrase)
    }

    @Test
    fun testJsonParseMalformed() {
        val parsed = Persona.fromJson("not-a-json")
        assertNull(parsed)
    }
}
