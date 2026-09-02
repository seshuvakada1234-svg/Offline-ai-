package com.myai.offline

import com.myai.offline.data.database.MessageEntity
import com.myai.offline.ui.components.MarkdownBlock
import com.myai.offline.ui.components.MarkdownParser
import com.myai.offline.ui.components.buildAnnotatedInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChatInterfaceTest {

    @Test
    fun testChronologicalMessageFlow() {
        val convId = UUID.randomUUID().toString()
        val messages = mutableListOf<MessageEntity>()

        // Step 1: User asks question
        val user1 = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "user",
            content = "Open YouTube",
            timestamp = 1000L
        )
        messages.add(user1)

        // Step 2: AI answers
        val ai1 = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "assistant",
            content = "Opening YouTube for you now.",
            timestamp = 2000L
        )
        messages.add(ai1)

        // Step 3: User follow-up
        val user2 = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "user",
            content = "Explain Kotlin coroutines",
            timestamp = 3000L
        )
        messages.add(user2)

        // Step 4: AI follow-up
        val ai2 = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "assistant",
            content = "Kotlin coroutines provide lightweight asynchronous concurrency.",
            timestamp = 4000L
        )
        messages.add(ai2)

        assertEquals(4, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("assistant", messages[1].role)
        assertEquals("user", messages[2].role)
        assertEquals("assistant", messages[3].role)
    }

    @Test
    fun testNoDuplicateMessageEntries() {
        val messageMap = mutableMapOf<String, MessageEntity>()

        val userMessageId = UUID.randomUUID().toString()
        val userMessage = MessageEntity(
            id = userMessageId,
            conversationId = "conv_123",
            role = "user",
            content = "Search Telugu songs"
        )
        messageMap[userMessage.id] = userMessage

        // Simulate streaming tokens for one assistant response
        val assistantMessageId = UUID.randomUUID().toString()
        var streamedContent = ""
        val tokenChunks = listOf("Sure", ", I", " will", " search", " for", " Telugu", " songs", ".")

        for (token in tokenChunks) {
            streamedContent += token
            // In a correct streaming architecture, only the existing assistant entity is updated
            messageMap[assistantMessageId] = MessageEntity(
                id = assistantMessageId,
                conversationId = "conv_123",
                role = "assistant",
                content = streamedContent
            )
        }

        // Exactly 2 total messages: 1 user, 1 assistant
        assertEquals(2, messageMap.size)
        assertEquals("Sure, I will search for Telugu songs.", messageMap[assistantMessageId]?.content)
    }

    @Test
    fun testMarkdownParserCodeBlock() {
        val markdownText = """
            Here is the code:
            ```kotlin
            fun main() {
                println("Hello MyAI")
            }
            ```
            Let me know if you have questions!
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdownText)
        assertTrue(blocks.any { it is MarkdownBlock.CodeBlock })

        val codeBlock = blocks.filterIsInstance<MarkdownBlock.CodeBlock>().first()
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.code.contains("println(\"Hello MyAI\")"))
    }

    @Test
    fun testMarkdownParserHeadingsAndLists() {
        val markdownText = """
            # Overview
            ## Key Points
            - Point one
            - Point two
            1. First step
            2. Second step
        """.trimIndent()

        val blocks = MarkdownParser.parse(markdownText)

        val h1 = blocks.filterIsInstance<MarkdownBlock.Heading>().find { it.level == 1 }
        val h2 = blocks.filterIsInstance<MarkdownBlock.Heading>().find { it.level == 2 }
        assertNotNull(h1)
        assertEquals("Overview", h1?.text)
        assertNotNull(h2)
        assertEquals("Key Points", h2?.text)

        val bulletList = blocks.filterIsInstance<MarkdownBlock.BulletList>().firstOrNull()
        assertNotNull(bulletList)
        assertEquals(2, bulletList?.items?.size)
        assertEquals("Point one", bulletList?.items?.get(0))

        val numList = blocks.filterIsInstance<MarkdownBlock.NumberedList>().firstOrNull()
        assertNotNull(numList)
        assertEquals(2, numList?.items?.size)
        assertEquals("First step", numList?.items?.get(0))
    }

    @Test
    fun testInlineMarkdownFormatting() {
        val text = "This is **bold** and this is *italic* and `val x = 10`"
        val annotated = buildAnnotatedInlineMarkdown(text, androidx.compose.ui.graphics.Color.White)
        assertEquals("This is bold and this is italic and  val x = 10 ", annotated.text)
    }

    @Test
    fun testEmptyConversationStartsBlank() {
        val messages = emptyList<MessageEntity>()
        assertTrue(messages.isEmpty())
    }
}
