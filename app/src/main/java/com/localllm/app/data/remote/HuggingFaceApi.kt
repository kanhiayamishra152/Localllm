package com.localllm.app.data.remote

import com.localllm.app.data.db.ModelInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class HFModel(
    @SerialName("_id") val id: String? = null,
    @SerialName("id") val modelId: String,
    @SerialName("author") val author: String? = null,
    @SerialName("downloads") val downloads: Int = 0,
    @SerialName("likes") val likes: Int = 0,
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    @SerialName("lastModified") val lastModified: String? = null
)

@Serializable
data class HFFile(
    @SerialName("rfilename") val filename: String,
    @SerialName("size") val size: Long? = null
)

class HuggingFaceApi(private val client: OkHttpClient) {

    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val BASE_URL = "https://huggingface.co"
        private const val API_URL = "https://huggingface.co/api"
    }

    suspend fun searchModels(
        query: String,
        filter: String = "gguf",
        limit: Int = 30,
        sortBy: String = "downloads"
    ): List<HFModel> = withContext(Dispatchers.IO) {
        val url = "$API_URL/models".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("filter", filter)
            .addQueryParameter("sort", sortBy)
            .addQueryParameter("direction", "-1")
            .addQueryParameter("limit", limit.toString())
            .build()

        val request = Request.Builder().url(url).get().build()
        
        val response = suspendCoroutine<Response> { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) = cont.resume(response)
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            })
        }

        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("Search failed: ${resp.code}")
            val body = resp.body?.string() ?: "[]"
            json.decodeFromString<List<HFModel>>(body)
        }
    }

    suspend fun getModelFiles(modelId: String): List<HFFile> = withContext(Dispatchers.IO) {
        val url = "$API_URL/models/$modelId"
        val request = Request.Builder().url(url).get().build()

        val response = suspendCoroutine<Response> { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) = cont.resume(response)
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)
            })
        }

        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("Failed to get model info: ${resp.code}")
            val body = resp.body?.string() ?: "{}"
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val siblings = jsonObj["siblings"]?.jsonArray ?: return@use emptyList()
            
            siblings.mapNotNull { sibling ->
                try {
                    json.decodeFromJsonElement<HFFile>(sibling)
                } catch (e: Exception) {
                    null
                }
            }.filter { it.filename.endsWith(".gguf") }
        }
    }

    fun getDownloadUrl(modelId: String, fileName: String): String {
        return "$BASE_URL/$modelId/resolve/main/$fileName"
    }

    suspend fun getGGUFModelsWithFiles(
        query: String = "",
        limit: Int = 20
    ): List<ModelInfo> = withContext(Dispatchers.IO) {
        val models = searchModels(
            query = if (query.isBlank()) "gguf" else "$query gguf",
            limit = limit
        )

        models.flatMap { model ->
            try {
                val files = getModelFiles(model.modelId)
                files.map { file ->
                    val quantization = extractQuantization(file.filename)
                    val isVision = model.tags.any { tag ->
                        tag.contains("vision", ignoreCase = true) ||
                        tag.contains("vlm", ignoreCase = true) ||
                        tag.contains("image-text", ignoreCase = true)
                    } || model.pipelineTag?.contains("image", ignoreCase = true) == true

                    ModelInfo(
                        id = "${model.modelId}/${file.filename}",
                        name = model.modelId.substringAfterLast("/"),
                        author = model.author ?: model.modelId.substringBefore("/"),
                        fileName = file.filename,
                        size = file.size ?: 0L,
                        quantization = quantization,
                        isVision = isVision,
                        downloadUrl = getDownloadUrl(model.modelId, file.filename),
                        description = model.pipelineTag ?: "",
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
            "Q6_K", "Q8_0", "F16", "F32",
            "IQ1_S", "IQ1_M", "IQ2_XXS", "IQ2_XS", "IQ2_S", "IQ2_M",
            "IQ3_XXS", "IQ3_XS", "IQ3_S", "IQ3_M",
            "IQ4_XS", "IQ4_NL"
        )
        val upper = filename.uppercase()
        return patterns.firstOrNull { upper.contains(it) } ?: "Unknown"
    }
}
