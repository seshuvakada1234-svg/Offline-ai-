package com.myai.offline

import com.myai.offline.actions.ActionValidator
import com.myai.offline.data.model.AssistantAction
import com.myai.offline.data.model.AssistantActionType
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionValidatorTest {

    @Test
    fun testValidYouTubeSearch() {
        val action = AssistantAction(
            type = AssistantActionType.SEARCH_YOUTUBE,
            query = "Telugu new songs"
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
    }

    @Test
    fun testValidHttpsUrl() {
        val action = AssistantAction(
            type = AssistantActionType.OPEN_URL,
            url = "https://developer.android.com"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Valid)
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
    fun testValidPhoneNumber() {
        val action = AssistantAction(
            type = AssistantActionType.MAKE_CALL,
            phoneNumber = "+1-555-0199"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Valid)
    }

    @Test
    fun testInvalidPhoneNumber() {
        val action = AssistantAction(
            type = AssistantActionType.MAKE_CALL,
            phoneNumber = "abc;rm -rf /"
        )
        val result = ActionValidator.validate(action)
        assertTrue(result is ActionValidator.ValidationResult.Invalid)
    }
}
