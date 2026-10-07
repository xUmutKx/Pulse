package com.umutk.pulse

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.File

class Section(val title: String, val rows: List<Pair<String, String>>)

/** Static and slow-changing facts: device, storage, battery health, thermal zones, wifi. */
object Device {
    private fun read(path: String): String = try { File(path).readText().trim() } catch (e: Exception) { "" }

    private fun gb(b: Long) = "%.1f GB".format(b / 1073741824.0)

    private fun uptime(): String {
        val s = SystemClock.elapsedRealtime() / 1000
        return "${s / 86400}d ${s % 86400 / 3600}h ${s % 3600 / 60}m"
    }

    fun sections(ctx: Context): List<Section> {
        val out = ArrayList<Section>()
        out += Section("Device", listOf(
            "Model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Kernel" to (System.getProperty("os.version") ?: ""),
            "Hardware" to Build.HARDWARE,
            "Up time" to uptime(),
        ))
        val d = StatFs(Environment.getDataDirectory().path)
        val total = d.totalBytes; val free = d.availableBytes
        out += Section("Storage", listOf(
            "Total" to gb(total), "Used" to "${gb(total - free)} (%${100 * (total - free) / total.coerceAtLeast(1)})", "Free" to gb(free),
        ))
        val p = "/sys/class/power_supply/battery/"
        val cycles = read(p + "cycle_count")
        val full = read(p + "charge_full").toLongOrNull()
        val design = read(p + "charge_full_design").toLongOrNull()
        val rows = ArrayList<Pair<String, String>>()
        rows += "Technology" to read(p + "technology").ifBlank { "—" }
        rows += "Health" to read(p + "health").ifBlank { "—" }
        if (cycles.isNotBlank()) rows += "Charge cycles" to cycles
        if (full != null && design != null && design > 0) rows += "Capacity" to "${full / 1000} / ${design / 1000} mAh (%${100 * full / design})"
        out += Section("Battery", rows)
        val zones = ArrayList<Pair<String, String>>()
        for (i in 0 until 60) {
            val t = read("/sys/class/thermal/thermal_zone$i/temp").toFloatOrNull() ?: continue
            val c = if (t > 1000) t / 1000 else t
            if (c !in 1f..150f) continue
            zones += read("/sys/class/thermal/thermal_zone$i/type").ifBlank { "zone$i" } to "%.1f°C".format(c)
        }
        if (zones.isNotEmpty()) out += Section("Temperatures", zones.take(16))
        try {
            @Suppress("DEPRECATION")
            val wi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
            if (wi != null && wi.networkId != -1) out += Section("Wi-Fi", listOf("Speed" to "${wi.linkSpeed} Mbps", "Signal" to "${wi.rssi} dBm", "Frequency" to "${wi.frequency} MHz"))
        } catch (e: Exception) { }
        return out
    }
}
