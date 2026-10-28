package com.experimento.wifiradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// =====================================================================================
//  4. WiFi RTT (802.11mc): distancia real al router por tiempo de vuelo de la señal.
// =====================================================================================

class RttPanel(
    ctx: Context,
    private val nets: () -> List<Net>,
) : ExperimentPanel(ctx) {
    override val periodMs = 1500L

    private val rtt = if (Build.VERSION.SDK_INT >= 28)
        ctx.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager else null
    private val supported = Build.VERSION.SDK_INT >= 28 &&
        ctx.packageManager.hasSystemFeature("android.hardware.wifi.rtt") && rtt != null

    private val title = ctx.label(18f, FG, bold = true)
    private val status = ctx.label(13f, DIM)
    private val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private var ranging = false

    init {
        setBackgroundColor(BG)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(13f, DIM).apply {
                text = "WiFi RTT mide el tiempo que tarda la señal en ir y volver al router, a la velocidad de la " +
                    "luz. Da la distancia con 1-2 m de error (no la estimación por intensidad). Necesita que el " +
                    "móvil y el router sean compatibles con 802.11mc (Nest Wifi, algunos APs de oficina)."
            })
            addView(title)
            addView(status)
            addView(box)
        })
        if (!supported) {
            title.text = "No disponible"
            status.text = if (Build.VERSION.SDK_INT < 28)
                "Tu Android es anterior a 9.0." else "Este móvil no tiene hardware WiFi RTT."
        } else title.text = "📏 Midiendo distancias reales…"
    }

    @RequiresApi(28)
    override fun tick() {
        if (!supported || ranging) return
        val candidates = nets().filter { it.freq in 2400..5900 }
            .sortedByDescending { it.rssi }.take(RangingRequest.getMaxPeers())
        if (candidates.isEmpty()) { status.text = "Esperando escaneo WiFi…"; return }
        val builder = RangingRequest.Builder()
        for (n in candidates) {
            try { builder.addAccessPoint(makeScan(n)) } catch (_: Throwable) {}
        }
        ranging = true
        status.text = "Consultando ${candidates.size} redes… (las que no soporten RTT fallarán)"
        try {
            rtt!!.startRanging(builder.build(), context.mainExecutor, object : RangingResultCallback() {
                override fun onRangingFailure(code: Int) { ranging = false; status.text = "Ranging falló (código $code)" }
                override fun onRangingResults(results: List<RangingResult>) {
                    ranging = false; render(results)
                }
            })
        } catch (e: Exception) { ranging = false; status.text = "Error: ${e.message}" }
    }

    @Suppress("DEPRECATION")
    private fun makeScan(n: Net): ScanResult = ScanResult().apply {
        BSSID = n.bssid; frequency = n.freq; level = n.rssi
    }

    @RequiresApi(28)
    private fun render(results: List<RangingResult>) {
        box.removeAllViews()
        val ok = results.filter { it.status == RangingResult.STATUS_SUCCESS }
        val byBssid = nets().associateBy { it.bssid.lowercase() }
        if (ok.isEmpty()) {
            status.text = "Ningún router cercano soporta RTT (es lo normal en casa). " +
                "Compatibles: Google Nest Wifi, puntos de acceso de empresa Wi-Fi 6."
            return
        }
        status.text = "✔ ${ok.size} routers respondieron con distancia real:"
        for (r in ok.sortedBy { it.distanceMm }) {
            val name = byBssid[r.macAddress?.toString()?.lowercase()]?.displaySsid ?: r.macAddress.toString()
            box.addView(context.label(15f, GREEN, bold = true).apply {
                text = "📏 $name: %.2f m  (±%.2f m)".format(r.distanceMm / 1000.0, r.distanceStdDevMm / 1000.0)
            })
        }
    }
}

// =====================================================================================
//  5. Magnetómetro: el móvil como detector de metales.
// =====================================================================================

class MagnetPanel(ctx: Context) : ExperimentPanel(ctx), SensorEventListener {
    override val periodMs = 100L

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private var magnitude = 0f
    private var baseMag = 0f
    private val recent = ArrayDeque<Float>()
    private val beeper = Beeper(ctx)
    private var lastBeep = 0L

    private val big = ctx.label(56f, GREEN, bold = true).apply { gravity = android.view.Gravity.CENTER }
    private val unit = ctx.label(16f, DIM).apply { gravity = android.view.Gravity.CENTER }
    private val delta = ctx.label(24f, FG, bold = true).apply { gravity = android.view.Gravity.CENTER }
    private val chart = ChartView(ctx).apply { capacity = 150; lineColor = Color.parseColor("#A855F7") }
    private val state = ctx.label(30f, DIM, bold = true).apply { gravity = android.view.Gravity.CENTER }

