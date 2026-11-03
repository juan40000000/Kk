package com.gangas.meli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gangas.core.model.Deal
import com.gangas.core.model.Risk
import com.gangas.meli.data.Watch
import com.gangas.meli.work.ScanScheduler

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: SearchViewModel) {
    val st by vm.state.collectAsStateWithLifecycle()
    val settings by vm.container.settings.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val deals = st.visibleDeals(settings.minScore)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = st.query, onValueChange = vm::setQuery,
                modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Ej: iphone 15, ps5, rtx 4070…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                trailingIcon = { IconButton(onClick = vm::search) { Icon(Icons.Filled.Search, "Buscar") } },
            )
            IconButton(onClick = {
                if (st.query.isNotBlank()) {
                    vm.container.watches.add(Watch(query = st.query.trim(), minScore = settings.minScore))
                    ScanScheduler.reschedule(context)
                    android.widget.Toast.makeText(context, "Alerta creada para \"${st.query}\"", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Filled.AddAlert, "Crear alerta") }
        }
        if (st.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortBy.entries.forEach { s ->
                FilterChip(selected = st.sortBy == s, onClick = { vm.setSort(s) }, label = { Text(s.label) })
            }
            FilterChip(selected = st.hideHighRisk, onClick = vm::toggleHideRisk, label = { Text("Ocultar riesgo alto") })
            FilterChip(selected = st.onlyAboveThreshold, onClick = vm::toggleThreshold, label = { Text("Score ≥ ${settings.minScore}") })
        }

        st.outcome?.let { o ->
            val a = o.analysis
            Text(
                "${o.fetched} publicaciones (${o.source}) · ${a.analyzed} relevantes · " +
                    "${a.discardedAccessories} accesorios · mediana ${a.marketMedian?.let { formatPrice(it, "") } ?: "-"} · ${deals.size} gangas",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        st.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
        if (st.outcome == null && !st.loading && st.error == null) {
            Column(Modifier.padding(24.dp)) {
                Text("Buscá un producto y el motor analiza cientos de publicaciones:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "• Calcula un precio de referencia con los productos MÁS SIMILARES (no un promedio general).",
                    "• Usa estadística robusta (mediana, MAD, z-score modificado, vallas de Tukey).",
                    "• Detecta errores de tipeo: precios ~10×, 100× o 1000× más bajos.",
                    "• Descarta accesorios (fundas, vidrios, repuestos) y precios señuelo (\"consultar\", seña, cuotas).",
                    "• Marca riesgos: réplicas, usados, demasiado bueno para ser verdad.",
                    "• Creá alertas y recibí notificaciones en segundo plano.",
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(deals, key = { it.listing.id }) { d -> DealCard(d) { openUrl(context, d.listing.permalink) } }
        }
    }
}

fun scoreColor(score: Int): Color = when {
    score >= 75 -> DealGreen
    score >= 50 -> DealOrange
    else -> Color(0xFF9E9E9E)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DealCard(d: Deal, onClick: () -> Unit) {
    val l = d.listing
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.padding(10.dp)) {
            Box {
                AsyncImage(
                    model = l.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEEEEE)),
                )
                Box(
                    Modifier.align(Alignment.TopStart).padding(4.dp).size(34.dp).clip(CircleShape).background(scoreColor(d.score)),
                    contentAlignment = Alignment.Center,
                ) { Text("${d.score}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPrice(l.price, l.currency), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    if (d.belowReference > 0) Text(
                        "-${(d.belowReference * 100).toInt()}%", color = DealGreen, fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Ref. ${formatPrice(d.referencePrice, l.currency)} · ${d.comparables} similares · z=${"%.1f".format(d.robustZ)}",
                    style = MaterialTheme.typography.labelSmall,
                )
                l.originalPrice?.let {
                    Text("Antes ${formatPrice(it, l.currency)}", style = MaterialTheme.typography.labelSmall, textDecoration = TextDecoration.LineThrough)
                }
                d.previousPrice?.takeIf { it != l.price }?.let {
                    Text("Último visto: ${formatPrice(it, l.currency)}", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RiskTag(d.risk)
                    d.signals.forEach { s -> Tag(s.label, if (s.positive) DealGreen else DealRed) }
                }
            }
        }
    }
}

@Composable
private fun RiskTag(r: Risk) = when (r) {
    Risk.LOW -> Tag("Riesgo bajo", DealGreen)
    Risk.MEDIUM -> Tag("Riesgo medio", DealOrange)
    Risk.HIGH -> Tag("Riesgo ALTO", DealRed)
}

@Composable
fun Tag(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
