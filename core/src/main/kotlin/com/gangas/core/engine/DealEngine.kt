package com.gangas.core.engine

import com.gangas.core.model.Condition
import com.gangas.core.model.Deal
import com.gangas.core.model.EngineConfig
import com.gangas.core.model.Listing
import com.gangas.core.model.Risk
import com.gangas.core.model.Signal
import com.gangas.core.text.TextTools
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow

data class AnalysisResult(
    val deals: List<Deal>,
    val marketMedian: Double?,
    val analyzed: Int,
    val discardedIrrelevant: Int,
    val discardedAccessories: Int,
)

/**
 * Motor de detección de gangas / errores de publicación.
 *
 * Técnicas:
 *  1. Filtro de relevancia difuso (tokens normalizados, tolerancia a plurales y typos).
 *  2. Detección de accesorios por posición ("Funda ...", "... para iPhone") y de precios señuelo.
 *  3. Precio de referencia LOCAL por k vecinos más similares (Jaccard ponderado por IDF,
 *     tokens numéricos/modelos con más peso) → mediana ponderada por similitud.
 *  4. Dispersión robusta en escala logarítmica (MAD) → z-score modificado de Iglewicz–Hoaglin.
 *  5. Valla inferior de Tukey (Q1 − 1.5·IQR) en log-precios.
 *  6. Detector de errores de tipeo: precio ≈ referencia / 10ⁿ (faltan ceros / coma mal puesta).
 *  7. Brecha contra el vecino más barato, descuento publicado e historial de precios.
 *  8. Score 0–100 con penalizaciones por riesgo y confianza según tamaño de muestra.
 */
class DealEngine(private val config: EngineConfig = EngineConfig()) {

    private class Item(
        val listing: Listing,
        val norm: String,
        val tokens: List<String>,
        val tokenSet: Set<String>,
        val relevance: Double,
        val accessory: Boolean,
        val bait: Boolean,
        val risky: Boolean,
    ) {
        val logPrice = ln(listing.price)
    }

    fun analyze(
        query: String,
        listings: List<Listing>,
        history: Map<String, Double> = emptyMap(),
    ): AnalysisResult {
        val queryNorm = TextTools.normalize(query)
        val queryTokens = TextTools.tokens(query).distinct()
        val extraExclude = config.extraExcludeWords.map { it.trim() }.filter { it.isNotEmpty() }

        val unique = listings
            .filter { it.price > 0 && it.title.isNotBlank() }
            .groupBy { it.id }
            .map { (_, v) -> v.minBy { it.price } }

        var irrelevant = 0
        var accessories = 0
        val items = ArrayList<Item>()
        for (l in unique) {
            val norm = TextTools.normalize(l.title)
            val toks = TextTools.tokens(l.title)
            val rel = TextTools.relevance(queryTokens, toks)
            if (rel < config.minRelevance) { irrelevant++; continue }
            if (extraExclude.any { TextTools.containsPhrase(norm, it) }) { irrelevant++; continue }
            if (!config.includeUsed && l.condition == Condition.USED) continue
            val acc = isAccessory(norm, toks, queryNorm, queryTokens)
            if (acc) accessories++
            if (acc && !config.includeAccessories) continue
            items += Item(
                listing = l, norm = norm, tokens = toks, tokenSet = toks.toSet(), relevance = rel,
                accessory = acc,
                bait = Lexicon.BAIT.any { !TextTools.containsPhrase(queryNorm, it) && TextTools.containsPhrase(norm, it) },
                risky = Lexicon.RISKY.any { !TextTools.containsPhrase(queryNorm, it) && TextTools.containsPhrase(norm, it) },
            )
        }

        // Pool de referencia: sólo publicaciones "limpias".
        val pool = items.filter { !it.accessory && !it.bait && !it.risky }
        if (pool.size < 4) {
            return AnalysisResult(emptyList(), pool.takeIf { it.isNotEmpty() }?.let { p -> RobustStats.median(p.map { it.listing.price }) },
                items.size, irrelevant, accessories)
        }
        val idf = buildIdf(pool)
        val groups = pool.groupBy { it.listing.condition }

        val deals = items.map { scoreItem(it, poolFor(it, groups, pool), idf, history) }
            .filter { it.score >= config.minScore }
            .sortedWith(compareByDescending<Deal> { it.score }.thenByDescending { it.belowReference })

        return AnalysisResult(
            deals = deals,
            marketMedian = RobustStats.median(pool.map { it.listing.price }),
            analyzed = items.size,
            discardedIrrelevant = irrelevant,
            discardedAccessories = accessories,
        )
    }

    private fun poolFor(item: Item, groups: Map<Condition, List<Item>>, pool: List<Item>): List<Item> {
        val g = groups[item.listing.condition].orEmpty()
        return if (g.size >= 8) g else pool
    }

