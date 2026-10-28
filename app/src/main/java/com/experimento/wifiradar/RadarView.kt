package com.experimento.wifiradar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar animado: cada red aparece a su distancia estimada (escala logarítmica).
 * El ángulo NO es real (el WiFi del móvil no da dirección); se deriva del BSSID
 * para que cada red mantenga siempre su posición.
 */
class RadarView(ctx: Context) : View(ctx) {

    var nets: List<Net> = emptyList()
        set(v) { field = v; invalidate() }

    private var sweep = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 28f
    }
    private val maxDist = 150.0
    private val rings = intArrayOf(2, 5, 10, 25, 50, 100)

    private val ticker = object : Runnable {
        override fun run() {
            sweep = (sweep + 2.5f) % 360f
            invalidate()
            postDelayed(this, 30)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(ticker) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); super.onDetachedFromWindow() }

    private fun radiusFor(d: Double, r: Float): Float =
        (r * ln(1 + d.coerceIn(0.0, maxDist)) / ln(1 + maxDist)).toFloat()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 30f
        canvas.drawColor(Color.parseColor("#0B1220"))

        // Anillos de distancia
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#1F6F43")
        text.textSize = 22f
        text.color = Color.parseColor("#4ADE80")
        for (m in rings) {
            val rr = radiusFor(m.toDouble(), r)
            canvas.drawCircle(cx, cy, rr, paint)
            canvas.drawText("${m}m", cx + 6, cy - rr - 4, text)
        }
        canvas.drawLine(cx - r, cy, cx + r, cy, paint)
        canvas.drawLine(cx, cy - r, cx, cy + r, paint)

        // Barrido
        paint.style = Paint.Style.FILL
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        for (i in 0 until 30) {
            paint.color = Color.argb(70 - i * 2, 34, 197, 94)
            canvas.drawArc(oval, sweep - i * 1.5f - 1.5f, 1.6f, true, paint)
        }

        // Redes
        text.textSize = 26f
        for (n in nets) {
            val angDeg = ((n.bssid.hashCode() and 0xffff) / 65535f) * 360f
            val rad = Math.toRadians(angDeg.toDouble())
            val rr = radiusFor(n.distance, r)
            val x = cx + (rr * cos(rad)).toFloat()
            val y = cy + (rr * sin(rad)).toFloat()
            // La red "se ilumina" justo después de que pasa el barrido
            val since = ((sweep - angDeg) + 360f) % 360f
            val alpha = (255 - since * 0.55f).toInt().coerceIn(90, 255)
            val col = securityColor(n.security)
            paint.color = Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawCircle(x, y, if (since < 20f) 14f else 10f, paint)
            text.color = Color.argb(alpha, 226, 232, 240)
            canvas.drawText(n.displaySsid.take(16), x + 16, y + 8, text)
        }

        // Tú
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 8f, paint)
    }

    companion object {
        fun securityColor(sec: String): Int = Color.parseColor(
            when (sec) {
                "Abierta" -> "#EF4444"
                "WEP" -> "#F97316"
                "WPA" -> "#F59E0B"
                "WPA3", "WPA2/WPA3", "OWE" -> "#22C55E"
                "Enterprise" -> "#A855F7"
                else -> "#3B82F6"
            }
        )
    }
}
