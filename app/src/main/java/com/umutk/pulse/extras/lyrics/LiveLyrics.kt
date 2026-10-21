package com.umutk.pulse.extras.lyrics

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.MusicSettings
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.extras.ui.SwitchRow
import com.umutk.pulse.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live lyrics of the playing track, shown in three places that are switched on separately (prefs "lyrics_live"):
 * the player ([LiveLyricsLine]), a floating window ([MiniLyrics]) and the media notification (line in [notifLine]).
 */
object LiveLyrics {
    const val PREFS = "lyrics_live"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun player(c: Context) = prefs(c).getBoolean("player", true)
    fun floating(c: Context) = prefs(c).getBoolean("floating", false)
    // on by default: the current lyric line is the notification text whenever the track has synced lyrics
    fun notification(c: Context) = prefs(c).getBoolean("notification", true)
    // a notification of its own for the lyrics, beside the media one (off by default)
    fun separate(c: Context) = prefs(c).getBoolean("separate", false)
    /** Lyrics found online are written as a .lrc beside the song. */
    fun saveLrc(c: Context) = prefs(c).getBoolean("save_lrc", true)
    /** Also written into the MP3's tag; this rewrites the song file, so it is off until asked for. */
    fun embed(c: Context) = prefs(c).getBoolean("embed_tag", false)
    fun set(c: Context, key: String, on: Boolean) = prefs(c).edit().putBoolean(key, on).apply()

    /** The line the notification shows now; null hides it. Read by [com.umutk.pulse.extras.music.notif.NotifProvider]. */
    @Volatile var notifLine: String? = null

    /** The current line whenever a notification or the lyrics widget asks for it; read by [com.umutk.pulse.widgets.music.LyricsWidget]. */
    @Volatile var currentLine: String? = null

    // one track at a time: the lyrics of the last track asked for
    private var slot: Pair<String, LyricsResult?>? = null

    /** Synced lyrics of [t]: the embedded tag or a sidecar .lrc / .txt first, then the saved and online ones. Null when none. */
    suspend fun resultFor(ctx: Context, t: Track): LyricsResult? {
        slot?.takeIf { it.first == t.path }?.let { return it.second }
        val (artist, title) = guessArtistTitle(t)
        val r = withContext(Dispatchers.IO) {
            Lyrics.localRecord(t.path, title, artist)?.let { Lyrics.toResult(it, "local") }
                ?: Lyrics.forTrack(ctx, title, artist, t.album, t.durationSec * 1000L, t.path)
        }
        val synced = r?.takeIf { it.synced }
        slot = t.path to synced
        return synced
    }

    /**
     * [s] in Unicode "mathematical sans-serif bold" letters and digits: notifications ignore bold spans on many phones, but never these letters.
     * Accented letters keep their accent as a combining mark; letters with no bold form stay plain.
     */
    fun bold(s: String): String {
        val d = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (ch in d) when (ch) {
            in 'A'..'Z' -> sb.appendCodePoint(0x1D5D4 + (ch - 'A'))
            in 'a'..'z' -> sb.appendCodePoint(0x1D5EE + (ch - 'a'))
            in '0'..'9' -> sb.appendCodePoint(0x1D7EC + (ch - '0'))
            else -> sb.append(ch)
        }
        return java.text.Normalizer.normalize(sb.toString(), java.text.Normalizer.Form.NFC)
    }

    /** Line at [posMs] with the lines before and after it; null before the first line. */
    fun lineAt(r: LyricsResult?, posMs: Int): Triple<String?, String, String?>? {
        val lines = r?.lines ?: return null
        val i = lines.indexOfLast { it.timeMs <= posMs }
        if (i < 0) return null
        return Triple(lines.getOrNull(i - 1)?.text, lines[i].text, lines.getOrNull(i + 1)?.text)
    }
}

