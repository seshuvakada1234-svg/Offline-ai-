package com.myai.offline.actions

import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import java.net.URI

object ActionValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Strictly verifies that an action produced by the model conforms to safe, permitted schemas.
     * Prevents shell injection, unauthorized intents, invalid phone numbers, or dangerous URLs.
     */
    fun validate(action: AssistantAction): ValidationResult {
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

            AssistantActionType.OPEN_URL -> {
                val url = action.url
                if (url.isNullOrBlank()) {
                    return ValidationResult.Invalid("URL is required for OPEN_URL action.")
                }
                try {
                    val uri = URI(url)
                    val scheme = uri.scheme?.lowercase()
                    if (scheme != "http" && scheme != "https") {
                        return ValidationResult.Invalid("Only HTTP and HTTPS URLs are allowed. Got '$scheme'.")
                    }
                    ValidationResult.Valid
                } catch (e: Exception) {
                    ValidationResult.Invalid("Malformed URL format: ${e.message}")
                }
            }

            AssistantActionType.OPEN_APP -> {
                val app = action.appName
                if (app.isNullOrBlank()) {
                    ValidationResult.Invalid("App name or package is required for OPEN_APP action.")
                } else {
                    ValidationResult.Valid
                }
            }

            AssistantActionType.MAKE_CALL -> {
                val phone = action.phoneNumber
                if (phone.isNullOrBlank()) {
                    ValidationResult.Invalid("Phone number is required for MAKE_CALL action.")
                } else if (!phone.matches(Regex("^[+0-9\\-\\s()]{3,20}$"))) {
                    ValidationResult.Invalid("Invalid phone number format.")
                } else {
                    ValidationResult.Valid
                }
            }

            AssistantActionType.SEND_SMS -> {
                val phone = action.phoneNumber
                if (phone.isNullOrBlank()) {
                    ValidationResult.Invalid("Recipient phone number is required for SEND_SMS.")
                } else if (!phone.matches(Regex("^[+0-9\\-\\s()]{3,20}$"))) {
                    ValidationResult.Invalid("Invalid phone number format.")
                } else {
                    ValidationResult.Valid
                }
            }
        }
    }
}
