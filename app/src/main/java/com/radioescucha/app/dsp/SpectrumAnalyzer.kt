package com.radioescucha.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

/** Acumula FFTs con ventana de Hann y entrega el espectro promedio en dBFS, con DC en el centro. */
class SpectrumAnalyzer(val size: Int = 1024) {
    private val fft = Fft(size)
    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / (size - 1))).toFloat() }
    private val norm: Double
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val acc = DoubleArray(size)
    var count = 0
        private set

    init {
        var s = 0.0
        for (w in window) s += w
        norm = s * s
    }

    fun add(i: FloatArray, q: FloatArray, offset: Int) {
        for (k in 0 until size) {
            re[k] = i[offset + k] * window[k]
            im[k] = q[offset + k] * window[k]
        }
        fft.transform(re, im)
        for (k in 0 until size) acc[k] += (re[k] * re[k] + im[k] * im[k]).toDouble()
        count++
    }

    /** Agrega todas las FFTs que entran en el bloque (hasta [maxFfts]). */
    fun addBlock(i: FloatArray, q: FloatArray, n: Int, maxFfts: Int) {
        var off = 0
        var done = 0
        while (off + size <= n && done < maxFfts) {
            add(i, q, off)
            off += size
            done++
        }
    }

    /** Escribe el promedio en [out] (dBFS, reordenado para que DC quede en el centro) y reinicia. */
    fun result(out: FloatArray) {
        val c = if (count == 0) 1 else count
        val half = size / 2
        for (k in 0 until size) {
            val src = (k + half) % size
            out[k] = (10 * log10(acc[src] / c / norm + 1e-15)).toFloat()
        }
        acc.fill(0.0)
        count = 0
    }
}
