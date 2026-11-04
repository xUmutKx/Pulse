package com.umutk.pulse

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Pulse's own file picker, in place of Android's document picker. Every place that opened the system picker (open a file, several files, a photo,
 * save a file) goes through these contracts instead: they start [PulsePickActivity], which browses the phone's storage in Pulse's look, and hand
 * back a content address of the chosen file. When the app has no all-files access the system picker is used as before.
 */
object PulsePick {
    const val FLAG = "pulse_pick"

    private fun usable(c: Context) = android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    private fun intent(c: Context, mode: String, mimes: Array<String>, name: String = ""): Intent =
        Intent(c, PulsePickActivity::class.java).putExtra(FLAG, true).putExtra("mode", mode).putExtra("mimes", mimes).putExtra("name", name)

    /** The address a picked file is handed on with: a FileProvider address (names and sizes can be asked for, and it reads and writes like any document). */
    fun uriOf(c: Context, f: File): Uri = runCatching { FileProvider.getUriForFile(c, c.packageName + ".files", f) }.getOrElse { Uri.fromFile(f) }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        private val sys = ActivityResultContracts.OpenDocument()
        override fun createIntent(context: Context, input: Array<String>): Intent = if (usable(context)) intent(context, "open", input) else sys.createIntent(context, input)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (intent?.getBooleanExtra(FLAG, false) == true) (if (resultCode == Activity.RESULT_OK) intent.data else null) else sys.parseResult(resultCode, intent)
    }

    class GetContent : ActivityResultContract<String, Uri?>() {
        private val sys = ActivityResultContracts.GetContent()
        override fun createIntent(context: Context, input: String): Intent = if (usable(context)) intent(context, "open", arrayOf(input)) else sys.createIntent(context, input)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (intent?.getBooleanExtra(FLAG, false) == true) (if (resultCode == Activity.RESULT_OK) intent.data else null) else sys.parseResult(resultCode, intent)
    }

    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        private val sys = ActivityResultContracts.OpenMultipleDocuments()
        override fun createIntent(context: Context, input: Array<String>): Intent = if (usable(context)) intent(context, "multi", input) else sys.createIntent(context, input)
        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
            if (intent?.getBooleanExtra(FLAG, false) != true) return sys.parseResult(resultCode, intent)
            if (resultCode != Activity.RESULT_OK) return emptyList()
            val clip = intent.clipData ?: return listOfNotNull(intent.data)
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        }
    }

    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        private val sys = ActivityResultContracts.PickVisualMedia()
        override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
            if (!usable(context)) return sys.createIntent(context, input)
            val mimes = when (val t = input.mediaType) {
                is ActivityResultContracts.PickVisualMedia.ImageOnly -> arrayOf("image/*")
                is ActivityResultContracts.PickVisualMedia.VideoOnly -> arrayOf("video/*")
                is ActivityResultContracts.PickVisualMedia.SingleMimeType -> arrayOf(t.mimeType)
                else -> arrayOf("image/*", "video/*")
            }
            return intent(context, "open", mimes)
        }
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (intent?.getBooleanExtra(FLAG, false) == true) (if (resultCode == Activity.RESULT_OK) intent.data else null) else sys.parseResult(resultCode, intent)
    }

    class CreateDocument(private val mime: String) : ActivityResultContract<String, Uri?>() {
        private val sys = ActivityResultContracts.CreateDocument(mime)
        override fun createIntent(context: Context, input: String): Intent = if (usable(context)) intent(context, "save", arrayOf(mime), input) else sys.createIntent(context, input)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (intent?.getBooleanExtra(FLAG, false) == true) (if (resultCode == Activity.RESULT_OK) intent.data else null) else sys.parseResult(resultCode, intent)
    }

    /** A folder of the phone: the path comes back as text (no system picker has a folder path to fall back to, so this one is always Pulse's own). */
    class PickFolder : ActivityResultContract<Unit, String?>() {
        override fun createIntent(context: Context, input: Unit): Intent = intent(context, "folder", emptyArray())
        override fun parseResult(resultCode: Int, intent: Intent?): String? = if (resultCode == Activity.RESULT_OK) intent?.getStringExtra("path") else null
    }

    /** True when [name] fits the wanted types ("image/*", "audio/mpeg", "*/*"); an unknown type is let through so a file is never hidden by mistake. */
    fun fits(name: String, mimes: List<String>): Boolean {
        if (mimes.isEmpty() || mimes.any { it == "*/*" || it.isBlank() }) return true
        val m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: return true
        return mimes.any { w -> w == m || (w.endsWith("/*") && m.startsWith(w.removeSuffix("*"))) }
    }
}

