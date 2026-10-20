package com.umutk.pulse.extras.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale
import java.util.UUID

/** Attachment files: images copied in, sketches and audio recorded here. Names keep their kind as a prefix (im_, sk_, au_). */
object NotesMedia {
    fun copyIn(c: Context, uri: Uri): String? {
        val mime = c.contentResolver.getType(uri).orEmpty()
        val ext = when { "png" in mime -> "png"; "webp" in mime -> "webp"; "gif" in mime -> "gif"; else -> "jpg" }
        val name = "im_${UUID.randomUUID()}.$ext"
        val out = File(NotesStore.attachments(c), name)
        return try {
            c.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: return null
            name
        } catch (e: Exception) { out.delete(); null }
    }

    /** A decoded copy at half size, enough for the editor and the thumbnails. */
    fun image(c: Context, name: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(File(NotesStore.attachments(c), name).path, BitmapFactory.Options().apply { inSampleSize = 2 })
    }.getOrNull()

    /** A second copy of an attachment, so deleting one note never removes the file of its duplicate. */
    fun copyOf(c: Context, name: String): String? {
        val dir = NotesStore.attachments(c)
        val copy = "${name.substringBefore('_')}_${UUID.randomUUID()}.${name.substringAfterLast('.')}"
        return runCatching { File(dir, name).copyTo(File(dir, copy), overwrite = true); copy }.getOrNull()
    }

    fun remove(c: Context, name: String) { File(NotesStore.attachments(c), name).delete() }
    fun file(c: Context, name: String): File = File(NotesStore.attachments(c), name)
}

/** Records one audio attachment (AAC in an MP4 container). Start and stop are separate, and a failed stop drops the file. */
class NRecorder(private val c: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start(): Boolean {
        val f = File(NotesStore.attachments(c), "au_${UUID.randomUUID()}.m4a")
        return try {
            val r = MediaRecorder(c)
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
            recorder = r; file = f
            true
        } catch (e: Exception) { f.delete(); release(); false }
    }

    /** The file name of the finished recording, or null when it was too short to keep. */
    fun stop(): String? {
        val r = recorder ?: return null
        val f = file
        recorder = null; file = null
        return try { r.stop(); r.release(); f?.name } catch (e: Exception) { r.release(); f?.delete(); null }
    }

    fun release() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null; file = null
    }
}

/** Plays one audio attachment; [onEnd] runs when it finishes. */
class NPlayer(private val c: Context, private val name: String, private val onEnd: () -> Unit) {
    private var player: MediaPlayer? = null

    fun toggle(): Boolean {
        player?.let { stop(); return false }
        return try {
            val p = MediaPlayer()
            p.setDataSource(NotesMedia.file(c, name).path)
            p.setOnCompletionListener { stop(); onEnd() }
            p.prepare(); p.start()
            player = p
            true
        } catch (e: Exception) { false }
    }

    fun stop() { player?.let { runCatching { it.stop() }; it.release() }; player = null }
}

/** Reads a note aloud with the phone's text-to-speech engine. */
class NSpeaker(c: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(c) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.getDefault()
        }
    }

    fun speak(text: String) { if (ready && text.isNotBlank()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "note") }
    fun stop() { tts?.stop() }
    fun release() { tts?.stop(); tts?.shutdown(); tts = null; ready = false }
}
