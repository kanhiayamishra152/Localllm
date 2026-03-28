package com.localllm.app.data.repository

import com.localllm.app.data.db.ChatDao
import com.localllm.app.data.db.ConversationEntity
import com.localllm.app.data.db.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(private val chatDao: ChatDao) {

    fun getAllConversations(): Flow<List<ConversationEntity>> = chatDao.getAllConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(title: String = "New Chat"): String {
        val id = UUID.randomUUID().toString()
        chatDao.insertConversation(ConversationEntity(id = id, title = title))
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
        chatDao.updateConversationTitle(conversationId, content.take(50), System.currentTimeMillis())
        return id
    }

    suspend fun deleteConversation(conversationId: String) {
        chatDao.deleteConversationById(conversationId)
    }
}
