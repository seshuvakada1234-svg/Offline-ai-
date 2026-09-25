package com.myai.offline.actions

import com.myai.offline.data.model.ActionParseResult
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import org.json.JSONObject

object ActionParser {
    private val actionFence = Regex("\\A```(?:json)?[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\z", RegexOption.IGNORE_CASE)
    private val jsonString = """"(?:[^"\\\x00-\x1f]++|\\["\\/bfnrt]|\\u[0-9a-fA-F]{4})*""""
    private val member = Regex("[ \\t\\r\\n]*($jsonString)[ \\t\\r\\n]*:[ \\t\\r\\n]*($jsonString)[ \\t\\r\\n]*")

    /**
     * Accepts one standalone action object, optionally in a JSON fence. This only parses
     * structure; the response pipeline decides whether the user requested an action.
     */
    fun parse(rawText: String): ActionParseResult {
        val plain = ActionParseResult(hasAction = false, cleanText = rawText)
        val trimmed = rawText.trim()
        val rawJson = actionFence.matchEntire(trimmed)?.groupValues?.get(1)?.trim()
            ?: trimmed.takeIf { it.startsWith("{") } ?: return plain

        return try {
            // Android's JSONObject also accepts non-JSON syntax (single quotes, comments,
            // unquoted names and trailing commas). Check the flat string-only grammar first.
            require(rawJson.length <= 4096 && rawJson.startsWith("{") && rawJson.endsWith("}"))
            var offset = 1
            var memberCount = 0
            while (offset < rawJson.lastIndex) {
                val match = member.find(rawJson, offset)
                require(match != null && match.range.first == offset) { "Invalid JSON member" }
                memberCount++
                offset = match.range.last + 1
                if (offset == rawJson.lastIndex) break
                require(rawJson.getOrNull(offset) == ',') { "Expected a comma" }
                offset++
                require(offset < rawJson.lastIndex) { "Trailing comma" }
            }
            val json = JSONObject(rawJson)
            require(json.length() == memberCount) { "Duplicate JSON member" }
            if (!json.has("action")) return plain
            require(json.keys().asSequence().all { it in setOf("action", "appName", "query") }) { "Unexpected action field" }
            val type = requireNotNull(AssistantActionType.fromString(json.opt("action") as? String))
            val action = AssistantAction(
                type = type,
                appName = json.opt("appName") as? String,
                query = json.opt("query") as? String
            )
            require(ActionValidator.validate(action) is ActionValidator.ValidationResult.Valid) { "Invalid action parameters" }
            ActionParseResult(hasAction = true, action = action, cleanText = "", rawActionBlock = rawJson)
        } catch (e: Exception) {
            plain.copy(isMalformed = true, rawActionBlock = rawJson)
        }
    }
}
