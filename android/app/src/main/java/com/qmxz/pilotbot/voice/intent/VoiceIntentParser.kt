package com.qmxz.pilotbot.voice.intent

/**
 * Parsed voice interaction intent for the automotive copilot.
 */
sealed class VoiceIntent {
    data class NavigateTo(val destination: String) : VoiceIntent()
    data class SearchNearby(val keyword: String) : VoiceIntent()
    data object GoHome : VoiceIntent()
    data object GoCompany : VoiceIntent()
    data class RememberFact(val fact: String) : VoiceIntent()
    data class Chat(val text: String) : VoiceIntent()
}

/**
 * Intelligent rule- and keyword-based voice intent parser for in-vehicle scenarios.
 */
object VoiceIntentParser {

    private val REMEMBER_PATTERNS = listOf(
        Regex("^(?:请|帮我)?(?:记住|记一下|记录|记下|牢记|记着)[，,：:\\s]*(.+)$"),
    )

    private val GO_HOME_PATTERNS = listOf(
        Regex("^(?:请|帮我)?(?:我要|我想|带我|送我|准备|开始)?(?:导航|开车|走)?(?:去|回|到)?(?:家|我的家|家里|老家|我家)$"),
    )

    private val GO_COMPANY_PATTERNS = listOf(
        Regex("^(?:请|帮我)?(?:我要|我想|带我|送我|准备|开始)?(?:导航|开车|走)?(?:去|回|到)?(?:公司|单位|上班|办公室|我的公司)$"),
    )

    private val NEARBY_INDICATORS = listOf("附近", "周边", "周围", "就近")

    private val NEARBY_PREFIX_REGEX = Regex(
        "^(?:请|帮我|麻烦)?(?:搜一下|搜索|查找|查询|查一下|查查|查|找一下|找找|找个|找|看看|看下|带我找)?(?:一下)?(?:附近的|附近|周边的|周边|周围的|周围|就近的|就近)?(?:有什么|有哪些|有没有|哪里有|哪有|找个|找)?(?:的)?"
    )

    private val NEARBY_SUFFIX_REGEX = Regex(
        "(?:在什么地方|在哪里|在哪|推荐|吗|呢|啊|呀|吧|哦|个|的地点|的位置)+$"
    )

    private val NAVIGATE_PATTERNS = listOf(
        Regex("^(?:请|帮我)?(?:我要去|我想去|送我去|带我去)\\s*(.+)$"),
        Regex("^(?:请|帮我)?(?:导航|开车|带我|送我|前往|开往)?(?:去|到|前往|开往|导航到|导航去)\\s*(.+)$"),
        Regex("^(?:请|帮我)?(?:导航|路线规划到|规划路线到|规划去)\\s*(.+)$"),
        Regex("^(?:查一下去|怎么去|如何去)\\s*(.+)$"),
    )

    private val NAVIGATE_SUFFIX_REGEX = Regex(
        "(?:怎么走|怎么去|如何走|如何去|的路线|的导航|路线|导航|吧|啊|呀|哈)+$"
    )

    /**
     * Parses a spoken utterance into a structured [VoiceIntent].
     */
    fun parse(utterance: String): VoiceIntent {
        val raw = utterance.trim()
        if (raw.isEmpty()) {
            return VoiceIntent.Chat("")
        }

        // Clean punctuation from start/end
        val clean = raw.replace(Regex("^[\\s,，。？！?!:：~]+|[\\s,，。？！?!:：~]+$"), "")
        if (clean.isEmpty()) {
            return VoiceIntent.Chat(raw)
        }

        // 1. RememberFact
        for (pattern in REMEMBER_PATTERNS) {
            val match = pattern.find(clean)
            if (match != null) {
                val fact = match.groupValues[1].trim().replace(Regex("^[，,：:\\s]+|[，,：:\\s]+$"), "")
                if (fact.isNotEmpty()) {
                    return VoiceIntent.RememberFact(fact)
                }
            }
        }

        // 2. GoHome
        for (pattern in GO_HOME_PATTERNS) {
            if (pattern.matches(clean)) {
                return VoiceIntent.GoHome
            }
        }

        // 3. GoCompany
        for (pattern in GO_COMPANY_PATTERNS) {
            if (pattern.matches(clean)) {
                return VoiceIntent.GoCompany
            }
        }

        // 4. SearchNearby
        if (NEARBY_INDICATORS.any { clean.contains(it) }) {
            var keyword = clean
            // Strip prefix
            keyword = NEARBY_PREFIX_REGEX.replace(keyword, "").trim()
            // Strip suffix
            keyword = NEARBY_SUFFIX_REGEX.replace(keyword, "").trim()
            // Clean up any remaining leading "的", "个", "有"
            keyword = keyword.replace(Regex("^(?:的|个|有)+"), "").trim()

            if (keyword.isNotEmpty() && !NEARBY_INDICATORS.contains(keyword)) {
                return VoiceIntent.SearchNearby(keyword)
            } else if (clean.contains("附近") || clean.contains("周边") || clean.contains("周围")) {
                return VoiceIntent.SearchNearby(if (keyword.isNotEmpty()) keyword else "附近")
            }
        }

        // 5. NavigateTo
        for (pattern in NAVIGATE_PATTERNS) {
            val match = pattern.find(clean)
            if (match != null) {
                var destination = match.groupValues[1].trim()
                destination = NAVIGATE_SUFFIX_REGEX.replace(destination, "").trim()
                if (destination.isNotEmpty()) {
                    // Check if destination is Home or Company
                    if (destination in listOf("家", "我的家", "家里", "我家", "老家")) {
                        return VoiceIntent.GoHome
                    }
                    if (destination in listOf("公司", "单位", "上班", "办公室", "我的公司")) {
                        return VoiceIntent.GoCompany
                    }
                    return VoiceIntent.NavigateTo(destination)
                }
            }
        }

        // 6. Default chat
        return VoiceIntent.Chat(raw)
    }
}
