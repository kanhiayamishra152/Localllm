package com.localllm.app.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.localllm.app.data.db.ConversationEntity
import com.localllm.app.ui.theme.AccentGreen
import com.localllm.app.ui.theme.SuccessColor
import com.localllm.app.ui.theme.ThinkingBubbleColor
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxHeight().width(300.dp).padding(12.dp)) {
                    Button(onClick = { viewModel.newConversation(); scope.launch { drawerState.close() } },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("New Chat")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Recent Chats", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(uiState.conversations, key = { it.id }) { conv ->
                            Surface(onClick = { viewModel.selectConversation(conv.id); scope.launch { drawerState.close() } },
                                shape = RoundedCornerShape(8.dp),
                                color = if (conv.id == uiState.currentConversationId) MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                                else MaterialTheme.colorScheme.surface) {
                                Row(Modifier.fillMaxWidth().padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Text(conv.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { viewModel.deleteConversation(conv.id) }, Modifier.size(24.dp)) {
                                        Icon(Icons.Outlined.Delete, "Delete", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(icon = { Icon(Icons.Outlined.Download, null) }, label = { Text("Models") }, selected = false,
                        onClick = { navController.navigate("models"); scope.launch { drawerState.close() } })
                    NavigationDrawerItem(icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("Settings") }, selected = false,
                        onClick = { navController.navigate("settings"); scope.launch { drawerState.close() } })
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("LocalLLM", style = MaterialTheme.typography.titleLarge)
                                uiState.loadedModelName?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (uiState.isModelLoaded) { Spacer(Modifier.width(8.dp)); Badge(containerColor = SuccessColor) { Text("●") } }
                        }
                    },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Filled.Menu, "Menu") } },
                    actions = { IconButton(onClick = { viewModel.newConversation() }) { Icon(Icons.Outlined.Edit, "New") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                Column {
                    // Toggles
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = uiState.webSearchEnabled, onClick = { viewModel.toggleWebSearch() },
                            label = { Text("Web Search", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Outlined.TravelExplore, null, Modifier.size(16.dp)) }, modifier = Modifier.height(32.dp))
                        FilterChip(selected = uiState.thinkingEnabled, onClick = { viewModel.toggleThinking() },
                            label = { Text("Thinking", style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = { Icon(Icons.Outlined.Psychology, null, Modifier.size(16.dp)) }, modifier = Modifier.height(32.dp))
                    }
                    // Input
                    InputBar(uiState.isGenerating, uiState.isModelLoaded, onSend = { viewModel.sendMessage(it) }, onStop = { viewModel.stopGeneration() })
                }
            }
        ) { paddingValues ->
            Box(Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {
                if (uiState.messages.isEmpty() && !uiState.isLoadingModel) {
                    EmptyState(uiState.isModelLoaded, uiState.downloadedModels,
                        onLoadModel = { viewModel.loadModel(it) }, onBrowseModels = { navController.navigate("models") })
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(uiState.messages, key = { it.id }) { msg -> MessageBubble(msg) }
                        if (uiState.isLoadingModel) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Loading model...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(if (isUser) MaterialTheme.colorScheme.surfaceVariant else AccentGreen),
                contentAlignment = Alignment.Center) {
                Icon(if (isUser) Icons.Filled.Person else Icons.Filled.AutoAwesome, null, Modifier.size(16.dp),
                    if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (isUser) "You" else "Assistant", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (message.isGenerating) {
                Spacer(Modifier.width(8.dp))
                val inf = rememberInfiniteTransition(label = "dots")
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { i ->
                        val alpha by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, i * 200), RepeatMode.Reverse), "d$i")
                        Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha)))
                    }
                }
            }
        }
        // Thinking bubble
        if (!isUser && message.thinkingContent != null) {
            var expanded by remember { mutableStateOf(false) }
            Surface(Modifier.fillMaxWidth().padding(start = 36.dp), color = ThinkingBubbleColor.copy(0.15f), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Psychology, null, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.weight(1f))
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(20.dp))
                    }
                    AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
                        Text(message.thinkingContent, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (message.content.isNotEmpty()) {
            Text(message.content, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 36.dp))
        }
    }
}

@Composable
private fun InputBar(isGenerating: Boolean, isModelLoaded: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.3f), RoundedCornerShape(24.dp)).padding(16.dp, 12.dp)) {
                BasicTextField(value = text, onValueChange = { text = it }, Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), maxLines = 6, enabled = isModelLoaded,
                    decorationBox = { inner ->
                        Box { if (text.isEmpty()) Text(if (isModelLoaded) "Message LocalLLM..." else "Load a model to start...",
                            style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontSize = 16.sp)); inner() }
                    })
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(isGenerating, label = "btn") { gen ->
                if (gen) {
                    IconButton(onClick = onStop, Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Stop, "Stop", tint = MaterialTheme.colorScheme.onError)
                    }
                } else {
                    IconButton(onClick = { if (text.isNotBlank()) { onSend(text.trim()); text = "" } },
                        enabled = text.isNotBlank() && isModelLoaded,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(
                            if (text.isNotBlank() && isModelLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)) {
                        Icon(Icons.Filled.ArrowUpward, "Send",
                            tint = if (text.isNotBlank() && isModelLoaded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isModelLoaded: Boolean, downloadedModels: List<File>, onLoadModel: (File) -> Unit, onBrowseModels: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(48.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("LocalLLM", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        if (!isModelLoaded) {
            Text("Load a model to start chatting", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if (downloadedModels.isNotEmpty()) {
                Text("Downloaded Models:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                downloadedModels.forEach { file ->
                    OutlinedCard(onClick = { onLoadModel(file) }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyMedium)
                                Text("${file.length() / (1024 * 1024)} MB", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.PlayArrow, "Load", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Button(onClick = onBrowseModels) {
                Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Browse & Download Models")
            }
        } else {
            Text("How can I help you today?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
