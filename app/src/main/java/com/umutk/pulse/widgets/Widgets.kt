package com.umutk.pulse.widgets

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.Extra
import com.umutk.pulse.Guard
import com.umutk.pulse.MainActivity
import com.umutk.pulse.Opt
import com.umutk.pulse.Sampler
import com.umutk.pulse.R
import com.umutk.pulse.Snap
import com.umutk.pulse.features.DlState
import com.umutk.pulse.features.Downloads
import com.umutk.pulse.mb
import com.umutk.pulse.rate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Page a widget tap asked for; MainActivity shows it and clears it. Empty: nothing to open. */
object WidgetOpen { var page by mutableStateOf("") }

/** The looks a widget can wear. Each one has its own background drawable (shape, bevel or gradient) and text colours. */
enum class WidgetStyle(val label: String, val bg: Int, val title: Int, val value: Int, val titleBg: Int = 0, val valueBg: Int = 0) {
    MATERIAL("Material", MaterialArt.card, 0xFF49454F.toInt(), 0xFF1D1B20.toInt()),
    AMOLED("AMOLED", R.drawable.widget_amoled, 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt()),
    WIN11("Windows 11", R.drawable.widget_win11, 0xFFC8C8C8.toInt(), 0xFFFFFFFF.toInt()),
    WIN11_DARK("Windows 11 dark", R.drawable.widget_win11_dark, 0xFFD0D0D0.toInt(), 0xFFFFFFFF.toInt()),
    WIN10("Windows 10", R.drawable.widget_win10, 0xFFCCE4F7.toInt(), 0xFFFFFFFF.toInt()),
    WIN7("Windows 7", R.drawable.widget_win7_gadget, 0xFFCFE8FF.toInt(), 0xFFFFFFFF.toInt()),
    WIN95("Windows 95", R.drawable.widget_win95_bevel, 0xFF000000.toInt(), 0xFF000000.toInt(), valueBg = R.drawable.widget_win95_field),
    XP("Windows XP", R.drawable.widget_xp, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), R.drawable.widget_xp_title),
    ONEUI("One UI", R.drawable.widget_oneui, 0xFF6B6B70.toInt(), 0xFF000000.toInt());

    /** The design key of the opacity defaults ([WidgetLook.defaults]). */
    val design get() = when (this) { MATERIAL -> "material"; AMOLED -> "amoled"; WIN11, WIN11_DARK -> "win11"; WIN10 -> "win10"; WIN7 -> "win7"; WIN95 -> "win95"; XP -> "xp"; ONEUI -> "oneui" }
}

/** Which look each widget instance wears, kept per appWidgetId in SharedPreferences. */
object WidgetStyles {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    /** The look a widget gets when it has none of its own: the one picked on the Widgets page. */
    fun of(c: Context, id: Int): WidgetStyle =
        WidgetStyle.values().firstOrNull { it.name == prefs(c).getString("style_$id", null) } ?: default(c)

    /** The picked default, else the look Pulse itself wears (its saved theme), so an unpicked widget matches the app. */
    fun default(c: Context): WidgetStyle =
        WidgetStyle.values().firstOrNull { it.name == prefs(c).getString("default", null) } ?: appLook(c)

    /** Pulse's saved theme as a widget look; themes without a widget look (glass, iOS, ColorOS, ...) use Material. */
    private fun appLook(c: Context): WidgetStyle = when (com.umutk.pulse.ownPrefs(c, "pulse_settings").getString("theme", "win11")) {
        "win11" -> WidgetStyle.WIN11; "win10" -> WidgetStyle.WIN10; "win7" -> WidgetStyle.WIN7
        "xp" -> WidgetStyle.XP; "win95" -> WidgetStyle.WIN95; "amoled" -> WidgetStyle.AMOLED
        else -> WidgetStyle.MATERIAL
    }

    fun saveDefault(c: Context, s: WidgetStyle) = prefs(c).edit().putString("default", s.name).apply()

    fun save(c: Context, id: Int, s: WidgetStyle) = prefs(c).edit().putString("style_$id", s.name).apply()

    fun remove(c: Context, ids: IntArray) {
        val edit = prefs(c).edit()
        ids.forEach { edit.remove("style_$it") }
        edit.apply()
    }
}

/** The values the app saved while it sampled: the widgets read these, so they still show something when Pulse is not running. */
object WidgetData {
    private var lastSaved = 0L

