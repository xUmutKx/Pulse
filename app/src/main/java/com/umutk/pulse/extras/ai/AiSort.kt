package com.umutk.pulse.extras.ai

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PProgress
import com.umutk.pulse.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/*
 * Sorting the pictures of the phone with a vision model, by what is in them: people, food, animals ... Every picture gets one label, once; the
 * labels are kept, so the next run only does the new pictures. Each picture is asked on a fresh conversation (the model stays loaded), so the
 * chat's memory never fills up. The albums are the labels: tap one to see its pictures; tap a picture to open it.
 */
object PhotoSort {
    val CATEGORIES = listOf("people", "food", "animals", "nature", "city", "car", "document", "screenshot", "art", "other")
    private const val SYSTEM = "You label pictures. Reply with exactly one word from this list: people, food, animals, nature, city, car, document, screenshot, art, other."
    private const val QUESTION = "What is the main thing in this picture? One word from the list."
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** Running now: how many pictures are done and how many there are. */
    @Volatile var done = 0; private set
    @Volatile var total = 0; private set
    @Volatile var running = false; private set
    @Volatile var lastError: String? = null; private set

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("photo_sort", Context.MODE_PRIVATE)

    /** The label of each picture (its content URI as text). */
    fun labels(c: Context): Map<String, String> = runCatching {
        val o = JSONObject(sp(c).getString("labels", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    private fun save(c: Context, map: Map<String, String>) { sp(c).edit().putString("labels", JSONObject(map).toString()).apply() }

    /** The newest pictures on the phone, up to [limit]. */
    fun pictures(c: Context, limit: Int): List<Uri> {
        val out = mutableListOf<Uri>()
        runCatching {
            c.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID), null, null,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC")?.use { cur ->
                val idx = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cur.moveToNext() && out.size < limit) out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cur.getLong(idx))
            }
        }
        return out
    }

    /** Starts the sorting in the background: the chosen chat model must be a vision model. Returns false when it cannot start. */
    fun start(c: Context, limit: Int = 300): Boolean {
        if (running) return true
        val app = c.applicationContext
        val model = AiEngine.chosenModel(app)
        if (model?.projector == null) { lastError = "vision"; return false }
        running = true; lastError = null; done = 0; total = 0
        job = scope.launch {
            try {
                val map = labels(app).toMutableMap()
                val todo = pictures(app, limit).filter { it.toString() !in map }
                total = todo.size
                if (todo.isNotEmpty()) {
                    val ok = AiEngine.start(app, SYSTEM)
                    if (!ok || !AiEngine.vision) throw IllegalStateException("model")
                    for ((i, u) in todo.withIndex()) {
                        currentCoroutineContext().ensureActive()
                        val bytes = pictureFrom(app, u)?.second
                        if (bytes != null) {
                            AiEngine.engine(app).restartConversation(SYSTEM)
                            val sb = StringBuilder()
                            AiEngine.askPicture(app, QUESTION, bytes, 6).collect { sb.append(it) }
                            map[u.toString()] = parseLabel(sb.toString())
                        } else map[u.toString()] = "other"
                        done = i + 1
                        if (done % 10 == 0) save(app, map)
                    }
                    save(app, map)
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            } finally {
                running = false
            }
        }
        return true
    }

    fun stop() { job?.cancel(); running = false }

    private fun parseLabel(answer: String): String {
        val words = answer.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return words.firstOrNull { it in CATEGORIES } ?: "other"
    }
}

/** A picture for the vision model: its bytes as a JPEG no larger than 768 pixels (the model shrinks it anyway, so this is quicker). */
internal fun pictureFrom(c: Context, u: Uri): Pair<String, ByteArray>? = runCatching {
    val name = c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "picture"
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    c.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) sample *= 2
    val bmp = c.contentResolver.openInputStream(u)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
    val out = java.io.ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
    bmp.recycle()
    name to out.toByteArray()
}.getOrNull()

