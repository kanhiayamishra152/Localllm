package com.localllm.app.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class GenerationConfig(
    val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 1024,
    val threads: Int = 4
)

@Singleton
class InferenceEngine @Inject constructor() {

    private var loadedModelPath: String? = null
    private var _isLoaded = false
    val isLoaded: Boolean get() = _isLoaded

    suspend fun loadModel(path: String, config: GenerationConfig): Result<Unit> {
        return try {
            // TODO: Replace with actual llama.cpp JNI call
            // For now this is a mock so the app compiles and runs
            loadedModelPath = path
            _isLoaded = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateStream(prompt: String, config: GenerationConfig): Flow<String> = flow {
        // TODO: Replace with actual llama.cpp JNI streaming
        // Mock response for testing UI
        val mockResponse = "I'm running locally on your device! This is a mock response. " +
                "Once llama.cpp native library is integrated, I'll generate real responses " +
                "using the loaded GGUF model. The UI is fully functional - you can browse " +
                "models on HuggingFace, download them, and chat."
        for (word in mockResponse.split(" ")) {
            emit("$word ")
            delay(50)
        }
    }

    fun stop() {
        // TODO: Stop native generation
    }

    fun unload() {
        loadedModelPath = null
        _isLoaded = false
    }

    fun buildPrompt(
        messages: List<Pair<String, String>>,
        systemPrompt: String? = null,
        webContext: String? = null,
        thinkingEnabled: Boolean = false
    ): String {
        val sb = StringBuilder()
        val system = buildString {
            append(systemPrompt ?: "You are a helpful AI assistant.")
            if (webContext != null) {
                append("\n\nWeb search results:\n$webContext")
            }
            if (thinkingEnabled) {
                append("\n\nThink step by step in <think> tags, then give your answer.")
            }
        }
        sb.append("<|im_start|>system\n$system<|im_end|>\n")
        for ((role, content) in messages) {
            sb.append("<|im_start|>$role\n$content<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }
}
