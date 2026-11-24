package com.cumbiapaso1.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder

/** Genera un WAV filtrado de la canción completa (mismo largo, mismo tiempo). */
object AudioFilter {

    fun render(
        ctx: Context,
        uri: Uri,
        mode: Int,
        out: File,
        cancel: () -> Boolean,
        progress: (Int) -> Unit
    ): Boolean {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        var raf: RandomAccessFile? = null
        var ok = false
        try {
            ex.setDataSource(ctx, uri, null)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (m.startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return false
            ex.selectTrack(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return false
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = maxOf(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val durationUs =
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            val r = RandomAccessFile(out, "rw")
            raf = r
            r.setLength(0)
            r.write(ByteArray(44))
            var dataLen = 0L

            var chains = Array(channels) { SoundModes.chain(mode, rate.toDouble()) }
            val gain = SoundModes.gain(mode)
            var cc = 0

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastPct = -1

            while (!outputDone) {
                if (cancel()) return false
                if (!inputDone) {
                    val ii = c.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val ib = c.getInputBuffer(ii)
                        val n = if (ib != null) ex.readSampleData(ib, 0) else -1
                        if (n < 0) {
                            c.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val st = ex.sampleTime
                            c.queueInputBuffer(ii, 0, n, st, 0)
                            if (durationUs > 0) {
                                val pct = (st * 100L / durationUs).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    progress(pct)
                                }
                            }
                            ex.advance()
                        }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10000)
                if (oi >= 0) {
                    val ob = c.getOutputBuffer(oi)
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val total = sb.remaining()
                        val bytes = ByteArray(total * 2)
                        for (idx in 0 until total) {
                            var x = sb.get(idx).toDouble()
                            val ch = chains[cc]
                            for (f in ch) x = f.process(x)
                            x *= gain
                            if (x > 32767.0) x = 32767.0
                            if (x < -32768.0) x = -32768.0
                            val v = x.toInt()
                            bytes[2 * idx] = (v and 0xFF).toByte()
                            bytes[2 * idx + 1] = ((v shr 8) and 0xFF).toByte()
                            cc++
                            if (cc >= channels) cc = 0
                        }
                        r.write(bytes, 0, bytes.size)
                        dataLen += bytes.size
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = maxOf(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    }
                    chains = Array(channels) { SoundModes.chain(mode, rate.toDouble()) }
                    cc = 0
                }
            }

            // Cabecera WAV definitiva
            val h = java.nio.ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray())
            h.putInt((36L + dataLen).toInt())
            h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray())
            h.putInt(16)
            h.putShort(1)
            h.putShort(channels.toShort())
            h.putInt(rate)
            h.putInt(rate * channels * 2)
            h.putShort((channels * 2).toShort())
            h.putShort(16)
            h.put("data".toByteArray())
            h.putInt(dataLen.toInt())
            r.seek(0)
            r.write(h.array())
            ok = dataLen > 0L
            return ok
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // ignorar
            }
            try {
                codec?.release()
            } catch (e: Exception) {
                // ignorar
            }
            ex.release()
            try {
                raf?.close()
            } catch (e: Exception) {
                // ignorar
            }
            if (!ok) out.delete()
        }
    }
}
