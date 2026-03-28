package com.localllm.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

class DuckDuckGoSearch(private val client: OkHttpClient) {

    suspend fun search(query: String, maxResults: Int = 5): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://html.duckduckgo.com/html/?q=${
                    java.net.URLEncoder.encode(query, "UTF-8")
                }"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                    .build()
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext emptyList()
                val doc = Jsoup.parse(html)
                doc.select(".result").take(maxResults).mapNotNull { element ->
                    val title = element.select(".result__title a").text()
                    val link = element.select(".result__title a").attr("href")
                    val snippet = element.select(".result__snippet").text()
                    if (title.isNotBlank() && link.isNotBlank()) {
                        SearchResult(title, link, snippet)
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    suspend fun searchAndSummarize(query: String): String {
        val results = search(query, 3)
        if (results.isEmpty()) return "No web results found for: $query"
        return buildString {
            appendLine("Web search results for \"$query\":\n")
            results.forEachIndexed { i, r ->
                appendLine("[${i + 1}] ${r.title}")
                appendLine("    ${r.snippet}")
                appendLine()
            }
        }
    }
}
