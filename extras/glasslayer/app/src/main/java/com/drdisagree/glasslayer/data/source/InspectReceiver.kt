package com.drdisagree.glasslayer.data.source

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * Keeps what the One UI inspector wrote down in System UI and the apps: plain text, one file per package, in the app's own folder (never run, only saved
 * and shown). The newest windows go at the end; a file is kept under two megabytes by dropping its oldest half.
 */
class InspectReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OneUiKeys.ACTION_INSPECT) return
        val source = intent.getStringExtra(OneUiKeys.EXTRA_SOURCE)?.filter { it.isLetterOrDigit() || it == '.' || it == '_' }?.take(80) ?: return
        val text = intent.getStringExtra(OneUiKeys.EXTRA_TEXT) ?: return
        val file = File(dir(context), "$source.txt")
        runCatching {
            file.appendText(text.take(150_000) + "\n")
            if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast((MAX_BYTES / 2).toInt()))
        }
    }

    companion object {
        private const val MAX_BYTES = 2_000_000L
        fun dir(context: Context): File = File(context.filesDir, "inspect").apply { mkdirs() }
    }
}
