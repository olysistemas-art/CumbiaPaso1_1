package com.cumbiapaso1.app

import android.content.Context

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cumbia", Context.MODE_PRIVATE)

    // ---- Práctica (micrófono) ----
    /** 0 = Automático, 1 = Calibrar BPM */
    var mode: Int
        get() = sp.getInt("mode", 0)
        set(v) { sp.edit().putInt("mode", v).apply() }

    /** 0 = pie izquierdo primero, 1 = derecho */
    var foot: Int
        get() = sp.getInt("foot", 0)
        set(v) { sp.edit().putInt("foot", v).apply() }

    /** 0 = suave, 1 = media, 2 = fuerte */
    var vib: Int
        get() = sp.getInt("vib", 1)
        set(v) { sp.edit().putInt("vib", v).apply() }

    /** 0..100 */
    var sens: Int
        get() = sp.getInt("sens", 50)
        set(v) { sp.edit().putInt("sens", v).apply() }

    var calibBpm: Int
        get() = sp.getInt("calib", 0)
        set(v) { sp.edit().putInt("calib", v).apply() }

    // ---- Ritmos ----
    var bpm: Int
        get() = sp.getInt("bpm", 95)
        set(v) { sp.edit().putInt("bpm", v).apply() }

    var preset: Int
        get() = sp.getInt("preset", 0)
        set(v) { sp.edit().putInt("preset", v).apply() }

    fun pattern(i: Int): String =
        sp.getString("pat_$i", Cumbia.PRESETS[0][i]) ?: Cumbia.PRESETS[0][i]

    fun setPattern(i: Int, s: String) {
        sp.edit().putString("pat_$i", s).apply()
    }

    fun vol(i: Int): Int = sp.getInt("vol_$i", 80)
    fun setVol(i: Int, v: Int) { sp.edit().putInt("vol_$i", v).apply() }

    fun muted(i: Int): Boolean = sp.getBoolean("mute_$i", false)
    fun setMuted(i: Int, m: Boolean) { sp.edit().putBoolean("mute_$i", m).apply() }

    // ---- Música ----
    var songVibe: Int
        get() = sp.getInt("song_vibe", 0)
        set(v) { sp.edit().putInt("song_vibe", v).apply() }

    var songGuide: Int
        get() = sp.getInt("song_guide", 0)
        set(v) { sp.edit().putInt("song_guide", v).apply() }

    var guideVol: Int
        get() = sp.getInt("guide_vol", 70)
        set(v) { sp.edit().putInt("guide_vol", v).apply() }

    // ---- Historial de práctica ----
    fun addSession(beats: Int, bpm: Int, seconds: Int) {
        val line = "${System.currentTimeMillis()}|$beats|$bpm|$seconds"
        val all = ArrayList<String>()
        all.add(line)
        all.addAll(sessions())
        val trimmed = if (all.size > 50) all.subList(0, 50) else all
        sp.edit().putString("sessions", trimmed.joinToString("\n")).apply()
    }

    fun sessions(): List<String> {
        val raw = sp.getString("sessions", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\n")
    }

    fun clearSessions() {
        sp.edit().remove("sessions").apply()
    }
}
