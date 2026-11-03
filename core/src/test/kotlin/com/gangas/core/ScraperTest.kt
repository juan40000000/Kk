package com.gangas.core

import com.gangas.core.model.Condition
import com.gangas.core.model.Site
import com.gangas.core.net.MeliWebScraper
import kotlin.test.Test
import kotlin.test.assertEquals

class ScraperTest {
    private val html = """
        <ol>
        <li class="ui-search-layout__item"><div class="poly-card">
          <img data-src="https://http2.mlstatic.com/D_1.jpg">
          <h3 class="poly-component__title-wrapper"><a class="poly-component__title" href="https://articulo.mercadolibre.com.ar/MLA-1234567890-iphone-15-_JM#pos=1">Apple iPhone 15 (128 GB) - Negro</a></h3>
          <span class="poly-component__seller">Por Apple</span>
          <div class="poly-component__price">
            <s class="andes-money-amount andes-money-amount--previous"><span class="andes-money-amount__fraction">1.999.999</span></s>
            <div class="poly-price__current"><span class="andes-money-amount"><span class="andes-money-amount__fraction">1.499.999</span><span class="andes-money-amount__cents">50</span></span></div>
          </div>
          <div class="poly-component__shipping">Envío gratis</div>
        </div></li>
        <li class="ui-search-layout__item"><div class="poly-card">
          <h3><a class="poly-component__title" href="https://www.mercadolibre.com.ar/p/MLA27172677">iPhone 15 usado</a></h3>
          <span class="poly-component__item-condition">Usado</span>
          <div class="poly-price__current"><span class="andes-money-amount"><span class="andes-money-amount__fraction">900.000</span></span></div>
        </div></li>
        </ol>
    """.trimIndent()

    @Test fun parsesPolyCards() {
        val r = MeliWebScraper.parse(html, Site.ARGENTINA)
        assertEquals(2, r.size)
        assertEquals("MLA1234567890", r[0].id)
        assertEquals(1_499_999.50, r[0].price)
        assertEquals(1_999_999.0, r[0].originalPrice)
        assertEquals(true, r[0].freeShipping)
        assertEquals(true, r[0].officialStore)
        assertEquals("https://http2.mlstatic.com/D_1.jpg", r[0].thumbnail)
        assertEquals("MLA27172677", r[1].id)
        assertEquals(Condition.USED, r[1].condition)
    }

    @Test fun buildsUrl() {
        val s = MeliWebScraper(okhttp3.OkHttpClient())
        assertEquals("https://listado.mercadolibre.com.ar/iphone-15-pro_Desde_51_OrderId_PRICE_NoIndex_True",
            s.buildUrl(Site.ARGENTINA, "iPhone 15 Pró", 50, true))
    }

    @Test fun genericFallbackUnknownLayout() {
        val h = """
            <div class="grid"><div class="x-item"><a href="https://articulo.mercadolibre.com.ar/MLA-9876543210-ps5-slim">Consola PS5 Slim 1TB</a>
            <div><span class="andes-money-amount__fraction">850.000</span></div></div>
            <div class="x-item"><a href="https://articulo.mercadolibre.com.ar/MLA-9876543211-ps5">Sony PlayStation 5 Digital</a>
            <span class="andes-money-amount__fraction">799.999</span></div></div>
        """
        val r = MeliWebScraper.parse(h, Site.ARGENTINA)
        assertEquals(listOf("MLA9876543210", "MLA9876543211"), r.map { it.id })
        assertEquals(listOf(850_000.0, 799_999.0), r.map { it.price })
    }
}
