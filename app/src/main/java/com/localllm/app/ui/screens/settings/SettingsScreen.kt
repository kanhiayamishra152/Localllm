package com.localllm.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware Info
            uiState.hardwareProfile?.let { profile ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Device Hardware",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        HardwareRow("Device", profile.deviceModel)
                        HardwareRow("Total RAM", "${profile.totalRamMB} MB")
                        HardwareRow("Available RAM", "${profile.availableRamMB} MB")
                        HardwareRow("CPU Cores", "${profile.cpuCores}")
                        HardwareRow("Architecture", profile.cpuArchitecture)
                        HardwareRow("64-bit", if (profile.is64Bit) "Yes" else "No")
                        HardwareRow("NEON Support", if (profile.hasNeon) "Yes" else "No")
                    }
                }
            }

            // Generation Settings
            Text(
                "Generation Parameters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Context Window
            SettingsSlider(
                label = "Context Window",
                value = uiState.config.contextSize.toFloat(),
                range = 256f..8192f,
                steps = 15,
                displayValue = "${uiState.config.contextSize}",
                onValueChange = { viewModel.updateContextWindow(it.toInt()) },
                description = "Maximum context length. Higher = more memory."
            )

            // Temperature
            SettingsSlider(
                label = "Temperature",
                value = uiState.config.temperature,
                range = 0f..2f,
                steps = 20,
                displayValue = "%.2f".format(uiState.config.temperature),
                onValueChange = { viewModel.updateTemperature(it) },
                description = "Higher = more creative, Lower = more focused."
            )

            // Top-K
            SettingsSlider(
                label = "Top-K",
                value = uiState.config.topK.toFloat(),
                range = 1f..100f,
                steps = 99,
                displayValue = "${uiState.config.topK}",
                onValueChange = { viewModel.updateTopK(it.toInt()) },
                description = "Number of top tokens to consider."
            )

            // Top-P
            SettingsSlider(
                label = "Top-P",
                value = uiState.config.topP,
                range = 0f..1f,
                steps = 20,
                displayValue = "%.2f".format(uiState.config.topP),
                onValueChange = { viewModel.updateTopP(it) },
                description = "Nucleus sampling. Lower = less random."
            )

            // Repeat Penalty
            SettingsSlider(
                label = "Repeat Penalty",
                value = uiState.config.repeatPenalty,
                range = 1f..2f,
                steps = 10,
                displayValue = "%.2f".format(uiState.config.repeatPenalty),
                onValueChange = { viewModel.updateRepeatPenalty(it) },
                description = "Penalizes repeated tokens."
            )

            // Max Tokens
            SettingsSlider(
                label = "Max Tokens",
                value = uiState.config.maxTokens.toFloat(),
                range = 64f..4096f,
                steps = 15,
                displayValue = "${uiState.config.maxTokens}",
                onValueChange = { viewModel.updateMaxTokens(it.toInt()) },
                description = "Maximum response length."
            )

            // Threads
            SettingsSlider(
                label = "Threads",
                value = uiState.config.threads.toFloat(),
                range = 1f..12f,
                steps = 11,
                displayValue = "${uiState.config.threads}",
                onValueChange = { viewModel.updateThreads(it.toInt()) },
                description = "CPU threads for inference."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    description: String
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text(displayValue)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HardwareRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
