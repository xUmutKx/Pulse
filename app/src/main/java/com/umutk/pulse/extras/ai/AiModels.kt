package com.umutk.pulse.extras.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.launch
import android.os.Environment
import android.os.ParcelFileDescriptor
import java.io.File
import com.umutk.pulse.tr
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


/** A model that can be downloaded: a chat model (GGUF, llama.cpp) or a speech model (ggml, whisper.cpp). */
class AiModel(
    val id: String, val kind: String, val name: String, val file: String, val url: String, val bytes: Long,
    val license: String, val uncensored: Boolean = false, val note: String = "",
    /** A file found on the phone or from a search: its path or URI, not in the catalog. */
    val path: String? = null,
    /** A vision model: the file that lets it see pictures (llama.cpp's mmproj), downloaded with the model. */
    val projector: Projector? = null,
)

/** The vision part of a vision model: its file, where it comes from and its size. */
class Projector(val file: String, val url: String, val bytes: Long)

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
    // vision: these see pictures (the picture goes in with the question); the small one answers quickest
    AiModel("smolvlm-500m-vision", "chat", "SmolVLM 500M vision", "SmolVLM-500M-Instruct-Q8_0.gguf", hf("ggml-org/SmolVLM-500M-Instruct-GGUF", "SmolVLM-500M-Instruct-Q8_0.gguf"), 436_806_912, "Apache 2.0",
        note = "Sees pictures, quickest", projector = Projector("mmproj-SmolVLM-500M-Instruct-Q8_0.gguf", hf("ggml-org/SmolVLM-500M-Instruct-GGUF", "mmproj-SmolVLM-500M-Instruct-Q8_0.gguf"), 108_783_360)),
    AiModel("gemma3-4b-vision", "chat", "Gemma 3 4B vision", "gemma-3-4b-it-Q4_K_M.gguf", hf("ggml-org/gemma-3-4b-it-GGUF", "gemma-3-4b-it-Q4_K_M.gguf"), 2_489_757_856, "Gemma terms",
        note = "Sees pictures, better answers", projector = Projector("mmproj-gemma-3-4b-it-f16.gguf", hf("ggml-org/gemma-3-4b-it-GGUF", "mmproj-model-f16.gguf"), 851_251_104)),
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
        val need = (m.bytes + (m.projector?.bytes ?: 0L)) * 1.25
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

    /**
     * The model downloads run in Pulse's own downloader (the engine of the Downloads page): they go on when the app is closed, show on the
     * Downloads page while they run, and the file is kept in Download/PulseAI.
     */
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Starts a download of the model. */
    fun download(c: Context, m: AiModel) {
        val app = c.applicationContext
        val file = fileOf(m)
        file.parentFile?.mkdirs()
        jobs.remove(m.id)?.cancel()
        jobs[m.id] = scope.launch {
            runCatching { com.umutk.pulse.features.Downloads.fetchFile(app, m.url, file, m.bytes) {} }
            m.projector?.let { p -> runCatching { com.umutk.pulse.features.Downloads.fetchFile(app, p.url, projectorFile(p), p.bytes) {} } }
        }
    }

    /** The vision part of a vision model, in the same folder. */
    fun projectorFile(p: Projector) = File(dir, p.file)

    /** 0..1 while downloading, 1 when the file is there, -1 when not started or failed. A vision model's projector is the second part. */
    fun progress(c: Context, m: AiModel): Float {
        if (has(c, m)) return 1f
        val p = m.projector
        val mainDone = fileOf(m).isFile && fileOf(m).length() >= m.bytes * 98 / 100
        val second = p != null && mainDone
        val file = if (second) projectorFile(p!!) else fileOf(m)
        val total = if (second) p!!.bytes else m.bytes
        val t = com.umutk.pulse.features.Downloads.tasks.value.firstOrNull { it.file.path == file.path } ?: return if (second) 0f else -1f
        return when (t.state.value) {
            com.umutk.pulse.features.DlState.DONE -> 1f
            com.umutk.pulse.features.DlState.FAILED -> -1f
            else -> (t.done.value.toFloat() / (t.total.value.takeIf { it > 0 } ?: total)).coerceIn(0f, .999f)
        }
    }

    /** The file is there whole: its size is right and no download of it is still running. */
    fun has(c: Context, m: AiModel): Boolean {
        m.path?.let { return File(it).isFile }
        val f = fileOf(m)
        if (!f.isFile || f.length() < m.bytes * 98 / 100) return false
        m.projector?.let { p -> projectorFile(p).let { pf -> if (!pf.isFile || pf.length() < p.bytes * 98 / 100) return false } }
        return com.umutk.pulse.features.Downloads.tasks.value.none { it.file.path == f.path && it.state.value == com.umutk.pulse.features.DlState.RUNNING }
    }

    fun delete(c: Context, m: AiModel) {
        jobs.remove(m.id)?.cancel()
        com.umutk.pulse.features.Downloads.tasks.value.firstOrNull { it.file.path == fileOf(m).path }?.let { com.umutk.pulse.features.Downloads.remove(it) }
        runCatching { fileOf(m).delete() }
        m.projector?.let { p -> runCatching { projectorFile(p).delete() } }
        sp(c).edit().remove("dl_${m.id}").apply()
    }

    /** A path the native code can open: the model file itself (Pulse has all-files access, the same as its downloads). */
    fun openPath(c: Context, m: AiModel, hold: (ParcelFileDescriptor) -> Unit): String? {
        m.path?.let { return it }
        return fileOf(m).takeIf { it.isFile }?.path
    }

    /** A model file picked by hand (any GGUF or ggml file on the phone). */
    fun openUri(c: Context, uri: Uri, hold: (ParcelFileDescriptor) -> Unit): String? {
        val pfd = runCatching { c.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
        hold(pfd)
        return "/proc/self/fd/${pfd.fd}"
    }

    /** Models from a search or found on the phone, kept so they stay in the list once picked or downloaded. */
    fun remember(c: Context, m: AiModel) {
        val o = JSONObject().put("id", m.id).put("kind", m.kind).put("name", m.name).put("file", m.file).put("url", m.url).put("bytes", m.bytes).put("license", m.license).put("unc", m.uncensored).put("path", m.path ?: "")
        sp(c).edit().putString("extra_${m.id}", o.toString()).apply()
    }
    fun forget(c: Context, m: AiModel) { sp(c).edit().remove("extra_${m.id}").apply() }
    fun extras(c: Context): List<AiModel> = sp(c).all.filterKeys { it.startsWith("extra_") }.values.mapNotNull { v -> runCatching {
        val o = JSONObject(v as String)
        AiModel(o.getString("id"), o.getString("kind"), o.getString("name"), o.getString("file"), o.getString("url"), o.getLong("bytes"), o.getString("license"), o.optBoolean("unc"), path = o.optString("path").ifEmpty { null })
    }.getOrNull() }
    /** Every model of a kind this phone knows: the catalog and the remembered ones. */
    fun known(c: Context, kind: String): List<AiModel> = (if (kind == "chat") CHAT_MODELS else SPEECH_MODELS) + extras(c).filter { it.kind == kind }

    fun active(c: Context, kind: String): String? = sp(c).getString("active_$kind", null)
    fun setActive(c: Context, kind: String, id: String) { sp(c).edit().putString("active_$kind", id).apply() }
    fun customUri(c: Context, kind: String): Uri? = sp(c).getString("custom_$kind", null)?.let(Uri::parse)
    fun setCustomUri(c: Context, kind: String, u: Uri?) { sp(c).edit().putString("custom_$kind", u?.toString()).putString("active_$kind", if (u != null) "custom" else null).apply() }
}

