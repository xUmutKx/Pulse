package com.umutk.pulse.features

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.umutk.pulse.features.Downloads
import java.io.File

/**
 * A magnet link or a .torrent file sent to Pulse from the system's "open with" menu: it becomes a download in Pulse's list,
 * and the download page opens. Shows nothing of its own.
 */
class TorrentLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri != null) Thread {
            // a file another app shares is copied into the cache first, so the engine reads a file of its own
            val url = if (uri.scheme == "content") {
                val f = File(cacheDir, "torrents/${System.nanoTime()}.torrent").apply { parentFile?.mkdirs() }
                contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                f.toURI().toString()
            } else uri.toString()
            Downloads.add(applicationContext, url)
        }.start()
        startActivity(Downloads.openPage(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
