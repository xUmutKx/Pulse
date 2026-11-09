package com.umutk.pulse.extras.ai

import android.content.Context
import android.os.ParcelFileDescriptor
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.umutk.pulse.tr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The chat model in use (llama.cpp through llama.cpp's own Android JNI). One model and one conversation at a time. */
object AiEngine {
    private var held: ParcelFileDescriptor? = null
    var loadedId: String? = null; private set
    private val lock = Mutex()

    fun engine(c: Context): InferenceEngine = AiChat.getInferenceEngine(c.applicationContext)

    /** The model picked in the Models tab (or a file picked by hand): its id and path, or null when none is there. */
    fun chosenPath(c: Context): Pair<String, String>? {
        val all = Store.known(c, "chat")
        val id = Store.active(c, "chat") ?: Device.recommended(c, all.filter { Store.has(c, it) })?.id ?: return null
        if (id == "custom") { val u = Store.customUri(c, "chat") ?: return null; return Store.openUri(c, u) { keep(it) }?.let { id to it } }
        val m = all.firstOrNull { it.id == id && Store.has(c, it) } ?: all.firstOrNull { Store.has(c, it) } ?: return null
        return Store.openPath(c, m) { keep(it) }?.let { m.id to it }
    }

    private fun keep(p: ParcelFileDescriptor) { held?.let { runCatching { it.close() } }; held = p }

    /** Starts a new conversation with [system] as its instructions (the model is loaded again so nothing of the last one stays). */
    suspend fun start(c: Context, system: String): Boolean = lock.withLock {
        if (!aiSupported) return false
        val e = engine(c)
        e.state.first { it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing }
        if (e.state.value.isModelLoaded || e.state.value is InferenceEngine.State.Error) runCatching { e.cleanUp() }
        val (id, path) = chosenPath(c) ?: return false
        e.loadModel(path)
        loadedId = id
        if (system.isNotBlank()) e.setSystemPrompt(system)
        true
    }

    fun ask(c: Context, text: String, max: Int = 2048): Flow<String> = engine(c).sendUserPrompt(text, max)

    /** One answer to one question in a fresh conversation (the text tools). */
    suspend fun once(c: Context, system: String, text: String, max: Int = 512): String? {
        if (!start(c, system)) return null
        val sb = StringBuilder()
        ask(c, text, max).collect { sb.append(it) }
        return sb.toString().trim()
    }

    /** The keyboard's AI fix: spelling, typos, punctuation, capitals and the Turkish letters mended, nothing else changed. */
    suspend fun fix(c: Context, text: String): String? = once(c,
        "You correct text. Fix spelling, typos, punctuation, capital letters and Turkish characters (ı ş ğ ü ö ç) in the user's message. " +
            "Keep its language, words, meaning and tone. Reply with the corrected text only, no quotes, no notes.", text.take(4000), (text.length / 2 + 64).coerceAtMost(1024))
}

/** What the person told Pulse AI to remember about them (the idea of Pumpkin's memory): every chat starts knowing it. Kept on the phone, editable. */
object AiMemory {
    private fun sp(c: Context) = c.getSharedPreferences("ai_memory", Context.MODE_PRIVATE)
    fun facts(c: Context): List<String> = sp(c).getString("facts", "").orEmpty().split('\n').filter { it.isNotBlank() }
    fun set(c: Context, list: List<String>) { sp(c).edit().putString("facts", list.filter { it.isNotBlank() }.joinToString("\n")).apply() }
    fun on(c: Context) = sp(c).getBoolean("on", true)
    fun setOn(c: Context, v: Boolean) { sp(c).edit().putBoolean("on", v).apply() }
}

/** The instructions a chat starts with: the base one, the person's own, and what Pulse AI remembers. */
fun systemPrompt(c: Context): String {
    val base = c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).getString("system", null)
        ?: tr("You are Pulse AI, a helpful assistant running offline on this phone. Answer in the user's language, clearly and briefly.",
            "Sen Pulse AI'sın, bu telefonda çevrimdışı çalışan yardımcı bir asistansın. Kullanıcının dilinde, açık ve kısa yanıt ver.")
    val mem = if (AiMemory.on(c)) AiMemory.facts(c) else emptyList()
    return if (mem.isEmpty()) base else base + "\n\n" + tr("What you know about the user:", "Kullanıcı hakkında bildiklerin:") + "\n" + mem.joinToString("\n") { "- $it" }
}
