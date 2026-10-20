package com.umutk.pulse.extras.callbg

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.Shell
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The call background a Samsung contact has (set in the Phone app), read through root.
 * shortcut: the contacts database schema is not checked on a device here; the column is found by its name (contains "call_bg"),
 * and sqlite3 must exist on the phone. Without root or a match it gives null.
 */
object SamsungCallBg {
    private const val CONTACTS_DB = "/data/data/com.android.providers.contacts/databases/contacts2.db"
    // the only places a call background file is copied from: the dialer's own folders
    private val SOURCES = listOf("/data/user_de/0/com.samsung.android.incallui/", "/data/user/0/com.samsung.android.incallui/", "/data/data/com.samsung.android.incallui/")
    private val MEDIA = setOf("mp4", "jpg", "jpeg", "png")

    /** Contact id of a contact URI (content://com.android.contacts/contacts/.../<id>), or null. */
    fun contactIdOf(uri: Uri?): Long? = uri?.lastPathSegment?.toLongOrNull()

    /** Copies the background of [contactId] into the app cache and gives the copy; null when there is none or no root. */
    suspend fun copyFor(ctx: Context, contactId: Long): File? = withContext(Dispatchers.IO) {
        if (Shell.root != true) return@withContext null
        // the column is looked up first, then its value for this contact; both come from fixed text except the numeric id
        val cols = Shell.runRoot("sqlite3 -readonly '$CONTACTS_DB' \"SELECT name FROM pragma_table_info('raw_contacts')\"")
        val col = cols.lines().map { it.trim() }.firstOrNull { it.contains("call_bg") && it.matches(Regex("[A-Za-z0-9_]+")) } ?: return@withContext null
        val path = Shell.runRoot("sqlite3 -readonly '$CONTACTS_DB' \"SELECT $col FROM raw_contacts WHERE contact_id = $contactId AND $col IS NOT NULL AND $col != '' LIMIT 1\"")
            .lines().firstOrNull()?.trim().orEmpty()
        // the value comes from the phone's database: only a file under the dialer's folders with a picture or video extension is used
        // no quotes or dot-dots either: the path goes into a root shell command
        val inside = SOURCES.any { path.startsWith(it) } && !path.contains("..") && !path.contains('\'') && !path.contains('"')
        val ext = path.substringAfterLast('.', "").lowercase()
        if (!inside || ext !in MEDIA) return@withContext null
        val dir = File(ctx.cacheDir, "callbg").apply { mkdirs() }
        val dst = File(dir, "$contactId.$ext")
        val ok = Shell.runRoot("cp '$path' '${dst.path}' && chmod 644 '${dst.path}' && echo __OK").contains("__OK")
        if (ok) dst else null
    }
}

/** Shows a copied call background: a video plays looped, a picture is drawn as it is. */
@Composable
fun CallBgPreview(file: File?) {
    if (file == null) return
    if (file.extension.equals("mp4", true)) {
        AndroidView(
            factory = { c -> VideoView(c).apply { setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } },
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
    } else {
        val bmp by produceState<android.graphics.Bitmap?>(null, file) {
            value = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(file.path) }
        }
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(260.dp)) }
    }
}
