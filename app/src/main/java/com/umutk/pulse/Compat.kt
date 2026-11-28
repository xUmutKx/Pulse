package com.umutk.pulse

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File
import java.net.URLConnection

/** Small switches for the calls that older Androids (5 and up) do not have: each gives the same answer as the new call, or the nearest old one. */
object Compat {
    /** "Display over other apps" is a permission from Android 6; before it every app may draw. */
    fun canOverlay(c: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)

    /** All-files access exists from Android 11; before it the storage permission is the nearest thing. */
    fun isStorageManager() = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()

    @Suppress("DEPRECATION")
    fun longVersion(i: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) i.longVersionCode else i.versionCode.toLong()

    @Suppress("DEPRECATION")
    fun signers(i: PackageInfo): Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) i.signingInfo?.apkContentsSigners else i.signatures

    fun contentLength(c: URLConnection): Long = if (Build.VERSION.SDK_INT >= 24) c.contentLengthLong else c.contentLength.toLong()

    /** A symbolic link: its canonical path is not where its name says it is. */
    fun isLink(f: File): Boolean = runCatching { f.canonicalPath != File(f.absoluteFile.parentFile?.canonicalFile ?: return false, f.name).path }.getOrDefault(false)

    /** Tile subtitles exist from Android 10; before it nothing is shown under the name. */
}

var android.service.quicksettings.Tile.subtitleCompat: CharSequence?
    @androidx.annotation.RequiresApi(24) get() = if (Build.VERSION.SDK_INT >= 29) subtitle else null
    @androidx.annotation.RequiresApi(24) set(v) { if (Build.VERSION.SDK_INT >= 29) subtitle = v }

/** InputStream.readNBytes is Java 11 (Android 13+ only; older phones crash with NoSuchMethodError): reads up to [n] bytes the old way. */
fun java.io.InputStream.readUpTo(n: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(minOf(n, 64 * 1024))
    val buf = ByteArray(8192)
    var left = n
    while (left > 0) {
        val r = read(buf, 0, minOf(buf.size, left))
        if (r < 0) break
        out.write(buf, 0, r); left -= r
    }
    return out.toByteArray()
}

/**
 * RemoteViews.setColorStateList, setViewLayoutWidth and setViewLayoutHeight are Android 12+ (an older phone crashed with NoSuchMethodError in a widget's onUpdate).
 * Before Android 12 a background tint becomes the plain colour, a progress tint and a layout size are left as the layout has them.
 */
fun android.widget.RemoteViews.tintList(viewId: Int, method: String, csl: android.content.res.ColorStateList?) {
    if (android.os.Build.VERSION.SDK_INT >= 31) { setColorStateList(viewId, method, csl); return }  // api-ok
    if (csl != null && method == "setBackgroundTintList") setInt(viewId, "setBackgroundColor", csl.defaultColor)
}
fun android.widget.RemoteViews.layoutWidth(viewId: Int, size: Float, unit: Int) { if (android.os.Build.VERSION.SDK_INT >= 31) setViewLayoutWidth(viewId, size, unit) }  // api-ok
fun android.widget.RemoteViews.layoutHeight(viewId: Int, size: Float, unit: Int) { if (android.os.Build.VERSION.SDK_INT >= 31) setViewLayoutHeight(viewId, size, unit) }  // api-ok
