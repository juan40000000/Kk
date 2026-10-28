package com.experimento.wifiradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var wifi: WifiManager
    private lateinit var locMgr: LocationManager
    private lateinit var db: Db
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var lastLoc: Location? = null
    private var nets: List<Net> = emptyList()
    private var scans = 0
    private var lastScanOk = true

    private lateinit var status: TextView
    private lateinit var content: FrameLayout
    private lateinit var tabs: List<Button>
    private lateinit var listView: ListView
    private lateinit var radar: RadarView
    private lateinit var mapView: WebView
    private lateinit var statsText: TextView
    private lateinit var statsView: ScrollView
    private val adapter = NetAdapter()

    private val bg = Color.parseColor("#0B1220")
    private val card = Color.parseColor("#111A2E")
    private val fg = Color.parseColor("#E2E8F0")
    private val dim = Color.parseColor("#94A3B8")
    private val accent = Color.parseColor("#22C55E")

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locMgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        db = Db(this)
        setContentView(buildUi())
        showTab(0)
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        startLocation()
        handler.post(scanLoop)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(scanLoop)
        try { unregisterReceiver(scanReceiver) } catch (_: Exception) {}
        locMgr.removeUpdates(locListener)
    }

    // ---------------------------------------------------------------- permisos

    private fun permissionsNeeded(): Array<String> {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p.toTypedArray()
    }

    private fun hasPermissions() = permissionsNeeded().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun askPermissions() {
        if (!hasPermissions()) requestPermissions(permissionsNeeded(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            startLocation()
            doScan()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Permisos necesarios")
                .setMessage("Android exige permiso de ubicación para poder ver las redes WiFi cercanas (porque con ellas se puede saber dónde estás).")
                .setPositiveButton("Reintentar") { _, _ -> askPermissions() }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }

    // ---------------------------------------------------------------- ubicación

    private val locListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val prev = lastLoc
            // Preferimos el GPS; la ubicación de red sólo si no hay una mejor reciente.
            if (prev == null || location.provider == LocationManager.GPS_PROVIDER ||
                location.time - prev.time > 30_000 || location.accuracy <= prev.accuracy
            ) {
                lastLoc = location
                updateStatus()
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!hasPermissions()) return
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (locMgr.isProviderEnabled(p)) {
                    locMgr.requestLocationUpdates(p, 2000L, 0f, locListener, Looper.getMainLooper())
                    locMgr.getLastKnownLocation(p)?.let { if (lastLoc == null) lastLoc = it }
                }
            } catch (_: Exception) {}
        }
        updateStatus()
    }

    // ---------------------------------------------------------------- escaneo WiFi

    private val scanLoop = object : Runnable {
        override fun run() {
            doScan()
            // Android limita a ~4 escaneos cada 2 minutos en primer plano
            // (se puede quitar en Opciones de desarrollador > "Limitación de búsqueda de Wi-Fi").
            handler.postDelayed(this, 8000)
        }
    }

    @Suppress("DEPRECATION")
    private fun doScan() {
        if (!hasPermissions()) return
        if (!wifi.isWifiEnabled) {
            status.text = "⚠ El WiFi está apagado. Actívalo para escanear (no hace falta conectarse a ninguna red)."
            return
        }
        lastScanOk = try { wifi.startScan() } catch (_: Exception) { false }
        // Aunque el sistema rechace el escaneo por limitación, leemos los últimos resultados en caché.
        readResults()
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scans++
            readResults()
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readResults() {
        val results: List<ScanResult> = try { wifi.scanResults ?: emptyList() } catch (_: SecurityException) { emptyList() }
        nets = results.map { r ->
            var std = ""
            if (Build.VERSION.SDK_INT >= 30) {
                std = when (r.wifiStandard) {
                    ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
                    ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
                    ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
                    8 -> "Wi-Fi 7"
                    ScanResult.WIFI_STANDARD_LEGACY -> "Legacy"
                    else -> ""
                }
            }
            val width = when (r.channelWidth) {
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                5 -> 320
                else -> 20
            }
            Net(r.BSSID ?: "", (r.SSID ?: "").removeSurrounding("\""), r.level, r.frequency, r.capabilities ?: "", std, width)
        }.filter { it.bssid.isNotEmpty() }.sortedByDescending { it.rssi }

        lastLoc?.let { loc ->
            // Sólo guardamos si la posición es reciente y razonablemente precisa.
            val age = System.currentTimeMillis() - loc.time
            if (nets.isNotEmpty() && age < 60_000 && loc.accuracy < 80f) db.record(nets, loc)
        }
        refreshViews()
    }

    // ---------------------------------------------------------------- UI

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            fitsSystemWindows = true
        }
        root.addView(TextView(this).apply {
            text = "📡 WiFi Radar"
            setTextColor(accent)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(16), dp(12), dp(16), 0)
        })
        status = TextView(this).apply {
            setTextColor(dim)
            textSize = 12f
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        root.addView(status)

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), 0, dp(8), 0) }
        tabs = listOf("Lista", "Radar", "Mapa", "Stats").mapIndexed { i, label ->
            Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { showTab(i) }
                bar.addView(this, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        root.addView(bar)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        listView = ListView(this).apply {
            adapter = this@MainActivity.adapter
            divider = android.graphics.drawable.ColorDrawable(bg)
            dividerHeight = dp(2)
            setOnItemClickListener { _, _, pos, _ -> showDetail(nets[pos]) }
        }
        radar = RadarView(this)
        mapView = buildMap()
        statsText = TextView(this).apply {
            setTextColor(fg)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        statsView = ScrollView(this).apply {
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(statsText)
                addView(Button(this@MainActivity).apply {
                    text = "Exportar CSV (formato WiGLE)"
                    isAllCaps = false
                    setOnClickListener { exportCsv() }
                })
                addView(Button(this@MainActivity).apply {
                    text = "Borrar todos los datos"
                    isAllCaps = false
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage("¿Borrar todas las redes y el recorrido guardados?")
                            .setPositiveButton("Borrar") { _, _ -> db.clear(); refreshViews() }
                            .setNegativeButton("Cancelar", null).show()
                    }
                })
                addView(Button(this@MainActivity).apply {
                    text = "Abrir ajustes de ubicación"
                    isAllCaps = false
                    setOnClickListener { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                })
            })
        }
        for (v in listOf(listView, radar, mapView, statsView)) content.addView(v)
        return root
    }

    private var currentTab = 0

    private fun showTab(i: Int) {
        currentTab = i
        listOf(listView, radar, mapView, statsView).forEachIndexed { idx, v ->
            v.visibility = if (idx == i) View.VISIBLE else View.GONE
        }
        tabs.forEachIndexed { idx, b -> b.alpha = if (idx == i) 1f else 0.5f }
        refreshViews()
    }

    private fun updateStatus() {
        val loc = lastLoc
        val gps = if (loc == null) "GPS: buscando… (sal al exterior para mejor precisión)"
        else "GPS: %.5f, %.5f ±%dm (%s)".format(loc.latitude, loc.longitude, loc.accuracy.toInt(), loc.provider)
        val throttle = if (!lastScanOk) " · escaneo limitado por Android" else ""
        status.text = "Redes ahora: ${nets.size} · escaneos: $scans$throttle\n$gps"
    }

    private fun refreshViews() {
        updateStatus()
        adapter.notifyDataSetChanged()
        when (currentTab) {
            1 -> radar.nets = nets
            2 -> mapView.evaluateJavascript("window.refresh && refresh()", null)
            3 -> statsText.text = buildStats()
        }
    }

    private fun showDetail(n: Net) {
        val ap = db.aps().firstOrNull { it.bssid == n.bssid }
        val sb = StringBuilder()
        sb.append("SSID: ${n.displaySsid}\nBSSID: ${n.bssid}\nFabricante: ${n.vendor}\n")
        n.hint?.let { sb.append("Pista: $it\n") }
        sb.append("\nSeñal: ${n.rssi} dBm ${WifiUtil.bars(n.rssi)}\n")
        sb.append("Distancia estimada: ~%.1f m\n".format(n.distance))
        sb.append("Banda: ${n.band} · canal ${n.channel} · ${n.widthMhz} MHz\n")
        if (n.standard.isNotEmpty()) sb.append("Estándar: ${n.standard}\n")
        sb.append("Seguridad: ${n.security}${if (WifiUtil.hasWps(n.caps)) " + WPS" else ""}\n")
        sb.append("Capacidades: ${n.caps}\n")
        if (ap != null) {
            sb.append("\nUbicación estimada del router:\n%.6f, %.6f\n".format(ap.lat, ap.lon))
            sb.append("Basado en ${ap.count} observaciones. Mejor señal: ${ap.bestRssi} dBm\n")
            sb.append("Visto por primera vez: ${fmt(ap.firstTs)}")
        } else {
            sb.append("\nAún sin ubicación (hace falta señal GPS).")
        }
        AlertDialog.Builder(this).setTitle(n.displaySsid).setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .apply {
                if (ap != null) setNeutralButton("Ver en mapa") { _, _ ->
                    showTab(2)
                    mapView.evaluateJavascript("window.focusAp && focusAp(${ap.lat}, ${ap.lon}, '${ap.bssid}')", null)
                }
            }.show()
    }

    private fun fmt(ts: Long) = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))

    // ---------------------------------------------------------------- lista

    inner class NetAdapter : BaseAdapter() {
        override fun getCount() = nets.size
        override fun getItem(position: Int) = nets[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setBackgroundColor(card)
                layoutParams = android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(TextView(this@MainActivity).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD })
                addView(TextView(this@MainActivity).apply { textSize = 12f; setTextColor(dim) })
                addView(TextView(this@MainActivity).apply { textSize = 12f; setTextColor(Color.parseColor("#FBBF24")) })
            }
            val n = nets[position]
            val title = row.getChildAt(0) as TextView
            val sub = row.getChildAt(1) as TextView
            val hint = row.getChildAt(2) as TextView
            title.text = "${WifiUtil.bars(n.rssi)}  ${n.displaySsid}"
            title.setTextColor(RadarView.securityColor(n.security))
            sub.text = "${n.rssi} dBm · ~%.0f m · ${n.band} ch${n.channel} · ${n.security}\n${n.bssid} · ${n.vendor}".format(n.distance)
            val h = n.hint
            hint.text = h?.let { "💡 $it" } ?: ""
            hint.visibility = if (h == null) View.GONE else View.VISIBLE
            return row
        }
    }

    // ---------------------------------------------------------------- mapa

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildMap(): WebView = WebView(this).apply {
        setBackgroundColor(bg)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        addJavascriptInterface(Bridge(), "Android")
        loadUrl("file:///android_asset/map.html")
    }

    inner class Bridge {
        @JavascriptInterface
        fun data(): String {
            val o = JSONObject()
            lastLoc?.let { o.put("me", JSONArray().put(it.latitude).put(it.longitude).put(it.accuracy.toDouble())) }
            val track = JSONArray()
            for (p in db.track()) track.put(JSONArray().put(p[0]).put(p[1]))
            o.put("track", track)
            val visible = nets.map { it.bssid }.toSet()
            val aps = JSONArray()
            for (a in db.aps()) {
                val n = Net(a.bssid, a.ssid, a.bestRssi, a.freq, a.caps)
                aps.put(JSONObject().apply {
                    put("b", a.bssid); put("s", n.displaySsid); put("lat", a.lat); put("lon", a.lon)
                    put("sec", n.security); put("r", a.bestRssi); put("n", a.count)
                    put("v", n.vendor); put("ch", n.channel); put("band", n.band)
                    put("now", visible.contains(a.bssid))
                    put("first", fmt(a.firstTs)); put("last", fmt(a.lastTs))
                })
            }
            o.put("aps", aps)
            return o.toString()
        }
    }

    // ---------------------------------------------------------------- estadísticas

    private fun buildStats(): String {
        val aps = db.aps()
        val sb = StringBuilder()
        sb.append("═══ AHORA (${nets.size} redes) ═══\n\n")
        sb.append("Congestión de canales 2.4 GHz:\n")
        val by24 = nets.filter { it.freq < 3000 }.groupBy { it.channel }
        for (ch in 1..13) {
            val c = by24[ch]?.size ?: 0
            if (c > 0 || ch in listOf(1, 6, 11)) sb.append("ch%2d %s %d\n".format(ch, "█".repeat(c), c))
        }
        val free = listOf(1, 6, 11).minByOrNull { ch ->
            // Un canal interfiere con los ±4 vecinos
            nets.filter { it.freq < 3000 && kotlin.math.abs(it.channel - ch) < 5 }.sumOf { 100 + it.rssi }
        }
        if (free != null && nets.isNotEmpty()) sb.append("→ Mejor canal para tu router: $free\n")
        val by5 = nets.filter { it.freq in 3000..5924 }.groupBy { it.channel }
        if (by5.isNotEmpty()) {
            sb.append("\nCanales 5 GHz:\n")
            for ((ch, l) in by5.toSortedMap()) sb.append("ch%3d %s %d\n".format(ch, "█".repeat(l.size), l.size))
        }
        val n6 = nets.count { it.freq >= 5925 }
        if (n6 > 0) sb.append("\nRedes en 6 GHz (Wi-Fi 6E/7): $n6\n")

        sb.append("\n═══ HISTÓRICO ═══\n\n")
        sb.append("Routers únicos registrados: ${aps.size}\n")
        sb.append("Observaciones: ${db.count("obs")}\n")
        sb.append("Puntos de recorrido: ${db.count("track")}\n")
        val all = aps.map { Net(it.bssid, it.ssid, it.bestRssi, it.freq, it.caps) }
        if (all.isNotEmpty()) {
            sb.append("\nSeguridad:\n")
            for ((s, l) in all.groupBy { it.security }.toList().sortedByDescending { it.second.size }) {
                val pct = l.size * 100 / all.size
                sb.append("%-11s %s %d%%\n".format(s, "█".repeat((pct / 4).coerceAtLeast(1)), pct))
            }
            val open = all.count { it.security == "Abierta" || it.security == "WEP" }
            if (open > 0) sb.append("⚠ $open redes abiertas o con WEP (inseguras)\n")
            val wps = aps.count { WifiUtil.hasWps(it.caps) }
            if (wps > 0) sb.append("⚠ $wps redes anuncian WPS\n")

            sb.append("\nFabricantes:\n")
            for ((v, l) in all.groupBy { it.vendor }.toList().sortedByDescending { it.second.size }.take(10))
                sb.append("%-16s %d\n".format(v.take(16), l.size))

            sb.append("\nBandas:\n")
            for ((b, l) in all.groupBy { it.band }) sb.append("%-8s %d\n".format(b, l.size))

            sb.append("\nNombres de red más repetidos:\n")
            for ((s, l) in all.filter { it.ssid.isNotEmpty() }.groupBy { it.ssid }.toList()
                .sortedByDescending { it.second.size }.take(5))
                sb.append("${l.size}× $s\n")

            val curious = all.mapNotNull { n -> n.hint?.let { "${n.displaySsid}: $it" } }.distinct().take(15)
            if (curious.isNotEmpty()) {
                sb.append("\nDispositivos curiosos detectados:\n")
                curious.forEach { sb.append("• $it\n") }
            }
        }
        sb.append("\nNota: la distancia y la ubicación de los routers son estimaciones; ")
        sb.append("mejoran cuanto más te muevas alrededor de cada red.\n")
        return sb.toString()
    }

    // ---------------------------------------------------------------- exportar

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != 42 || resultCode != RESULT_OK || uri == null) return
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                w.write("WigleWifi-1.4,appRelease=1.0,model=${Build.MODEL},release=${Build.VERSION.RELEASE}," +
                    "device=${Build.DEVICE},display=${Build.DISPLAY},board=${Build.BOARD},brand=${Build.BRAND}\n")
                w.write("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
                val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                db.forEachObs { b, s, caps, rssi, freq, lat, lon, alt, acc, ts ->
                    val ssid = "\"" + s.replace("\"", "\"\"") + "\""
                    w.write("$b,$ssid,[${caps.replace(",", ";")}],${f.format(Date(ts))},${WifiUtil.channel(freq)},$rssi,$lat,$lon,$alt,$acc,WIFI\n")
                }
            }
            Toast.makeText(this, "CSV exportado ✔", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun exportCsv() {
        val name = "wifiradar_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".csv"
        startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv").putExtra(Intent.EXTRA_TITLE, name),
            42,
        )
    }
}
