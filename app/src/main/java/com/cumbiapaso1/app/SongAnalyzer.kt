package com.cumbiapaso1.app

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class Analysis(
    val beatsMs: LongArray,
    val bpm: Float,
    val offsetMs: Float,
    val durationMs: Long
)

/** Detecta golpes graves, BPM y fase del compás de una canción ya decodificada. */
object SongAnalyzer {
    private const val N = 1024
    private const val HOP = 256

    fun analyze(x: FloatArray, rate: Int, cancel: () -> Boolean): Analysis? {
        val frames = (x.size - N) / HOP + 1
        if (frames < 200) return null

        val fft = Fft(N)
        val win = FloatArray(N) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (N - 1))).toFloat() }
        val binHz = rate.toFloat() / N
        val lo = maxOf(2, Math.ceil((55f / binHz).toDouble()).toInt())
        val hi = maxOf(lo + 1, (150f / binHz).toInt())
        val re = FloatArray(N)
        val im = FloatArray(N)
        val e = FloatArray(frames)
        var total = 0.0
        for (f in 0 until frames) {
            if ((f and 255) == 0 && cancel()) return null
            val s = f * HOP
            for (i in 0 until N) {
                re[i] = x[s + i] * win[i]
                im[i] = 0f
            }
            fft.transform(re, im)
            var sum = 0f
            for (b in lo..hi) sum += re[b] * re[b] + im[b] * im[b]
            e[f] = sum
            total += sum
        }
        val mean = (total / frames).toFloat()
        val frameMs = HOP * 1000f / rate
        val centerMs = (N / 2) * 1000f / rate
        val fps = rate.toFloat() / HOP
        val durationMs = (x.size * 1000L) / rate

        // --- Golpes graves ---
        val w = maxOf(8, Math.round(fps))
        val beats = ArrayList<Long>()
        var lastMs = -10000f
        for (f in 1 until frames) {
            val a = maxOf(0, f - w)
            var sum = 0f
            for (k in a until f) sum += e[k]
            val avg = sum / (f - a)
            val t = f * frameMs + centerMs
            if (e[f] > avg * 1.6f && e[f] > e[f - 1] && e[f] > mean * 0.5f && t - lastMs >= 200f) {
                beats.add(t.toLong())
                lastMs = t
            }
        }

        // --- BPM por autocorrelación del flujo de energía ---
        val flux = FloatArray(frames)
        var prev = sqrt(e[0])
        for (f in 1 until frames) {
            val m = sqrt(e[f])
            flux[f] = if (m > prev) m - prev else 0f
            prev = m
        }
        val lagMin = maxOf(2, (60f * fps / 200f).toInt())
        val lagMax = (60f * fps / 60f).toInt()
        val ac = FloatArray(lagMax + 2)
        var bestLag = lagMin
        var best = -1f
        for (lag in lagMin..lagMax) {
            var s = 0f
            for (f in 0 until frames - lag) s += flux[f] * flux[f + lag]
            s /= (frames - lag)
            ac[lag] = s
            if (s > best) {
                best = s
                bestLag = lag
            }
        }
        var lagF = bestLag.toFloat()
        if (bestLag > lagMin && bestLag < lagMax) {
            val y0 = ac[bestLag - 1]
            val y1 = ac[bestLag]
            val y2 = ac[bestLag + 1]
            val den = y0 - 2f * y1 + y2
            if (den != 0f) lagF += 0.5f * (y0 - y2) / den
        }
        var bpm = 60f * fps / lagF
        while (bpm >= 140f) bpm /= 2f
        while (bpm < 70f) bpm *= 2f
        var period = 60000f / bpm

        // --- Afinar tempo y fase con regresión sobre los golpes detectados ---
        // Se asigna a cada golpe un índice de tiempo (k) y se ajusta t = t0 + k * periodo.
        var bestOff = 0f
        var fitted = false
        if (beats.size >= 8) {
            var prevT = beats[0].toDouble()
            var kk = 0
            val ks = ArrayList<Double>()
            val ts = ArrayList<Double>()
            ks.add(0.0)
            ts.add(prevT)
            for (i in 1 until beats.size) {
                val tt = beats[i].toDouble()
                val d = tt - prevT
                val step = Math.round(d / period).toInt()
                if (step >= 1 && step <= 4 && abs(d - step * period) < period * 0.15) {
                    kk += step
                    ks.add(kk.toDouble())
                    ts.add(tt)
                    prevT = tt
                }
            }
            if (ks.size >= 8) {
                val n = ks.size
                var sk = 0.0
                var st = 0.0
                for (i in 0 until n) {
                    sk += ks[i]
                    st += ts[i]
                }
                val mk = sk / n
                val mt = st / n
                var num = 0.0
                var den = 0.0
                for (i in 0 until n) {
                    num += (ks[i] - mk) * (ts[i] - mt)
                    den += (ks[i] - mk) * (ks[i] - mk)
                }
                if (den > 0.0) {
                    val slope = num / den
                    val t0 = mt - slope * mk
                    if (slope > 60000.0 / 150.0 && slope < 60000.0 / 60.0) {
                        period = slope.toFloat()
                        bpm = 60000f / period
                        var o = (t0 % period).toFloat()
                        if (o < 0f) o += period
                        bestOff = o
                        fitted = true
                    }
                }
            }
        }

        // Respaldo: buscar la fase que más energía de ataque reúne
        if (!fitted) {
            var bestScore = -1f
            var off = 0f
            while (off < period) {
                var sc = 0f
                var t = off
                while (t < durationMs) {
                    val fi = Math.round((t - centerMs) / frameMs)
                    if (fi >= 0 && fi < frames) sc += flux[fi]
                    t += period
                }
                if (sc > bestScore) {
                    bestScore = sc
                    bestOff = off
                }
                off += 5f
            }
        }

        // --- Cuál de los 4 tiempos es el 1 (el de más energía grave) ---
        val barSum = FloatArray(4)
        var k = 0
        var t = bestOff
        while (t < durationMs) {
            val fi = Math.round((t - centerMs) / frameMs)
            if (fi >= 0 && fi < frames) barSum[k and 3] += e[fi]
            k++
            t += period
        }
        var bestJ = 0
        for (j in 1..3) if (barSum[j] > barSum[bestJ]) bestJ = j

        val arr = LongArray(beats.size)
        for (i in 0 until beats.size) arr[i] = beats[i]
        return Analysis(arr, bpm, bestOff + bestJ * period, durationMs)
    }
}
