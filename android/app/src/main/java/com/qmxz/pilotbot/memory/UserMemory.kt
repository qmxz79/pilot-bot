package com.qmxz.pilotbot.memory

import org.json.JSONArray
import org.json.JSONObject

/**
 * Long-term user memory profile storing persistent facts, preferences, and addresses.
 */
data class UserMemory(
    val userName: String = "",
    val homeAddress: String = "",
    val companyAddress: String = "",
    val preferences: List<String> = emptyList(),
    val facts: List<String> = emptyList(),
) {
    /**
     * Serializes this memory object to a JSON string.
     */
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("userName", userName)
        obj.put("homeAddress", homeAddress)
        obj.put("companyAddress", companyAddress)

        val prefArray = JSONArray()
        preferences.forEach { prefArray.put(it) }
        obj.put("preferences", prefArray)

        val factsArray = JSONArray()
        facts.forEach { factsArray.put(it) }
        obj.put("facts", factsArray)

        return obj.toString()
    }

    companion object {
        /**
         * Deserializes a [UserMemory] from a JSON string.
         * Returns an empty [UserMemory] on parse failure or empty input.
         */
        fun fromJson(json: String): UserMemory {
            if (json.isBlank()) return UserMemory()
            return try {
                val obj = JSONObject(json)
                val userName = obj.optString("userName", "")
                val homeAddress = obj.optString("homeAddress", "")
                val companyAddress = obj.optString("companyAddress", "")

                val prefList = mutableListOf<String>()
                obj.optJSONArray("preferences")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        prefList.add(arr.optString(i))
                    }
                }

                val factList = mutableListOf<String>()
                obj.optJSONArray("facts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        factList.add(arr.optString(i))
                    }
                }

                UserMemory(
                    userName = userName,
                    homeAddress = homeAddress,
                    companyAddress = companyAddress,
                    preferences = prefList,
                    facts = factList,
                )
            } catch (_: Exception) {
                UserMemory()
            }
        }
    }
}
