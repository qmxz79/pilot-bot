package com.qmxz.pilotbot.context

enum class EventType {
    TURN,
    CONGESTION,
    ARRIVE,
    GENERIC,
}

/** A mechanical navigation broadcast classified into a structured driving event. */
data class StructuredEvent(
    val type: EventType,
    val naviText: String,
)

interface ContextBuilder {
    /** Classifies a raw navigation broadcast. */
    fun buildEvent(naviText: String): StructuredEvent

    /** Builds the "what is happening right now" text block fed to the LLM. */
    fun buildContextBlock(event: StructuredEvent): String
}

/** Keyword-based classifier; good enough for M1's short broadcasts. */
class SimpleContextBuilder : ContextBuilder {
    override fun buildEvent(naviText: String): StructuredEvent =
        StructuredEvent(classify(naviText), naviText)

    override fun buildContextBlock(event: StructuredEvent): String = buildString {
        append("导航刚播报：「${event.naviText}」\n")
        when (event.type) {
            EventType.TURN -> append("前方有转向动作，提醒司机即可，别展开长篇。")
            EventType.CONGESTION -> append("前面堵车了，可以安抚一下司机，也可以找点话聊聊。")
            EventType.ARRIVE -> append("快到了，收个尾，开心一点。")
            EventType.GENERIC -> append("正常播报，用口语自然转述，别照读。")
        }
    }

    companion object {
        fun classify(text: String): EventType = when {
            text.contains("拥堵") || text.contains("缓行") ||
                text.contains("事故") || text.contains("管制") -> EventType.CONGESTION

            text.contains("到达") || text.contains("抵达") || text.contains("目的地") -> EventType.ARRIVE

            text.contains("转") || text.contains("行驶") || text.contains("靠左") ||
                text.contains("靠右") || text.contains("进入") || text.contains("调头") ||
                text.contains("掉头") -> EventType.TURN

            else -> EventType.GENERIC
        }
    }
}
