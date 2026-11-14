package com.umutk.pulse.features

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import java.io.File

/** One run of a neofetch line: its text and its colour (null: the screen's own text colour). */
class NfSeg(val text: String, val color: Color? = null, val bold: Boolean = false)

/**
 * The system card of the phone, made like neofetch: an ASCII logo at the left (the Android robot, in the drawing of the phone's Android generation) and, beside it, the lines of the phone
 * (user@host, OS, Host, Kernel, Uptime, Packages, Shell, Resolution, DE, WM, Theme, Terminal, CPU, Memory, Disk, Battery) and the two rows of colour blocks. Used by the Terminal's
 * `neofetch` command, the charging animation and the Neofetch screen cover, so they all show the same card.
 */
object NeofetchText {
    val GREEN = Color(0xFF3DDC84)
    private val KEY = Color(0xFF3DDC84)
    private val PALETTE = listOf(0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510, 0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
        0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543, 0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF).map { Color(it) }

    /** The Android robot, one drawing for each generation of Android: the old square head, the classic robot with its antennae, the round modern one. */
    private fun logo(api: Int): List<String> = when {
        api >= 31 -> listOf(
            "        .-----.        ",
            "     ,-'       '-.     ",
            "    /   o     o   \\    ",
            "   |  ,___________,  |  ",
            "    \\  '---------'  /   ",
            "   ,-'-----------'-.   ",
            "  /                 \\  ",
            " |   |           |   | ",
            " |   |           |   | ",
            "  \\_/|           |\\_/  ",
            "     |_____ _____|     ",
            "     |  |     |  |     ",
            "     |__|     |__|     ",
        )
        api >= 28 -> listOf(
            "         -o          o-         ",
            "          +hydNNNNdyh+          ",
            "        +mMMMMMMMMMMMMm+        ",
            "      `dMMm:NMMMMMMN:mMMd`      ",
            "      hMMMMMMMMMMMMMMMMMMh      ",
            "  ..  yyyyyyyyyyyyyyyyyyyy  ..  ",
            ".mMMm`MMMMMMMMMMMMMMMMMMMM`mMMm.",
            ":MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM:",
            ":MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM:",
            ":MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM:",
            ":MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM:",
            "-MMMM-MMMMMMMMMMMMMMMMMMMM-MMMM-",
            " +yy+ MMMMMMMMMMMMMMMMMMMM +yy+ ",
            "      mMMMMMMMMMMMMMMMMMMm      ",
            "      `/++MMMMh++hMMMM++/`      ",
            "          MMMMo  oMMMM          ",
            "          MMMMo  oMMMM          ",
            "          oNMm-  -mMNs          ",
        )
        else -> listOf(
            "     .-.-.     ",
            "    ( o   o )  ",
            "  .-'-------'-.",
            " |  _________  |",
            " | |         | |",
            " | |         | |",
            " | |_________| |",
            " |_____________|",
            "   |_|     |_|  ",
        )
    }

    private fun uptime(): String {
        val m = SystemClock.elapsedRealtime() / 60000
        val d = m / 1440; val h = m % 1440 / 60; val mm = m % 60
        return listOfNotNull(if (d > 0) "$d day${if (d > 1) "s" else ""}" else null, if (h > 0) "$h hour${if (h > 1) "s" else ""}" else null, "$mm min${if (mm != 1L) "s" else ""}").joinToString(", ")
    }

    private fun readFirst(path: String): String? = runCatching { File(path).readText().trim() }.getOrNull()

    /** The lines of the card. [full]: false leaves out the slow ones (the packages count and the disk) for a card that is redrawn often. */
    fun info(ctx: Context): List<Pair<String, String>> {
        val dm = ctx.resources.displayMetrics
        val mi = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        val totalMem = mi.totalMem / 1048576; val usedMem = (mi.totalMem - mi.availMem) / 1048576
        val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val disk = st?.let { val t = it.totalBytes / 1073741824.0; val u = t - it.availableBytes / 1073741824.0; "%.1fG / %.1fG".format(u, t) } ?: "-"
        val pkgs = runCatching { ctx.packageManager.getInstalledApplications(0).size }.getOrDefault(0)
        val bat = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val pct = bat?.let { 100 * it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
        val charging = (bat?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val cores = Runtime.getRuntime().availableProcessors()
        val maxKhz = (0 until cores).mapNotNull { readFirst("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")?.toLongOrNull() }.maxOrNull()
        val soc = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != "unknown" }.joinToString(" ") else ""
        val cpu = (soc.ifBlank { Build.HARDWARE } + " (" + cores + ")" + (maxKhz?.let { " @ %.2fGHz".format(it / 1_000_000.0) } ?: ""))
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().let { if (it.startsWith("arm64")) "aarch64" else it }
        return listOf(
            "OS" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) $abi",
            "Host" to "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            "Kernel" to (System.getProperty("os.version") ?: "-"),
            "Uptime" to uptime(),
            "Packages" to "$pkgs (pm)",
            "Shell" to "sh (Pulse Terminal)",
            "Resolution" to "${dm.widthPixels}x${dm.heightPixels} @ ${dm.densityDpi}dpi",
            "DE" to "Pulse Launcher",
            "WM" to "SurfaceFlinger",
            "Theme" to (runCatching { com.umutk.pulse.Look.theme.name }.getOrNull() ?: "Pulse"),
            "Terminal" to "Pulse Terminal",
            "CPU" to cpu,
            "Memory" to "${usedMem}MiB / ${totalMem}MiB",
            "Disk (/data)" to disk,
            "Battery" to (if (pct >= 0) "$pct%" + if (charging) " [Charging]" else "" else "-"),
        )
    }

    /**
     * The whole card as coloured lines: the logo at the left in the Android green, the info at the right, then the colour blocks. [rows] limits how many info lines show (a small
     * card shows the first lines only).
     */
    fun lines(ctx: Context, rows: Int = 99): List<List<NfSeg>> {
        val art = logo(Build.VERSION.SDK_INT)
        val width = art.maxOf { it.length } + 3
        val info = info(ctx)
        val host = (Build.MODEL.ifBlank { "phone" }).lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val right = ArrayList<List<NfSeg>>()
        right.add(listOf(NfSeg("pulse", KEY, true), NfSeg("@", null), NfSeg(host, KEY, true)))
        right.add(listOf(NfSeg("-".repeat("pulse@$host".length), null)))
        info.take(rows).forEach { (k, v) -> right.add(listOf(NfSeg(k, KEY, true), NfSeg(": "), NfSeg(v))) }
        right.add(emptyList())
        right.add(PALETTE.take(8).map { NfSeg("   ", null).let { _ -> NfSeg("\u2588\u2588\u2588", it) } })
        right.add(PALETTE.drop(8).map { NfSeg("\u2588\u2588\u2588", it) })
        val n = maxOf(art.size, right.size)
        return List(n) { i ->
            val left = art.getOrNull(i).orEmpty()
            listOf(NfSeg(left.padEnd(width), GREEN)) + right.getOrElse(i) { emptyList() }
        }
    }
}
