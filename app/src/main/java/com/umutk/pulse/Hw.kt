package com.umutk.pulse

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import android.hardware.display.DisplayManager
import java.io.File
import java.net.NetworkInterface
import java.util.UUID

/** Readers for the hardware / system pages. Every one is wrapped so a phone that refuses a call just shows fewer rows. */
object Hw {
    private fun read(path: String): String = try { File(path).readText().trim() } catch (e: Exception) { "" }
    private fun gb(b: Long) = "%.1f GB".format(b / 1073741824.0)
    private fun mb(b: Long) = "%.0f MB".format(b / 1048576.0)
    private inline fun <T> tryOr(d: T, f: () -> T): T = try { f() } catch (e: Throwable) { d }
    private fun prop(k: String): String = Shell.run("getprop $k").trim()

    // ------------------------------------------------------------ battery
    fun battery(ctx: Context): List<Section> {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = (bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val status = when (bi?.getIntExtra(BatteryManager.EXTRA_STATUS, 0)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"; BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"; BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"; else -> "Unknown"
        }
        val plug = when (bi?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC adapter"; BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"; else -> "Not plugged in"
        }
        val health = when (bi?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"; BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"; BatteryManager.BATTERY_HEALTH_COLD -> "Cold"; else -> "Unknown"
        }
        val v = (bi?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val ua = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val ma = if (kotlin.math.abs(ua) > 20000) ua / 1000 else ua
        val avg = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE).let { if (kotlin.math.abs(it) > 20000) it / 1000 else it }
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val cycles = bi?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
        val remain = tryOr(-1L) { bm.computeChargeTimeRemaining() }
        val now = ArrayList<Pair<String, String>>()
        now += "Level" to "%${level * 100 / scale}"
        now += "Status" to status
        now += "Connection" to plug
        now += "Current (now)" to "$ma mA"
        if (avg != 0) now += "Current (average)" to "$avg mA"
        now += "Voltage" to "%.3f V".format(v)
        now += "Power" to "%.2f W".format(kotlin.math.abs(ma) * v / 1000f)
        now += "Temperature" to Opt.temp((bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f)
        if (remain > 0) now += "Time to full" to hms(remain)
        val hlt = ArrayList<Pair<String, String>>()
        hlt += "Health" to health
        hlt += "Technology" to (bi?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "—")
        if (cycles >= 0) hlt += "Charge cycles" to "$cycles"
        val p = "/sys/class/power_supply/battery/"
        read(p + "cycle_count").takeIf { it.isNotBlank() && cycles < 0 }?.let { hlt += "Charge cycles" to it }
        val full = read(p + "charge_full").toLongOrNull(); val design = read(p + "charge_full_design").toLongOrNull()
        if (full != null && design != null && design > 0) hlt += "Capacity" to "${full / 1000} / ${design / 1000} mAh (%${100 * full / design})"
        if (counter > 0) hlt += "Charge left" to "${counter / 1000} mAh"
        bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER).takeIf { it > 0 }?.let { hlt += "Energy left" to "%.2f Wh".format(it / 1e9) }
        read(p + "charge_type").takeIf { it.isNotBlank() }?.let { hlt += "Charge type" to it }
        return listOf(Section("Now", now), Section("Health", hlt))
    }

    // ------------------------------------------------------------ network
    fun network(ctx: Context): List<Section> {
        val out = ArrayList<Section>()
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        val caps = net?.let { cm.getNetworkCapabilities(it) }
        val lp = net?.let { cm.getLinkProperties(it) }
        val gen = ArrayList<Pair<String, String>>()
        gen += "Connection" to when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }
        if (caps != null) {
            gen += "Download capacity" to "${caps.linkDownstreamBandwidthKbps / 1000} Mbps"
            gen += "Upload capacity" to "${caps.linkUpstreamBandwidthKbps / 1000} Mbps"
            gen += "Metered" to if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "Yes" else "No"
            gen += "Internet validated" to if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "Yes" else "No"
            gen += "VPN" to if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "On" else "Off"
        }
        out += Section("General", gen)
        if (lp != null) {
            val ip = ArrayList<Pair<String, String>>()
            ip += "Interface" to (lp.interfaceName ?: "—")
            lp.linkAddresses.forEach { ip += "Address" to (it.address.hostAddress ?: "") }
            lp.routes.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress?.let { ip += "Gateway" to it }
            lp.dnsServers.forEach { ip += "DNS" to (it.hostAddress ?: "") }
            if (lp.mtu > 0) ip += "MTU" to "${lp.mtu}"
            lp.domains?.let { ip += "Domain" to it }
            ip += "Private DNS" to if (lp.isPrivateDnsActive) (lp.privateDnsServerName ?: "On") else "Off"
            out += Section("IP", ip)
        }
        tryOr(Unit) {
            @Suppress("DEPRECATION")
            val wi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo
            if (wi != null && wi.networkId != -1) {
                val f = wi.frequency
                val band = if (f in 2400..2500) "2.4 GHz" else if (f in 4900..5900) "5 GHz" else if (f >= 5925) "6 GHz" else "$f MHz"
                val rows = ArrayList<Pair<String, String>>()
                rows += "Signal" to "${wi.rssi} dBm (${WifiManager.calculateSignalLevel(wi.rssi, 5)}/4)"
                rows += "Link speed" to "${wi.linkSpeed} Mbps"
                rows += "Send / receive" to "${wi.txLinkSpeedMbps} / ${wi.rxLinkSpeedMbps} Mbps"
                rows += "Frequency" to "$f MHz ($band)"
                if (Build.VERSION.SDK_INT >= 30) rows += "Wi-Fi standard" to when (wi.wifiStandard) { 4 -> "Wi-Fi 4 (n)"; 5 -> "Wi-Fi 5 (ac)"; 6 -> "Wi-Fi 6 (ax)"; 7 -> "Wi-Fi 7 (be)"; 1 -> "Legacy (a/b/g)"; else -> "Unknown" }
                wi.ssid?.takeIf { it != "<unknown ssid>" }?.let { rows.add(0, "Network" to it.trim('"')) }
                out += Section("Wi-Fi", rows)
            }
        }
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        tryOr(Unit) {
            val opr = tm?.networkOperatorName
            if (!opr.isNullOrBlank()) out += Section("Carrier", listOf("Name" to opr, "Country" to (tm.networkCountryIso ?: "").uppercase(), "Roaming" to if (tm.isNetworkRoaming) "Yes" else "No"))
        }
        out += Section("Total traffic (since boot)", listOf(
            "Downloaded" to mb(TrafficStats.getTotalRxBytes()), "Uploaded" to mb(TrafficStats.getTotalTxBytes()),
            "Mobile downloaded" to mb(TrafficStats.getMobileRxBytes()), "Mobile uploaded" to mb(TrafficStats.getMobileTxBytes()),
        ))
        tryOr(Unit) {
            val rows = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.map { it.name to it.interfaceAddresses.joinToString(" ") { a -> a.address.hostAddress?.substringBefore('%') ?: "" } }
            if (rows.isNotEmpty()) out += Section("Interfaces", rows)
        }
        return out
    }

