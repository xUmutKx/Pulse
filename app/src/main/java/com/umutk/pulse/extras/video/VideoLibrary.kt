package com.umutk.pulse.extras.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Video.Media as V
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** One video: a MediaStore row, a picked document or a URL ([durMs] 0 when unknown). Sizes in bytes, times in seconds. */
data class VideoItem(
    val uri: Uri, val name: String, val durMs: Long, val folder: String,
    val path: String = "", val size: Long = 0, val added: Long = 0, val modified: Long = 0,
    val w: Int = 0, val h: Int = 0, val mime: String = "",
) {
    val key: String get() = uri.toString()
    val ext: String get() = name.substringAfterLast('.', "").uppercase()
    val local: Boolean get() = uri.scheme == "content" && uri.authority == "media"
}

/** All videos on the device, newest first. Needs READ_MEDIA_VIDEO; empty when it is missing. */
fun queryVideos(ctx: Context): List<VideoItem> {
    val out = ArrayList<VideoItem>()
    val cols = arrayOf(
        V._ID, V.DISPLAY_NAME, V.DURATION, V.BUCKET_DISPLAY_NAME,
        V.RELATIVE_PATH, V.SIZE, V.DATE_ADDED, V.DATE_MODIFIED, V.WIDTH, V.HEIGHT, V.MIME_TYPE,
    )
    runCatching {
        ctx.contentResolver.query(V.EXTERNAL_CONTENT_URI, cols, null, null, V.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext()) {
                out += VideoItem(
                    Uri.withAppendedPath(V.EXTERNAL_CONTENT_URI, c.getLong(0).toString()),
                    c.getString(1).orEmpty(), c.getLong(2), c.getString(3).orEmpty(),
                    c.getString(4).orEmpty(), c.getLong(5), c.getLong(6), c.getLong(7), c.getInt(8), c.getInt(9), c.getString(10).orEmpty(),
                )
            }
        }
    }
    return out
}

/** 1.4 GB, 320 MB, 12 KB. */
fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}

/** Opens the system share sheet for one video. */
fun shareVideo(ctx: Context, v: VideoItem) {
    runCatching {
        val i = Intent(Intent.ACTION_SEND).setType(v.mime.ifBlank { "video/*" }).putExtra(Intent.EXTRA_STREAM, v.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Rename in MediaStore; false when Android wants the write permission first (the caller asks with createWriteRequest and retries). */
fun renameVideo(ctx: Context, v: VideoItem, newName: String): Boolean = try {
    val cv = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, newName) }
    ctx.contentResolver.update(v.uri, cv, null, null) > 0
} catch (e: SecurityException) { false } catch (e: Exception) { false }

// about 8 MB of thumbnails, by bytes
private val thumbs = object : LruCache<String, ImageBitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/** Thumbnail of a MediaStore video, loaded off the main thread; null while loading or when none exists. */
@Composable
fun videoThumb(item: VideoItem): ImageBitmap? {
    val ctx = LocalContext.current
    val key = item.uri.toString()
    val bmp by produceState(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            if (android.os.Build.VERSION.SDK_INT < 29) null else runCatching { ctx.contentResolver.loadThumbnail(item.uri, Size(320, 180), null).asImageBitmap() }.getOrNull()
        }?.also { thumbs.put(key, it) }
    }
    return bmp
}

private fun bool(sp: () -> SharedPreferences, k: String, d: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = sp().getBoolean(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { sp().edit().putBoolean(k, value).apply() }
}
private fun int(sp: () -> SharedPreferences, k: String, d: Int) = object : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = sp().getInt(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { sp().edit().putInt(k, value).apply() }
}
private fun flt(sp: () -> SharedPreferences, k: String, d: Float) = object : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = sp().getFloat(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { sp().edit().putFloat(k, value).apply() }
}
private fun str(sp: () -> SharedPreferences, k: String, d: String) = object : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = sp().getString(k, d) ?: d
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { sp().edit().putString(k, value).apply() }
}

