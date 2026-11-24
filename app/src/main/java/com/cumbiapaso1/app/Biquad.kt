package com.cumbiapaso1.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Filtro biquad (forma transpuesta II) con coeficientes RBJ. */
class Biquad(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    companion object {
        private const val Q = 0.70710678

        fun lowPass(fc: Double, fs: Double): Biquad {
            val w = 2.0 * PI * fc / fs
            val c = cos(w)
            val al = sin(w) / (2.0 * Q)
            val a0 = 1.0 + al
            return Biquad((1.0 - c) / 2.0 / a0, (1.0 - c) / a0, (1.0 - c) / 2.0 / a0,
                -2.0 * c / a0, (1.0 - al) / a0)
        }

        fun highPass(fc: Double, fs: Double): Biquad {
            val w = 2.0 * PI * fc / fs
            val c = cos(w)
            val al = sin(w) / (2.0 * Q)
            val a0 = 1.0 + al
            return Biquad((1.0 + c) / 2.0 / a0, -(1.0 + c) / a0, (1.0 + c) / 2.0 / a0,
                -2.0 * c / a0, (1.0 - al) / a0)
        }
    }
}

/** Modos de sonido de la canción (filtrado por frecuencia, 24 dB/oct). */
object SoundModes {
    val NAMES = listOf("Original", "Solo bajo", "Bajo y tambores", "Sin güira", "Sin bajo")

    val HINTS = listOf(
        "Canción completa.",
        "Solo lo más grave (bajo y el golpe grave de la tambora). Ideal para sentir el tiempo 1.",
        "Graves y medios bajos: bajo y tambores, sin güira ni voces agudas.",
        "Quita lo agudo: güira, platillos y brillo.",
        "Quita el bajo para escuchar todo lo demás."
    )

    fun gain(mode: Int): Double = when (mode) {
        1 -> 1.5
        2 -> 1.25
        else -> 1.0
    }

    /** Cadena de filtros para un canal. */
    fun chain(mode: Int, fs: Double): Array<Biquad> = when (mode) {
        1 -> arrayOf(Biquad.lowPass(180.0, fs), Biquad.lowPass(180.0, fs))
        2 -> arrayOf(Biquad.lowPass(700.0, fs), Biquad.lowPass(700.0, fs))
        3 -> arrayOf(Biquad.lowPass(4500.0, fs), Biquad.lowPass(4500.0, fs))
        4 -> arrayOf(Biquad.highPass(200.0, fs), Biquad.highPass(200.0, fs))
        else -> arrayOf()
    }
}