    private fun scoreItem(item: Item, group: List<Item>, idf: Map<String, Double>, history: Map<String, Double>): Deal {
        val others = group.filter { it.listing.id != item.listing.id }
        val logs = others.map { it.logPrice }
        val globalMed = RobustStats.median(logs)
        val mad = RobustStats.mad(logs, globalMed)

        // --- referencia local por vecinos más similares ---
        val neighbors = others
            .map { it to similarity(item.tokenSet, it.tokenSet, idf) }
            .filter { it.second >= config.minSimilarity }
            .sortedByDescending { it.second }
            .take(config.neighbors)
        val n = neighbors.size
        val localLog = if (n > 0) {
            ln(RobustStats.weightedMedian(neighbors.map { it.first.listing.price }, neighbors.map { it.second }))
        } else globalMed
        val w = n / (n + 3.0)                       // contracción hacia la mediana global
        val refLog = w * localLog + (1 - w) * globalMed
        val ref = exp(refLog)

        val price = item.listing.price
        val below = 1 - price / ref
        val z = RobustStats.modifiedZ(item.logPrice, refLog, mad)
        val outlier = item.logPrice < RobustStats.tukeyLowerFence(logs) && below > 0.2

        val ratio = ref / price
        val typo = below > 0.8 && (1..3).any { k -> abs(ln(ratio) - ln(10.0.pow(k))) < ln(1.4) }
        val placeholder = price <= 10.0 && ref >= 1000.0 && !typo
        val cheapestByMargin = n >= 3 && price < 0.75 * neighbors.minOf { it.first.listing.price }

        val listedDiscount = item.listing.originalPrice
            ?.takeIf { it > price }?.let { 1 - price / it } ?: 0.0
        val prev = history[item.listing.id]
        val priceDrop = prev != null && price <= prev * 0.85

        val signals = mutableListOf<Signal>()
        if (below >= 0.35) signals += Signal.FAR_BELOW_SIMILAR
        if (outlier || z <= -3.5) signals += Signal.STATISTICAL_OUTLIER
        if (typo) signals += Signal.POSSIBLE_TYPO
        if (cheapestByMargin) signals += Signal.CHEAPEST_BY_MARGIN
        if (listedDiscount >= 0.4) signals += Signal.BIG_LISTED_DISCOUNT
        if (priceDrop) signals += Signal.PRICE_DROP
        if (item.listing.officialStore) signals += Signal.OFFICIAL_STORE
        if (item.listing.freeShipping) signals += Signal.FREE_SHIPPING
        if (item.accessory) signals += Signal.ACCESSORY
        if (item.bait || placeholder) signals += Signal.BAIT_PRICE
        if (item.risky) signals += Signal.RISKY_TEXT
        if (below >= 0.8) signals += Signal.TOO_GOOD
        if (n < 3 || group.size < 10) signals += Signal.LOW_CONFIDENCE
        if (item.listing.condition == Condition.USED) signals += Signal.USED

        var s = 0.0
        if (below > 0) {
            s += 50 * clamp(below / 0.6)
            s += 15 * clamp(-z / 3.5)
            if (outlier) s += 10
            if (typo) s += 12
            if (cheapestByMargin) s += 6
            s += 4 * clamp(listedDiscount / 0.7)
            if (priceDrop) s += 5
            if (item.listing.officialStore) s += 2
        }
        s *= 0.5 + 0.5 * item.relevance
        val confidence = (0.4 + 0.6 * min(group.size, 30) / 30.0) * (if (n < 3) 0.85 else 1.0)
        s *= confidence
        if (item.bait || placeholder) s *= 0.25
        if (item.risky) s *= 0.4
        if (item.accessory) s *= 0.5
        if (item.listing.condition == Condition.USED) s *= 0.9

        val risk = when {
            item.bait || placeholder || item.risky -> Risk.HIGH
            below >= 0.8 && !item.listing.officialStore -> Risk.HIGH
            below >= 0.55 || n < 3 || item.listing.condition == Condition.USED -> Risk.MEDIUM
            else -> Risk.LOW
        }

        return Deal(
            listing = item.listing,
            score = s.coerceIn(0.0, 100.0).toInt(),
            referencePrice = ref,
            belowReference = below,
            robustZ = z,
            comparables = n,
            signals = signals,
            risk = risk,
            previousPrice = prev,
        )
    }

    private fun buildIdf(pool: List<Item>): Map<String, Double> {
        val df = HashMap<String, Int>()
        pool.forEach { it.tokenSet.forEach { t -> df[t] = (df[t] ?: 0) + 1 } }
        val nDocs = pool.size.toDouble()
        return df.mapValues { (t, d) ->
            val base = ln(1 + nDocs / d)
            if (t.any(Char::isDigit)) base * 1.5 else base  // modelos/capacidades discriminan más
        }
    }

    /** Jaccard ponderado por IDF. Tokens desconocidos pesan como los más raros. */
    private fun similarity(a: Set<String>, b: Set<String>, idf: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxW = idf.values.maxOrNull() ?: 1.0
        fun wt(t: String) = idf[t] ?: maxW
        var inter = 0.0
        var union = 0.0
        for (t in a) { val x = wt(t); union += x; if (t in b) inter += x }
        for (t in b) if (t !in a) union += wt(t)
        return if (union == 0.0) 0.0 else inter / union
    }

    private fun isAccessory(norm: String, toks: List<String>, queryNorm: String, queryTokens: List<String>): Boolean {
        for (w in Lexicon.ACCESSORY) {
            if (TextTools.containsPhrase(queryNorm, w)) continue
            if (w.contains(' ')) {
                if (TextTools.containsPhrase(norm, w)) return true
                continue
            }
            val idx = toks.indexOf(w)
            if (idx < 0) continue
            if (idx <= 1) return true
            if (toks.subList(idx + 1, min(toks.size, idx + 4)).any { it in Lexicon.ACCESSORY_PREFIXES }) return true
        }
        // "... para iphone 15", "compatible samsung"
        for (i in 0 until toks.size - 1) {
            val t = toks[i]
            if (t in Lexicon.ACCESSORY_PREFIXES && t !in queryTokens &&
                queryTokens.any { TextTools.tokenMatches(it, toks[i + 1]) }
            ) return true
        }
        return false
    }

    private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
}
