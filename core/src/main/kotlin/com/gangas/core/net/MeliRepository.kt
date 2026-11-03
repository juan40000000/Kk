package com.gangas.core.net

import com.gangas.core.model.Listing
import com.gangas.core.model.Site
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class FetchMode { AUTO, API_ONLY, WEB_ONLY }

data class FetchResult(val listings: List<Listing>, val usedApi: Boolean, val errors: List<String>)

/**
 * Descarga una muestra representativa del mercado combinando:
 *  - páginas ordenadas por precio ascendente (donde aparecen los errores), y
 *  - páginas por relevancia (para estimar el precio "normal" sin sesgo).
 * Las páginas se piden en paralelo con un semáforo para no saturar el servidor.
 */
class MeliRepository(
    private val api: MeliApiClient,
    private val web: MeliWebScraper,
    private val hasCredentials: () -> Boolean,
    private val concurrency: Int = 3,
) {
    suspend fun fetch(site: Site, query: String, pages: Int, mode: FetchMode = FetchMode.AUTO): FetchResult {
        val useApi = when (mode) {
            FetchMode.API_ONLY -> true
            FetchMode.WEB_ONLY -> false
            FetchMode.AUTO -> hasCredentials()
        }
        val errors = mutableListOf<String>()
        if (useApi) {
            val r = runPages(pages, errors) { offset, asc -> api.search(site, query, offset, 50, asc) }
            if (r.isNotEmpty() || mode == FetchMode.API_ONLY) return FetchResult(r, true, errors)
        }
        val r = runPages(pages, errors) { offset, asc -> web.search(site, query, offset, asc) }
        if (r.isEmpty() && !useApi && mode == FetchMode.AUTO) {
            // último intento: API sin token (algunos sitios todavía la permiten)
            val apiR = runPages(1, errors) { offset, asc -> api.search(site, query, offset, 50, asc) }
            if (apiR.isNotEmpty()) return FetchResult(apiR, true, errors)
        }
        return FetchResult(r, false, errors)
    }

    private suspend fun runPages(
        pages: Int,
        errors: MutableList<String>,
        call: suspend (offset: Int, priceAsc: Boolean) -> List<Listing>,
    ): List<Listing> = coroutineScope {
        val sem = Semaphore(concurrency)
        val pagesAsc = pages.coerceIn(1, 20)
        val pagesRel = (pages / 2).coerceIn(1, 10)
        val jobs = (0 until pagesAsc).map { p -> p * 50 to true } + (0 until pagesRel).map { p -> p * 50 to false }
        jobs.map { (offset, asc) ->
            async {
                sem.withPermit {
                    runCatching { call(offset, asc) }
                        .onFailure { synchronized(errors) { errors += (it.message ?: it.javaClass.simpleName) } }
                        .getOrDefault(emptyList())
                }
            }
        }.flatMap { it.await() }.distinctBy { it.id }
    }
}
