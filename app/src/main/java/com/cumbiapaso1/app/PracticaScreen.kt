package com.cumbiapaso1.app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pestaña Práctica: escucha el bajo con el micrófono y vibra en cada golpe. */
class PracticaScreen(
    private val act: Activity,
    private val ui: Ui,
    private val store: Store,
    private val vibe: Vibe,
    private val hasMic: () -> Boolean,
    private val requestMic: () -> Unit
) : Screen, BeatDetector.Listener {

    private val detector = BeatDetector(this)
    private val handler = Handler(Looper.getMainLooper())
    private var detecting = false
    private var beats = 0
    private var bpmShown = 0
    private var startedAt = 0L
    private var lastBeatAt = 0L
    private val intervals = ArrayList<Long>()
    private val taps = ArrayList<Long>()

    private lateinit var cumbia: CumbiaView
    private lateinit var beatsTv: TextView
    private lateinit var bpmTv: TextView
    private lateinit var timeTv: TextView
    private lateinit var startBtn: TextView
    private lateinit var calibBtn: TextView
    private lateinit var historyBox: LinearLayout

    private val clock = object : Runnable {
        override fun run() {
            if (detecting) {
                val s = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
                timeTv.text = String.format(Locale.US, "%d:%02d", s / 60, s % 60)
                handler.postDelayed(this, 500)
            }
        }
    }

    override val view: View = build()

    private fun build(): View {
        val scroll = ScrollView(act)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(24))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(ui.tv("Práctica con micrófono", 24f, Pal.WHITE, true))
        col.addView(ui.tv("Ignora la güira: sigue el bajo o la conga", 13f, Pal.GRAY, false), ui.lp(top = 2))

        cumbia = CumbiaView(act)
        col.addView(cumbia, ui.lp(h = ui.dp(300), top = 6))

        val stats = LinearLayout(act)
        stats.orientation = LinearLayout.HORIZONTAL
        val c1 = ui.statCard("TIEMPOS 1")
        val c2 = ui.statCard("BPM")
        val c3 = ui.statCard("TIEMPO")
        beatsTv = c1.second
        bpmTv = c2.second
        timeTv = c3.second
        bpmTv.text = "--"
        timeTv.text = "0:00"
        for (c in listOf(c1.first, c2.first, c3.first)) {
            val p = LinearLayout.LayoutParams(0, WRAP, 1f)
            p.setMargins(ui.dp(4), 0, ui.dp(4), 0)
            stats.addView(c, p)
        }
        col.addView(stats, ui.lp(top = 4))

        startBtn = ui.bigButton("Iniciar detección del tiempo 1")
        startBtn.setOnClickListener {
            if (detecting) stopDetection() else startDetection()
        }
        col.addView(startBtn, ui.lp(top = 18))

        col.addView(ui.section("MODO DE DETECCIÓN"), ui.lp(top = 22))
        col.addView(ui.segmented(listOf("Automático", "Calibrar BPM"), store.mode) { i ->
            store.mode = i
            calibBtn.visibility = if (i == 1) View.VISIBLE else View.GONE
            applySettings()
        }, ui.lp(top = 8))

        calibBtn = ui.tv("Toca aquí al ritmo del bajo para calibrar", 14f, Pal.WHITE, true)
        calibBtn.gravity = Gravity.CENTER
        calibBtn.background = ui.roundBg(Pal.CARD, 14, Pal.CYAN)
        calibBtn.setPadding(ui.dp(12), ui.dp(16), ui.dp(12), ui.dp(16))
        calibBtn.visibility = if (store.mode == 1) View.VISIBLE else View.GONE
        calibBtn.setOnClickListener { onCalibTap() }
        col.addView(calibBtn, ui.lp(top = 10))

        col.addView(ui.section("PIE INICIAL"), ui.lp(top = 20))
        col.addView(ui.segmented(listOf("Pie izquierdo", "Pie derecho"), store.foot) { i ->
            store.foot = i
        }, ui.lp(top = 8))

        col.addView(ui.section("VIBRACIÓN"), ui.lp(top = 20))
        col.addView(ui.segmented(listOf("Suave", "Media", "Fuerte"), store.vib) { i ->
            store.vib = i
            vibe.pulse(i)
        }, ui.lp(top = 8))

        col.addView(ui.section("SENSIBILIDAD DE GRAVES"), ui.lp(top = 20))
        col.addView(ui.seekBar(100, store.sens) { p, _ ->
            store.sens = p
            applySettings()
        }, ui.lp(top = 6))
        col.addView(ui.tv("Súbela si no detecta golpes; bájala si vibra de más.", 12f, Pal.GRAY, false), ui.lp(top = 2))

        val histBtn = ui.smallButton("Ver / ocultar historial de sesiones") {
            if (historyBox.visibility == View.VISIBLE) {
                historyBox.visibility = View.GONE
            } else {
                fillHistory()
                historyBox.visibility = View.VISIBLE
            }
        }
        col.addView(histBtn, ui.lp(top = 24))

        historyBox = LinearLayout(act)
        historyBox.orientation = LinearLayout.VERTICAL
        historyBox.visibility = View.GONE
        col.addView(historyBox, ui.lp(top = 8))

        applySettings()
        return scroll
    }

    // ------------------------------------------------------------ detección

    private fun applySettings() {
        detector.sensitivity = 2.2f - 1.0f * store.sens / 100f
        val calib = store.calibBpm
        detector.minIntervalMs =
            if (store.mode == 1 && calib > 0) (0.6f * 60000f / calib).toLong() else 300L
    }

    fun onMicResult(granted: Boolean) {
        if (granted) {
            startDetection()
        } else {
            Toast.makeText(act, "Se necesita el micrófono para escuchar el bajo", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDetection() {
        if (!hasMic()) {
            requestMic()
            return
        }
        if (store.mode == 1 && store.calibBpm == 0) {
            Toast.makeText(act, "Modo calibrar: toca el botón de calibración al ritmo del bajo", Toast.LENGTH_LONG).show()
        }
        applySettings()
        beats = 0
        bpmShown = 0
        lastBeatAt = 0L
        intervals.clear()
        if (!detector.start()) {
            Toast.makeText(act, "No se pudo abrir el micrófono", Toast.LENGTH_LONG).show()
            return
        }
        detecting = true
        startedAt = SystemClock.elapsedRealtime()
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        beatsTv.text = "0"
        bpmTv.text = "--"
        timeTv.text = "0:00"
        cumbia.active = true
        cumbia.setTexts("ESCUCHA", "sigue el bajo")
        startBtn.text = "Detener"
        startBtn.background = ui.gradientBg(28, android.graphics.Color.parseColor("#334155"),
            android.graphics.Color.parseColor("#0F172A"))
        handler.post(clock)
    }

    private fun stopDetection() {
        detecting = false
        detector.stop()
        act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cumbia.active = false
        cumbia.setTexts("LISTO", "toca iniciar")
        startBtn.text = "Iniciar detección del tiempo 1"
        startBtn.background = ui.gradientBg(28, Pal.RED, Pal.PINK)
        val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        if (beats > 0) {
            store.addSession(beats, bpmShown, secs)
            Toast.makeText(act, "Sesión guardada: $beats tiempos 1", Toast.LENGTH_SHORT).show()
            if (historyBox.visibility == View.VISIBLE) fillHistory()
        }
    }

    override fun onBeat() {
        if (!detecting) return
        beats++
        beatsTv.text = beats.toString()
        cumbia.beat()
        vibe.pulse(store.vib)

        val left = (beats % 2 == 1) == (store.foot == 0)
        cumbia.setTexts(if (left) "IZQ" else "DER", "tiempo 1")

        val now = SystemClock.elapsedRealtime()
        if (lastBeatAt > 0L) {
            val gap = now - lastBeatAt
            if (gap >= 250L && gap <= 2000L) {
                intervals.add(gap)
                if (intervals.size > 8) intervals.removeAt(0)
            }
        }
        lastBeatAt = now
        if (intervals.size >= 3) {
            val sorted = intervals.sorted()
            val med = sorted[sorted.size / 2]
            var bpm = 60000f / med
            while (bpm > 150f) bpm /= 2f
            while (bpm < 60f) bpm *= 2f
            bpmShown = bpm.toInt()
            bpmTv.text = bpmShown.toString()
        }
    }

    override fun onFrame(bars: FloatArray) {
        if (detecting) cumbia.setBars(bars)
    }

    private fun onCalibTap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps[taps.size - 1] > 2000L) taps.clear()
        taps.add(now)
        if (taps.size < 2) {
            calibBtn.text = "Sigue tocando al ritmo del bajo…"
            return
        }
        val avg = (taps[taps.size - 1] - taps[0]).toFloat() / (taps.size - 1)
        val bpm = (60000f / avg).toInt()
        if (taps.size >= 8) {
            store.calibBpm = bpm
            taps.clear()
            applySettings()
            calibBtn.text = "Calibrado: $bpm BPM · toca para recalibrar"
            Toast.makeText(act, "Calibrado a $bpm BPM", Toast.LENGTH_SHORT).show()
        } else {
            calibBtn.text = "$bpm BPM (${taps.size}/8) · sigue tocando"
        }
    }

    // ------------------------------------------------------------ historial

    private fun fillHistory() {
        historyBox.removeAllViews()
        val items = store.sessions()
        if (items.isEmpty()) {
            val e = ui.tv("Aún no hay sesiones de práctica", 14f, Pal.GRAY, false)
            e.gravity = Gravity.CENTER
            historyBox.addView(e, ui.lp(top = 12))
            return
        }
        val df = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        for (line in items) {
            val p = line.split("|")
            if (p.size < 4) continue
            val ts = p[0].toLongOrNull() ?: 0L
            val secs = p[3].toIntOrNull() ?: 0
            val box = ui.card()
            box.addView(ui.tv(p[1] + " tiempos 1 · " + p[2] + " BPM", 16f, Pal.WHITE, true))
            box.addView(ui.tv(
                df.format(Date(ts)) + " · " + (secs / 60) + ":" + String.format(Locale.US, "%02d", secs % 60),
                12f, Pal.GRAY, false), ui.lp(top = 2))
            historyBox.addView(box, ui.lp(top = 8))
        }
        val clear = ui.smallButton("Vaciar historial") {
            store.clearSessions()
            fillHistory()
        }
        historyBox.addView(clear, ui.lp(top = 12))
    }

    override fun onLeave() {
        if (detecting) stopDetection()
    }
}
