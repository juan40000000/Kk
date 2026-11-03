package com.gangas.meli.data

import android.content.Context
import com.gangas.core.model.EngineConfig
import com.gangas.core.model.Site
import com.gangas.core.net.FetchMode
import com.gangas.core.net.MeliCredentials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

data class Settings(
    val site: Site = Site.ARGENTINA,
    val mode: FetchMode = FetchMode.AUTO,
    val pages: Int = 4,
    val minScore: Int = 45,
    val includeUsed: Boolean = true,
    val includeAccessories: Boolean = false,
    val excludeWords: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val token: String = "",
    val scanIntervalMin: Int = 60,
    val alertsEnabled: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state

    private fun load() = Settings(
        site = Site.fromId(prefs.getString("site", null)),
        mode = runCatching { FetchMode.valueOf(prefs.getString("mode", "AUTO")!!) }.getOrDefault(FetchMode.AUTO),
        pages = prefs.getInt("pages", 4),
        minScore = prefs.getInt("minScore", 45),
        includeUsed = prefs.getBoolean("includeUsed", true),
        includeAccessories = prefs.getBoolean("includeAccessories", false),
        excludeWords = prefs.getString("excludeWords", "")!!,
        clientId = prefs.getString("clientId", "")!!,
        clientSecret = prefs.getString("clientSecret", "")!!,
        token = prefs.getString("token", "")!!,
        scanIntervalMin = prefs.getInt("scanIntervalMin", 60),
        alertsEnabled = prefs.getBoolean("alertsEnabled", true),
    )

    fun update(f: (Settings) -> Settings) {
        val s = f(_state.value)
        prefs.edit()
            .putString("site", s.site.id)
            .putString("mode", s.mode.name)
            .putInt("pages", s.pages)
            .putInt("minScore", s.minScore)
            .putBoolean("includeUsed", s.includeUsed)
            .putBoolean("includeAccessories", s.includeAccessories)
            .putString("excludeWords", s.excludeWords)
            .putString("clientId", s.clientId)
            .putString("clientSecret", s.clientSecret)
            .putString("token", s.token)
            .putInt("scanIntervalMin", s.scanIntervalMin)
            .putBoolean("alertsEnabled", s.alertsEnabled)
            .apply()
        _state.value = s
    }

    fun credentials(): MeliCredentials = _state.value.let { MeliCredentials(it.clientId, it.clientSecret, it.token) }

    fun engineConfig(): EngineConfig = _state.value.let {
        EngineConfig(
            includeAccessories = it.includeAccessories,
            includeUsed = it.includeUsed,
            extraExcludeWords = it.excludeWords.split(',', '\n').map(String::trim).filter(String::isNotEmpty),
        )
    }
}

@Serializable
data class Watch(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val maxPrice: Double? = null,
    val minScore: Int = 55,
    val enabled: Boolean = true,
    val lastRun: Long = 0,
    val lastFound: Int = 0,
)

class WatchStore(context: Context) {
    private val file = File(context.filesDir, "watches.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<List<Watch>> = _state

    private fun load(): List<Watch> = runCatching {
        AppJson.decodeFromString<List<Watch>>(file.readText())
    }.getOrDefault(emptyList())

    @Synchronized
    fun mutate(f: (List<Watch>) -> List<Watch>) {
        val n = f(_state.value)
        file.writeText(AppJson.encodeToString(n))
        _state.value = n
    }

    fun add(w: Watch) = mutate { it + w }
    fun remove(id: String) = mutate { l -> l.filterNot { it.id == id } }
    fun upsert(w: Watch) = mutate { l -> if (l.any { it.id == w.id }) l.map { if (it.id == w.id) w else it } else l + w }
}

/** Historial de precios observados por ítem + ítems ya notificados (para no repetir alertas). */
class HistoryStore(context: Context) {
    @Serializable
    private data class Data(
        val prices: Map<String, Double> = emptyMap(),
        val seen: Map<String, Long> = emptyMap(),
        val notified: Map<String, Long> = emptyMap(),
    )

    private val file = File(context.filesDir, "history.json")
    private var data: Data = runCatching { AppJson.decodeFromString<Data>(file.readText()) }.getOrDefault(Data())

    @Synchronized fun prices(): Map<String, Double> = data.prices

    @Synchronized
    fun record(prices: Map<String, Double>) {
        val now = System.currentTimeMillis()
        val seen = data.seen + prices.keys.associateWith { now }
        // conservar los 5000 más recientes
        val keep = seen.entries.sortedByDescending { it.value }.take(5000).map { it.key }.toSet()
        data = data.copy(
            prices = (data.prices + prices).filterKeys { it in keep },
            seen = seen.filterKeys { it in keep },
        )
        save()
    }

    @Synchronized fun wasNotified(id: String, price: Double): Boolean = data.notified.containsKey("$id@${price.toLong()}")

    @Synchronized
    fun markNotified(ids: Collection<Pair<String, Double>>) {
        val now = System.currentTimeMillis()
        val cutoff = now - 30L * 24 * 3600 * 1000
        data = data.copy(notified = (data.notified + ids.associate { "${it.first}@${it.second.toLong()}" to now }).filterValues { it > cutoff })
        save()
    }

    private fun save() = runCatching { file.writeText(AppJson.encodeToString(data)) }
}
