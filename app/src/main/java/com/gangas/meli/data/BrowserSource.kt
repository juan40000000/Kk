package com.gangas.meli.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.gangas.core.model.Listing
import com.gangas.core.model.Site
import com.gangas.core.net.ListingSource
import com.gangas.core.net.MeliWebScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Carga el listado en un WebView invisible (motor Chrome real: ejecuta JS, guarda cookies,
 * pasa las verificaciones anti-bot) y luego parsea el HTML renderizado.
 */
class BrowserSource(private val context: Context, private val scraper: MeliWebScraper) : ListingSource {
    override val name = "navegador"
    override val concurrency = 1
    override val maxPages = 6

    override suspend fun search(site: Site, query: String, offset: Int, sortPriceAsc: Boolean): List<Listing> {
        val url = scraper.buildUrl(site, query, offset, sortPriceAsc)
        val html = withTimeout(35_000) { render(url) }
        val items = withContext(Dispatchers.Default) { MeliWebScraper.parse(html, site) }
        if (items.isEmpty() && !MeliWebScraper.isNoResultsPage(html)) {
            throw IOException("página sin publicaciones (\"${MeliWebScraper.pageTitle(html)}\")")
        }
        return items
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun render(url: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val wv = WebView(context.applicationContext)
            var finished = false
            fun finish(html: String) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                runCatching { wv.stopLoading(); wv.destroy() }
                if (cont.isActive) cont.resume(html)
            }
            fun grab() {
                wv.evaluateJavascript("document.documentElement.outerHTML") { raw ->
                    finish(runCatching { Json.decodeFromString<String>(raw) }.getOrDefault(""))
                }
            }
            var polls = 0
            fun poll() {
                if (finished) return
                // espera a que aparezcan precios o títulos (contenido renderizado por JS)
                wv.evaluateJavascript(
                    "(function(){return document.querySelectorAll('.andes-money-amount__fraction,.ui-search-rescue').length})()"
                ) { n ->
                    if ((n?.toIntOrNull() ?: 0) > 0 || ++polls >= 14) grab() else handler.postDelayed({ poll() }, 800)
                }
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = false
                blockNetworkImage = true
            }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, u: String) {
                    handler.removeCallbacksAndMessages(null)
                    polls = 0
                    handler.postDelayed({ poll() }, 600)
                }
            }
            cont.invokeOnCancellation { handler.post { finish("") } }
            wv.loadUrl(url)
        }
    }
}
