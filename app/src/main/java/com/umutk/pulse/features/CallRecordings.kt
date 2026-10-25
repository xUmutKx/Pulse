package com.umutk.pulse.features

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The folder Recordings/Call (the phone's call recordings): it gets a page of its own instead of the plain list. */
internal fun isCallRecordings(dir: File) = dir.name.equals("Call", true) && dir.parentFile?.name.equals("Recordings", true)

/** "Call recording Mom_221105_203000.m4a" is a call with "Mom": the words before the date stamp, without the prefix. */
private fun callName(f: File): String {
    var n = f.nameWithoutExtension.trim()
    n = n.replace(Regex("^(call recording|call|çağrı kaydı)[ _-]+", RegexOption.IGNORE_CASE), "")
    n = n.replace(Regex("[_ -]\\d{6,8}([_ -]\\d{4,6})?$"), "")
    return n.replace('_', ' ').trim().ifEmpty { f.nameWithoutExtension }
}

/** The photo of the contact called [name] (null when there is no such contact or no photo). */
private fun contactPhoto(ctx: Context, name: String): ImageBitmap? = runCatching {
    ctx.contentResolver.query(Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(name)), arrayOf(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI), null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use null
        val u = c.getString(0) ?: return@use null
        ctx.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }
}.getOrNull()

/**
 * The call recordings page: the nine people called most, three by three (a tap shows only their calls), and below the calls grouped by day, each day
 * closing and opening. A tap plays the recording in the player of Files.
 */
@Composable
internal fun CallPane(files: List<File>, onPlay: (File) -> Unit) {
    val ctx = LocalContext.current
    val audio = remember(files) { files.filter { it.isFile && it.extension.lowercase() in EX_AUDIO }.sortedByDescending { it.lastModified() } }
    val names = remember(audio) { audio.associateWith { callName(it) } }
    val top = remember(audio) { names.values.groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }.take(9).map { e -> names.values.first { it.lowercase() == e.key } to e.value } }
    var pick by remember { mutableStateOf<String?>(null) }
    var closed by remember { mutableStateOf(setOf<String>()) }
    val shown = if (pick == null) audio else audio.filter { names[it]?.lowercase() == pick }
    val day = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()) }
    val key = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val hm = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth()) {
        if (top.isEmpty()) Text(tr("No call recordings here", "Burada çağrı kaydı yok"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
        top.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (n, count) ->
                    val on = pick == n.lowercase()
                    val photo by produceState<ImageBitmap?>(null, n) { value = withContext(Dispatchers.IO) { contactPhoto(ctx, n) } }
                    Column(Modifier.width(96.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Look.accent.copy(alpha = .2f) else androidx.compose.ui.graphics.Color.Transparent).clickable { pick = if (on) null else n.lowercase() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(Look.accent.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                            val p = photo
                            if (p != null) androidx.compose.foundation.Image(p, null, Modifier.size(56.dp), contentScale = ContentScale.Crop)
                            else Text(n.firstOrNull()?.uppercase() ?: "?", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(n, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text("$count", color = T.sub, fontSize = 11.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.width(96.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        shown.groupBy { key.format(Date(it.lastModified())) }.forEach { (k, list) ->
            val shut = k in closed
            Row(Modifier.fillMaxWidth().clickable { closed = if (shut) closed - k else closed + k }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(day.format(Date(list.first().lastModified())), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${list.size}", color = T.sub, fontSize = 13.sp)
                Text(if (shut) "▸" else "▾", color = T.sub, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
            }
            if (!shut) list.forEach { f ->
                val now = ExAudio.file == f
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (now) Look.accent.copy(alpha = .15f) else androidx.compose.ui.graphics.Color.Transparent).clickable { onPlay(f) }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Rounded.PlayArrow, null, tint = Look.accent, modifier = Modifier.size(26.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(names[f].orEmpty(), color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hm.format(Date(f.lastModified())) + "  ·  " + exSize(f.length()), color = T.sub, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }
}
