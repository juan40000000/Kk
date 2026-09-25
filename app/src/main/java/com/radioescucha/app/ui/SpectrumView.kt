package com.radioescucha.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.radioescucha.app.dsp.Demodulator
import kotlin.math.abs
import kotlin.math.ceil

/** Espectro (arriba) y cascada/waterfall (abajo). Tocar sintoniza una señal; arrastrar mueve la frecuencia. */
class SpectrumView(context: Context) : View(context) {
    interface Listener {
        /** Tocó una posición: desplazamiento respecto al centro, en Hz. */
        fun onTapOffset(offsetHz: Double)
        /** Arrastró horizontalmente: cambio de frecuencia en Hz. */
        fun onDrag(deltaHz: Double)
    }

    var listener: Listener? = null

    /** Frecuencia central del espectro que se está mostrando. */
    var centerHz = 0L
    /** Canal sintonizado respecto al centro y su ancho de banda; null oculta el marcador. */
    var channelOffsetHz: Double? = 0.0
    var channelLow = -6000
    var channelHigh = 6000

    private val span = Demodulator.INPUT_RATE.toDouble()
    private val bins = 1024
    private val waterRows = 400
    private val water = Bitmap.createBitmap(bins, waterRows, Bitmap.Config.ARGB_8888)
    private var waterTop = 0 // fila más reciente en el buffer circular
    private val row = IntArray(bins)
    private val palette = IntArray(256) { paletteColor(it / 255f) }

    private val spectrum = FloatArray(bins) { -100f }
    private val pending = ArrayDeque<FloatArray>()
    private var pendingCenter = 0L
    private var floorDb = -70f
    private var hasData = false

