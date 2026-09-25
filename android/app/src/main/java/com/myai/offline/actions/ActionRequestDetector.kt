package com.myai.offline.actions

import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import java.util.Locale

/** Recognizes complete, explicit device requests. Explanations and model prose are never inputs. */
object ActionRequestDetector {
    private val politePrefix = Regex("^(?:(?:please\\s+)|(?:(?:can|could|would|will)\\s+you\\s+(?:please\\s+)?))", RegexOption.IGNORE_CASE)
    private val youtubeSearch = Regex("^(?:search\\s+(?:on\\s+)?youtube\\s+for|open\\s+youtube\\s+and\\s+search(?:\\s+for)?)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val openApp = Regex("^(?:open|launch)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?(?:\\s+for\\s+me)?[.!?]?$", RegexOption.IGNORE_CASE)
    private val appName = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,79}$")
    private val nonAppWords = Regex("\\b(and|then|with|using|how|why|what|a|an|my|website|webpage|url|file|folder|code|project|terminal|command)\\b", RegexOption.IGNORE_CASE)
    private val commonApps = setOf(
        "whatsapp", "instagram", "facebook", "telegram", "snapchat", "spotify", "netflix",
        "discord", "slack", "gmail", "email", "maps", "google maps", "camera", "photos",
        "gallery", "clock", "calendar", "contacts", "phone", "messages", "calculator",
        "play store", "google play", "files", "drive", "google drive", "twitter", "tiktok"
    )

    fun detect(userText: String): AssistantAction? {
        val text = userText.trim().replace(politePrefix, "")
        if (text.contains('\n') || text.contains('\r') || text.contains('`')) return null

        youtubeSearch.matchEntire(text)?.let { match ->
            val query = match.groupValues[1].trim()
            val action = AssistantAction(type = AssistantActionType.SEARCH_YOUTUBE, query = query)
            return action.takeIf { ActionValidator.validate(it) is ActionValidator.ValidationResult.Valid }
        }

        val target = openApp.matchEntire(text)?.groupValues?.get(1)?.trim() ?: return null
        if (!appName.matches(target) || nonAppWords.containsMatchIn(target) || target.split(Regex("\\s+")).size > 4) return null

        return when (target.lowercase(Locale.ROOT)) {
            "youtube", "yt" -> AssistantAction(type = AssistantActionType.OPEN_YOUTUBE)
            "chrome", "google chrome", "browser" -> AssistantAction(type = AssistantActionType.OPEN_CHROME)
            "settings", "android settings", "phone settings", "system settings" -> AssistantAction(type = AssistantActionType.OPEN_SETTINGS)
            else -> {
                val explicitApp = Regex("\\s+app(?:\\s+for\\s+me)?[.!?]?$", RegexOption.IGNORE_CASE).containsMatchIn(text)
                val packageName = Regex("^(?:com|org|net)\\.[a-zA-Z_]\\w*(?:\\.[a-zA-Z_]\\w*)+$").matches(target)
                if (target.lowercase(Locale.ROOT) in commonApps || explicitApp || packageName) {
                    AssistantAction(type = AssistantActionType.OPEN_APP, appName = target)
                } else null
            }
        }
    }
}
