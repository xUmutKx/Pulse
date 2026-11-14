package com.umutk.pulse.features

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.umutk.pulse.extras.blackout.Privilege

/**
 * A Google Play link (play.google.com/store/apps/details?id=… or market://details?id=…) that the system gave to Pulse: an app that is on the phone opens on its
 * page in Pulse's app manager; any other goes on to the Play Store itself, which is started by name so the link does not come back here.
 */
class PlayLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val pkg = data?.getQueryParameter("id").orEmpty()
        val installed = pkg.isNotEmpty() && runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess
        if (installed) {
            startActivity(Intent(this, com.umutk.pulse.ExtraToolActivity::class.java).putExtra("route", "app:$pkg"))
        } else if (data != null) {
            val toStore = Intent(Intent.ACTION_VIEW, data).setPackage("com.android.vending")
            runCatching { startActivity(toStore) }.onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addCategory(Intent.CATEGORY_BROWSABLE)) } }
        }
        finish()
    }
}

/** Makes Pulse take the Play Store links through root or Shizuku (the system's "open supported links" choice, which no app can set for itself). */
object PlayLinks {
    private const val HOST = "play.google.com"

    /** True when the commands ran; the result tells nothing about whether the phone accepted them (old Androids have no such command). */
    fun take(pkg: String, on: Boolean): Boolean {
        if (Privilege.mode == Privilege.Mode.None) return false
        val a = if (on) "true" else "false"
        val out = StringBuilder()
        out.append(Privilege.run("pm set-app-links-allowed --user 0 --package $pkg $a").text)
        out.append(Privilege.run("pm set-app-links-user-selection --user 0 --package $pkg $a $HOST").text)
        // the Play Store gives the link up while Pulse holds it, and takes it back after
        out.append(Privilege.run("pm set-app-links-user-selection --user 0 --package com.android.vending ${if (on) "false" else "true"} $HOST").text)
        return !Regex("(?i)exception|error|unknown").containsMatchIn(out.toString())
    }
}
