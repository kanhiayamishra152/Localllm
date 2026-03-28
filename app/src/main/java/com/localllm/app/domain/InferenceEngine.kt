package com.localllm.app.domain

import com.localllm.llama.LlamaAndroid
import com.localllm.llama.LlamaGenerationParams
import kotlinx.coroutines.flow.Flow
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
    
    private val llama = LlamaAndroid()
    val isLoaded: Boolean get() = llama.isModelLoaded

    suspend fun loadModel(path: String, config: GenerationConfig): Result<Unit> {
        return llama.loadModel(
            path,
            LlamaGenerationParams(
                contextSize = config.contextSize,
                nThreads = config.threads
            )
        )
    }

    fun generateStream(prompt: String, config: GenerationConfig): Flow<String> {
        return llama.generateStream(
            prompt,
            LlamaGenerationParams(
                contextSize = config.contextSize,
                temperature = config.temperature,
                topK = config.topK,
                topP = config.topP,
                repeatPenalty = config.repeatPenalty,
                maxTokens = config.maxTokens,
                nThreads = config.threads
            )
        )
    }

    fun stop() {
        llama.stopGeneration()
    }

    fun unload() {
        llama.unloadModel()
    }

    fun buildPrompt(
        messages: List<Pair<String, String>>, // role to content
        systemPrompt: String? = null,
        webContext: String? = null,
        thinkingEnabled: Boolean = false
    ): String {
        val sb = StringBuilder()

        // ChatML format (widely compatible)
        val system = buildString {
            append(systemPrompt ?: "You are a helpful, honest, and concise AI assistant.")
            if (webContext != null) {
                append("\n\nThe following web search results are provided for context. Use them to answer the user's question accurately:\n")
                append(webContext)
            }
            if (thinkingEnabled) {
                append("\n\nPlease think step by step before providing your answer. Show your reasoning process within <think> tags, then provide your final answer.")
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
