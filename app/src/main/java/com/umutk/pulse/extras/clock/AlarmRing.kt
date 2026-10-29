package com.umutk.pulse.extras.clock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plays an alarm: a foreground service holds the sound (the phone's alarm tone, looping, at the alarm volume), the buzz and a notification with a full-screen
 * intent, so the ring screen opens over the lock screen. It stops itself when the alarm is dismissed or snoozed, or after ten minutes.
 */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var id = -1
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var wake: android.os.PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> { finishAlarm(); return START_NOT_STICKY }
            ACTION_SNOOZE -> { snooze(); return START_NOT_STICKY }
        }
        id = intent?.getIntExtra("id", -1) ?: -1
        val item = AlarmStore.get(this, id)
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH, tr("Alarms", "Alarmlar"), NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
        wake = getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "pulse:alarm").apply { acquire(11 * 60_000L) }
        val ring = PendingIntent.getActivity(this, 0, Intent(this, AlarmRingActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun act(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AlarmRingService::class.java).setAction(a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = androidx.core.app.NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(item?.label?.ifBlank { null } ?: tr("Alarm", "Alarm")).setContentText(String.format("%02d:%02d", item?.hour ?: 0, item?.minute ?: 0))
            .setCategory(Notification.CATEGORY_ALARM).setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX).setOngoing(true)
            .setFullScreenIntent(ring, true).setContentIntent(ring)
            .addAction(0, tr("Snooze", "Ertele"), act(ACTION_SNOOZE, 1)).addAction(0, tr("Dismiss", "Kapat"), act(ACTION_DISMISS, 2)).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(NOTE, n)
        startSound(); if (item?.vibrate != false) startBuzz()
        // some phones do not open the screen from the notification: the activity is also started directly when the app may
        runCatching { startActivity(Intent(this, AlarmRingActivity::class.java).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)) }
        handler.postDelayed({ finishAlarm(missed = true) }, 10 * 60_000L)
        return START_NOT_STICKY
    }

    private fun startSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmRingService, uri); isLooping = true; prepare(); start()
            }
        }
    }

    private fun startBuzz() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0)) else { @Suppress("DEPRECATION") v.vibrate(pattern, 0) }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop(); player?.release() }; player = null
        runCatching { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() }
        wake?.let { if (it.isHeld) it.release() }; wake = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        sendBroadcast(Intent(ACTION_CLOSED).setPackage(packageName))
    }

    private fun finishAlarm(missed: Boolean = false) { AlarmEngine.afterRing(this, id); stopAll() }
    private fun snooze() { val m = AlarmStore.get(this, id)?.snoozeMin ?: 10; AlarmEngine.snooze(this, id, m); stopAll() }
    override fun onDestroy() { runCatching { player?.release() }; super.onDestroy() }

    companion object {
        const val CH = "pulse_alarm"; const val NOTE = 7201
        const val ACTION_DISMISS = "com.umutk.pulse.clock.DISMISS"; const val ACTION_SNOOZE = "com.umutk.pulse.clock.SNOOZE"; const val ACTION_CLOSED = "com.umutk.pulse.clock.CLOSED"
    }
}

/** The ring screen: over the lock screen, the time large, Snooze and Dismiss. */
class AlarmRingActivity : ComponentActivity() {
    private val closed = object : android.content.BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { finish() } }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else { @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getIntExtra("id", -1)
        androidx.core.content.ContextCompat.registerReceiver(this, closed, android.content.IntentFilter(AlarmRingService.ACTION_CLOSED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { RingScreen(AlarmStore.get(this, id)) { a -> startService(Intent(this, AlarmRingService::class.java).setAction(a)); finish() } }
    }

    override fun onDestroy() { runCatching { unregisterReceiver(closed) }; super.onDestroy() }
}

@Composable
private fun RingScreen(item: AlarmItem?, onAction: (String) -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), color = Color(0xFFB0B0B0), fontSize = 18.sp)
        Spacer(Modifier.height(18.dp))
        Text(item?.label?.ifBlank { null } ?: tr("Alarm", "Alarm"), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(64.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(32.dp)).background(Color(0xFF2A2A2A)).clickable { onAction(AlarmRingService.ACTION_SNOOZE) }.padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                Text(tr("Snooze", "Ertele") + (item?.snoozeMin?.let { " $it" } ?: ""), color = Color.White, fontSize = 18.sp, maxLines = 1)
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(32.dp)).background(Color(0xFFE5484D)).clickable { onAction(AlarmRingService.ACTION_DISMISS) }.padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                Text(tr("Dismiss", "Kapat"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
