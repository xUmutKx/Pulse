package com.umutk.pulse

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.FileProvider
import java.io.File

/**
 * Pulse Files as the file picker of other apps ("choose a file" to upload or attach): the same Files page, and a tap on a file hands it back.
 * Android shows it beside its own picker; "Always" makes it the one that opens.
 */
class FilePickActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        setContent {
            LookHost(transparent = false) {
                CompositionLocalProvider(
                    com.umutk.pulse.features.LocalFilePick provides { f: File -> deliver(f) },
                    com.umutk.pulse.features.LocalPickAny provides true,
                    com.umutk.pulse.features.LocalPickMime provides intent?.type,
                ) { com.umutk.pulse.features.FilesPage() }
            }
        }
    }

    private fun deliver(f: File) {
        val uri = runCatching { FileProvider.getUriForFile(this, "$packageName.files", f) }.getOrNull() ?: return
        val out = Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        out.clipData = ClipData.newRawUri(f.name, uri)
        setResult(RESULT_OK, out)
        finish()
    }
}
