package com.umutk.pulse.ai

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.Locale

/** Turkish when the phone is in Turkish. */
fun tr(en: String, trText: String): String = if (Locale.getDefault().language == "tr") trText else en

/** A model that can be downloaded: a chat model (GGUF, llama.cpp) or a speech model (ggml, whisper.cpp). */
class AiModel(
    val id: String, val kind: String, val name: String, val file: String, val url: String, val bytes: Long,
    val license: String, val uncensored: Boolean = false, val note: String = "",
)

private fun hf(repo: String, file: String) = "https://huggingface.co/$repo/resolve/main/$file"

/** The catalog: small to large. Uncensored ones are "abliterated" builds (the refusal direction removed from the weights). */
val CHAT_MODELS = listOf(
    AiModel("qwen25-05b", "chat", "Qwen 2.5 0.5B", "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf", hf("bartowski/Qwen2.5-0.5B-Instruct-GGUF", "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"), 397_808_192, "Apache 2.0",
        note = "Very small, quick on any phone"),
    AiModel("gemma3-1b", "chat", "Gemma 3 1B", "google_gemma-3-1b-it-Q4_K_M.gguf", hf("bartowski/google_gemma-3-1b-it-GGUF", "google_gemma-3-1b-it-Q4_K_M.gguf"), 806_058_496, "Gemma terms"),
    AiModel("gemma3-1b-abl", "chat", "Gemma 3 1B abliterated", "gemma-3-1b-it-abliterated.q4_k_m.gguf", hf("mlabonne/gemma-3-1b-it-abliterated-GGUF", "gemma-3-1b-it-abliterated.q4_k_m.gguf"), 806_057_920, "Gemma terms", uncensored = true),
    AiModel("llama32-1b", "chat", "Llama 3.2 1B", "Llama-3.2-1B-Instruct-Q4_K_M.gguf", hf("bartowski/Llama-3.2-1B-Instruct-GGUF", "Llama-3.2-1B-Instruct-Q4_K_M.gguf"), 807_694_464, "Llama 3.2 Community License"),
    AiModel("qwen3-17b", "chat", "Qwen 3 1.7B", "Qwen_Qwen3-1.7B-Q4_K_M.gguf", hf("bartowski/Qwen_Qwen3-1.7B-GGUF", "Qwen_Qwen3-1.7B-Q4_K_M.gguf"), 1_282_439_584, "Apache 2.0",
        note = "Good Turkish for its size"),
    AiModel("llama32-3b", "chat", "Llama 3.2 3B", "Llama-3.2-3B-Instruct-Q4_K_M.gguf", hf("bartowski/Llama-3.2-3B-Instruct-GGUF", "Llama-3.2-3B-Instruct-Q4_K_M.gguf"), 2_019_377_696, "Llama 3.2 Community License"),
    AiModel("llama32-3b-abl", "chat", "Llama 3.2 3B abliterated", "Llama-3.2-3B-Instruct-abliterated.Q4_K_M.gguf", hf("mradermacher/Llama-3.2-3B-Instruct-abliterated-GGUF", "Llama-3.2-3B-Instruct-abliterated.Q4_K_M.gguf"), 2_241_004_512, "Llama 3.2 Community License", uncensored = true),
    AiModel("gemma3-4b", "chat", "Gemma 3 4B", "google_gemma-3-4b-it-Q4_K_M.gguf", hf("bartowski/google_gemma-3-4b-it-GGUF", "google_gemma-3-4b-it-Q4_K_M.gguf"), 2_489_758_112, "Gemma terms",
        note = "Best Turkish of the 4B ones"),
    AiModel("gemma3-4b-abl", "chat", "Gemma 3 4B abliterated", "gemma-3-4b-it-abliterated.q4_k_m.gguf", hf("mlabonne/gemma-3-4b-it-abliterated-GGUF", "gemma-3-4b-it-abliterated.q4_k_m.gguf"), 2_489_894_016, "Gemma terms", uncensored = true),
    AiModel("qwen3-4b", "chat", "Qwen 3 4B", "Qwen_Qwen3-4B-Q4_K_M.gguf", hf("bartowski/Qwen_Qwen3-4B-GGUF", "Qwen_Qwen3-4B-Q4_K_M.gguf"), 2_497_280_960, "Apache 2.0"),
    AiModel("qwen3-4b-abl", "chat", "Qwen 3 4B abliterated", "Qwen3-4B-abliterated.Q4_K_M.gguf", hf("mradermacher/Qwen3-4B-abliterated-GGUF", "Qwen3-4B-abliterated.Q4_K_M.gguf"), 2_497_280_704, "Apache 2.0", uncensored = true),
    AiModel("qwen25-7b", "chat", "Qwen 2.5 7B", "Qwen2.5-7B-Instruct-Q4_K_M.gguf", hf("bartowski/Qwen2.5-7B-Instruct-GGUF", "Qwen2.5-7B-Instruct-Q4_K_M.gguf"), 4_683_074_240, "Apache 2.0",
        note = "12 GB phones and up"),
)