    init {
        setBackgroundColor(BG)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(13f, DIM).apply {
                text = "El magnetómetro mide el campo magnético en microteslas (µT). La Tierra marca unos 25-65 µT; " +
                    "el acero, los imanes y los cables con corriente lo alteran. Acerca el móvil a un enchufe, " +
                    "un altavoz, una tubería o un tornillo oculto en la pared."
            })
            addView(big); addView(unit); addView(state); addView(delta)
            addView(chart, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(140)))
            addView(ctx.button("Poner a cero aquí (calibrar)") { baseMag = magnitude })
        })
        if (sensor == null) { big.text = "—"; state.text = "Sin magnetómetro"; unit.text = "Este móvil no tiene sensor de campo magnético" }
    }

    override fun start() {
        super.start()
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }
    override fun stop() { super.stop(); sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        magnitude = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        if (baseMag == 0f) baseMag = magnitude
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun tick() {
        if (sensor == null) return
        recent.addLast(magnitude)
        while (recent.size > 5) recent.removeFirst()
        val m = recent.average().toFloat()
        chart.add(m)
        big.text = "%.0f".format(m)
        unit.text = "µT  ·  base %.0f µT".format(baseMag)
        val d = m - baseMag
        delta.text = (if (d >= 0) "+" else "") + "%.0f µT sobre la base".format(d)
        val a = abs(d)
        when {
            a > 80 -> { state.text = "🧲 ¡METAL FUERTE!"; state.setTextColor(RED); big.setTextColor(RED) }
            a > 25 -> { state.text = "🔩 Metal cerca"; state.setTextColor(AMBER); big.setTextColor(AMBER) }
            else -> { state.text = "· campo normal ·"; state.setTextColor(GREEN); big.setTextColor(GREEN) }
        }
        val now = System.currentTimeMillis()
        if (a > 25) {
            val interval = (700 - min(a, 150f) * 4).toLong().coerceAtLeast(60)
            if (now - lastBeep > interval) { lastBeep = now; beeper.beep(25, vibrate = a > 80) }
        }
    }
}

// =====================================================================================
//  6. Sonar ultrasónico: gestos de la mano por efecto Doppler (altavoz + micro).
// =====================================================================================

class SonarPanel(ctx: Context) : ExperimentPanel(ctx) {
    override val periodMs = 60L

    private val sampleRate = 44100
    private val toneHz = 19000.0           // Casi inaudible para la mayoría
    private var track: AudioTrack? = null
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var shift = 0.0      // Desplazamiento Doppler (Hz): + acercar, - alejar
    @Volatile private var energy = 0.0
    private var hasMic = true

    private val state = ctx.label(40f, GREEN, bold = true).apply { gravity = android.view.Gravity.CENTER }
    private val info = ctx.label(14f, DIM).apply { gravity = android.view.Gravity.CENTER }
    private val gesture = ctx.label(28f, AMBER, bold = true).apply { gravity = android.view.Gravity.CENTER }
    private val wave = DopplerView(ctx)
    private var lastGesture = 0L
    private val beeper = Beeper(ctx)