    /** TCP connect times to a host, in ms; -1 when it failed. */
    fun ping(host: String, port: Int, n: Int = 5): List<Long> = List(n) {
        val t = SystemClock.elapsedRealtime()
        try { java.net.Socket().use { it.connect(java.net.InetSocketAddress(host, port), 2000) }; SystemClock.elapsedRealtime() - t } catch (e: Exception) { -1L }
    }

    // ------------------------------------------------------------ display
    fun display(ctx: Context): List<Section> {
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val d = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val m = ctx.resources.displayMetrics
        val rows = ArrayList<Pair<String, String>>()
        rows += "Resolution" to "${m.widthPixels} × ${m.heightPixels} px"
        rows += "Density" to "${m.densityDpi} dpi (${"%.2f".format(m.density)}x)"
        rows += "Physical dpi" to "${"%.0f".format(m.xdpi)} × ${"%.0f".format(m.ydpi)}"
        val inch = Math.hypot((m.widthPixels / m.xdpi).toDouble(), (m.heightPixels / m.ydpi).toDouble())
        rows += "Screen size" to "%.2f in".format(inch)
        rows += "Aspect ratio" to "%.2f : 1".format(maxOf(m.widthPixels, m.heightPixels).toFloat() / minOf(m.widthPixels, m.heightPixels))
        if (d != null) {
            rows += "Refresh rate" to "%.0f Hz".format(d.refreshRate)
            val modes = d.supportedModes.map { "%.0f".format(it.refreshRate) }.distinct()
            rows += "Supported rates" to modes.joinToString(" / ") + " Hz"
            rows += "Wide colour (P3)" to if (d.isWideColorGamut) "Yes" else "None"
            @Suppress("DEPRECATION")
            val hdr = d.hdrCapabilities?.supportedHdrTypes?.map { when (it) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "?" } }.orEmpty()
            rows += "HDR" to if (hdr.isEmpty()) "None" else hdr.joinToString(", ")
            rows += "Name" to d.name
        }
        val set = ArrayList<Pair<String, String>>()
        tryOr(Unit) { set += "Brightness" to "${android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)} / 255" }
        tryOr(Unit) { set += "Screen timeout" to "${android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_OFF_TIMEOUT) / 1000} s" }
        set += "Font scale" to "%.2fx".format(ctx.resources.configuration.fontScale)
        set += "Orientation" to if (ctx.resources.configuration.orientation == 2) "Landscape" else "Portrait"
        set += "Dark mode" to if ((ctx.resources.configuration.uiMode and 0x30) == 0x20) "On" else "Off"
        return listOf(Section("Panel", rows), Section("Settings", set))
    }

    // ------------------------------------------------------------ camera
    fun camera(ctx: Context): List<Section> = tryOr(emptyList()) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.mapNotNull { id ->
            tryOr(null) {
                val c = cm.getCameraCharacteristics(id)
                val face = when (c.get(CameraCharacteristics.LENS_FACING)) { CameraCharacteristics.LENS_FACING_FRONT -> "Front"; CameraCharacteristics.LENS_FACING_BACK -> "Back"; else -> "External" }
                val px = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val phys = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val ap = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                val level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) { 0 -> "Limited"; 1 -> "Full"; 2 -> "Legacy"; 3 -> "Level 3"; 4 -> "External"; else -> "?" }
                val ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.any { it == 1 } == true
                val rows = ArrayList<Pair<String, String>>()
                if (px != null) rows += "Sensor" to "${px.width} × ${px.height} (%.1f MP)".format(px.width * px.height / 1e6)
                if (phys != null) rows += "Sensor size" to "%.2f × %.2f mm".format(phys.width, phys.height)
                if (focal != null && focal.isNotEmpty()) rows += "Focal length" to focal.joinToString(", ") { "%.1f mm".format(it) }
                if (ap != null && ap.isNotEmpty()) rows += "Aperture" to ap.joinToString(", ") { "f/%.1f".format(it) }
                rows += "Flash" to if (c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) "Yes" else "None"
                rows += "OIS" to if (ois) "Yes" else "None"
                rows += "Hardware level" to level
                rows += "Sensor orientation" to "${c.get(CameraCharacteristics.SENSOR_ORIENTATION)}°"
                Section("Kamera $id · $face", rows)
            }
        }
    }

    // ------------------------------------------------------------ media
    fun media(): List<Section> {
        val out = ArrayList<Section>()
        tryOr(Unit) {
            val infos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            val dec = infos.filter { !it.isEncoder }; val enc = infos.filter { it.isEncoder }
            out += Section("Codecs", listOf("Decoders" to "${dec.size}", "Encoders" to "${enc.size}", "Hardware accelerated" to "${infos.count { it.isHardwareAccelerated }}"))
            fun group(list: List<android.media.MediaCodecInfo>, title: String) {
                val rows = list.filter { it.isHardwareAccelerated }.flatMap { ci -> ci.supportedTypes.filter { it.startsWith("video/") || it.startsWith("audio/") }.map { it to ci.name } }
                    .groupBy({ it.first }, { it.second }).map { (k, v) -> k.removePrefix("video/").removePrefix("audio/").uppercase() to v.first().substringAfterLast('.') }.sortedBy { it.first }
                if (rows.isNotEmpty()) out += Section(title, rows)
            }
            group(dec, "Hardware decoders"); group(enc, "Hardware encoders")
        }
        tryOr(Unit) {
            val w = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
            val drm = MediaDrm(w)
            out += Section("DRM (Widevine)", listOf("Level" to drm.getPropertyString("securityLevel"), "Vendor" to drm.getPropertyString("vendor"), "Version" to drm.getPropertyString("version"), "Algorithms" to drm.getPropertyString("algorithms")))
            drm.close()
        }
        return out
    }

    // ------------------------------------------------------------ system
    fun system(ctx: Context): List<Section> {
        val out = ArrayList<Section>()
        out += Section("Android", listOf(
            "Version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", "Security patch" to Build.VERSION.SECURITY_PATCH,
            "Build" to Build.DISPLAY, "Fingerprint" to Build.FINGERPRINT, "Type / tags" to "${Build.TYPE} / ${Build.TAGS}",
            "Bootloader" to Build.BOOTLOADER, "Radio" to (Build.getRadioVersion() ?: "—"),
        ))
        val ai = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        out += Section("Hardware", listOf(
            "Manufacturer / model" to "${Build.MANUFACTURER} ${Build.MODEL}", "Codename" to "${Build.DEVICE} · ${Build.PRODUCT}", "Board" to Build.BOARD,
            "Chipset" to (if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it != "unknown" } ?: Build.HARDWARE else Build.HARDWARE), "Architecture" to Build.SUPPORTED_ABIS.joinToString(", "),
            "OpenGL ES" to ai.deviceConfigurationInfo.glEsVersion, "Memory class" to "${ai.memoryClass} MB (large ${ai.largeMemoryClass} MB)",
        ))
        val up = SystemClock.elapsedRealtime(); val awake = SystemClock.uptimeMillis()
        out += Section("Runtime", listOf(
            "Up time" to hms(up), "Deep sleep" to "%${100 * (up - awake) / up.coerceAtLeast(1)} (${hms(up - awake)})",
            "Kernel" to (System.getProperty("os.version") ?: ""), "Java VM" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}",
            "Language / time zone" to "${java.util.Locale.getDefault().toLanguageTag()} · ${java.util.TimeZone.getDefault().id}",
        ))
        val sec = ArrayList<Pair<String, String>>()
        Shell.run("getenforce").trim().takeIf { it.isNotBlank() }?.let { sec += "SELinux" to it }
        prop("ro.boot.verifiedbootstate").takeIf { it.isNotBlank() }?.let { sec += "Verified boot" to it }
        prop("ro.boot.flash.locked").takeIf { it.isNotBlank() }?.let { sec += "Bootloader lock" to if (it == "1") "Locked" else "On" }
        prop("ro.crypto.state").takeIf { it.isNotBlank() }?.let { sec += "Encryption" to it }
        prop("ro.treble.enabled").takeIf { it.isNotBlank() }?.let { sec += "Project Treble" to if (it == "true") "Yes" else "No" }
        prop("ro.boot.dynamic_partitions").takeIf { it.isNotBlank() }?.let { sec += "Dynamic partitions" to it }
        sec += "Root" to if (Shell.root == true) "Yes" else "None"
        tryOr(Unit) { ctx.packageManager.getPackageInfo("com.google.android.gms", 0).let { sec += "Google Play services" to (it.versionName ?: "present") } }
        out += Section("Security", sec)
        tryOr(Unit) {
            val vk = ctx.packageManager.systemAvailableFeatures.firstOrNull { it.name == "android.hardware.vulkan.version" }
            if (vk != null) { val v = vk.version; out += Section("Graphics", listOf("Vulkan" to "${v shr 22}.${(v shr 12) and 0x3FF}.${v and 0xFFF}")) }
        }
        return out
    }

    // ------------------------------------------------------------ thermal
    fun thermal(ctx: Context): List<Section> {
        val out = ArrayList<Section>()
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val st = when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "None"; PowerManager.THERMAL_STATUS_LIGHT -> "Light"; PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"; PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"; PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"; else -> "Shutdown"
        }
        val rows = arrayListOf("System state" to st)
        if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(10).takeIf { !it.isNaN() }?.let { rows += "Headroom to throttling" to "%${(it * 100).toInt()}" }
        rows += "Battery saver" to if (pm.isPowerSaveMode) "On" else "Off"
        out += Section("Status", rows)
        val zones = ArrayList<Pair<String, String>>()
        for (i in 0 until 80) {
            val t = read("/sys/class/thermal/thermal_zone$i/temp").toFloatOrNull() ?: continue
            val c = if (t > 1000) t / 1000 else t
            if (c !in 1f..150f) continue
            zones += read("/sys/class/thermal/thermal_zone$i/type").ifBlank { "zone$i" } to Opt.temp(c)
        }
        if (zones.isEmpty() && Shell.root != true) out += Section("Zones", listOf("Info" to "This phone does not let apps read temperature zones (root unlocks it)"))
        else out += Section("Zones (${zones.size})", zones)
        return out
    }

    fun maxTemp(): Float {
        var best = 0f
        for (i in 0 until 80) {
            val t = read("/sys/class/thermal/thermal_zone$i/temp").toFloatOrNull() ?: continue
            val c = if (t > 1000) t / 1000 else t
            if (c in 20f..120f && c > best) best = c
        }
        return best
    }

    // ------------------------------------------------------------ memory + storage
    fun memory(ctx: Context): List<Section> {
        val mem = read("/proc/meminfo").lineSequence().associate { l -> l.substringBefore(':') to (l.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L) }
        fun m(k: String) = "%.0f MB".format((mem[k] ?: 0L) / 1024.0)
        val ai = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); ai.getMemoryInfo(mi)
        return listOf(
            Section("RAM", listOf("Total" to m("MemTotal"), "Available" to m("MemAvailable"), "Free" to m("MemFree"), "Cached" to m("Cached"), "Buffers" to m("Buffers"),
                "Active" to m("Active"), "Inactive" to m("Inactive"), "Shared" to m("Shmem"), "Slab (kernel)" to m("Slab"), "Waiting to write" to m("Dirty"))),
            Section("Swap (zRAM)", listOf("Total" to m("SwapTotal"), "Free" to m("SwapFree"), "In cache" to m("SwapCached"))),
            Section("Low memory", listOf("Threshold" to mb(mi.threshold), "Low memory now" to if (mi.lowMemory) "Yes" else "No")),
        )
    }

    fun storage(ctx: Context): List<Section> {
        val out = ArrayList<Section>()
        fun fs(title: String, path: File) = tryOr(Unit) {
            val s = StatFs(path.path); val t = s.totalBytes; val f = s.availableBytes
            out += Section(title, listOf("Total" to gb(t), "Used" to "${gb(t - f)} (%${100 * (t - f) / t.coerceAtLeast(1)})", "Free" to gb(f), "File system" to "block ${s.blockSizeLong} B"))
        }
        fs("Internal (data)", Environment.getDataDirectory())
        fs("Shared storage", Environment.getExternalStorageDirectory())
        tryOr(Unit) {
            ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEachIndexed { i, f -> fs("External card ${i + 1}", f) }
        }
        fs("System partition", Environment.getRootDirectory())
        return out
    }

    /** Folder sizes of the shared storage (slow: walks the files). */
    fun folderSizes(): List<Pair<String, Long>> {
        val root = Environment.getExternalStorageDirectory()
        return (root.listFiles() ?: emptyArray()).filter { it.isDirectory }.map { d -> d.name to tryOr(0L) { d.walkTopDown().filter { it.isFile }.sumOf { it.length() } } }.sortedByDescending { it.second }
    }

    // ------------------------------------------------------------ lists
    fun features(ctx: Context): List<String> = ctx.packageManager.systemAvailableFeatures.mapNotNull { it.name?.removePrefix("android.") }.sorted()

    fun props(): List<Pair<String, String>> =
        Shell.run("getprop").lineSequence().mapNotNull { l ->
            val m = Regex("""\[(.*?)]: \[(.*)]""").find(l) ?: return@mapNotNull null
            m.groupValues[1] to m.groupValues[2]
        }.sortedBy { it.first }.toList()

    // ------------------------------------------------------------ cores
    class CoreInfo(val id: Int, val online: Boolean, val gov: String, val minMhz: Int, val maxMhz: Int)

    fun cores(): List<CoreInfo> = (0 until Runtime.getRuntime().availableProcessors()).map { i ->
        val b = "/sys/devices/system/cpu/cpu$i/"
        CoreInfo(i, read(b + "online") != "0", read(b + "cpufreq/scaling_governor").ifBlank { "—" },
            (read(b + "cpufreq/cpuinfo_min_freq").toIntOrNull() ?: 0) / 1000, (read(b + "cpufreq/cpuinfo_max_freq").toIntOrNull() ?: 0) / 1000)
    }

    /** A plain-text report of everything, to share. */
    fun report(ctx: Context): String {
        val sb = StringBuilder("Pulse report\n")
        listOf(system(ctx), battery(ctx), memory(ctx), storage(ctx), display(ctx), network(ctx), thermal(ctx), camera(ctx), media()).flatten().forEach { s ->
            sb.append("\n== ${s.title}\n"); s.rows.forEach { (k, v) -> sb.append("$k: $v\n") }
        }
        return sb.toString()
    }
}
