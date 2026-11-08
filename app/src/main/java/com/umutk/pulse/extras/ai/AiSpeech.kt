package com.umutk.pulse.extras.ai

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.umutk.pulse.ai.stt.AudioDecode
import com.umutk.pulse.ai.stt.Segment
import com.umutk.pulse.ai.stt.Whisper
import com.umutk.pulse.ai.stt.toLrc
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Speech to text with the chosen whisper model: a song becomes timed lyrics (LRC). */
object Transcriber {
    /** A speech model is there (downloaded, found on the phone or picked by hand). */
    fun ready(c: Context): Boolean = aiSupported && (Store.customUri(c, "speech") != null || Store.known(c, "speech").any { Store.has(c, it) })

    /** The speech model to use: the one picked, else the biggest one there. Null when none is there. */
    fun modelPath(c: Context, hold: (ParcelFileDescriptor) -> Unit): String? {
        val id = Store.active(c, "speech")
        if (id == "custom") Store.customUri(c, "speech")?.let { return Store.openUri(c, it, hold) }
        val all = Store.known(c, "speech")
        val m = all.firstOrNull { it.id == id && Store.has(c, it) } ?: all.filter { Store.has(c, it) }.maxByOrNull { it.bytes } ?: return null
        return Store.openPath(c, m, hold)
    }

    /** Set by [stop]: the song being listened to is left at once (the chunk that is running is cut short). */
    @Volatile private var stopped = false

    /**
     * [stage] gets the step and 0..1 while it works (the listening step reads [progress]). Returns the LRC text, or null without a model or when stopped.
     * The song is read and listened to in chunks of four minutes, so a long song never fills the memory at once; the times carry on from one chunk to the next.
     */
    suspend fun lrc(c: Context, audio: Uri, lang: String, title: String?, artist: String?, stage: (String, Float) -> Unit): String? = withContext(Dispatchers.Default) {
        if (!aiSupported) return@withContext null
        var pfd: ParcelFileDescriptor? = null
        stopped = false
        try {
            val path = modelPath(c) { pfd = it } ?: return@withContext null
            stage(tr("Loading the speech model", "Ses modeli yükleniyor"), 0f)
            Whisper.open(path).use { w ->
                // the title and the artist help the model with names it would not know
                val prompt = listOfNotNull(title, artist).filter { it.isNotBlank() }.joinToString(" - ").ifEmpty { null }
                val segs = ArrayList<Segment>()
                var offsetMs = 0L
                try {
                    AudioDecode.pcm16kChunks(c, audio, 240, { stage(tr("Reading the song", "Şarkı okunuyor"), it) }) { chunk ->
                        if (stopped) throw Stopped()
                        stage(LISTENING, 0f)
                        w.transcribe(chunk, lang, prompt).forEach { sg -> segs += Segment(sg.startMs + offsetMs, sg.endMs + offsetMs, sg.text) }
                        offsetMs += chunk.size * 1000L / 16000
                    }
                } catch (e: Stopped) { return@use null }
                if (stopped) null else toLrc(segs, title, artist)
            }
        } catch (e: Stopped) { null }
        finally { runCatching { pfd?.close() } }
    }

    private class Stopped : Exception("stopped")

    val LISTENING get() = tr("Listening", "Dinleniyor")
    /** 0..1 of the listening step. */
    fun progress(): Float = runCatching { Whisper.progress() / 100f }.getOrDefault(0f)
    fun stop() { stopped = true; runCatching { Whisper.abort() } }
}
