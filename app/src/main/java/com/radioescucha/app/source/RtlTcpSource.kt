package com.radioescucha.app.source

import com.radioescucha.app.dsp.Demodulator
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cliente del protocolo rtl_tcp. Sirve tanto para un dongle RTL-SDR conectado por USB
 * (a través de la app "RTL-SDR driver", que expone rtl_tcp en 127.0.0.1) como para un servidor en la red.
 */
class RtlTcpSource(private val host: String, private val port: Int) : IqSource {
    override val name = "rtl_tcp $host:$port"
    override val settleBlocks = 6

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
    private var bytes = ByteArray(0)
    private val lut = FloatArray(256) { (it - 127.4f) / 128f }

    @Volatile private var pendingFreq = -1L
    @Volatile private var pendingGain: Int? = null
    @Volatile private var pendingPpm: Int? = null
    var tunerName = "desconocido"
        private set

    override fun open() {
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 5000
        s.tcpNoDelay = true
        s.receiveBufferSize = 256 * 1024
        socket = s
        val inp = DataInputStream(s.getInputStream().buffered(64 * 1024))
        input = inp
        output = s.getOutputStream()
        val header = ByteArray(12)
        inp.readFully(header)
        if (header[0] != 'R'.code.toByte() || header[1] != 'T'.code.toByte() || header[2] != 'L'.code.toByte()) {
            throw IOException("El servidor no respondió como rtl_tcp")
        }
        val tuner = ((header[4].toInt() and 0xff) shl 24) or ((header[5].toInt() and 0xff) shl 16) or
            ((header[6].toInt() and 0xff) shl 8) or (header[7].toInt() and 0xff)
        tunerName = when (tuner) {
            1 -> "E4000"; 2 -> "FC0012"; 3 -> "FC0013"; 4 -> "FC2580"; 5 -> "R820T/R828D"; 6 -> "R828D"
            else -> "desconocido ($tuner)"
        }
        command(0x02, Demodulator.INPUT_RATE)
        applyPending()
    }

    private fun command(cmd: Int, value: Int) {
        val out = output ?: return
        val b = byteArrayOf(
            cmd.toByte(),
            (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte(),
        )
        out.write(b)
        out.flush()
    }

    private fun applyPending() {
        val f = pendingFreq
        if (f >= 0) {
            pendingFreq = -1
            command(0x01, f.toInt())
        }
        pendingPpm?.let { pendingPpm = null; command(0x05, it) }
        pendingGain?.let { g ->
            pendingGain = null
            if (g < 0) {
                command(0x03, 0) // ganancia automática del sintonizador
                command(0x08, 1) // AGC del RTL2832
            } else {
                command(0x08, 0)
                command(0x03, 1)
                command(0x04, g)
            }
        }
    }

    override fun read(i: FloatArray, q: FloatArray): Int {
        applyPending()
        val inp = input ?: return -1
        val n = i.size
        if (bytes.size < 2 * n) bytes = ByteArray(2 * n)
        inp.readFully(bytes, 0, 2 * n)
        for (k in 0 until n) {
            i[k] = lut[bytes[2 * k].toInt() and 0xff]
            q[k] = lut[bytes[2 * k + 1].toInt() and 0xff]
        }
        return n
    }

    override fun setFrequency(hz: Long) { pendingFreq = hz }
    override fun setGain(tenthsDb: Int) { pendingGain = tenthsDb }
    override fun setPpm(ppm: Int) { pendingPpm = ppm }

    override fun close() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        input = null
        output = null
    }
}
