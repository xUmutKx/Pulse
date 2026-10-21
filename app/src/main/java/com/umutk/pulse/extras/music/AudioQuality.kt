package com.umutk.pulse.extras.music

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** The sound quality of a track as one short line: format, sample rate, bit depth when the file says it, bitrate. Read from the file, off the main thread. */
object AudioQuality {
    private val cache = ConcurrentHashMap<String, String>()

    /** "FLAC · 44.1 kHz · 16-bit · 940 kbps"; empty when the file cannot be read (streams, missing files). */
    fun describe(ctx: Context, t: Track): String = cache[t.path] ?: read(ctx, t).also { cache[t.path] = it }

    private fun read(ctx: Context, t: Track): String = try {
        val ex = MediaExtractor()
        try {
            if (t.path.startsWith("http")) return ""
            if (t.path.contains("://")) ex.setDataSource(ctx, Uri.parse(t.path), null) else ex.setDataSource(t.path)
            val idx = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return ""
            val f = ex.getTrackFormat(idx)
            val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
            val name = when {
                mime.contains("mpeg") -> "MP3"
                mime.contains("flac") -> "FLAC"
                mime.contains("alac") -> "ALAC"
                mime.contains("mp4a") || mime.contains("aac") -> "AAC"
                mime.contains("opus") -> "Opus"
                mime.contains("vorbis") -> "Vorbis"
                mime.contains("raw") || mime.contains("wav") -> "WAV"
                else -> mime.substringAfter('/').uppercase()
            }
            val rate = if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) f.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 0
            // PCM encoding constants of AudioFormat: 8 bit = 3, 16 bit = 2, float = 4, 24 bit packed = 21, 32 bit = 22
            val bits = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) { 3 -> 8; 2 -> 16; 21 -> 24; 22, 4 -> 32; else -> 0 } else 0
            var kbps = if (f.containsKey(MediaFormat.KEY_BIT_RATE)) f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000 else 0
            if (kbps <= 0 && !t.path.contains("://")) {
                // no bitrate in the header: file size over duration
                val sec = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 else t.durationSec.toDouble()
                if (sec > 0) kbps = (File(t.path).length() * 8 / sec / 1000).toInt()
            }
            listOfNotNull(
                name,
                rate.takeIf { it > 0 }?.let { "${"%.1f".format(it / 1000.0).removeSuffix(".0")} kHz" },
                bits.takeIf { it > 0 }?.let { "$it-bit" },
                kbps.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" · ")
        } finally {
            ex.release()
        }
    } catch (e: Exception) {
        ""
    }
}
