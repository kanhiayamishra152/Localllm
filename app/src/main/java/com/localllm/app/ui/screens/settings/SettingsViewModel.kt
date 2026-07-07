package com.localllm.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.app.data.preferences.UserPreferencesRepository
import com.localllm.app.domain.GenerationConfig
import com.localllm.app.domain.HardwareProfile
import com.localllm.app.domain.HardwareProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: GenerationConfig = GenerationConfig(),
    val hardwareProfile: HardwareProfile? = null,
    val themeMode: Int = 0,
    val geminiApiKey: String = "",
    val openAiApiKey: String = "",
    val openAiBaseUrl: String = "",
    val githubEnabled: Boolean = false,
    val gmailEnabled: Boolean = false,
    val telegramEnabled: Boolean = false,
    val whatsappEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val hardwareProfiler: HardwareProfiler,
    private val userPreferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(hardwareProfile = hardwareProfiler.getProfile()) }
        viewModelScope.launch {
            userPreferences.themeMode.collect { _uiState.update { s -> s.copy(themeMode = it) } }
            userPreferences.geminiApiKey.collect { _uiState.update { s -> s.copy(geminiApiKey = it) } }
            userPreferences.openAiApiKey.collect { _uiState.update { s -> s.copy(openAiApiKey = it) } }
            userPreferences.openAiBaseUrl.collect { _uiState.update { s -> s.copy(openAiBaseUrl = it) } }
            userPreferences.githubEnabled.collect { _uiState.update { s -> s.copy(githubEnabled = it) } }
            userPreferences.gmailEnabled.collect { _uiState.update { s -> s.copy(gmailEnabled = it) } }
            userPreferences.telegramEnabled.collect { _uiState.update { s -> s.copy(telegramEnabled = it) } }
            userPreferences.whatsappEnabled.collect { _uiState.update { s -> s.copy(whatsappEnabled = it) } }
        }
    }

    fun setThemeMode(value: Int) = viewModelScope.launch { userPreferences.setThemeMode(value) }
    fun updateGeminiKey(v: String) { _uiState.update { it.copy(geminiApiKey = v) } }
    fun saveGeminiKey(v: String) = viewModelScope.launch { userPreferences.setGeminiApiKey(v) }
    fun updateOpenAiKey(v: String) { _uiState.update { it.copy(openAiApiKey = v) } }
    fun saveOpenAiKey(v: String) = viewModelScope.launch { userPreferences.setOpenAiApiKey(v) }
    fun updateOpenAiBaseUrl(v: String) { _uiState.update { it.copy(openAiBaseUrl = v) } }
    fun saveOpenAiBaseUrl(v: String) = viewModelScope.launch { userPreferences.setOpenAiBaseUrl(v) }

    fun setGithubEnabled(v: Boolean) = viewModelScope.launch { userPreferences.setGithubEnabled(v) }
    fun setGmailEnabled(v: Boolean) = viewModelScope.launch { userPreferences.setGmailEnabled(v) }
    fun setTelegramEnabled(v: Boolean) = viewModelScope.launch { userPreferences.setTelegramEnabled(v) }
    fun setWhatsappEnabled(v: Boolean) = viewModelScope.launch { userPreferences.setWhatsappEnabled(v) }

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