fun gb(bytes: Long): String = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "%d MB".format(bytes / 1_000_000)


/** Pulse AI runs on arm64 phones with Android 9 or newer (its engines are built for those). */
val aiSupported: Boolean get() = android.os.Build.VERSION.SDK_INT >= 28 && android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"

/**
 * Any GGUF on Hugging Face, by words (the idea of Pumpkin's model search): the most downloaded repos for [q], and in each the Q4_K_M file
 * (else the smallest Q4 one), with its size so the phone's fit can be shown. Runs on the network; call it off the main thread.
 */
fun searchHf(q: String, limit: Int = 12): List<AiModel> {
    fun get(u: String): String = (URL(u).openConnection() as HttpURLConnection).run { connectTimeout = 10000; readTimeout = 20000; inputStream.bufferedReader().use { it.readText() } }
    val repos = JSONArray(get("https://huggingface.co/api/models?search=" + java.net.URLEncoder.encode(q, "UTF-8") + "&filter=gguf&sort=downloads&direction=-1&limit=$limit"))
    val out = ArrayList<AiModel>()
    for (i in 0 until repos.length()) {
        val id = repos.getJSONObject(i).getString("id")
        val info = runCatching { JSONObject(get("https://huggingface.co/api/models/$id?blobs=true")) }.getOrNull() ?: continue
        val files = info.optJSONArray("siblings") ?: continue
        val ggufs = (0 until files.length()).map { files.getJSONObject(it) }.filter { it.getString("rfilename").endsWith(".gguf", true) && !it.getString("rfilename").contains("mmproj", true) }
        val pick = ggufs.firstOrNull { it.getString("rfilename").contains("q4_k_m", true) } ?: ggufs.filter { it.getString("rfilename").contains("q4", true) }.minByOrNull { it.optLong("size") } ?: continue
        val f = pick.getString("rfilename")
        val lic = info.optJSONObject("cardData")?.optString("license").orEmpty().ifEmpty { "?" }
        out += AiModel("hf:" + id + "/" + f, "chat", id.substringAfter('/'), f.substringAfterLast('/'), "https://huggingface.co/$id/resolve/main/$f", pick.optLong("size"), lic,
            uncensored = Regex("abliterat|uncensor", RegexOption.IGNORE_CASE).containsMatchIn(id))
    }
    return out
}

/** GGUF and whisper files already on the phone (Download, Download/PulseAI and the folders other apps keep them in): used in place, nothing is copied. */
fun phoneModels(): List<AiModel> {
    if (!Environment.isExternalStorageManager()) return emptyList()
    val root = Environment.getExternalStorageDirectory()
    val dirs = listOf(Store.dir, File(root, "Download"), File(root, "Documents"), File(root, "Models"), File(root, "Android/media"))
    val seen = HashSet<String>()
    return dirs.flatMap { d -> d.walkTopDown().maxDepth(3).filter { it.isFile && (it.name.endsWith(".gguf", true) || (it.name.startsWith("ggml-") && it.name.endsWith(".bin"))) }.toList() }
        .filter { seen.add(it.canonicalPath) && CHAT_MODELS.none { m -> m.file == it.name } && SPEECH_MODELS.none { m -> m.file == it.name } }
        .map { f -> AiModel("file:" + f.path, if (f.name.endsWith(".bin")) "speech" else "chat", f.nameWithoutExtension, f.name, "", f.length(), "?", path = f.path) }
}
