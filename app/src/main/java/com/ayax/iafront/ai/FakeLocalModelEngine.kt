package com.ayax.iafront.ai

import kotlinx.coroutines.delay

class FakeLocalModelEngine : LocalModelEngine {

    private var initialized = false
    private var baseUrl = "http://192.168.0.194:1234"
    private val models = listOf("mock-1", "mock-2")
    private var currentModel: String? = null

    override suspend fun initialize(): Boolean {
        delay(350)
        initialized = true
        if (currentModel == null) {
            currentModel = models.first()
        }
        return initialized
    }

    override suspend fun listModels(): List<String> {
        delay(120)
        return models
    }

    override fun selectModel(modelId: String) {
        currentModel = if (models.contains(modelId)) modelId else currentModel
    }

    override fun selectedModel(): String? = currentModel

    override fun setBaseUrl(baseUrl: String) {
        this.baseUrl = baseUrl
    }

    override fun getBaseUrl(): String = baseUrl

    override suspend fun generateConversationTitle(firstUserPrompt: String): String {
        delay(100)
        return firstUserPrompt.trim().take(42).ifBlank { "Conversacion" }
    }

    override suspend fun generateReply(prompt: String): String {
        delay(250)
        if (!initialized) return "Modelo no inicializado."
        return "[Mock ${currentModel ?: "sin-modelo"}] Recibido: $prompt"
    }
}
