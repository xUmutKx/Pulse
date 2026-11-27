package com.umutk.pulse.extras.launcher

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.tr
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/*
 * Task Scheduler: "open this app at this time" (once or every day). Each task is an alarm of the phone; when it goes off a notification appears and a tap opens the app (Android does not let
 * an app start another one from the background by itself, so the notification is the way in).
 */
internal class SchedTask(val id: Int, val name: String, val pkg: String, val label: String, val hour: Int, val minute: Int, val daily: Boolean, val on: Boolean)

internal object TaskSched {
    private fun sp(c: Context) = c.getSharedPreferences("win_tasks", Context.MODE_PRIVATE)

    fun load(c: Context): List<SchedTask> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { SchedTask(it.getInt("id"), it.getString("name"), it.getString("pkg"), it.getString("label"), it.getInt("h"), it.getInt("m"), it.getBoolean("daily"), it.getBoolean("on")) } }
    }.getOrDefault(emptyList())

    fun save(c: Context, list: List<SchedTask>) {
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("pkg", it.pkg).put("label", it.label).put("h", it.hour).put("m", it.minute).put("daily", it.daily).put("on", it.on)) }
        sp(c).edit().putString("list", a.toString()).apply()
        list.forEach { if (it.on) schedule(c, it) else cancel(c, it) }
    }

    private fun pending(c: Context, id: Int) = PendingIntent.getBroadcast(c, 7000 + id, Intent(c, TaskAlarmReceiver::class.java).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun nextTime(t: SchedTask): Long {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, t.hour); set(Calendar.MINUTE, t.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(c: Context, t: SchedTask) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime(t), pending(c, t.id)) }
    }

    fun cancel(c: Context, t: SchedTask) { runCatching { (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(c, t.id)) } }
}

/** The alarm of a task: a notification that opens the app; a daily task sets its next alarm. */
class TaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val t = TaskSched.load(c).firstOrNull { it.id == id && it.on } ?: return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(com.umutk.pulse.QuietChannel("pulse_tasks", "Scheduled tasks", NotificationManager.IMPORTANCE_HIGH))
        val launch = c.packageManager.getLaunchIntentForPackage(t.pkg) ?: Intent(c, LauncherActivity::class.java)
        val pi = PendingIntent.getActivity(c, 8000 + id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        runCatching {
            nm.notify(9000 + id, androidx.core.app.NotificationCompat.Builder(c, "pulse_tasks").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(t.name).setContentText(t.label)
                .setContentIntent(pi).setAutoCancel(true).setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).build())
        }
        if (t.daily) TaskSched.schedule(c, t) else TaskSched.save(c, TaskSched.load(c).map { if (it.id == id) SchedTask(it.id, it.name, it.pkg, it.label, it.hour, it.minute, false, false) else it })
    }
}

/** The Task Scheduler window: the tasks with a switch each, and a box to make a new one. */
@Composable
fun TaskSchedulerWindow() {
    val ctx = LocalContext.current
    WinGlass()
    var list by remember { mutableStateOf(TaskSched.load(ctx)) }
    var adding by remember { mutableStateOf(false) }
    fun commit(l: List<SchedTask>) { list = l; TaskSched.save(ctx, l) }
    Box1(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(winPageBg()).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Create task", "Görev oluştur")) { adding = true }
            if (list.isEmpty()) Text(tr("No tasks yet", "Henüz görev yok"), color = T.sub, fontSize = 13.sp)
            list.forEach { t ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(T.panel.copy(alpha = .3f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("%02d:%02d · ".format(t.hour, t.minute) + (if (t.daily) tr("Every day", "Her gün") else tr("Once", "Bir kez")) + " · " + t.label, color = T.sub, fontSize = 12.sp)
                    }
                    PSwitch(t.on, { on -> commit(list.map { if (it.id == t.id) SchedTask(it.id, it.name, it.pkg, it.label, it.hour, it.minute, it.daily, on) else it }) })
                    Text("✕", color = T.sub, fontSize = 16.sp, modifier = Modifier.clickable { TaskSched.cancel(ctx, t); commit(list.filter { it.id != t.id }) }.padding(start = 12.dp, end = 4.dp))
                }
            }
        }
        if (adding) NewTask({ adding = false }) { t -> adding = false; commit(list + t) }
    }
}

@Composable
private fun Box1(modifier: Modifier, content: @Composable () -> Unit) { androidx.compose.foundation.layout.Box(modifier) { content() } }

@Composable
private fun NewTask(close: () -> Unit, add: (SchedTask) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Pair<String, String>?>(null) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var daily by remember { mutableStateOf(true) }
    val apps = remember {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    WinPopup(tr("Create task", "Görev oluştur"), close) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PField(name, { name = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
            PField(q, { q = it }, tr("Find the app to open", "Açılacak uygulamayı bul"), Modifier.fillMaxWidth())
            if (pick != null) Text("✓ " + pick!!.second, color = Color.White, fontSize = 13.sp)
            else Column(Modifier.fillMaxWidth()) {
                apps.filter { q.isNotBlank() && it.second.contains(q.trim(), true) }.take(5).forEach { a -> Text(a.second, color = Color.White, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clickable { pick = a; if (name.isBlank()) name = a.second }.padding(vertical = 5.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("−", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { hour = (hour + 23) % 24 }.padding(8.dp)); Text("%02d".format(hour), color = Color.White, fontSize = 20.sp); Text("+", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { hour = (hour + 1) % 24 }.padding(8.dp))
                Text(":", color = Color.White, fontSize = 20.sp)
                Text("−", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { minute = (minute + 55) % 60 }.padding(8.dp)); Text("%02d".format(minute), color = Color.White, fontSize = 20.sp); Text("+", color = Color.White, fontSize = 20.sp, modifier = Modifier.clickable { minute = (minute + 5) % 60 }.padding(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PChip(daily, tr("Every day", "Her gün")) { daily = true }; PChip(!daily, tr("Once", "Bir kez")) { daily = false } }
            PButton(tr("OK", "Tamam")) {
                val p = pick
                if (p != null) add(SchedTask((System.currentTimeMillis() % 1_000_000).toInt(), name.ifBlank { p.second }, p.first, p.second, hour, minute, daily, true))
            }
        }
    }
}
