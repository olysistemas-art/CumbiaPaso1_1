package com.cumbiapaso1.app

/** Estado compartido de los 4 instrumentos: patrón, volumen y silencio. */
class RhythmModel(private val store: Store) {
    val pat = Array(4) { IntArray(Cumbia.STEPS) }
    val vol = FloatArray(4) { 0.8f }
    val mute = BooleanArray(4)

    init {
        for (i in 0 until 4) {
            val s = store.pattern(i)
            for (k in 0 until Cumbia.STEPS) {
                pat[i][k] = if (k < s.length) (s[k] - '0').coerceIn(0, 2) else 0
            }
            vol[i] = store.vol(i) / 100f
            mute[i] = store.muted(i)
        }
    }

    fun savePattern(i: Int) {
        store.setPattern(i, pat[i].joinToString(""))
    }

    fun applyPreset(p: Int) {
        for (i in 0 until 4) {
            val s = Cumbia.PRESETS[p][i]
            for (k in 0 until Cumbia.STEPS) {
                pat[i][k] = (s[k] - '0').coerceIn(0, 2)
            }
            savePattern(i)
        }
    }
}
