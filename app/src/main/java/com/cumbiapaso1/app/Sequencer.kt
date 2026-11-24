package com.cumbiapaso1.app

import android.os.Handler
import android.os.Looper

/** Secuenciador de 16 pasos con temporización de alta precisión. */
class Sequencer(
    private val samples: Samples,
    private val model: RhythmModel,
    private val onStep: (Int) -> Unit
) {
    @Volatile var bpm: Int = 95
    @Volatile private var running = false
    private var thread: Thread? = null
    private val ui = Handler(Looper.getMainLooper())

    val isRunning: Boolean
        get() = running

    fun start() {
        if (running) return
        running = true
        val t = Thread { loop() }
        t.priority = Thread.MAX_PRIORITY
        thread = t
        t.start()
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (e: InterruptedException) {
            // ignorar
        }
        thread = null
    }

    private fun loop() {
        var step = 0
        var next = System.nanoTime() + 100_000_000L
        while (running) {
            var wait = next - System.nanoTime()
            while (wait > 2_000_000L && running) {
                try {
                    Thread.sleep(wait / 1_000_000L - 1L)
                } catch (e: InterruptedException) {
                    return
                }
                wait = next - System.nanoTime()
            }
            while (System.nanoTime() < next && running) {
                // espera activa corta para mayor precisión
            }
            if (!running) break

            val s = step
            for (i in 0 until 4) {
                val v = model.pat[i][s]
                if (v > 0 && !model.mute[i]) {
                    samples.play(i, model.vol[i] * (if (v == 2) 1f else 0.6f))
                }
            }
            ui.post { onStep(s) }

            step = (step + 1) % Cumbia.STEPS
            val stepNs = 60_000_000_000L / bpm / 4L
            next += stepNs
            val now = System.nanoTime()
            if (next < now - 100_000_000L) next = now
        }
    }
}
