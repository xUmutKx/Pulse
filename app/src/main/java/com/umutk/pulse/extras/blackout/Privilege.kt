package com.umutk.pulse.extras.blackout

import com.umutk.pulse.Shell

/**
 * How commands run with shell-level power. Pulse already knows root (su) and Shizuku ([Shell]); this keeps BlackOut's own small interface on top of it:
 * overlays can only be fabricated by root or the shell user.
 */
object Privilege {
    enum class Mode { None, Root, Shizuku }

    class Out(val code: Int, val text: String)

    @Volatile var mode = Mode.None; private set
    @Volatile var detail = ""; private set
    @Volatile var rootNote = ""; private set

    fun shizukuRunning(): Boolean = Shell.shizuku >= 1

    fun requestShizuku() { try { rikka.shizuku.Shizuku.requestPermission(41) } catch (_: Throwable) { } }

    /** Asks for root explicitly (this is what makes the root manager show its grant popup). */
    fun tryRoot(): Boolean {
        val r = try { Shell.runRoot("id") } catch (_: Throwable) { "" }
        rootNote = when {
            r.isBlank() -> "su could not be started (no root on this phone, or the root app hides su)"
            r.contains("uid=0") -> "root works"
            else -> "su answered: " + r.take(160)
        }
        if (r.contains("uid=0")) { mode = Mode.Root; detail = "root"; return true }
        return false
    }

    /** Looks at what is available now (Shizuku or root) and remembers it. [prefer] forces one of them. */
    fun detect(prefer: String = "auto"): Mode {
        Shell.refreshShizuku()
        if (prefer != "root" && Shell.shizukuOk()) { mode = Mode.Shizuku; detail = "Shizuku"; return mode }
        if (prefer != "shizuku" && tryRoot()) return mode
        mode = Mode.None
        detail = if (shizukuRunning()) "Shizuku is running but Pulse has no permission yet. $rootNote" else "$rootNote; Shizuku is not running"
        return mode
    }

    /** Runs a shell script with the detected power (root, or Shizuku's shell). The exit code is not known; the text holds stdout and stderr. */
    fun run(script: String): Out = when (mode) {
        Mode.Root -> Out(0, Shell.runRoot(script).trim())
        Mode.Shizuku -> Out(0, Shell.run(script).trim())
        Mode.None -> Out(-1, "no root / Shizuku")
    }
}
