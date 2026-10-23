package com.umutk.pulse.features

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.storage.StorageManager
import com.umutk.pulse.Extra

/** Network speed and app size per uid for the Processes table. Both need usage access; without it they stay empty. */
object ProcStats {
    private val uids = HashMap<String, Int>()
    private val sizes = HashMap<Int, Long>()
    private var prev: Map<Int, Long> = emptyMap()
    private var prevAt = 0L
    // a fixed window: the totals then only grow, so two reads apart give the bytes moved in between
    private val from = System.currentTimeMillis() - 86_400_000L
    private val to = from + 4 * 86_400_000L

    /** Uid of the app a process belongs to (com.x:service -> com.x), -1 for system processes. */
    fun uidOf(c: Context, process: String): Int = uids.getOrPut(process.substringBefore(':')) {
        runCatching { c.packageManager.getApplicationInfo(process.substringBefore(':'), 0).uid }.getOrDefault(-1)
    }

    private fun totals(c: Context): Map<Int, Long> {
        if (android.os.Build.VERSION.SDK_INT < 23) return emptyMap()
        val nsm = c.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val sums = HashMap<Int, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) runCatching {
            val s = nsm.querySummary(type, null, from, to)
            val b = NetworkStats.Bucket()
            try { while (s.hasNextBucket()) { s.getNextBucket(b); sums[b.uid] = (sums[b.uid] ?: 0L) + b.rxBytes + b.txBytes } } finally { s.close() }
        }
        return sums
    }

    /** Bytes per second per uid since the last call; empty on the first call. Blocking. */
    fun netRates(c: Context): Map<Int, Float> {
        if (!Extra.usageGranted(c)) return emptyMap()
        val now = System.currentTimeMillis()
        val cur = totals(c)
        val dt = (now - prevAt) / 1000f
        val out = if (prevAt == 0L || dt <= 0f) emptyMap() else cur.mapValues { (uid, v) -> ((v - (prev[uid] ?: v)).coerceAtLeast(0L) / dt) }
        prev = cur; prevAt = now
        return out
    }

    /** App + data + cache bytes of a uid, read once and kept. Blocking. Null without usage access or for system processes. */
    fun size(c: Context, uid: Int): Long? {
        if (uid < 10000 || !Extra.usageGranted(c)) return null
        if (android.os.Build.VERSION.SDK_INT < 26) return null
        sizes[uid]?.let { return it }
        val st = runCatching {
            (c.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager).queryStatsForUid(StorageManager.UUID_DEFAULT, uid)
        }.getOrNull() ?: return null
        return (st.appBytes + st.dataBytes).also { sizes[uid] = it }
    }
}
