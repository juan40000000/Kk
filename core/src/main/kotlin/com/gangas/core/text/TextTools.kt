package com.gangas.core.text

import java.text.Normalizer

object TextTools {
    private val DIACRITICS = "\\p{Mn}+".toRegex()
    private val NON_ALNUM = "[^a-z0-9]+".toRegex()
    // une "128 gb" -> "128gb", "15 pro" no se toca
    private val UNIT_JOIN = "\\b(\\d+)\\s+(gb|tb|mb|mah|w|hz|pulgadas|pulg|mm|cm|l|kg|g|ml)\\b".toRegex()

    val STOPWORDS = setOf(
        "de", "del", "la", "el", "los", "las", "y", "o", "en", "con", "sin", "un", "una", "por",
        "a", "al", "e", "x", "the", "and", "for", "com", "do", "da", "nuevo", "nueva", "original",
        "oferta", "envio", "gratis", "garantia", "sellado", "sellada", "modelo", "color", "unidad",
        "novo", "nova", "the", "new", "pack", "kit", "set",
    )

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(DIACRITICS, "")
        return n.replace(UNIT_JOIN, "$1$2")
    }

    fun tokens(s: String): List<String> =
        normalize(s).split(NON_ALNUM).filter { it.isNotBlank() && it !in STOPWORDS }

    /** Coincidencia de tokens tolerante a plurales/prefijos ("auricular" ~ "auriculares"). */
    fun tokenMatches(q: String, t: String): Boolean {
        if (q == t) return true
        if (q.any { it.isDigit() } || t.any { it.isDigit() }) return false // números exactos
        if (q.length >= 4 && t.length >= 4) {
            return t.startsWith(q) || q.startsWith(t) || levenshteinAtMostOne(q, t)
        }
        return false
    }

    private fun levenshteinAtMostOne(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    /** Fracción de tokens de la búsqueda presentes en el título. */
    fun relevance(queryTokens: List<String>, titleTokens: Collection<String>): Double {
        if (queryTokens.isEmpty()) return 1.0
        val hits = queryTokens.count { q -> titleTokens.any { t -> tokenMatches(q, t) } }
        return hits.toDouble() / queryTokens.size
    }

    fun containsPhrase(normalizedTitle: String, phrase: String): Boolean {
        val p = normalize(phrase).trim()
        if (p.isEmpty()) return false
        return Regex("(^|[^a-z0-9])" + Regex.escape(p) + "($|[^a-z0-9])").containsMatchIn(normalizedTitle)
    }
}
