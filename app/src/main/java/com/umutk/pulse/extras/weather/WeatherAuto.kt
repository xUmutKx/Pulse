package com.umutk.pulse.extras.weather

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.umutk.pulse.widgets.WeatherWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Every 30 minutes, in the background: the forecast of the saved place is read again, so the page and the widgets open with fresh numbers. */
class WeatherRefreshWorker(private val app: Context, params: WorkerParameters) : CoroutineWorker(app, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
        val place = WeatherStore.savedPlace(app) ?: return@withContext androidx.work.ListenableWorker.Result.success()
        runCatching {
            val raw = WeatherApi.forecast(place.lat, place.lon)
            val aqi = WeatherApi.airQuality(place.lat, place.lon)
            WeatherStore.saveCache(app, place.name, raw, aqi, WeatherApi.extras(place.lat, place.lon, WeatherStore.source(app) == "mgm", WeatherStore.combine(app)))
            WeatherWidgets.refreshAll(app)
        }
        // the transparent clock-weather widget keeps its own copy per place
        runCatching { com.umutk.pulse.widgets.WeatherClearWidget().fetchAll(app) }
        androidx.work.ListenableWorker.Result.success()
    }
}

object WeatherAuto {
    private const val NAME = "weather_refresh"

    /** Queues the periodic refresh once (an existing one is kept). */
    fun ensure(c: Context) {
        val req = PeriodicWorkRequestBuilder<WeatherRefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
