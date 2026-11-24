package com.cumbiapaso1.app

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File

/**
 * Reproduce una canción, hace vibrar el teléfono en cada golpe grave detectado
 * y toca encima la guía de cumbia sincronizada con la posición de la canción.
 */
class SongPlayer(
    private val ctx: Context,
    private val samples: Samples,
    private val model: RhythmModel,
    private val vibe: Vibe,
    private val onTick: (Int, Int) -> Unit,
    private val onEnd: () -> Unit
) {
    private var mp: MediaPlayer? = null
    private val ui = Handler(Looper.getMainLooper())

    var analysis: Analysis? = null
    var bpm: Float = 95f
    var nudgeMs: Float = 0f
    var beatShift: Int = 0
    var speed: Float = 1f
    var vibrateOn: Boolean = false
    var guideOn: Boolean = false
    var vibLevel: Int = 1
    var guideVol: Float = 0.7f

    private var duration = 0
    private var sourceUri: Uri? = null
    private var ptr = 0
    private var nextStep = 0L
    private var lastP = -1
    private var lastT = 0L
    private var lastUi = 0L
    private var ticking = false

    val isPlaying: Boolean
        get() = try {
            mp?.isPlaying == true
        } catch (e: Exception) {
            false
        }

    fun load(uri: Uri): Int {
        release()
        sourceUri = uri
        val p = MediaPlayer()
        p.setDataSource(ctx, uri)
        p.prepare()
        p.setOnCompletionListener {
            ticking = false
            onEnd()
        }
        mp = p
        duration = p.duration
        lastP = -1
        return duration
    }

    /** Cambia a un archivo filtrado (o al original si file es null) sin perder la posición. */
    fun switchTo(file: File?): Boolean {
        val old = mp ?: return false
        val u = sourceUri
        val pos = try {
            old.currentPosition
        } catch (e: Exception) {
            0
        }
        val was = isPlaying
        pause()
        val p = MediaPlayer()
        try {
            if (file != null) {
                p.setDataSource(file.absolutePath)
            } else {
                if (u == null) {
                    p.release()
                    return false
                }
                p.setDataSource(ctx, u)
            }
            p.prepare()
        } catch (e: Exception) {
            p.release()
            return false
        }
        p.setOnCompletionListener {
            ticking = false
            onEnd()
        }
        try {
            old.release()
        } catch (e: Exception) {
            // ignorar
        }
        mp = p
        duration = p.duration
        lastP = -1
        p.seekTo(pos)
        if (was) play() else resetPointers(pos.toFloat())
        return true
    }

    fun play() {
        val p = mp ?: return
        p.start()
        applySpeed()
        resetPointers()
        if (!ticking) {
            ticking = true
            ui.post(tick)
        }
    }

    fun pause() {
        try {
            mp?.pause()
        } catch (e: Exception) {
            // ignorar
        }
        ticking = false
        ui.removeCallbacks(tick)
    }

    fun stop() {
        pause()
        try {
            mp?.seekTo(0)
        } catch (e: Exception) {
            // ignorar
        }
    }

    fun seekTo(ms: Int) {
        try {
            mp?.seekTo(ms)
        } catch (e: Exception) {
            // ignorar
        }
        lastP = -1
        resetPointers(ms.toFloat())
    }

    fun release() {
        pause()
        try {
            mp?.release()
        } catch (e: Exception) {
            // ignorar
        }
        mp = null
    }

    fun applySpeed() {
        val p = mp ?: return
        try {
            val pp = PlaybackParams()
            pp.speed = speed
            pp.pitch = 1f
            p.playbackParams = pp
        } catch (e: Exception) {
            // ignorar: el dispositivo no soporta cambiar velocidad
        }
    }

    /** Llamar tras cambiar BPM, desfase, tiempo 1 o activar opciones. */
    fun resetPointers(at: Float = -1f) {
        val a = analysis ?: return
        val pos = if (at >= 0f) at else estimate()
        // Siguiente golpe grave
        val arr = a.beatsMs
        var i = java.util.Arrays.binarySearch(arr, pos.toLong())
        if (i < 0) i = -i - 1
        ptr = i
        // Siguiente paso de la guía
        val sm = stepMs()
        nextStep = Math.ceil(((pos - base()) / sm).toDouble()).toLong()
    }

    private fun stepMs(): Float = 60000f / bpm / 4f

    private fun base(): Float {
        val a = analysis ?: return 0f
        return a.offsetMs + beatShift * (60000f / bpm) + nudgeMs
    }

    private fun estimate(): Float {
        val p = mp ?: return 0f
        val cur = try {
            p.currentPosition
        } catch (e: Exception) {
            0
        }
        val now = SystemClock.elapsedRealtime()
        if (cur != lastP) {
            lastP = cur
            lastT = now
        }
        var extra = (now - lastT) * speed
        if (extra > 40f) extra = 40f
        if (!isPlaying) extra = 0f
        return cur + extra
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!ticking) return
            val a = analysis
            val pos = estimate()
            val now = SystemClock.elapsedRealtime()
            if (now - lastUi >= 100L) {
                lastUi = now
                onTick(pos.toInt(), duration)
            }
            if (a != null) {
                if (vibrateOn) {
                    val arr = a.beatsMs
                    while (ptr < arr.size && arr[ptr] - 25f <= pos) {
                        if (pos - arr[ptr] < 200f) vibe.pulse(vibLevel)
                        ptr++
                    }
                }
                if (guideOn) {
                    val sm = stepMs()
                    val b = base()
                    while (b + nextStep * sm - 30f <= pos) {
                        if (pos - (b + nextStep * sm) < 150f) {
                            playStep((((nextStep % 16L) + 16L) % 16L).toInt())
                        }
                        nextStep++
                    }
                }
            }
            ui.postDelayed(this, 8)
        }
    }

    private fun playStep(s: Int) {
        for (i in 0 until 4) {
            val v = model.pat[i][s]
            if (v > 0 && !model.mute[i]) {
                samples.play(i, model.vol[i] * guideVol * (if (v == 2) 1f else 0.6f))
            }
        }
    }
}
