package com.cumbiapaso1.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** FFT radix-2 iterativa en sitio. n debe ser potencia de 2. */
class Fft(private val n: Int) {
    private val cosT = FloatArray(n / 2) { k -> cos(2.0 * PI * k / n).toFloat() }
    private val sinT = FloatArray(n / 2) { k -> (-sin(2.0 * PI * k / n)).toFloat() }

    fun transform(re: FloatArray, im: FloatArray) {
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
