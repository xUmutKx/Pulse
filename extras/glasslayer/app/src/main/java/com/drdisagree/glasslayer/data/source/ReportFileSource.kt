package com.drdisagree.glasslayer.data.source

import android.content.Context
import androidx.core.net.toUri
import com.drdisagree.glasslayer.domain.model.ReportLog
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportFileSource(private val context: Context) {

    suspend fun write(target: String, files: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(target.toUri(), WRITE_TRUNCATE)?.use { stream ->
                ZipOutputStream(stream).use { zip ->
                    files.forEach { (name, text) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(text.toByteArray())
                        zip.closeEntry()
                    }
                }
                true
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun systemLogs(): Map<ReportLog, String> = buildMap {
        read(SYSTEMUI_COMMAND)?.let { put(ReportLog.SYSTEMUI, it) }
        read(CRASH_COMMAND)?.let { put(ReportLog.CRASH, it) }
        read(FRAMEWORK_COMMAND)?.let { put(ReportLog.FRAMEWORK, it) }
    }

    private suspend fun read(command: String): String? = RootShell.read(command) { it.readBytes().decodeToString() }
        ?.trim()
        ?.ifEmpty { null }
}

private const val WRITE_TRUNCATE = "wt"
private const val SYSTEMUI_COMMAND =
    "pid=$(pidof com.android.systemui); " +
        "if [ -n \"\$pid\" ]; then logcat -d -v threadtime -b main,system --pid=\$pid 2>/dev/null | tail -n 800; " +
        "else logcat -d -v threadtime -b main,system 2>/dev/null | " +
        "grep -iE 'GlassLayer|LSPosed|Vector|libxposed|AndroidRuntime|FATAL|com\\.android\\.systemui' | tail -n 800; fi"
private const val CRASH_COMMAND = "logcat -d -v threadtime -b crash 2>/dev/null | tail -n 300"
private const val FRAMEWORK_COMMAND =
    "dir=/data/adb/lspd/log; " +
        "m=$(ls -t \$dir/modules_*.log 2>/dev/null | head -n 1); " +
        "v=$(ls -t \$dir/verbose_*.log 2>/dev/null | head -n 1); " +
        "if [ -n \"\$m\" ]; then echo \"# \$m\"; tail -n 300 \"\$m\"; fi; " +
        "if [ -n \"\$v\" ]; then echo \"# \$v\"; grep -iE 'glasslayer|systemui|error|exception|fatal' \"\$v\" | tail -n 200; fi"
