package com.radioescucha.app.radio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.radioescucha.app.dsp.Demodulator
import com.radioescucha.app.dsp.SpectrumAnalyzer
import com.radioescucha.app.source.IqSource
import java.io.File

/**
 * Motor del receptor: lee IQ de la fuente en un hilo propio, calcula el espectro, demodula
 * y reproduce el audio. También ejecuta el escáner de señales cuando está activo.
 * Los callbacks se invocan desde el hilo del motor.
 */
class RadioEngine(private val cb: Callback) {
    interface Callback {
        fun onStarted(sourceName: String)
        fun onStopped(error: String?)
        fun onSpectrum(db: FloatArray, centerHz: Long)
        fun onLevel(db: Float, squelchOpen: Boolean)
        fun onScanStep(centerHz: Long, sweeps: Int, found: Int)
    }

    companion object {
        const val BLOCK = 16_384
        const val FFT_SIZE = 1024
        const val MIN_FREQ = 500_000L
        const val MAX_FREQ = 1_766_000_000L
    }

    val demod = Demodulator()

    @Volatile var centerHz = 145_650_000L
        private set
    @Volatile var volume = 0.7f
    @Volatile var running = false
        private set
    @Volatile var scanner: SignalScanner? = null
    @Volatile private var gainTenths = -1
    @Volatile private var ppm = 0

    private var source: IqSource? = null
    private var thread: Thread? = null
    private val recLock = Any()
    private var recorder: WavWriter? = null

    fun setCenter(hz: Long) {
        centerHz = hz.coerceIn(MIN_FREQ, MAX_FREQ)
        if (scanner == null) source?.setFrequency(centerHz)
    }

    fun setGain(tenthsDb: Int) {
        gainTenths = tenthsDb
        source?.setGain(tenthsDb)
    }

    fun setPpm(value: Int) {
        ppm = value
        source?.setPpm(value)
    }

    fun start(src: IqSource) {
        if (running) return
        running = true
        source = src
        thread = Thread({ loop(src) }, "motor-sdr").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        source?.close()
        thread?.join(2000)
        thread = null
        source = null
    }

    val isRecording get() = synchronized(recLock) { recorder != null }

    fun startRecording(file: File) = synchronized(recLock) {
        recorder?.close()
        recorder = WavWriter(file, Demodulator.AUDIO_RATE)
    }

    /** Detiene la grabación y devuelve el archivo generado. */
    fun stopRecording(): File? = synchronized(recLock) {
        val r = recorder ?: return null
        recorder = null
        r.close()
        r.file
    }

    private fun newAudioTrack(): AudioTrack {
        val rate = Demodulator.AUDIO_RATE
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, rate / 2 * 2)) // 0,5 s; múltiplo del tamaño de muestra (2 bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun loop(src: IqSource) {
        var error: String? = null
        var track: AudioTrack? = null
        try {
            src.setFrequency(centerHz)
            src.setGain(gainTenths)
            src.setPpm(ppm)
            src.open()
            cb.onStarted(src.name)
            track = newAudioTrack().also { it.play() }

            val i = FloatArray(BLOCK)
            val q = FloatArray(BLOCK)
            val audio = FloatArray(demod.maxAudio(BLOCK))
            val pcm = ShortArray(audio.size)
            val analyzer = SpectrumAnalyzer(FFT_SIZE)
            val spec = FloatArray(FFT_SIZE)
            var blocks = 0L
            var settle = 0
            var scanBlocks = 0
            var activeScanner: SignalScanner? = null

            while (running) {
                val n = src.read(i, q)
                if (n <= 0) break
                blocks++

                val sc = scanner
                if (sc !== activeScanner) {
                    activeScanner = sc
                    src.setFrequency(sc?.currentCenter ?: centerHz)
                    settle = src.settleBlocks
                    scanBlocks = 0
                    analyzer.result(spec) // descarta lo acumulado
                    continue
                }
                if (settle > 0) {
                    settle--
                    continue
                }

                if (sc != null) {
                    analyzer.addBlock(i, q, n, 16)
                    if (++scanBlocks >= 2) {
                        val c = sc.currentCenter
                        analyzer.result(spec)
                        val found = sc.analyze(spec, c)
                        cb.onSpectrum(spec, c)
                        cb.onScanStep(c, sc.sweeps, found)
                        src.setFrequency(sc.next())
                        settle = src.settleBlocks
                        scanBlocks = 0
                    }
                    continue
                }

                analyzer.addBlock(i, q, n, 4)
                if (blocks % 2 == 0L) {
                    analyzer.result(spec)
                    cb.onSpectrum(spec, centerHz)
                }

                val na = demod.process(i, q, n, audio)
                val vol = volume * 32767f
                for (k in 0 until na) {
                    pcm[k] = (audio[k] * vol).coerceIn(-32767f, 32767f).toInt().toShort()
                }
                track.write(pcm, 0, na, AudioTrack.WRITE_NON_BLOCKING)
                synchronized(recLock) { recorder?.write(pcm, na) }
                if (blocks % 4 == 0L) cb.onLevel(demod.channelDb, demod.squelchOpen)
            }
        } catch (e: Exception) {
            if (running) error = e.message ?: e.javaClass.simpleName
        } finally {
            try {
                track?.stop()
            } catch (_: IllegalStateException) {
            }
            track?.release()
            src.close()
            stopRecording()
            running = false
            cb.onStopped(error)
        }
    }
}
