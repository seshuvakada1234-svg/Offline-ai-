package com.myai.offline.data.repository

import com.myai.offline.data.database.ConversationDao
import com.myai.offline.data.database.ConversationEntity
import com.myai.offline.data.database.MessageDao
import com.myai.offline.data.database.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun getMessagesList(conversationId: String): List<MessageEntity> {
        return messageDao.getMessagesList(conversationId)
    }

    suspend fun createNewConversation(title: String = "New Chat", modelId: String = "qwen3-1.7b"): String {
        val id = UUID.randomUUID().toString()
        val conversation = ConversationEntity(
            id = id,
            title = title,
            selectedModelId = modelId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insertConversation(conversation)
        return id
    }

    suspend fun updateConversationTitle(id: String, newTitle: String) {
        val existing = conversationDao.getConversationById(id) ?: return
        conversationDao.updateConversation(existing.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateConversationModel(id: String, modelId: String) {
        val existing = conversationDao.getConversationById(id) ?: return
        conversationDao.updateConversation(existing.copy(selectedModelId = modelId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversationById(id)
    }

    suspend fun clearAllConversations() {
        conversationDao.clearAllConversations()
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        actionType: String? = null,
        actionDataJson: String? = null,
        metricsJson: String? = null,
        isVoiceInput: Boolean = false
    ): MessageEntity {
        val message = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            actionType = actionType,
            actionDataJson = actionDataJson,
            metricsJson = metricsJson,
            isVoiceInput = isVoiceInput
        )
        messageDao.insertMessage(message)
        
        // Touch parent conversation updatedAt
        val parent = conversationDao.getConversationById(conversationId)
        if (parent != null) {
            conversationDao.updateConversation(parent.copy(updatedAt = System.currentTimeMillis()))
        }
        
        return message
    }
}
