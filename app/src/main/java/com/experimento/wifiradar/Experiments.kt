package com.experimento.wifiradar

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Red a la que está conectado el móvil: (BSSID, RSSI). Se actualiza mucho más seguido que los escaneos. */
typealias ConnectedProvider = () -> Pair<String, Int>?

internal val BG = Color.parseColor("#0B1220")
internal val FG = Color.parseColor("#E2E8F0")
internal val DIM = Color.parseColor("#94A3B8")
internal val GREEN = Color.parseColor("#22C55E")
internal val RED = Color.parseColor("#EF4444")
internal val AMBER = Color.parseColor("#F59E0B")

internal fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

internal fun Context.label(size: Float, color: Int = FG, bold: Boolean = false) = TextView(this).apply {
    textSize = size
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setPadding(dp(16), dp(4), dp(16), dp(4))
}

internal fun Context.button(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setOnClickListener { onClick() }
}

internal fun now() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

/** Panel con ciclo de vida: el Activity lo arranca al mostrar su pestaña y lo para al salir. */
abstract class ExperimentPanel(ctx: Context) : ScrollView(ctx) {
    private var running = false
    protected abstract val periodMs: Long
    protected abstract fun tick()
    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            tick()
            postDelayed(this, periodMs)
        }
    }
    open fun start() { if (!running) { running = true; post(loop) } }
    open fun stop() { running = false; removeCallbacks(loop) }
}

/** Gráfica de línea simple con umbral opcional. */
class ChartView(ctx: Context) : View(ctx) {
    private val values = ArrayDeque<Float>()
    var capacity = 120
    var threshold: Float? = null
    var lineColor = GREEN
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f; style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM; textSize = 26f }

    fun add(v: Float) {
        values.addLast(v)
        while (values.size > capacity) values.removeFirst()
        invalidate()
    }

    fun clear() { values.clear(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#111A2E"))
        if (values.size < 2) return
        var lo = values.min()
        var hi = values.max()
        threshold?.let { lo = min(lo, it); hi = max(hi, it) }
        if (hi - lo < 4f) { val m = (hi + lo) / 2; lo = m - 2; hi = m + 2 }
        val pad = 20f
        fun y(v: Float) = height - pad - (v - lo) / (hi - lo) * (height - 2 * pad)
        val dx = width.toFloat() / (capacity - 1)
        threshold?.let {
            paint.color = RED; paint.strokeWidth = 2f
            canvas.drawLine(0f, y(it), width.toFloat(), y(it), paint)
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        paint.color = lineColor; paint.strokeWidth = 4f
        canvas.drawPath(path, paint)
        canvas.drawText("%.0f".format(hi), 8f, 30f, text)
        canvas.drawText("%.0f".format(lo), 8f, height - 8f, text)
    }
}

internal class Beeper(ctx: Context) {
    private val tone = try { ToneGenerator(AudioManager.STREAM_MUSIC, 80) } catch (_: Exception) { null }
    @Suppress("DEPRECATION")
    private val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    @Suppress("DEPRECATION")
    fun beep(ms: Int = 40, vibrate: Boolean = false) {
        try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, ms) } catch (_: Exception) {}
        if (vibrate) {
            try {
                if (Build.VERSION.SDK_INT >= 26) vib?.vibrate(VibrationEffect.createOneShot(ms.toLong(), 180))
                else vib?.vibrate(ms.toLong())
            } catch (_: Exception) {}
        }
    }
}

// =====================================================================================
//  1. Detector de movimiento: el cuerpo humano (≈60% agua) absorbe y refleja el WiFi.
//     Si alguien cruza entre el móvil y el router, la señal tiembla.
// =====================================================================================

class MotionPanel(ctx: Context, private val connected: ConnectedProvider) : ExperimentPanel(ctx) {
    override val periodMs = 500L

    private val samples = ArrayDeque<Int>()
    private var threshold = 1.5f
    private var calibrating = 0
    private val calib = ArrayList<Float>()
    private var lastAlarm = 0L
    private val beeper = Beeper(ctx)

