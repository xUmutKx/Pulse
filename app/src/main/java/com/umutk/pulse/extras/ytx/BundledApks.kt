package com.umutk.pulse.extras.ytx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.umutk.pulse.tr
import java.io.File

/**
 * Apps that the open-source build carries inside itself (built by the workflow from their source in extras/, signed with Pulse's key): each is an app of its own with its own
 * icon. Pulse installs it once (its own installer window), opens it afterwards, and the entry it had on the home screen takes itself away then.
 */
class BundledApk(val pkg: String, val asset: String, val file: String, val minSdk: Int = 21)

object BundledApks {
    val ytx = BundledApk("com.umutk.pulse.ytx", "extras/ytxtube.apk", "ytxtube.apk")
    val athenaeum = BundledApk("com.umutk.pulse.athenaeum", "extras/athenaeum.apk", "athenaeum.apk", minSdk = 26)

    /** True in the open-source build when the workflow put the app inside (and the phone is new enough for it). */
    fun bundled(c: Context, a: BundledApk): Boolean =
        com.umutk.pulse.BuildConfig.APK_INSTALL && android.os.Build.VERSION.SDK_INT >= a.minSdk && runCatching { c.assets.open(a.asset).use { true } }.getOrDefault(false)

    fun installed(c: Context, a: BundledApk): Boolean = runCatching { c.packageManager.getPackageInfo(a.pkg, 0); true }.getOrDefault(false)

    /** Opens the app when it is installed; otherwise hands its APK to Pulse's installer window. */
    fun openOrInstall(c: Context, a: BundledApk) {
        if (installed(c, a)) {
            val i = c.packageManager.getLaunchIntentForPackage(a.pkg)
            if (i != null) { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        }
        install(c, a)
    }

    fun install(c: Context, a: BundledApk) {
        runCatching {
            val dir = File(c.cacheDir, "installer").apply { mkdirs() }
            val f = File(dir, a.file)
            c.assets.open(a.asset).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            c.startActivity(com.umutk.pulse.features.Downloads.openIntent(c, f).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(c, tr("Could not open the installer", "Yükleyici açılamadı"), Toast.LENGTH_LONG).show() }
    }

    /** The home-screen entry of Pulse for the app: opens it (installing first when needed); once the app is there it is switched off, the app has its own icon. */
    fun launchFromIcon(activity: android.app.Activity, a: BundledApk, self: Class<*>) {
        val was = installed(activity, a)
        openOrInstall(activity, a)
        if (was) runCatching { activity.packageManager.setComponentEnabledSetting(ComponentName(activity, self), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP) }
        activity.finish()
    }
}
