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
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.widget.RemoteViews
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.Extra
import com.umutk.pulse.Guard
import com.umutk.pulse.MainActivity
import com.umutk.pulse.Opt
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
enum class WidgetStyle(val label: String, val bg: Int, val title: Int, val value: Int, val titleBg: Int = 0) {
    MATERIAL("Material", R.drawable.widget_material, 0xFF49454F.toInt(), 0xFF1D1B20.toInt()),
    MATERIAL_FLAT("Material flat", R.drawable.widget_material_flat, 0xFF625B71.toInt(), 0xFF1D1B20.toInt()),
    AMOLED("AMOLED", R.drawable.widget_amoled, 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt()),
    WIN11("Windows 11", R.drawable.widget_win11, 0xFFCCCCCC.toInt(), 0xFFFFFFFF.toInt()),
    WIN10("Windows 10", R.drawable.widget_win10, 0xFFCCE4F7.toInt(), 0xFFFFFFFF.toInt()),
    WIN7("Windows 7", R.drawable.widget_win7, 0xFF9FD3FF.toInt(), 0xFFFFFFFF.toInt()),
    WIN95("Windows 95", R.drawable.widget_win95, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), R.drawable.widget_win95_title),
    XP("Windows XP", R.drawable.widget_xp, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), R.drawable.widget_xp_title);
}

/** Which look each widget instance wears, kept per appWidgetId in SharedPreferences. */
object WidgetStyles {
    private fun prefs(c: Context) = c.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    fun of(c: Context, id: Int): WidgetStyle =
        WidgetStyle.values().firstOrNull { it.name == prefs(c).getString("style_$id", null) } ?: WidgetStyle.MATERIAL

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

    /** Pushes the saved values to each widget on the home screen, each in its own style. */
    private fun refresh(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        all.forEach { w ->
            mgr.getAppWidgetIds(ComponentName(c, w.javaClass)).forEach { id ->
                mgr.updateAppWidget(id, w.content(c, WidgetStyles.of(c, id)))
            }
        }
    }

    val all: List<PulseWidget> by lazy {
        listOf(
            UsageWidget(), BatteryWidget(), StorageWidget(), DownloadsWidget(), MemoryWidget(),
            TempWidget(), NetworkWidget(), ClockWidget(), UptimeWidget(), StorageBarWidget(),
            RamFreeWidget(), CoresWidget(), BrightnessWidget(), WifiWidget(), SignalWidget(),
            BatteryHealthWidget(), AppCountWidget(), LastDownloadWidget(), NextAlarmWidget(), QuickWidget(),
        )
    }
}

/** Base of the 20 home-screen widgets: each one shows a title and a value; a tap opens Pulse on [page]. */
abstract class PulseWidget : AppWidgetProvider() {
    /** The RemoteViews for this widget in [s], built from its layout and the latest values. */
    abstract fun content(ctx: Context, s: WidgetStyle): RemoteViews

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, content(ctx, WidgetStyles.of(ctx, it))) }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) = WidgetStyles.remove(ctx, ids)

    protected fun views(ctx: Context, s: WidgetStyle, layout: Int, title: String, value: String, page: String = ""): RemoteViews =
        RemoteViews(ctx.packageName, layout).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.value, value)
            setInt(R.id.root, "setBackgroundResource", s.bg)
            setTextColor(R.id.title, s.title)
            setTextColor(R.id.value, s.value)
            if (s.titleBg != 0) setInt(R.id.title, "setBackgroundResource", s.titleBg)
            val open = Intent(ctx, MainActivity::class.java).putExtra("start", page)
            setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, layout, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }

    protected fun pct(a: Int, b: Int) = if (b <= 0) 0 else a * 100 / b

    /** Free and total bytes of the data partition, the one the app's storage pages read. */
    protected fun disk(): Pair<Long, Long> = try {
        StatFs(Environment.getDataDirectory().path).let { it.availableBytes to it.totalBytes }
    } catch (e: Exception) { 0L to 0L }
}

class UsageWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val ram = pct(p.getInt("ramUsed", 0), p.getInt("ramTotal", 0))
        return views(ctx, s, R.layout.widget_usage, "CPU and memory", "CPU ${p.getInt("cpu", 0)}% · RAM $ram%", "performance")
    }
}

class BatteryWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val charging = if (p.getBoolean("charging", false)) " · charging" else ""
        return views(ctx, s, R.layout.widget_battery, "Battery", "${p.getInt("bat", 0)}%$charging", "device")
    }
}

class StorageWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val (free, total) = disk()
        return views(ctx, s, R.layout.widget_storage, "Storage", "${Guard.gb(free)} free of ${Guard.gb(total)}", "files")
    }
}

class DownloadsWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        return views(ctx, s, R.layout.widget_downloads, "Downloads", "${p.getInt("dlActive", 0)} active · ${rate(p.getLong("dlSpeed", 0L).toFloat())}", "downloads")
    }
}

class MemoryWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        return views(ctx, s, R.layout.widget_memory, "Memory", "${mb(p.getInt("ramUsed", 0))} of ${mb(p.getInt("ramTotal", 0))}", "performance")
    }
}

class TempWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        Opt.load(ctx)
        val t = WidgetData.prefs(ctx).getFloat("temp", 0f)
        return views(ctx, s, R.layout.widget_temp, "Temperature", if (t > 0) Opt.temp(t) else "—", "performance")
    }
}

class NetworkWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        val down = rate(p.getLong("down", 0L).toFloat())
        val up = rate(p.getLong("up", 0L).toFloat())
        return views(ctx, s, R.layout.widget_network, "Network", "↓ $down  ↑ $up", "performance")
    }
}

class ClockWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val now = Date()
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
        return views(ctx, s, R.layout.widget_clock, date, SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
    }
}

class UptimeWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle) = views(ctx, s, R.layout.widget_uptime, "Uptime", Extra.uptime(), "performance")
}

class StorageBarWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val (free, total) = disk()
        val share = if (total <= 0) 0 else (free * 100 / total).toInt()
        val filled = share / 10
        val bar = "█".repeat(filled) + "░".repeat(10 - filled)
        return views(ctx, s, R.layout.widget_storagebar, "Storage free", "$bar $share%", "files")
    }
}

class RamFreeWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = WidgetData.prefs(ctx)
        return views(ctx, s, R.layout.widget_ramfree, "RAM free", mb(p.getInt("ramTotal", 0) - p.getInt("ramUsed", 0)), "performance")
    }
}

class CoresWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle) =
        views(ctx, s, R.layout.widget_cores, "Cores online", "${Runtime.getRuntime().availableProcessors()}", "performance")
}

class BrightnessWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val level = try { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) } catch (e: Exception) { 0 }
        return views(ctx, s, R.layout.widget_brightness, "Brightness", "${level * 100 / 255}%", "device")
    }
}

class WifiWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        // the network name needs location permission; without it the widget just says Wi-Fi
        val name = if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) "Wi-Fi" else {
            val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.connectionInfo?.ssid?.trim('"').orEmpty().let { if (it.isEmpty() || it == "<unknown ssid>") "Not connected" else it }
        }
        return views(ctx, s, R.layout.widget_wifi, "Wi-Fi network", name, "device")
    }
}

class SignalWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        // the signal type needs phone-state permission, which Pulse does not ask for; this shows the mobile link only
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val cellular = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        return views(ctx, s, R.layout.widget_signal, "Mobile signal", if (cellular) "Mobile data" else "Not on mobile", "device")
    }
}

class BatteryHealthWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val b = Guard.battery(ctx)
        val cycles = if (b.cycles > 0) " · ${b.cycles} cycles" else ""
        return views(ctx, s, R.layout.widget_batthealth, "Battery health", b.health + cycles, "device")
    }
}

class AppCountWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val apps = ctx.packageManager.getInstalledApplications(0)
        val user = apps.count { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
        return views(ctx, s, R.layout.widget_apps, "Apps", "$user user · ${apps.size - user} system", "apps")
    }
}

class LastDownloadWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val name = WidgetData.prefs(ctx).getString("lastDl", "").orEmpty().ifEmpty { "None" }
        return views(ctx, s, R.layout.widget_lastdl, "Last download", name, "downloads")
    }
}

class NextAlarmWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val t = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        val value = if (t == null) "None" else SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(t))
        return views(ctx, s, R.layout.widget_alarm, "Next alarm", value, "")
    }
}

class QuickWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle) = views(ctx, s, R.layout.widget_quick, "Quick action", "Toolkit", "toolkit")
}
