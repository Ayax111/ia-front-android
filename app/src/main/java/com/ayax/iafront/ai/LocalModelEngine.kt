package com.ayax.iafront.ai

interface LocalModelEngine {
    suspend fun initialize(): Boolean
    suspend fun listModels(): List<String>
    fun selectModel(modelId: String)
    fun selectedModel(): String?
    fun setBaseUrl(baseUrl: String)
    fun getBaseUrl(): String
    suspend fun generateConversationTitle(firstUserPrompt: String): String
    suspend fun generateReply(prompt: String): String
}
