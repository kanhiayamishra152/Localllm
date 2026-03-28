package com.localllm.app.data.repository

import android.content.Context
import com.localllm.app.data.db.ModelInfo
import com.localllm.app.data.remote.HuggingFaceApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedMB: Long, val totalMB: Long) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
    data object Cancelled : DownloadState()
}

@Singleton
class ModelRepository @Inject constructor(
    private val context: Context,
    private val huggingFaceApi: HuggingFaceApi,
    private val client: OkHttpClient
) {
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    @Volatile
    private var isCancelled = false

    suspend fun searchModels(query: String): List<ModelInfo> {
        return huggingFaceApi.getGGUFModelsWithFiles(query)
    }

    suspend fun downloadModel(model: ModelInfo): Result<String> = withContext(Dispatchers.IO) {
        isCancelled = false
        val targetFile = File(modelsDir, model.fileName)

        if (targetFile.exists() && targetFile.length() == model.size) {
            _downloadState.value = DownloadState.Completed(targetFile.absolutePath)
            return@withContext Result.success(targetFile.absolutePath)
        }

        try {
            _downloadState.value = DownloadState.Downloading(0f, 0, model.size / (1024 * 1024))

            val request = Request.Builder()
                .url(model.downloadUrl)
                .header("User-Agent", "LocalLLM-Android/1.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = "Download failed: HTTP ${response.code}"
                _downloadState.value = DownloadState.Error(error)
                return@withContext Result.failure(Exception(error))
            }

            val body = response.body ?: run {
                _downloadState.value = DownloadState.Error("Empty response body")
                return@withContext Result.failure(Exception("Empty response"))
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            // Support resume
            val tempFile = File(modelsDir, "${model.fileName}.tmp")
            if (tempFile.exists()) {
                downloadedBytes = tempFile.length()
            }

            FileOutputStream(tempFile, tempFile.exists()).use { fos ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            _downloadState.value = DownloadState.Cancelled
                            return@withContext Result.failure(Exception("Cancelled"))
                        }

                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else 0f

                        _downloadState.value = DownloadState.Downloading(
                            progress = progress,
                            downloadedMB = downloadedBytes / (1024 * 1024),
                            totalMB = totalBytes / (1024 * 1024)
                        )
                    }
                }
            }

            tempFile.renameTo(targetFile)
            _downloadState.value = DownloadState.Completed(targetFile.absolutePath)
            Result.success(targetFile.absolutePath)

        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun cancelDownload() {
        isCancelled = true
    }

    fun getDownloadedModels(): List<File> {
        return modelsDir.listFiles()
            ?.filter { it.extension == "gguf" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return file.delete()
    }

    fun getModelPath(fileName: String): String {
        return File(modelsDir, fileName).absolutePath
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }
}
