package com.umutk.pulse.watch.link

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Where the watch keeps what it got from the phone (songs, videos, files). */
fun watchMediaDir(c: Context): File = File(c.getExternalFilesDir(null), "Phone").apply { mkdirs() }

/** Copies a file of the phone to the watch in pieces; it resumes where an earlier copy stopped. [progress] gets 0..1. */
suspend fun copyFromPhone(c: Context, phonePath: String, size: Long, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
    val out = File(watchMediaDir(c), phonePath.substringAfterLast('/'))
    val ok = runCatching {
        RandomAccessFile(out, "rw").use { w ->
            var off = if (out.length() in 1 until size) out.length() else 0L
            w.setLength(off)
            while (off < size) {
                val r = PhoneLink.tryAsk(c, "get", JSONObject().put("p", phonePath).put("off", off)) ?: return@runCatching false
                val n = r.optInt("n"); if (n <= 0) return@runCatching false
                w.seek(off); w.write(Base64.decode(r.optString("data"), Base64.DEFAULT)); off += n
                progress(off.toFloat() / size)
            }
        }
        true
    }.getOrDefault(false)
    if (ok) out else null
}
