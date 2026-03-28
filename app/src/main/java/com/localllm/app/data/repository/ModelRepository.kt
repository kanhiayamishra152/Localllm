package com.localllm.app.data.repository

import android.content.Context
import com.localllm.app.data.remote.HuggingFaceApi
import com.localllm.app.data.remote.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Volatile private var isCancelled = false

    suspend fun searchModels(query: String): List<ModelInfo> = huggingFaceApi.getGGUFModels(query)

    suspend fun downloadModel(model: ModelInfo): Result<String> = withContext(Dispatchers.IO) {
        isCancelled = false
        val targetFile = File(modelsDir, model.fileName)
        if (targetFile.exists() && targetFile.length() == model.size) {
            _downloadState.value = DownloadState.Completed(targetFile.absolutePath)
            return@withContext Result.success(targetFile.absolutePath)
        }
        try {
            _downloadState.value = DownloadState.Downloading(0f, 0, model.size / (1024 * 1024))
            val request = Request.Builder().url(model.downloadUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _downloadState.value = DownloadState.Error("HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val body = response.body ?: run {
                _downloadState.value = DownloadState.Error("Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }
            val totalBytes = body.contentLength()
            var downloaded = 0L
            FileOutputStream(targetFile).use { fos ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            targetFile.delete()
                            _downloadState.value = DownloadState.Idle
                            return@withContext Result.failure(Exception("Cancelled"))
                        }
                        fos.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                        _downloadState.value = DownloadState.Downloading(progress, downloaded / (1024 * 1024), totalBytes / (1024 * 1024))
                    }
                }
            }
            _downloadState.value = DownloadState.Completed(targetFile.absolutePath)
            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun cancelDownload() { isCancelled = true }
    fun getDownloadedModels(): List<File> = modelsDir.listFiles()?.filter { it.extension == "gguf" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    fun deleteModel(fileName: String): Boolean = File(modelsDir, fileName).delete()
    fun resetDownloadState() { _downloadState.value = DownloadState.Idle }
}
