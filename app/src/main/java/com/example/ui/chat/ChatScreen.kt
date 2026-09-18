package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChatMessageEntity
import com.example.ui.components.ModelLogo
import com.example.ui.components.NavigationDrawerContent
import com.example.ui.components.PerformanceStatsDialog
import com.example.ui.theme.VipoGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val showPerformanceSetting by viewModel.showPerformanceSetting.collectAsState()
    val enabledPlugins by viewModel.enabledPluginIds.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var topMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshModels() }

    LaunchedEffect(uiState.messages.size, uiState.streamingContent) {
        val count = uiState.messages.size + if (uiState.isGenerating) 1 else 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerShape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                modifier = Modifier.width(304.dp)
            ) {
                NavigationDrawerContent(
                    conversations = conversations,
                    activeConversationId = uiState.currentConversation?.id,
                    activePluginCount = enabledPlugins.size,
                    onSelectConversation = { id ->
                        viewModel.selectConversation(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.newConversation()
                        scope.launch { drawerState.close() }
                    },
                    onRenameConversation = { id, title -> viewModel.renameConversation(id, title) },
                    onDeleteConversation = { id -> viewModel.deleteConversation(id) },
                    onTogglePin = { id, pin -> viewModel.togglePin(id, pin) },
                    onDuplicateConversation = { id -> viewModel.duplicateConversation(id) },
                    onNavigateToLibrary = {
                        scope.launch { drawerState.close() }
                        onNavigateToLibrary()
                    },
                    onNavigateToPlugins = {
                        scope.launch { drawerState.close() }
                        onNavigateToPlugins()
                    },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        onNavigateToSettings()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { viewModel.setModelSwitchDialogVisible(true) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            ModelLogo(
                                modelName = uiState.activeModelName ?: "Vipo",
                                size = 26.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.activeModelName ?: "Choose a model",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = "Switch model",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Chats")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.newConversation() }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat", modifier = Modifier.size(20.dp))
                        }

                        Box {
                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Library") },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        onNavigateToLibrary()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Plugins") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    trailingIcon = {
                                        Text(
                                            text = enabledPlugins.size.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        onNavigateToPlugins()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("System prompt") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        viewModel.setSystemPromptDialogVisible(true)
                                    }
                                )
                                if (showPerformanceSetting) {
                                    DropdownMenuItem(
                                        text = { Text("Performance") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                                        },
                                        onClick = {
                                            topMenuExpanded = false
                                            viewModel.setPerformancePanelVisible(true)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        onNavigateToSettings()
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                DropdownMenuItem(
                                    text = { Text("Clear messages", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        viewModel.clearCurrentChat()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyChatWelcome(
                        isModelLoaded = uiState.isModelLoaded,
                        activePluginCount = enabledPlugins.size,
                        onSuggestionClick = { prompt ->
                            viewModel.onInputTextChanged(prompt)
                            viewModel.sendMessage()
                        },
                        onOpenLibrary = onNavigateToLibrary,
                        onOpenPlugins = onNavigateToPlugins,
                        onNewChat = { viewModel.newConversation() },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageBubble(
                                message = message,
                                isEditing = uiState.editingMessageId == message.id,
                                editText = uiState.editingMessageText,
                                showStats = showPerformanceSetting,
                                onEditTextChange = { viewModel.onEditMessageTextChanged(it) },
                                onSaveEdit = { viewModel.saveEditedMessageAndRegenerate() },
                                onCancelEdit = { viewModel.cancelEditMessage() },
                                onStartEdit = { viewModel.startEditMessage(message.id, message.content) },
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Vipo", message.content))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                onRegenerate = { viewModel.regenerateLastMessage() }
                            )
                        }

                        if (uiState.isGenerating) {
                            item {
                                StreamingAssistantBubble(
                                    content = uiState.streamingContent,
                                    tokensPerSecond = uiState.performanceStats.tokensPerSecond,
                                    showStats = showPerformanceSetting,
                                    onStop = { viewModel.stopGeneration() }
                                )
                            }
                        }
                    }
                }

                ChatInputBar(
                    text = uiState.inputText,
                    onTextChange = { viewModel.onInputTextChanged(it) },
                    isGenerating = uiState.isGenerating,
                    isModelLoaded = uiState.isModelLoaded,
                    onSend = { viewModel.sendMessage() },
                    onStop = { viewModel.stopGeneration() },
                    onSelectModel = { viewModel.setModelSwitchDialogVisible(true) }
                )
            }
        }
    }

    if (uiState.showPerformancePanel) {
        PerformanceStatsDialog(
            stats = uiState.performanceStats,
            modelName = uiState.activeModelName,
            metadata = viewModel.engine.getMetadata(),
            onDismiss = { viewModel.setPerformancePanelVisible(false) }
        )
    }

    if (uiState.showSystemPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setSystemPromptDialogVisible(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("System prompt") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Sets how the model behaves in this chat. Active plugins are appended to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.systemPromptDraft,
                        onValueChange = { viewModel.onSystemPromptDraftChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("You are Vipo, a private on-device assistant...") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.saveSystemPrompt() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setSystemPromptDialogVisible(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showModelSwitchDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setModelSwitchDialogVisible(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Models on this device") },
            text = {
                if (downloadedModels.isEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            text = "No models downloaded yet. Open the library to get one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(downloadedModels, key = { it.id }) { model ->
                            val isCurrent = model.filePath == viewModel.engine.activeModelPath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { viewModel.switchModel(model.filePath, model.displayName) }
                                    .padding(vertical = 9.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ModelLogo(
                                    modelName = model.displayName,
                                    architecture = model.architecture,
                                    size = 30.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${model.variantName ?: model.architecture?.uppercase() ?: "GGUF"} · ${model.formattedSize}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Active",
                                        tint = VipoGreen,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setModelSwitchDialogVisible(false)
                    onNavigateToLibrary()
                }) {
                    Text("Open library")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setModelSwitchDialogVisible(false) }) { Text("Close") }
            }
        )
    }
}

@Composable
fun EmptyChatWelcome(
    isModelLoaded: Boolean,
    activePluginCount: Int,
    onSuggestionClick: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlugins: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Vipo",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Local models. Nothing leaves your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(icon = Icons.Default.Add, label = "New chat", onClick = onNewChat)
            QuickAction(icon = Icons.AutoMirrored.Filled.LibraryBooks, label = "Library", onClick = onOpenLibrary)
            QuickAction(
                icon = Icons.Default.Extension,
                label = if (activePluginCount > 0) "Plugins · $activePluginCount" else "Plugins",
                onClick = onOpenPlugins
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (!isModelLoaded) {
            Text(
                text = "No model loaded yet. Download one from the library to start chatting offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val suggestions = listOf(
                "Explain how GGUF quantization works",
                "Write a Kotlin coroutine that reads a file",
                "Summarize the text I paste next"
            )
            suggestions.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onSuggestionClick(prompt) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    isEditing: Boolean,
    editText: String,
    showStats: Boolean,
    onEditTextChange: (String) -> Unit,
    onSaveEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onStartEdit: () -> Unit,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isEditing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onEditTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancelEdit) { Text("Cancel") }
                    TextButton(onClick = onSaveEdit) { Text("Save and regenerate") }
                }
            }
        } else {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                // Assistant replies read as plain text, no container.
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (!isUser && showStats && message.tokensPerSec > 0f) {
                    Text(
                        text = "${message.tokensPerSec} t/s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }

                if (isUser) {
                    IconButton(onClick = onStartEdit, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                } else {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingAssistantBubble(
    content: String,
    tokensPerSecond: Float,
    showStats: Boolean,
    onStop: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (content.isEmpty()) "Thinking on device..." else content,
            style = MaterialTheme.typography.bodyLarge,
            color = if (content.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (showStats && tokensPerSecond > 0f) {
                Text(
                    text = "$tokensPerSecond t/s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            TextButton(
                onClick = onStop,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Stop",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onSelectModel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = if (isModelLoaded) "Message" else "Load a model to chat",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            maxLines = 5,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        val sendEnabled = isModelLoaded && text.isNotBlank()
        IconButton(
            onClick = {
                when {
                    isGenerating -> onStop()
                    !isModelLoaded -> onSelectModel()
                    else -> onSend()
                }
            },
            enabled = isGenerating || !isModelLoaded || sendEnabled,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (sendEnabled || isGenerating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
        ) {
            Icon(
                imageVector = if (isGenerating) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                contentDescription = if (isGenerating) "Stop" else "Send",
                tint = if (sendEnabled || isGenerating) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
