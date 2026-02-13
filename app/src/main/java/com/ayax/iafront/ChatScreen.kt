package com.ayax.iafront

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import com.ayax.iafront.ui.MarkdownText
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private sealed interface HistoryListItem {
    data class Header(val label: String) : HistoryListItem
    data class Entry(val conversation: ConversationSummary) : HistoryListItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val historyItems = remember(uiState.history) { buildHistoryItems(uiState.history) }

    var prompt by remember { mutableStateOf("") }
    var modelsExpanded by remember { mutableStateOf(false) }
    var serverInput by remember(uiState.serverBaseUrl) { mutableStateOf(uiState.serverBaseUrl) }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var renameValue by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val canSend = uiState.isModelReady && !uiState.selectedModel.isNullOrBlank() && prompt.isNotBlank()
    val sendMessage = {
        if (canSend) {
            viewModel.sendMessage(prompt)
            prompt = ""
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar conversacion") },
            text = { Text("Esta accion eliminara la conversacion del historial local.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(deleteTarget!!.id)
                        deleteTarget = null
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar conversacion") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameConversation(renameTarget!!.id, renameValue)
                        renameTarget = null
                    },
                    enabled = renameValue.isNotBlank()
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancelar") }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                OptionsPanel(
                    uiState = uiState,
                    historyItems = historyItems,
                    modelsExpanded = modelsExpanded,
                    onModelsExpandedChange = { modelsExpanded = it },
                    serverInput = serverInput,
                    onServerInputChange = { serverInput = it },
                    onApplyServer = { viewModel.updateServerBaseUrl(serverInput) },
                    onSelectModel = { viewModel.selectModel(it) },
                    onReloadModels = { viewModel.initializeModel() },
                    onOpenConversation = {
                        viewModel.openConversation(it)
                        scope.launch { drawerState.close() }
                    },
                    onRenameConversation = { conv ->
                        renameTarget = conv
                        renameValue = conv.title
                    },
                    onDeleteConversation = { deleteTarget = it }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "IA Front",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Text("☰")
                        }
                    }
                )
            },
            floatingActionButton = {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.startNewConversation() }
                    ) {
                        Text("+")
                    }
                }
            }
        ) { innerPadding ->
            ConversationPanel(
                uiState = uiState,
                prompt = prompt,
                onPromptChange = { prompt = it },
                onSend = sendMessage,
                canSend = canSend,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionsPanel(
    uiState: ChatUiState,
    historyItems: List<HistoryListItem>,
    modelsExpanded: Boolean,
    onModelsExpandedChange: (Boolean) -> Unit,
    serverInput: String,
    onServerInputChange: (String) -> Unit,
    onApplyServer: () -> Unit,
    onSelectModel: (String) -> Unit,
    onReloadModels: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRenameConversation: (ConversationSummary) -> Unit,
    onDeleteConversation: (ConversationSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Opciones", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = serverInput,
            onValueChange = onServerInputChange,
            label = { Text("Servidor local") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApplyServer) { Text("Aplicar") }
            Button(onClick = onReloadModels, enabled = !uiState.isLoadingModels) {
                Text(if (uiState.isLoadingModels) "Consultando..." else "Recargar modelos")
            }
        }

        ExposedDropdownMenuBox(
            expanded = modelsExpanded,
            onExpandedChange = onModelsExpandedChange
        ) {
            OutlinedTextField(
                value = uiState.selectedModel ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Modelo") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelsExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            DropdownMenu(
                expanded = modelsExpanded,
                onDismissRequest = { onModelsExpandedChange(false) }
            ) {
                uiState.availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onSelectModel(model)
                            onModelsExpandedChange(false)
                        }
                    )
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Historial", style = MaterialTheme.typography.titleMedium)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(
                items = historyItems,
                key = { item ->
                    when (item) {
                        is HistoryListItem.Header -> "header:${item.label}"
                        is HistoryListItem.Entry -> item.conversation.id
                    }
                }
            ) { item ->
                when (item) {
                    is HistoryListItem.Header -> {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is HistoryListItem.Entry -> {
                        val conv = item.conversation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onOpenConversation(conv.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(conv.title, maxLines = 1)
                            }
                            IconButton(onClick = { onRenameConversation(conv) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Renombrar"
                                )
                            }
                            IconButton(onClick = { onDeleteConversation(conv) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Eliminar"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationPanel(
    uiState: ChatUiState,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val hasSelectedModel = uiState.isModelReady && !uiState.selectedModel.isNullOrBlank()
        if (!hasSelectedModel) {
            Text(
                text = "No hay modelo seleccionado.",
                color = Color(0xFFB71C1C)
            )
        }
        if (!uiState.statusMessage.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = uiState.statusMessage ?: "",
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uiState.messages) { message ->
                ChatBubble(message)
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            label = { Text("Escribe tu mensaje") },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            trailingIcon = {
                Button(onClick = onSend, enabled = canSend) { Text("Enviar") }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.User
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.shapes.medium
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SelectionContainer {
                MarkdownText(
                    text = message.content,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}

private fun buildHistoryItems(history: List<ConversationSummary>): List<HistoryListItem> {
    if (history.isEmpty()) return emptyList()
    val grouped = linkedMapOf<String, MutableList<ConversationSummary>>()
    history.forEach { summary ->
        val label = historyDateLabel(summary.updatedAt)
        grouped.getOrPut(label) { mutableListOf() }.add(summary)
    }

    return buildList {
        grouped.forEach { (label, conversations) ->
            add(HistoryListItem.Header(label))
            conversations.forEach { add(HistoryListItem.Entry(it)) }
        }
    }
}

private fun historyDateLabel(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> "Hoy"
        date == today.minusDays(1) -> "Ayer"
        date.isAfter(today.minusDays(7)) -> "Esta semana"
        else -> "Anteriores"
    }
}
