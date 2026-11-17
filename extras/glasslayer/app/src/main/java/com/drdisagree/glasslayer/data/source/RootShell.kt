package com.drdisagree.glasslayer.data.source

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootShell {

    suspend fun run(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun <T> read(command: String, block: (InputStream) -> T?): T? = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command).start()
            val result = process.inputStream.use(block)
            process.waitFor()
            result
        } catch (_: Exception) {
            null
        }
    }
}
