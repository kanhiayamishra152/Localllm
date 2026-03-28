package com.localllm.app.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localllm.app.data.db.ConversationEntity
import com.localllm.app.data.db.MessageEntity
import com.localllm.app.data.remote.DuckDuckGoSearch
import com.localllm.app.data.repository.ChatRepository
import com.localllm.app.data.repository.ModelRepository
import com.localllm.app.domain.GenerationConfig
import com.localllm.app.domain.HardwareProfiler
import com.localllm.app.domain.InferenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
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
    private val webSearch: DuckDuckGoSearch,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private val initialConversationId: String? = savedStateHandle["conversationId"]

    init {
        loadConversations()
        loadDownloadedModels()
        if (initialConversationId != null) {
            selectConversation(initialConversationId)
        }
    }

    private fun loadConversations() {
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { convos ->
                _uiState.update { it.copy(conversations = convos) }
            }
        }
    }

    fun loadDownloadedModels() {
        _uiState.update { it.copy(downloadedModels = modelRepository.getDownloadedModels()) }
    }

    fun loadModel(modelFile: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, errorMessage = null) }

            val profile = hardwareProfiler.analyzeModelCompatibility(
                modelFile.length() / (1024 * 1024)
            )

            val config = GenerationConfig(
                contextSize = profile.contextWindow,
                temperature = profile.temperature,
                topK = profile.topK,
                topP = profile.topP,
                repeatPenalty = profile.repeatPenalty,
                maxTokens = profile.maxTokens,
                threads = profile.threads
            )

            val result = inferenceEngine.loadModel(modelFile.absolutePath, config)

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isModelLoaded = true,
                            isLoadingModel = false,
                            loadedModelName = modelFile.nameWithoutExtension,
                            generationConfig = config
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            errorMessage = "Failed to load model: ${error.message}"
                        )
                    }
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

    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentConversationId = conversationId) }
            chatRepository.getMessages(conversationId).collect { msgs ->
                _uiState.update {
                    it.copy(
                        messages = msgs.map { m ->
                            ChatMessage(
                                id = m.id,
                                role = m.role,
                                content = m.content,
                                thinkingContent = m.thinkingContent,
                                isWebSearchResult = m.isWebSearchResult
                            )
                        }
                    )
                }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            if (_uiState.value.currentConversationId == conversationId) {
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
            // Ensure conversation exists
            val conversationId = _uiState.value.currentConversationId ?: run {
                val id = chatRepository.createConversation(
                    title = content.take(50)
                )
                _uiState.update { it.copy(currentConversationId = id) }
                id
            }

            // Add user message
            chatRepository.addMessage(conversationId, "user", content)

            val userMsg = ChatMessage(
                id = System.currentTimeMillis().toString(),
                role = "user",
                content = content
            )
            _uiState.update { it.copy(messages = it.messages + userMsg) }

            // Web search if enabled
            var webContext: String? = null
            if (_uiState.value.webSearchEnabled) {
                try {
                    webContext = webSearch.searchAndSummarize(content)
                } catch (e: Exception) {
                    // Silent fail for web search
                }
            }

            // Start generation
            _uiState.update { it.copy(isGenerating = true) }

            val allMessages = _uiState.value.messages.map { it.role to it.content }
            val prompt = inferenceEngine.buildPrompt(
                messages = allMessages,
                webContext = webContext,
                thinkingEnabled = _uiState.value.thinkingEnabled
            )

            val assistantMsgId = System.currentTimeMillis().toString() + "_assistant"
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                role = "assistant",
                content = "",
                isGenerating = true
            )
            _uiState.update { it.copy(messages = it.messages + assistantMsg) }

            val responseBuilder = StringBuilder()
            var thinkingContent: String? = null
            var finalContent = ""

            generationJob = launch {
                try {
                    inferenceEngine.generateStream(prompt, _uiState.value.generationConfig)
                        .collect { token ->
                            responseBuilder.append(token)
                            val fullText = responseBuilder.toString()

                            // Parse thinking tags
                            if (_uiState.value.thinkingEnabled && fullText.contains("<think>")) {
                                val thinkEnd = fullText.indexOf("</think>")
                                if (thinkEnd != -1) {
                                    thinkingContent = fullText.substringAfter("<think>")
                                        .substringBefore("</think>").trim()
                                    finalContent = fullText.substringAfter("</think>").trim()
                                } else {
                                    thinkingContent = fullText.substringAfter("<think>").trim()
                                    finalContent = ""
                                }
                            } else {
                                finalContent = fullText
                            }

                            _uiState.update { state ->
                                state.copy(
                                    messages = state.messages.map { msg ->
                                        if (msg.id == assistantMsgId) {
                                            msg.copy(
                                                content = finalContent,
                                                thinkingContent = thinkingContent,
                                                isGenerating = true
                                            )
                                        } else msg
                                    }
                                )
                            }
                        }
                } catch (e: Exception) {
                    if (responseBuilder.isEmpty()) {
                        _uiState.update {
                            it.copy(errorMessage = "Generation error: ${e.message}")
                        }
                    }
                } finally {
                    // Save final message
                    chatRepository.addMessage(
                        conversationId,
                        "assistant",
                        finalContent,
                        thinkingContent,
                        webContext != null
                    )

                    _uiState.update { state ->
                        state.copy(
                            isGenerating = false,
                            messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(isGenerating = false)
                                } else msg
                            }
                        )
                    }
                }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        inferenceEngine.stop()
        _uiState.update { it.copy(isGenerating = false) }
    }

    fun toggleWebSearch() {
        _uiState.update { it.copy(webSearchEnabled = !it.webSearchEnabled) }
    }

    fun toggleThinking() {
        _uiState.update { it.copy(thinkingEnabled = !it.thinkingEnabled) }
    }

    fun updateConfig(config: GenerationConfig) {
        _uiState.update { it.copy(generationConfig = config) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun unloadModel() {
        inferenceEngine.unload()
        _uiState.update { it.copy(isModelLoaded = false, loadedModelName = null) }
    }

    override fun onCleared() {
        super.onCleared()
        inferenceEngine.unload()
    }
}
