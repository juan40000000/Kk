package com.gangas.core.net

import com.gangas.core.model.Listing
import com.gangas.core.model.Site
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class FetchMode { AUTO, API_ONLY, WEB_ONLY, BROWSER_ONLY }

data class FetchResult(val listings: List<Listing>, val sourceName: String, val errors: List<String>) {
    val usedApi get() = sourceName == "API"
}

/** Una forma de obtener una página de resultados. */
interface ListingSource {
    val name: String
    /** Pedidos simultáneos permitidos. */
    val concurrency: Int
    /** Máximo de páginas (ascendente + relevancia) que vale la pena pedir. */
    val maxPages: Int
    suspend fun search(site: Site, query: String, offset: Int, sortPriceAsc: Boolean): List<Listing>
}

class ApiSource(private val api: MeliApiClient) : ListingSource {
    override val name = "API"
    override val concurrency = 4
    override val maxPages = 20
    override suspend fun search(site: Site, query: String, offset: Int, sortPriceAsc: Boolean) =
        api.search(site, query, offset, 50, sortPriceAsc)
}

class HttpWebSource(private val web: MeliWebScraper) : ListingSource {
    override val name = "web"
    override val concurrency = 3
    override val maxPages = 20
    override suspend fun search(site: Site, query: String, offset: Int, sortPriceAsc: Boolean) =
        web.search(site, query, offset, sortPriceAsc)
}

/**
 * Descarga una muestra representativa del mercado combinando:
 *  - páginas ordenadas por precio ascendente (donde aparecen los errores), y
 *  - páginas por relevancia (para estimar el precio "normal" sin sesgo).
 * Prueba las fuentes en cascada: API (si hay credenciales) → HTML directo → navegador embebido.
 */
class MeliRepository(
    private val api: ListingSource,
    private val web: ListingSource,
    private val browser: ListingSource?,
    private val hasCredentials: () -> Boolean,
) {
    suspend fun fetch(site: Site, query: String, pages: Int, mode: FetchMode = FetchMode.AUTO): FetchResult {
        val chain = when (mode) {
            FetchMode.API_ONLY -> listOf(api)
            FetchMode.WEB_ONLY -> listOfNotNull(web, browser)
            FetchMode.BROWSER_ONLY -> listOfNotNull(browser ?: web)
            FetchMode.AUTO -> listOfNotNull(api.takeIf { hasCredentials() }, web, browser)
        }
        val errors = mutableListOf<String>()
        for (src in chain) {
            val r = runPages(src, site, query, pages, errors)
            if (r.isNotEmpty()) return FetchResult(r, src.name, errors)
        }
        return FetchResult(emptyList(), chain.lastOrNull()?.name ?: "-", errors)
    }

    private suspend fun runPages(
        src: ListingSource, site: Site, query: String, pages: Int, errors: MutableList<String>,
    ): List<Listing> = coroutineScope {
        val sem = Semaphore(src.concurrency)
        val pagesAsc = pages.coerceIn(1, 20)
        val pagesRel = (pages / 2).coerceIn(1, 10)
        val jobs = ((0 until pagesAsc).map { it * 50 to true } + (0 until pagesRel).map { it * 50 to false })
            .take(src.maxPages)
        val results = jobs.map { (offset, asc) ->
            async {
                sem.withPermit {
                    runCatching { src.search(site, query, offset, asc) }
                        .onFailure { synchronized(errors) { errors += "${src.name}: ${it.message ?: it.javaClass.simpleName}" } }
                        .getOrDefault(emptyList())
                }
            }
        }.map { it.await() }
        results.flatten().distinctBy { it.id }
    }
}
