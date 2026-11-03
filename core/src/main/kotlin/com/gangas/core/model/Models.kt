package com.gangas.core.model

import kotlinx.serialization.Serializable

/** Sitios de Mercado Libre soportados (id de API + dominio del listado web). */
enum class Site(val id: String, val label: String, val listingHost: String, val currencySymbol: String) {
    ARGENTINA("MLA", "Argentina", "listado.mercadolibre.com.ar", "$"),
    MEXICO("MLM", "México", "listado.mercadolibre.com.mx", "$"),
    BRASIL("MLB", "Brasil", "lista.mercadolivre.com.br", "R$"),
    CHILE("MLC", "Chile", "listado.mercadolibre.cl", "$"),
    COLOMBIA("MCO", "Colombia", "listado.mercadolibre.com.co", "$"),
    URUGUAY("MLU", "Uruguay", "listado.mercadolibre.com.uy", "$"),
    PERU("MPE", "Perú", "listado.mercadolibre.com.pe", "S/");

    companion object {
        fun fromId(id: String?): Site = entries.firstOrNull { it.id == id } ?: ARGENTINA
    }
}

enum class Condition { NEW, USED, UNKNOWN }

enum class Source { API, WEB }

@Serializable
data class Listing(
    val id: String,
    val title: String,
    val price: Double,
    val originalPrice: Double? = null,
    val currency: String = "",
    val condition: Condition = Condition.UNKNOWN,
    val permalink: String = "",
    val thumbnail: String? = null,
    val freeShipping: Boolean = false,
    val sellerId: Long? = null,
    val officialStore: Boolean = false,
    val source: Source = Source.WEB,
)

/** Señales que explican por qué algo es (o no) una ganga. */
enum class Signal(val label: String, val positive: Boolean) {
    FAR_BELOW_SIMILAR("Muy debajo de similares", true),
    STATISTICAL_OUTLIER("Outlier estadístico", true),
    POSSIBLE_TYPO("Posible error de tipeo (x10/x100/x1000)", true),
    CHEAPEST_BY_MARGIN("El más barato por amplio margen", true),
    BIG_LISTED_DISCOUNT("Descuento publicado grande", true),
    PRICE_DROP("Bajó de precio desde el último escaneo", true),
    OFFICIAL_STORE("Tienda oficial", true),
    FREE_SHIPPING("Envío gratis", true),
    ACCESSORY("Parece accesorio/repuesto", false),
    BAIT_PRICE("Precio señuelo (consultar/seña/cuota)", false),
    RISKY_TEXT("Texto sospechoso (réplica/roto/bloqueado)", false),
    TOO_GOOD("Demasiado bueno: verificar vendedor", false),
    LOW_CONFIDENCE("Pocas referencias comparables", false),
    USED("Usado", false),
}

enum class Risk { LOW, MEDIUM, HIGH }

@Serializable
data class Deal(
    val listing: Listing,
    /** 0..100 */
    val score: Int,
    /** Precio de referencia de mercado estimado para este ítem. */
    val referencePrice: Double,
    /** Fracción por debajo de la referencia (0.4 = 40% más barato). */
    val belowReference: Double,
    val robustZ: Double,
    val comparables: Int,
    val signals: List<Signal>,
    val risk: Risk,
    val previousPrice: Double? = null,
)

data class EngineConfig(
    val minRelevance: Double = 0.5,
    val includeAccessories: Boolean = false,
    val includeUsed: Boolean = true,
    val extraExcludeWords: List<String> = emptyList(),
    val neighbors: Int = 12,
    val minSimilarity: Double = 0.30,
    val minScore: Int = 0,
)
