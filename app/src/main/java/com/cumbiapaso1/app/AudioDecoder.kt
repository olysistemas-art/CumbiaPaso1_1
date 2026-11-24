package com.cumbiapaso1.app

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

class Decoded(val samples: FloatArray, val rate: Int)

/** Decodifica un archivo de audio a mono y lo reduce a ~11 kHz (suficiente para graves). */
object AudioDecoder {

    fun decode(
        ctx: Context,
        uri: Uri,
        targetRate: Int,
        cancel: () -> Boolean,
        progress: (Int) -> Unit
    ): Decoded? {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
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
            if (track < 0 || fmt == null) return null
            ex.selectTrack(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: return null
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = maxOf(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            val durationUs =
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            var factor = maxOf(1, rate / targetRate)
            val finalRate = rate / factor

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(fmt, null, null, 0)
            c.start()

            var buf = FloatArray(1 shl 20)
            var len = 0
            var acc = 0f
            var cnt = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastPct = -1

            while (!outputDone) {
                if (cancel()) return null
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
                        var idx = 0
                        while (idx + channels <= total) {
                            var s = 0f
                            for (ch in 0 until channels) s += sb.get(idx + ch)
                            idx += channels
                            acc += s / channels
                            cnt++
                            if (cnt >= factor) {
                                if (len == buf.size) buf = buf.copyOf(buf.size * 2)
                                buf[len++] = acc / cnt / 32768f
                                acc = 0f
                                cnt = 0
                            }
                        }
                    }
                    c.releaseOutputBuffer(oi, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val nf = c.outputFormat
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = maxOf(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                    }
                    factor = maxOf(1, rate / targetRate)
                }
            }
            if (len < 4096) return null
            return Decoded(buf.copyOf(len), finalRate)
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
        }
    }
}
