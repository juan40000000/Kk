package com.gangas.core.net

import com.gangas.core.model.Condition
import com.gangas.core.model.Listing
import com.gangas.core.model.Site
import com.gangas.core.model.Source
import com.gangas.core.text.TextTools
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Fallback cuando no hay credenciales de API: parsea el listado público.
 * Soporta el layout "poly-card" actual y el layout clásico "ui-search-result".
 */
class MeliWebScraper(private val client: OkHttpClient) {

    fun buildUrl(site: Site, query: String, offset: Int, sortPriceAsc: Boolean): String {
        val slug = TextTools.normalize(query).trim().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
        val sb = StringBuilder("https://${site.listingHost}/$slug")
        if (offset > 0) sb.append("_Desde_${offset + 1}")
        if (sortPriceAsc) sb.append("_OrderId_PRICE")
        sb.append("_NoIndex_True")
        return sb.toString()
    }

    suspend fun search(site: Site, query: String, offset: Int, sortPriceAsc: Boolean): List<Listing> {
        val req = Request.Builder().url(buildUrl(site, query, offset, sortPriceAsc))
            .header("User-Agent", Http.randomUserAgent())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", if (site == Site.BRASIL) "pt-BR,pt;q=0.9" else "es-AR,es;q=0.9")
            .build()
        return parse(Http.getText(client, req), site)
    }

    companion object {
        private val ID_REGEX = "(M[LCP][A-Z])-?(\\d{6,})".toRegex()

        fun parse(html: String, site: Site): List<Listing> {
            val doc = Jsoup.parse(html)
            val cards = doc.select("li.ui-search-layout__item, div.ui-search-result__wrapper, div.poly-card")
                .map { it.closestCard() }
                .distinct()
            return cards.mapNotNull { parseCard(it, site) }
        }

        private fun Element.closestCard(): Element =
            closest("li.ui-search-layout__item") ?: this

        private fun parseCard(card: Element, site: Site): Listing? {
            val link = card.selectFirst("a.poly-component__title, h3 a, h2 a, a.ui-search-link, a.ui-search-item__group__element, a[href]")
                ?: return null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val title = card.selectFirst(".poly-component__title, .ui-search-item__title, h2, h3")?.text()?.trim()
                ?.ifBlank { null } ?: link.text().trim()
            if (title.isBlank()) return null

            val id = extractId(href) ?: extractId(card.outerHtml().take(4000)) ?: return null

            val current = card.selectFirst(
                ".poly-price__current .andes-money-amount, .ui-search-price__second-line .andes-money-amount, .andes-money-amount--cents-superscript"
            ) ?: card.select(".andes-money-amount").firstOrNull { it.closest("s") == null && !it.hasClass("andes-money-amount--previous") }
            val price = current?.let { amount(it) } ?: return null

            val previous = card.selectFirst("s.andes-money-amount--previous, .andes-money-amount--previous, s .andes-money-amount")
                ?.let { amount(it) }

            val text = card.text().lowercase()
            val condition = when {
                text.contains("usado") -> Condition.USED
                text.contains("reacondicionado") || text.contains("recondicionado") -> Condition.USED
                else -> Condition.UNKNOWN
            }
            val img = card.selectFirst("img")
            val thumb = img?.let { i -> listOf("data-src", "src").map { i.absUrl(it).ifBlank { i.attr(it) } }.firstOrNull { it.startsWith("http") } }

            return Listing(
                id = id,
                title = title,
                price = price,
                originalPrice = previous?.takeIf { it > price },
                currency = site.currencySymbol,
                condition = condition,
                permalink = href.substringBefore("#"),
                thumbnail = thumb,
                freeShipping = text.contains("envío gratis") || text.contains("envio gratis") || text.contains("frete grátis"),
                officialStore = card.selectFirst(".poly-component__seller, .ui-search-official-store-label")?.text()
                    ?.let { it.contains("tienda oficial", true) || it.contains("loja oficial", true) || it.startsWith("Por ", true) } == true,
                source = Source.WEB,
            )
        }

        private fun extractId(s: String): String? =
            ID_REGEX.find(s)?.let { it.groupValues[1] + it.groupValues[2] }

        /** "1.234.567" + centavos "99" → 1234567.99 */
        private fun amount(el: Element): Double? {
            val frac = el.selectFirst(".andes-money-amount__fraction")?.text()
                ?: el.attr("aria-label").takeIf { it.isNotBlank() }
                ?: return null
            val digits = frac.filter(Char::isDigit)
            if (digits.isEmpty()) return null
            val cents = el.selectFirst(".andes-money-amount__cents")?.text()?.filter(Char::isDigit).orEmpty()
            return (digits + if (cents.isNotEmpty()) ".$cents" else "").toDoubleOrNull()
        }
    }
}