    private val state = ctx.label(30f, GREEN, bold = true).apply { gravity = Gravity.CENTER }
    private val info = ctx.label(13f, DIM)
    private val rssiChart = ChartView(ctx).apply { capacity = 120 }
    private val activityChart = ChartView(ctx).apply { capacity = 120; lineColor = AMBER }
    private val log = ctx.label(12f, FG).apply { typeface = Typeface.MONOSPACE }
    private val events = ArrayDeque<String>()

    init {
        setBackgroundColor(BG)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(13f, DIM).apply {
                text = "Deja el móvil quieto, con el router en otra parte de la habitación o detrás de una pared. " +
                    "Cuando alguien camina cerca de la línea entre ambos, la señal se altera… y el móvil lo nota."
            })
            addView(state)
            addView(info)
            addView(ctx.label(12f, DIM).apply { text = "Señal de tu WiFi (dBm):" })
            addView(rssiChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(140)))
            addView(ctx.label(12f, DIM).apply { text = "Nivel de perturbación (línea roja = umbral):" })
            addView(activityChart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(110)))
            addView(ctx.button("Calibrar con la habitación vacía (20 s)") {
                calibrating = 40; calib.clear()
            })
            addView(log)
        })
        render(null, 0f)
    }

    override fun tick() {
        val c = connected()
        if (c == null) {
            state.text = "📶 Sin conexión"
            state.setTextColor(DIM)
            info.text = "Conéctate a una red WiFi (por ejemplo la de tu casa): el detector usa la señal de esa conexión."
            return
        }
        samples.addLast(c.second)
        while (samples.size > 16) samples.removeFirst()
        rssiChart.add(c.second.toFloat())

        // Desviación estándar de los últimos ~8 s
        val mean = samples.average()
        val sd = sqrt(samples.sumOf { (it - mean) * (it - mean) } / samples.size).toFloat()
        activityChart.threshold = threshold
        activityChart.add(sd)

        if (calibrating > 0) {
            calib += sd
            calibrating--
            if (calibrating == 0) {
                val base = calib.average().toFloat()
                val peak = calib.max()
                threshold = max(1.0f, max(base * 2.5f, peak * 1.3f))
                addEvent("Calibrado: ruido base %.2f dB → umbral %.2f dB".format(base, threshold))
            }
        }
        render(c, sd)
    }

    private fun render(c: Pair<String, Int>?, sd: Float) {
        val moving = c != null && calibrating == 0 && samples.size >= 6 && sd > threshold
        when {
            calibrating > 0 -> { state.text = "⏳ Calibrando… ${calibrating / 2} s"; state.setTextColor(AMBER) }
            moving -> { state.text = "🚶 ¡MOVIMIENTO!"; state.setTextColor(RED) }
            else -> { state.text = "😴 Todo quieto"; state.setTextColor(GREEN) }
        }
        if (moving && System.currentTimeMillis() - lastAlarm > 4000) {
            lastAlarm = System.currentTimeMillis()
            beeper.beep(150, vibrate = true)
            addEvent("Movimiento detectado (σ = %.1f dB)".format(sd))
        }
        if (c != null) info.text = "Router: ${c.first} · ${c.second} dBm · perturbación %.2f dB · umbral %.2f dB".format(sd, threshold)
    }

    private fun addEvent(e: String) {
        events.addFirst("${now()}  $e")
        while (events.size > 12) events.removeLast()
        log.text = events.joinToString("\n")
    }
}

// =====================================================================================
//  2. Modo Geiger: pitidos cada vez más rápidos a medida que te acercas al router.
// =====================================================================================

