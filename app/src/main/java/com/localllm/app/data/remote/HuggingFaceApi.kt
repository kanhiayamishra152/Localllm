package com.localllm.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class HFModel(
    @SerialName("id") val modelId: String = "",
    @SerialName("author") val author: String? = null,
    @SerialName("downloads") val downloads: Int = 0,
    @SerialName("likes") val likes: Int = 0,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("pipeline_tag") val pipelineTag: String? = null
)

@Serializable
data class HFFile(
    @SerialName("rfilename") val filename: String = "",
    @SerialName("size") val size: Long? = null
)

@Serializable
data class HFModelDetail(
    @SerialName("id") val modelId: String = "",
    @SerialName("siblings") val siblings: List<HFFile> = emptyList()
)

data class ModelInfo(
    val id: String,
    val name: String,
    val author: String,
    val fileName: String,
    val size: Long,
    val quantization: String,
    val isVision: Boolean = false,
    val downloadUrl: String,
    val downloads: Int = 0,
    val likes: Int = 0
)

class HuggingFaceApi(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchModels(query: String, limit: Int = 20): List<HFModel> =
        withContext(Dispatchers.IO) {
            try {
                val searchTerm = if (query.isBlank()) "gguf" else "$query gguf"
                val url = "https://huggingface.co/api/models?search=${
                    java.net.URLEncoder.encode(searchTerm, "UTF-8")
                }&filter=gguf&sort=downloads&direction=-1&limit=$limit"

                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "[]"
                json.decodeFromString<List<HFModel>>(body)
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun getModelFiles(modelId: String): List<HFFile> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://huggingface.co/api/models/$modelId"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val detail = json.decodeFromString<HFModelDetail>(body)
                detail.siblings.filter { it.filename.endsWith(".gguf") }
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun getDownloadUrl(modelId: String, fileName: String): String {
        return "https://huggingface.co/$modelId/resolve/main/$fileName"
    }

    suspend fun getGGUFModels(query: String = ""): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val models = searchModels(query)
            models.flatMap { model ->
                try {
                    val files = getModelFiles(model.modelId)
                    files.map { file ->
                        ModelInfo(
                            id = "${model.modelId}/${file.filename}",
                            name = model.modelId.substringAfterLast("/"),
                            author = model.author ?: model.modelId.substringBefore("/"),
                            fileName = file.filename,
                            size = file.size ?: 0L,
                            quantization = extractQuantization(file.filename),
                            isVision = model.tags.any {
                                it.contains("vision", true) || it.contains("vlm", true)
                            },
                            downloadUrl = getDownloadUrl(model.modelId, file.filename),
                            downloads = model.downloads,
                            likes = model.likes
                        )
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    private fun extractQuantization(filename: String): String {
        val patterns = listOf(
            "Q2_K", "Q3_K_S", "Q3_K_M", "Q3_K_L",
            "Q4_0", "Q4_1", "Q4_K_S", "Q4_K_M",
            "Q5_0", "Q5_1", "Q5_K_S", "Q5_K_M",
            "Q6_K", "Q8_0", "F16", "F32"
        )
        val upper = filename.uppercase()
        return patterns.firstOrNull { upper.contains(it) } ?: "Unknown"
    }
}
