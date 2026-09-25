package com.myai.offline

import com.myai.offline.actions.ActionValidator
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    @Test
    fun supportedActionsRejectUnexpectedOrInvalidParameters() {
        val actions = listOf(
            AssistantAction(type = AssistantActionType.OPEN_SETTINGS, appName = "Chrome"),
            AssistantAction(type = AssistantActionType.OPEN_YOUTUBE, url = "https://example.com"),
            AssistantAction(type = AssistantActionType.OPEN_APP, appName = "WhatsApp", query = "ignored"),
            AssistantAction(type = AssistantActionType.OPEN_APP, appName = " "),
            AssistantAction(type = AssistantActionType.OPEN_APP, appName = "a".repeat(81)),
            AssistantAction(type = AssistantActionType.SEARCH_YOUTUBE, query = "a".repeat(301)),
            AssistantAction(type = AssistantActionType.SEARCH_YOUTUBE, query = "Python\nOpen Chrome"),
            AssistantAction(type = AssistantActionType.SEARCH_YOUTUBE, query = "Python\u0085tutorials")
        )
        for (action in actions) {
            assertTrue(action.toString(), ActionValidator.validate(action) is ActionValidator.ValidationResult.Invalid)
        }
    }

    @Test
    fun testValidOpenYouTubeAction() {
        val action = AssistantAction(
            type = AssistantActionType.OPEN_YOUTUBE
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidYouTubeSearchTeluguSongs() {
        val action = AssistantAction(
            type = AssistantActionType.SEARCH_YOUTUBE,
            query = "Telugu songs"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Valid)
    }

    @Test
    fun testInvalidEmptyYouTubeSearch() {
        val action = AssistantAction(
            type = AssistantActionType.SEARCH_YOUTUBE,
            query = ""
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Invalid)
        assertEquals("YouTube search requires a non-empty query parameter.", (result as ActionValidator.ValidationResult.Invalid).reason)
    }

    @Test
    fun testValidOpenChromeAction() {
        val action = AssistantAction(
            type = AssistantActionType.OPEN_CHROME
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Valid)
    }

    @Test
    fun testValidOpenSettingsAction() {
        val action = AssistantAction(
            type = AssistantActionType.OPEN_SETTINGS
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Valid)
    }

    @Test
    fun testUrlActionsAreOutsideSupportedDeviceActions() {
        val action = AssistantAction(
            type = AssistantActionType.OPEN_URL,
            url = "https://developer.android.com"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Invalid)
    }

    @Test
    fun testDangerousUrlSchemeRejected() {
        val action = AssistantAction(
            type = AssistantActionType.OPEN_URL,
            url = "javascript:alert(1)"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Invalid)
    }

    @Test
    fun testCallsAreOutsideSupportedDeviceActions() {
        val action = AssistantAction(
            type = AssistantActionType.MAKE_CALL,
            phoneNumber = "+1-555-0199"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Invalid)
    }

    @Test
    fun testInvalidPhoneNumberShellInjection() {
        val action = AssistantAction(
            type = AssistantActionType.MAKE_CALL,
            phoneNumber = "123;cat /etc/passwd"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Invalid)
    }
}
