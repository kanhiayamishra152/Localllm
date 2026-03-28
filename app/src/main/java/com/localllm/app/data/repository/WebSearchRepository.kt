package com.localllm.app.data.repository

import com.localllm.app.data.remote.DuckDuckGoSearch
import com.localllm.app.data.remote.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSearchRepository @Inject constructor(
    private val duckDuckGoSearch: DuckDuckGoSearch
) {
    suspend fun search(query: String): List<SearchResult> {
        return duckDuckGoSearch.search(query)
    }

    suspend fun searchAndSummarize(query: String): String {
        return duckDuckGoSearch.searchAndSummarize(query)
    }
}
