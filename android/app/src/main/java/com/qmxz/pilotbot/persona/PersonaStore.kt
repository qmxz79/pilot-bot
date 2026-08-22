package com.qmxz.pilotbot.persona

/** Built-in persona presets plus the custom slot used by the settings screen. */
object PersonaStore {
    const val CUSTOM_ID = "custom"

    val BUILTINS: List<Persona> = listOf(
        Persona("cheerful", "小伴", "活泼、话多、爱开玩笑，看到什么都想聊两句", "出发啦！"),
        Persona("calm", "老哥", "沉稳、话少、靠谱，句句在点上", "稳着开。"),
        Persona("sarcastic", "损友", "毒舌、爱挤兑，开得起玩笑", "行，您厉害。"),
    )

    fun find(id: String): Persona? = BUILTINS.firstOrNull { it.id == id }
}
