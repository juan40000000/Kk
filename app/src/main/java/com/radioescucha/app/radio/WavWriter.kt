package com.radioescucha.app.radio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Graba audio PCM 16 bits mono en un archivo WAV. */
class WavWriter(val file: File, private val sampleRate: Int) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var buf = ByteBuffer.allocate(0)

    init {
        raf.setLength(0)
        raf.write(header(0))
    }

    fun write(pcm: ShortArray, n: Int) {
        if (buf.capacity() < n * 2) buf = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.clear()
        for (k in 0 until n) buf.putShort(pcm[k])
        raf.write(buf.array(), 0, n * 2)
        dataBytes += n * 2
    }

    fun close() {
        raf.seek(0)
        raf.write(header(dataBytes))
        raf.close()
    }

    private fun header(data: Long): ByteArray {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt((36 + data).toInt()); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1)
        b.putInt(sampleRate); b.putInt(sampleRate * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data.toInt())
        return b.array()
    }
}
