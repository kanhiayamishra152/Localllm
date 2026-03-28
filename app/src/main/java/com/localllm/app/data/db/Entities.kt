package com.localllm.app.data.db

import androidx.room.*
import kotlinx.serialization.Serializable

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelId: String? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val thinkingContent: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isWebSearchResult: Boolean = false
)

@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val author: String,
    val fileName: String,
    val size: Long,
    val quantization: String,
    val isVision: Boolean = false,
    val downloadUrl: String,
    val description: String = "",
    val downloads: Int = 0,
    val likes: Int = 0
)

data class ConversationWithMessages(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val messages: List<MessageEntity>
)
