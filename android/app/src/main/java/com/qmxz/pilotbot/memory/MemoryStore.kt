package com.qmxz.pilotbot.memory

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists and retrieves [UserMemory] using [SharedPreferences], and formats memory prompts
 * for LLM context injection.
 */
class MemoryStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    /** Retrieves the current stored memory or a default empty [UserMemory]. */
    fun getMemory(): UserMemory {
        val json = prefs.getString(KEY_USER_MEMORY, null) ?: return UserMemory()
        return UserMemory.fromJson(json)
    }

    /** Overwrites the entire user memory state. */
    fun saveMemory(memory: UserMemory) {
        prefs.edit().putString(KEY_USER_MEMORY, memory.toJson()).apply()
    }

    /** Updates the driver's display name / nickname. */
    fun setUserName(name: String) {
        val current = getMemory()
        saveMemory(current.copy(userName = name.trim()))
    }

    /** Updates the home address. */
    fun setHomeAddress(address: String) {
        val current = getMemory()
        saveMemory(current.copy(homeAddress = address.trim()))
    }

    /** Updates the company / work address. */
    fun setCompanyAddress(address: String) {
        val current = getMemory()
        saveMemory(current.copy(companyAddress = address.trim()))
    }

    /** Appends a new fact about the driver. Blank entries are ignored. */
    fun addFact(fact: String) {
        val trimmed = fact.trim()
        if (trimmed.isEmpty()) return
        val current = getMemory()
        val updatedFacts = current.facts + trimmed
        saveMemory(current.copy(facts = updatedFacts))
    }

    /** Removes a fact by index if within bounds. */
    fun removeFact(index: Int) {
        val current = getMemory()
        if (index in current.facts.indices) {
            val updatedFacts = current.facts.toMutableList().apply { removeAt(index) }
            saveMemory(current.copy(facts = updatedFacts))
        }
    }

    /** Appends a driving preference. Blank entries are ignored. */
    fun addPreference(preference: String) {
        val trimmed = preference.trim()
        if (trimmed.isEmpty()) return
        val current = getMemory()
        val updatedPreferences = current.preferences + trimmed
        saveMemory(current.copy(preferences = updatedPreferences))
    }

    /** Removes a preference by index if within bounds. */
    fun removePreference(index: Int) {
        val current = getMemory()
        if (index in current.preferences.indices) {
            val updated = current.preferences.toMutableList().apply { removeAt(index) }
            saveMemory(current.copy(preferences = updated))
        }
    }

    /**
     * Formats the stored user memory into a prompt section tailored for LLM understanding.
     * Returns an empty string if no memory attributes exist.
     */
    fun buildMemoryPrompt(): String {
        val memory = getMemory()
        val lines = mutableListOf<String>()

        if (memory.userName.isNotBlank()) {
            lines.add("- 车主称呼：${memory.userName}")
        }
        if (memory.homeAddress.isNotBlank()) {
            lines.add("- 家地址：${memory.homeAddress}")
        }
        if (memory.companyAddress.isNotBlank()) {
            lines.add("- 公司地址：${memory.companyAddress}")
        }
        if (memory.preferences.isNotEmpty()) {
            lines.add("- 驾驶偏好：${memory.preferences.joinToString("、")}")
        }
        if (memory.facts.isNotEmpty()) {
            lines.add("- 关于车主的点滴记忆：")
            memory.facts.forEach { fact ->
                lines.add("  * $fact")
            }
        }

        if (lines.isEmpty()) {
            return ""
        }

        return buildString {
            append("【老朋友的记忆】\n")
            lines.forEach { line ->
                append(line).append("\n")
            }
            append("（请在与车主对话时，像认识很久的老朋友一样，自然地融入这些记忆，不需要刻意显摆，但要体现出懂他/她的默契与关怀。）\n")
        }
    }

    companion object {
        const val PREFS_NAME = "pilot_bot_memory"
        const val KEY_USER_MEMORY = "user_memory"
    }
}