    fun prefs(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** Called on every sample; writes and refreshes the widgets at most every 5 s. */
    fun save(c: Context, s: Snap) {
        val now = System.currentTimeMillis()
        if (now - lastSaved < 5_000) return
        lastSaved = now
        val dl = Downloads.tasks.value
        val running = dl.filter { it.state.value == DlState.RUNNING }
        prefs(c).edit()
            .putInt("cpu", s.cpu).putInt("ramUsed", s.ramUsedMb).putInt("ramTotal", s.ramTotalMb)
            .putFloat("temp", s.cpuTemp).putLong("down", s.down).putLong("up", s.up)
            .putInt("bat", s.batPct).putBoolean("charging", s.charging)
            .putInt("dlActive", running.size).putLong("dlSpeed", running.sumOf { it.speed.value })
            .putString("lastDl", dl.lastOrNull()?.file?.name.orEmpty())
            .apply()
        refresh(c)
    }

    /** Redraws every placed widget now, for example after a look setting changed. */
    fun refreshNow(c: Context) = refresh(c)

    /** Pushes the saved values to each widget on the home screen, each in its own style. */
    private fun refresh(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        all.forEach { w ->
            mgr.getAppWidgetIds(ComponentName(c, w.javaClass)).forEach { id ->
                mgr.updateAppWidget(id, w.contentFor(c, id, WidgetStyles.of(c, id)))
            }
        }

        // the clock and day faces show the time, so they follow the same refresh
        FaceWidget.refreshAll(c)
        SkinWidget.refreshAll(c)
    }

    val all: List<PulseWidget> by lazy {
        listOf(
            UsageWidget(), BatteryWidget(), StorageWidget(), DownloadsWidget(), MemoryWidget(),
            TempWidget(), NetworkWidget(), ClockWidget(), UptimeWidget(), StorageBarWidget(),
            RamFreeWidget(), CoresWidget(), BrightnessWidget(), WifiWidget(), SignalWidget(),
            WeatherWidget(), WeatherWideWidget(), WeatherClearWidget(),
            BatteryHealthWidget(), AppCountWidget(), LastDownloadWidget(), NextAlarmWidget(), QuickWidget(), VoiceWidget(),
        ) + HealthWidgets.all + com.umutk.pulse.extras.notes.NotesWidget()
    }
}

/** The appWidgetId a text widget is being built for (0 = a preview); read by [PulseWidget.views]. */
@Volatile internal var textId = 0

/** The icon each text widget layout shows next to its title and value. */
private val ICONS = mapOf(
    R.layout.widget_notes to R.drawable.wi_notes,
    R.layout.widget_usage to R.drawable.wi_usage, R.layout.widget_battery to R.drawable.wi_battery, R.layout.widget_batthealth to R.drawable.wi_batthealth,
    R.layout.widget_storage to R.drawable.wi_storage, R.layout.widget_storagebar to R.drawable.wi_storagebar, R.layout.widget_downloads to R.drawable.wi_downloads,
    R.layout.widget_lastdl to R.drawable.wi_lastdl, R.layout.widget_memory to R.drawable.wi_memory, R.layout.widget_ramfree to R.drawable.wi_ramfree,
    R.layout.widget_temp to R.drawable.wi_temp, R.layout.widget_cores to R.drawable.wi_cores, R.layout.widget_network to R.drawable.wi_network,
    R.layout.widget_wifi to R.drawable.wi_wifi, R.layout.widget_signal to R.drawable.wi_signal, R.layout.widget_brightness to R.drawable.wi_brightness,
    R.layout.widget_clock to R.drawable.wi_clock, R.layout.widget_uptime to R.drawable.wi_uptime, R.layout.widget_alarm to R.drawable.wi_alarm,
    R.layout.widget_apps to R.drawable.wi_apps, R.layout.widget_quick to R.drawable.wi_quick, R.layout.widget_health to R.drawable.wi_health,
)

/** Base of the 20 home-screen widgets: each one shows a title and a value; a tap opens Pulse on [page]. */
abstract class PulseWidget : AppWidgetProvider() {
    /** The RemoteViews for this widget in [s], built from its layout and the latest values. */
    abstract fun content(ctx: Context, s: WidgetStyle): RemoteViews

    /** [content] for the placed widget [id], so its size and own look overrides are used. */
    fun contentFor(ctx: Context, id: Int, s: WidgetStyle): RemoteViews { textId = id; try { return content(ctx, s) } finally { textId = 0 } }

