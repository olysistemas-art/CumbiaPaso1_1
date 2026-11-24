package com.cumbiapaso1.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sonidos de los instrumentos. Si existe res/raw/guira, bajo, conga o timbal
 * (wav, mp3 u ogg) se usa tu audio; si no, se genera uno sintético.
 */
class Samples(private val ctx: Context) {
    private val pool: SoundPool
    private val ids = IntArray(4)
    val custom = BooleanArray(4)

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        pool = SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs).build()
        for (i in 0 until 4) {
            val resId = ctx.resources.getIdentifier(Cumbia.RAW[i], "raw", ctx.packageName)
            if (resId != 0) {
                ids[i] = pool.load(ctx, resId, 1)
                custom[i] = true
            } else {
                val f = File(ctx.cacheDir, "synth_" + Cumbia.RAW[i] + ".wav")
                try {
                    Synth.write(f, i)
                    ids[i] = pool.load(f.absolutePath, 1)
                } catch (e: Exception) {
                    ids[i] = 0
                }
            }
        }
    }

    fun play(i: Int, volume: Float) {
        val id = ids[i]
        if (id != 0) {
            val v = volume.coerceIn(0f, 1f)
            pool.play(id, v, v, 1, 0, 1f)
        }
    }

    fun release() {
        pool.release()
    }
}

/** Sonidos de respaldo generados por código. */
object Synth {
    private const val RATE = 22050

    fun write(f: File, kind: Int) {
        val pcm = make(kind)
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + dataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(RATE)
        bb.putInt(RATE * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(dataLen)
        for (s in pcm) bb.putShort(s)
        f.writeBytes(bb.array())
    }

    private fun make(kind: Int): ShortArray {
        val rnd = java.util.Random(7L + kind)
        val ms = when (kind) {
            0 -> 110
            1 -> 320
            2 -> 240
            else -> 220
        }
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        var phase = 0.0
        var prevNoise = 0.0
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            var v: Double
            when (kind) {
                0 -> {
                    val nz = rnd.nextDouble() * 2.0 - 1.0
                    val hp = nz - prevNoise
                    prevNoise = nz
                    v = hp * exp(-t * 45.0) * 0.8
                }
                1 -> {
                    val f = 62.0 + 40.0 * exp(-t * 25.0)
                    phase += 2.0 * PI * f / RATE
                    v = (sin(phase) + 0.25 * sin(2.0 * phase)) * exp(-t * 9.0) * 0.9
                }
                2 -> {
                    val f = 150.0 + 130.0 * exp(-t * 28.0)
                    phase += 2.0 * PI * f / RATE
                    val click = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 220.0) * 0.4
                    v = sin(phase) * exp(-t * 16.0) * 0.85 + click
                }
                else -> {
                    val f = 190.0 + 90.0 * exp(-t * 30.0)
                    phase += 2.0 * PI * f / RATE
                    val nz = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 38.0) * 0.6
                    v = sin(phase) * exp(-t * 20.0) * 0.6 + nz
                }
            }
            val c = if (v > 1.0) 1.0 else if (v < -1.0) -1.0 else v
            out[i] = (c * 32000.0).toInt().toShort()
        }
        return out
    }
}
