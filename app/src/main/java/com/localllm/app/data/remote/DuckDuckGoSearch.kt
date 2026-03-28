package com.localllm.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

class DuckDuckGoSearch(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: String, maxResults: Int = 5): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                // Try DuckDuckGo HTML first (more reliable)
                searchViaHTML(query, maxResults)
            } catch (e: Exception) {
                try {
                    // Fallback to DuckDuckGo Lite
                    searchViaLite(query, maxResults)
                } catch (e2: Exception) {
                    emptyList()
                }
            }
        }

    private suspend fun searchViaHTML(query: String, maxResults: Int): List<SearchResult> {
        val url = "https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: return emptyList()

        val doc = Jsoup.parse(html)
        val results = mutableListOf<SearchResult>()

        doc.select(".result").take(maxResults).forEach { element ->
            val title = element.select(".result__title a").text()
            val link = element.select(".result__title a").attr("href")
            val snippet = element.select(".result__snippet").text()

            if (title.isNotBlank() && link.isNotBlank()) {
                // Extract actual URL from DuckDuckGo redirect
                val actualUrl = extractRealUrl(link)
                results.add(SearchResult(title, actualUrl, snippet))
            }
        }

        return results
    }

    private fun searchViaLite(query: String, maxResults: Int): List<SearchResult> {
        val url = "https://lite.duckduckgo.com/lite/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Android 14; Mobile)")
            .timeout(10000)
            .get()

        val results = mutableListOf<SearchResult>()
        val links = doc.select("a.result-link")
        val snippets = doc.select("td.result-snippet")

        for (i in 0 until minOf(links.size, snippets.size, maxResults)) {
            results.add(
                SearchResult(
                    title = links[i].text(),
                    url = links[i].attr("href"),
                    snippet = snippets[i].text()
                )
            )
        }
        return results
    }

    suspend fun scrapeContent(url: String, maxChars: Int = 2000): String =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Android 14; Mobile)")
                    .timeout(8000)
                    .get()

                // Remove scripts, styles, nav, footer
                doc.select("script, style, nav, footer, header, aside, .ad, .advertisement").remove()

                val text = doc.body()?.text() ?: ""
                text.take(maxChars)
            } catch (e: Exception) {
                ""
            }
        }

    suspend fun searchAndSummarize(query: String): String {
        val results = search(query, 3)
        if (results.isEmpty()) return "No web results found for: $query"

        val builder = StringBuilder()
        builder.appendLine("Web search results for: \"$query\"\n")

        results.forEachIndexed { index, result ->
            builder.appendLine("[${index + 1}] ${result.title}")
            builder.appendLine("    URL: ${result.url}")
            builder.appendLine("    ${result.snippet}")

            // Optionally scrape first result for more detail
            if (index == 0) {
                val content = scrapeContent(result.url, 1000)
                if (content.isNotBlank()) {
                    builder.appendLine("    Content: ${content.take(500)}...")
                }
            }
            builder.appendLine()
        }

        return builder.toString()
    }

    private fun extractRealUrl(ddgUrl: String): String {
        return try {
            if (ddgUrl.contains("uddg=")) {
                val encoded = ddgUrl.substringAfter("uddg=").substringBefore("&")
                java.net.URLDecoder.decode(encoded, "UTF-8")
            } else ddgUrl
        } catch (e: Exception) {
            ddgUrl
        }
    }
}
