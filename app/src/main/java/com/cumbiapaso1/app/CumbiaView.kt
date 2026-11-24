package com.cumbiapaso1.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Círculo que late con cada golpe grave + espectro radial de graves. */
class CumbiaView(ctx: Context) : View(ctx) {

    private val d = resources.displayMetrics.density
    private val n = BeatDetector.BAR_COUNT
    private val target = FloatArray(n)
    private val shown = FloatArray(n)
    private var pulse = 0f

    var active = false
        set(value) {
            field = value
            if (!value) java.util.Arrays.fill(target, 0f)
            invalidate()
        }

    private var label = "LISTO"
    private var sub = "toca iniciar"

    private val colors = IntArray(n) { i ->
        Color.HSVToColor(floatArrayOf(345f - 155f * i / (n - 1), 0.85f, 1f))
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    fun beat() {
        pulse = 1f
        invalidate()
    }

    fun setBars(b: FloatArray) {
        val m = min(b.size, n)
        for (i in 0 until m) target[i] = b[i]
        invalidate()
    }

    fun setTexts(main: String, small: String) {
        label = main
        sub = small
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val base = min(w, h) * 0.24f
        val r = base * (1f + 0.14f * pulse)

        // Halos
        for (i in 3 downTo 1) {
            val a = (18f + 90f * pulse / i).toInt().coerceIn(0, 255)
            fill.color = Color.argb(a, 225, 29, 72)
            canvas.drawCircle(cx, cy, r + i * 12f * d * (1f + pulse), fill)
        }

        // Núcleo
        fill.color = if (active) Color.parseColor("#E11D48") else Color.parseColor("#26263A")
        canvas.drawCircle(cx, cy, r, fill)
        ring.strokeWidth = 3f * d
        ring.color = if (active) Color.parseColor("#22D3EE") else Color.parseColor("#3A3A55")
        canvas.drawCircle(cx, cy, r + 4f * d, ring)

        // Barras radiales (espejo de 2 x n)
        bar.strokeWidth = 5f * d
        val total = n * 2
        for (i in 0 until total) {
            val idx = if (i < n) i else total - 1 - i
            shown[idx] += (target[idx] - shown[idx]) * 0.35f
            val len = 4f * d + shown[idx] * base * 0.95f
            val ang = 2.0 * PI * i / total - PI / 2.0
            val c = cos(ang).toFloat()
            val s = sin(ang).toFloat()
            val r1 = r + 14f * d
            bar.color = colors[idx]
            canvas.drawLine(cx + c * r1, cy + s * r1, cx + c * (r1 + len), cy + s * (r1 + len), bar)
        }

        // Textos
        txt.textSize = 34f * d
        canvas.drawText(label, cx, cy + 12f * d, txt)
        txt.textSize = 12f * d
        txt.color = Color.parseColor("#AAAAB5")
        canvas.drawText(sub, cx, cy + 34f * d, txt)
        txt.color = Color.WHITE

        // Animación
        pulse *= 0.88f
        if (pulse < 0.01f) pulse = 0f
        var moving = pulse > 0f
        for (i in 0 until n) {
            if (Math.abs(shown[i] - target[i]) > 0.01f) moving = true
        }
        if (active || moving) postInvalidateOnAnimation()
    }
}
