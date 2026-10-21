package com.umutk.pulse

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * With root (or Shizuku) Pulse gives itself the permissions its features need, so nothing asks and nothing stays silent
 * (notifications, music and pictures, all files, display over apps, usage access, installing apps). Camera, location,
 * contacts, SMS and the like are not touched: they are asked for when the feature that needs them is used.
 */
object SelfGrant {
    private val runtime = buildList {
        if (Build.VERSION.SDK_INT >= 33) add("android.permission.POST_NOTIFICATIONS")
        addAll(listOf("android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"))
    }
    // app ops, set once per version: all files, display over other apps, usage access, installing apps, writing system settings
    private val ops = listOf("MANAGE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW", "GET_USAGE_STATS", "REQUEST_INSTALL_PACKAGES", "WRITE_SETTINGS")

    /** Grants what is missing. Runs the shell, so call it off the main thread. Returns the permissions that were granted now. */
    fun run(c: Context): List<String> {
        if (Shell.root != true && Shell.shizuku != 2) return emptyList()
        val pkg = c.packageName
        val done = mutableListOf<String>()
        runtime.filter { c.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.forEach { p ->
            Shell.run("pm grant $pkg $p")
            if (c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) done += p
        }
        val sp = c.getSharedPreferences("p", Context.MODE_PRIVATE)
        val version = runCatching { c.packageManager.getPackageInfo(pkg, 0).longVersionCode }.getOrDefault(0L)
        if (sp.getLong("self_grant_ops", -1L) != version) {
            ops.forEach { Shell.run("appops set $pkg $it allow") }
            sp.edit().putLong("self_grant_ops", version).apply()
        }
        return done
    }
}
