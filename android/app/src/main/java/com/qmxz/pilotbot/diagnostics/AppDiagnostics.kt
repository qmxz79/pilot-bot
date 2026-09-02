package com.qmxz.pilotbot.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticEvent(val area: AppIssue.Area, val kind: AppIssue.Kind, val message: String)

/** Small bounded, local-only diagnostics log. It intentionally never persists credentials. */
class DiagnosticLog(private val maxEntries: Int = 40) {
    private val events = ArrayDeque<DiagnosticEvent>()
    fun record(issue: AppIssue) {
        if (events.size == maxEntries) events.removeFirst()
        events.addLast(DiagnosticEvent(issue.area, issue.kind, redact(issue.technicalMessage ?: issue.userMessage)))
    }
    fun entries(): List<DiagnosticEvent> = events.toList()
    fun clear() = events.clear()

    companion object {
        fun redact(value: String): String = value
            .replace(Regex("(?i)(bearer\\s+)[^\\s,]+"), "$1***")
            .replace(Regex("(?i)(api[_ -]?key[=:]\\s*)[^\\s,]+"), "$1***")
            .take(240)
    }
}

class AppDiagnostics(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val log = DiagnosticLog().also { destination ->
        runCatching {
            JSONArray(prefs.getString(KEY_EVENTS, "[]")).let { saved ->
                for (i in 0 until saved.length()) {
                    saved.optJSONObject(i)?.let { item ->
                        destination.record(AppIssue(
                            area = AppIssue.Area.valueOf(item.optString("area")),
                            kind = AppIssue.Kind.valueOf(item.optString("kind")),
                            userMessage = "",
                            technicalMessage = item.optString("message"),
                        ))
                    }
                }
            }
        }
    }

    fun record(issue: AppIssue) {
        if (!isEnabled()) return
        log.record(issue)
        val json = JSONArray()
        log.entries().forEach { event -> json.put(JSONObject().apply {
            put("area", event.area.name); put("kind", event.kind.name); put("message", event.message)
        }) }
        prefs.edit().putString(KEY_EVENTS, json.toString()).apply()
    }

    fun clear() { log.clear(); prefs.edit().remove(KEY_EVENTS).apply() }
    fun entries(): List<DiagnosticEvent> = log.entries()
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) clear()
    }

    private companion object {
        const val PREFS = "pilot_bot_diagnostics"; const val KEY_EVENTS = "events"; const val KEY_ENABLED = "enabled"
    }
}
