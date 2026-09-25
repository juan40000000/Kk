package com.radioescucha.app.source

/**
 * Fuente de muestras IQ a [com.radioescucha.app.dsp.Demodulator.INPUT_RATE].
 * Los cambios de parámetros se aplican de forma diferida desde el hilo que llama a [read],
 * por lo que se pueden invocar desde el hilo de la interfaz.
 */
interface IqSource {
    val name: String

    /** Abre la conexión o el dispositivo. Bloqueante; se llama desde el hilo del motor. */
    fun open()

    /** Lee hasta i.size muestras complejas normalizadas a ±1. Devuelve la cantidad leída o -1 al terminar. */
    fun read(i: FloatArray, q: FloatArray): Int

    fun setFrequency(hz: Long)

    /** Ganancia en décimas de dB; un valor negativo activa la ganancia automática. */
    fun setGain(tenthsDb: Int)

    fun setPpm(ppm: Int)

    /** Bloques a descartar tras un cambio de frecuencia (datos viejos en tránsito). */
    val settleBlocks: Int

    fun close()
}
