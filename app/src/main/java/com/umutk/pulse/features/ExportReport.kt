package com.umutk.pulse.features

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.umutk.pulse.Hw
import com.umutk.pulse.Section
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Each section is isolated so one failing probe does not drop the whole report.
private fun part(f: () -> List<Section>): List<Section> = runCatching(f).getOrElse { emptyList() }

suspend fun buildReport(context: Context): String = withContext(Dispatchers.IO) {
    val sections = mutableListOf<Section>()
    sections += part { Hw.storage(context) }
    sections += part { Hw.memory(context) }
    sections += part { Hw.battery(context) }
    runCatching { Hw.maxTemp() }.getOrDefault(0f).takeIf { it > 0f }?.let {
        sections += Section("Temperature", listOf("Max zone" to "%.1f °C".format(it)))
    }
    // shortcut: APK size only, not app data size; upgrade to StorageStatsManager when usage access is granted
    val apps = runCatching {
        context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA).map {
            it.loadLabel(context.packageManager).toString() to File(it.sourceDir).length()
        }.sortedByDescending { it.second }.take(10)
    }.getOrDefault(emptyList())
    if (apps.isNotEmpty()) {
        sections += Section("Top apps by size (APK)", apps.map { (n, b) -> n to "%.1f MB".format(b / 1048576.0) })
    }

    buildString {
        appendLine("Pulse device report")
        appendLine("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        for (s in sections) {
            appendLine()
            appendLine("== ${s.title} ==")
            for ((k, v) in s.rows) appendLine("$k: $v")
        }
    }
}

// Returns the saved display name. minSdk is 29, so MediaStore is the only path needed.
suspend fun saveReport(context: Context, text: String): String = withContext(Dispatchers.IO) {
    val name = "pulse-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("Could not create Downloads entry")
    try {
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Could not open $name for writing")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
    name
}

@Composable
fun ExportReportButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    Box(modifier) {
        Button(onClick = {
            scope.launch {
                val message = try {
                    "Saved ${saveReport(context, buildReport(context))} to Downloads"
                } catch (e: Exception) {
                    "Export failed: ${e.message}"
                }
                snackbar.showSnackbar(message)
            }
        }) { Text("Export report") }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
