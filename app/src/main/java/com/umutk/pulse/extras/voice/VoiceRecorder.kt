package com.umutk.pulse.extras.voice

import android.content.ClipData
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One saved recording in Music/Recordings. */
data class Recording(val uri: Uri, val name: String, val millis: Long, val added: Long, val bytes: Long)

/** The extra app's own settings; Pulse's Cfg/Opt are never written from here. */
object VoicePrefs {
    private fun p(c: Context) = c.getSharedPreferences("voice_prefs", Context.MODE_PRIVATE)
    fun quality(c: Context): Int = p(c).getInt("quality", 1)
    fun setQuality(c: Context, v: Int) { p(c).edit().putInt("quality", v).apply() }
    fun sort(c: Context): String = p(c).getString("sort", "newest") ?: "newest"
    fun setSort(c: Context, v: String) { p(c).edit().putString("sort", v).apply() }
    fun autoStopMin(c: Context): Int = p(c).getInt("autostop", 0)
    fun setAutoStopMin(c: Context, v: Int) { p(c).edit().putInt("autostop", v).apply() }
    fun countdown(c: Context): Boolean = p(c).getBoolean("countdown", false)
    fun setCountdown(c: Context, v: Boolean) { p(c).edit().putBoolean("countdown", v).apply() }
    fun accent(c: Context): Int = p(c).getInt("accent", 0)
    fun setAccent(c: Context, v: Int) { p(c).edit().putInt("accent", v).apply() }
    /** Where playback of a take stopped, in ms; 0 clears it. */
    fun savePos(c: Context, uri: String, ms: Long) { p(c).edit().putLong("pos_$uri", ms).apply() }
    fun pos(c: Context, uri: String): Long = p(c).getLong("pos_$uri", 0L)
    fun favs(c: Context): Set<String> = p(c).getStringSet("favs", emptySet()).orEmpty().toSet()
    fun toggleFav(c: Context, uri: String) {
        val s = favs(c).toMutableSet()
        if (!s.add(uri)) s.remove(uri)
        p(c).edit().putStringSet("favs", s).apply()
    }
}

/** Sample rate and bit rate for quality 0 (low), 1 (standard) and 2 (high). */
private val RATES = arrayOf(22_050 to 48_000, 44_100 to 128_000, 48_000 to 256_000)

