package com.radioescucha.app.radio

import com.radioescucha.app.dsp.Mode

data class Band(val startHz: Long, val endHz: Long, val name: String, val mode: Mode)

/** Plan de bandas simplificado (orientado a la Región 2 de la UIT) para etiquetar señales. */
object BandPlan {
    private fun mhz(v: Double) = (v * 1_000_000).toLong()

    // Las entradas más específicas van primero: se usa la primera que coincide.
    val bands = listOf(
        Band(mhz(121.45), mhz(121.55), "Emergencia aeronáutica", Mode.AM),
        Band(mhz(156.79), mhz(156.81), "Marino canal 16 (socorro)", Mode.NFM),
        Band(mhz(145.79), mhz(145.81), "ISS voz", Mode.NFM),
        Band(mhz(144.38), mhz(144.40), "APRS", Mode.NFM),
        Band(mhz(446.0), mhz(446.2), "PMR446", Mode.NFM),
        Band(mhz(433.05), mhz(434.79), "ISM 433 MHz (sensores, controles)", Mode.NFM),
        Band(mhz(0.53), mhz(1.71), "Radiodifusión AM (onda media)", Mode.AM),
        Band(mhz(1.8), mhz(2.0), "Radioaficionados 160 m", Mode.LSB),
        Band(mhz(3.5), mhz(4.0), "Radioaficionados 80 m", Mode.LSB),
        Band(mhz(5.9), mhz(6.2), "Onda corta 49 m", Mode.AM),
        Band(mhz(7.0), mhz(7.3), "Radioaficionados 40 m", Mode.LSB),
        Band(mhz(9.4), mhz(9.9), "Onda corta 31 m", Mode.AM),
        Band(mhz(10.1), mhz(10.15), "Radioaficionados 30 m", Mode.CW),
        Band(mhz(11.6), mhz(12.1), "Onda corta 25 m", Mode.AM),
        Band(mhz(14.0), mhz(14.35), "Radioaficionados 20 m", Mode.USB),
        Band(mhz(18.068), mhz(18.168), "Radioaficionados 17 m", Mode.USB),
        Band(mhz(21.0), mhz(21.45), "Radioaficionados 15 m", Mode.USB),
        Band(mhz(24.89), mhz(24.99), "Radioaficionados 12 m", Mode.USB),
        Band(mhz(26.965), mhz(27.405), "Banda ciudadana (CB)", Mode.AM),
        Band(mhz(28.0), mhz(29.7), "Radioaficionados 10 m", Mode.USB),
        Band(mhz(50.0), mhz(54.0), "Radioaficionados 6 m", Mode.USB),
        Band(mhz(88.0), mhz(108.0), "FM comercial", Mode.WFM),
        Band(mhz(108.0), mhz(117.975), "Radionavegación aérea (VOR/ILS)", Mode.AM),
        Band(mhz(117.975), mhz(137.0), "Banda aérea (torres, aviones)", Mode.AM),
        Band(mhz(137.0), mhz(138.0), "Satélites meteorológicos", Mode.WFM),
        Band(mhz(144.0), mhz(144.1), "Radioaficionados 2 m (CW, balizas)", Mode.CW),
        Band(mhz(144.1), mhz(144.3), "Radioaficionados 2 m (SSB)", Mode.USB),
        Band(mhz(144.0), mhz(148.0), "Radioaficionados 2 m", Mode.NFM),
        Band(mhz(148.0), mhz(156.0), "VHF servicios", Mode.NFM),
        Band(mhz(156.0), mhz(162.025), "VHF marino", Mode.NFM),
        Band(mhz(162.4), mhz(162.55), "Radio meteorológica NOAA", Mode.NFM),
        Band(mhz(220.0), mhz(225.0), "Radioaficionados 1,25 m", Mode.NFM),
        Band(mhz(420.0), mhz(450.0), "Radioaficionados 70 cm", Mode.NFM),
        Band(mhz(450.0), mhz(470.0), "UHF servicios / FRS / GMRS", Mode.NFM),
        Band(mhz(902.0), mhz(928.0), "Radioaficionados 33 cm / ISM 915", Mode.NFM),
        Band(mhz(1089.0), mhz(1091.0), "ADS-B (transpondedores de aviones)", Mode.AM),
        Band(mhz(1240.0), mhz(1300.0), "Radioaficionados 23 cm", Mode.NFM),
    )

    fun find(freqHz: Long): Band? = bands.firstOrNull { freqHz in it.startHz until it.endHz }

    fun describe(freqHz: Long) = find(freqHz)?.name ?: "Fuera de plan"

    fun suggestedMode(freqHz: Long): Mode = find(freqHz)?.mode ?: if (freqHz < 30_000_000) Mode.AM else Mode.NFM
}
