package com.umutk.pulse.extras.launcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/**
 * While Pulse is the home app, the launcher's wallpaper is also the phone's wallpaper (so the recent apps and the lock-side previews show the same picture).
 * The wallpaper the phone had is saved first and goes back when Pulse stops being the home app (checked whenever Pulse's own app opens), when the switch is turned
 * off, or with the button in Look. Without a saved copy nothing is changed.
 */
object PhoneWall {
    private fun prefs(c: Context) = c.getSharedPreferences("pulse_phone_wall", Context.MODE_PRIVATE)
    private fun backup(c: Context) = File(c.filesDir, "phone_wall_backup.png")

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("sync", true)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("sync", on).apply(); if (!on) restore(c) }
    fun hasBackup(c: Context): Boolean = backup(c).isFile

    fun isDefaultHome(c: Context): Boolean = runCatching {
        c.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == c.packageName
    }.getOrDefault(false)

    private fun key(lookId: String): String = "$lookId|${wallpaperRes(lookId)}|${LauncherPrefs.wallFile}"

    /** The launcher is on screen: its picture becomes the phone's when Pulse is the home app (off the main thread). */
    fun sync(c: Context, lookId: String) {
        val app = c.applicationContext
        if (!enabled(app) || !isDefaultHome(app)) return
        val k = key(lookId)
        if (prefs(app).getString("key", "") == k) return
        Thread {
            runCatching {
                val wm = WallpaperManager.getInstance(app)
                // the phone's own wallpaper is kept before the first change
                if (!backup(app).isFile) {
                    val bmp = runCatching { wm.drawable?.toBitmap() }.getOrNull() ?: return@runCatching
                    backup(app).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                val own = LauncherPrefs.wallFile.takeIf { it.isNotEmpty() }?.let { File(it) }?.takeIf { it.isFile }
                val full = (if (own != null) BitmapFactory.decodeFile(own.path) else wallpaperRes(lookId)?.let { BitmapFactory.decodeResource(app.resources, it) }) ?: return@runCatching
                wm.setBitmap(full, null, true, WallpaperManager.FLAG_SYSTEM)
                prefs(app).edit().putString("key", k).apply()
            }
        }.start()
    }

    /** Puts the phone's own wallpaper back (and forgets the saved copy). */
    fun restore(c: Context) {
        val app = c.applicationContext
        val f = backup(app)
        if (!f.isFile) return
        Thread {
            runCatching {
                val bmp = BitmapFactory.decodeFile(f.path) ?: return@runCatching
                WallpaperManager.getInstance(app).setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM)
                f.delete()
                prefs(app).edit().remove("key").apply()
            }
        }.start()
    }

    /** Pulse's own app opens: when another launcher has become the home app, the phone's wallpaper returns. */
    fun restoreIfLeft(c: Context) { if (hasBackup(c) && !isDefaultHome(c)) restore(c) }
}
