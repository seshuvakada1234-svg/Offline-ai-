package com.myai.offline.actions

import com.myai.offline.data.model.ActionParseResult
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import org.json.JSONObject
import java.util.regex.Pattern

object ActionParser {

    private val JSON_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE)
    private val INLINE_JSON_PATTERN = Pattern.compile("\\{\\s*\"action\"\\s*:\\s*\"[A-Z_]+\"[^}]*\\}", Pattern.CASE_INSENSITIVE)

    /**
     * Parses generated LLM response tokens/text to extract structured action payloads.
     */
    fun parse(rawText: String): ActionParseResult {
        var clean = rawText.trim()
        var rawJson: String? = null

        // 1. Try markdown code fence
        val fenceMatcher = JSON_BLOCK_PATTERN.matcher(clean)
        if (fenceMatcher.find()) {
            rawJson = fenceMatcher.group(1)?.trim()
            clean = fenceMatcher.replaceAll("").trim()
        } else {
            // 2. Try inline raw JSON block
            val inlineMatcher = INLINE_JSON_PATTERN.matcher(clean)
            if (inlineMatcher.find()) {
                rawJson = inlineMatcher.group(0)?.trim()
                clean = inlineMatcher.replaceAll("").trim()
            }
        }

        if (rawJson.isNullOrBlank()) {
            return ActionParseResult(
                hasAction = false,
                action = null,
                cleanText = clean,
                isMalformed = false,
                rawActionBlock = null
            )
        }

        return try {
            val json = JSONObject(rawJson)
            val actionStr = json.optString("action", null)
            val type = AssistantActionType.fromString(actionStr)

            if (type == null) {
                ActionParseResult(
                    hasAction = false,
                    action = null,
                    cleanText = clean,
                    isMalformed = true,
                    rawActionBlock = rawJson
                )
            } else {
                val action = AssistantAction(
                    type = type,
                    appName = json.optString("appName").takeIf { it.isNotBlank() },
                    url = json.optString("url").takeIf { it.isNotBlank() },
                    query = json.optString("query").takeIf { it.isNotBlank() },
                    phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() },
                    messageText = json.optString("messageText").takeIf { it.isNotBlank() },
                    requiresConfirmation = when (type) {
                        AssistantActionType.MAKE_CALL, AssistantActionType.SEND_SMS -> true
                        else -> false
                    }
                )

                ActionParseResult(
                    hasAction = true,
                    action = action,
                    cleanText = clean,
                    isMalformed = false,
                    rawActionBlock = rawJson
                )
            }
        } catch (e: Exception) {
            ActionParseResult(
                hasAction = false,
                action = null,
                cleanText = clean,
                isMalformed = true,
                rawActionBlock = rawJson
            )
        }
    }
}
