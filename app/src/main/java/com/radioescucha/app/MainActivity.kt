package com.radioescucha.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.radioescucha.app.dsp.Demodulator
import com.radioescucha.app.dsp.Mode
import com.radioescucha.app.radio.BandPlan
import com.radioescucha.app.radio.Bookmark
import com.radioescucha.app.radio.Bookmarks
import com.radioescucha.app.radio.RadioEngine
import com.radioescucha.app.radio.SignalScanner
import com.radioescucha.app.source.DemoSource
import com.radioescucha.app.source.RtlTcpSource
import com.radioescucha.app.ui.LevelView
import com.radioescucha.app.ui.SpectrumView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class MainActivity : Activity(), RadioEngine.Callback, SpectrumView.Listener {

    companion object {
        private const val REQ_USB = 1001
        private const val USB_PORT = 14423
        private const val DRIVER_PACKAGE = "marto.rtl_tcp_andro"
        private const val SRC_DEMO = "demo"
        private const val SRC_USB = "usb"
        private const val SRC_NET = "red"

        private val STEPS = intArrayOf(100, 1_000, 5_000, 6_250, 8_330, 12_500, 25_000, 100_000)

        private val C_BG = Color.rgb(7, 15, 24)
        private val C_PANEL = Color.rgb(14, 28, 42)
        private val C_ACCENT = Color.rgb(53, 208, 160)
        private val C_WARM = Color.rgb(255, 200, 87)
        private val C_TEXT = Color.rgb(220, 232, 240)
        private val C_DIM = Color.rgb(130, 160, 180)
        private val C_RED = Color.rgb(240, 80, 70)
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var bookmarks: Bookmarks
    private val engine = RadioEngine(this)
    private val ui = Handler(Looper.getMainLooper())
    private val dp by lazy { resources.displayMetrics.density }

    private lateinit var spectrum: SpectrumView
    private lateinit var level: LevelView
    private lateinit var freqText: TextView
    private lateinit var bandText: TextView
    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var recBtn: Button
    private lateinit var stepBtn: Button
    private val modeButtons = HashMap<Mode, TextView>()

    private var step = 12_500
    private var dragAccum = 0.0
    private var lastScanner: SignalScanner? = null

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("ajustes", MODE_PRIVATE)
        bookmarks = Bookmarks(this)
        buildUi()

        val mode = runCatching { Mode.valueOf(prefs.getString("modo", Mode.NFM.name)!!) }.getOrDefault(Mode.NFM)
        step = prefs.getInt("paso", mode.defaultStep)
        engine.volume = prefs.getInt("volumen", 70) / 100f
        engine.setGain(prefs.getInt("ganancia", -1))
        engine.setPpm(prefs.getInt("ppm", 0))
        engine.demod.squelchDb = squelchFromSlider(prefs.getInt("squelch", 0))
        setMode(mode, updateStep = false)
        tuneTo(prefs.getLong("frecuencia", 145_800_000L), recenter = true)
        updateStepButton()

        if (!prefs.getBoolean("bienvenida", false)) showWelcome()
    }

    override fun onDestroy() {
        lastScanner = null
        engine.scanner = null
        engine.stop()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        prefs.edit()
            .putLong("frecuencia", channelFreq())
            .putString("modo", engine.demod.mode.name)
            .putInt("paso", step)
            .apply()
    }

    // ---------------------------------------------------------------- interfaz

    private fun px(v: Int) = (v * dp).toInt()

    private fun rounded(color: Int, radius: Int = 10, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * dp
        if (stroke != null) setStroke(px(1), stroke)
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(C_TEXT)
        textSize = 14f
        background = rounded(C_PANEL, stroke = Color.rgb(40, 70, 95))
        minHeight = px(44)
        minimumHeight = px(44)
        setPadding(px(6), 0, px(6), 0)
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float = 13f, color: Int = C_DIM) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun row(vararg views: View, weights: Boolean = true) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        for (v in views) {
            val lp = if (weights) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(px(3), px(3), px(3), px(3))
            addView(v, lp)
        }
    }

    private fun slider(max: Int, value: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
        this.max = max
        progress = value
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun labeled(lbl: TextView, bar: SeekBar) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(lbl, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f))
        addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
    }

    private fun buildUi() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(C_BG)
            setPadding(px(6), px(6), px(6), px(6))
        }
        // Android 15 dibuja la app detrás de las barras del sistema: dejar espacio para no tapar botones.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val (l, t, r, b) = if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                listOf(i.left, i.top, i.right, i.bottom)
            } else {
                @Suppress("DEPRECATION")
                listOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            v.setPadding(px(6) + l, px(6) + t, px(6) + r, px(6) + b)
            insets
        }

        // Barra superior
        val title = label("📡 Radio Escucha SDR", 17f, C_TEXT).apply { typeface = Typeface.DEFAULT_BOLD }
        statusText = label("Detenido", 12f)
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(statusText)
        }
        val settings = button("⚙") { showSettings() }
        val help = button("?") { showWelcome() }
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(help, LinearLayout.LayoutParams(px(48), px(44)).apply { marginEnd = px(4) })
            addView(settings, LinearLayout.LayoutParams(px(48), px(44)))
        })

        // Frecuencia
        freqText = TextView(this).apply {
            textSize = 34f
            typeface = Typeface.MONOSPACE
            setTextColor(C_WARM)
            gravity = Gravity.CENTER
            setOnClickListener { showFrequencyDialog() }
        }
        bandText = label("", 13f, C_ACCENT).apply { gravity = Gravity.CENTER }
        root.addView(freqText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(bandText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Sintonía
        stepBtn = button("") { cycleStep() }
        root.addView(
            row(
                button("◀◀") { tuneTo(channelFreq() - 10L * step, recenter = true) },
                button("◀") { tuneTo(channelFreq() - step, recenter = true) },
                stepBtn,
                button("▶") { tuneTo(channelFreq() + step, recenter = true) },
                button("▶▶") { tuneTo(channelFreq() + 10L * step, recenter = true) },
            )
        )

        // Espectro + cascada
        spectrum = SpectrumView(this).apply { listener = this@MainActivity }
        root.addView(spectrum, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = px(4); bottomMargin = px(4)
        })

        // Medidor
        level = LevelView(this)
        root.addView(level, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(22)))

        // Modos
        val modes = Mode.values().map { m ->
            TextView(this).apply {
                text = m.label
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(0, px(8), 0, px(8))
                setOnClickListener { setMode(m, updateStep = true) }
                modeButtons[m] = this
            }
        }
        root.addView(row(*modes.toTypedArray()))

        // Controles deslizantes
        val volLabel = label("Volumen")
        val sqLabel = label("Silenciador")
        val gainLabel = label("Ganancia")
        val gainNow = prefs.getInt("ganancia", -1)
        fun gainText(g: Int) = if (g < 0) "Ganancia: auto" else "Ganancia: ${g / 10} dB"
        gainLabel.text = gainText(gainNow)
        fun sqText(v: Int) = if (v == 0) "Silenciador: no" else "Silenciador: ${squelchFromSlider(v).toInt()} dB"
        sqLabel.text = sqText(prefs.getInt("squelch", 0))
        val sliders = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(4), 0, px(4), 0)
            addView(labeled(volLabel, slider(100, prefs.getInt("volumen", 70)) {
                engine.volume = it / 100f
                prefs.edit().putInt("volumen", it).apply()
            }))
            addView(labeled(sqLabel, slider(90, prefs.getInt("squelch", 0)) {
                val db = squelchFromSlider(it)
                engine.demod.squelchDb = db
                level.squelchDb = db
                sqLabel.text = sqText(it)
                prefs.edit().putInt("squelch", it).apply()
            }))
            addView(labeled(gainLabel, slider(50, if (gainNow < 0) 0 else gainNow / 10) {
                val g = if (it == 0) -1 else it * 10
                engine.setGain(g)
                gainLabel.text = gainText(g)
                prefs.edit().putInt("ganancia", g).apply()
            }))
        }
        level.squelchDb = engine.demod.squelchDb
        root.addView(sliders)

        // Acciones
        startBtn = button("▶ Iniciar") { toggleRunning() }
        recBtn = button("⏺ Grabar") { toggleRecording() }
        root.addView(
            row(
                startBtn,
                recBtn,
                button("★ Favoritos") { showBookmarks() },
                button("🔎 Buscar") { showScanner() },
            )
        )

        setContentView(root)
    }

    private fun squelchFromSlider(v: Int) = if (v == 0) -130f else -100f + v

    private fun refreshModeButtons() {
        for ((m, v) in modeButtons) {
            val sel = m == engine.demod.mode
            v.setTextColor(if (sel) Color.BLACK else C_TEXT)
            v.background = rounded(if (sel) C_ACCENT else C_PANEL, 8)
        }
    }

    private fun updateStepButton() {
        stepBtn.text = "Paso\n${formatStep(step)}"
        stepBtn.textSize = 11f
    }

    private fun formatStep(s: Int) = when {
        s >= 1000 && s % 1000 == 0 -> "${s / 1000} kHz"
        s >= 1000 -> String.format(Locale.US, "%.2f kHz", s / 1000.0).trimEnd('0')
        else -> "$s Hz"
    }

    private fun cycleStep() {
        val idx = STEPS.indexOf(step)
        step = STEPS[(idx + 1) % STEPS.size]
        updateStepButton()
    }

    // ---------------------------------------------------------------- sintonía

    private fun channelFreq(): Long = engine.centerHz + engine.demod.offsetHz.roundToLong()

    private fun setMode(m: Mode, updateStep: Boolean) {
        engine.demod.mode = m
        spectrum.channelLow = m.bandwidthLow
        spectrum.channelHigh = m.bandwidthHigh
        if (updateStep) {
            step = m.defaultStep
            updateStepButton()
        }
        refreshModeButtons()
        spectrum.invalidate()
    }

    /**
     * Sintoniza [freq]. Si queda cerca del borde del espectro, se recentra el receptor dejando el canal
     * desplazado 150 kHz del centro para evitar el pico de continua del RTL-SDR.
     */
    private fun tuneTo(freq: Long, recenter: Boolean) {
        val f = freq.coerceIn(RadioEngine.MIN_FREQ, RadioEngine.MAX_FREQ)
        val limit = Demodulator.INPUT_RATE * 0.4 - 100_000
        var off = (f - engine.centerHz).toDouble()
        if (recenter && (abs(off) > limit || abs(off) < 10_000)) {
            engine.setCenter(f + 150_000)
            off = (f - engine.centerHz).toDouble()
        }
        engine.demod.offsetHz = off
        spectrum.channelOffsetHz = off
        if (!engine.running) spectrum.centerHz = engine.centerHz
        spectrum.invalidate()
        updateFreqDisplay()
    }

    private fun updateFreqDisplay() {
        val f = channelFreq()
        val mhz = f / 1_000_000
        val khz = (f / 1000) % 1000
        val hz = f % 1000
        freqText.text = String.format(Locale.US, "%d.%03d.%03d", mhz, khz, hz)
        bandText.text = "${BandPlan.describe(f)}  ·  ${engine.demod.mode.label}"
    }

    override fun onTapOffset(offsetHz: Double) {
        if (engine.scanner != null) return
        val raw = engine.centerHz + offsetHz
        val snapped = (raw / step).roundToLong() * step
        tuneTo(snapped, recenter = false)
    }

    override fun onDrag(deltaHz: Double) {
        if (engine.scanner != null) return
        dragAccum += deltaHz
        val whole = (dragAccum / 100).toLong() * 100
        if (whole != 0L) {
            dragAccum -= whole
            engine.setCenter(engine.centerHz + whole)
            if (!engine.running) spectrum.centerHz = engine.centerHz
            spectrum.invalidate()
            updateFreqDisplay()
        }
    }

    private fun showFrequencyDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.4f", channelFreq() / 1e6))
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Frecuencia (MHz)")
            .setMessage("Rango típico de un RTL-SDR: 24 – 1766 MHz (HF con dongles V3/V4 o conversor).")
            .setView(input)
            .setPositiveButton("Sintonizar") { _, _ ->
                val mhz = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (mhz == null || mhz <= 0) {
                    toast("Frecuencia inválida")
                } else {
                    val f = (mhz * 1e6).roundToLong()
                    setMode(BandPlan.suggestedMode(f), updateStep = true)
                    tuneTo(f, recenter = true)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------------- arranque / parada

    private fun toggleRunning() {
        if (engine.running) {
            stopScanner()
            engine.stop()
        } else {
            startReceiver()
        }
    }

    private fun startReceiver() {
        spectrum.clear()
        when (prefs.getString("fuente", SRC_DEMO)) {
            SRC_USB -> launchUsbDriver()
            SRC_NET -> {
                val host = prefs.getString("host", "192.168.0.10") ?: "192.168.0.10"
                val port = prefs.getInt("puerto", 1234)
                statusText.text = "Conectando a $host:$port…"
                engine.start(RtlTcpSource(host, port))
            }
            else -> engine.start(DemoSource())
        }
    }

    private fun launchUsbDriver() {
        val args = "-a 127.0.0.1 -p $USB_PORT -n 1 -f ${engine.centerHz} -s ${Demodulator.INPUT_RATE}"
        val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse("iqsrc://$args"))
        try {
            statusText.text = "Abriendo el driver RTL-SDR…"
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_USB)
        } catch (e: ActivityNotFoundException) {
            statusText.text = "Detenido"
            AlertDialog.Builder(this)
                .setTitle("Falta el driver RTL-SDR")
                .setMessage(
                    "Para usar un dongle RTL-SDR por USB (cable OTG) se necesita la app gratuita " +
                        "\"RTL-SDR driver\" (Martin Marinov). Esta app se conecta a ella automáticamente.\n\n" +
                        "Mientras tanto podés probar el modo Demo desde ⚙ Ajustes."
                )
                .setPositiveButton("Instalar") { _, _ -> openStore() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun openStore() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$DRIVER_PACKAGE")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$DRIVER_PACKAGE")))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_USB) return
        if (resultCode == RESULT_OK) {
            engine.start(RtlTcpSource("127.0.0.1", USB_PORT))
        } else {
            statusText.text = "Detenido"
            val detail = data?.getStringExtra("detailed_exception_message")
            AlertDialog.Builder(this)
                .setTitle("No se pudo abrir el dongle")
                .setMessage(
                    "Verificá que el RTL-SDR esté conectado con un cable OTG y que aceptaste el permiso USB." +
                        (if (detail != null) "\n\nDetalle: $detail" else "")
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ---------------------------------------------------------------- callbacks del motor

    override fun onStarted(sourceName: String) {
        ui.post {
            statusText.text = "Escuchando · $sourceName"
            startBtn.text = "■ Detener"
            if (sourceName.startsWith("Demo")) {
                toast("Modo Demo: probá ★ Favoritos → ISS 145.800 o 🔎 Buscar en 2 m")
            }
            startBtn.background = rounded(Color.rgb(90, 30, 30), stroke = C_RED)
        }
    }

    override fun onStopped(error: String?) {
        ui.post {
            stopScanner()
            statusText.text = if (error != null) "Error: $error" else "Detenido"
            startBtn.text = "▶ Iniciar"
            startBtn.background = rounded(C_PANEL, stroke = Color.rgb(40, 70, 95))
            recBtn.text = "⏺ Grabar"
            level.levelDb = -120f
            if (error != null) toast("Se detuvo el receptor: $error")
        }
    }

    override fun onSpectrum(db: FloatArray, centerHz: Long) = spectrum.push(db, centerHz)

    override fun onLevel(db: Float, squelchOpen: Boolean) {
        ui.post {
            level.levelDb = db
            level.open = squelchOpen
        }
    }

    override fun onScanStep(centerHz: Long, sweeps: Int, found: Int) {}

    // ---------------------------------------------------------------- grabación

    private fun toggleRecording() {
        if (engine.isRecording) {
            val f = engine.stopRecording()
            recBtn.text = "⏺ Grabar"
            if (f != null) toast("Grabación guardada:\n${f.absolutePath}")
            return
        }
        if (!engine.running) {
            toast("Iniciá el receptor para grabar")
            return
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rx_${channelFreq()}Hz_${engine.demod.mode.label}_$stamp.wav")
        try {
            engine.startRecording(file)
            recBtn.text = "⏹ Grabando"
        } catch (e: Exception) {
            toast("No se pudo grabar: ${e.message}")
        }
    }

    // ---------------------------------------------------------------- marcadores

    private fun formatMhz(f: Long) = String.format(Locale.US, "%.4f MHz", f / 1e6)

    private fun showBookmarks() {
        val list = bookmarks.all()
        val items = list.map { (if (it.builtIn) "" else "★ ") + "${formatMhz(it.freqHz)} · ${it.mode.label}\n${it.name}" }
        val lv = ListView(this)
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Frecuencias interesantes")
            .setView(lv)
            .setPositiveButton("Guardar la actual") { _, _ -> saveBookmarkDialog() }
            .setNegativeButton("Cerrar", null)
            .create()
        lv.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val b = list[pos]
            setMode(b.mode, updateStep = true)
            tuneTo(b.freqHz, recenter = true)
            dialog.dismiss()
        }
        lv.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            val b = list[pos]
            if (b.builtIn) {
                toast("Los marcadores predefinidos no se pueden borrar")
            } else {
                bookmarks.remove(b)
                toast("Marcador borrado")
                dialog.dismiss()
            }
            true
        }
        dialog.show()
    }

    private fun saveBookmarkDialog() {
        val input = EditText(this).apply { hint = "Nombre (ej.: repetidora local)" }
        AlertDialog.Builder(this)
            .setTitle("Guardar ${formatMhz(channelFreq())}")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val name = input.text.toString().ifBlank { BandPlan.describe(channelFreq()) }
                bookmarks.add(Bookmark(channelFreq(), engine.demod.mode, name))
                toast("Marcador guardado")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------------------------------------------------------- escáner

    private data class ScanPreset(val name: String, val startMhz: Double, val endMhz: Double)

    private val scanPresets = listOf(
        ScanPreset("Radioaficionados 2 m", 144.0, 148.0),
        ScanPreset("Radioaficionados 70 cm", 430.0, 440.0),
        ScanPreset("Banda aérea", 118.0, 137.0),
        ScanPreset("VHF marino", 156.0, 162.6),
        ScanPreset("Satélites meteorológicos", 137.0, 138.0),
        ScanPreset("PMR446 / FRS", 446.0, 467.8),
        ScanPreset("ISM 433 MHz", 433.0, 435.0),
        ScanPreset("FM comercial", 88.0, 108.0),
        ScanPreset("Banda ciudadana 27 MHz", 26.9, 27.5),
        ScanPreset("Personalizado", 0.0, 0.0),
    )

    private fun stopScanner() {
        engine.scanner = null
        spectrum.channelOffsetHz = engine.demod.offsetHz
    }

    private fun showScanner() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), 0)
        }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, scanPresets.map { it.name })
        val startEt = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "Desde (MHz)" }
        val endEt = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; hint = "Hasta (MHz)" }
        val thrLabel = label("")
        val thrBar = slider(30, prefs.getInt("umbral", 12) - 5) {
            thrLabel.text = "Sensibilidad: señales ${it + 5} dB sobre el ruido"
            engine.scanner?.thresholdDb = (it + 5).toFloat()
            prefs.edit().putInt("umbral", it + 5).apply()
        }
        thrLabel.text = "Sensibilidad: señales ${thrBar.progress + 5} dB sobre el ruido"
        val status = label("Elegí una banda y tocá Escanear.", 13f, C_DIM)
        val run = Button(this).apply { isAllCaps = false }
        val results = ListView(this)
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, ArrayList())
        results.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val sp = scanPresets[pos]
                if (sp.endMhz > 0) {
                    startEt.setText(sp.startMhz.toString())
                    endEt.setText(sp.endMhz.toString())
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        lastScanner?.let {
            startEt.setText((it.startHz / 1e6).toString())
            endEt.setText((it.endHz / 1e6).toString())
            spinner.setSelection(scanPresets.size - 1)
        }

        box.addView(spinner)
        box.addView(row(startEt, endEt))
        box.addView(thrLabel)
        box.addView(thrBar)
        box.addView(run)
        box.addView(status)
        box.addView(results, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(260)))

        var shown: List<com.radioescucha.app.radio.Detection> = emptyList()
        fun refresh() {
            val sc = engine.scanner ?: lastScanner
            run.text = if (engine.scanner != null) "■ Detener escaneo" else "▶ Escanear"
            if (sc == null) return
            shown = sc.results()
            val now = System.currentTimeMillis()
            adapter.clear()
            adapter.addAll(shown.map {
                val ago = (now - it.lastSeen) / 1000
                "${formatMhz(it.freqHz)} · ${it.mode.label} · SNR ${it.snrDb.toInt()} dB\n" +
                    "${it.band} · ${it.hits}× · hace ${ago}s"
            })
            status.text = if (engine.scanner != null)
                "Escaneando ${formatMhz(sc.currentCenter)} · barrido ${sc.sweeps + 1} · ${shown.size} señales"
            else "${shown.size} señales encontradas. Tocá una para escucharla."
        }

        val ticker = object : Runnable {
            override fun run() {
                refresh()
                ui.postDelayed(this, 700)
            }
        }

        run.setOnClickListener {
            if (engine.scanner != null) {
                stopScanner()
                refresh()
                return@setOnClickListener
            }
            val a = startEt.text.toString().replace(',', '.').toDoubleOrNull()
            val b = endEt.text.toString().replace(',', '.').toDoubleOrNull()
            if (a == null || b == null || b <= a) {
                toast("Rango inválido")
                return@setOnClickListener
            }
            val sc = SignalScanner((a * 1e6).toLong(), (b * 1e6).toLong(), (thrBar.progress + 5).toFloat())
            lastScanner = sc
            spectrum.channelOffsetHz = null
            engine.scanner = sc
            if (!engine.running) startReceiver()
            refresh()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("🔎 Buscador de señales")
            .setView(box)
            .setNegativeButton("Cerrar", null)
            .create()
        results.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val d = shown.getOrNull(pos) ?: return@OnItemClickListener
            stopScanner()
            setMode(d.mode, updateStep = true)
            tuneTo(d.freqHz, recenter = true)
            dialog.dismiss()
        }
        dialog.setOnDismissListener {
            ui.removeCallbacks(ticker)
            if (engine.scanner != null) {
                stopScanner()
                toast("Escaneo detenido")
            }
        }
        dialog.show()
        ticker.run()
    }

    // ---------------------------------------------------------------- ajustes y ayuda

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), 0)
        }
        val group = RadioGroup(this)
        val options = listOf(
            SRC_DEMO to "Demo (señales simuladas, sin hardware)",
            SRC_USB to "Dongle RTL-SDR por USB (OTG)",
            SRC_NET to "Servidor rtl_tcp en la red",
        )
        val current = prefs.getString("fuente", SRC_DEMO)
        val ids = HashMap<Int, String>()
        for ((key, text) in options) {
            val rb = RadioButton(this).apply {
                this.text = text
                id = View.generateViewId()
            }
            ids[rb.id] = key
            group.addView(rb)
            if (key == current) group.check(rb.id)
        }
        val host = EditText(this).apply { hint = "Host rtl_tcp"; setText(prefs.getString("host", "192.168.0.10")) }
        val port = EditText(this).apply {
            hint = "Puerto"; inputType = InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("puerto", 1234).toString())
        }
        val ppm = EditText(this).apply {
            hint = "Corrección ppm"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(prefs.getInt("ppm", 0).toString())
        }
        box.addView(label("Fuente de señal", 14f, C_DIM))
        box.addView(group)
        box.addView(label("Servidor rtl_tcp (solo para la opción de red)", 14f, C_DIM))
        box.addView(host)
        box.addView(port)
        box.addView(label("Corrección de frecuencia del cristal (ppm)", 14f, C_DIM))
        box.addView(ppm)

        AlertDialog.Builder(this)
            .setTitle("⚙ Ajustes")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Guardar") { _, _ ->
                val src = ids[group.checkedRadioButtonId] ?: SRC_DEMO
                val changed = src != current
                val p = ppm.text.toString().toIntOrNull() ?: 0
                prefs.edit()
                    .putString("fuente", src)
                    .putString("host", host.text.toString().trim())
                    .putInt("puerto", port.text.toString().toIntOrNull() ?: 1234)
                    .putInt("ppm", p)
                    .apply()
                engine.setPpm(p)
                if (changed && engine.running) toast("Detené e iniciá de nuevo para usar la nueva fuente")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showWelcome() {
        prefs.edit().putBoolean("bienvenida", true).apply()
        AlertDialog.Builder(this)
            .setTitle("Bienvenido a Radio Escucha SDR")
            .setMessage(
                "Receptor de radio definido por software para radioaficionados y curiosos del espectro.\n\n" +
                    "• Hardware: un dongle RTL-SDR (RTL2832U) conectado con cable OTG, usando la app gratuita " +
                    "\"RTL-SDR driver\". También podés conectarte a un servidor rtl_tcp en tu red, o usar el modo Demo.\n\n" +
                    "• Tocá la cascada para sintonizar una señal; arrastrá para moverte por la banda.\n" +
                    "• Tocá la frecuencia para escribir una nueva.\n" +
                    "• ★ Favoritos: ISS, APRS, satélites meteorológicos, banda aérea, marina y más.\n" +
                    "• 🔎 Buscar: barre una banda y lista las señales activas que encuentra, con su intensidad.\n" +
                    "• ⏺ Grabar: guarda el audio en WAV.\n\n" +
                    "Por defecto arranca en modo Demo. Cambiá la fuente en ⚙ Ajustes.\n\n" +
                    "Recordá: escuchar está permitido en la mayoría de los países, pero respetá la privacidad " +
                    "de las comunicaciones y las leyes locales."
            )
            .setPositiveButton("¡A escuchar!", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