/** Saved resume positions, recent videos and every player and library option (the "video_player" file; the widget reads last_uri and r_<hash>). */
class VideoPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("video_player", Context.MODE_PRIVATE)
    private val g = { sp }

    fun resume(key: String): Long = if (resumeOn) sp.getLong("r_" + key.hashCode(), 0L) else 0L

    /** Forgets the position near the start or the end, so a finished film starts over. */
    fun setResume(key: String, pos: Long, dur: Long) {
        if (incognito) return
        val e = sp.edit()
        if (pos < 5000 || (dur > 0 && pos > dur - 5000)) e.remove("r_" + key.hashCode()) else e.putLong("r_" + key.hashCode(), pos)
        if (pos >= 5000) e.putBoolean("p_" + key.hashCode(), true)
        // the home-screen video widget shows the last video and its Resume button opens it
        e.putString("last_uri", key).putLong("last_dur", dur)
        e.apply()
    }

    /** Played before (any position of 5 s or more was saved). */
    fun played(key: String): Boolean = sp.getBoolean("p_" + key.hashCode(), false)

    /** Saved position as 0..1 of [dur]; 0 when none. */
    fun progress(key: String, dur: Long): Float = if (dur <= 0) 0f else (sp.getLong("r_" + key.hashCode(), 0L).toFloat() / dur).coerceIn(0f, 1f)

    fun forget(key: String) { sp.edit().remove("r_" + key.hashCode()).remove("p_" + key.hashCode()).apply() }

    /** Recently played, newest first; one line per video: uri, name, duration, folder separated by TAB. */
    fun recent(): List<VideoItem> = (sp.getString("recent", "") ?: "").lines().mapNotNull { l ->
        val p = l.split('\t'); if (p.size < 4) null else VideoItem(Uri.parse(p[0]), p[1], p[2].toLongOrNull() ?: 0, p[3])
    }

    fun addRecent(v: VideoItem) {
        if (incognito) return
        val line = listOf(v.key, v.name, v.durMs, v.folder).joinToString("\t").replace("\n", " ")
        val rest = (sp.getString("recent", "") ?: "").lines().filter { it.isNotBlank() && !it.startsWith(v.key + "\t") }
        sp.edit().putString("recent", (listOf(line) + rest).take(recentMax).joinToString("\n")).apply()
    }

    fun clearRecent() { sp.edit().remove("recent").apply() }

    /** Recent list, saved positions and played marks, the widget's last video. */
    fun clearHistory() {
        val e = sp.edit().remove("recent").remove("last_uri").remove("last_dur")
        sp.all.keys.filter { it.startsWith("r_") || it.startsWith("p_") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun isSetting(k: String) = !(k.startsWith("r_") || k.startsWith("p_") || k in setOf("recent", "urls", "last_uri", "last_dur"))

    /** Every option as JSON: key -> [type, value]. History, addresses and resume positions stay out. */
    fun exportJson(): String {
        val o = org.json.JSONObject()
        sp.all.forEach { (k, v) ->
            if (!isSetting(k)) return@forEach
            when (v) {
                is Boolean -> o.put(k, org.json.JSONArray().put("b").put(v))
                is Int -> o.put(k, org.json.JSONArray().put("i").put(v))
                is Float -> o.put(k, org.json.JSONArray().put("f").put(v.toDouble()))
                is String -> o.put(k, org.json.JSONArray().put("s").put(v))
                is Set<*> -> o.put(k, org.json.JSONArray().put("t").put(org.json.JSONArray(v.toList())))
            }
        }
        return o.toString(1)
    }

    /** Applies a file made by [exportJson]; false when it cannot be read. */
    fun importJson(text: String): Boolean = runCatching {
        val o = org.json.JSONObject(text)
        val e = sp.edit()
        o.keys().forEach { k ->
            if (!isSetting(k)) return@forEach
            val a = o.getJSONArray(k)
            when (a.getString(0)) {
                "b" -> e.putBoolean(k, a.getBoolean(1))
                "i" -> e.putInt(k, a.getInt(1))
                "f" -> e.putFloat(k, a.getDouble(1).toFloat())
                "s" -> e.putString(k, a.getString(1))
                "t" -> e.putStringSet(k, a.getJSONArray(1).let { r -> (0 until r.length()).map { r.getString(it) }.toSet() })
            }
        }
        e.apply(); true
    }.getOrDefault(false)

    /** Back to the defaults for every option (history stays). */
    fun resetAll() {
        val e = sp.edit()
        sp.all.keys.filter { isSetting(it) }.forEach { e.remove(it) }
        e.apply()
    }

    /** Typed network addresses, newest first. */
    fun urls(): List<String> = (sp.getString("urls", "") ?: "").lines().filter { it.isNotBlank() }
    fun addUrl(u: String) { sp.edit().putString("urls", (listOf(u) + urls().filter { it != u }).take(15).joinToString("\n")).apply() }
    fun clearUrls() { sp.edit().remove("urls").apply() }

    /** Folders hidden from the library (their path, or the bucket name when the path is unknown). */
    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("hidden", v).apply() }

    /** folders shown alone (empty = all); [hidden] holds the excluded ones */
    var include: Set<String>
        get() = sp.getStringSet("include", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("include", v).apply() }

    // library
    var showHidden by bool(g, "show_hidden", false)
    /** videos shorter than this many seconds are not listed */
    var minDur by int(g, "min_dur", 0)
    var recentMax by int(g, "recent_max", 40)
    /** 0 folders, 1 all files, 2 tree */
    var browse by int(g, "browse", 0)
    /** 0 title, 1 date added, 2 duration, 3 size, 4 path, 5 resolution, 6 date modified */
    var sortBy by int(g, "sort_by", 1)
    var sortDesc by bool(g, "sort_desc", true)
    var grid by bool(g, "grid", false)
    var showThumb by bool(g, "s_thumb", true)
    var showDur by bool(g, "s_dur", true)
    var showExt by bool(g, "s_ext", false)
    var showPath by bool(g, "s_path", false)
    var showSize by bool(g, "s_size", false)
    var showRes by bool(g, "s_res", false)
    var showProgress by bool(g, "s_prog", true)
    var markNew by bool(g, "mark_new", true)
    var showRecent by bool(g, "s_recent", true)

    // player
    var skin by str(g, "skin", "next")
    var bg by bool(g, "bg", false)
    /** 0 fit, 1 stretch, 2 crop, 3 16:9, 4 4:3 */
    var aspect by int(g, "aspect", 0)
    /** 0 system, 1 landscape, 2 portrait, 3 by the video, 4 reverse landscape, 5 any direction */
    var orient by int(g, "orient", 3)
    /** 0 off, 1 one, 2 all */
    var repeat by int(g, "repeat", 0)
    var loop: Boolean
        get() = repeat == 1
        set(v) { repeat = if (v) 1 else 0 }
    var resumeOn by bool(g, "resume_on", true)
    var autoPlay by bool(g, "autoplay", true)
    var speed by flt(g, "speed", 1f)
    var defSpeed by flt(g, "def_speed", 1f) // start speed when "Remember speed" is off
    var rememberSpeed by bool(g, "remember_speed", false)
    var lpSpeed by flt(g, "lp_speed", 2f)
    var gestSeek by bool(g, "g_seek", true)
    var gestVol by bool(g, "g_vol", true)
    var gestBright by bool(g, "g_bright", true)
    var gestZoom by bool(g, "g_zoom", true)
    var gestLong by bool(g, "g_long", true)
    /** 0 off, 1 seek on the sides, 2 pause, 3 both */
    var dtap by int(g, "dtap", 3)
    var dtapSec by int(g, "dtap_sec", 10)
    /** seconds crossed by a swipe over the whole width */
    var seekSens by int(g, "seek_sens", 90)
    var ctrlTimeout by int(g, "ctrl_ms", 3500)
    var showRemaining by bool(g, "remaining", false)
    var rememberBright by bool(g, "rem_bright", false)
    var bright by flt(g, "bright", -1f)
    var autoPip by bool(g, "auto_pip", true)
    var pauseNoisy by bool(g, "noisy", true)
    var audioFocus by bool(g, "focus", true)
    var skipSilence by bool(g, "skip_silence", false)
    /** loudness boost in millibel, 0..1500 */
    var boost by int(g, "boost", 0)
    /** 0 HW+ (device first, extensions allowed), 1 SW (software first), 2 HW (device only) */
    var decoder by int(g, "decoder", 0)
    var fastSeek by bool(g, "fast_seek", false)
    var seekPreview by bool(g, "seek_preview", true)
    var eqOn by bool(g, "eq_on", false)
    /** equalizer band levels in millibel, comma separated, band order */
    var eqLevels by str(g, "eq_lv", "")
    var keepAwake by bool(g, "keep_awake", true)
    /** home-screen video widget look, 0..49 */
    var widgetLook by int(g, "widget_look", 0)

    // audio and subtitles
    var prefAudio by str(g, "pref_audio", "")
    var prefSub by str(g, "pref_sub", "")
    var rememberSel by bool(g, "remember_sel", true)
    var subSize by int(g, "sub_size", 20)
    var subBg by bool(g, "sub_bg", false)
    var subBold by bool(g, "sub_bold", false)
    var subEdge by bool(g, "sub_edge", true)
    var subEnc by str(g, "sub_enc", "UTF-8")
    var subAuto by bool(g, "sub_auto", true)
    var subAutoSel by bool(g, "sub_autosel", true)
    /** 0 sans, 1 serif, 2 mono, 3 casual, 4 cursive */
    var subFont by int(g, "sub_font", 0)
    var subColor by int(g, "sub_color", 0)
    /** 1 outline, 2 shadow, 3 raised, 4 depressed (used when [subEdge] is on) */
    var subEdgeKind by int(g, "sub_edge_kind", 1)
    /** background opacity, percent */
    var subBgAlpha by int(g, "sub_bg_alpha", 69)
    /** distance from the bottom, percent of the height */
    var subPos by int(g, "sub_pos", 8)
    /** side margin in dp */
    var subMargin by int(g, "sub_margin", 0)
    /** true: use only the options here, false: keep the colours and sizes of the subtitle file */
    var subIgnoreStyle by bool(g, "sub_ignore", true)

    // player extras
    var rememberVolume by bool(g, "rem_vol", false)
    var volume by int(g, "volume", -1)
    var pauseCall by bool(g, "pause_call", true)
    var showClock by bool(g, "s_clock", false)
    var showBattery by bool(g, "s_battery", false)
    /** brightness and volume swipe strength, percent */
    var gestSens by int(g, "gest_sens", 100)

    // audio
    /** player volume ceiling, percent */
    var volCap by int(g, "vol_cap", 100)
    var downmix by bool(g, "downmix", false)
    var pitchFix by bool(g, "pitch_fix", true)

    // decoder
    var tunnel by bool(g, "tunnel", false)
    /** 0 auto, 1 asynchronous queueing on, 2 off */
    var asyncCodec by int(g, "async_codec", 0)

    // network
    var userAgent by str(g, "ua", "")
    var bufMin by int(g, "buf_min", 50)
    var bufMax by int(g, "buf_max", 50)
    var bufPlay by int(g, "buf_play", 25)
    var connTimeout by int(g, "conn_to", 8)
    var readTimeout by int(g, "read_to", 8)
    var cacheMb by int(g, "cache_mb", 0)

    // display
    var matchRate by bool(g, "match_rate", true)
    var hdrOut by bool(g, "hdr_out", false)
    var brightFixed by bool(g, "bright_fixed", false)
    var brightLevel by int(g, "bright_level", 60)

    // privacy
    var incognito by bool(g, "incognito", false)
    var lockOn by bool(g, "lock_on", false)
}

/** m:ss, or h:mm:ss for a film. */
fun clock(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Entries of an .m3u / .pls list (http, file or content address); relative lines resolve against the list's own address. Empty when unreadable. */
fun readPlaylist(ctx: Context, list: Uri): List<Uri> = runCatching {
    val text = if (list.scheme == "http" || list.scheme == "https") java.net.URL(list.toString()).openStream().bufferedReader().use { it.readText() }
    else ctx.contentResolver.openInputStream(list)!!.bufferedReader().use { it.readText() }
    text.lines().map { it.trim() }.mapNotNull { l ->
        val v = if (l.startsWith("File", true) && '=' in l) l.substringAfter('=').trim() else l
        if (v.isEmpty() || v.startsWith("#") || v.startsWith("[") || '=' in v && !v.contains("://")) null
        else if (v.contains("://")) Uri.parse(v) else Uri.parse(list.toString().substringBeforeLast('/') + "/" + Uri.encode(v, "/"))
    }.take(500)
}.getOrDefault(emptyList())
