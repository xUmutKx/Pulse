package com.umutk.pulse.features

import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.umutk.pulse.Cfg
import com.umutk.pulse.LookHost
import com.umutk.pulse.Opt
import com.umutk.pulse.Shell
import java.io.File
import java.io.IOException

/** An APK opened from outside: the install window floats over the app underneath, Pulse's main screen does not open. */
class InstallActivity : ComponentActivity() {
    private var file by mutableStateOf<File?>(null)
    private var copied by mutableStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // a browser that says "octet-stream" for every download sends all of them here: only a real package installs, any other file goes to the app that opens its kind
        if (intent.getStringArrayListExtra("apks").isNullOrEmpty() && intent.data?.let { notPackage(it) } == true) { handOn(intent.data!!); finish(); return }
        // the play build does not install APK files (no REQUEST_INSTALL_PACKAGES there)
        if (!com.umutk.pulse.BuildConfig.APK_INSTALL) {
            android.widget.Toast.makeText(this, com.umutk.pulse.tr("This version cannot install apps", "Bu sürüm uygulama kuramaz"), android.widget.Toast.LENGTH_LONG).show()
            finish(); return
        }
        Cfg.load(this); Opt.load(this); Installer.load(this); InstallShow.load(this)
        // several APKs from Files: one floating window for all of them
        val many = intent.getStringArrayListExtra("apks")
        if (many != null && many.isNotEmpty()) {
            setContent { LookHost(transparent = true) { Box(Modifier.fillMaxSize()) { InstallMultiWindow(many, intent.getStringExtra("look")) { finish() } } } }
            return
        }
        val uri = intent.data
        if (uri == null) { finish(); return }
        setContent {
            LookHost(transparent = true) {
                Box(Modifier.fillMaxSize()) {
                    val f = file
                    if (f != null) InstallWindow(f, intent.getStringExtra("look")) { finish() } else ApkCopying(copied) { finish() }
                }
            }
        }
        // root is detected before the window reads it; the APK is copied off the main thread, as MainActivity did
        // the root check (it can wait on a root prompt for seconds) runs beside the copy, not before it; a root that is already known is not asked again
        val rootCheck = Thread { if (Shell.root == null) Shell.detectRoot() }.also { it.start() }
        Thread {
            val f = File(cacheDir, "open/open.apk").apply { parentFile?.mkdirs() }
            val total = size(uri)
            try {
                val input = contentResolver.openInputStream(uri) ?: throw IOException("no content")
                input.use { i -> f.outputStream().use { o ->
                    val buf = ByteArray(1024 * 1024)
                    var done = 0L; var shownAt = 0L
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        // the bar moves a few times a second, not once for every read
                        val now = android.os.SystemClock.uptimeMillis()
                        if (total > 0 && now - shownAt > 100) { shownAt = now; val p = done.toFloat() / total; runOnUiThread { copied = p } }
                    }
                } }
            }
            catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Could not read the APK: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show(); finish() }; return@Thread }
            // the window reads the root state: it waits for the check a moment at most
            runCatching { rootCheck.join(1500) }
            runOnUiThread { file = f }
        }.start()
    }

    private fun nameOf(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c: Cursor -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    /** True when the link is not an APK or a bundle: by its name first, then by the first bytes (a package is a zip). */
    private fun notPackage(uri: Uri): Boolean {
        val ext = nameOf(uri).substringAfterLast('.', "").lowercase()
        if (ext in setOf("apk", "xapk", "apks", "apkm")) return false
        if (ext.isNotEmpty() && ext != "bin") return true
        val head = runCatching { contentResolver.openInputStream(uri)?.use { i -> ByteArray(4).also { i.read(it) } } }.getOrNull() ?: return false
        return !(head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte())
    }

    /** The file goes on to the app that opens its kind (the Android chooser asks), never back to this window. */
    private fun handOn(uri: Uri) {
        val ext = nameOf(uri).substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: contentResolver.getType(uri)?.takeIf { it != "application/octet-stream" } ?: "*/*"
        val view = android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = android.content.Intent.createChooser(view, null).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(android.content.Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(android.content.ComponentName(this, InstallActivity::class.java)))
        runCatching { startActivity(chooser) }.onFailure { Toast.makeText(this, com.umutk.pulse.tr("No app opens this file", "Bu dosyayı açan uygulama yok"), Toast.LENGTH_LONG).show() }
    }

    /** Size of the APK for the copy bar; 0 when the provider does not say. */
    private fun size(uri: Uri): Long = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c: Cursor -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L)
}