/** The name of an album in the language of the phone. */
private fun albumName(k: String): String = when (k) {
    "people" -> tr("People", "İnsanlar"); "food" -> tr("Food", "Yemek"); "animals" -> tr("Animals", "Hayvanlar"); "nature" -> tr("Nature", "Doğa")
    "city" -> tr("City", "Şehir"); "car" -> tr("Cars", "Araba"); "document" -> tr("Documents", "Belgeler"); "screenshot" -> tr("Screenshots", "Ekran görüntüleri")
    "art" -> tr("Art", "Sanat"); else -> tr("Other", "Diğer")
}

/** The sorting: start or stop it, its progress, the albums with their counts; an album shows its pictures, a tap opens one. */
@Composable
internal fun PhotoSortDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(700); tick++ } }
    var album by remember { mutableStateOf<String?>(null) }
    var refused by remember { mutableStateOf(false) }
    val labels = remember(tick) { PhotoSort.labels(c) }
    val counts = remember(tick, labels) { labels.values.groupingBy { it }.eachCount() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF161616)).padding(16.dp)) {
            val open = album
            if (open != null) {
                Row(Modifier.fillMaxWidth().clickable { album = null }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("back", Color(0xFFEDEDED), 20.dp)
                    Text(albumName(open) + "  ·  " + (counts[open] ?: 0), color = Color(0xFFEDEDED), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                }
                val pics = labels.filterValues { it == open }.keys.map { Uri.parse(it) }
                LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(pics) { u -> Thumb(c, u) }
                }
            } else {
                Text(tr("Sort the pictures", "Fotoğrafları sırala"), color = Color(0xFFEDEDED), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("A vision model looks at each picture and puts it into an album. Stays on the phone.",
                    "Görsel bir model her fotoğrafa bakıp bir albüme koyar. Her şey telefonda kalır."), color = Color(0xFF9A9A9A), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                val running = PhotoSort.running
                if (refused || PhotoSort.lastError == "vision") Text(tr("Pick a vision model first: Models, then Gemma 3 4B vision or SmolVLM 500M vision.",
                    "Önce görsel bir model seç: Modeller'den Gemma 3 4B vision ya da SmolVLM 500M vision."), color = Color(0xFFEDEDED), fontSize = 13.sp)
                else PhotoSort.lastError?.let { Text(tr("Stopped: ", "Durdu: ") + it, color = Color(0xFF9A9A9A), fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (running) {
                        PProgress(if (PhotoSort.total > 0) PhotoSort.done.toFloat() / PhotoSort.total else 0f, Modifier.weight(1f))
                        Text("  ${PhotoSort.done} / ${PhotoSort.total}", color = Color(0xFF9A9A9A), fontSize = 12.sp)
                        Box(Modifier.padding(start = 10.dp).size(40.dp).clickable { PhotoSort.stop() }, contentAlignment = Alignment.Center) { PIcon("stop", Color(0xFFEDEDED), 18.dp) }
                    } else PButton(tr("Sort the newest 300", "En yeni 300'ü sırala"), Modifier.fillMaxWidth()) {
                        refused = !PhotoSort.start(c, 300)
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(PhotoSort.CATEGORIES.filter { (counts[it] ?: 0) > 0 }) { k ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { album = k }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            PIcon("photos", Color(0xFFEDEDED), 20.dp)
                            Text(albumName(k), color = Color(0xFFEDEDED), fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            Text("${counts[k]}", color = Color(0xFF9A9A9A), fontSize = 13.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                PButton(tr("Close", "Kapat"), compact = true) { onClose() }
            }
        }
    }
}

/** One picture of an album: a small preview (Android 10 and up), a tap opens it in the phone's viewer. */
@Composable
private fun Thumb(c: Context, u: Uri) {
    val bmp by produceState<Bitmap?>(null, u) {
        value = withContext(Dispatchers.IO) { runCatching { if (android.os.Build.VERSION.SDK_INT >= 29) c.contentResolver.loadThumbnail(u, android.util.Size(256, 256), null) else null }.getOrNull() }
    }
    Box(Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).clickable {
        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
