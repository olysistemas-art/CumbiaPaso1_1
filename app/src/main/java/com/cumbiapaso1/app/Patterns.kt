package com.cumbiapaso1.app

/** Instrumentos y patrones base de cumbia (16 pasos = 1 compás de 4 tiempos). */
object Cumbia {
    val NAMES = listOf("Güira", "Bajo", "Conga", "Timbal")

    /** Nombres de archivo en res/raw (sin extensión) para sonidos propios. */
    val RAW = listOf("guira", "bajo", "conga", "timbal")

    const val STEPS = 16

    val PRESET_NAMES = listOf("Colombiana", "Sonidera", "Mexicana")

    // 0 = silencio, 1 = golpe, 2 = acento. Orden: güira, bajo, conga, timbal.
    val PRESETS: List<List<String>> = listOf(
        listOf("2111211121112111", "2000001020000010", "0000102000001020", "0000200000002000"),
        listOf("2121212121212121", "2010001020100010", "0010201000102010", "0000200000102010"),
        listOf("2010201020102010", "2000200020002000", "0010001000100010", "0000200000002000")
    )
}
