package com.umutk.pulse.features

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.umutk.pulse.features.Downloads

/**
 * A magnet link or a .torrent file sent to Pulse from the system's "open with" menu: it goes to a torrent app on the phone,
 * and Pulse's download page opens. Shows nothing of its own.
 */
class TorrentLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { Downloads.openTorrent(applicationContext, it) }
        startActivity(Downloads.openPage(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
