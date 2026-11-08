package com.umutk.pulse.ai

import android.content.Context
import android.os.ParcelFileDescriptor
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The chat model in use (llama.cpp through llama.cpp's own Android JNI). One model and one conversation at a time. */
object Engine {
    private var held: ParcelFileDescriptor? = null
    var loadedId: String? = null; private set
    private val lock = Mutex()

    fun engine(c: Context): InferenceEngine = AiChat.getInferenceEngine(c.applicationContext)

    /** The model picked in the Models tab (or a file picked by hand): its path, or null when none is there. */
    fun chosenPath(c: Context): Pair<String, String>? {
        val id = Store.active(c, "chat") ?: Device.recommended(c, CHAT_MODELS.filter { Store.has(c, it) })?.id ?: return null
        if (id == "custom") { val u = Store.customUri(c, "chat") ?: return null; return Store.openUri(c, u) { keep(it) }?.let { id to it } }
        val m = CHAT_MODELS.firstOrNull { it.id == id && Store.has(c, it) } ?: CHAT_MODELS.firstOrNull { Store.has(c, it) } ?: return null
        return Store.openPath(c, m) { keep(it) }?.let { m.id to it }
    }

    private fun keep(p: ParcelFileDescriptor) { held?.let { runCatching { it.close() } }; held = p }

    /** Starts a new conversation with [system] as its instructions (the model is loaded again so nothing of the last one stays). */
    suspend fun start(c: Context, system: String): Boolean = lock.withLock {
        val e = engine(c)
        e.state.first { it !is InferenceEngine.State.Uninitialized && it !is InferenceEngine.State.Initializing }
        if (e.state.value.isModelLoaded || e.state.value is InferenceEngine.State.Error) runCatching { e.cleanUp() }
        val (id, path) = chosenPath(c) ?: return false
        e.loadModel(path)
        loadedId = id
        if (system.isNotBlank()) e.setSystemPrompt(system)
        true
    }

    fun ask(c: Context, text: String, max: Int = 1024): Flow<String> = engine(c).sendUserPrompt(text, max)

    /** One answer to one question in a fresh conversation (the text tools). */
    suspend fun once(c: Context, system: String, text: String, max: Int = 512): String? {
        if (!start(c, system)) return null
        val sb = StringBuilder()
        ask(c, text, max).collect { sb.append(it) }
        return sb.toString().trim()
    }
}

/** The instructions a chat starts with. */
fun systemPrompt(c: Context): String = c.getSharedPreferences("chat", Context.MODE_PRIVATE).getString("system", null)
    ?: tr("You are Pulse AI, a helpful assistant running offline on this phone. Answer in the user's language, clearly and briefly.",
        "Sen Pulse AI'sın, bu telefonda çevrimdışı çalışan yardımcı bir asistansın. Kullanıcının dilinde, açık ve kısa yanıt ver.")
