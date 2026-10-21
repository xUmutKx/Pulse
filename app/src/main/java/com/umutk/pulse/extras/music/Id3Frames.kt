package com.umutk.pulse.extras.music

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Puts one frame (a cover picture) into an MP3's ID3v2 tag, replacing a frame with the same id and keeping every other one. The song is rewritten
 * through a temp copy and swapped in only when the copy is complete. Only plain ID3v2.3 / 2.4 tags are touched (no unsynchronisation, no extended header);
 * anything else returns false and leaves the file alone.
 */
object Id3Frames {
    fun putFrame(path: String, id: String, body: ByteArray): Boolean = runCatching {
        val f = File(path)
        if (!f.extension.equals("mp3", true) || !f.canWrite()) return@runCatching false
        val head = ByteArray(10)
        val old: ByteArray; val oldLen: Int; val ver: Int
        f.inputStream().use { i ->
            val n = i.read(head)
            if (n == 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                ver = head[3].toInt()
                if ((ver != 3 && ver != 4) || (head[5].toInt() and 0xC0) != 0) return@runCatching false
                oldLen = (head[6].toInt() shl 21) or (head[7].toInt() shl 14) or (head[8].toInt() shl 7) or head[9].toInt()
                old = ByteArray(oldLen); var got = 0
                while (got < oldLen) { val k = i.read(old, got, oldLen - got); if (k < 0) return@runCatching false; got += k }
            } else { ver = 3; old = ByteArray(0); oldLen = -1 }
        }
        fun size4(v: Int, sync: Boolean) = if (sync) byteArrayOf((v shr 21 and 127).toByte(), (v shr 14 and 127).toByte(), (v shr 7 and 127).toByte(), (v and 127).toByte())
            else byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        val out = ByteArrayOutputStream()
        var p = 0
        while (p + 10 <= old.size && old[p].toInt() != 0) {
            val fid = String(old, p, 4, Charsets.ISO_8859_1)
            val sz = if (ver == 4) (old[p + 4].toInt() shl 21) or (old[p + 5].toInt() shl 14) or (old[p + 6].toInt() shl 7) or old[p + 7].toInt()
                else ((old[p + 4].toInt() and 255) shl 24) or ((old[p + 5].toInt() and 255) shl 16) or ((old[p + 6].toInt() and 255) shl 8) or (old[p + 7].toInt() and 255)
            if (sz < 0 || p + 10 + sz > old.size) return@runCatching false
            if (fid != id) out.write(old, p, 10 + sz)
            p += 10 + sz
        }
        out.write(id.toByteArray(Charsets.ISO_8859_1)); out.write(size4(body.size, ver == 4)); out.write(byteArrayOf(0, 0)); out.write(body)
        val frames = out.toByteArray()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.outputStream().buffered().use { o ->
            o.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), ver.toByte(), 0, 0)); o.write(size4(frames.size, true)); o.write(frames)
            f.inputStream().buffered().use { i -> if (oldLen >= 0) i.skip(10L + oldLen); i.copyTo(o) }
        }
        if (tmp.length() < f.length() - (if (oldLen >= 0) 10 + oldLen else 0)) { tmp.delete(); return@runCatching false }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        true
    }.getOrDefault(false)

    /** APIC body: UTF-8-free (ISO-8859-1) encoding byte, MIME type, picture type 3 (front cover), empty description, the picture. */
    fun coverBody(jpg: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(0); write("image/jpeg".toByteArray(Charsets.ISO_8859_1)); write(0); write(3); write(0); write(jpg)
    }.toByteArray()
}
