package com.cumbiapaso1.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Pal {
    val BG = Color.parseColor("#0B0B12")
    val CARD = Color.parseColor("#17172A")
    val BORDER = Color.parseColor("#2A2A44")
    val RED = Color.parseColor("#E11D48")
    val PINK = Color.parseColor("#BE185D")
    val CYAN = Color.parseColor("#22D3EE")
    val GOLD = Color.parseColor("#F5C04A")
    val OFF = Color.parseColor("#1F1F33")
    val WHITE = Color.WHITE
    val GRAY = Color.parseColor("#9A9AB0")
}

interface Screen {
    val view: View
    fun onLeave()
}

class ChoiceGroup(val view: LinearLayout, private val paint: (Int) -> Unit) {
    fun select(i: Int) {
        paint(i)
    }
}

/** Ayudantes para construir la interfaz por código. */
class Ui(private val ctx: Context) {

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = dp(top)
        return p
    }

    fun tv(text: String, sizeSp: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(ctx)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun section(text: String): TextView {
        val t = tv(text, 12f, Pal.CYAN, true)
        t.letterSpacing = 0.12f
        return t
    }

    fun roundBg(color: Int, radiusDp: Int, stroke: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setStroke(dp(1), stroke)
        return g
    }

    fun gradientBg(radiusDp: Int, c1: Int, c2: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2))
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    fun bigButton(text: String): TextView {
        val b = tv(text, 17f, Pal.WHITE, true)
        b.gravity = Gravity.CENTER
        b.background = gradientBg(28, Pal.RED, Pal.PINK)
        b.setPadding(dp(16), dp(18), dp(16), dp(18))
        return b
    }

    fun smallButton(text: String, onClick: () -> Unit): TextView {
        val b = tv(text, 14f, Pal.WHITE, true)
        b.gravity = Gravity.CENTER
        b.background = roundBg(Pal.CARD, 12, Pal.BORDER)
        b.setPadding(dp(8), dp(12), dp(8), dp(12))
        b.setOnClickListener { onClick() }
        return b
    }

    fun card(): LinearLayout {
        val c = LinearLayout(ctx)
        c.orientation = LinearLayout.VERTICAL
        c.background = roundBg(Pal.CARD, 16, Pal.BORDER)
        c.setPadding(dp(14), dp(12), dp(14), dp(14))
        return c
    }

    fun statCard(label: String): Pair<LinearLayout, TextView> {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.background = roundBg(Pal.CARD, 16, Pal.BORDER)
        box.setPadding(dp(8), dp(14), dp(8), dp(14))
        val v = tv("0", 28f, Pal.WHITE, true)
        v.gravity = Gravity.CENTER
        val l = tv(label, 11f, Pal.GRAY, false)
        l.gravity = Gravity.CENTER
        box.addView(v)
        box.addView(l)
        return Pair(box, v)
    }

    /** Fila de botones tipo selector. Llama onSelect(i) al tocar. */
    fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val labels = ArrayList<TextView>()

        fun paint(sel: Int) {
            for (i in 0 until labels.size) {
                val on = i == sel
                labels[i].background = roundBg(if (on) Pal.RED else Pal.CARD, 12, if (on) Pal.RED else Pal.BORDER)
                labels[i].setTextColor(if (on) Pal.WHITE else Pal.GRAY)
            }
        }

        for (i in 0 until options.size) {
            val t = tv(options[i], 14f, Pal.GRAY, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(6), dp(13), dp(6), dp(13))
            t.setOnClickListener {
                paint(i)
                onSelect(i)
            }
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.setMargins(dp(3), 0, dp(3), 0)
            row.addView(t, p)
            labels.add(t)
        }
        paint(selected)
        return row
    }

    fun seekBar(max: Int, progress: Int, onChange: (Int, Boolean) -> Unit): SeekBar {
        val s = SeekBar(ctx)
        s.max = max
        s.progress = progress
        s.progressTintList = ColorStateList.valueOf(Pal.RED)
        s.thumbTintList = ColorStateList.valueOf(Pal.RED)
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                onChange(p, fromUser)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        return s
    }

    /** Opciones repartidas en filas de perRow botones. */
    fun choices(options: List<String>, selected: Int, perRow: Int, onSelect: (Int) -> Unit): ChoiceGroup {
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        val labels = ArrayList<TextView>()

        fun paint(sel: Int) {
            for (i in 0 until labels.size) {
                val on = i == sel
                labels[i].background = roundBg(if (on) Pal.RED else Pal.CARD, 12, if (on) Pal.RED else Pal.BORDER)
                labels[i].setTextColor(if (on) Pal.WHITE else Pal.GRAY)
            }
        }

        var row: LinearLayout? = null
        for (i in 0 until options.size) {
            if (i % perRow == 0) {
                row = LinearLayout(ctx)
                row.orientation = LinearLayout.HORIZONTAL
                box.addView(row, lp(top = if (i == 0) 0 else 6))
            }
            val t = tv(options[i], 14f, Pal.GRAY, true)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(6), dp(13), dp(6), dp(13))
            t.setOnClickListener {
                paint(i)
                onSelect(i)
            }
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.setMargins(dp(3), 0, dp(3), 0)
            row?.addView(t, p)
            labels.add(t)
        }
        // Relleno para que los botones de la última fila no se estiren
        val rest = options.size % perRow
        if (rest != 0) {
            for (k in rest until perRow) {
                val sp = View(ctx)
                val p = LinearLayout.LayoutParams(0, 1, 1f)
                p.setMargins(dp(3), 0, dp(3), 0)
                row?.addView(sp, p)
            }
        }
        paint(selected)
        return ChoiceGroup(box) { sel -> paint(sel) }
    }
}
