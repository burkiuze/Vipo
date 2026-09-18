package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["updatedAt"]), Index(value = ["isPinned"])]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val modelUsed: String = "Default",
    val modelPath: String = "",
    val systemPrompt: String = "You are Vipo, a private AI assistant running completely offline on device.",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["conversationId"]), Index(value = ["timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensCount: Int = 0,
    val generationTimeMs: Long = 0L,
    val tokensPerSec: Float = 0f
)