    private val dp = resources.displayMetrics.density
    private val bgPaint = Paint().apply { color = Color.rgb(5, 12, 20) }
    private val gridPaint = Paint().apply { color = Color.argb(60, 120, 180, 220); strokeWidth = 1f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(150, 190, 215); textSize = 10 * dp }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(53, 208, 160); style = Paint.Style.STROKE; strokeWidth = 1.5f * dp
    }
    private val fillPaint = Paint().apply { color = Color.argb(70, 53, 208, 160); style = Paint.Style.FILL }
    private val chanPaint = Paint().apply { color = Color.argb(60, 255, 200, 87) }
    private val chanLine = Paint().apply { color = Color.rgb(255, 90, 80); strokeWidth = 1.5f * dp }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val src = Rect()
    private val dst = RectF()

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            listener?.onTapOffset((e.x / width - 0.5) * span)
            return true
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (abs(dx) > abs(dy)) listener?.onDrag(dx / width * span)
            return true
        }
    })

    init {
        water.eraseColor(Color.BLACK)
    }

    override fun onTouchEvent(event: MotionEvent) = gestures.onTouchEvent(event) || super.onTouchEvent(event)

    /** Llamado desde el hilo del motor. */
    fun push(db: FloatArray, center: Long) {
        synchronized(pending) {
            if (pending.size < 8) pending.addLast(db.copyOf())
            pendingCenter = center
        }
        postInvalidateOnAnimation()
    }

    fun clear() {
        synchronized(pending) { pending.clear() }
        water.eraseColor(Color.BLACK)
        spectrum.fill(-100f)
        hasData = false
        invalidate()
    }

    private fun drainPending() {
        while (true) {
            val line = synchronized(pending) {
                centerHz = pendingCenter
                pending.removeFirstOrNull()
            } ?: break
            val n = minOf(line.size, bins)
            for (k in 0 until n) {
                spectrum[k] = if (hasData) spectrum[k] * 0.55f + line[k] * 0.45f else line[k]
            }
            hasData = true
            // Piso de ruido: mediana, seguida suavemente para escalar los colores.
            val sorted = line.copyOf()
            sorted.sort()
            floorDb = floorDb * 0.9f + sorted[sorted.size / 2] * 0.1f
            val lo = floorDb - 5f
            val range = 55f
            for (k in 0 until n) {
                val v = ((line[k] - lo) / range).coerceIn(0f, 1f)
                row[k] = palette[(v * 255).toInt()]
            }
            waterTop = (waterTop - 1 + waterRows) % waterRows
            water.setPixels(row, 0, bins, 0, waterTop, bins, 1)
        }
    }

    override fun onDraw(canvas: Canvas) {
        drainPending()
        val w = width.toFloat()
        val h = height.toFloat()
        val specH = h * 0.38f
        canvas.drawRect(0f, 0f, w, specH, bgPaint)

        val lo = floorDb - 10f
        val hi = floorDb + 60f
        fun yOf(db: Float) = specH - ((db - lo) / (hi - lo)).coerceIn(0f, 1f) * specH

        // Rejilla horizontal cada 10 dB
        var g = ceil(lo / 10f) * 10f
        while (g < hi) {
            val y = yOf(g)
            canvas.drawLine(0f, y, w, y, gridPaint)
            g += 10f
        }
        // Rejilla vertical con etiquetas de frecuencia
        val startHz = centerHz - span / 2
        val stepHz = if (w / dp < 420) 200_000.0 else 100_000.0
        var f = ceil(startHz / stepHz) * stepHz
        while (f < startHz + span) {
            val x = ((f - startHz) / span * w).toFloat()
            canvas.drawLine(x, 0f, x, specH, gridPaint)
            val label = String.format("%.1f", f / 1e6)
            canvas.drawText(label, x + 2 * dp, specH - 3 * dp, textPaint)
            f += stepHz
        }

        if (hasData) {
            path.reset()
            path.moveTo(0f, specH)
            for (k in 0 until bins) {
                path.lineTo(k * w / (bins - 1), yOf(spectrum[k]))
            }
            path.lineTo(w, specH)
            path.close()
            canvas.drawPath(path, fillPaint)
            path.reset()
            for (k in 0 until bins) {
                val x = k * w / (bins - 1)
                val y = yOf(spectrum[k])
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        } else {
            canvas.drawText("Detenido: tocá ▶ Iniciar (abajo)", 8 * dp, specH / 2, textPaint)
        }
        canvas.drawText(String.format("%.0f dB", hi), 2 * dp, 12 * dp, textPaint)

        // Cascada (buffer circular: la fila waterTop es la más reciente y va arriba)
        val wTop = specH
        val wH = h - specH
        val firstRows = waterRows - waterTop
        val rowH = wH / waterRows
        src.set(0, waterTop, bins, waterRows)
        dst.set(0f, wTop, w, wTop + firstRows * rowH)
        canvas.drawBitmap(water, src, dst, bmpPaint)
        if (waterTop > 0) {
            src.set(0, 0, bins, waterTop)
            dst.set(0f, wTop + firstRows * rowH, w, h)
            canvas.drawBitmap(water, src, dst, bmpPaint)
        }

        // Marcador del canal sintonizado
        channelOffsetHz?.let { off ->
            val cx = ((off / span + 0.5) * w).toFloat()
            val x1 = (((off + channelLow) / span + 0.5) * w).toFloat()
            val x2 = (((off + channelHigh) / span + 0.5) * w).toFloat()
            val minW = 2 * dp
            val l = if (x2 - x1 < minW) cx - minW / 2 else x1
            val r = if (x2 - x1 < minW) cx + minW / 2 else x2
            canvas.drawRect(l, 0f, r, h, chanPaint)
            canvas.drawLine(cx, 0f, cx, h, chanLine)
        }
    }

    private fun paletteColor(v: Float): Int {
        // negro -> azul -> cian -> amarillo -> rojo -> blanco
        val stops = floatArrayOf(0f, 0.25f, 0.5f, 0.7f, 0.88f, 1f)
        val cols = arrayOf(
            intArrayOf(0, 0, 0), intArrayOf(10, 30, 140), intArrayOf(0, 180, 200),
            intArrayOf(240, 230, 60), intArrayOf(240, 60, 30), intArrayOf(255, 255, 255),
        )
        var i = 0
        while (i < stops.size - 2 && v > stops[i + 1]) i++
        val t = ((v - stops[i]) / (stops[i + 1] - stops[i])).coerceIn(0f, 1f)
        val a = cols[i]
        val b = cols[i + 1]
        return Color.rgb(
            (a[0] + (b[0] - a[0]) * t).toInt(),
            (a[1] + (b[1] - a[1]) * t).toInt(),
            (a[2] + (b[2] - a[2]) * t).toInt(),
        )
    }

}
