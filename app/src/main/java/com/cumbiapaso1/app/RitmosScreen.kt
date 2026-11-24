package com.cumbiapaso1.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Pestaña Ritmos: 4 instrumentos con rejilla de 16 pasos editable. */
class RitmosScreen(
    private val ctx: Context,
    private val ui: Ui,
    private val store: Store,
    private val model: RhythmModel,
    private val samples: Samples
) : Screen {

    private val headers = Array(4) { ArrayList<TextView>() }
    private val cells = Array(4) { ArrayList<TextView>() }
    private val muteBtns = ArrayList<TextView>()
    private var curStep = -1
    private lateinit var playBtn: TextView
    private lateinit var bpmTv: TextView

    private val seq = Sequencer(samples, model) { s -> highlight(s) }

    override val view: View = build()

    private fun build(): View {
        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL

        val scroll = ScrollView(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(12), ui.dp(20), ui.dp(12), ui.dp(16))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(ui.tv("Ritmos de cumbia", 24f, Pal.WHITE, true))
        col.addView(ui.tv("Toca los cuadros: apagado → golpe → acento", 13f, Pal.GRAY, false), ui.lp(top = 2))

        col.addView(ui.section("ESTILO"), ui.lp(top = 16))
        col.addView(ui.segmented(Cumbia.PRESET_NAMES, store.preset) { i ->
            store.preset = i
            model.applyPreset(i)
            refreshCells()
        }, ui.lp(top = 8))

        for (i in 0 until 4) col.addView(buildCard(i), ui.lp(top = 12))

        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(buildBar(), LinearLayout.LayoutParams(MATCH, WRAP))
        return root
    }

    private fun buildCard(i: Int): View {
        val card = ui.card()

        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val name = ui.tv(Cumbia.NAMES[i], 18f, Pal.WHITE, true)
        top.addView(name, LinearLayout.LayoutParams(WRAP, WRAP))
        val src = ui.tv(if (samples.custom[i]) "  tu audio" else "  sintético", 11f, Pal.GRAY, false)
        top.addView(src, LinearLayout.LayoutParams(0, WRAP, 1f))
        val mute = ui.tv(if (model.mute[i]) "🔇" else "🔊", 22f, Pal.WHITE, false)
        mute.setPadding(ui.dp(10), ui.dp(4), ui.dp(10), ui.dp(4))
        mute.setOnClickListener {
            model.mute[i] = !model.mute[i]
            store.setMuted(i, model.mute[i])
            mute.text = if (model.mute[i]) "🔇" else "🔊"
        }
        muteBtns.add(mute)
        top.addView(mute)
        card.addView(top)

        card.addView(ui.seekBar(100, store.vol(i)) { p, _ ->
            model.vol[i] = p / 100f
            store.setVol(i, p)
        }, ui.lp(top = 2))

        // Fila de conteo
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        for (k in 0 until Cumbia.STEPS) {
            val label = if (k % 4 == 0) (k / 4 + 1).toString() else if (k % 4 == 2) "y" else "·"
            val h = ui.tv(label, 10f, Pal.GRAY, k % 4 == 0)
            h.gravity = Gravity.CENTER
            head.addView(h, cellParams(k, 0))
            headers[i].add(h)
        }
        card.addView(head, ui.lp(top = 6))

        // Fila de pasos
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        for (k in 0 until Cumbia.STEPS) {
            val c = TextView(ctx)
            c.setOnClickListener {
                model.pat[i][k] = (model.pat[i][k] + 1) % 3
                model.savePattern(i)
                styleCell(i, k)
                if (model.pat[i][k] > 0) {
                    samples.play(i, model.vol[i] * (if (model.pat[i][k] == 2) 1f else 0.6f))
                }
            }
            row.addView(c, cellParams(k, ui.dp(36)))
            cells[i].add(c)
            styleCell(i, k)
        }
        card.addView(row, ui.lp(top = 2))
        return card
    }

    private fun cellParams(k: Int, h: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(0, if (h == 0) WRAP else h, 1f)
        p.setMargins(if (k > 0 && k % 4 == 0) ui.dp(5) else ui.dp(1), 0, ui.dp(1), 0)
        return p
    }

    private fun styleCell(i: Int, k: Int) {
        val v = model.pat[i][k]
        val c = cells[i][k]
        c.background = when (v) {
            2 -> ui.roundBg(Pal.GOLD, 6, Pal.GOLD)
            1 -> ui.roundBg(Pal.RED, 6, Pal.RED)
            else -> ui.roundBg(Pal.OFF, 6, Pal.BORDER)
        }
    }

    private fun refreshCells() {
        for (i in 0 until 4) for (k in 0 until Cumbia.STEPS) styleCell(i, k)
    }

    private fun buildBar(): View {
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.parseColor("#12121F"))
        bar.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10))

        playBtn = ui.tv("▶", 28f, Pal.WHITE, true)
        playBtn.gravity = Gravity.CENTER
        playBtn.background = ui.gradientBg(36, Pal.RED, Pal.PINK)
        playBtn.setOnClickListener { toggle() }
        bar.addView(playBtn, LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)))

        val spacer = View(ctx)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val bpmBox = LinearLayout(ctx)
        bpmBox.orientation = LinearLayout.HORIZONTAL
        bpmBox.gravity = Gravity.CENTER_VERTICAL
        bpmBox.background = ui.roundBg(Pal.CARD, 28, Pal.BORDER)
        val minus = ui.tv("−", 28f, Pal.WHITE, true)
        minus.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(8))
        bpmTv = ui.tv(store.bpm.toString(), 26f, Pal.WHITE, true)
        bpmTv.gravity = Gravity.CENTER
        bpmTv.minWidth = ui.dp(56)
        val plus = ui.tv("+", 28f, Pal.WHITE, true)
        plus.setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(8))
        minus.setOnClickListener { setBpm(store.bpm - 1) }
        plus.setOnClickListener { setBpm(store.bpm + 1) }
        minus.setOnLongClickListener { setBpm(store.bpm - 5); true }
        plus.setOnLongClickListener { setBpm(store.bpm + 5); true }
        bpmBox.addView(minus)
        bpmBox.addView(bpmTv)
        bpmBox.addView(plus)
        bar.addView(bpmBox)
        return bar
    }

    private fun setBpm(v: Int) {
        val b = v.coerceIn(40, 200)
        store.bpm = b
        seq.bpm = b
        bpmTv.text = b.toString()
    }

    private fun toggle() {
        if (seq.isRunning) {
            seq.stop()
            playBtn.text = "▶"
            clearHighlight()
        } else {
            seq.bpm = store.bpm
            seq.start()
            playBtn.text = "⏸"
        }
    }

    private fun highlight(s: Int) {
        if (!seq.isRunning) return
        if (curStep >= 0) setHeader(curStep, false)
        curStep = s
        setHeader(s, true)
    }

    private fun clearHighlight() {
        if (curStep >= 0) setHeader(curStep, false)
        curStep = -1
    }

    private fun setHeader(s: Int, on: Boolean) {
        for (i in 0 until 4) {
            headers[i][s].setTextColor(if (on) Pal.CYAN else Pal.GRAY)
        }
    }

    override fun onLeave() {
        if (seq.isRunning) {
            seq.stop()
            playBtn.text = "▶"
            clearHighlight()
        }
    }
}
