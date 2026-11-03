package com.gangas.core.engine

import kotlin.math.abs

/** Estadística robusta (insensible a outliers), base de la detección. */
object RobustStats {
    fun median(values: List<Double>): Double = quantile(values, 0.5)

    /** Cuantil con interpolación lineal (tipo 7, como R/numpy). */
    fun quantile(values: List<Double>, q: Double): Double {
        require(values.isNotEmpty())
        val s = values.sorted()
        val pos = (s.size - 1) * q.coerceIn(0.0, 1.0)
        val lo = pos.toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }

    /** Median Absolute Deviation. */
    fun mad(values: List<Double>, med: Double = median(values)): Double =
        median(values.map { abs(it - med) })

    /** Mediana ponderada: el valor donde se acumula la mitad del peso. */
    fun weightedMedian(values: List<Double>, weights: List<Double>): Double {
        require(values.size == weights.size && values.isNotEmpty())
        val pairs = values.zip(weights).sortedBy { it.first }
        val total = pairs.sumOf { it.second }
        if (total <= 0) return median(values)
        var acc = 0.0
        for ((v, w) in pairs) {
            acc += w
            if (acc >= total / 2) return v
        }
        return pairs.last().first
    }

    /**
     * z-score modificado de Iglewicz–Hoaglin: 0.6745·(x − mediana)/MAD.
     * |z| > 3.5 se considera outlier.
     */
    fun modifiedZ(x: Double, med: Double, mad: Double, madFloor: Double = 0.05): Double =
        0.6745 * (x - med) / maxOf(mad, madFloor)

    /** Valla inferior de Tukey: Q1 − k·IQR. */
    fun tukeyLowerFence(values: List<Double>, k: Double = 1.5): Double {
        val q1 = quantile(values, 0.25)
        val q3 = quantile(values, 0.75)
        return q1 - k * (q3 - q1)
    }
}
