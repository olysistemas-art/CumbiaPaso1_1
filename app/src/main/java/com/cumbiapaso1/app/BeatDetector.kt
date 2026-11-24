package com.cumbiapaso1.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Escucha el micrófono, calcula el espectro (FFT) y detecta golpes en la
 * banda grave (~54-150 Hz: bajo / conga / tambora) que marcan el tiempo 1.
 * Todo local, sin internet y sin guardar audio.
 */
class BeatDetector(private val listener: Listener) {

    interface Listener {
        fun onBeat()
        fun onFrame(bars: FloatArray)
    }

    companion object {
        const val BAR_COUNT = 16
    }

    /** Razón de energía grave sobre el promedio para contar un golpe. Menor = más sensible. */
    @Volatile var sensitivity: Float = 1.7f

    /** Tiempo mínimo entre golpes (ms). */
    @Volatile var minIntervalMs: Long = 300L

    private val sampleRate = 44100
    private val fftSize = 4096
    private val hop = 1024
    private val ui = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    private val window = FloatArray(fftSize) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))).toFloat()
    }
    private val cosT = FloatArray(fftSize / 2) { k -> cos(2.0 * PI * k / fftSize).toFloat() }
    private val sinT = FloatArray(fftSize / 2) { k -> (-sin(2.0 * PI * k / fftSize)).toFloat() }

    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, hop * 4)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        try {
            rec.startRecording()
        } catch (e: Exception) {
            rec.release()
            return false
        }
        record = rec
        running = true
        val t = Thread { loop(rec) }
        thread = t
        t.start()
        return true
    }

    fun stop() {
        running = false
        try {
            thread?.join(800)
        } catch (e: InterruptedException) {
            // ignorar
        }
        thread = null
        val rec = record
        if (rec != null) {
            try {
                rec.stop()
            } catch (e: Exception) {
                // ignorar
            }
            rec.release()
        }
        record = null
    }

    private fun loop(rec: AudioRecord) {
        val chunk = ShortArray(hop)
        val ring = FloatArray(fftSize)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val hist = FloatArray(43)
        var histCount = 0
        var histPos = 0
        var prevE = 0f
        var lastBeat = 0L
        var filled = 0

        while (running) {
            val n = rec.read(chunk, 0, hop)
            if (n < 0) break
            if (n == 0) continue

            System.arraycopy(ring, n, ring, 0, fftSize - n)
            for (i in 0 until n) {
                ring[fftSize - n + i] = chunk[i] / 32768f
            }
            filled += n
            if (filled < fftSize) continue

            for (i in 0 until fftSize) {
                re[i] = ring[i] * window[i]
                im[i] = 0f
            }
            fft(re, im)

            // Energía en la banda grave: bins 5..14 (~54-151 Hz con 10.77 Hz por bin)
            var e = 0f
            for (b in 5..14) {
                e += re[b] * re[b] + im[b] * im[b]
            }

            // Promedio móvil de ~1 segundo
            val cnt = if (histCount < hist.size) histCount else hist.size
            var avg = 0f
            for (i in 0 until cnt) avg += hist[i]
            if (cnt > 0) avg /= cnt

            val now = SystemClock.elapsedRealtime()
            val isBeat = cnt >= 10 &&
                e > 60f &&
                e > avg * sensitivity &&
                e > prevE &&
                (now - lastBeat) >= minIntervalMs

            hist[histPos] = e
            histPos = (histPos + 1) % hist.size
            if (histCount < hist.size) histCount++
            prevE = e

            if (isBeat) {
                lastBeat = now
                ui.post { listener.onBeat() }
            }

            // Barras del espectro (32 a 366 Hz aprox.) para la visualización
            val bars = FloatArray(BAR_COUNT)
            for (b in 0 until BAR_COUNT) {
                val k = 3 + b * 2
                val p = re[k] * re[k] + im[k] * im[k] + re[k + 1] * re[k + 1] + im[k + 1] * im[k + 1]
                val amp = sqrt(p / 2f) / 1024f
                val db = 20f * log10(amp + 0.00001f)
                bars[b] = ((db + 80f) / 60f).coerceIn(0f, 1f)
            }
            ui.post { listener.onFrame(bars) }
        }
    }

    // FFT iterativa radix-2 en sitio
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = fftSize
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val step = n / len
            var i = 0
            while (i < n) {
                var k = 0
                for (m in 0 until half) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val a = i + m
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] = re[a] + tr
                    im[a] = im[a] + ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}
