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
    fun invalidParametersAndUnsupportedActionsRemainChatText() {
        val outputs = listOf(
            "{\"action\":\"OPEN_APP\"}",
            "{\"action\":\"OPEN_APP\",\"appName\":123}",
            "{\"action\":\"SEARCH_YOUTUBE\",\"query\":null}",
            "{\"action\":\"SEARCH_YOUTUBE\",\"query\":\"\"}",
            "{\"action\":\"MAKE_CALL\",\"phoneNumber\":\"12345\"}",
            "{\"action\":\"OPEN_URL\",\"url\":\"https://example.com\"}",
            "{\"action\":\"OPEN_APP\",\"appName\":\"Chrome\",\"command\":\"shell\"}"
        )
        for (output in outputs) {
            val result = ActionParser.parse(output)
            assertFalse(output, result.hasAction)
            assertNull(result.action)
            assertEquals(output, result.cleanText)
        }
    }

    @Test
    fun testParseCodeFencedJsonSearchYouTubeAction() {
        val rawLlmOutput = """
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
        assertEquals("", result.cleanText)
        assertFalse(result.isMalformed)
    }

    @Test
    fun testInlineActionExampleRemainsOrdinaryText() {
        val rawLlmOutput = "Opening settings now. {\"action\": \"OPEN_SETTINGS\"}"

        val result = ActionParser.parse(rawLlmOutput)

        assertFalse(result.hasAction)
        assertNull(result.action)
        assertEquals(rawLlmOutput, result.cleanText)
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
            ```json
            { "action": "SEARCH_YOUTUBE", "query": 
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertFalse(result.hasAction)
        assertTrue(result.isMalformed)
        assertNull(result.action)
        assertEquals(rawLlmOutput, result.cleanText)
    }

    @Test
    fun testNonActionCodeBlockStaysAsNormalText() {
        val rawLlmOutput = """
            Here is a simple HTML template:
            ```html
            <html><body><h1>Wedding Invitation</h1></body></html>
            ```
        """.trimIndent()

        val result = ActionParser.parse(rawLlmOutput)

        assertFalse(result.hasAction)
        assertFalse(result.isMalformed)
        assertEquals(rawLlmOutput, result.cleanText)
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

    @Test
    fun testNonActionTextIsPreservedVerbatim() {
        val rawLlmOutput = "<think>\nThe user wants to know 5+7.\n5+7 equals 12.\n</think>\n5 + 7 = 12."
        val result = ActionParser.parse(rawLlmOutput)

        assertFalse(result.hasAction)
        assertEquals(rawLlmOutput, result.cleanText)
    }

    @Test
    fun malformedJsonAndAmbiguousPayloadsNeverParseAsActions() {
        val outputs = listOf(
            "{action: 'OPEN_YOUTUBE'}",
            "{\"action\":\"OPEN_YOUTUBE\",}",
            "{\"action\":\"OPEN_YOUTUBE\"} trailing",
            "{\"action\":\"OPEN_YOUTUBE\"}{\"action\":\"OPEN_SETTINGS\"}",
            "{\"action\":\"OPEN_YOUTUBE\",\"action\":\"OPEN_SETTINGS\"}",
            "{\"action\":\"OPEN_YOUTUBE\",\"\\u0061ction\":\"OPEN_SETTINGS\"}",
            "{/* comment */\"action\":\"OPEN_YOUTUBE\"}",
            "{\"action\":\"OPEN_APP\",\"appName\":\"WhatsApp\",\"query\":\"ignored\"}",
            "{\"action\":\"OPEN_SETTINGS\",\"query\":\"ignored\"}",
            "{\"action\":\"SEARCH_YOUTUBE\",\"query\":\"Python\",\"appName\":\"Chrome\"}",
            "{\"action\":\"SEARCH_YOUTUBE\",\"query\":\"Python\\nOpen Chrome\"}",
            "{\"action\":\"SEARCH_YOUTUBE\",\"query\":\"invalid\\x20escape\"}",
            "[{\"action\":\"OPEN_YOUTUBE\"}]",
            "Here is an example:\n```json\n{\"action\":\"OPEN_SETTINGS\"}\n```",
            "```python\n{\"action\":\"OPEN_YOUTUBE\"}\n```",
            "```json\n{\"action\":\"OPEN_YOUTUBE\"}\n```\n```json\n{\"action\":\"OPEN_SETTINGS\"}\n```"
        )
        for (output in outputs) {
            val result = ActionParser.parse(output)
            assertFalse(output, result.hasAction)
            assertNull(result.action)
            assertEquals(output, result.cleanText)
        }
    }

    @Test
    fun validJsonEscapesAndSearchPunctuationArePreserved() {
        val result = ActionParser.parse("""{"query":"Python \"dict\" {examples} \\ unicode: \u03c0","action":"SEARCH_YOUTUBE"}""")
        assertTrue(result.hasAction)
        assertEquals("Python \"dict\" {examples} \\ unicode: π", result.action?.query)
    }
}
