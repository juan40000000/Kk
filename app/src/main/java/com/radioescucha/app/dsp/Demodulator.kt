package com.radioescucha.app.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Cadena de demodulación: IQ a 1.024 MS/s -> canal -> audio a 32 kHz.
 *
 *  1.024 MS/s --(mezcla + FIR /4)--> 256 kS/s --WFM--> discriminador -> FIR /8 -> de-énfasis -> audio
 *                                               \--(FIR /8)--> 32 kS/s -> filtro de modo -> NFM/AM/USB/LSB/CW
 */
class Demodulator {
    companion object {
        const val INPUT_RATE = 1_024_000
        const val RATE1 = INPUT_RATE / 4
        const val AUDIO_RATE = RATE1 / 8
        private const val SSB_SHIFT = 1_500.0
        private const val CW_TONE = 700.0
    }

    @Volatile var mode: Mode = Mode.NFM
    /** Desplazamiento del canal respecto a la frecuencia central del receptor, en Hz. */
    @Volatile var offsetHz: Double = 0.0
    /** Umbral de silenciador en dBFS; por debajo de -120 se considera desactivado. */
    @Volatile var squelchDb: Float = -130f

    /** Potencia del canal suavizada, en dBFS. */
    @Volatile var channelDb: Float = -120f
        private set
    @Volatile var squelchOpen: Boolean = true
        private set

    private val nco = Nco(INPUT_RATE.toDouble())
    private val dec1 = ComplexFirDecimator(FirDesign.lowPass(48, 100_000.0, INPUT_RATE.toDouble()), 4)
    private val dec2 = ComplexFirDecimator(FirDesign.lowPass(96, 12_000.0, RATE1.toDouble()), 8)
    private val wfmAudio = RealFirDecimator(FirDesign.lowPass(64, 15_000.0, RATE1.toDouble()), 8)

    private var activeMode: Mode? = null
    private var modeFilter = ComplexFirDecimator(FloatArray(1) { 1f }, 1)
    private val preShift = Nco(AUDIO_RATE.toDouble())
    private val postShift = Nco(AUDIO_RATE.toDouble())
    private var agc = Agc(AUDIO_RATE)

    private var lastI = 0f
    private var lastQ = 0f
    private var deemph = 0f
    private val deemphAlpha = (1 - exp(-1.0 / (AUDIO_RATE * 50e-6))).toFloat()
    private val nfmAlpha = (1 - exp(-2 * PI * 3_000.0 / AUDIO_RATE)).toFloat()
    private var nfmLp = 0f
    private var dcX = 0f
    private var dcY = 0f

    private var mI = FloatArray(0); private var mQ = FloatArray(0)
    private var b1I = FloatArray(0); private var b1Q = FloatArray(0)
    private var b2I = FloatArray(0); private var b2Q = FloatArray(0)
    private var b3I = FloatArray(0); private var b3Q = FloatArray(0)
    private var fm = FloatArray(0)

    private fun ensure(n: Int) {
        if (mI.size >= n) return
        mI = FloatArray(n); mQ = FloatArray(n)
        val n1 = n / 4 + 2
        b1I = FloatArray(n1); b1Q = FloatArray(n1); fm = FloatArray(n1)
        val n2 = n1 / 8 + 2
        b2I = FloatArray(n2); b2Q = FloatArray(n2)
        b3I = FloatArray(n2); b3Q = FloatArray(n2)
    }

    /** Tamaño mínimo del buffer de audio para un bloque de [n] muestras IQ. */
    fun maxAudio(n: Int) = n / 32 + 4

