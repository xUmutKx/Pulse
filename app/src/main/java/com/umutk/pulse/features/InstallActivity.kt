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
        Cfg.load(this); Opt.load(this); Installer.load(this); InstallShow.load(this)
        val uri = intent.data
        if (uri == null) { finish(); return }
        setContent {
            LookHost(transparent = true) {
                Box(Modifier.fillMaxSize()) {
                    val f = file
                    if (f != null) InstallWindow(f) { finish() } else ApkCopying(copied) { finish() }
                }
            }
        }
        // root is detected before the window reads it; the APK is copied off the main thread, as MainActivity did
        Thread {
            Shell.detectRoot()
            val f = File(cacheDir, "open/open.apk").apply { parentFile?.mkdirs() }
            val total = size(uri)
            try {
                val input = contentResolver.openInputStream(uri) ?: throw IOException("no content")
                input.use { i -> f.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = i.read(buf); if (n < 0) break
                        o.write(buf, 0, n); done += n
                        if (total > 0) { val p = done.toFloat() / total; runOnUiThread { copied = p } }
                    }
                } }
            }
            catch (e: Exception) { runOnUiThread { Toast.makeText(this, "Could not read the APK: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show(); finish() }; return@Thread }
            runOnUiThread { file = f }
        }.start()
    }

    /** Size of the APK for the copy bar; 0 when the provider does not say. */
    private fun size(uri: Uri): Long = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c: Cursor -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
    }.getOrDefault(0L)
}
