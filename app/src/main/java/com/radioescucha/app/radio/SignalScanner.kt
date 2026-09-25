package com.radioescucha.app.radio

import com.radioescucha.app.dsp.Demodulator
import com.radioescucha.app.dsp.Mode
import kotlin.math.abs
import kotlin.math.max

data class Detection(
    val freqHz: Long,
    var snrDb: Float,
    var lastSnrDb: Float,
    var hits: Int,
    val firstSeen: Long,
    var lastSeen: Long,
    val band: String,
    val mode: Mode,
)

/**
 * Barre un rango de frecuencias en pasos del ancho de banda útil del receptor y registra
 * las señales que sobresalen [thresholdDb] por encima del piso de ruido.
 */
class SignalScanner(val startHz: Long, val endHz: Long, @Volatile var thresholdDb: Float) {
    companion object {
        const val USABLE_FRACTION = 0.8
        val STEP = (Demodulator.INPUT_RATE * USABLE_FRACTION).toLong()
    }

    private val detections = ArrayList<Detection>()
    var sweeps = 0
        private set
    var currentCenter = firstCenter()
        private set

    private fun firstCenter() = startHz + STEP / 2

    /** Avanza a la siguiente frecuencia central del barrido. */
    fun next(): Long {
        currentCenter += STEP
        if (currentCenter - STEP / 2 >= endHz) {
            currentCenter = firstCenter()
            sweeps++
        }
        return currentCenter
    }

    /** Analiza un espectro promedio (dBFS, DC al centro) capturado con centro [centerHz]. */
    fun analyze(db: FloatArray, centerHz: Long, now: Long = System.currentTimeMillis()): Int {
        val n = db.size
        val binHz = Demodulator.INPUT_RATE.toDouble() / n
        val lo = ((1 - USABLE_FRACTION) / 2 * n).toInt()
        val hi = n - lo
        val floor = median(db, lo, hi)
        val thr = floor + thresholdDb
        var found = 0
        var k = lo
        while (k < hi) {
            if (db[k] > thr && abs(k - n / 2) > 2) {
                var peakBin = k
                var j = k
                while (j < hi && db[j] > thr - 3f) {
                    if (db[j] > db[peakBin]) peakBin = j
                    j++
                }
                val f = centerHz + ((peakBin - n / 2) * binHz).toLong()
                if (f in startHz..endHz) {
                    record(f, db[peakBin] - floor, now)
                    found++
                }
                k = j
            } else {
                k++
            }
        }
        return found
    }

    private fun record(freq: Long, snr: Float, now: Long) {
        val mode = BandPlan.suggestedMode(freq)
        val tol = if (mode == Mode.WFM) 100_000L else 8_000L
        synchronized(detections) {
            val d = detections.firstOrNull { abs(it.freqHz - freq) <= tol }
            if (d != null) {
                d.snrDb = max(d.snrDb, snr)
                d.lastSnrDb = snr
                d.hits++
                d.lastSeen = now
            } else {
                detections += Detection(roundFreq(freq, mode), snr, snr, 1, now, now, BandPlan.describe(freq), mode)
            }
        }
    }

    private fun roundFreq(f: Long, mode: Mode): Long {
        val step = when (mode) {
            Mode.WFM -> 100_000L
            Mode.NFM -> 2_500L
            Mode.AM -> 5_000L
            else -> 500L
        }
        return (f + step / 2) / step * step
    }

    fun results(): List<Detection> = synchronized(detections) {
        detections.map { it.copy() }.sortedByDescending { it.snrDb }
    }

    private fun median(a: FloatArray, from: Int, to: Int): Float {
        val c = a.copyOfRange(from, to)
        c.sort()
        return c[c.size / 2]
    }
}
