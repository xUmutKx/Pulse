package com.umutk.pulse.features

import android.content.Context
import java.io.File
import java.io.IOException

/** The Google Play build has no torrent engine (the free-software build has it): the same names, nothing behind them. */
object TorrentEngine {
    val available: Boolean get() = false
    @Suppress("UNUSED_PARAMETER")
    suspend fun download(ctx: Context, magnet: String, torrent: ByteArray?, base: File, stop: () -> Boolean, onName: (String) -> Unit, onProgress: (done: Long, total: Long, peers: Int) -> Unit): File? =
        throw IOException("this build has no torrent engine (it is in the free-software full build)")
    @Suppress("UNUSED_PARAMETER")
    fun done(file: File) {}
}
