package com.localllm.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.localllm.app.domain.GenerationConfig
import com.localllm.app.domain.HardwareProfile
import com.localllm.app.domain.HardwareProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
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

    init {
        _uiState.update { it.copy(hardwareProfile = hardwareProfiler.getProfile()) }
    }

    fun updateContextWindow(value: Int) {
        _uiState.update { it.copy(config = it.config.copy(contextSize = value)) }
    }

    fun updateTemperature(value: Float) {
        _uiState.update { it.copy(config = it.config.copy(temperature = value)) }
    }

    fun updateTopK(value: Int) {
        _uiState.update { it.copy(config = it.config.copy(topK = value)) }
    }

    fun updateTopP(value: Float) {
        _uiState.update { it.copy(config = it.config.copy(topP = value)) }
    }

    fun updateRepeatPenalty(value: Float) {
        _uiState.update { it.copy(config = it.config.copy(repeatPenalty = value)) }
    }

    fun updateMaxTokens(value: Int) {
        _uiState.update { it.copy(config = it.config.copy(maxTokens = value)) }
    }

    fun updateThreads(value: Int) {
        _uiState.update { it.copy(config = it.config.copy(threads = value)) }
    }
}