/** The current line in large text with the neighbours dimmed. The player places it at the bottom of the cover. */
@Composable
fun LiveLyricsLine(track: Track?, positionMs: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (!LiveLyrics.player(ctx)) return
    val res by produceState<LyricsResult?>(null, track?.path) { value = track?.let { LiveLyrics.resultFor(ctx, it) } }
    val cur = LiveLyrics.lineAt(res, positionMs) ?: return
    val size = MusicSettings.lyricsFont(ctx)
    val dim = T.text.copy(alpha = .35f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(cur.first.orEmpty(), color = dim, fontSize = (size - 6).sp, textAlign = TextAlign.Center)
        Text(cur.second, color = T.text, fontSize = size.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(cur.third.orEmpty(), color = dim, fontSize = (size - 6).sp, textAlign = TextAlign.Center)
    }
}

/**
 * Runs beside the playback service: every 2 seconds it updates the notification line and starts the floating window
 * once the overlay permission is granted. [stop] removes the floating window.
 */
class LiveLyricsTicker(private val ctx: Context, private val engine: MusicPlayerEngine, private val changed: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                // a line must reach the notification as it is sung: twice a second while playing
                delay(if (engine.state.value.isPlaying) 500L else 2_000L)
            }
        }
    }

    private suspend fun tick() {
        if (LiveLyrics.floating(ctx) && !MiniLyrics.running && Settings.canDrawOverlays(ctx)) MiniLyrics.start(ctx)
        val st = engine.state.value
        // lyrics are looked up only while a notification or the lyrics widget shows them
        val wanted = LiveLyrics.notification(ctx) || LiveLyrics.separate(ctx) || com.umutk.pulse.widgets.music.LyricsWidget.placed(ctx)
        // the separate lyrics notification: the line with its neighbours, replaced as the song moves
        if (LiveLyrics.separate(ctx) && st.isPlaying && st.current != null) {
            val trio = LiveLyrics.lineAt(LiveLyrics.resultFor(ctx, st.current!!), engine.positionMs + AHEAD_MS)
            postNote(st.current!!, trio)
        } else clearNote()
        val line = if (wanted && st.isPlaying) {
            st.current?.let { LiveLyrics.lineAt(LiveLyrics.resultFor(ctx, it), engine.positionMs + AHEAD_MS) }?.second?.takeIf { it.isNotBlank() }
        } else null
        if (line != LiveLyrics.currentLine) {
            LiveLyrics.currentLine = line
            LiveLyrics.notifLine = if (LiveLyrics.notification(ctx)) line?.let { LiveLyrics.bold(it) } else null
            changed()
        }
    }

    /** The notification is drawn a moment after the tick: the lines are taken a little early so they do not trail the voice. */
    private val AHEAD_MS = 700

    private val nm get() = ctx.getSystemService(android.app.NotificationManager::class.java)
    private var noteText: String? = null

    private fun postNote(t: Track, trio: Triple<String?, String, String?>?) {
        val body = trio?.second?.takeIf { it.isNotBlank() } ?: return clearNote()
        // the current line in bold letters behind a text-style arrow (no emoji picture), the neighbours plain
        val text = listOfNotNull(trio.first?.takeIf { it.isNotBlank() }, "\u25B6\uFE0E " + LiveLyrics.bold(trio.second), trio.third?.takeIf { it.isNotBlank() }).joinToString("\n")
        if (text == noteText) return
        noteText = text
        val bold = text
        val boldLine = LiveLyrics.bold(trio.second)
        nm.createNotificationChannel(android.app.NotificationChannel("pulse_lyrics", tr("Lyrics", "Sözler"), android.app.NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) })
        val open = android.app.PendingIntent.getActivity(ctx, 4106, android.content.Intent().setClassName(ctx, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "music").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(4106, androidx.core.app.NotificationCompat.Builder(ctx, "pulse_lyrics").setSmallIcon(com.umutk.pulse.R.drawable.ic_notif_music)
            .setContentTitle(t.title ?: t.path.substringAfterLast('/')).setContentText(boldLine).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(bold))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(open).setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT).build())
    }

    private fun clearNote() { if (noteText != null) { noteText = null; nm.cancel(4106) } }

    fun stop() {
        clearNote()
        scope.cancel()
        MiniLyrics.stop(ctx)
        LiveLyrics.notifLine = null
        LiveLyrics.currentLine = null
    }
}

/** The three switches of the music settings: player, floating window, notification. */
@Composable
fun LiveLyricsSettingsSection() {
    val c = LocalContext.current
    var player by remember { mutableStateOf(LiveLyrics.player(c)) }
    var floating by remember { mutableStateOf(LiveLyrics.floating(c)) }
    var notif by remember { mutableStateOf(LiveLyrics.notification(c)) }
    SettingsSection(tr("Live lyrics", "Canlı sözler")) {
        SwitchRow(tr("Show in player", "Oynatıcıda göster"), player) { player = it; LiveLyrics.set(c, "player", it) }
        // without the overlay permission this opens its Settings page; the window appears once it is granted
        SwitchRow(tr("Show floating", "Yüzen pencerede göster"), floating) {
            floating = it; LiveLyrics.set(c, "floating", it)
            if (it) MiniLyrics.start(c) else MiniLyrics.stop(c)
        }
        SwitchRow(tr("Show in notification", "Bildirimde göster"), notif) { notif = it; LiveLyrics.set(c, "notification", it) }
        // a second notification of its own: the current line with the one before and after it
        var sep by remember { mutableStateOf(LiveLyrics.separate(c)) }
        var save by remember { mutableStateOf(LiveLyrics.saveLrc(c)) }
        SwitchRow(tr("Save found lyrics as a .lrc file beside the song", "Bulunan sözleri şarkının yanına .lrc dosyası olarak kaydet"), save) { save = it; LiveLyrics.set(c, "save_lrc", it) }
        var emb by remember { mutableStateOf(LiveLyrics.embed(c)) }
        SwitchRow(tr("Also write into the song's tag (MP3, rewrites the file)", "Şarkının etiketine de yaz (MP3, dosyayı yeniden yazar)"), emb) { emb = it; LiveLyrics.set(c, "embed_tag", it) }
        SwitchRow(tr("Separate lyrics notification", "Ayrı söz bildirimi"), sep) { sep = it; LiveLyrics.set(c, "separate", it) }
    }
}
