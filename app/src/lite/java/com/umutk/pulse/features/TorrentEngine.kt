package com.umutk.pulse.features

import android.content.Context
import java.io.File
import java.io.IOException

/** The lite build has no torrent engine (it is in the full build): the same names, nothing behind them. */
object TorrentEngine {
    val available: Boolean get() = false
    @Suppress("UNUSED_PARAMETER")
    suspend fun download(ctx: Context, magnet: String, dir: File, stop: () -> Boolean, onName: (String) -> Unit, onProgress: (done: Long, total: Long, peers: Int) -> Unit): File? =
        throw IOException("this build has no torrent engine (it is in the full build)")
}
