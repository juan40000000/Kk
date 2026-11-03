package com.gangas.meli.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MeliYellow = Color(0xFFFFE600)
val MeliBlue = Color(0xFF2D3277)
val DealGreen = Color(0xFF00A650)
val DealOrange = Color(0xFFFF7733)
val DealRed = Color(0xFFF23D4F)

private val Light = lightColorScheme(
    primary = MeliBlue, onPrimary = Color.White,
    secondary = DealGreen,
    primaryContainer = MeliYellow, onPrimaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFF5F5F5), surface = Color.White,
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9EA6FF), onPrimary = Color(0xFF101340),
    secondary = DealGreen,
    primaryContainer = Color(0xFF4A4300), onPrimaryContainer = MeliYellow,
)

@Composable
fun GangasTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