/** Speech to text (whisper.cpp ggml files): for timed lyrics and voice. */
val SPEECH_MODELS = listOf(
    AiModel("whisper-base", "speech", "Whisper base", "ggml-base-q5_1.bin", hf("ggerganov/whisper.cpp", "ggml-base-q5_1.bin"), 59_707_625, "MIT", note = "Fast, rough with music"),
    AiModel("whisper-small", "speech", "Whisper small", "ggml-small-q5_1.bin", hf("ggerganov/whisper.cpp", "ggml-small-q5_1.bin"), 190_085_487, "MIT"),
    AiModel("whisper-medium", "speech", "Whisper medium", "ggml-medium-q5_0.bin", hf("ggerganov/whisper.cpp", "ggml-medium-q5_0.bin"), 539_212_467, "MIT", note = "Good with singing"),
    AiModel("whisper-turbo", "speech", "Whisper large v3 turbo", "ggml-large-v3-turbo-q5_0.bin", hf("ggerganov/whisper.cpp", "ggml-large-v3-turbo-q5_0.bin"), 574_041_195, "MIT", note = "Best lyrics, slowest"),
)

object Device {
    fun ramBytes(c: Context): Long = ActivityManager.MemoryInfo().also { (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.totalMem
    fun cores(): Int = Runtime.getRuntime().availableProcessors()
    /** How a model fits this phone: 2 recommended, 1 runs, 0 too big. A loaded model takes about its file size plus a fifth in memory. */
    fun fit(c: Context, m: AiModel): Int {
        val ram = ramBytes(c).toDouble()
        val need = m.bytes * 1.25
        return when { need <= ram * .28 -> 2; need <= ram * .45 -> 1; else -> 0 }
    }
    /** The best model this phone runs well. */
    fun recommended(c: Context, list: List<AiModel>): AiModel? = list.filter { !it.uncensored && fit(c, it) == 2 }.maxByOrNull { it.bytes } ?: list.minByOrNull { it.bytes }
}

/** Where the models live: Download/PulseAI (shared, they survive a reinstall and other apps can use them). */
object Store {
    private fun sp(c: Context) = c.getSharedPreferences("models", Context.MODE_PRIVATE)
    val dir: File get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PulseAI")
    fun fileOf(m: AiModel) = File(dir, m.file)

    /** Starts a download with the system download manager (it goes on in the background and survives the app). */
    fun download(c: Context, m: AiModel): Long {
        val dm = c.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(m.url)).setTitle("Pulse AI: ${m.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "PulseAI/${m.file}")
        val id = dm.enqueue(req)
        sp(c).edit().putLong("dl_${m.id}", id).apply()
        return id
    }

    /** 0..1 while downloading, 1 when the file is there, -1 when not started or failed. */
    fun progress(c: Context, m: AiModel): Float {
        if (has(c, m)) return 1f
        val id = sp(c).getLong("dl_${m.id}", -1L); if (id < 0) return -1f
        val dm = c.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id))?.use { q ->
            if (!q.moveToFirst()) return -1f
            val st = q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: m.bytes
            return when (st) { DownloadManager.STATUS_SUCCESSFUL -> 1f; DownloadManager.STATUS_FAILED -> -1f; else -> (done.toFloat() / total).coerceIn(0f, .999f) }
        }
        return -1f
    }

    fun has(c: Context, m: AiModel): Boolean {
        if (Environment.isExternalStorageManager()) return fileOf(m).let { it.isFile && it.length() >= m.bytes * 98 / 100 }
        val id = sp(c).getLong("dl_${m.id}", -1L); if (id < 0) return false
        val dm = c.getSystemService(DownloadManager::class.java)
        return dm.query(DownloadManager.Query().setFilterById(id))?.use { q -> q.moveToFirst() && q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL } == true
    }

    fun delete(c: Context, m: AiModel) {
        val id = sp(c).getLong("dl_${m.id}", -1L)
        if (id >= 0) runCatching { c.getSystemService(DownloadManager::class.java).remove(id) }
        runCatching { fileOf(m).delete() }
        sp(c).edit().remove("dl_${m.id}").apply()
    }

    /**
     * A path the native code can open: the file itself with all-files access, else the download handed over by the download manager
     * (/proc/self/fd/N; the descriptor stays open while the model is used, [hold] keeps it).
     */
    fun openPath(c: Context, m: AiModel, hold: (ParcelFileDescriptor) -> Unit): String? {
        val f = fileOf(m)
        if (Environment.isExternalStorageManager() && f.isFile) return f.path
        val id = sp(c).getLong("dl_${m.id}", -1L); if (id < 0) return null
        val pfd = runCatching { c.getSystemService(DownloadManager::class.java).openDownloadedFile(id) }.getOrNull() ?: return null
        hold(pfd)
        return "/proc/self/fd/${pfd.fd}"
    }

    /** A model file picked by hand (any GGUF or ggml file on the phone). */
    fun openUri(c: Context, uri: Uri, hold: (ParcelFileDescriptor) -> Unit): String? {
        val pfd = runCatching { c.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
        hold(pfd)
        return "/proc/self/fd/${pfd.fd}"
    }

    fun active(c: Context, kind: String): String? = sp(c).getString("active_$kind", null)
    fun setActive(c: Context, kind: String, id: String) { sp(c).edit().putString("active_$kind", id).apply() }
    fun customUri(c: Context, kind: String): Uri? = sp(c).getString("custom_$kind", null)?.let(Uri::parse)
    fun setCustomUri(c: Context, kind: String, u: Uri?) { sp(c).edit().putString("custom_$kind", u?.toString()).putString("active_$kind", if (u != null) "custom" else null).apply() }
}

fun gb(bytes: Long): String = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "%d MB".format(bytes / 1_000_000)
