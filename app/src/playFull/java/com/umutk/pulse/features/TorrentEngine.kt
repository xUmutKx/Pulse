package com.umutk.pulse.features

import android.content.Context
import java.io.File
import java.io.IOException

/** The Google Play build has no torrent engine (the free-software build has it): the same names, nothing behind them. */
object TorrentEngine {
    val available: Boolean get() = false
    fun dhtNodes(): Long = 0L
    fun sessionAgeMs(): Long = 0L
    @Suppress("UNUSED_PARAMETER")
    fun ready(ctx: Context): Boolean = false
    val fetching = kotlinx.coroutines.flow.MutableStateFlow(false)
    @Suppress("UNUSED_PARAMETER")
    suspend fun fetch(ctx: Context): Boolean = false
    @Suppress("UNUSED_PARAMETER")
    suspend fun download(ctx: Context, magnet: String, torrent: ByteArray?, base: File, stop: () -> Boolean, onName: (String) -> Unit, onProgress: (done: Long, total: Long, peers: Int) -> Unit): File? =
        throw IOException("this build has no torrent engine (it is in the free-software full build)")
    @Suppress("UNUSED_PARAMETER")
    fun done(file: File) {}
    /** Plays a torrent while it downloads (the free-software full build): not here. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun stream(ctx: Context, magnet: String, onStatus: (String) -> Unit): String? = null
    fun stopStream() {}
}
