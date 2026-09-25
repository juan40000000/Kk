package com.radioescucha.app.radio

import android.content.Context
import com.radioescucha.app.dsp.Mode

data class Bookmark(val freqHz: Long, val mode: Mode, val name: String, val builtIn: Boolean = false)

/** Frecuencias interesantes predefinidas + marcadores del usuario guardados en SharedPreferences. */
class Bookmarks(context: Context) {
    companion object {
        private fun b(mhz: Double, mode: Mode, name: String) = Bookmark((mhz * 1_000_000).toLong(), mode, name, true)

        val PRESETS = listOf(
            b(145.800, Mode.NFM, "ISS – voz de astronautas (bajada)"),
            b(145.825, Mode.NFM, "ISS – digipetidor APRS"),
            b(437.800, Mode.NFM, "ISS – repetidor cruzado (bajada)"),
            b(144.390, Mode.NFM, "APRS América"),
            b(144.800, Mode.NFM, "APRS Europa"),
            b(146.520, Mode.NFM, "Llamada simplex 2 m (América)"),
            b(145.500, Mode.NFM, "Llamada simplex 2 m (Región 1)"),
            b(144.050, Mode.CW, "CW / balizas 2 m"),
            b(144.200, Mode.USB, "SSB 2 m (llamada)"),
            b(137.100, Mode.WFM, "Satélites Meteor-M (LRPT)"),
            b(137.900, Mode.WFM, "Satélites Meteor-M (alternativa)"),
            b(121.500, Mode.AM, "Emergencia aeronáutica"),
            b(118.100, Mode.AM, "Banda aérea (torre típica)"),
            b(156.800, Mode.NFM, "Marino canal 16 (socorro y llamada)"),
            b(162.400, Mode.NFM, "Radio meteorológica NOAA"),
            b(446.00625, Mode.NFM, "PMR446 canal 1"),
            b(462.5625, Mode.NFM, "FRS canal 1"),
            b(433.920, Mode.NFM, "ISM 433 (sensores, llaveros)"),
            b(27.065, Mode.AM, "CB canal 9 (emergencias)"),
            b(28.400, Mode.USB, "10 m SSB"),
            b(14.074, Mode.USB, "FT8 20 m (requiere HF)"),
            b(7.074, Mode.USB, "FT8 40 m (requiere HF)"),
            b(7.100, Mode.LSB, "40 m fonía (requiere HF)"),
            b(10.000, Mode.AM, "WWV señal horaria 10 MHz (HF)"),
            b(15.000, Mode.AM, "WWV señal horaria 15 MHz (HF)"),
            b(100.100, Mode.WFM, "FM comercial (ejemplo)"),
        )
    }

    private val prefs = context.getSharedPreferences("marcadores", Context.MODE_PRIVATE)

    fun user(): List<Bookmark> {
        val raw = prefs.getString("lista", "") ?: ""
        return raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split("|", limit = 3)
            if (p.size < 3) return@mapNotNull null
            val f = p[0].toLongOrNull() ?: return@mapNotNull null
            val m = runCatching { Mode.valueOf(p[1]) }.getOrDefault(Mode.NFM)
            Bookmark(f, m, p[2])
        }
    }

    fun all(): List<Bookmark> = user() + PRESETS

    fun add(bm: Bookmark) = save(user() + bm.copy(name = bm.name.replace("|", "/").replace("\n", " ")))

    fun remove(bm: Bookmark) = save(user().filterNot { it.freqHz == bm.freqHz && it.name == bm.name })

    private fun save(list: List<Bookmark>) {
        prefs.edit().putString("lista", list.joinToString("\n") { "${it.freqHz}|${it.mode.name}|${it.name}" }).apply()
    }
}