    init {
        setBackgroundColor(BG)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(ctx.label(13f, DIM).apply {
                text = "El móvil emite un tono de 19 kHz (casi nadie lo oye) por el altavoz y escucha su eco con el " +
                    "micrófono. Si mueves la mano hacia el móvil, el eco sube de frecuencia (efecto Doppler, como " +
                    "la sirena de una ambulancia); si la alejas, baja. Mueve la mano a 10-30 cm por encima del " +
                    "teléfono. Sube el volumen. Nota: algunos altavoces no llegan a 19 kHz."
            })
            addView(state); addView(gesture); addView(info)
            addView(wave, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(160)))
        })
    }

    override fun start() {
        super.start()
        if (running) return
        running = true
        startTone()
        startListening()
    }

    override fun stop() {
        super.stop()
        running = false
        try { thread?.join(300) } catch (_: Exception) {}
        thread = null
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        try { record?.stop(); record?.release() } catch (_: Exception) {}
        track = null; record = null
    }

    private fun startTone() {
        val n = sampleRate
        val buf = ShortArray(n)
        for (i in 0 until n) buf[i] = (sin(2 * Math.PI * toneHz * i / sampleRate) * 0.9 * Short.MAX_VALUE).toInt().toShort()
        try {
            @Suppress("DEPRECATION")
            val t = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, buf.size * 2, AudioTrack.MODE_STATIC,
            )
            t.write(buf, 0, buf.size)
            t.setLoopPoints(0, buf.size, -1)
            t.play()
            track = t
        } catch (_: Exception) {}
    }

    private fun startListening() {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) { hasMic = false; return }
        val size = max(minBuf, 4096)
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size * 2)
        } catch (_: SecurityException) { hasMic = false; return } catch (_: Exception) { hasMic = false; return }
        if (rec.state != AudioRecord.STATE_INITIALIZED) { hasMic = false; return }
        record = rec
        try { rec.startRecording() } catch (_: Exception) { hasMic = false; return }

        thread = Thread {
            val buf = ShortArray(size)
            // Analizamos un abanico de frecuencias alrededor del tono con el algoritmo de Goertzel.
            val binHz = 40.0
            val freqs = DoubleArray(21) { toneHz + (it - 10) * binHz }
            while (running) {
                val read = try { rec.read(buf, 0, buf.size) } catch (_: Exception) { -1 }
                if (read <= 0) continue
                val mags = DoubleArray(freqs.size)
                for (f in freqs.indices) mags[f] = goertzel(buf, read, freqs[f])
                val carrier = mags[10]
                // Centro de masa del espectro desplazado respecto al tono original
                var up = 0.0; var down = 0.0; var sum = 0.0
                for (f in freqs.indices) {
                    if (f == 10) continue
                    val w = mags[f]
                    sum += w
                    if (freqs[f] > toneHz) up += w * (freqs[f] - toneHz) else down += w * (toneHz - freqs[f])
                }
                shift = if (sum > 0) (up - down) / sum else 0.0
                energy = sum / (carrier + 1)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /** Potencia de una frecuencia concreta en el bloque (filtro de Goertzel). */
    private fun goertzel(buf: ShortArray, len: Int, freq: Double): Double {
        val w = 2 * Math.PI * freq / sampleRate
        val coeff = 2 * cos(w)
        var s0: Double; var s1 = 0.0; var s2 = 0.0
        for (i in 0 until len) { s0 = buf[i] + coeff * s1 - s2; s2 = s1; s1 = s0 }
        return sqrt(s1 * s1 + s2 * s2 - coeff * s1 * s2)
    }

    override fun tick() {
        if (!hasMic) {
            state.text = "🎤 Sin micrófono"
            info.text = "Concede el permiso de micrófono (se pide al abrir esta pestaña) o este móvil no deja grabar."
            return
        }
        if (track == null) {
            state.text = "🔇 Sin altavoz 19 kHz"; info.text = "No se pudo generar el tono."; return
        }
        wave.push(shift, energy)
        val s = shift
        when {
            energy < 0.05 -> { state.text = "· · ·"; state.setTextColor(DIM) }
            s > 12 -> { state.text = "⬇ ACERCANDO"; state.setTextColor(RED) }
            s < -12 -> { state.text = "⬆ ALEJANDO"; state.setTextColor(Color.parseColor("#38BDF8")) }
            else -> { state.text = "✋ mano quieta"; state.setTextColor(GREEN) }
        }
        info.text = "Doppler: %+.0f Hz · eco: %.2f".format(s, energy)
        val now = System.currentTimeMillis()
        if (abs(s) > 30 && energy > 0.1 && now - lastGesture > 500) {
            lastGesture = now
            gesture.text = if (s > 0) "👋 Gesto hacia el móvil" else "👋 Gesto hacia fuera"
            beeper.beep(25)
        } else if (now - lastGesture > 1500) gesture.text = ""
    }
}

/** Dibuja el desplazamiento Doppler como una onda que sube (acercar) o baja (alejar). */
private class DopplerView(ctx: Context) : View(ctx) {
    private val vals = ArrayDeque<Double>()
    private val ens = ArrayDeque<Double>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
    private val mid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM; strokeWidth = 1f }

    fun push(shift: Double, energy: Double) {
        vals.addLast(shift); ens.addLast(energy)
        while (vals.size > 160) { vals.removeFirst(); ens.removeFirst() }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#111A2E"))
        val cy = height / 2f
        canvas.drawLine(0f, cy, width.toFloat(), cy, mid)
        if (vals.size < 2) return
        val dx = width.toFloat() / 159
        var prevX = 0f; var prevY = cy
        vals.forEachIndexed { i, v ->
            val x = i * dx
            val y = (cy - (v / 120.0).coerceIn(-1.0, 1.0) * (height / 2 - 10)).toFloat()
            val e = ens.elementAt(i).coerceIn(0.0, 1.0)
            paint.color = Color.argb((60 + e * 195).toInt().coerceIn(60, 255),
                if (v > 0) 239 else 56, if (v > 0) 68 else 189, if (v > 0) 68 else 248)
            if (i > 0) canvas.drawLine(prevX, prevY, x, y, paint)
            prevX = x; prevY = y
        }
    }
}
