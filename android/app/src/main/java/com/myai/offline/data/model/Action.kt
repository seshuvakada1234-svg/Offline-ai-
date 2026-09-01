package com.myai.offline.data.model

import java.util.UUID

enum class AssistantActionType(val rawValue: String) {
    OPEN_YOUTUBE("OPEN_YOUTUBE"),
    SEARCH_YOUTUBE("SEARCH_YOUTUBE"),
    OPEN_CHROME("OPEN_CHROME"),
    OPEN_SETTINGS("OPEN_SETTINGS"),
    OPEN_APP("OPEN_APP"),
    OPEN_URL("OPEN_URL"),
    MAKE_CALL("MAKE_CALL"),
    SEND_SMS("SEND_SMS");

    companion object {
        fun fromString(str: String?): AssistantActionType? {
            if (str == null) return null
            return values().firstOrNull { it.name.equals(str, ignoreCase = true) || it.rawValue.equals(str, ignoreCase = true) }
        }
    }
}

data class AssistantAction(
    val id: String = UUID.randomUUID().toString(),
    val type: AssistantActionType,
    val appName: String? = null,
    val url: String? = null,
    val query: String? = null,
    val phoneNumber: String? = null,
    val messageText: String? = null,
    val requiresConfirmation: Boolean = false,
    val confirmed: Boolean = false,
    val executed: Boolean = false,
    val resultMessage: String? = null
)

data class ActionParseResult(
    val hasAction: Boolean,
    val action: AssistantAction? = null,
    val cleanText: String,
    val isMalformed: Boolean = false,
    val rawActionBlock: String? = null
)
