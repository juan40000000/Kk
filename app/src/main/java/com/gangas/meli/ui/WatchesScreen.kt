package com.gangas.meli.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gangas.meli.AppContainer
import com.gangas.meli.data.Watch
import com.gangas.meli.work.ScanScheduler
import java.text.DateFormat
import java.util.Date

@Composable
fun WatchesScreen(c: AppContainer, onOpenSearch: (String) -> Unit) {
    val watches by c.watches.state.collectAsStateWithLifecycle()
    val settings by c.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var minScore by remember { mutableStateOf("60") }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Nueva alerta", fontWeight = FontWeight.Bold)
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), label = { Text("Producto") }, singleLine = true)
                    Row {
                        OutlinedTextField(max, { max = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Precio máx. (opcional)") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(minScore, { minScore = it.filter(Char::isDigit).take(3) }, Modifier.weight(0.6f), label = { Text("Score mín.") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (q.isNotBlank()) {
                            c.watches.add(Watch(query = q.trim(), maxPrice = max.toDoubleOrNull(), minScore = minScore.toIntOrNull()?.coerceIn(0, 100) ?: 60))
                            q = ""; max = ""
                            ScanScheduler.reschedule(context)
                        }
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Agregar") }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (settings.alertsEnabled) "Escaneo automático cada ${settings.scanIntervalMin} min" else "Alertas desactivadas en Ajustes",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { ScanScheduler.runNow(context) }) { Text("Escanear ahora") }
            }
        }
        items(watches, key = { it.id }) { w ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.query, fontWeight = FontWeight.Bold)
                        Text(
                            "Score ≥ ${w.minScore}" + (w.maxPrice?.let { " · máx ${formatPrice(it, "")}" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (w.lastRun > 0) Text(
                            "Último: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(w.lastRun))} · ${w.lastFound} nuevas",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(onClick = { onOpenSearch(w.query) }) { Icon(Icons.Filled.PlayArrow, "Buscar ahora") }
                    Switch(checked = w.enabled, onCheckedChange = { c.watches.upsert(w.copy(enabled = it)); ScanScheduler.reschedule(context) })
                    IconButton(onClick = { c.watches.remove(w.id); ScanScheduler.reschedule(context) }) { Icon(Icons.Filled.Delete, "Borrar") }
                }
            }
        }
    }
}
