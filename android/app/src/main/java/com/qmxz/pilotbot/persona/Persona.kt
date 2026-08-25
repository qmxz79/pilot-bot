package com.qmxz.pilotbot.persona

import org.json.JSONObject

/** A copilot persona used to build the system prompt. */
data class Persona(
    val id: String,
    val name: String,
    val tone: String,
    val catchphrase: String,
) {
    fun buildSystemPrompt(memoryPrompt: String = ""): String = buildString {
        append("你是副驾驶座上的朋友「${name.ifBlank { "小伴" }}」，正陪司机开车。\n")
        append("说话方式：${tone.ifBlank { "轻松、活泼、像朋友" }}。\n")
        if (catchphrase.isNotBlank()) {
            append("口头禅：$catchphrase（可以自然用上，别每句都带）。\n")
        }
        append("铁律：\n")
        append("1. 把机械的导航播报改写成自然口语，像人说话，不要照读路名和数字堆砌。\n")
        append("2. 句子要短，适合开车时听。\n")
        append("3. 语气亲切，可以聊两句，但别啰嗦、别重复。\n")
        if (memoryPrompt.isNotBlank()) {
            append("\n").append(memoryPrompt.trim()).append("\n")
        }
    }

    /**
     * JSON export for sharing; intentionally excludes [id] so any endpoint can read it. Import
     * lands in the custom slot ([PersonaStore.CUSTOM_ID]) rather than extending multi-preset storage.
     */
    fun toJson(): String = JSONObject()
        .put("name", name)
        .put("tone", tone)
        .put("catchphrase", catchphrase)
        .toString()

    companion object {
        /** Parses an exported persona JSON; null on malformed input. */
        fun fromJson(json: String): Persona? {
            val obj = try {
                JSONObject(json)
            } catch (_: Exception) {
                return null
            }
            return Persona(
                id = "",
                name = obj.optString("name"),
                tone = obj.optString("tone"),
                catchphrase = obj.optString("catchphrase"),
            )
        }
    }
}
