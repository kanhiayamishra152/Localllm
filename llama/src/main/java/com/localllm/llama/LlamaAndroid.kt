package com.localllm.llama

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

data class LlamaGenerationParams(
    val contextSize: Int = 2048,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.9f,
    val repeatPenalty: Float = 1.1f,
    val nGpuLayers: Int = 0,
    val nThreads: Int = 4,
    val maxTokens: Int = 1024
)

class LlamaAndroid {
    
    companion object {
        private const val TAG = "LlamaAndroid"
        
        init {
            try {
                System.loadLibrary("llama-android")
                Log.d(TAG, "llama-android library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load llama-android library", e)
            }
        }
    }

    private var modelPtr: Long = 0L
    private var contextPtr: Long = 0L
    private var isLoaded = false
    private var generationJob: Job? = null

    val isModelLoaded: Boolean get() = isLoaded

    suspend fun loadModel(
        modelPath: String,
        params: LlamaGenerationParams = LlamaGenerationParams()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isLoaded) {
                unloadModel()
            }
            
            modelPtr = nativeLoadModel(
                modelPath,
                params.contextSize,
                params.nGpuLayers,
                params.nThreads
            )
            
            if (modelPtr == 0L) {
                return@withContext Result.failure(RuntimeException("Failed to load model from: $modelPath"))
            }

            contextPtr = nativeCreateContext(modelPtr, params.contextSize, params.nThreads)
            
            if (contextPtr == 0L) {
                nativeFreeModel(modelPtr)
                modelPtr = 0L
                return@withContext Result.failure(RuntimeException("Failed to create context"))
            }
            
            isLoaded = true
            Log.d(TAG, "Model loaded successfully: $modelPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(e)
        }
    }

    fun generateStream(
        prompt: String,
        params: LlamaGenerationParams = LlamaGenerationParams()
    ): Flow<String> = callbackFlow {
        if (!isLoaded || contextPtr == 0L) {
            close(RuntimeException("Model not loaded"))
            return@callbackFlow
        }

        val job = launch(Dispatchers.IO) {
            try {
                nativeGenerateStream(
                    contextPtr,
                    prompt,
                    params.maxTokens,
                    params.temperature,
                    params.topK,
                    params.topP,
                    params.repeatPenalty
                ) { token ->
                    if (!isClosedForSend) {
                        trySend(token)
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }

        generationJob = job
        
        awaitClose {
            job.cancel()
            nativeStopGeneration(contextPtr)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generate(
        prompt: String,
        params: LlamaGenerationParams = LlamaGenerationParams()
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isLoaded || contextPtr == 0L) {
            return@withContext Result.failure(RuntimeException("Model not loaded"))
        }
        
        try {
            val result = nativeGenerate(
                contextPtr,
                prompt,
                params.maxTokens,
                params.temperature,
                params.topK,
                params.topP,
                params.repeatPenalty
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        if (contextPtr != 0L) {
            nativeStopGeneration(contextPtr)
        }
    }

    fun unloadModel() {
        stopGeneration()
        if (contextPtr != 0L) {
            nativeFreeContext(contextPtr)
            contextPtr = 0L
        }
        if (modelPtr != 0L) {
            nativeFreeModel(modelPtr)
            modelPtr = 0L
        }
        isLoaded = false
    }

    fun getModelInfo(): Map<String, String> {
        if (!isLoaded || modelPtr == 0L) return emptyMap()
        return mapOf(
            "params" to nativeGetModelParamCount(modelPtr).toString(),
            "contextLength" to nativeGetModelContextLength(modelPtr).toString(),
            "embeddingLength" to nativeGetModelEmbeddingLength(modelPtr).toString()
        )
    }

    protected fun finalize() {
        unloadModel()
    }

    // Native methods
    private external fun nativeLoadModel(
        path: String,
        contextSize: Int,
        nGpuLayers: Int,
        nThreads: Int
    ): Long

    private external fun nativeCreateContext(
        modelPtr: Long,
        contextSize: Int,
        nThreads: Int
    ): Long

    private external fun nativeGenerate(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float
    ): String

    private external fun nativeGenerateStream(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        repeatPenalty: Float,
        callback: (String) -> Unit
    )

    private external fun nativeStopGeneration(contextPtr: Long)
    private external fun nativeFreeContext(contextPtr: Long)
    private external fun nativeFreeModel(modelPtr: Long)
    private external fun nativeGetModelParamCount(modelPtr: Long): Long
    private external fun nativeGetModelContextLength(modelPtr: Long): Int
    private external fun nativeGetModelEmbeddingLength(modelPtr: Long): Int
}
