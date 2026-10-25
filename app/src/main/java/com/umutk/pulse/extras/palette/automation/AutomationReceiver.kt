package com.umutk.pulse.extras.palette.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.umutk.pulse.extras.palette.PaletteStore
import com.umutk.pulse.extras.palette.PaletteStudio
import com.umutk.pulse.extras.palette.core.palette.MoodPresets

/**
 * The entry point for other apps (Tasker, Modes and Routines, MacroDroid, adb):
 *   adb shell am broadcast -a com.umutk.pulse.palette.APPLY_THEME --es theme "Nord"
 *   adb shell am broadcast -a com.umutk.pulse.palette.NEXT_PALETTE
 *   adb shell am broadcast -a com.umutk.pulse.palette.REGENERATE --ez wallpaper true
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val c = ctx.applicationContext
        when (intent.action) {
            ACTION_APPLY -> {
                val preset = MoodPresets.byName(intent.getStringExtra("theme") ?: return) ?: return
                PaletteStudio.save(c, PaletteStore.active(c).copy(palette = preset))
                enqueueRebuild(c, false)
            }
            ACTION_NEXT -> {
                val cur = PaletteStore.active(c)
                val list = MoodPresets.all
                val next = list[(list.indexOfFirst { it.name == cur.palette.name } + 1).mod(list.size)]
                PaletteStudio.save(c, cur.copy(palette = next))
                enqueueRebuild(c, false)
            }
            ACTION_REGENERATE -> enqueueRebuild(c, intent.getBooleanExtra("wallpaper", false))
            Intent.ACTION_BOOT_COMPLETED -> if (PaletteStore.dayNightAuto(c) || PaletteStore.followWallpaper(c)) ThemeWorker.schedule(c)
        }
    }

    private fun enqueueRebuild(ctx: Context, followWallpaper: Boolean) {
        val req = OneTimeWorkRequestBuilder<ThemeWorker>().setInputData(Data.Builder().putBoolean(ThemeWorker.KEY_FOLLOW_WALLPAPER, followWallpaper).build()).build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    companion object {
        const val ACTION_APPLY = "com.umutk.pulse.palette.APPLY_THEME"
        const val ACTION_NEXT = "com.umutk.pulse.palette.NEXT_PALETTE"
        const val ACTION_REGENERATE = "com.umutk.pulse.palette.REGENERATE"
    }
}