class GeigerPanel(
    ctx: Context,
    private val connected: ConnectedProvider,
    private val nets: () -> List<Net>,
) : ExperimentPanel(ctx) {
    override val periodMs = 300L

    private var target: Net? = null
    private var ema: Double? = null
    private val history = ArrayDeque<Double>()
    private var lastBeep = 0L
    private val beeper = Beeper(ctx)

    private val title = ctx.label(20f, FG, bold = true).apply { gravity = Gravity.CENTER }
    private val big = ctx.label(64f, GREEN, bold = true).apply { gravity = Gravity.CENTER }
    private val trend = ctx.label(24f, AMBER, bold = true).apply { gravity = Gravity.CENTER }
    private val meter = ctx.label(22f, GREEN).apply { gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE }
    private val info = ctx.label(13f, DIM).apply { gravity = Gravity.CENTER }
    private val chart = ChartView(ctx).apply { capacity = 150 }

    init {
        setBackgroundColor(BG)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.button("Elegir red a buscar") { pick() })
            addView(title); addView(big); addView(meter); addView(trend); addView(info)
            addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(130)))
            addView(ctx.label(12f, DIM).apply {
                text = "Camina despacio y gira sobre ti mismo: tu cuerpo bloquea la señal, así que hacia donde " +
                    "suene más rápido está el router. La red a la que estás conectado se actualiza cada ~1-3 s; " +
                    "las demás dependen de los escaneos (desactiva \"Limitación de búsqueda de Wi-Fi\" en " +
                    "Opciones de desarrollador para que sea fluido)."
            })
        })
    }

    fun setTarget(n: Net) {
        target = n; ema = null; history.clear(); chart.clear()
    }

    private fun pick() {
        val list = nets()
        if (list.isEmpty()) return
        AlertDialog.Builder(context)
            .setTitle("¿Qué router quieres encontrar?")
            .setItems(list.map { "${it.rssi} dBm  ${it.displaySsid}" }.toTypedArray()) { _, i -> setTarget(list[i]) }
            .show()
    }

    private fun currentRssi(): Int? {
        val t = target ?: return null
        val c = connected()
        if (c != null && c.first.equals(t.bssid, ignoreCase = true)) return c.second
        return nets().firstOrNull { it.bssid == t.bssid }?.rssi
    }

    override fun tick() {
        if (target == null) {
            val c = connected()
            val n = c?.let { cc -> nets().firstOrNull { it.bssid.equals(cc.first, ignoreCase = true) } }
            if (n != null) setTarget(n) else {
                title.text = "Elige una red para empezar"
                big.text = "—"; meter.text = ""; trend.text = ""; info.text = ""
                return
            }
        }
        val t = target!!
        val r = currentRssi()
        title.text = "🎯 ${t.displaySsid}"
        if (r == null) {
            big.text = "?"; big.setTextColor(DIM); trend.text = "Fuera de alcance"; return
        }
        val e = ema?.let { it * 0.7 + r * 0.3 } ?: r.toDouble()
        ema = e
        history.addLast(e)
        while (history.size > 17) history.removeFirst() // ~5 s
        chart.add(e.toFloat())

        // 0 (lejos, -95 dBm) .. 1 (pegado, -35 dBm)
        val p = ((e + 95) / 60).coerceIn(0.0, 1.0)
        big.text = "${r} dBm"
        big.setTextColor(if (p > 0.7) RED else if (p > 0.4) AMBER else GREEN)
        val filled = (p * 20).toInt()
        meter.text = "▮".repeat(filled) + "▯".repeat(20 - filled)
        info.text = "≈ %.1f m · ${t.band} canal ${t.channel}".format(WifiUtil.distanceMeters(r, t.freq))
        val delta = e - history.first()
        trend.text = when {
            history.size < 8 -> ""
            delta > 2 -> "🔥 Más caliente"
            delta < -2 -> "🧊 Más frío"
            else -> "· · ·"
        }

        // Intervalo entre pitidos: 1.5 s lejos → 60 ms pegado
        val interval = (1500 - p * 1440).toLong()
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastBeep >= interval) {
            lastBeep = nowMs
            beeper.beep(30, vibrate = p > 0.75)
        }
    }

}

// =====================================================================================
//  3. ¿Dónde estoy? Posicionamiento en interiores sin GPS por "huella" WiFi.
// =====================================================================================

class PlacesPanel(ctx: Context, private val db: Db) : ExperimentPanel(ctx) {
    override val periodMs = 1000L

    private var fingerprints: Map<String, Map<String, Double>> = db.fingerprints()
    private var training: String? = null
    private var trainUntil = 0L
    private var trainScans = 0
    private var lastNets: List<Net> = emptyList()

