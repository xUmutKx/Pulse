package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Sends one command to Termux's shell in the background. Needs RUN_COMMAND granted and allow-external-apps=true in Termux. */
object TermuxRun {
    const val PERM = "com.termux.permission.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"

    fun granted(c: Context) = c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED

    /** Returns the status to show: a success line, or why Termux did not take the command. */
    fun send(c: Context, command: String): String {
        return try {
            c.packageManager.getPackageInfo("com.termux", 0)
            val i = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            c.startService(i)
            "Sent to Termux. The output shows in Termux."
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            "Termux is not installed."
        } catch (e: Exception) {
            "Termux did not take the command. In Termux, set allow-external-apps=true in ~/.termux/termux.properties and restart Termux."
        }
    }
}
