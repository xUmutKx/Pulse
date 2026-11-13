package com.umutk.pulse

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

/**
 * After a crash, the next start of Pulse offers to send a bug report: it opens a new issue on GitHub with the error already filled in (the Pulse and
 * Android version, the phone model, the error lines). Nothing is sent by itself: the person presses Submit there. Can be switched off in Settings.
 */
object CrashReport {
    private const val ASK = "crash_ask"
    fun enabled(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE).getBoolean(ASK, true)
    fun setEnabled(c: Context, on: Boolean) { c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putBoolean(ASK, on).apply() }

    /** The question, once per crash. */
    fun askIfPending(a: Activity) {
        val f = File(a.filesDir, "crash-pending.txt")
        if (!f.isFile || a.isFinishing) return
        val text = runCatching { f.readText() }.getOrDefault("")
        f.delete()
        if (text.isBlank() || !enabled(a)) return
        val first = text.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }?.trim()?.take(90) ?: "crash"
        val body = "**Pulse crash report**\n\n```\n" + text.take(3000) + "\n```\n\nWhat I was doing when it crashed:\n"
        val url = "https://github.com/xUmutKx/Pulse/issues/new?title=" + Uri.encode("Crash: $first") + "&body=" + Uri.encode(body)
        android.app.AlertDialog.Builder(a)
            .setTitle(tr("Pulse stopped last time", "Pulse geçen sefer durdu"))
            .setMessage(tr(
                "Send a bug report to the developer? It opens GitHub with the error already filled in (the version, the phone model and the error lines). Nothing is sent until you press Submit there.",
                "Geliştiriciye hata raporu gönderilsin mi? GitHub, hata önceden doldurulmuş olarak açılır (sürüm, telefon modeli ve hata satırları). Orada Gönder'e basana kadar hiçbir şey gönderilmez.",
            ))
            .setPositiveButton(tr("Send report", "Rapor gönder")) { _, _ -> runCatching { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            .setNegativeButton(tr("Not now", "Şimdi değil"), null)
            .show()
    }
}

/**
 * The lite build has no Pulse AI, but the models an earlier full build downloaded (Download/PulseAI, gigabytes) are still on the phone. Once, it asks whether
 * to delete them. Nothing is deleted without the answer, since other apps may use the same files.
 */
object LiteModels {
    fun askIfNeeded(a: Activity) {
        if (BuildConfig.LOCAL_AI) return
        val sp = a.getSharedPreferences("p", Context.MODE_PRIVATE)
        if (sp.getBoolean("lite_models_asked", false)) return
        Thread {
            val dir = com.umutk.pulse.extras.ai.Store.dir
            val bytes = runCatching { if (dir.isDirectory) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L }.getOrDefault(0L)
            if (bytes < 1_000_000L) { sp.edit().putBoolean("lite_models_asked", true).apply(); return@Thread }
            a.runOnUiThread {
                if (a.isFinishing) return@runOnUiThread
                android.app.AlertDialog.Builder(a)
                    .setTitle(tr("Pulse AI models are still on the phone", "Pulse AI modelleri hâlâ telefonda"))
                    .setMessage(tr(
                        "This lite version has no Pulse AI. The models in Download/PulseAI take %.1f GB. Delete them to free the space?".format(bytes / 1e9),
                        "Bu lite sürümde Pulse AI yok. Download/PulseAI klasöründeki modeller %.1f GB yer kaplıyor. Yer açmak için silinsin mi?".format(bytes / 1e9),
                    ))
                    .setPositiveButton(tr("Delete", "Sil")) { _, _ ->
                        sp.edit().putBoolean("lite_models_asked", true).apply()
                        Thread { runCatching { dir.deleteRecursively() } }.start()
                    }
                    .setNegativeButton(tr("Keep", "Kalsın")) { _, _ -> sp.edit().putBoolean("lite_models_asked", true).apply() }
                    .show()
            }
        }.start()
    }
}
