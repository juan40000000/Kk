package com.gangas.core

import com.gangas.core.engine.DealEngine
import com.gangas.core.engine.RobustStats
import com.gangas.core.model.Condition
import com.gangas.core.model.Listing
import com.gangas.core.model.Risk
import com.gangas.core.model.Signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DealEngineTest {
    private fun market(seed: Int = 1): MutableList<Listing> {
        val rnd = Random(seed)
        val out = mutableListOf<Listing>()
        repeat(40) { i ->
            out += Listing("MLA1$i", "Apple iPhone 15 128gb Negro", 1_200_000.0 * (0.9 + rnd.nextDouble() * 0.25), condition = Condition.NEW)
        }
        repeat(30) { i ->
            out += Listing("MLA2$i", "Apple iPhone 15 Pro Max 256gb Titanio", 2_200_000.0 * (0.9 + rnd.nextDouble() * 0.25), condition = Condition.NEW)
        }
        return out
    }

    @Test fun detectsTypoPriceError() {
        val l = market()
        l += Listing("MLA999", "Apple iPhone 15 128gb Negro", 120_000.0, condition = Condition.NEW)
        val r = DealEngine().analyze("iphone 15", l)
        val top = r.deals.first()
        assertEquals("MLA999", top.listing.id)
        assertTrue(Signal.POSSIBLE_TYPO in top.signals, "${top.signals}")
        assertTrue(top.score >= 70, "score ${top.score}")
    }

    @Test fun usesLocalReferenceNotGlobal() {
        // Un Pro Max a precio de iPhone 15 base es ganga aunque esté cerca de la mediana global
        val l = market()
        l += Listing("MLA777", "Apple iPhone 15 Pro Max 256gb Titanio", 1_250_000.0, condition = Condition.NEW)
        val r = DealEngine().analyze("iphone 15", l)
        val d = r.deals.first { it.listing.id == "MLA777" }
        assertTrue(d.referencePrice > 1_900_000, "ref ${d.referencePrice}")
        assertTrue(d.belowReference > 0.3)
        assertEquals("MLA777", r.deals.first().listing.id)
    }

    @Test fun filtersAccessoriesAndPenalizesBait() {
        val l = market()
        l += Listing("MLA501", "Funda Silicona Para iPhone 15", 9_000.0)
        l += Listing("MLA502", "Vidrio Templado iPhone 15 Pro Max", 5_000.0)
        l += Listing("MLA503", "iPhone 15 128gb Consultar Precio", 1.0)
        l += Listing("MLA504", "iPhone 15 128gb replica aaa", 150_000.0)
        val r = DealEngine().analyze("iphone 15", l)
        val ids = r.deals.map { it.listing.id }
        assertTrue("MLA501" !in ids && "MLA502" !in ids)
        assertEquals(2, r.discardedAccessories)
        val bait = r.deals.first { it.listing.id == "MLA503" }
        assertEquals(Risk.HIGH, bait.risk)
        assertTrue(bait.score < 30, "bait ${bait.score}")
        val replica = r.deals.first { it.listing.id == "MLA504" }
        assertTrue(Signal.RISKY_TEXT in replica.signals)
    }

    @Test fun normalPricesScoreLow() {
        val r = DealEngine().analyze("iphone 15", market())
        assertTrue(r.deals.all { it.score < 40 }, r.deals.take(3).joinToString { "${it.listing.title} ${it.score}" })
    }

    @Test fun irrelevantDropped() {
        val l = market()
        l += Listing("MLA600", "Heladera Samsung No Frost", 1_000.0)
        val r = DealEngine().analyze("iphone 15", l)
        assertTrue(r.deals.none { it.listing.id == "MLA600" })
    }

    @Test fun priceDropFromHistory() {
        val l = market()
        val target = l.first().copy(price = 900_000.0)
        l[0] = target
        val r = DealEngine().analyze("iphone 15", l, history = mapOf(target.id to 1_250_000.0))
        assertTrue(Signal.PRICE_DROP in r.deals.first { it.listing.id == target.id }.signals)
    }

    @Test fun stats() {
        assertEquals(3.0, RobustStats.median(listOf(5.0, 1.0, 3.0)))
        assertEquals(2.5, RobustStats.median(listOf(1.0, 2.0, 3.0, 4.0)))
        assertEquals(1.0, RobustStats.mad(listOf(1.0, 2.0, 3.0, 4.0, 100.0)))
        assertEquals(2.0, RobustStats.weightedMedian(listOf(1.0, 2.0, 3.0), listOf(1.0, 5.0, 1.0)))
    }
}
