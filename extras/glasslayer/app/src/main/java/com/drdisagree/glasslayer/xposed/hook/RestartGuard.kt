package com.drdisagree.glasslayer.xposed.hook

import java.io.File

object RestartGuard {

    private const val FILE_NAME = "glass_layer_restarts"
    private const val WINDOW_MS = 40_000L
    private const val LIMIT = 5
    private const val TOKEN = "token="
    private const val SAFE = "safe="
    private const val TIMES = "times="
    private const val SEPARATOR = ","

    @Volatile
    var safeMode = false
        private set

    fun check(dataDir: String?, appToken: Long): Boolean {
        val file = dataDir?.let { File(it, FILE_NAME) } ?: return false
        try {
            val lines = if (file.exists()) file.readLines() else emptyList()
            var token = lines.value(TOKEN)?.toLongOrNull() ?: 0L
            var safe = lines.value(SAFE) == SAFE_ON
            var times = lines.value(TIMES)?.split(SEPARATOR)?.mapNotNull { it.toLongOrNull() }.orEmpty()
            if (appToken != token) {
                token = appToken
                safe = false
                times = emptyList()
            }
            val now = System.currentTimeMillis()
            times = times.filter { now - it in 0..WINDOW_MS } + now
            if (times.size >= LIMIT) safe = true
            file.writeText(
                TOKEN + token + "\n" +
                    SAFE + (if (safe) SAFE_ON else SAFE_OFF) + "\n" +
                    TIMES + times.takeLast(LIMIT).joinToString(SEPARATOR) + "\n"
            )
            safeMode = safe
        } catch (throwable: Throwable) {
            HookEnv.log("Restart guard failed", throwable)
        }
        return safeMode
    }

    private fun List<String>.value(prefix: String): String? = firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

    private const val SAFE_ON = "1"
    private const val SAFE_OFF = "0"
}
