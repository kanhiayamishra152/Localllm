package com.localllm.app.ui.screens.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.app.data.remote.ModelInfo
import com.localllm.app.data.repository.DownloadState
import com.localllm.app.data.repository.ModelRepository
import com.localllm.app.domain.AutoConfig
import com.localllm.app.domain.HardwareProfile
import com.localllm.app.domain.HardwareProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelBrowserUiState(
    val models: List<ModelInfo> = emptyList(),
    val isSearching: Boolean = false,
    val downloadState: DownloadState = DownloadState.Idle,
    val downloadingModelId: String? = null,
    val hardwareProfile: HardwareProfile? = null,
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
        _uiState.update { it.copy(hardwareProfile = hardwareProfiler.getProfile()) }
        loadDownloadedModels()
        searchModels("")
        viewModelScope.launch {
            modelRepository.downloadState.collect { state -> _uiState.update { it.copy(downloadState = state) } }
        }
    }

    private fun loadDownloadedModels() {
        _uiState.update { it.copy(downloadedFileNames = modelRepository.getDownloadedModels().map { it.name }.toSet()) }
    }

    fun searchModels(query: String) {
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            try {
                val models = modelRepository.searchModels(query)
                _uiState.update { it.copy(models = models, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false, error = "Search failed: ${e.message}") }
            }
        }
    }

    fun analyzeModel(model: ModelInfo): AutoConfig = hardwareProfiler.analyzeModelCompatibility(model.size / (1024 * 1024))

    fun downloadModel(model: ModelInfo) {
        _uiState.update { it.copy(downloadingModelId = model.id) }
        viewModelScope.launch { modelRepository.downloadModel(model); loadDownloadedModels() }
    }

    fun cancelDownload() { modelRepository.cancelDownload(); _uiState.update { it.copy(downloadingModelId = null) } }
    fun deleteModel(fileName: String) { modelRepository.deleteModel(fileName); loadDownloadedModels() }
    fun clearError() { _uiState.update { it.copy(error = null) } }
}
