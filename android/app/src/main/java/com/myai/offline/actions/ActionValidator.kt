package com.myai.offline.actions

import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType

object ActionValidator {

    val SUPPORTED_TYPES = setOf(
        AssistantActionType.OPEN_YOUTUBE,
        AssistantActionType.SEARCH_YOUTUBE,
        AssistantActionType.OPEN_APP,
        AssistantActionType.OPEN_CHROME,
        AssistantActionType.OPEN_SETTINGS
    )

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /** The same allowlist and parameter checks apply at parsing and execution boundaries. */
    fun validate(action: AssistantAction): ValidationResult {
        if (action.type !in SUPPORTED_TYPES) {
            return ValidationResult.Invalid("Unsupported device action: ${action.type.rawValue}")
        }
        if (action.url != null || action.phoneNumber != null || action.messageText != null ||
            (action.type != AssistantActionType.OPEN_APP && action.appName != null) ||
            (action.type != AssistantActionType.SEARCH_YOUTUBE && action.query != null)
        ) {
            return ValidationResult.Invalid("Unexpected parameters for ${action.type.rawValue}.")
        }
        if (listOfNotNull(action.appName, action.query).any { value -> value.any { it.isISOControl() } }) {
            return ValidationResult.Invalid("Action parameters contain control characters.")
        }
        return when (action.type) {
            AssistantActionType.OPEN_YOUTUBE -> {
                ValidationResult.Valid
            }

            AssistantActionType.SEARCH_YOUTUBE -> {
                if (action.query.isNullOrBlank()) {
                    ValidationResult.Invalid("YouTube search requires a non-empty query parameter.")
                } else if (action.query.length > 300) {
                    ValidationResult.Invalid("Search query exceeds maximum permitted character length.")
                } else {
                    ValidationResult.Valid
                }
            }

            AssistantActionType.OPEN_CHROME -> {
                ValidationResult.Valid
            }

            AssistantActionType.OPEN_SETTINGS -> {
                ValidationResult.Valid
            }

            AssistantActionType.OPEN_APP -> {
                val app = action.appName
                if (app.isNullOrBlank()) {
                    ValidationResult.Invalid("App name or package is required for OPEN_APP action.")
                } else if (app.length > 80 || !app.matches(Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]*$"))) {
                    ValidationResult.Invalid("Invalid application name.")
                } else {
                    ValidationResult.Valid
                }
            }
            else -> ValidationResult.Invalid("Unsupported device action: ${action.type.rawValue}")
        }
    }
}
