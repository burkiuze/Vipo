package com.example.repository

import com.example.data.local.ChatMessageDao
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao
) {
    val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    /** One-shot read of a conversation's messages, used to build the model prompt. */
    suspend fun getMessagesOnce(conversationId: String): List<ChatMessageEntity> {
        return chatMessageDao.getMessagesSnapshot(conversationId)
    }

    fun getMessages(conversationId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForConversation(conversationId)
    }

    fun searchConversations(query: String): Flow<List<ConversationEntity>> {
        return conversationDao.searchConversations(query)
    }

    suspend fun getConversationById(id: String): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }

    suspend fun createConversation(
        title: String = "New Chat",
        modelUsed: String = "Default Model",
        modelPath: String = "",
        systemPrompt: String = "You are Vipo, a private AI assistant running completely offline on device."
    ): ConversationEntity {
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            modelUsed = modelUsed,
            modelPath = modelPath,
            systemPrompt = systemPrompt,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = false
        )
        conversationDao.insertConversation(conv)
        return conv
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        conversationDao.renameConversation(id, newTitle)
    }

    suspend fun togglePin(id: String, isPinned: Boolean) {
        conversationDao.setPinned(id, !isPinned)
    }

    suspend fun updateModelUsed(id: String, modelName: String, modelPath: String) {
        conversationDao.updateModelUsed(id, modelName, modelPath)
    }

    suspend fun updateSystemPrompt(id: String, systemPrompt: String) {
        conversationDao.updateSystemPrompt(id, systemPrompt)
    }

    suspend fun deleteConversation(id: String) {
        chatMessageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversationById(id)
    }

    suspend fun duplicateConversation(id: String): ConversationEntity? {
        val orig = conversationDao.getConversationById(id) ?: return null
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "${orig.title} (Copy)",
            modelUsed = orig.modelUsed,
            modelPath = orig.modelPath,
            systemPrompt = orig.systemPrompt,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = false
        )
        conversationDao.insertConversation(newConv)

        val origMessages = chatMessageDao.getMessagesSnapshot(id)
        for (m in origMessages) {
            chatMessageDao.insertMessage(
                m.copy(
                    id = UUID.randomUUID().toString(),
                    conversationId = newConv.id
                )
            )
        }
        return newConv
    }

    suspend fun insertMessage(
        conversationId: String,
        role: String,
        content: String,
        tokensCount: Int = 0,
        generationTimeMs: Long = 0L,
        tokensPerSec: Float = 0f
    ): ChatMessageEntity {
        val msg = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            tokensCount = tokensCount,
            generationTimeMs = generationTimeMs,
            tokensPerSec = tokensPerSec
        )
        chatMessageDao.insertMessage(msg)
        // Update conversation's updatedAt
        val conv = conversationDao.getConversationById(conversationId)
        if (conv != null) {
            val title = if (conv.title == "New Chat" && role == "user") {
                val preview = content.take(32).trim()
                if (content.length > 32) "$preview..." else preview
            } else conv.title
            conversationDao.updateConversation(conv.copy(title = title, updatedAt = System.currentTimeMillis()))
        }
        return msg
    }

    suspend fun updateMessage(id: String, content: String) {
        chatMessageDao.updateMessageContent(id, content)
    }

    suspend fun deleteMessagesFrom(conversationId: String, timestamp: Long) {
        chatMessageDao.deleteMessagesFromTimestamp(conversationId, timestamp)
    }

    suspend fun clearMessages(conversationId: String) {
        chatMessageDao.deleteMessagesForConversation(conversationId)
    }
}
