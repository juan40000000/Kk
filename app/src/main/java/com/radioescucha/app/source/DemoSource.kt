package com.radioescucha.app.source

import com.radioescucha.app.dsp.Demodulator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fuente simulada: genera ruido y un puñado de emisoras ficticias para probar la app sin hardware.
 * Las señales aparecen y desaparecen como transmisiones reales, así el escáner tiene algo que encontrar.
 */
class DemoSource : IqSource {
    override val name = "Demo (señales simuladas)"
    override val settleBlocks = 1

    private enum class Kind { VOICE_FM, MUSIC_FM, VOICE_AM, CW, SSB, APRS, OOK }

    private class Sim(val freqHz: Long, val kind: Kind, val level: Float, val periodS: Double, val dutyOn: Double, val phaseS: Double) {
        var carrierPhase = 0.0
        var modPhase = 0.0
        var modPhase2 = 0.0
    }

    private val sims = listOf(
        Sim(145_800_000, Kind.VOICE_FM, 0.12f, 20.0, 0.55, 0.0),
        Sim(146_520_000, Kind.VOICE_FM, 0.05f, 30.0, 0.4, 7.0),
        Sim(145_500_000, Kind.VOICE_FM, 0.03f, 25.0, 0.3, 3.0),
        Sim(144_390_000, Kind.APRS, 0.08f, 8.0, 0.12, 1.0),
        Sim(144_050_000, Kind.CW, 0.06f, 1.0, 1.0, 0.0),
        Sim(144_200_000, Kind.SSB, 0.05f, 18.0, 0.6, 4.0),
        Sim(118_100_000, Kind.VOICE_AM, 0.08f, 15.0, 0.45, 2.0),
        Sim(121_500_000, Kind.VOICE_AM, 0.02f, 40.0, 0.2, 11.0),
        Sim(156_800_000, Kind.VOICE_FM, 0.07f, 22.0, 0.35, 5.0),
        Sim(162_400_000, Kind.VOICE_FM, 0.05f, 1.0, 1.0, 0.0),
        Sim(98_500_000, Kind.MUSIC_FM, 0.25f, 1.0, 1.0, 0.0),
        Sim(100_100_000, Kind.MUSIC_FM, 0.35f, 1.0, 1.0, 0.0),
        Sim(104_300_000, Kind.MUSIC_FM, 0.15f, 1.0, 1.0, 0.0),
        Sim(433_920_000, Kind.OOK, 0.10f, 6.0, 0.08, 0.5),
        Sim(446_006_250, Kind.VOICE_FM, 0.06f, 16.0, 0.4, 9.0),
        Sim(27_065_000, Kind.VOICE_AM, 0.05f, 12.0, 0.5, 1.0),
        Sim(14_074_000, Kind.SSB, 0.04f, 15.0, 0.9, 0.0),
        Sim(7_100_000, Kind.SSB, 0.06f, 14.0, 0.5, 6.0),
        Sim(10_000_000, Kind.VOICE_AM, 0.05f, 1.0, 1.0, 0.0),
    )

    // "CQ CQ DE LU1ABC K" en Morse ('.' = punto, '-' = raya, ' ' = espacio entre letras, '/' = entre palabras)
    private val morse = "-.-. --.- / -.-. --.- / -.. . / .-.. ..- .---- .- -... -.-. / -.-"
    private val cwUnits: BooleanArray = buildList {
        for (c in morse) when (c) {
            '.' -> { add(true); add(false) }
            '-' -> { add(true); add(true); add(true); add(false) }
            ' ' -> { add(false); add(false) }
            '/' -> repeat(4) { add(false) }
        }
        repeat(14) { add(false) }
    }.toBooleanArray()

    @Volatile private var centerHz = 100_000_000L
    private var sampleIndex = 0L
    private var startNs = 0L
    private var rng = 0x2545F4914F6CDD1DL
    private var closed = false

    override fun open() {
        startNs = System.nanoTime()
        sampleIndex = 0
    }

    private fun noise(): Float {
        rng = rng xor (rng shl 13)
        rng = rng xor (rng ushr 7)
        rng = rng xor (rng shl 17)
        return ((rng ushr 40).toInt() / 16777216f - 0.5f)
    }

    override fun read(i: FloatArray, q: FloatArray): Int {
        if (closed) return -1
        val n = i.size
        val fs = Demodulator.INPUT_RATE.toDouble()
        val center = centerHz
        val t0 = sampleIndex / fs
        for (k in 0 until n) {
            i[k] = noise() * 0.02f
            q[k] = noise() * 0.02f
        }
        for (s in sims) {
            val off = (s.freqHz - center).toDouble()
            if (abs(off) > fs / 2 - 20_000) continue
            val cycle = ((t0 + s.phaseS) % s.periodS) / s.periodS
            if (cycle > s.dutyOn) continue
            synth(s, off, t0, n, fs, i, q)
        }
        sampleIndex += n
        // Mantener el ritmo de tiempo real.
        val targetNs = startNs + (sampleIndex * 1_000_000_000L / Demodulator.INPUT_RATE)
        val sleepMs = (targetNs - System.nanoTime()) / 1_000_000
        if (sleepMs > 0) Thread.sleep(sleepMs)
        return n
    }

