package com.radioescucha.app

import com.radioescucha.app.dsp.Demodulator
import com.radioescucha.app.dsp.Mode
import com.radioescucha.app.dsp.SpectrumAnalyzer
import com.radioescucha.app.radio.SignalScanner
import com.radioescucha.app.source.DemoSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    private val fs = Demodulator.INPUT_RATE.toDouble()
    private val block = 16_384

    /** Genera [seconds] de IQ con la función de fase/amplitud dada y la demodula. */
    private fun demodulate(mode: Mode, offset: Double, seconds: Double, noise: Float = 0.001f,
                           gen: (t: Double) -> Pair<Double, Double>): FloatArray {
        val d = Demodulator()
        d.mode = mode
        d.offsetHz = offset
        val total = (seconds * fs).toInt() / block * block
        val out = ArrayList<Float>()
        val i = FloatArray(block); val q = FloatArray(block)
        val audio = FloatArray(d.maxAudio(block))
        val rnd = java.util.Random(1)
        var n = 0
        while (n < total) {
            for (k in 0 until block) {
                val (amp, ph) = gen((n + k) / fs)
                i[k] = (amp * cos(ph)).toFloat() + noise * rnd.nextGaussian().toFloat()
                q[k] = (amp * sin(ph)).toFloat() + noise * rnd.nextGaussian().toFloat()
            }
            val na = d.process(i, q, block, audio)
            for (k in 0 until na) out += audio[k]
            n += block
        }
        return out.toFloatArray()
    }

    /** Frecuencia dominante del audio (búsqueda con Goertzel entre 100 Hz y 5 kHz), ignorando el transitorio inicial. */
    private fun dominant(audio: FloatArray): Pair<Double, Double> {
        val a = audio.copyOfRange(audio.size / 4, audio.size)
        val rate = Demodulator.AUDIO_RATE.toDouble()
        var best = 0.0; var bestP = -1.0
        var f = 100.0
        while (f < 5000) {
            val w = 2 * PI * f / rate
            val c = 2 * cos(w)
            var s1 = 0.0; var s2 = 0.0
            for (x in a) { val s = x + c * s1 - s2; s2 = s1; s1 = s }
            val p = s1 * s1 + s2 * s2 - c * s1 * s2
            if (p > bestP) { bestP = p; best = f }
            f += 10.0
        }
        val rms = sqrt(a.fold(0.0) { acc, x -> acc + x * x } / a.size)
        return best to rms
    }

    @Test fun nfmRecoversTone() {
        val off = 200_000.0
        var phase = 0.0
        var last = 0.0
        val audio = demodulate(Mode.NFM, off, 0.5) { t ->
            val dt = t - last; last = t
            phase += 2 * PI * (off + 3000 * sin(2 * PI * 1000 * t)) * dt
            0.3 to phase
        }
        val (f, rms) = dominant(audio)
        println("NFM: $f Hz rms=$rms")
        assertEquals(1000.0, f, 20.0)
        assertTrue(rms > 0.1)
    }

    @Test fun wfmRecoversTone() {
        val off = -250_000.0
        var phase = 0.0
        var last = 0.0
        val audio = demodulate(Mode.WFM, off, 0.5) { t ->
            val dt = t - last; last = t
            phase += 2 * PI * (off + 60_000 * sin(2 * PI * 1500 * t)) * dt
            0.3 to phase
        }
        val (f, rms) = dominant(audio)
        println("WFM: $f Hz rms=$rms")
        assertEquals(1500.0, f, 20.0)
    }

    @Test fun amRecoversTone() {
        val off = 100_000.0
        val audio = demodulate(Mode.AM, off, 0.5) { t ->
            0.2 * (1 + 0.5 * sin(2 * PI * 600 * t)) to 2 * PI * off * t
        }
        val (f, _) = dominant(audio)
        println("AM: $f Hz")
        assertEquals(600.0, f, 20.0)
    }

    @Test fun usbAndLsbSelectCorrectSideband() {
        val off = 50_000.0
        val usb = demodulate(Mode.USB, off, 0.5) { t -> 0.1 to 2 * PI * (off + 1200) * t }
        val (fu, ru) = dominant(usb)
        println("USB: $fu Hz rms=$ru")
        assertEquals(1200.0, fu, 20.0)

        val lsb = demodulate(Mode.LSB, off, 0.5) { t -> 0.1 to 2 * PI * (off - 800) * t }
        val (fl, _) = dominant(lsb)
        println("LSB: $fl Hz")
        assertEquals(800.0, fl, 20.0)

        // Tono en la banda opuesta: en USB debe quedar muy atenuado frente al AGC con ruido.
        val wrong = demodulate(Mode.USB, off, 0.5, noise = 0.01f) { t -> 0.1 to 2 * PI * (off - 1200) * t }
        val a = wrong.copyOfRange(wrong.size / 4, wrong.size)
        val rate = Demodulator.AUDIO_RATE.toDouble()
        fun power(f: Double): Double {
            val c = 2 * cos(2 * PI * f / rate); var s1 = 0.0; var s2 = 0.0
            for (x in a) { val s = x + c * s1 - s2; s2 = s1; s1 = s }
            return s1 * s1 + s2 * s2 - c * s1 * s2
        }
        println("USB rechazo LSB: p(1200)=${power(1200.0)} p(1500)=${power(1500.0)}")
        assertTrue(power(1200.0) < 100 * power(1500.0))
    }

    @Test fun cwProducesSidetone() {
        val off = -120_000.0
        val audio = demodulate(Mode.CW, off, 0.5) { t -> 0.05 to 2 * PI * off * t }
        val (f, _) = dominant(audio)
        println("CW: $f Hz")
        assertEquals(700.0, f, 20.0)
    }

    @Test fun squelchMutesNoise() {
        val d = Demodulator()
        d.mode = Mode.NFM
        d.squelchDb = -40f
        val i = FloatArray(block) { (Math.random().toFloat() - 0.5f) * 0.01f }
        val q = FloatArray(block) { (Math.random().toFloat() - 0.5f) * 0.01f }
        val audio = FloatArray(d.maxAudio(block))
        var na = 0
        repeat(10) { na = d.process(i, q, block, audio) }
        assertTrue(!d.squelchOpen)
        assertTrue(audio.take(na).all { it == 0f })
    }

    @Test fun spectrumFindsTone() {
        val sa = SpectrumAnalyzer(1024)
        val f = 250_000.0
        val i = FloatArray(1024) { cos(2 * PI * f * it / fs).toFloat() * 0.5f }
        val q = FloatArray(1024) { sin(2 * PI * f * it / fs).toFloat() * 0.5f }
        sa.add(i, q, 0)
        val out = FloatArray(1024)
        sa.result(out)
        val peak = out.indices.maxBy { out[it] }
        assertEquals(512 + 250, peak)
        assertEquals(-6.0, out[peak].toDouble(), 1.0)
    }

    @Test fun scannerFindsDemoStations() {
        val src = DemoSource()
        val sc = SignalScanner(144_000_000, 148_000_000, 12f)
        src.setFrequency(sc.currentCenter)
        src.open()
        val sa = SpectrumAnalyzer(1024)
        val spec = FloatArray(1024)
        val i = FloatArray(block); val q = FloatArray(block)
        repeat(6) {
            src.read(i, q) // bloque de asentamiento
            repeat(2) { src.read(i, q); sa.addBlock(i, q, block, 16) }
            sa.result(spec)
            sc.analyze(spec, sc.currentCenter)
            src.setFrequency(sc.next())
        }
        val found = sc.results()
        found.forEach { println("Detectada: ${it.freqHz} ${it.band} ${it.mode} SNR ${it.snrDb}") }
        assertTrue(found.any { abs(it.freqHz - 145_800_000) <= 5000 })
        assertTrue(found.any { abs(it.freqHz - 146_520_000) <= 5000 })
        assertTrue(found.all { it.freqHz in 144_000_000..148_000_000 })
    }

    @Test fun throughputIsRealtime() {
        val i = FloatArray(block) { (Math.random().toFloat() - 0.5f) * 0.1f }
        val q = FloatArray(block) { (Math.random().toFloat() - 0.5f) * 0.1f }
        for (m in Mode.values()) {
            val d = Demodulator(); d.mode = m; d.offsetHz = 123_000.0
            val audio = FloatArray(d.maxAudio(block))
            repeat(20) { d.process(i, q, block, audio) } // calentamiento JIT
            val blocks = Demodulator.INPUT_RATE / block * 2
            val t0 = System.nanoTime()
            repeat(blocks) { d.process(i, q, block, audio) }
            val secs = (System.nanoTime() - t0) / 1e9
            println("$m: ${"%.1f".format(2.0 / secs)}x tiempo real")
            assertTrue(secs < 2.0)
        }
    }
}
