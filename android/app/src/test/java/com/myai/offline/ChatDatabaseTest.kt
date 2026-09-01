package com.myai.offline

import com.myai.offline.data.database.ConversationEntity
import com.myai.offline.data.database.MessageEntity
import com.myai.offline.data.model.InferenceMetrics
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ChatDatabaseTest {

    @Test
    fun testConversationEntityCreation() {
        val convId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val conv = ConversationEntity(
            id = convId,
            title = "Telugu Songs Query",
            createdAt = timestamp,
            updatedAt = timestamp,
            selectedModelId = "qwen3-1.7b"
        )

        assertEquals(convId, conv.id)
        assertEquals("Telugu Songs Query", conv.title)
        assertEquals("qwen3-1.7b", conv.selectedModelId)
        assertEquals(timestamp, conv.createdAt)
    }

    @Test
    fun testMessageEntityRolesAndContent() {
        val convId = UUID.randomUUID().toString()
        val userMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "user",
            content = "Open YouTube and search Telugu songs",
            isVoiceInput = true
        )

        val assistantMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = convId,
            role = "assistant",
            content = "Searching YouTube for Telugu songs.",
            metricsJson = "{\"tokensPerSec\":28.4,\"timeToFirstTokenMs\":110}"
        )

        assertEquals("user", userMsg.role)
        assertTrue(userMsg.isVoiceInput)
        assertEquals("assistant", assistantMsg.role)
        assertNotNull(assistantMsg.metricsJson)
    }

    @Test
    fun testMetricsJsonSerialization() {
        val metrics = InferenceMetrics(
            modelLoadTimeMs = 350L,
            timeToFirstTokenMs = 95L,
            tokensPerSec = 31.2,
            totalTokens = 120,
            totalGenTimeMs = 3840L,
            timestamp = 1700000000000L
        )

        val json = JSONObject().apply {
            put("timeToFirstTokenMs", metrics.timeToFirstTokenMs)
            put("tokensPerSec", metrics.tokensPerSec)
            put("totalTokens", metrics.totalTokens)
            put("totalGenTimeMs", metrics.totalGenTimeMs)
        }.toString()

        val parsed = JSONObject(json)
        assertEquals(95L, parsed.getLong("timeToFirstTokenMs"))
        assertEquals(31.2, parsed.getDouble("tokensPerSec"), 0.01)
        assertEquals(120, parsed.getInt("totalTokens"))
        assertEquals(3840L, parsed.getLong("totalGenTimeMs"))
    }
}