    private val result = ctx.label(30f, GREEN, bold = true).apply { gravity = Gravity.CENTER }
    private val ranking = ctx.label(14f, FG).apply { typeface = Typeface.MONOSPACE }
    private val trainInfo = ctx.label(13f, AMBER)
    private val placesBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(BG)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(13f, DIM).apply {
                text = "Cada sitio tiene una \"huella\" única: qué routers se oyen y con qué fuerza. " +
                    "Ve a cada habitación, pulsa el botón y ponle nombre. Luego muévete por la casa: la app " +
                    "adivinará dónde estás sin GPS (así funciona la ubicación en centros comerciales)."
            })
            addView(result)
            addView(ranking)
            addView(trainInfo)
            addView(ctx.button("＋ Enseñar este lugar (30 s)") { askName() })
            addView(placesBox)
        })
        renderPlaces()
    }

    private fun askName() {
        val input = EditText(context).apply {
            hint = "Cocina, Habitación, Baño…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(context).setTitle("¿Cómo se llama este lugar?").setView(input)
            .setPositiveButton("Empezar") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    training = name; trainUntil = System.currentTimeMillis() + 30_000; trainScans = 0
                    if (lastNets.isNotEmpty()) addSample(name, lastNets)
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun addSample(place: String, nets: List<Net>) {
        db.addFingerprint(place, nets)
        trainScans++
        fingerprints = db.fingerprints()
        renderPlaces()
    }

    /** El Activity llama a esto con cada escaneo nuevo. */
    fun onScan(nets: List<Net>) {
        lastNets = nets
        val t = training
        if (t != null && System.currentTimeMillis() < trainUntil && nets.isNotEmpty()) addSample(t, nets)
        if (isShown) guess()
    }

    override fun tick() {
        val t = training
        if (t != null) {
            val left = (trainUntil - System.currentTimeMillis()) / 1000
            if (left <= 0) {
                trainInfo.text = "✔ \"$t\" aprendido con $trainScans escaneos. Puedes repetirlo para mejorar la precisión."
                training = null
            } else {
                trainInfo.text = "📝 Aprendiendo \"$t\"… ${left}s · escaneos: $trainScans (muévete un poco por la habitación)"
            }
        }
    }

    override fun start() { super.start(); guess() }

    private fun guess() {
        if (fingerprints.isEmpty()) {
            result.text = "Sin lugares todavía"; result.setTextColor(DIM); ranking.text = ""; return
        }
        if (lastNets.isEmpty()) { result.text = "Esperando escaneo…"; return }
        val scan = lastNets.associate { it.bssid to it.rssi.toDouble() }
        val dist = fingerprints.mapValues { (_, fp) -> distance(scan, fp) }
        // Convertimos distancias en "probabilidades" con un softmax
        val w = dist.mapValues { exp(-it.value / 3.0) }
        val total = w.values.sum().takeIf { it > 0 } ?: 1.0
        val ranked = w.toList().sortedByDescending { it.second }
        val (best, bw) = ranked.first()
        val conf = (bw / total * 100).toInt()
        result.text = "📍 $best"
        result.setTextColor(if (conf >= 60) GREEN else AMBER)
        ranking.text = ranked.joinToString("\n") { (p, v) ->
            val pct = (v / total * 100).toInt()
            "%-14s %s %d%%  (dif %.1f dB)".format(p.take(14), "█".repeat(pct / 5), pct, dist[p])
        }
    }

    /** Distancia RMS entre dos huellas; una red que no se oye cuenta como -100 dBm. */
    private fun distance(a: Map<String, Double>, b: Map<String, Double>): Double {
        val keys = (a.keys + b.keys).filter { (a[it] ?: -100.0) > -88 || (b[it] ?: -100.0) > -88 }
        if (keys.isEmpty()) return 99.0
        val sum = keys.sumOf { val d = (a[it] ?: -100.0) - (b[it] ?: -100.0); d * d }
        return sqrt(sum / keys.size)
    }

    private fun renderPlaces() {
        placesBox.removeAllViews()
        if (fingerprints.isEmpty()) return
        placesBox.addView(context.label(12f, DIM).apply { text = "Lugares aprendidos (mantén pulsado para borrar):" })
        for ((p, fp) in fingerprints) {
            placesBox.addView(context.label(15f, FG).apply {
                text = "• $p  (${fp.size} redes)"
                setOnLongClickListener {
                    AlertDialog.Builder(context).setMessage("¿Borrar \"$p\"?")
                        .setPositiveButton("Borrar") { _, _ -> db.deletePlace(p); fingerprints = db.fingerprints(); renderPlaces(); guess() }
                        .setNegativeButton("Cancelar", null).show()
                    true
                }
            })
        }
    }
}
