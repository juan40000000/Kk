package com.radioescucha.app.dsp

/** Modos de demodulación. [bandwidthLow]/[bandwidthHigh] son los bordes del canal respecto a la portadora. */
enum class Mode(val label: String, val bandwidthLow: Int, val bandwidthHigh: Int, val defaultStep: Int) {
    WFM("WFM", -90_000, 90_000, 100_000),
    NFM("NFM", -7_000, 7_000, 12_500),
    AM("AM", -5_000, 5_000, 5_000),
    USB("USB", 0, 3_000, 100),
    LSB("LSB", -3_000, 0, 100),
    CW("CW", -250, 250, 100);
}
