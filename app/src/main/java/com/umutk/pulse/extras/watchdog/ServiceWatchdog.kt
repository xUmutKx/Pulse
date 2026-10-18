package com.umutk.pulse.extras.watchdog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Restarts player and ambient services that Android killed while the user still wants them running.
 * The user's intent is stored per service class; a service that stops on purpose (user stop, or its own end) must call [setWanted] with false.
 */
object ServiceWatchdog {
    private const val TAG = "ServiceWatchdog"
    private const val PREFS = "service_watchdog"
    private const val KEY_WANTED = "wanted"
    private const val WORK = "service-watchdog"

    fun isWanted(c: Context, cls: Class<out Service>): Boolean = wantedNames(c).contains(cls.name)

    /** Call with true when the user starts a service, with false when it stops on purpose. */
    fun setWanted(c: Context, cls: Class<out Service>, wanted: Boolean) {
        val names = HashSet(wantedNames(c))
        if (wanted) names += cls.name else names -= cls.name
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_WANTED, names).apply()
        if (wanted) {
            schedule(c)
        } else {
            ServiceHealth.clear(c, cls)
            if (names.isEmpty()) WorkManager.getInstance(c).cancelUniqueWork(WORK)
        }
    }

    /** One periodic check, kept across reboots. KEEP leaves an existing schedule alone. */
    private fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    private fun wantedNames(c: Context): Set<String> =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_WANTED, emptySet()) ?: emptySet()

    private fun wantedClasses(c: Context): List<Class<out Service>> =
        wantedNames(c).mapNotNull { name -> runCatching { Class.forName(name).asSubclass(Service::class.java) }.getOrNull() }

    /**
     * Runs in the background. Android 12+ refuses a foreground start from the background in some cases;
     * a refused start is logged and the next check tries again.
     */
    class CheckWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
        override suspend fun doWork(): Result {
            val c = applicationContext
            for (cls in wantedClasses(c)) {
                if (!ServiceHealth.isStale(c, cls)) continue
                Log.w(TAG, "restarting ${cls.simpleName}")
                try {
                    ContextCompat.startForegroundService(c, Intent(c, cls))
                } catch (e: IllegalStateException) {
                    // ForegroundServiceStartNotAllowedException extends this
                    Log.w(TAG, "start refused for ${cls.simpleName}: ${e.message}")
                } catch (e: SecurityException) {
                    Log.w(TAG, "start denied for ${cls.simpleName}: ${e.message}")
                }
            }
            return Result.success()
        }
    }
}
