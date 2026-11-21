package com.umutk.pulse.extras.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Other apps open in windows of the desktop only when Android's own freeform windows are on (the launcher asks for a window of a size; the phone decides). This switches the
 * phone's settings that allow it: with the permission to write secure settings (one adb command on a computer, once), or with root or Shizuku. A restart of the phone may be needed.
 */
object FreeformSetup {
    private val KEYS = listOf("enable_freeform_support", "force_resizable_activities", "freeform_window_management")
    const val ADB_GRANT = "adb shell pm grant com.umutk.pulse android.permission.WRITE_SECURE_SETTINGS"

    fun isOn(c: Context): Boolean = runCatching { Settings.Global.getInt(c.contentResolver, "enable_freeform_support", 0) == 1 && Settings.Global.getInt(c.contentResolver, "force_resizable_activities", 0) == 1 }.getOrDefault(false)

    /** Returns a short result: "on", "needs-adb" (the command was copied to the clipboard) or "failed". */
    fun enable(c: Context): String {
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
            val ok = runCatching { KEYS.forEach { Settings.Global.putInt(c.contentResolver, it, 1) }; true }.getOrDefault(false)
            return if (ok) "on" else "failed"
        }
        if (com.umutk.pulse.extras.blackout.Privilege.mode != com.umutk.pulse.extras.blackout.Privilege.Mode.None) {
            com.umutk.pulse.extras.blackout.Privilege.run(KEYS.joinToString("; ") { "settings put global $it 1" })
            return if (isOn(c)) "on" else "failed"
        }
        runCatching { (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("adb", ADB_GRANT)) }
        return "needs-adb"
    }
}
