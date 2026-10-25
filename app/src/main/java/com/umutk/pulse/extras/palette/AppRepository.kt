package com.umutk.pulse.extras.palette

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val brandColor: Int = 0,
    val system: Boolean = false
) {
    val component: String get() = "ComponentInfo{$packageName/$activityName}"
    val key: String get() = "$packageName/$activityName"
    val safeName: String
        get() = (packageName + "_" + activityName)
            .lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
}

/**
 * Launcher-visible app inventory. Uses LauncherApps (multi-profile aware, work
 * apps included) with a PackageManager fallback, and never keeps drawables in
 * memory — the icon cache owns bitmaps, this owns metadata only.
 */
class AppRepository(private val ctx: Context) {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    suspend fun load(force: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!force && _apps.value.isNotEmpty()) return@withContext _apps.value
        val result = runCatching { viaLauncherApps() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: viaPackageManager()
        val sorted = result.distinctBy { it.key }.sortedBy { it.label.lowercase() }
        _apps.value = sorted
        sorted
    }

    private fun viaLauncherApps(): List<AppInfo> {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val user: UserHandle = Process.myUserHandle()
        return la.getActivityList(null, user).map {
            AppInfo(
                packageName = it.componentName.packageName,
                activityName = it.componentName.className,
                label = it.label.toString(),
                system = false
            )
        }
    }

    private fun viaPackageManager(): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map {
            AppInfo(
                packageName = it.activityInfo.packageName,
                activityName = it.activityInfo.name,
                label = it.loadLabel(pm).toString(),
                system = (it.activityInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
    }

    fun loadIcon(app: AppInfo): Drawable? = runCatching {
        val pm = ctx.packageManager
        pm.getActivityIcon(android.content.ComponentName(app.packageName, app.activityName))
    }.getOrElse {
        runCatching { ctx.packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }

    /** Small, curated set used for the live launcher preview. */
    fun previewSet(all: List<AppInfo>, count: Int = 20): List<AppInfo> {
        val priority = listOf(
            "whatsapp", "instagram", "tiktok", "telegram", "spotify", "youtube",
            "chrome", "gmail", "camera", "gallery", "photos", "maps", "settings",
            "phone", "messages", "pinterest", "discord", "x", "twitter", "reddit"
        )
        val picked = LinkedHashSet<AppInfo>()
        priority.forEach { p ->
            all.firstOrNull { it.packageName.contains(p, true) || it.label.contains(p, true) }
                ?.let { picked += it }
        }
        all.forEach { if (picked.size < count) picked += it }
        return picked.take(count)
    }
}