    /** [contentFor], or the plain Pulse fallback when building it throws: the launcher then shows that instead of "Can't load widget". */
    private fun safeContent(ctx: Context, id: Int): RemoteViews = try {
        contentFor(ctx, id, WidgetStyles.of(ctx, id))
    } catch (e: Exception) {
        android.util.Log.e("PulseWidget", "${javaClass.simpleName} ($id) could not be built", e)
        RemoteViews(ctx.packageName, R.layout.widget_fallback)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, safeContent(ctx, it)) }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: android.os.Bundle?) {
        mgr.updateAppWidget(id, safeContent(ctx, id))
    }

    override fun onDeleted(ctx: Context, ids: IntArray) = WidgetStyles.remove(ctx, ids)

    protected fun views(ctx: Context, s: WidgetStyle, layout: Int, title: String, value: String, page: String = "", t: Tile? = null): RemoteViews =
        RemoteViews(ctx.packageName, layout).apply {
            // the whole tile (background, icon, title, value, lines, bar, graph) is drawn into one bitmap at the widget's real size, in the look's own style
            val id = textId
            val dens = ctx.resources.displayMetrics.density
            val sz = WidgetSize.dp(ctx, id) ?: (250 to 110)
            val tw = (sz.first * dens).toInt().coerceIn(100, 1400); val th = (sz.second * dens).toInt().coerceIn(60, 1400)
            val prev = Win11Material.forId.get(); Win11Material.forId.set(id)
            try {
                setImageViewBitmap(R.id.tile, TextTile.fitBinder(TextTile.draw(ctx, s, ICONS[layout] ?: 0, title, t ?: Tile(value), tw, th, id)))
            } finally { Win11Material.forId.set(prev) }
            setViewVisibility(R.id.tile, android.view.View.VISIBLE)
            setContentDescription(R.id.tile, "$title $value")
            setViewVisibility(R.id.icon, android.view.View.GONE)
            setViewVisibility(R.id.title, android.view.View.GONE)
            setViewVisibility(R.id.value, android.view.View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            setOnClickPendingIntent(R.id.root, WidgetTap.text(ctx, layout, page, id))
        }

    protected fun pct(a: Int, b: Int) = if (b <= 0) 0 else a * 100 / b

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    /** Free and total bytes of the data partition, the one the app's storage pages read. */
    protected fun disk(): Pair<Long, Long> = try {
        StatFs(Environment.getDataDirectory().path).let { it.availableBytes to it.totalBytes }
    } catch (e: Exception) { 0L to 0L }
}

private fun hist(key: String): List<Float> = Sampler.history.value[key].orEmpty()
private fun toneOf(pct: Int, charging: Boolean) = when { charging -> 0xFF43A047.toInt(); pct <= 15 -> 0xFFE53935.toInt(); pct <= 30 -> 0xFFFFA000.toInt(); else -> 0 }

class UsageWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val cpu = p.getInt("cpu", 0); val used = p.getInt("ramUsed", 0); val total = p.getInt("ramTotal", 0)
        val ram = pct(used, total)
        val temp = p.getFloat("temp", 0f)
        Opt.load(ctx)
        val sub = listOfNotNull("RAM $ram% · ${mb(used)} / ${mb(total)}", if (temp > 0) "CPU ${Opt.temp(temp)}" else null)
        return views(ctx, s, R.layout.widget_usage, "CPU and memory", "CPU $cpu% · RAM $ram%", "performance",
            Tile("CPU $cpu%", sub, ram / 100f, hist("cpu"), hist("ram"), 100f))
    }
}

class BatteryWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val bat = p.getInt("bat", 0); val ch = p.getBoolean("charging", false)
        val sn = Sampler.snap.value
        Opt.load(ctx)
        val sub = listOfNotNull(if (ch) "Charging" else "On battery",
            if (sn.batV > 0f) "%.2f V · %d mA".format(sn.batV, kotlin.math.abs(sn.batMa)) + (if (sn.batTemp > 0f) " · ${Opt.temp(sn.batTemp)}" else "") else null)
        return views(ctx, s, R.layout.widget_battery, "Battery", "$bat%${if (ch) " · charging" else ""}", "device",
            Tile("$bat%", sub, bat / 100f, hist("bat"), seriesMax = 100f, tone = toneOf(bat, ch)))
    }
}

class StorageWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val (free, total) = disk()
        val used = if (total > 0) 1f - free.toFloat() / total else 0f
        return views(ctx, s, R.layout.widget_storage, "Storage", "${Guard.gb(free)} free of ${Guard.gb(total)}", "files",
            Tile(Guard.gb(free), listOf("free of ${Guard.gb(total)}", "${(used * 100).toInt()}% used · ${Guard.gb(total - free)}"), used))
    }
}

class DownloadsWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val n = p.getInt("dlActive", 0); val speed = p.getLong("dlSpeed", 0L)
        val last = p.getString("lastDl", "").orEmpty()
        return views(ctx, s, R.layout.widget_downloads, "Downloads", "$n active · ${rate(speed.toFloat())}", "downloads",
            Tile(if (n > 0) rate(speed.toFloat()) else "Idle", listOfNotNull("$n active", if (last.isNotEmpty()) "Last: $last" else null), if (n > 0) 1f else 0f, hist("down"), seriesMax = 0f))
    }
}

class MemoryWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val used = p.getInt("ramUsed", 0); val total = p.getInt("ramTotal", 0)
        val sn = Sampler.snap.value
        val sub = listOfNotNull("of ${mb(total)} · ${pct(used, total)}%", if (sn.swapTotalMb > 0) "Swap ${mb(sn.swapUsedMb)} / ${mb(sn.swapTotalMb)}" else null)
        return views(ctx, s, R.layout.widget_memory, "Memory", "${mb(used)} of ${mb(total)}", "performance",
            Tile(mb(used), sub, pct(used, total) / 100f, hist("ram"), seriesMax = 100f))
    }
}

class TempWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        Opt.load(ctx)
        val t = WidgetData.prefs(ctx).getFloat("temp", 0f)
        val sn = Sampler.snap.value
        val sub = listOfNotNull("CPU", if (sn.batTemp > 0f) "Battery ${Opt.temp(sn.batTemp)}" else null)
        val tone = when { t >= 75 -> 0xFFE53935.toInt(); t >= 60 -> 0xFFFFA000.toInt(); else -> 0 }
        return views(ctx, s, R.layout.widget_temp, "Temperature", if (t > 0) Opt.temp(t) else "—", "performance",
            Tile(if (t > 0) Opt.temp(t) else "—", sub, if (t > 0) (t / 100f) else null, hist("temp"), seriesMax = 100f, tone = tone))
    }
}

class NetworkWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val down = rate(p.getLong("down", 0L).toFloat())
        val up = rate(p.getLong("up", 0L).toFloat())
        val sn = Sampler.snap.value
        val sub = listOfNotNull("↑ $up", if (sn.rxTotal > 0) "Total ↓ ${Guard.gb(sn.rxTotal)} ↑ ${Guard.gb(sn.txTotal)}" else null)
        return views(ctx, s, R.layout.widget_network, "Network", "↓ $down  ↑ $up", "performance",
            Tile("↓ $down", sub, series = hist("down"), series2 = hist("up")))
    }
}

class ClockWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val now = Date()
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val cal = java.util.Calendar.getInstance()
        val sub = listOf(SimpleDateFormat("EEEE", Locale.getDefault()).format(now), "Week ${cal.get(java.util.Calendar.WEEK_OF_YEAR)} · day ${cal.get(java.util.Calendar.DAY_OF_YEAR)}")
        val dayFrac = (cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)) / 1440f
        return views(ctx, s, R.layout.widget_clock, date, time, "", Tile(time, sub, dayFrac))
    }
}

class UptimeWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val sec = android.os.SystemClock.elapsedRealtime() / 1000
        val big = if (sec >= 86400) "${sec / 86400}d %02dh %02dm".format(sec % 86400 / 3600, sec % 3600 / 60) else "%dh %02dm".format(sec / 3600, sec % 3600 / 60)
        val boot = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() - sec * 1000))
        val left = 86400 - sec % 86400
        return views(ctx, s, R.layout.widget_uptime, "Uptime", Extra.uptime(), "performance",
            Tile(big, listOf("Booted $boot", "Next day in ${left / 3600}h ${left % 3600 / 60}m"), (sec % 86400) / 86400f))
    }
}

class StorageBarWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val (free, total) = disk()
        val share = if (total <= 0) 0 else (free * 100 / total).toInt()
        return views(ctx, s, R.layout.widget_storagebar, "Storage free", "$share% free", "files",
            Tile("$share%", listOf("free", "${Guard.gb(free)} of ${Guard.gb(total)}"), share / 100f, tone = if (share <= 10) 0xFFE53935.toInt() else 0))
    }
}

class RamFreeWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val total = p.getInt("ramTotal", 0); val free = total - p.getInt("ramUsed", 0)
        return views(ctx, s, R.layout.widget_ramfree, "RAM free", mb(free), "performance",
            Tile(mb(free), listOf("free of ${mb(total)}", "${pct(free, total)}% available"), pct(free, total) / 100f))
    }
}

class CoresWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val n = Runtime.getRuntime().availableProcessors()
        val sn = Sampler.snap.value
        val top = sn.cores.maxOfOrNull { it.mhz } ?: 0
        val sub = listOfNotNull("cores online", "CPU ${sn.cpu}%" + if (top > 0) " · up to $top MHz" else "")
        return views(ctx, s, R.layout.widget_cores, "Cores online", "$n", "performance",
            Tile("$n", sub, bars = sn.cores.map { it.usage / 100f }))
    }
}

class BrightnessWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val level = try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 0 }
        val auto = try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC } catch (e: Exception) { false }
        val pc = level * 100 / 255
        return views(ctx, s, R.layout.widget_brightness, "Brightness", "$pc%", "device",
            Tile("$pc%", listOf(if (auto) "Automatic" else "Manual", "level $level / 255"), level / 255f))
    }
}

class WifiWidget : PulseWidget() {
    @Suppress("DEPRECATION")
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        // the network name needs location permission; whatever else the system gives without it is shown, never a crash
        val located = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val info = try { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo } catch (e: Exception) { null }
        val connected = info != null && info.rssi > -127
        val ssid = info?.ssid?.trim('"').orEmpty().let { if (!located || it.isEmpty() || it == "<unknown ssid>") if (connected) "Wi-Fi" else "Not connected" else it }
        val rssi = if (connected) info!!.rssi else -100
        val level = ((rssi + 100) / 60f).coerceIn(0f, 1f)
        val std = if (connected && android.os.Build.VERSION.SDK_INT >= 30) when (info!!.wifiStandard) { 4 -> "Wi-Fi 4"; 5 -> "Wi-Fi 5"; 6 -> "Wi-Fi 6"; 7 -> "Wi-Fi 7"; else -> "" } else ""
        val ghz = if (connected && info!!.frequency > 0) "%.1f GHz".format(info.frequency / 1000f) else ""
        val ip = if (connected && info!!.ipAddress != 0) info.ipAddress.let { "${it and 255}.${it shr 8 and 255}.${it shr 16 and 255}.${it shr 24 and 255}" } else ""
        val sub = if (!connected) listOf("Turn on Wi-Fi and connect")
        else listOfNotNull(listOf(std, ghz).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }, "${info!!.linkSpeed} Mbps · $rssi dBm", ip.ifEmpty { null })
        return views(ctx, s, R.layout.widget_wifi, "Wi-Fi network", ssid, "device",
            Tile(ssid, sub, bars = listOf(.3f, .55f, .8f, 1f), lit = if (connected) 1 + (level * 3.99f).toInt() else 0))
    }
}

class SignalWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        // no phone-state permission: the carrier name and the link the system reports, and the network type when the system allows it
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val tm = ctx.getSystemService(android.telephony.TelephonyManager::class.java)
        val carrier = try { tm?.networkOperatorName.orEmpty() } catch (e: Exception) { "" }
        val type = try {
            when (tm?.dataNetworkType) { 20 -> "5G"; 13 -> "4G LTE"; 3, 5, 6, 8, 9, 10, 15 -> "3G"; 1, 2 -> "2G"; else -> "Mobile" }
        } catch (e: Exception) { "Mobile" }
        val sig = caps?.signalStrength?.takeIf { it != Int.MIN_VALUE }
        val bars = if (sig != null) ((sig + 100) / 60f * 4).toInt().coerceIn(0, 4) else if (cellular) 3 else 0
        val big = if (cellular) type else if (wifi) "Wi-Fi" else "Offline"
        val down = caps?.linkDownstreamBandwidthKbps ?: 0
        val sub = listOfNotNull(carrier.ifEmpty { null }, if (sig != null) "$sig dBm" else null, if (down > 0) "up to ${down / 1000} Mbps" else null)
        return views(ctx, s, R.layout.widget_signal, "Mobile signal", if (cellular) "Mobile data" else "Not on mobile", "device",
            Tile(big, sub.ifEmpty { listOf(if (cellular) "Mobile data" else "Not on mobile") }, bars = listOf(.3f, .55f, .8f, 1f), lit = bars))
    }
}

class BatteryHealthWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val b = Guard.battery(ctx)
        val cycles = if (b.cycles > 0) " · ${b.cycles} cycles" else ""
        val cap = if (b.designMah > 0 && b.fullMah > 0) (b.fullMah * 100f / b.designMah).coerceIn(0f, 100f) else -1f
        val sub = listOfNotNull(if (b.cycles > 0) "${b.cycles} cycles" else null, if (cap >= 0) "Capacity ${cap.toInt()}% of design (${b.fullMah} mAh)" else null, b.plug.ifEmpty { null })
        return views(ctx, s, R.layout.widget_batthealth, "Battery health", b.health + cycles, "device",
            Tile(b.health, sub, if (cap >= 0) cap / 100f else null, tone = if (cap in 0f..60f) 0xFFFFA000.toInt() else 0))
    }
}

class AppCountWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val apps = ctx.packageManager.getInstalledApplications(0)
        val user = apps.count { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        return views(ctx, s, R.layout.widget_apps, "Apps", "$user user · ${apps.size - user} system", "apps",
            Tile("${apps.size}", listOf("$user user", "${apps.size - user} system"), if (apps.isEmpty()) 0f else user / apps.size.toFloat()))
    }
}

class LastDownloadWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val name = p.getString("lastDl", "").orEmpty().ifEmpty { "None" }
        val n = p.getInt("dlActive", 0)
        return views(ctx, s, R.layout.widget_lastdl, "Last download", name, "downloads",
            Tile(name, listOf(if (n > 0) "$n downloading · ${rate(p.getLong("dlSpeed", 0L).toFloat())}" else "Nothing downloading"), if (n > 0) 1f else null))
    }
}

class NextAlarmWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val t = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        val value = if (t == null) "None" else SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(t))
        val left = if (t == null) 0L else ((t - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
        val sub = if (t == null) listOf("No alarm set") else listOf("in ${left / 60} h ${left % 60} min")
        return views(ctx, s, R.layout.widget_alarm, "Next alarm", value, "", Tile(value, sub, if (t == null) null else (1f - left / 1440f).coerceIn(0f, 1f)))
    }
}

class QuickWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle) =
        views(ctx, s, R.layout.widget_quick, "Quick action", "Toolkit", "toolkit", Tile("Toolkit", listOf("Tools, cleaners, shell")))
}

/** What a tap on a placed widget opens, in one place: clocks the phone's Clock app, dates the Calendar, batteries the system battery screen, the rest Pulse on their own page. */
object WidgetTap {
    fun skin(ctx: Context, s: Skin, id: Int): PendingIntent {
        val n = s.name
        val kind = when {
            n.contains("BATTERY") -> "battery"
            n.contains("DATE") || n.contains("CALENDAR") || n.contains("UP_NEXT") -> "calendar"
            n.contains("CLOCK") || n.contains("TIME") || n.contains("WORLD") -> "clock"
            else -> ""
        }
        return pending(ctx, kind, s.page, 2000 + s.ordinal * 7 + (id and 0xFF) * 1000)
    }

    fun face(ctx: Context, look: Int, id: Int): PendingIntent {
        val f = Faces.looks[look]
        val kind = when { f == null -> ""; f.battery -> "battery"; f.day || f.cal -> "calendar"; else -> "clock" }
        return pending(ctx, kind, "", 900 + look + (id and 0xFF) * 1000)
    }

    fun text(ctx: Context, layout: Int, page: String, id: Int): PendingIntent {
        // the clock widget opens the Clock app, the alarm widget its alarms, the battery ones the system battery screen
        val kind = when (layout) { R.layout.widget_clock -> "clock"; R.layout.widget_alarm -> "clock"; R.layout.widget_battery, R.layout.widget_batthealth -> "battery"; else -> "" }
        return pending(ctx, kind, page, layout + (id and 0xFF) * 100000)
    }

    private fun pending(ctx: Context, kind: String, page: String, req: Int): PendingIntent {
        val system = when (kind) {
            "clock" -> Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
            "calendar" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            "battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            else -> null
        }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // no such app on the phone: fall back to Pulse itself
        val i = if (system != null && system.resolveActivity(ctx.packageManager) != null) system
        else Intent(ctx, MainActivity::class.java).putExtra("start", page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
