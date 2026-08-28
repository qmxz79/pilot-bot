package com.qmxz.pilotbot.avatar.state

/**
 * Avatar character selection:
 * - FEMALE: "心怡" (Sweet, intellectual, friendly modern female copilot)
 * - MALE: "修然" (Handsome, sunny, capable modern male copilot)
 */
enum class AvatarGender(val characterName: String, val title: String, val description: String) {
    FEMALE("心怡", "甜美知性 · 闺蜜", "甜美灵动、知性优雅的高颜值都市小姐姐，眼神会说话"),
    MALE("修然", "阳光帅气 · 男神", "帅气干练、阳光沉稳的高颜值男神老铁，眼神有光充满安全感");

    companion object {
        fun fromName(name: String?): AvatarGender {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FEMALE
        }
    }
}
