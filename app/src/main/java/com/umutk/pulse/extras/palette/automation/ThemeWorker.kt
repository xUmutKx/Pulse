package com.umutk.pulse.extras.palette.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.umutk.pulse.extras.palette.PaletteStore
import com.umutk.pulse.extras.palette.PaletteStudio
import com.umutk.pulse.extras.palette.core.palette.PaletteExtractor
import com.umutk.pulse.extras.palette.widget.WidgetRefresher
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Background regeneration: day / night switching, wallpaper-follow palettes and a warm-up of the icon cache so the launcher gets its icons at once.
 * WorkManager keeps to Doze and battery saver; no service, no wake lock.
 */
class ThemeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val spec = PaletteStore.active(c)
        var next = spec
        if (spec.dayNight || PaletteStore.dayNightAuto(c)) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val night = hour < 7 || hour >= 19
            next = next.copy(brightness = if (night) -0.10f else 0.04f, backgroundIndex = if (night) 0 else next.palette.size - 1)
        }
        if (inputData.getBoolean(KEY_FOLLOW_WALLPAPER, false) || PaletteStore.followWallpaper(c)) {
            PaletteExtractor.fromWallpaper(c, next.palette.size.coerceAtLeast(3))?.let { next = next.copy(palette = it) }
        }
        if (next != spec) {
            PaletteStudio.save(c, next)
            WidgetRefresher.refresh(c, next)
        }
        val apps = PaletteStudio.apps(c).load(force = true)
        val engine = PaletteStudio.engine(c)
        engine.prepareLuminanceRanks(apps)
        engine.renderAll(apps.take(MAX_WARM), next, 192, PaletteStore.overrides(c))
        return Result.success()
    }

    companion object {
        const val KEY_FOLLOW_WALLPAPER = "follow_wallpaper"
        private const val MAX_WARM = 120
        private const val WORK = "palette_theme_worker"

        fun schedule(ctx: Context, hours: Long = 6) {
            val req = PeriodicWorkRequestBuilder<ThemeWorker>(hours, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(WORK) }
    }
}
