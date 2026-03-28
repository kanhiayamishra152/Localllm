package com.localllm.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.app.data.db.ConversationEntity
import com.localllm.app.data.remote.DuckDuckGoSearch
import com.localllm.app.data.repository.ChatRepository
import com.localllm.app.data.repository.ModelRepository
import com.localllm.app.domain.GenerationConfig
import com.localllm.app.domain.HardwareProfiler
import com.localllm.app.domain.InferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val thinkingContent: String? = null,
    val isGenerating: Boolean = false,
    val isWebSearchResult: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null,
    val isGenerating: Boolean = false,
    val isModelLoaded: Boolean = false,
    val loadedModelName: String? = null,
    val webSearchEnabled: Boolean = false,
    val thinkingEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isLoadingModel: Boolean = false,
    val generationConfig: GenerationConfig = GenerationConfig(),
    val downloadedModels: List<File> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine,
    private val hardwareProfiler: HardwareProfiler,
    private val webSearch: DuckDuckGoSearch
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { convos ->
                _uiState.update { it.copy(conversations = convos) }
            }
        }
        loadDownloadedModels()
    }

    fun loadDownloadedModels() {
        _uiState.update { it.copy(downloadedModels = modelRepository.getDownloadedModels()) }
    }

    fun loadModel(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, errorMessage = null) }
            val profile = hardwareProfiler.analyzeModelCompatibility(file.length() / (1024 * 1024))
            val config = GenerationConfig(
                contextSize = profile.contextWindow, temperature = profile.temperature,
                topK = profile.topK, topP = profile.topP, repeatPenalty = profile.repeatPenalty,
                maxTokens = profile.maxTokens, threads = profile.threads
            )
            inferenceEngine.loadModel(file.absolutePath, config).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isModelLoaded = true, isLoadingModel = false,
                            loadedModelName = file.nameWithoutExtension, generationConfig = config)
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoadingModel = false, errorMessage = "Load failed: ${e.message}") }
                }
            )
        }
    }

    fun newConversation() {
        viewModelScope.launch {
            val id = chatRepository.createConversation()
            _uiState.update { it.copy(currentConversationId = id, messages = emptyList()) }
        }
    }

    fun selectConversation(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentConversationId = id) }
            chatRepository.getMessages(id).collect { msgs ->
                _uiState.update { s ->
                    s.copy(messages = msgs.map { m ->
                        ChatMessage(m.id, m.role, m.content, m.thinkingContent, isWebSearchResult = m.isWebSearchResult)
                    })
                }
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_uiState.value.currentConversationId == id) {
                _uiState.update { it.copy(currentConversationId = null, messages = emptyList()) }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isGenerating) return
        if (!_uiState.value.isModelLoaded) {
            _uiState.update { it.copy(errorMessage = "Please load a model first") }
            return
        }
        viewModelScope.launch {
            val convId = _uiState.value.currentConversationId ?: chatRepository.createConversation(content.take(50)).also { id ->
                _uiState.update { it.copy(currentConversationId = id) }
            }
            chatRepository.addMessage(convId, "user", content)
            val userMsg = ChatMessage(System.currentTimeMillis().toString(), "user", content)
            _uiState.update { it.copy(messages = it.messages + userMsg) }

            var webContext: String? = null
            if (_uiState.value.webSearchEnabled) {
                try { webContext = webSearch.searchAndSummarize(content) } catch (_: Exception) {}
            }

            _uiState.update { it.copy(isGenerating = true) }
            val assistantId = "${System.currentTimeMillis()}_a"
            _uiState.update { it.copy(messages = it.messages + ChatMessage(assistantId, "assistant", "", isGenerating = true)) }

            val prompt = inferenceEngine.buildPrompt(
                _uiState.value.messages.map { it.role to it.content },
                webContext = webContext, thinkingEnabled = _uiState.value.thinkingEnabled
            )

            val sb = StringBuilder()
            var thinking: String? = null
            var final_ = ""

            generationJob = launch {
                try {
                    inferenceEngine.generateStream(prompt, _uiState.value.generationConfig).collect { token ->
                        sb.append(token)
                        val full = sb.toString()
                        if (_uiState.value.thinkingEnabled && full.contains("<think>")) {
                            val end = full.indexOf("</think>")
                            if (end != -1) {
                                thinking = full.substringAfter("<think>").substringBefore("</think>").trim()
                                final_ = full.substringAfter("</think>").trim()
                            } else {
                                thinking = full.substringAfter("<think>").trim()
                                final_ = ""
                            }
                        } else {
                            final_ = full
                        }
                        _uiState.update { s -> s.copy(messages = s.messages.map { m ->
                            if (m.id == assistantId) m.copy(content = final_, thinkingContent = thinking, isGenerating = true) else m
                        }) }
                    }
                } catch (_: Exception) {}
                finally {
                    chatRepository.addMessage(convId, "assistant", final_, thinking, webContext != null)
                    _uiState.update { s -> s.copy(isGenerating = false, messages = s.messages.map { m ->
                        if (m.id == assistantId) m.copy(isGenerating = false) else m
                    }) }
                }
            }
        }
    }

    fun stopGeneration() { generationJob?.cancel(); inferenceEngine.stop(); _uiState.update { it.copy(isGenerating = false) } }
    fun toggleWebSearch() { _uiState.update { it.copy(webSearchEnabled = !it.webSearchEnabled) } }
    fun toggleThinking() { _uiState.update { it.copy(thinkingEnabled = !it.thinkingEnabled) } }
    fun updateConfig(config: GenerationConfig) { _uiState.update { it.copy(generationConfig = config) } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    override fun onCleared() { super.onCleared(); inferenceEngine.unload() }
}
