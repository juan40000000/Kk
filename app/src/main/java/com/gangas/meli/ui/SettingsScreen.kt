package com.gangas.meli.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gangas.core.model.Site
import com.gangas.core.net.FetchMode
import com.gangas.meli.AppContainer
import com.gangas.meli.work.ScanScheduler
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(c: AppContainer) {
    val s by c.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Section("Búsqueda")
        Picker("País", s.site.label, Site.entries.map { it.label }) { i -> c.settings.update { it.copy(site = Site.entries[i]) } }
        Picker("Fuente de datos", modeLabel(s.mode), FetchMode.entries.map(::modeLabel)) { i -> c.settings.update { it.copy(mode = FetchMode.entries[i]) } }
        LabeledSlider("Páginas por orden de precio: ${s.pages} (~${s.pages * 50 + (s.pages / 2).coerceAtLeast(1) * 50} publicaciones)",
            s.pages.toFloat(), 1f..12f, 10) { v -> c.settings.update { it.copy(pages = v.roundToInt()) } }
        LabeledSlider("Score mínimo para mostrar/alertar: ${s.minScore}", s.minScore.toFloat(), 0f..95f, 18) { v ->
            c.settings.update { it.copy(minScore = v.roundToInt()) }
        }
        Toggle("Incluir usados", s.includeUsed) { v -> c.settings.update { it.copy(includeUsed = v) } }
        Toggle("Incluir accesorios/repuestos", s.includeAccessories) { v -> c.settings.update { it.copy(includeAccessories = v) } }
        OutlinedTextField(
            s.excludeWords, { v -> c.settings.update { it.copy(excludeWords = v) } }, Modifier.fillMaxWidth(),
            label = { Text("Excluir palabras (separadas por coma)") },
        )

        HorizontalDivider()
        Section("Alertas en segundo plano")
        Toggle("Activar alertas", s.alertsEnabled) { v -> c.settings.update { it.copy(alertsEnabled = v) }; ScanScheduler.reschedule(context) }
        LabeledSlider("Intervalo: ${s.scanIntervalMin} min", s.scanIntervalMin.toFloat(), 15f..360f, 22) { v ->
            c.settings.update { it.copy(scanIntervalMin = (v / 15).roundToInt() * 15) }
        }
        OutlinedButton(onClick = { ScanScheduler.reschedule(context) }) { Text("Aplicar intervalo") }

        HorizontalDivider()
        Section("API oficial de Mercado Libre (recomendado)")
        Text(
            "Creá una app gratis en developers.mercadolibre.com y pegá su Client ID y Secret. " +
                "La app obtiene y renueva el token sola (client_credentials). Sin credenciales se usa el listado web público.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(s.clientId, { v -> c.settings.update { it.copy(clientId = v.trim()) } }, Modifier.fillMaxWidth(), label = { Text("Client ID (App ID)") }, singleLine = true)
        OutlinedTextField(s.clientSecret, { v -> c.settings.update { it.copy(clientSecret = v.trim()) } }, Modifier.fillMaxWidth(),
            label = { Text("Client Secret") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        OutlinedTextField(s.token, { v -> c.settings.update { it.copy(token = v.trim()) } }, Modifier.fillMaxWidth(),
            label = { Text("Access token manual (opcional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
        TextButton(onClick = { openUrl(context, "https://developers.mercadolibre.com.ar/devcenter") }) { Text("Abrir DevCenter") }
    }
}

private fun modeLabel(m: FetchMode) = when (m) {
    FetchMode.AUTO -> "Automático (API → web → navegador)"
    FetchMode.API_ONLY -> "Sólo API (requiere credenciales)"
    FetchMode.WEB_ONLY -> "Web (HTML directo → navegador)"
    FetchMode.BROWSER_ONLY -> "Sólo navegador interno (más lento, más robusto)"
}

@Composable private fun Section(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun Picker(label: String, current: String, options: List<String>, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(current) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = { onPick(i); open = false })
                }
            }
        }
    }
}
