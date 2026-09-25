package com.radioescucha.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Medidor de señal tipo S-meter con marca del umbral de silenciador. */
class LevelView(context: Context) : View(context) {
    private val minDb = -100f
    private val maxDb = -10f
    var levelDb = -120f
        set(v) { field = v; invalidate() }
    var squelchDb = -130f
        set(v) { field = v; invalidate() }
    var open = true
        set(v) { field = v; invalidate() }

    private val dp = resources.displayMetrics.density
    private val bg = Paint().apply { color = Color.rgb(16, 30, 44) }
    private val bar = Paint()
    private val mark = Paint().apply { color = Color.rgb(255, 90, 80); strokeWidth = 2 * dp }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 11 * dp }

    private fun x(db: Float) = ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f) * width

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, width.toFloat(), h, bg)
        bar.color = if (open) Color.rgb(53, 208, 160) else Color.rgb(70, 110, 120)
        canvas.drawRect(0f, 0f, x(levelDb), h, bar)
        if (squelchDb > -120f) {
            val sx = x(squelchDb)
            canvas.drawLine(sx, 0f, sx, h, mark)
        }
        // Escala S aproximada: S9 ≈ -40 dBFS, 6 dB por unidad S.
        val s = ((levelDb + 94f) / 6f).toInt()
        val label = when {
            levelDb < -118f -> "--"
            s <= 9 -> "S${s.coerceAtLeast(0)}"
            else -> "S9+${((levelDb + 40f)).toInt()}"
        }
        canvas.drawText("$label  ${levelDb.toInt()} dBFS", 6 * dp, h / 2 + 4 * dp, text)
    }
}
