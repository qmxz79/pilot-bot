package com.qmxz.pilotbot.diagnostics

/** Stable, user-safe failure description shared by network, voice and map surfaces. */
data class AppIssue(
    val area: Area,
    val kind: Kind,
    val userMessage: String,
    val technicalMessage: String? = null,
) {
    enum class Area { NAVIGATION, MAP, ASR, TTS, LLM }
    enum class Kind { AUTH, RATE_LIMIT, NETWORK, CONFIGURATION, SERVICE, UNKNOWN }
}

object AppIssueMapper {
    fun fromThrowable(area: AppIssue.Area, error: Throwable): AppIssue {
        val detail = error.message.orEmpty()
        val kind = when {
            detail.contains("401") || detail.contains("403") -> AppIssue.Kind.AUTH
            detail.contains("429") -> AppIssue.Kind.RATE_LIMIT
            detail.contains("404") || detail.contains("未配置") || detail.contains("API Key") -> AppIssue.Kind.CONFIGURATION
            detail.contains("Unable to resolve host", true) || detail.contains("timeout", true) -> AppIssue.Kind.NETWORK
            detail.contains("HTTP 5") || detail.contains("503") || detail.contains("502") || detail.contains("504") -> AppIssue.Kind.SERVICE
            else -> AppIssue.Kind.UNKNOWN
        }
        val message = when (kind) {
            AppIssue.Kind.AUTH -> "认证失败，请在设置中核对该服务的 API Key。"
            AppIssue.Kind.RATE_LIMIT -> "服务额度或请求频率受限，请稍后再试。"
            AppIssue.Kind.CONFIGURATION -> "服务尚未配置，请前往设置完成配置。"
            AppIssue.Kind.NETWORK -> "网络不可用或服务连接超时，请检查网络后重试。"
            AppIssue.Kind.SERVICE -> "服务暂时繁忙，已保留当前操作，可稍后重试。"
            AppIssue.Kind.UNKNOWN -> "服务暂时不可用，请稍后重试。"
        }
        return AppIssue(area, kind, message, detail.takeIf { it.isNotBlank() })
    }
}
