package com.umutk.pulse.features.doctor

import java.util.concurrent.TimeUnit

// Runs one catalog check as root. Port of AndroDoc's Shell and Guard, read-only: a command is refused unless it only reads state.
// shortcut: no write rules at all (the catalog never writes), so a future fix must add its own guard, not reuse this.
internal object TroubleShell {
    // Returns the output and the exit code as text, ready to show under the check.
    fun run(cmd: String): String {
        if (!allowed(cmd)) return tx("Blocked by the safety filter", "Güvenlik filtresi engelledi")
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            // Read on a side thread: reading here first would block forever on a hung command, so the timeout never fired.
            var out = ""
            val reader = Thread { out = p.inputStream.bufferedReader().readText() }
            reader.start()
            val done = p.waitFor(20, TimeUnit.SECONDS)
            if (!done) p.destroyForcibly()
            reader.join(2000)
            when {
                !done -> tx("Command timed out", "Komut zaman aşımına uğradı")
                else -> out.trim().ifEmpty { tx("(no output)", "(çıktı yok)") } + "\n[exit ${p.exitValue()}]"
            }
        } catch (e: Exception) {
            tx("Root shell could not open", "Root shell açılamadı") + " (${e.message ?: "su failed"})"
        }
    }

    private val quoted = Regex("'[^']*'|\"[^\"]*\"")
    private val redirect = Regex("""\d?>\s*(&\d|/dev/null)""")
    private val spaces = Regex("\\s+")
    private val activitySubcommands = setOf("lru", "services", "processes", "activities")
    private val dumpsysServices = setOf(
        "audio", "media_session", "battery", "power", "deviceidle", "activity", "notification", "device_policy",
        "display", "wifi", "connectivity", "telephony.registry", "bluetooth_manager", "meminfo", "sensorservice",
        "SurfaceFlinger", "media.camera", "package",
    )
    private val readCommands = setOf(
        "getprop", "cat", "ls", "df", "du", "ping", "id", "uname", "echo", "grep", "head", "tail", "wc",
        "sort", "cut", "awk", "top", "pidof", "ps", "nproc", "tr",
    )

    private fun allowed(command: String): Boolean {
        for (raw in split(command)) {
            val segment = raw.replace(redirect, "").trim()
            if (segment.isEmpty()) continue
            // Whatever redirection is left would write somewhere. Quoted text (like awk '{print $4}') is ignored.
            val bare = segment.replace(quoted, "''")
            if (bare.contains('>') || bare.contains('<') || bare.contains('`') || bare.contains("$(")) return false
            if (!readOnly(bare)) return false
        }
        return true
    }

    // Splits on unquoted ; | || && so quoted patterns such as 'a\|b' stay in one piece.
    private fun split(command: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < command.length) {
            val ch = command[i]
            val next = command.getOrNull(i + 1)
            when {
                quote != null -> { current.append(ch); if (ch == quote) quote = null }
                ch == '\'' || ch == '"' -> { quote = ch; current.append(ch) }
                ch == ';' || ch == '|' || (ch == '&' && next == '&') -> {
                    parts.add(current.toString())
                    current.clear()
                    if ((ch == '|' && next == '|') || ch == '&') i++
                }
                else -> current.append(ch)
            }
            i++
        }
        parts.add(current.toString())
        return parts
    }

    private fun readOnly(bare: String): Boolean {
        val w = bare.split(spaces)
        return when (w[0]) {
            in readCommands -> true
            "sed" -> w.none { it.startsWith("-i") || it == "--in-place" }
            "logcat" -> w.none { it == "-c" || it == "-G" || it == "-P" }
            "dmesg" -> w.none { it == "-c" || it == "-C" || it == "-n" }
            "settings" -> w.getOrNull(1) == "get"
            "cmd" -> (w.getOrNull(1) == "appops" && w.getOrNull(2) == "get") ||
                (w.size == 3 && w[1] == "uimode" && w[2] == "night")
            "pm" -> w.getOrNull(1) == "path" || w.getOrNull(1) == "list"
            "am" -> w.getOrNull(1) == "get-standby-bucket"
            "wm" -> w.size == 2 && (w[1] == "size" || w[1] == "density")
            "service" -> w.getOrNull(1) == "check"
            "ip" -> w.getOrNull(1) == "route" || w.getOrNull(1) == "link"
            "date" -> w.size == 1
            "getevent" -> w.size == 2 && w[1] == "-p"
            "dpm" -> w.getOrNull(1) == "list-owners"
            "dumpsys" -> dumpsysReadable(w)
            else -> false
        }
    }

    private fun dumpsysReadable(w: List<String>): Boolean {
        val service = w.getOrNull(1) ?: return false
        if (service !in dumpsysServices) return false
        return when (service) {
            "battery" -> w.size == 2
            "deviceidle" -> w.size == 2 || (w.size == 3 && w[2] == "whitelist")
            "activity" -> w.getOrNull(2) in activitySubcommands
            else -> true
        }
    }
}
