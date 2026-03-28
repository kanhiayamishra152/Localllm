package com.localllm.app.data.repository

import com.localllm.app.data.db.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun getAllConversations(): Flow<List<ConversationEntity>> {
        return chatDao.getAllConversations()
    }

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return chatDao.getMessagesForConversation(conversationId)
    }

    suspend fun createConversation(title: String = "New Chat", modelId: String? = null): String {
        val id = UUID.randomUUID().toString()
        chatDao.insertConversation(
            ConversationEntity(
                id = id,
                title = title,
                modelId = modelId
            )
        )
        return id
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        content: String,
        thinkingContent: String? = null,
        isWebSearch: Boolean = false
    ): String {
        val id = UUID.randomUUID().toString()
        chatDao.insertMessage(
            MessageEntity(
                id = id,
                conversationId = conversationId,
                role = role,
                content = content,
                thinkingContent = thinkingContent,
                isWebSearchResult = isWebSearch
            )
        )
        // Update conversation timestamp
        chatDao.getAllConversations()
        return id
    }

    suspend fun updateMessage(messageId: String, content: String, thinkingContent: String? = null) {
        // We'll use insert with REPLACE
        // For simplicity, update via a direct query would be better
    }

    suspend fun updateConversationTitle(conversationId: String, title: String) {
        chatDao.insertConversation(
            ConversationEntity(
                id = conversationId,
                title = title,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteConversation(conversationId: String) {
        chatDao.deleteConversationById(conversationId)
    }
}
