package com.localllm.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.localllm.app.domain.HardwareProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import com.localllm.app.domain.GenerationConfig
import com.localllm.app.domain.HardwareProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val config: GenerationConfig = GenerationConfig(),
    val hardwareProfile: HardwareProfile? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val hardwareProfiler: HardwareProfiler
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    init { _uiState.update { it.copy(hardwareProfile = hardwareProfiler.getProfile()) } }
    fun updateContextWindow(v: Int) { _uiState.update { it.copy(config = it.config.copy(contextSize = v)) } }
    fun updateTemperature(v: Float) { _uiState.update { it.copy(config = it.config.copy(temperature = v)) } }
    fun updateTopK(v: Int) { _uiState.update { it.copy(config = it.config.copy(topK = v)) } }
    fun updateTopP(v: Float) { _uiState.update { it.copy(config = it.config.copy(topP = v)) } }
    fun updateRepeatPenalty(v: Float) { _uiState.update { it.copy(config = it.config.copy(repeatPenalty = v)) } }
    fun updateMaxTokens(v: Int) { _uiState.update { it.copy(config = it.config.copy(maxTokens = v)) } }
    fun updateThreads(v: Int) { _uiState.update { it.copy(config = it.config.copy(threads = v)) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Advanced Settings") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back") } })
        }
    ) { pv ->
        Column(Modifier.fillMaxSize().padding(pv).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            uiState.hardwareProfile?.let { p ->
                Card(colors = CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer.copy(0.3f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Device Hardware", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
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
            Text("Generation Parameters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
private fun SettingsSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int,
    displayValue: String, onValueChange: (Float) -> Unit, description: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) { Text(displayValue) }
            }
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth())
        }
    }
}
