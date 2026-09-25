package com.radioescucha.app.dsp

import kotlin.math.abs
import kotlin.math.exp

/** Control automático de ganancia simple (ataque rápido, caída lenta). */
class Agc(sampleRate: Int, private val target: Float = 0.35f, releaseSeconds: Double = 0.6) {
    private val decay = exp(-1.0 / (releaseSeconds * sampleRate)).toFloat()
    private var peak = 1e-4f

    fun process(buf: FloatArray, n: Int) {
        for (k in 0 until n) {
            val a = abs(buf[k])
            peak = if (a > peak) a else peak * decay
            if (peak < 1e-5f) peak = 1e-5f
            val g = (target / peak).coerceAtMost(3000f)
            buf[k] *= g
        }
    }
}
