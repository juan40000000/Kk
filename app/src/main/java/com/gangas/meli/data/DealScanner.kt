package com.gangas.meli.data

import com.gangas.core.engine.AnalysisResult
import com.gangas.core.engine.DealEngine
import com.gangas.core.model.Site
import com.gangas.core.net.FetchMode
import com.gangas.core.net.MeliRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanOutcome(val analysis: AnalysisResult, val fetched: Int, val usedApi: Boolean, val errors: List<String>)

class DealScanner(
    private val repository: MeliRepository,
    private val history: HistoryStore,
    private val engine: () -> DealEngine,
) {
    suspend fun scan(site: Site, query: String, pages: Int, mode: FetchMode): ScanOutcome {
        val fetch = repository.fetch(site, query, pages, mode)
        val analysis = withContext(Dispatchers.Default) {
            engine().analyze(query, fetch.listings, history.prices())
        }
        withContext(Dispatchers.IO) { history.record(fetch.listings.associate { it.id to it.price }) }
        return ScanOutcome(analysis, fetch.listings.size, fetch.usedApi, fetch.errors)
    }
}
