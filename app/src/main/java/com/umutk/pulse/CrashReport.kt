package com.umutk.pulse

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * After a crash, the next start of Pulse offers to send a bug report: it opens a new issue on GitHub with the error already filled in (the Pulse and
 * Android version, the phone model, the error lines). Nothing is sent by itself: the person presses Submit there. Can be switched off in Settings.
 * The question is a window of Pulse's own (in the look that is on), see [CrashReportPrompt].
 */
object CrashReport {
    private const val ASK = "crash_ask"
    fun enabled(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean(ASK, true)
    fun setEnabled(c: Context, on: Boolean) { c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean(ASK, on).apply() }

    /** The report of the last crash, once: it is taken out of the way as it is read; null when there is none or the question is switched off. */
    fun takePending(c: Context): String? {
        val f = File(c.filesDir, "crash-pending.txt")
        if (!f.isFile) return null
        val text = runCatching { f.readText() }.getOrDefault("")
        f.delete()
        return text.takeIf { it.isNotBlank() && enabled(c) }
    }

    /** Opens the new GitHub issue with [text] filled in. */
    fun open(c: Context, text: String) {
        val first = text.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }?.trim()?.take(90) ?: "crash"
        val body = "**Pulse crash report**\n\n```\n" + text.take(3000) + "\n```\n\nWhat I was doing when it crashed:\n"
        val url = "https://github.com/xUmutKx/Pulse/issues/new?title=" + Uri.encode("Crash: $first") + "&body=" + Uri.encode(body)
        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** The question after a crash, in a window of Pulse's own look. */
@Composable
fun CrashReportPrompt() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { CrashReport.takePending(ctx) } }
    report?.let { text ->
        WinAlert(
            onDismissRequest = { report = null },
            title = { Text(tr("Pulse stopped last time", "Pulse geçen sefer durdu"), color = T.text) },
            text = {
                Text(
                    tr(
                        "Send a bug report to the developer? It opens GitHub with the error already filled in (the version, the phone model and the error lines). Nothing is sent until you press Submit there.",
                        "Geliştiriciye hata raporu gönderilsin mi? GitHub, hata önceden doldurulmuş olarak açılır (sürüm, telefon modeli ve hata satırları). Orada Gönder'e basana kadar hiçbir şey gönderilmez.",
                    ),
                    color = T.sub, fontSize = 14.sp,
                )
            },
            confirmButton = { PButton(tr("Send report", "Rapor gönder")) { CrashReport.open(ctx, text); report = null } },
            dismissButton = { PButton(tr("Not now", "Şimdi değil")) { report = null } },
        )
    }
}

/**
 * The lite build has no Pulse AI, but the models an earlier full build downloaded (Download/PulseAI, gigabytes) are still on the phone. Once, it asks whether
 * to delete them. Nothing is deleted without the answer, since other apps may use the same files.
 */
@Composable
fun LiteModelsPrompt() {
    if (BuildConfig.LOCAL_AI) return
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("p", Context.MODE_PRIVATE) }
    var bytes by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        if (sp.getBoolean("lite_models_asked", false)) return@LaunchedEffect
        val dir = com.umutk.pulse.extras.ai.Store.dir
        val b = withContext(Dispatchers.IO) { runCatching { if (dir.isDirectory) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }.getOrDefault(0L) }
        if (b < 1_000_000L) sp.edit().putBoolean("lite_models_asked", true).apply() else bytes = b
    }
    if (bytes > 0L) {
        fun done() { sp.edit().putBoolean("lite_models_asked", true).apply(); bytes = 0L }
        WinAlert(
            onDismissRequest = { done() },
            title = { Text(tr("Pulse AI models are still on the phone", "Pulse AI modelleri hâlâ telefonda"), color = T.text) },
            text = {
                Text(
                    tr(
                        "This lite version has no Pulse AI. The models in Download/PulseAI take %.1f GB. Delete them to free the space?".format(bytes / 1e9),
                        "Bu lite sürümde Pulse AI yok. Download/PulseAI klasöründeki modeller %.1f GB yer kaplıyor. Yer açmak için silinsin mi?".format(bytes / 1e9),
                    ),
                    color = T.sub, fontSize = 14.sp,
                )
            },
            confirmButton = { PButton(tr("Delete", "Sil")) { val d = com.umutk.pulse.extras.ai.Store.dir; Thread { runCatching { d.deleteRecursively() } }.start(); done() } },
            dismissButton = { PButton(tr("Keep", "Kalsın")) { done() } },
        )
    }
}
