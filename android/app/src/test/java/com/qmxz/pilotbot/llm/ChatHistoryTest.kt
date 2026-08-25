package com.qmxz.pilotbot.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {

    @Test
    fun testChatHistorySlidingWindow() {
        val history = ChatHistory(maxSize = 3)
        history.append(ChatMessage(Role.USER, "消息1"))
        history.append(ChatMessage(Role.ASSISTANT, "回复1"))
        history.append(ChatMessage(Role.USER, "消息2"))

        assertEquals(3, history.messages().size)
        assertEquals("消息1", history.messages()[0].content)

        // Append 4th message -> 1st should be evicted
        history.append(ChatMessage(Role.ASSISTANT, "回复2"))
        assertEquals(3, history.messages().size)
        assertEquals("回复1", history.messages()[0].content)
        assertEquals("消息2", history.messages()[1].content)
        assertEquals("回复2", history.messages()[2].content)
    }

    @Test
    fun testChatHistoryClear() {
        val history = ChatHistory(maxSize = 5)
        history.append(ChatMessage(Role.USER, "你好"))
        assertEquals(1, history.messages().size)
        history.clear()
        assertTrue(history.messages().isEmpty())
    }
}
