package com.umutk.pulse.features

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.umutk.pulse.extras.blackout.Privilege
import java.util.concurrent.TimeUnit

/**
 * Android marks an accessibility service "Not working" when it crashed or was killed, and leaves it that way until someone switches it off and on again.
 * With the power to write the setting (the one-time WRITE_SECURE_SETTINGS grant, root, or Shizuku) Pulse does that itself: a service that is in the enabled list but
 * is not running is taken out and put back. The first time the power is there, Pulse's own services are switched on (they stay idle until a feature uses them).
 */
object A11yRepair {
    private const val PREFS = "a11y_repair"
    private const val WORK = "a11y-repair"
    private val NAMES = listOf(
        "com.umutk.pulse.features.AutoClickService",
        "com.umutk.pulse.features.CoverAccessibilityService",
        "com.umutk.pulse.applock.LockService",
        "com.umutk.pulse.extras.keyboard.AccessKeyboardService",
        "com.umutk.pulse.extras.blackout.AppWatch",
    )

    private fun comp(c: Context, n: String) = "${c.packageName}/$n"
    private fun current(c: Context) = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    private fun list(s: String) = s.split(':').filter { it.isNotBlank() && it != "null" }

    private fun canWrite(c: Context) = c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Writes the enabled list: directly with the grant, else through root or Shizuku (asked once per run; nothing happens when neither is there). */
    private fun write(c: Context, v: String): Boolean {
        if (canWrite(c)) return runCatching {
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, v)
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1); true
        }.getOrDefault(false)
        if (Privilege.mode == Privilege.Mode.None) Privilege.detect()
        if (Privilege.mode == Privilege.Mode.None) return false
        Privilege.run("settings put secure enabled_accessibility_services '${v.ifEmpty { "null" }}'; settings put secure accessibility_enabled 1")
        return true
    }

    private fun hasPower(c: Context) = canWrite(c) || Privilege.mode != Privilege.Mode.None || runCatching { Privilege.detect() != Privilege.Mode.None }.getOrDefault(false)

    private fun running(c: Context): Set<String> =
        (c.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)
            ?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.mapNotNull { it.resolveInfo?.serviceInfo?.let { s -> "${s.packageName}/${s.name}" } }?.toSet().orEmpty()

    /** One pass: switch the services on the first time, then put back the ones that are listed but not running. Returns how many were repaired. */
    @Synchronized
    fun run(c0: Context): Int {
        val c = c0.applicationContext
        val sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!hasPower(c)) return 0
        var cur = list(current(c))
        // the first time the power is there: Pulse's services are on by default
        if (!sp.getBoolean("defaulted", false)) {
            val add = NAMES.map { comp(c, it) }.filter { it !in cur }
            if (add.isNotEmpty() && write(c, (cur + add).joinToString(":"))) cur = cur + add
            sp.edit().putBoolean("defaulted", true).apply()
            return 0
        }
        val up = running(c)
        val dead = NAMES.map { comp(c, it) }.filter { it in cur && it !in up }
        if (dead.isEmpty()) return 0
        // off, a moment, on: that is what the person does in the settings to clear "Not working"
        if (!write(c, cur.filter { it !in dead }.joinToString(":"))) return 0
        Thread.sleep(800)
        write(c, (list(current(c)) + dead).distinct().joinToString(":"))
        return dead.size
    }

    class Worker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
        override suspend fun doWork(): Result { runCatching { run(applicationContext) }; return Result.success() }
    }

    /** Checked at every start of the app and then every quarter of an hour. */
    fun schedule(c: Context) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<Worker>(15, TimeUnit.MINUTES).build())
        Thread { runCatching { Thread.sleep(4000); run(c) } }.start()
    }
}
