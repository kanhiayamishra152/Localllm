package com.localllm.app.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.localllm.app.ui.navigation.Screen
import com.localllm.app.ui.screens.chat.components.*
import com.localllm.app.ui.screens.sidebar.SidebarDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    conversationId: String? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawer(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                onNewChat = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onNavigateToModels = {
                    navController.navigate(Screen.Models.route)
                    scope.launch { drawerState.close() }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                ChatTopBar(
                    modelName = uiState.loadedModelName,
                    isModelLoaded = uiState.isModelLoaded,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewChat = { viewModel.newConversation() }
                )
            },
            bottomBar = {
                Column {
                    // Toggle bar
                    ToggleBar(
                        webSearchEnabled = uiState.webSearchEnabled,
                        thinkingEnabled = uiState.thinkingEnabled,
                        onToggleWebSearch = { viewModel.toggleWebSearch() },
                        onToggleThinking = { viewModel.toggleThinking() }
                    )
                    // Input bar
                    InputBar(
                        isGenerating = uiState.isGenerating,
                        isModelLoaded = uiState.isModelLoaded,
                        onSend = { viewModel.sendMessage(it) },
                        onStop = { viewModel.stopGeneration() }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.messages.isEmpty() && !uiState.isLoadingModel) {
                    // Empty state
                    EmptyState(
                        isModelLoaded = uiState.isModelLoaded,
                        downloadedModels = uiState.downloadedModels,
                        onLoadModel = { viewModel.loadModel(it) },
                        onBrowseModels = { navController.navigate(Screen.Models.route) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = uiState.messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                thinkingEnabled = uiState.thinkingEnabled
                            )
                        }

                        // Loading indicator
                        if (uiState.isLoadingModel) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Loading model...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleBar(
    webSearchEnabled: Boolean,
    thinkingEnabled: Boolean,
    onToggleWebSearch: () -> Unit,
    onToggleThinking: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = webSearchEnabled,
            onClick = onToggleWebSearch,
            label = { Text("Web Search", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.TravelExplore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.height(32.dp)
        )

        FilterChip(
            selected = thinkingEnabled,
            onClick = onToggleThinking,
            label = { Text("Thinking", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun EmptyState(
    isModelLoaded: Boolean,
    downloadedModels: List<java.io.File>,
    onLoadModel: (java.io.File) -> Unit,
    onBrowseModels: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "LocalLLM",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (!isModelLoaded) {
            Text(
                "Load a model to start chatting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (downloadedModels.isNotEmpty()) {
                Text(
                    "Downloaded Models:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                downloadedModels.forEach { file ->
                    OutlinedCard(
                        onClick = { onLoadModel(file) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.nameWithoutExtension,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${file.length() / (1024 * 1024)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Load",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(onClick = onBrowseModels) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse & Download Models")
            }
        } else {
            Text(
                "How can I help you today?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