/** The picker window itself: folders and the files that fit, in the app's look. One tap picks a file; several-file mode ticks them; save mode asks for the name. */
class PulsePickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val mode = intent.getStringExtra("mode") ?: "open"
        val mimes = intent.getStringArrayExtra("mimes")?.toList().orEmpty()
        val suggested = intent.getStringExtra("name").orEmpty()
        val look = ExtraToolLook.get(this)
        setContent {
            LookHost(lookId = look) { th ->
                Box(Modifier.fillMaxSize().background(th.bg).statusBarsPadding().navigationBarsPadding()) {
                    PickerBody(mode, mimes, suggested, onCancel = { setResult(RESULT_CANCELED, Intent().putExtra(PulsePick.FLAG, true)); finish() }) { files ->
                        if (mode == "folder") { setResult(RESULT_OK, Intent().putExtra(PulsePick.FLAG, true).putExtra("path", files.firstOrNull()?.path)); finish(); return@PickerBody }
                        val uris = files.map { PulsePick.uriOf(this@PulsePickActivity, it) }
                        val out = Intent().putExtra(PulsePick.FLAG, true).setData(uris.firstOrNull())
                        if (uris.size > 1) out.clipData = ClipData.newRawUri("files", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
                        out.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        setResult(RESULT_OK, out); finish()
                    }
                }
            }
        }
    }

    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
}

@Composable
private fun PickerBody(mode: String, mimes: List<String>, suggested: String, onCancel: () -> Unit, onPicked: (List<File>) -> Unit) {
    val top = remember { Environment.getExternalStorageDirectory() }
    var cur by remember { mutableStateOf(top) }
    var name by remember { mutableStateOf(suggested) }
    val ticked = remember { mutableStateListOf<File>() }
    val items = remember(cur) {
        val all = cur.listFiles().orEmpty().filter { !it.name.startsWith(".") }
        all.filter { it.isDirectory }.sortedBy { it.name.lowercase() } + (if (mode == "folder") emptyList() else all.filter { it.isFile && PulsePick.fits(it.name, mimes) }.sortedBy { it.name.lowercase() })
    }
    val up: () -> Unit = { if (cur != top) cur = cur.parentFile ?: top else onCancel() }
    BackHandler { up() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { up() }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 22.dp, Modifier.rotate(180f)) }
            Text(cur.path.removePrefix(top.path).ifEmpty { "/" }, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clickable { onCancel() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 22.dp) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(items, key = { it.path }) { f ->
                val on = f in ticked
                Row(
                    Modifier.fillMaxWidth().background(if (on) T.accent.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable {
                            when {
                                f.isDirectory -> cur = f
                                mode == "multi" -> if (on) ticked.remove(f) else ticked.add(f)
                                mode == "save" -> name = f.name
                                else -> onPicked(listOf(f))
                            }
                        }.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PIcon(if (f.isDirectory) "folder" else "details", if (f.isDirectory) T.accent else T.sub, 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(f.name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (mode == "multi" && f.isFile) PCheck(on, { if (on) ticked.remove(f) else ticked.add(f) })
                }
            }
        }
        if (mode == "folder") Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            PIcon("folder", T.sub, 20.dp)
            Text(cur.name.ifEmpty { "/" }, color = T.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            Box(Modifier.size(48.dp).clickable { onPicked(listOf(cur)) }, contentAlignment = Alignment.Center) { PIcon("check", T.accent, 26.dp) }
        }
        if (mode == "multi") Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("${ticked.size}", color = T.sub, fontSize = 15.sp, modifier = Modifier.padding(end = 12.dp))
            Box(Modifier.size(48.dp).clickable(enabled = ticked.isNotEmpty()) { onPicked(ticked.toList()) }, contentAlignment = Alignment.Center) { PIcon("check", if (ticked.isNotEmpty()) T.accent else T.sub, 26.dp) }
        }
        if (mode == "save") Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PNameField(name, { name = it }, "", Modifier.weight(1f))
            Box(Modifier.size(48.dp).clickable(enabled = name.isNotBlank()) {
                val f = File(cur, name.trim())
                runCatching { if (!f.exists()) f.createNewFile() }
                onPicked(listOf(f))
            }, contentAlignment = Alignment.Center) { PIcon("check", if (name.isNotBlank()) T.accent else T.sub, 26.dp) }
        }
    }
}
