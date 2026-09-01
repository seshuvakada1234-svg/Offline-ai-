package com.myai.offline

import com.myai.offline.actions.ActionParser
import com.myai.offline.data.model.AssistantActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    @Test
    fun testParseCodeFencedJsonAction() {
        val rawLlmOutput = """
            Sure, I am searching for Telugu songs on YouTube.
            ```json
            {
              "action": "SEARCH_YOUTUBE",
              "query": "Telugu songs"
            }
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertTrue(result.hasAction)
        assertNotNull(result.action)
        assertEquals(AssistantActionType.SEARCH_YOUTUBE, result.action?.type)
        assertEquals("Telugu songs", result.action?.query)
        assertEquals("Sure, I am searching for Telugu songs on YouTube.", result.cleanText)
        assertFalse(result.isMalformed)
    }

    @Test
    fun testParseInlineJsonAction() {
        val rawLlmOutput = "Opening settings now. {\"action\": \"OPEN_SETTINGS\"}"

        val result = ActionParser.parse(rawLlmOutput)

        assertTrue(result.hasAction)
        assertNotNull(result.action)
        assertEquals(AssistantActionType.OPEN_SETTINGS, result.action?.type)
        assertEquals("Opening settings now.", result.cleanText)
        assertFalse(result.isMalformed)
    }

    @Test
    fun testParseOpenYouTubeAction() {
        val rawLlmOutput = """
            ```json
            {
              "action": "OPEN_YOUTUBE"
            }
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertTrue(result.hasAction)
        assertEquals(AssistantActionType.OPEN_YOUTUBE, result.action?.type)
        assertFalse(result.action?.requiresConfirmation ?: true)
    }

    @Test
    fun testParseMakeCallRequiresConfirmation() {
        val rawLlmOutput = """
            ```json
            {
              "action": "MAKE_CALL",
              "phoneNumber": "+1234567890"
            }
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertTrue(result.hasAction)
        assertEquals(AssistantActionType.MAKE_CALL, result.action?.type)
        assertEquals("+1234567890", result.action?.phoneNumber)
        assertTrue(result.action?.requiresConfirmation == true)
    }

    @Test
    fun testNoActionPlainResponse() {
        val text = "An Operating System is system software that manages computer hardware and software."
        val result = ActionParser.parse(text)

        assertFalse(result.hasAction)
        assertEquals(text, result.cleanText)
        assertFalse(result.isMalformed)
    }
}
