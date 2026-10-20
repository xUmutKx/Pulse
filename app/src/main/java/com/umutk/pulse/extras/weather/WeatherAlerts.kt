package com.umutk.pulse.extras.weather

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.umutk.pulse.R
import com.umutk.pulse.tr
import com.umutk.pulse.widgets.WeatherWidgets
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext

/** Every 3 hours: a storm, heavy snow or heavy rain in the next 6 hours at the saved place gives one notice. */
class WeatherAlertWorker(private val app: Context, params: WorkerParameters) : CoroutineWorker(app, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        if (!WeatherStore.alertsOn(app)) return@withContext androidx.work.ListenableWorker.Result.success()
        val place = WeatherStore.savedPlace(app) ?: return@withContext androidx.work.ListenableWorker.Result.success()
        val raw = runCatching { WeatherApi.forecast(place.lat, place.lon) }.getOrNull()
            ?: return@withContext androidx.work.ListenableWorker.Result.success()
        val aqi = WeatherStore.cached(app)?.aqi ?: -1
        WeatherStore.saveCache(app, place.name, raw, aqi)
        val f = WeatherApi.parse(place.name, raw, System.currentTimeMillis(), aqi)
        val storm = f.hours.take(6).firstOrNull { WeatherApi.severe(it.code) }
        if (storm != null && storm.time != WeatherStore.lastAlert(app)) {
            WeatherStore.setLastAlert(app, storm.time)
            notify(app, WeatherApi.condition(storm.code) + " · " + WeatherFmt.clock(storm.time))
        }
        WeatherWidgets.refreshAll(app)
        androidx.work.ListenableWorker.Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(c: Context, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, tr("Weather alerts", "Hava uyarıları"), NotificationManager.IMPORTANCE_HIGH))
        // Android 13+ drops notices without the permission; the screen asks for it when the alert is switched on
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            c, 9200, Intent().setComponent(ComponentName(c, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(R.drawable.wi_weather_bolt)
            .setContentTitle(tr("Severe weather soon", "Yakında sert hava"))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(c).notify(NOTE_ID, n)
    }

    private companion object {
        const val CH = "weather_alerts"
        const val NOTE_ID = 9201
    }
}

/** Switches the background alert on or off; off cancels the periodic job. */
object WeatherAlerts {
    private const val NAME = "weather_alerts"

    fun set(c: Context, on: Boolean) {
        WeatherStore.setAlertsOn(c, on)
        val wm = WorkManager.getInstance(c)
        if (on) {
            val req = PeriodicWorkRequestBuilder<WeatherAlertWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}
