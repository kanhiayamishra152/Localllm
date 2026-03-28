package com.localllm.app.ui.screens.models

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.localllm.app.data.remote.ModelInfo
import com.localllm.app.data.repository.DownloadState
import com.localllm.app.domain.AutoConfig
import com.localllm.app.domain.ModelCompatibility
import com.localllm.app.ui.theme.ErrorColor
import com.localllm.app.ui.theme.SuccessColor
import com.localllm.app.ui.theme.WarningColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowserScreen(navController: NavController, viewModel: ModelBrowserViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) { uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Model Browser") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } })
        }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv)) {
            uiState.hardwareProfile?.let { p ->
                Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(p.deviceModel, style = MaterialTheme.typography.labelLarge)
                            Text("RAM: ${p.totalRamMB}MB | ${p.cpuCores} cores | ${p.cpuArchitecture}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            OutlinedTextField(value = searchText, onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp), placeholder = { Text("Search GGUF models...") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (searchText.isNotEmpty()) IconButton(onClick = { searchText = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchModels(searchText) }), singleLine = true, shape = MaterialTheme.shapes.medium)

            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tags = listOf("llama", "phi", "gemma", "qwen", "mistral", "deepseek")
                items(tags) { tag -> SuggestionChip(onClick = { searchText = tag; viewModel.searchModels(tag) }, label = { Text(tag) }) }
            }

            // Download progress
            (uiState.downloadState as? DownloadState.Downloading)?.let { dl ->
                Card(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Downloading...", style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { viewModel.cancelDownload() }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("${dl.downloadedMB}MB / ${dl.totalMB}MB (${(dl.progress * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (uiState.isSearching) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.models, key = { it.id }) { model ->
                        ModelCard(model, uiState.downloadedFileNames.contains(model.fileName), uiState.downloadingModelId == model.id,
                            onAnalyze = { viewModel.analyzeModel(model) }, onDownload = { viewModel.downloadModel(model) },
                            onDelete = { viewModel.deleteModel(model.fileName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: ModelInfo, isDownloaded: Boolean, isDownloading: Boolean,
    onAnalyze: () -> AutoConfig, onDownload: () -> Unit, onDelete: () -> Unit) {
    var analysis by remember { mutableStateOf<AutoConfig?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (model.isVision) { Spacer(Modifier.width(8.dp)); Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("VLM") } }
                    }
                    Text("by ${model.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isDownloaded) Badge(containerColor = SuccessColor) { Text("Downloaded") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("📦 ${formatSize(model.size)}", style = MaterialTheme.typography.bodySmall)
                Text("🔧 ${model.quantization}", style = MaterialTheme.typography.bodySmall)
                Text("⬇ ${formatNum(model.downloads)}", style = MaterialTheme.typography.bodySmall)
                Text("❤ ${formatNum(model.likes)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(model.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

            analysis?.let { cfg ->
                Spacer(Modifier.height(8.dp))
                val (bg, fg) = when (cfg.compatibility) {
                    ModelCompatibility.SMOOTH -> SuccessColor.copy(0.1f) to SuccessColor
                    ModelCompatibility.MODERATE -> WarningColor.copy(0.1f) to WarningColor
                    ModelCompatibility.BARELY -> WarningColor.copy(0.15f) to WarningColor
                    ModelCompatibility.NOT_SUPPORTED -> ErrorColor.copy(0.1f) to ErrorColor
                }
                Card(colors = CardDefaults.cardColors(bg)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${cfg.compatibility.emoji} ${cfg.compatibility.label}", style = MaterialTheme.typography.titleLarge, color = fg, fontWeight = FontWeight.Bold)
                        cfg.warningMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            listOf("Ctx" to "${cfg.contextWindow}", "TopK" to "${cfg.topK}", "TopP" to "%.2f".format(cfg.topP),
                                "Temp" to "%.1f".format(cfg.temperature), "Threads" to "${cfg.threads}").forEach { (l, v) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(v, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { analysis = onAnalyze() }, Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Analytics, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Analyze")
                }
                if (isDownloaded) {
                    OutlinedButton(onClick = onDelete, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor)) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete")
                    }
                } else {
                    Button(onClick = onDownload, Modifier.weight(1f), enabled = !isDownloading) {
                        if (isDownloading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp)); Text(if (isDownloading) "Downloading..." else "Download")
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long) = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "$bytes B"
}
private fun formatNum(n: Int) = when { n >= 1_000_000 -> "%.1fM".format(n / 1e6); n >= 1_000 -> "%.1fK".format(n / 1e3); else -> "$n" }
