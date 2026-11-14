package com.umutk.pulse.features

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.umutk.pulse.extras.blackout.Privilege

/**
 * Removes an app. With root (or Shizuku) it goes straight away with "pm uninstall", with no question from Android; without them Android's own uninstall window
 * opens and the result comes when the system reports the app gone. [done] is called on the main thread with true when the app is no longer on the phone.
 */
object AppUninstall {
    fun run(ctx: Context, pkg: String, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            var mode = Privilege.mode
            if (mode == Privilege.Mode.None) mode = runCatching { Privilege.detect() }.getOrDefault(Privilege.Mode.None)
            var ok = false
            if (mode != Privilege.Mode.None) {
                val out = Privilege.run("pm uninstall $pkg").text
                ok = out.contains("Success", true) || !installed(app, pkg)
                // a system app has no full removal: it is taken off the user's profile instead
                if (!ok) ok = Privilege.run("pm uninstall --user 0 $pkg").text.contains("Success", true)
            }
            main.post {
                if (ok) done(true) else system(app, pkg, done)
            }
        }.start()
    }

    private fun installed(c: Context, pkg: String) = runCatching { c.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Android's own window; the callback comes when the package is removed, or false when the window was left with the app still there. */
    private fun system(app: Context, pkg: String, done: (Boolean) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                if (i.data?.schemeSpecificPart == pkg) { runCatching { app.unregisterReceiver(this) }; done(true) }
            }
        }
        val f = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        runCatching { if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f) }
        runCatching { app.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { app.unregisterReceiver(r) }; done(false) }
    }
}