    private fun synth(s: Sim, off: Double, t0: Double, n: Int, fs: Double, i: FloatArray, q: FloatArray) {
        val twoPi = 2 * PI
        val a = s.level
        when (s.kind) {
            Kind.VOICE_FM, Kind.MUSIC_FM -> {
                val dev = if (s.kind == Kind.MUSIC_FM) 50_000.0 else 3_000.0
                val f1 = if (s.kind == Kind.MUSIC_FM) 523.25 else 440.0
                val f2 = if (s.kind == Kind.MUSIC_FM) 659.25 else 700.0
                for (k in 0 until n) {
                    val t = t0 + k / fs
                    // "Sílabas": envolvente de 4 Hz para la voz; acorde suave para la música.
                    val env = if (s.kind == Kind.MUSIC_FM) 0.8 else 0.5 + 0.5 * sin(twoPi * 3.7 * t)
                    val m = env * (0.6 * sin(s.modPhase) + 0.4 * sin(s.modPhase2))
                    s.modPhase += twoPi * f1 * (1 + 0.1 * sin(twoPi * 0.5 * t)) / fs
                    s.modPhase2 += twoPi * f2 / fs
                    s.carrierPhase += twoPi * (off + dev * m) / fs
                    i[k] += (a * cos(s.carrierPhase)).toFloat()
                    q[k] += (a * sin(s.carrierPhase)).toFloat()
                }
            }
            Kind.VOICE_AM -> {
                for (k in 0 until n) {
                    val t = t0 + k / fs
                    val env = 0.5 + 0.5 * sin(twoPi * 3.1 * t)
                    val m = 1 + 0.7 * env * sin(s.modPhase)
                    s.modPhase += twoPi * (500 + 150 * sin(twoPi * 1.3 * t)) / fs
                    s.carrierPhase += twoPi * off / fs
                    i[k] += (a * m * cos(s.carrierPhase)).toFloat()
                    q[k] += (a * m * sin(s.carrierPhase)).toFloat()
                }
            }
            Kind.CW -> {
                val unitS = 1.2 / 18.0 // 18 palabras por minuto
                for (k in 0 until n) {
                    val t = t0 + k / fs
                    val on = cwUnits[((t / unitS).toLong() % cwUnits.size).toInt()]
                    s.carrierPhase += twoPi * off / fs
                    if (on) {
                        i[k] += (a * cos(s.carrierPhase)).toFloat()
                        q[k] += (a * sin(s.carrierPhase)).toFloat()
                    }
                }
            }
            Kind.SSB -> {
                // Banda lateral superior: dos tonos a +600 y +1400 Hz con envolvente de voz.
                for (k in 0 until n) {
                    val t = t0 + k / fs
                    val env = 0.5 + 0.5 * sin(twoPi * 2.9 * t)
                    s.carrierPhase += twoPi * (off + 600) / fs
                    s.modPhase += twoPi * (off + 1400 + 200 * sin(twoPi * 0.7 * t)) / fs
                    i[k] += (a * env * (cos(s.carrierPhase) + 0.6 * cos(s.modPhase))).toFloat()
                    q[k] += (a * env * (sin(s.carrierPhase) + 0.6 * sin(s.modPhase))).toFloat()
                }
            }
            Kind.APRS -> {
                // AFSK 1200/2200 Hz en FM, como un paquete APRS.
                for (k in 0 until n) {
                    val t = t0 + k / fs
                    val bit = ((t * 1200).toLong() * 2654435761L ushr 7) and 1L
                    s.modPhase += twoPi * (if (bit == 1L) 1200.0 else 2200.0) / fs
                    s.carrierPhase += twoPi * (off + 3_000 * sin(s.modPhase)) / fs
                    i[k] += (a * cos(s.carrierPhase)).toFloat()
                    q[k] += (a * sin(s.carrierPhase)).toFloat()
                }
            }
            Kind.OOK -> {
                for (k in 0 until n) {
                    val t = t0 + k / fs
                    val on = ((t * 2000).toLong() * 40503L ushr 5) and 1L == 1L
                    s.carrierPhase += twoPi * off / fs
                    if (on) {
                        i[k] += (a * cos(s.carrierPhase)).toFloat()
                        q[k] += (a * sin(s.carrierPhase)).toFloat()
                    }
                }
            }
        }
        s.carrierPhase %= twoPi
        s.modPhase %= twoPi
        s.modPhase2 %= twoPi
    }

    override fun setFrequency(hz: Long) { centerHz = hz }
    override fun setGain(tenthsDb: Int) {}
    override fun setPpm(ppm: Int) {}
    override fun close() { closed = true }

}
