package com.umutk.pulse.extras.ytx

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * Writes the iTunes tags of an .m4a song: title, artist, album and the cover picture (moov/udta/meta/ilst). The music players of the phone read
 * these, so a song saved from YouTube shows with its name, singer and cover like any other song.
 * The moov box is rebuilt with the tags; when it lies before the audio data, the chunk offsets (stco/co64) are moved by the size it grew.
 * A fragmented file (YouTube's DASH audio) keeps working: its fragments count from their own boxes.
 */
object Mp4Tags {
    private class Box(val type: String, val start: Long, val size: Long, val header: Int)

    private fun readBoxes(f: RandomAccessFile, from: Long, to: Long): List<Box> {
        val out = ArrayList<Box>()
        var p = from
        while (p + 8 <= to) {
            f.seek(p)
            var size = f.readInt().toLong() and 0xFFFFFFFFL
            val t = ByteArray(4).also { f.readFully(it) }
            var header = 8
            if (size == 1L) { size = f.readLong(); header = 16 } else if (size == 0L) size = to - p
            if (size < header) break
            out += Box(String(t, Charsets.ISO_8859_1), p, size, header)
            p += size
        }
        return out
    }

    private fun box(type: String, body: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(body.size + 8); val d = DataOutputStream(o)
        d.writeInt(body.size + 8); d.write(type.toByteArray(Charsets.ISO_8859_1)); d.write(body); return o.toByteArray()
    }

    private fun data(kind: Int, payload: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); val d = DataOutputStream(o)
        d.writeInt(kind); d.writeInt(0); d.write(payload)
        return box("data", o.toByteArray())
    }

    private fun text(type: String, v: String) = box(type, data(1, v.toByteArray(Charsets.UTF_8)))

    private fun udta(title: String, artist: String, album: String, cover: ByteArray?): ByteArray {
        val ilst = ByteArrayOutputStream()
        if (title.isNotBlank()) ilst.write(text("©nam", title))
        if (artist.isNotBlank()) { ilst.write(text("©ART", artist)); ilst.write(text("aART", artist)) }
        if (album.isNotBlank()) ilst.write(text("©alb", album))
        if (cover != null && cover.isNotEmpty()) ilst.write(box("covr", data(13, cover)))
        val hdlr = ByteArrayOutputStream().also { o ->
            val d = DataOutputStream(o)
            d.writeInt(0); d.writeInt(0); d.write("mdir".toByteArray()); d.write("appl".toByteArray()); d.writeInt(0); d.writeInt(0); d.writeByte(0)
        }.toByteArray()
        val meta = ByteArrayOutputStream().also { o -> DataOutputStream(o).writeInt(0); o.write(box("hdlr", hdlr)); o.write(box("ilst", ilst.toByteArray())) }.toByteArray()
        return box("udta", box("meta", meta))
    }

    /** Moves every chunk offset (stco, co64) inside [buf] (a copy of moov) by [delta]. */
    private fun shiftOffsets(buf: ByteArray, from: Int, to: Int, delta: Long) {
        var p = from
        while (p + 8 <= to) {
            val size = ((buf[p].toInt() and 0xFF) shl 24 or (buf[p + 1].toInt() and 0xFF shl 16) or (buf[p + 2].toInt() and 0xFF shl 8) or (buf[p + 3].toInt() and 0xFF)).toLong() and 0xFFFFFFFFL
            val type = String(buf, p + 4, 4, Charsets.ISO_8859_1)
            if (size < 8 || p + size > to) return
            val end = (p + size).toInt()
            when (type) {
                "trak", "mdia", "minf", "stbl", "edts" -> shiftOffsets(buf, p + 8, end, delta)
                "stco" -> {
                    val n = java.nio.ByteBuffer.wrap(buf, p + 12, 4).int
                    for (i in 0 until n) {
                        val at = p + 16 + i * 4
                        val bb = java.nio.ByteBuffer.wrap(buf, at, 4)
                        val v = (bb.int.toLong() and 0xFFFFFFFFL) + delta
                        java.nio.ByteBuffer.wrap(buf, at, 4).putInt(v.toInt())
                    }
                }
                "co64" -> {
                    val n = java.nio.ByteBuffer.wrap(buf, p + 12, 4).int
                    for (i in 0 until n) {
                        val at = p + 16 + i * 8
                        val v = java.nio.ByteBuffer.wrap(buf, at, 8).long + delta
                        java.nio.ByteBuffer.wrap(buf, at, 8).putLong(v)
                    }
                }
            }
            p = end
        }
    }

    /** Writes the tags into [file]; false (and the file untouched) when it is not an MP4 it can read. */
    fun write(file: File, title: String, artist: String, album: String, cover: ByteArray?): Boolean = runCatching {
        val tmp = File(file.parentFile, ".${file.name}.tag")
        RandomAccessFile(file, "r").use { f ->
            val len = f.length()
            val top = readBoxes(f, 0, len)
            val moov = top.firstOrNull { it.type == "moov" } ?: return false
            if (moov.size > 64L * 1024 * 1024 || moov.header != 8) return false
            // the moov without its old udta, then the new udta at its end
            val kids = readBoxes(f, moov.start + 8, moov.start + moov.size).filter { it.type != "udta" }
            val body = ByteArrayOutputStream()
            for (k in kids) { val b = ByteArray(k.size.toInt()); f.seek(k.start); f.readFully(b); body.write(b) }
            body.write(udta(title, artist, album, cover))
            val newMoov = box("moov", body.toByteArray())
            val delta = newMoov.size.toLong() - moov.size
            // the media data lies after moov: its chunk offsets move by delta
            val dataAfter = top.any { it.type == "mdat" && it.start > moov.start }
            if (dataAfter && delta != 0L && top.none { it.type == "moof" }) shiftOffsets(newMoov, 8, newMoov.size, delta)
            tmp.outputStream().buffered(1 shl 16).use { o ->
                val buf = ByteArray(1 shl 16)
                fun copy(from: Long, to: Long) { var p = from; f.seek(p); while (p < to) { val n = f.read(buf, 0, minOf(buf.size.toLong(), to - p).toInt()); if (n <= 0) break; o.write(buf, 0, n); p += n } }
                copy(0, moov.start)
                o.write(newMoov)
                copy(moov.start + moov.size, len)
            }
        }
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        true
    }.getOrDefault(false)

    /** The cover of a video as a square JPEG: the middle of its thumbnail (music videos have the cover in the middle of a 16:9 frame). */
    fun squareCover(videoId: String): ByteArray? {
        for (name in listOf("maxresdefault", "hq720", "hqdefault")) {
            val bytes = runCatching {
                val c = java.net.URL("https://i.ytimg.com/vi/$videoId/$name.jpg").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 10000
                if (c.responseCode != 200) null else c.inputStream.use { it.readBytes() }
            }.getOrNull() ?: continue
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            // hqdefault has black bars above and below; the square in the middle has none
            val side = minOf(bmp.width, bmp.height)
            val sq = android.graphics.Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val o = ByteArrayOutputStream()
            sq.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, o)
            return o.toByteArray()
        }
        return null
    }
}
