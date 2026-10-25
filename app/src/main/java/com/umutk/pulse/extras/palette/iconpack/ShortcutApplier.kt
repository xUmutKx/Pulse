package com.umutk.pulse.extras.palette.iconpack

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.umutk.pulse.extras.palette.AppInfo

/**
 * Universal fallback: pin a themed shortcut to the home screen.
 * Works on every launcher, including ones with no icon-pack support at all,
 * and needs no root, no Xposed and no rebuild.
 */
object ShortcutApplier {

    fun canPin(ctx: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)

    fun pin(ctx: Context, app: AppInfo, icon: Bitmap, label: String = app.label): Boolean {
        val launch = ctx.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                setClassName(app.packageName, app.activityName)
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val info = ShortcutInfoCompat.Builder(ctx, "palette_" + app.key)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithBitmap(icon))
            .setIntent(launch)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
}
