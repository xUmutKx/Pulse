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
}
