package com.qmxz.pilotbot.llm

/** Sliding-window chat history; the system prompt is injected separately at build time. */
class ChatHistory(private val maxSize: Int = 8) {
    private val items = ArrayDeque<ChatMessage>()

    fun append(message: ChatMessage) {
        if (items.size >= maxSize) items.removeFirst()
        items.addLast(message)
    }

    fun messages(): List<ChatMessage> = items.toList()

    fun clear() = items.clear()
}
