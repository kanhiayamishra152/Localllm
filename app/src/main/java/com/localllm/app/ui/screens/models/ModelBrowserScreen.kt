package com.localllm.app.ui.screens.models

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.localllm.app.data.db.ModelInfo
import com.localllm.app.data.repository.DownloadState
import com.localllm.app.domain.ModelCompatibility
import com.localllm.app.ui.screens.models.components.ModelCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelBrowserScreen(
    navController: NavController,
    viewModel: ModelBrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchText by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Model Browser") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Hardware info card
            uiState.hardwareProfile?.let { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "${profile.deviceModel}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                "RAM: ${profile.totalRamMB}MB | ${profile.cpuCores} cores | ${profile.cpuArchitecture}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search GGUF models...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { viewModel.searchModels(searchText) }
                ),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            // Quick search chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("llama", "phi", "gemma", "qwen", "mistral", "deepseek").forEach { tag ->
                    SuggestionChip(
                        onClick = {
                            searchText = tag
                            viewModel.searchModels(tag)
                        },
                        label = { Text(tag, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Download progress
            AnimatedVisibility(
                visible = uiState.downloadState is DownloadState.Downloading
            ) {
                val state = uiState.downloadState as? DownloadState.Downloading
                if (state != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Downloading...",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                TextButton(onClick = { viewModel.cancelDownload() }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${state.downloadedMB}MB / ${state.totalMB}MB (${(state.progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Results
            if (uiState.isSearching) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.models, key = { it.id }) { model ->
                        val isDownloaded = uiState.downloadedFileNames.contains(model.fileName)
                        val isDownloading = uiState.downloadingModelId == model.id

                        ModelCard(
                            model = model,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            onAnalyze = { viewModel.analyzeModel(model) },
                            onDownload = { viewModel.downloadModel(model) },
                            onDelete = { viewModel.deleteModel(model.fileName) },
                            hardwareProfile = uiState.hardwareProfile
                        )
                    }
                }
            }
        }
    }
}
