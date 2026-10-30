package com.umutk.pulse.applock

import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import java.util.Calendar

/**
 * A daily time limit per app. How it works: the usage-access permission tells how long an app was in front today; the App lock accessibility service sees when
 * an app comes to the front; when its limit is used up the phone is sent back to the home screen with a short message. The limit starts again at midnight.
 * A limited app can still run in the background (music, downloads); only being in front is counted and stopped.
 */
object AppLimits {
    private fun sp(c: Context) = c.getSharedPreferences("applimits", Context.MODE_PRIVATE)
    fun minutes(c: Context, pkg: String) = sp(c).getInt(pkg, 0)
    fun set(c: Context, pkg: String, min: Int) { sp(c).edit().apply { if (min > 0) putInt(pkg, min) else remove(pkg) }.apply(); cache.clear() }
    fun any(c: Context) = sp(c).all.isNotEmpty()

    private val cache = HashMap<String, Pair<Long, Boolean>>()

    /** Foreground time of [pkg] today in minutes, from the usage stats. */
    fun usedToday(c: Context, pkg: String): Int {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val ms = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis()).filter { it.packageName == pkg }.sumOf { it.totalTimeInForeground }
        return (ms / 60_000L).toInt()
    }

    /** True when [pkg] has a limit and today's time is used up (looked up at most every 20 seconds). */
    fun exceeded(c: Context, pkg: String): Boolean {
        val lim = minutes(c, pkg)
        if (lim <= 0) return false
        val now = System.currentTimeMillis()
        cache[pkg]?.let { if (now - it.first < 20_000L) return it.second }
        val over = runCatching { usedToday(c, pkg) >= lim }.getOrDefault(false)
        cache[pkg] = now to over
        return over
    }
}

/** The limit of one app: off, or 15 minutes to 3 hours a day. */
@Composable
fun AppLimitDialog(pkg: String, label: String, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cur by remember { mutableIntStateOf(AppLimits.minutes(ctx, pkg)) }
    WinAlert(onDismissRequest = onDismiss, title = { Text(label) },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Daily limit", "Günlük sınır"), color = T.sub, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to tr("Off", "Kapalı"), 15 to "15 min", 30 to "30 min", 60 to "1 h", 90 to "1.5 h", 120 to "2 h", 180 to "3 h").forEach { (m, n) ->
                        PChip(cur == m, n) { cur = m; AppLimits.set(ctx, pkg, m) }
                    }
                }
                Text(tr("When the time is up the app is closed and the home screen opens, until midnight. It needs the usage access permission and the App lock helper to be on. Playing music or downloading in the background is not stopped.",
                    "Süre dolunca uygulama kapatılır ve ana ekran açılır, gece yarısına kadar. Kullanım erişimi izni ve Uygulama kilidi yardımcısının açık olması gerekir. Arka planda müzik çalması ve indirme durmaz."), color = T.sub, fontSize = 12.sp)
                if (cur > 0) Text(tr("Today: ", "Bugün: ") + AppLimits.usedToday(ctx, pkg) + " / $cur min", color = T.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = { Text(tr("Done", "Tamam"), color = T.text, fontSize = 15.sp, modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp)) })
}
