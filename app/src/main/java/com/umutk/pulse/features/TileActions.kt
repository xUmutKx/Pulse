package com.umutk.pulse.features

import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.umutk.pulse.Shell
import kotlin.math.roundToInt

/** The real actions behind Pulse's Quick Settings tiles; the Toolkit screens call the same functions. Safe on the main thread: shell work runs on its own thread. */
object TileActions {
    private val main = Handler(Looper.getMainLooper())
    private fun prefs(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    private fun canShell() = Shell.shizukuOk() || Shell.root == true
    /** Quick main-thread guess: false only when root was checked and refused and Shizuku is not there. */
    private fun mayShell() = Shell.root != false || Shell.shizukuOk()
    /** Background only. A tile can wake Pulse in a fresh process where the Shizuku binder arrives a moment later. */
    private fun waitShell(): Boolean {
        repeat(15) { if (!canShell()) Thread.sleep(100) }
        if (!canShell() && Shell.root == null) Shell.detectRoot()
        return canShell()
    }
    private fun bg(work: () -> Unit) = Thread(work).start()

    /** Collapses the shade, captures the screen to Pictures/Screenshots and adds it to the gallery. */
    fun screenshot(c: Context, onDone: (Boolean) -> Unit = {}): Boolean {
        if (!mayShell()) return false
        val app = c.applicationContext
        bg {
            val f = "/sdcard/Pictures/Screenshots/pulse_${System.currentTimeMillis()}.png"
            val ok = waitShell() && Shell.run("cmd statusbar collapse; sleep 0.7; mkdir -p /sdcard/Pictures/Screenshots && screencap -p $f && [ -s $f ] && echo OK").contains("OK")
            if (ok) {
                prefs(app).edit().putLong("shot_last", System.currentTimeMillis()).remove("shot_uri").apply()
                // the gallery's own uri for the shot, so the Toolkit screen can open it without file access
                MediaScannerConnection.scanFile(app, arrayOf(f), arrayOf("image/png")) { _, uri -> if (uri != null) prefs(app).edit().putString("shot_uri", uri.toString()).apply() }
            }
            if (ok) main.post { flash(app) }
            main.post { onDone(ok) }
        }
        return true
    }

    /** A glow along the screen edges that fades out, so you can tell the shot was taken. Needs the overlay permission; silent without it. */
    private fun flash(c: Context) = runCatching {
        if (!Settings.canDrawOverlays(c)) return@runCatching
        val d = c.resources.displayMetrics.density
        val wm = c.getSystemService(android.view.WindowManager::class.java)
        val v = android.view.View(c).apply {
            background = android.graphics.drawable.GradientDrawable().apply { setStroke((10 * d).toInt(), 0xCCFFFFFF.toInt()); cornerRadius = 28 * d }
        }
        val lp = android.view.WindowManager.LayoutParams(-1, -1, android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT)
        wm.addView(v, lp)
        v.animate().alpha(0f).setDuration(450).withEndAction { runCatching { wm.removeView(v) } }.start()
    }

    // "Sensors off" here is the microphone + camera privacy pair: the all-sensors switch (SensorPrivacyManager.setSensorPrivacy)
    // has no shell command, only `cmd sensor_privacy enable|disable <user> microphone|camera` (Android 12+) is reachable.
    /** Last known state of the mic + camera block (null = not read yet / unreadable); a fresh read runs in the background. */
    fun sensorsOff(c: Context): Boolean? {
        refreshSensors(c)
        return cachedSensors(c)
    }

    private fun cachedSensors(c: Context): Boolean? = prefs(c).let { if (it.contains("sensors_off")) it.getBoolean("sensors_off", false) else null }

    /** Reads the real state from `dumpsys sensor_privacy` and keeps it for [sensorsOff]. */
    fun refreshSensors(c: Context, onDone: (Boolean?) -> Unit = {}) {
        val app = c.applicationContext
        if (!mayShell()) { main.post { onDone(null) }; return }
        bg {
            val s = if (waitShell()) readSensors() else null
            if (s != null) prefs(app).edit().putBoolean("sensors_off", s).apply()
            main.post { onDone(s) }
        }
    }

    /** True when both the microphone and camera software toggles are on. The dump layout differs between Android
     *  versions (is_enabled=true on 12, state_type=ENABLED on 13+), so each entry is matched on either form. */
    private fun readSensors(): Boolean? {
        val entries = Shell.run("dumpsys sensor_privacy").split('{', '}').map { it.lowercase() }
            .filter { "sensor=microphone" in it || "sensor=camera" in it }
            .filter { "toggle_type=hardware" !in it }
        if (entries.isEmpty()) return null
        fun on(name: String) = entries.any { "sensor=$name" in it && ("is_enabled=true" in it || "state_type=enabled" in it) }
        return on("microphone") && on("camera")
    }

    /** Switches the microphone and camera block together. */
    fun toggleSensorsOff(c: Context, onDone: (Boolean?) -> Unit = {}): Boolean {
        if (!mayShell()) return false
        val app = c.applicationContext
        bg {
            var now: Boolean? = null
            if (waitShell()) {
                val want = !(readSensors() ?: cachedSensors(app) ?: false)
                val verb = if (want) "enable" else "disable"
                val user = android.os.Process.myUid() / 100000
                Shell.run("cmd sensor_privacy $verb $user microphone; cmd sensor_privacy $verb $user camera")
                now = readSensors()
                if (now != null) prefs(app).edit().putBoolean("sensors_off", now).apply()
            }
            main.post { onDone(now) }
        }
        return true
    }

    val scales = floatArrayOf(.85f, 1f, 1.15f, 1.3f)
    fun fontScale(c: Context) = Settings.System.getFloat(c.contentResolver, Settings.System.FONT_SCALE, 1f)
    fun scaleLabel(c: Context) = "${(fontScale(c) * 100).roundToInt()}%"

    /** Moves the system font scale to the next step of [scales], wrapping round to the smallest. */
    fun scaleNext(c: Context): Boolean {
        val cur = fontScale(c)
        return setFontScale(c, scales.firstOrNull { it > cur + .01f } ?: scales[0])
    }

    /** Writes Settings.System font_scale directly when allowed (Modify system settings, or WRITE_SECURE_SETTINGS), otherwise through root/Shizuku. */
    fun setFontScale(c: Context, v: Float, onDone: (Boolean) -> Unit = {}): Boolean {
        if (runCatching { Settings.System.putFloat(c.contentResolver, Settings.System.FONT_SCALE, v) }.getOrDefault(false)) { onDone(true); return true }
        if (!mayShell()) return false
        val pkg = c.packageName
        bg {
            // the grant lets the next change go through directly, without the shell
            val ok = waitShell().also { if (it) Shell.run("settings put system font_scale $v; pm grant $pkg android.permission.WRITE_SECURE_SETTINGS") }
            main.post { onDone(ok) }
        }
        return true
    }

    /** Android 10+ hands the clipboard only to the foreground app and the keyboard: null when hidden or empty. */
    fun clipboardText(c: Context): String? = runCatching {
        c.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString()
    }.getOrNull()


    /** Writes one Settings.System int directly when allowed, otherwise through root/Shizuku. */
    fun putSystem(c: Context, key: String, v: Int, onDone: (Boolean) -> Unit = {}): Boolean {
        if (runCatching { Settings.System.putInt(c.contentResolver, key, v) }.getOrDefault(false)) { onDone(true); return true }
        if (!mayShell()) return false
        bg { val ok = waitShell().also { if (it) Shell.run("settings put system $key $v") }; main.post { onDone(ok) } }
        return true
    }
    fun getSystem(c: Context, key: String, def: Int = 0) = Settings.System.getInt(c.contentResolver, key, def)

    /** Developer-option overlays: "show_touches" draws a dot under each finger, "pointer_location" the touch lines and coordinates. */
    fun flagOn(c: Context, key: String) = getSystem(c, key) == 1
    fun toggleFlag(c: Context, key: String, onDone: (Boolean) -> Unit = {}) = putSystem(c, key, if (flagOn(c, key)) 0 else 1, onDone)

    /** Screen rotation steps: -1 = automatic, else the fixed rotation 0, 90, 180 or 270 degrees (all four, including upside down). */
    val rotations = intArrayOf(-1, 0, 1, 2, 3)
    fun rotation(c: Context): Int = if (getSystem(c, "accelerometer_rotation", 1) == 1) -1 else getSystem(c, "user_rotation")
    fun rotationLabel(r: Int) = if (r < 0) "Auto" else "${r * 90}°"
    fun setRotation(c: Context, r: Int, onDone: (Boolean) -> Unit = {}): Boolean {
        val app = c.applicationContext
        if (r < 0) return putSystem(app, "accelerometer_rotation", 1, onDone)
        // pick the angle first, then lock: the lock alone would freeze whatever the screen shows now
        return putSystem(app, "user_rotation", r) { ok -> if (ok) putSystem(app, "accelerometer_rotation", 0, onDone) else onDone(false) }
    }
    fun rotationNext(c: Context, onDone: (Boolean) -> Unit = {}): Boolean {
        val cur = rotations.indexOf(rotation(c)).coerceAtLeast(0)
        return setRotation(c, rotations[(cur + 1) % rotations.size], onDone)
    }

    fun powerMenu(): Boolean = com.umutk.pulse.PowerMenu.show()

    fun volumePanel(c: Context) =
        c.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
}
