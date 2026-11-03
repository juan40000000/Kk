package com.gangas.meli.ui

import java.text.NumberFormat
import java.util.Locale

private val nf = NumberFormat.getNumberInstance(Locale("es", "AR")).apply { maximumFractionDigits = 0 }

fun formatPrice(value: Double, currency: String): String {
    val symbol = when (currency) {
        "ARS", "MXN", "CLP", "COP", "UYU", "" -> "$"
        "BRL" -> "R$"
        "PEN" -> "S/"
        "USD" -> "US$"
        else -> currency
    }
    return "$symbol ${nf.format(value)}"
}