/** Recordings live in MediaStore under Music/Recordings, so the Music player and other apps see them too. */
object VoiceStore {
    const val FOLDER = "Music/Recordings"
    private val collection get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    fun list(c: Context): List<Recording> {
        val out = mutableListOf<Recording>()
        val proj = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.SIZE)
        c.contentResolver.query(collection, proj, "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("$FOLDER%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cur ->
            while (cur.moveToNext()) {
                out += Recording(ContentUris.withAppendedId(collection, cur.getLong(0)), cur.getString(1).orEmpty(),
                    cur.getLong(2), cur.getLong(3), cur.getLong(4))
            }
        }
        return out
    }

    /** Renames the file; the extension is kept. False when the provider refuses (name taken, not our file). */
    fun rename(c: Context, r: Recording, base: String): Boolean {
        val clean = base.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").ifEmpty { return false }
        val ext = r.name.substringAfterLast('.', "m4a")
        val v = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, "$clean.$ext") }
        return runCatching { c.contentResolver.update(r.uri, v, null, null) > 0 }.getOrDefault(false)
    }

    fun delete(c: Context, uri: Uri): Boolean = runCatching { c.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    /** A second copy of the take in the same folder. */
    fun duplicate(c: Context, r: Recording): Boolean {
        val base = r.name.substringBeforeLast('.')
        return copyTo(c, r.uri, collection, FOLDER, "${base}_copy.m4a", "audio/mp4")
    }

    /** Saves a copy in Download/Pulse, where the file manager shows it. */
    fun exportToDownloads(c: Context, r: Recording): Boolean =
        android.os.Build.VERSION.SDK_INT >= 29 && copyTo(c, r.uri, MediaStore.Downloads.EXTERNAL_CONTENT_URI, "Download/Pulse", r.name, "audio/mp4")

    private fun copyTo(c: Context, src: Uri, coll: Uri, rel: String, name: String, mime: String): Boolean {
        val v = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dst = c.contentResolver.insert(coll, v) ?: return false
        val ok = runCatching {
            c.contentResolver.openInputStream(src)!!.use { input ->
                c.contentResolver.openOutputStream(dst)!!.use { input.copyTo(it) }
            }
        }.isSuccess
        if (!ok) { c.contentResolver.delete(dst, null, null); return false }
        c.contentResolver.update(dst, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return true
    }

    /** A share sheet for one take, with the read grant attached so the receiving app can open it. */
    fun shareIntent(r: Recording): Intent {
        val i = Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, r.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri(r.name, r.uri)
        return Intent.createChooser(i, null)
    }
}

/** Records one take with MediaRecorder: AAC in MP4. Pause and resume keep the same file. */
class VoiceEngine(private val c: Context) {
    private var rec: MediaRecorder? = null
    private var uri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null

    /** Starts a new take. False when the microphone or the file cannot be opened (the caller shows a message). */
    fun start(): Boolean {
        if (rec != null) return false
        if (android.os.Build.VERSION.SDK_INT < 29) return false   // the voice recorder stores its takes with the Android 10 media store
        val name = "rec_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".m4a"
        val v = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, VoiceStore.FOLDER)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val coll = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val u = c.contentResolver.insert(coll, v) ?: return false
        val fd = runCatching { c.contentResolver.openFileDescriptor(u, "w") }.getOrNull()
        if (fd == null) { c.contentResolver.delete(u, null, null); return false }
        val q = VoicePrefs.quality(c).coerceIn(0, RATES.lastIndex)
        @Suppress("DEPRECATION") val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(c) else MediaRecorder()
        val ok = runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(RATES[q].first)
            r.setAudioEncodingBitRate(RATES[q].second)
            r.setOutputFile(fd.fileDescriptor)
            r.prepare()
            r.start()
        }.isSuccess
        if (!ok) {
            runCatching { r.release() }
            fd.close()
            c.contentResolver.delete(u, null, null)
            return false
        }
        rec = r; uri = u; pfd = fd
        return true
    }

    fun pause() { if (android.os.Build.VERSION.SDK_INT >= 24) runCatching { rec?.pause() } }
    fun resume() { if (android.os.Build.VERSION.SDK_INT >= 24) runCatching { rec?.resume() } }

    /** Peak input level since the last call, 0 to 32767; drives the live waveform. */
    fun level(): Int {
        val r = rec ?: return 0
        return runCatching { r.maxAmplitude }.getOrDefault(0)
    }

    /** Ends the take and publishes the file. A take too short to hold data is removed; returns the saved uri or null. */
    fun stop(): Uri? {
        val r = rec ?: return null
        val u = uri
        val fine = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        pfd?.close()
        rec = null; uri = null; pfd = null
        if (u == null) return null
        if (!fine) { VoiceStore.delete(c, u); return null }
        val v = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        c.contentResolver.update(u, v, null, null)
        return u
    }
}

/** Plays one take at a time; speed, looping and the end-of-take callback live here. */
class VoicePlayer(private val c: Context) {
    private var mp: MediaPlayer? = null
    var uri: Uri? = null
        private set
    var onEnd: () -> Unit = {}

    fun play(u: Uri, speed: Float, loop: Boolean): Boolean {
        stop()
        val p = runCatching { MediaPlayer().apply { setDataSource(c, u); prepare() } }.getOrNull() ?: return false
        p.isLooping = loop
        p.setOnCompletionListener { if (!it.isLooping) { uri = null; onEnd() } }
        if (android.os.Build.VERSION.SDK_INT >= 23) runCatching { p.playbackParams = PlaybackParams().setSpeed(speed) }
        p.start()
        mp = p; uri = u
        return true
    }

    fun setSpeed(s: Float) { if (android.os.Build.VERSION.SDK_INT >= 23) runCatching { mp?.playbackParams = PlaybackParams().setSpeed(s) } }
    fun setLoop(on: Boolean) { runCatching { mp?.isLooping = on } }
    fun toggle() { runCatching { mp?.let { if (it.isPlaying) it.pause() else it.start() } } }
    fun isPlaying(): Boolean = runCatching { mp?.isPlaying == true }.getOrDefault(false)
    fun position(): Long = runCatching { mp?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
    fun duration(): Long = runCatching { mp?.duration?.toLong() ?: 0L }.getOrDefault(0L).coerceAtLeast(0L)
    fun seek(ms: Long) { runCatching { mp?.seekTo(ms.toInt()) } }

    fun stop() {
        mp?.let { runCatching { it.stop() }; runCatching { it.release() } }
        mp = null; uri = null
    }
}
