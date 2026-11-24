package com.cumbiapaso1.app

import android.app.Activity
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Pestaña Música: cargar canción, BPM, golpes graves, vibración y guía de cumbia. */
class MusicaScreen(
    private val act: Activity,
    private val ui: Ui,
    private val store: Store,
    model: RhythmModel,
    samples: Samples,
    vibe: Vibe,
    private val pick: () -> Unit
) : Screen {

    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var destroyed = false
    private var seeking = false
    private var soundToken = 0
    private var currentUri: Uri? = null
    private lateinit var soundGroup: ChoiceGroup
    private lateinit var soundHint: TextView
    private val speeds = floatArrayOf(0.7f, 0.85f, 1f, 1.15f)

    private lateinit var nameTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var controls: LinearLayout
    private lateinit var infoTv: TextView
    private lateinit var bpmTv: TextView
    private lateinit var nudgeTv: TextView
    private lateinit var timeTv: TextView
    private lateinit var seek: SeekBar
    private lateinit var playBtn: TextView

    private val player = SongPlayer(act, samples, model, vibe,
        { pos, dur -> onTick(pos, dur) },
        { onEnded() })

    override val view: View = build()

    private fun build(): View {
        val scroll = ScrollView(act)
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(24))
        scroll.addView(col, LinearLayout.LayoutParams(MATCH, WRAP))

        col.addView(ui.tv("Música", 24f, Pal.WHITE, true))
        col.addView(ui.tv("Carga una canción: detecto su BPM y sus golpes graves.", 13f, Pal.GRAY, false), ui.lp(top = 2))

        val load = ui.bigButton("Cargar música del teléfono")
        load.setOnClickListener {
            player.pause()
            updatePlayButton()
            pick()
        }
        col.addView(load, ui.lp(top = 16))

        nameTv = ui.tv("", 16f, Pal.WHITE, true)
        col.addView(nameTv, ui.lp(top = 14))
        statusTv = ui.tv("Aún no has cargado ninguna canción", 13f, Pal.GRAY, false)
        col.addView(statusTv, ui.lp(top = 2))

        controls = LinearLayout(act)
        controls.orientation = LinearLayout.VERTICAL
        controls.visibility = View.GONE
        col.addView(controls, ui.lp(top = 8))

        infoTv = ui.tv("", 14f, Pal.CYAN, true)
        controls.addView(infoTv)

        seek = ui.seekBar(1000, 0) { p, fromUser ->
            if (fromUser) updateTime(p)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) updateTime(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                seeking = true
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                seeking = false
                player.seekTo(seek.progress)
            }
        })
        controls.addView(seek, ui.lp(top = 8))
        timeTv = ui.tv("0:00 / 0:00", 12f, Pal.GRAY, false)
        controls.addView(timeTv)

        playBtn = ui.bigButton("▶  Reproducir")
        playBtn.setOnClickListener { togglePlay() }
        controls.addView(playBtn, ui.lp(top = 12))

        controls.addView(ui.section("SONIDO DE LA CANCIÓN"), ui.lp(top = 20))
        soundGroup = ui.choices(SoundModes.NAMES, 0, 3) { i -> onSoundMode(i) }
        controls.addView(soundGroup.view, ui.lp(top = 8))
        soundHint = ui.tv(SoundModes.HINTS[0], 12f, Pal.GRAY, false)
        controls.addView(soundHint, ui.lp(top = 6))
        controls.addView(ui.tv(
            "Es un filtro por frecuencias, no separación por IA: no puede aislar un instrumento que comparte frecuencias con otro. La vibración y la guía siguen usando la canción original.",
            12f, Pal.GRAY, false), ui.lp(top = 2))

        controls.addView(ui.section("VELOCIDAD"), ui.lp(top = 20))
        controls.addView(ui.segmented(listOf("70%", "85%", "100%", "115%"), 2) { i ->
            player.speed = speeds[i]
            if (player.isPlaying) player.applySpeed()
        }, ui.lp(top = 8))

        controls.addView(ui.section("VIBRAR EN GOLPES GRAVES"), ui.lp(top = 20))
        controls.addView(ui.segmented(listOf("Sí", "No"), store.songVibe) { i ->
            store.songVibe = i
            player.vibrateOn = i == 0
            player.resetPointers()
        }, ui.lp(top = 8))

        controls.addView(ui.section("GUÍA DE CUMBIA ENCIMA"), ui.lp(top = 20))
        controls.addView(ui.segmented(listOf("Sí", "No"), store.songGuide) { i ->
            store.songGuide = i
            player.guideOn = i == 0
            player.resetPointers()
        }, ui.lp(top = 8))
        controls.addView(ui.tv("Volumen de la guía", 12f, Pal.GRAY, false), ui.lp(top = 8))
        controls.addView(ui.seekBar(100, store.guideVol) { p, _ ->
            store.guideVol = p
            player.guideVol = p / 100f
        })
        controls.addView(ui.tv("La guía usa el patrón y los volúmenes de la pestaña Ritmos.", 12f, Pal.GRAY, false))

        controls.addView(ui.section("AJUSTE FINO"), ui.lp(top = 20))
        bpmTv = ui.tv("", 14f, Pal.WHITE, true)
        controls.addView(bpmTv, ui.lp(top = 6))
        val r1 = LinearLayout(act)
        r1.orientation = LinearLayout.HORIZONTAL
        addBtn(r1, "BPM −") { adjustBpm(-1f) }
        addBtn(r1, "BPM +") { adjustBpm(1f) }
        controls.addView(r1, ui.lp(top = 6))

        nudgeTv = ui.tv("", 14f, Pal.WHITE, true)
        controls.addView(nudgeTv, ui.lp(top = 10))
        val r2 = LinearLayout(act)
        r2.orientation = LinearLayout.HORIZONTAL
        addBtn(r2, "◀ 10 ms") { adjustNudge(-10f) }
        addBtn(r2, "10 ms ▶") { adjustNudge(10f) }
        controls.addView(r2, ui.lp(top = 6))

        val r3 = LinearLayout(act)
        r3.orientation = LinearLayout.HORIZONTAL
        addBtn(r3, "Mover el tiempo 1") {
            player.beatShift = (player.beatShift + 1) % 4
            player.resetPointers()
            refreshNudge()
        }
        controls.addView(r3, ui.lp(top = 8))
        controls.addView(ui.tv(
            "Si la guía suena corrida: mueve el tiempo 1 hasta que el bajo caiga en el 1, y afina con los ms.",
            12f, Pal.GRAY, false), ui.lp(top = 6))

        return scroll
    }

    private fun addBtn(row: LinearLayout, text: String, onClick: () -> Unit) {
        val b = ui.smallButton(text, onClick)
        val p = LinearLayout.LayoutParams(0, WRAP, 1f)
        p.setMargins(ui.dp(3), 0, ui.dp(3), 0)
        row.addView(b, p)
    }

    // ------------------------------------------------------------ carga y análisis

    fun onPicked(uri: Uri) {
        token++
        soundToken++
        val my = token
        currentUri = uri
        clearVariants()
        soundGroup.select(0)
        soundHint.text = SoundModes.HINTS[0]
        player.release()
        player.analysis = null
        controls.visibility = View.GONE
        nameTv.text = displayName(uri)
        statusTv.text = "Abriendo…"
        try {
            player.load(uri)
        } catch (e: Exception) {
            statusTv.text = "No se pudo abrir ese archivo de audio"
            return
        }
        statusTv.text = "Analizando… 0%"
        Thread {
            var result: Analysis? = null
            val dec = try {
                AudioDecoder.decode(act, uri, 11025, { my != token || destroyed }) { p ->
                    handler.post { if (my == token) statusTv.text = "Analizando… $p%" }
                }
            } catch (e: Exception) {
                null
            }
            if (dec != null) {
                result = try {
                    SongAnalyzer.analyze(dec.samples, dec.rate) { my != token || destroyed }
                } catch (e: Exception) {
                    null
                }
            }
            handler.post {
                if (my == token && !destroyed) finishAnalysis(result)
            }
        }.start()
    }

    private fun finishAnalysis(a: Analysis?) {
        if (a == null) {
            statusTv.text = "No pude analizar este audio (formato no compatible o muy corto)"
            return
        }
        player.analysis = a
        player.bpm = a.bpm
        player.nudgeMs = 0f
        player.beatShift = 0
        player.speed = 1f
        player.vibrateOn = store.songVibe == 0
        player.guideOn = store.songGuide == 0
        player.vibLevel = store.vib
        player.guideVol = store.guideVol / 100f
        statusTv.text = "Listo"
        infoTv.text = String.format(Locale.US, "%d golpes graves detectados", a.beatsMs.size)
        seek.max = maxOf(1, a.durationMs.toInt())
        seek.progress = 0
        updateTime(0)
        refreshNudge()
        controls.visibility = View.VISIBLE
        player.resetPointers(0f)
    }

    private fun clearVariants() {
        for (m in 1 until SoundModes.NAMES.size) {
            File(act.cacheDir, "variant_" + m + ".wav").delete()
        }
    }

    private fun onSoundMode(i: Int) {
        val uri = currentUri ?: return
        if (player.analysis == null) return
        soundToken++
        val my = soundToken
        soundHint.text = SoundModes.HINTS[i]
        if (i == 0) {
            player.switchTo(null)
            statusTv.text = "Listo"
            return
        }
        val f = File(act.cacheDir, "variant_" + i + ".wav")
        if (f.exists() && f.length() > 44L) {
            player.switchTo(f)
            statusTv.text = "Listo"
            return
        }
        statusTv.text = "Preparando sonido… 0%"
        Thread {
            val ok = try {
                AudioFilter.render(act, uri, i, f, { my != soundToken || destroyed }) { p ->
                    handler.post { if (my == soundToken) statusTv.text = "Preparando sonido… $p%" }
                }
            } catch (e: Exception) {
                false
            }
            handler.post {
                if (my == soundToken && !destroyed) {
                    if (ok && player.switchTo(f)) {
                        statusTv.text = "Listo"
                    } else {
                        statusTv.text = "No pude preparar ese sonido con este archivo"
                        soundGroup.select(0)
                        soundHint.text = SoundModes.HINTS[0]
                        player.switchTo(null)
                    }
                }
            }
        }.start()
    }

    private fun displayName(uri: Uri): String {
        try {
            val c = act.contentResolver.query(uri, null, null, null, null)
            if (c != null) {
                c.use {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && it.moveToFirst()) {
                        val n = it.getString(idx)
                        if (n != null) return n
                    }
                }
            }
        } catch (e: Exception) {
            // ignorar
        }
        return "Canción"
    }

    // ------------------------------------------------------------ controles

    private fun togglePlay() {
        if (player.analysis == null) return
        if (player.isPlaying) player.pause() else player.play()
        updatePlayButton()
    }

    private fun updatePlayButton() {
        playBtn.text = if (player.isPlaying) "⏸  Pausar" else "▶  Reproducir"
    }

    private fun adjustBpm(d: Float) {
        player.bpm = (player.bpm + d).coerceIn(40f, 220f)
        player.resetPointers()
        refreshNudge()
    }

    private fun adjustNudge(d: Float) {
        player.nudgeMs += d
        player.resetPointers()
        refreshNudge()
    }

    private fun refreshNudge() {
        bpmTv.text = String.format(Locale.US, "BPM de la canción: %.1f", player.bpm)
        nudgeTv.text = String.format(Locale.US, "Desfase de la guía: %d ms · tiempo 1 movido: %d",
            player.nudgeMs.toInt(), player.beatShift)
    }

    private fun onTick(pos: Int, dur: Int) {
        if (!seeking) {
            seek.progress = pos
            updateTime(pos)
        }
    }

    private fun onEnded() {
        updatePlayButton()
        seek.progress = 0
        updateTime(0)
        player.seekTo(0)
    }

    private fun updateTime(posMs: Int) {
        timeTv.text = fmt(posMs) + " / " + fmt(seek.max)
    }

    private fun fmt(ms: Int): String {
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    override fun onLeave() {
        if (player.isPlaying) player.pause()
        updatePlayButton()
    }

    fun destroy() {
        destroyed = true
        player.release()
        clearVariants()
    }
}
