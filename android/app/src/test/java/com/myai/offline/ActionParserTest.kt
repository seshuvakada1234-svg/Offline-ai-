package com.myai.offline

import com.myai.offline.actions.ActionParser
import com.myai.offline.data.model.AssistantActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {

    @Test
    fun testParseCodeFencedJsonSearchYouTubeAction() {
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
    fun testParseInlineJsonOpenSettingsAction() {
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
    fun testParseOpenChromeAction() {
        val rawLlmOutput = """
            ```json
            {
              "action": "OPEN_CHROME"
            }
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertTrue(result.hasAction)
        assertEquals(AssistantActionType.OPEN_CHROME, result.action?.type)
    }

    @Test
    fun testMalformedJsonHandling() {
        val rawLlmOutput = """
            Opening app...
            ```json
            { "action": "SEARCH_YOUTUBE", "query": 
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertFalse(result.hasAction)
        assertTrue(result.isMalformed)
        assertNull(result.action)
    }

    @Test
    fun testInvalidUnknownActionType() {
        val rawLlmOutput = """
            ```json
            {
              "action": "EXECUTE_ARBITRARY_SHELL",
              "command": "rm -rf /"
            }
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertFalse(result.hasAction)
        assertTrue(result.isMalformed)
        assertNull(result.action)
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
