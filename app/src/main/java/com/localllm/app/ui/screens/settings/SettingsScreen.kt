package com.localllm.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
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
import com.localllm.app.domain.HardwareProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Appearance / Theme
            SectionTitle("Appearance")
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChoice("System", Icons.Outlined.PhoneAndroid, uiState.themeMode == 0) { viewModel.setThemeMode(0) }
                        ThemeChoice("Light", Icons.Outlined.LightMode, uiState.themeMode == 1) { viewModel.setThemeMode(1) }
                        ThemeChoice("Dark", Icons.Outlined.DarkMode, uiState.themeMode == 2) { viewModel.setThemeMode(2) }
                    }
                }
            }

            // Cloud API keys
            SectionTitle("Cloud LLM & API Keys")
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ApiKeyField(
                        label = "Google Gemini API Key",
                        value = uiState.geminiApiKey,
                        onValueChange = viewModel::updateGeminiKey,
                        onSave = {
                            viewModel.saveGeminiKey(uiState.geminiApiKey)
                            scope.launch { snackbarHostState.showSnackbar("Gemini key saved") }
                        }
                    )
                    ApiKeyField(
                        label = "OpenAI / Compatible API Key",
                        value = uiState.openAiApiKey,
                        onValueChange = viewModel::updateOpenAiKey,
                        onSave = {
                            viewModel.saveOpenAiKey(uiState.openAiApiKey)
                            scope.launch { snackbarHostState.showSnackbar("API key saved") }
                        }
                    )
                    ApiKeyField(
                        label = "OpenAI-Compatible Base URL",
                        value = uiState.openAiBaseUrl,
                        placeholder = "http://192.168.x.x:11434/v1",
                        onValueChange = viewModel::updateOpenAiBaseUrl,
                        onSave = {
                            viewModel.saveOpenAiBaseUrl(uiState.openAiBaseUrl)
                            scope.launch { snackbarHostState.showSnackbar("Base URL saved") }
                        }
                    )
                    Text(
                        "Cloud keys enable remote LLM execution and are required for background AI tasks (local models are restricted from background execution).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Third-party integrations
            SectionTitle("Integrations")
            Card {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    IntegrationRow("GitHub", "Commits, PRs & issues", Icons.Outlined.Code, uiState.githubEnabled) { viewModel.setGithubEnabled(it) }
                    HorizontalDivider()
                    IntegrationRow("Gmail", "Summarize daily email", Icons.Outlined.Email, uiState.gmailEnabled) { viewModel.setGmailEnabled(it) }
                    HorizontalDivider()
                    IntegrationRow("Telegram", "Send messages to NeuralTask", Icons.Outlined.Send, uiState.telegramEnabled) { viewModel.setTelegramEnabled(it) }
                    HorizontalDivider()
                    IntegrationRow("WhatsApp", "Quick to-dos on the go", Icons.Outlined.Chat, uiState.whatsappEnabled) { viewModel.setWhatsappEnabled(it) }
                }
            }

            // Hardware
            uiState.hardwareProfile?.let { p ->
                SectionTitle("Device Hardware")
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        listOf("Device" to p.deviceModel, "Total RAM" to "${p.totalRamMB} MB", "Available RAM" to "${p.availableRamMB} MB",
                            "CPU Cores" to "${p.cpuCores}", "Architecture" to p.cpuArchitecture, "64-bit" to if (p.is64Bit) "Yes" else "No"
                        ).forEach { (l, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
                                Text(l, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Generation parameters
            SectionTitle("Generation Parameters")
            SettingsSlider("Context Window", uiState.config.contextSize.toFloat(), 256f..8192f, 15, "${uiState.config.contextSize}",
                { viewModel.updateContextWindow(it.toInt()) }, "Max context length")
            SettingsSlider("Temperature", uiState.config.temperature, 0f..2f, 20, "%.2f".format(uiState.config.temperature),
                { viewModel.updateTemperature(it) }, "Higher = more creative")
            SettingsSlider("Top-K", uiState.config.topK.toFloat(), 1f..100f, 99, "${uiState.config.topK}",
                { viewModel.updateTopK(it.toInt()) }, "Top tokens to consider")
            SettingsSlider("Top-P", uiState.config.topP, 0f..1f, 20, "%.2f".format(uiState.config.topP),
                { viewModel.updateTopP(it) }, "Nucleus sampling")
            SettingsSlider("Repeat Penalty", uiState.config.repeatPenalty, 1f..2f, 10, "%.2f".format(uiState.config.repeatPenalty),
                { viewModel.updateRepeatPenalty(it) }, "Penalize repeated tokens")
            SettingsSlider("Max Tokens", uiState.config.maxTokens.toFloat(), 64f..4096f, 15, "${uiState.config.maxTokens}",
                { viewModel.updateMaxTokens(it.toInt()) }, "Max response length")
            SettingsSlider("Threads", uiState.config.threads.toFloat(), 1f..12f, 11, "${uiState.config.threads}",
                { viewModel.updateThreads(it.toInt()) }, "CPU threads")
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun ThemeChoice(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier.weight(1f).border(1.dp, border, MaterialTheme.shapes.medium)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ApiKeyField(
    label: String,
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = onSave, modifier = Modifier.align(Alignment.End)) { Text("Save") }
    }
}

@Composable
private fun IntegrationRow(label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SettingsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    displayValue: String, onValueChange: (Float) -> Unit, description: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) { Text(displayValue, color = MaterialTheme.colorScheme.onSurface) }
            }
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
        }
    }
}