    private fun configure(m: Mode) {
        activeMode = m
        val taps = when (m) {
            Mode.NFM -> FirDesign.lowPass(128, 7_000.0, AUDIO_RATE.toDouble())
            Mode.AM -> FirDesign.lowPass(128, 5_000.0, AUDIO_RATE.toDouble())
            Mode.USB, Mode.LSB -> FirDesign.lowPass(256, 1_400.0, AUDIO_RATE.toDouble())
            Mode.CW -> FirDesign.lowPass(256, 300.0, AUDIO_RATE.toDouble())
            Mode.WFM -> FloatArray(1) { 1f }
        }
        modeFilter = ComplexFirDecimator(taps, 1)
        preShift.frequency = when (m) {
            Mode.USB -> -SSB_SHIFT
            Mode.LSB -> SSB_SHIFT
            else -> 0.0
        }
        postShift.frequency = when (m) {
            Mode.USB -> SSB_SHIFT
            Mode.LSB -> -SSB_SHIFT
            Mode.CW -> CW_TONE
            else -> 0.0
        }
        agc = Agc(AUDIO_RATE)
    }

    /** Procesa [n] muestras IQ y escribe el audio resultante en [audio]. Devuelve cuántas muestras de audio escribió. */
    fun process(inI: FloatArray, inQ: FloatArray, n: Int, audio: FloatArray): Int {
        ensure(n)
        val m = mode
        if (m != activeMode) configure(m)

        System.arraycopy(inI, 0, mI, 0, n)
        System.arraycopy(inQ, 0, mQ, 0, n)
        nco.frequency = -offsetHz
        nco.mix(mI, mQ, n)
        val n1 = dec1.process(mI, mQ, n, b1I, b1Q)

        val na: Int
        if (m == Mode.WFM) {
            updatePower(b1I, b1Q, n1)
            fmDemod(b1I, b1Q, n1, fm, (RATE1 / (2 * PI * 75_000.0)).toFloat())
            na = wfmAudio.process(fm, n1, audio)
            for (k in 0 until na) {
                deemph += deemphAlpha * (audio[k] - deemph)
                audio[k] = deemph
            }
        } else {
            val n2 = dec2.process(b1I, b1Q, n1, b2I, b2Q)
            preShift.mix(b2I, b2Q, n2)
            na = modeFilter.process(b2I, b2Q, n2, b3I, b3Q)
            updatePower(b3I, b3Q, na)
            when (m) {
                Mode.NFM -> {
                    fmDemod(b3I, b3Q, na, audio, (AUDIO_RATE / (2 * PI * 5_000.0)).toFloat())
                    for (k in 0 until na) {
                        nfmLp += nfmAlpha * (audio[k] - nfmLp)
                        audio[k] = nfmLp
                    }
                }
                Mode.AM -> {
                    for (k in 0 until na) {
                        val mag = sqrt(b3I[k] * b3I[k] + b3Q[k] * b3Q[k])
                        dcY = mag - dcX + 0.995f * dcY
                        dcX = mag
                        audio[k] = dcY
                    }
                    agc.process(audio, na)
                }
                else -> { // USB, LSB, CW
                    postShift.mix(b3I, b3Q, na)
                    System.arraycopy(b3I, 0, audio, 0, na)
                    agc.process(audio, na)
                }
            }
        }

        val sq = squelchDb
        squelchOpen = if (sq <= -120f) true else if (squelchOpen) channelDb > sq - 2f else channelDb > sq
        if (!squelchOpen) java.util.Arrays.fill(audio, 0, na, 0f)
        return na
    }

    private fun updatePower(i: FloatArray, q: FloatArray, n: Int) {
        if (n == 0) return
        var p = 0.0
        for (k in 0 until n) p += i[k] * i[k] + q[k] * q[k]
        val db = (10 * log10(p / n + 1e-12)).toFloat()
        channelDb = if (db > channelDb) 0.5f * channelDb + 0.5f * db else 0.85f * channelDb + 0.15f * db
    }

    private fun fmDemod(i: FloatArray, q: FloatArray, n: Int, out: FloatArray, gain: Float) {
        var pi = lastI
        var pq = lastQ
        for (k in 0 until n) {
            val ci = i[k]
            val cq = q[k]
            out[k] = atan2(cq * pi - ci * pq, ci * pi + cq * pq) * gain
            pi = ci
            pq = cq
        }
        lastI = pi
        lastQ = pq
    }
}
