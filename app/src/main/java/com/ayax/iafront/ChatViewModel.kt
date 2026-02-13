package com.ayax.iafront

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ayax.iafront.ai.ApiLocalModelEngine
import com.ayax.iafront.ai.LocalModelEngine
import com.ayax.iafront.data.AppSettingsStore
import com.ayax.iafront.data.ChatHistoryStore
import com.ayax.iafront.data.StoredConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val modelEngine: LocalModelEngine = ApiLocalModelEngine(),
    private val historyStore: ChatHistoryStore,
    private val appSettingsStore: AppSettingsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(serverBaseUrl = modelEngine.getBaseUrl())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var conversations = mutableListOf<StoredConversation>()

    init {
        viewModelScope.launch {
            loadHistory()
        }
        initializeModel()
    }

    fun initializeModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true, statusMessage = null)
            try {
                val ready = modelEngine.initialize()
                val models = modelEngine.listModels()
                if (models.isNotEmpty() && modelEngine.selectedModel() == null) {
                    modelEngine.selectModel(models.first())
                }
                val selected = modelEngine.selectedModel()
                _uiState.value = _uiState.value.copy(
                    isModelReady = ready,
                    availableModels = models,
                    selectedModel = selected,
                    isLoadingModels = false,
                    statusMessage = if (ready) null else "No se encontraron modelos."
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isModelReady = false,
                    isLoadingModels = false,
                    statusMessage = "Error consultando modelos: ${error.message ?: "sin detalle"}"
                )
            }
        }
    }

    fun selectModel(modelId: String) {
        modelEngine.selectModel(modelId)
        _uiState.value = _uiState.value.copy(selectedModel = modelId)
    }

    fun updateServerBaseUrl(input: String) {
        val normalized = normalizeBaseUrl(input)
        appSettingsStore.setServerBaseUrl(normalized)
        modelEngine.setBaseUrl(normalized)
        _uiState.value = _uiState.value.copy(
            serverBaseUrl = normalized,
            isModelReady = false,
            availableModels = emptyList(),
            selectedModel = null,
            statusMessage = "Servidor actualizado. Recargando modelos..."
        )
        initializeModel()
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val newConversation = createEmptyConversation()
            conversations.add(0, newConversation)
            persistHistory()
            publishCurrentConversation(newConversation.id)
        }
    }

    fun openConversation(conversationId: String) {
        publishCurrentConversation(conversationId)
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversations.removeAll { it.id == conversationId }
            if (conversations.isEmpty()) {
                conversations.add(createEmptyConversation())
            }
            persistHistory()
            val next = conversations.first().id
            publishCurrentConversation(next)
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        val normalized = newTitle.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch {
            val idx = conversations.indexOfFirst { it.id == conversationId }
            if (idx < 0) return@launch
            val current = conversations[idx]
            conversations[idx] = current.copy(
                title = normalized,
                updatedAt = System.currentTimeMillis()
            )
            conversations.sortByDescending { it.updatedAt }
            persistHistory()
            val currentId = _uiState.value.currentConversationId ?: conversations.first().id
            publishCurrentConversation(currentId)
        }
    }

    fun sendMessage(userPrompt: String) {
        if (userPrompt.isBlank()) return

        val currentId = _uiState.value.currentConversationId ?: return
        val withUser = appendMessage(currentId, ChatMessage(ChatRole.User, userPrompt))
        publishCurrentConversation(currentId, withUser)

        viewModelScope.launch {
            persistHistory()
            val selectedModel = _uiState.value.selectedModel
            val reply = if (_uiState.value.isModelReady && !selectedModel.isNullOrBlank()) {
                try {
                    modelEngine.generateReply(userPrompt)
                } catch (error: Exception) {
                    "Error en consulta al modelo: ${error.message ?: "sin detalle"}"
                }
            } else {
                "Selecciona un modelo y vuelve a intentar."
            }
            val withAssistant = appendMessage(currentId, ChatMessage(ChatRole.Assistant, reply))
            maybeGenerateTitle(currentId, userPrompt)
            publishCurrentConversation(currentId, withAssistant)
            persistHistory()
        }
    }

    private suspend fun loadHistory() {
        conversations = historyStore.loadAll().sortedByDescending { it.updatedAt }.toMutableList()
        if (conversations.isEmpty()) {
            conversations.add(createEmptyConversation())
            persistHistory()
        }
        publishCurrentConversation(conversations.first().id)
    }

    private fun publishCurrentConversation(
        conversationId: String,
        overrideMessages: List<ChatMessage>? = null
    ) {
        val conv = conversations.firstOrNull { it.id == conversationId } ?: return
        val messages = overrideMessages ?: conv.messages
        _uiState.value = _uiState.value.copy(
            history = buildSummaries(),
            currentConversationId = conversationId,
            messages = messages
        )
    }

    private fun appendMessage(conversationId: String, message: ChatMessage): List<ChatMessage> {
        val idx = conversations.indexOfFirst { it.id == conversationId }
        if (idx < 0) return emptyList()

        val conv = conversations[idx]
        val updatedMessages = conv.messages + message
        val updatedConv = conv.copy(
            messages = updatedMessages,
            updatedAt = System.currentTimeMillis()
        )
        conversations[idx] = updatedConv
        conversations.sortByDescending { it.updatedAt }
        return updatedMessages
    }

    private fun buildSummaries(): List<ConversationSummary> =
        conversations
            .filter { it.messages.isNotEmpty() }
            .map { conv ->
            ConversationSummary(
                id = conv.id,
                title = conv.title,
                updatedAt = conv.updatedAt
            )
        }

    private fun createEmptyConversation(): StoredConversation =
        StoredConversation(
            id = UUID.randomUUID().toString(),
            title = "Nueva conversacion",
            messages = emptyList(),
            updatedAt = System.currentTimeMillis()
        )

    private suspend fun persistHistory() {
        historyStore.saveAll(conversations)
    }

    private suspend fun maybeGenerateTitle(conversationId: String, firstUserPrompt: String) {
        val idx = conversations.indexOfFirst { it.id == conversationId }
        if (idx < 0) return
        val conv = conversations[idx]
        if (conv.title != "Nueva conversacion") return

        val generated = runCatching {
            modelEngine.generateConversationTitle(firstUserPrompt)
        }.getOrNull().orEmpty().trim()

        val fallback = firstUserPrompt.trim().take(42).ifBlank { "Conversacion" }
        val title = generated.ifBlank { fallback }

        conversations[idx] = conv.copy(
            title = title,
            updatedAt = System.currentTimeMillis()
        )
        conversations.sortByDescending { it.updatedAt }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return "http://$trimmed"
    }
}
