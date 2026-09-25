package com.radioescucha.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object FirDesign {
    /** Filtro pasa-bajos por sinc enventanada (Blackman), ganancia unitaria en DC. */
    fun lowPass(numTaps: Int, cutoffHz: Double, sampleRate: Double): FloatArray {
        val fc = cutoffHz / sampleRate
        val m = numTaps - 1
        val taps = DoubleArray(numTaps)
        var sum = 0.0
        for (i in 0 until numTaps) {
            val n = i - m / 2.0
            val sinc = if (n == 0.0) 2 * fc else sin(2 * PI * fc * n) / (PI * n)
            val w = 0.42 - 0.5 * cos(2 * PI * i / m) + 0.08 * cos(4 * PI * i / m)
            taps[i] = sinc * w
            sum += taps[i]
        }
        return FloatArray(numTaps) { (taps[it] / sum).toFloat() }
    }
}

/** FIR complejo con diezmado (decim = 1 para filtrar sin diezmar). Mantiene estado entre bloques. */
class ComplexFirDecimator(private val taps: FloatArray, private val decim: Int) {
    private val nt = taps.size
    private var workI = FloatArray(nt - 1)
    private var workQ = FloatArray(nt - 1)
    private var phase = 0

    fun process(inI: FloatArray, inQ: FloatArray, n: Int, outI: FloatArray, outQ: FloatArray): Int {
        val total = nt - 1 + n
        if (workI.size < total) {
            workI = workI.copyOf(total)
            workQ = workQ.copyOf(total)
        }
        System.arraycopy(inI, 0, workI, nt - 1, n)
        System.arraycopy(inQ, 0, workQ, nt - 1, n)
        var count = 0
        var pos = phase
        while (pos + nt <= total) {
            var si = 0f
            var sq = 0f
            for (k in 0 until nt) {
                val t = taps[k]
                si += workI[pos + k] * t
                sq += workQ[pos + k] * t
            }
            outI[count] = si
            outQ[count] = sq
            count++
            pos += decim
        }
        phase = pos - n
        System.arraycopy(workI, n, workI, 0, nt - 1)
        System.arraycopy(workQ, n, workQ, 0, nt - 1)
        return count
    }

    /** Cantidad máxima de muestras de salida para [n] muestras de entrada. */
    fun maxOutput(n: Int) = n / decim + 1
}

/** FIR real con diezmado. */
class RealFirDecimator(private val taps: FloatArray, private val decim: Int) {
    private val nt = taps.size
    private var work = FloatArray(nt - 1)
    private var phase = 0

    fun process(input: FloatArray, n: Int, out: FloatArray): Int {
        val total = nt - 1 + n
        if (work.size < total) work = work.copyOf(total)
        System.arraycopy(input, 0, work, nt - 1, n)
        var count = 0
        var pos = phase
        while (pos + nt <= total) {
            var s = 0f
            for (k in 0 until nt) s += work[pos + k] * taps[k]
            out[count++] = s
            pos += decim
        }
        phase = pos - n
        System.arraycopy(work, n, work, 0, nt - 1)
        return count
    }
}

/** Oscilador numérico: desplaza en frecuencia una señal compleja (in-place). */
class Nco(private val sampleRate: Double) {
    var frequency = 0.0
    private var phase = 0.0

    fun mix(i: FloatArray, q: FloatArray, n: Int) {
        if (frequency == 0.0) return
        val w = 2 * PI * frequency / sampleRate
        val cw = cos(w)
        val sw = sin(w)
        var c = cos(phase)
        var s = sin(phase)
        for (k in 0 until n) {
            val ii = i[k]
            val qq = q[k]
            i[k] = (ii * c - qq * s).toFloat()
            q[k] = (ii * s + qq * c).toFloat()
            val nc = c * cw - s * sw
            s = s * cw + c * sw
            c = nc
        }
        phase = (phase + w * n) % (2 * PI)
    }
}
