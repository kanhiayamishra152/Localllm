package com.localllm.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.app.data.db.ModelInfo
import com.localllm.app.data.repository.DownloadState
import com.localllm.app.data.repository.ModelRepository
import com.localllm.app.domain.AutoConfig
import com.localllm.app.domain.HardwareProfiler
import com.localllm.app.domain.HardwareProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelBrowserUiState(
    val searchQuery: String = "",
    val models: List<ModelInfo> = emptyList(),
    val isSearching: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val downloadingModelId: String? = null,
    val hardwareProfile: HardwareProfile? = null,
    val selectedModelConfig: AutoConfig? = null,
    val error: String? = null,
    val downloadedFileNames: Set<String> = emptySet()
)

@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val hardwareProfiler: HardwareProfiler
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    init {
        loadHardwareProfile()
        loadDownloadedModels()
        searchModels("") // Load popular models

        viewModelScope.launch {
            modelRepository.downloadState.collect { state ->
                _uiState.update { it.copy(downloadState = state) }
            }
        }
    }

    private fun loadHardwareProfile() {
        val profile = hardwareProfiler.getProfile()
        _uiState.update { it.copy(hardwareProfile = profile) }
    }

    private fun loadDownloadedModels() {
        val downloaded = modelRepository.getDownloadedModels().map { it.name }.toSet()
        _uiState.update { it.copy(downloadedFileNames = downloaded) }
    }

    fun searchModels(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        viewModelScope.launch {
            try {
                val models = modelRepository.searchModels(query)
                _uiState.update { it.copy(models = models, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = "Search failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun analyzeModel(model: ModelInfo): AutoConfig {
        val config = hardwareProfiler.analyzeModelCompatibility(model.size / (1024 * 1024))
        _uiState.update { it.copy(selectedModelConfig = config) }
        return config
    }

    fun downloadModel(model: ModelInfo) {
        _uiState.update { it.copy(downloadingModelId = model.id) }
        viewModelScope.launch {
            modelRepository.downloadModel(model)
            loadDownloadedModels()
        }
    }

    fun cancelDownload() {
        modelRepository.cancelDownload()
        _uiState.update { it.copy(downloadingModelId = null) }
    }

    fun deleteModel(fileName: String) {
        modelRepository.deleteModel(fileName)
        loadDownloadedModels()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetDownload() {
        modelRepository.resetDownloadState()
        _uiState.update { it.copy(downloadingModelId = null) }
    }
}
