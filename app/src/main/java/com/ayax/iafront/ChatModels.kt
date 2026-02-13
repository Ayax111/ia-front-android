package com.ayax.iafront

enum class ChatRole {
    User,
    Assistant
}

data class ChatMessage(
    val role: ChatRole,
    val content: String
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long
)

data class ChatUiState(
    val isModelReady: Boolean = false,
    val serverBaseUrl: String = "http://192.168.0.194:1234",
    val availableModels: List<String> = emptyList(),
    val selectedModel: String? = null,
    val isLoadingModels: Boolean = false,
    val statusMessage: String? = null,
    val history: List<ConversationSummary> = emptyList(),
    val currentConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList()
)
