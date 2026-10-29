package com.umutk.pulse.extras.lyrics

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asAndroidBitmap
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
    // a notification of its own for the lyrics, beside the media one (on by default: the lyrics are right there in the shade)
    fun separate(c: Context) = prefs(c).getBoolean("separate", true)
    /** Lyrics found online are written as a .lrc beside the song. */
    fun saveLrc(c: Context) = prefs(c).getBoolean("save_lrc", true)
    /** Also written into the MP3's tag; this rewrites the song file, so it is off until asked for. */
    fun embed(c: Context) = prefs(c).getBoolean("embed_tag", false)
    /** How the notification tells the song's title from the lyric line: "underline" (title underlined, line bold), "thin" (the one-line view shows the line plain, the open view bold), "bold" (both bold). */
    fun emphasis(c: Context) = prefs(c).getString("emphasis", "underline") ?: "underline"
    fun setEmphasis(c: Context, v: String) = prefs(c).edit().putString("emphasis", v).apply()
    /** The line as the one-line (collapsed) notification text. */
    fun collapsed(c: Context, line: String) = if (emphasis(c) == "thin") line else bold(line)
    /** The song title as the notification shows it: underlined when that is the chosen emphasis. */
    fun titled(c: Context, title: CharSequence): CharSequence =
        if (emphasis(c) != "underline") title else android.text.SpannableString(title).also { it.setSpan(android.text.style.UnderlineSpan(), 0, it.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    fun set(c: Context, key: String, on: Boolean) = prefs(c).edit().putBoolean(key, on).apply()

    /** The line the notification shows now; null hides it. Read by [com.umutk.pulse.extras.music.notif.NotifProvider]. */
    @Volatile var notifLine: String? = null

    /** The current line whenever a notification or the lyrics widget asks for it; read by [com.umutk.pulse.widgets.music.LyricsWidget]. */
    @Volatile var currentLine: String? = null

    // one track at a time: the lyrics of the last track asked for
    private var slot: Pair<String, LyricsResult?>? = null
    private var slotAt = 0L

    /** Synced lyrics of [t]: the embedded tag or a sidecar .lrc / .txt first, then the saved and online ones. Null when none. */
    suspend fun resultFor(ctx: Context, t: Track): LyricsResult? {
        // a song that has lyrics keeps them; one that had none (the network was not up yet, the search came back empty) is looked up again after a minute
        slot?.takeIf { it.first == t.path && (it.second != null || android.os.SystemClock.uptimeMillis() - slotAt < 60_000L) }?.let { return it.second }
        val (artist, title) = guessArtistTitle(t)
        val r = withContext(Dispatchers.IO) {
            Lyrics.localRecord(t.path, title, artist)?.let { Lyrics.toResult(it, "local") }
                ?: Lyrics.forTrack(ctx, title, artist, t.album, t.durationSec * 1000L, t.path)
        }
        val synced = r?.takeIf { it.synced }
        slot = t.path to synced; slotAt = android.os.SystemClock.uptimeMillis()
        return synced
    }

    /**
     * [s] in Unicode "mathematical sans-serif bold" letters and digits: notifications ignore bold spans on many phones, but never these letters.
     * Accented letters keep their accent as a combining mark; letters with no bold form stay plain.
     */
    fun bold(s: String): String {
        val sb = StringBuilder()
        // the plain letters and digits change; the Turkish letters are a bold base letter with its mark on top (c + cedilla, g + breve, ...), so they are as heavy as the rest
        fun up(c: Char) = sb.appendCodePoint(0x1D5D4 + (c - 'A'))
        fun lo(c: Char) = sb.appendCodePoint(0x1D5EE + (c - 'a'))
        for (ch in s) when (ch) {
            in 'A'..'Z' -> up(ch)
            in 'a'..'z' -> lo(ch)
            in '0'..'9' -> sb.appendCodePoint(0x1D7EC + (ch - '0'))
            'ç' -> { lo('c'); sb.append('\u0327') }
            'ş' -> { lo('s'); sb.append('\u0327') }
            'ğ' -> { lo('g'); sb.append('\u0306') }
            'ö' -> { lo('o'); sb.append('\u0308') }
            'ü' -> { lo('u'); sb.append('\u0308') }
            'ı' -> sb.appendCodePoint(0x1D6CA)   // bold small iota: a dotless i as heavy as the rest
            'Ç' -> { up('C'); sb.append('\u0327') }
            'Ş' -> { up('S'); sb.append('\u0327') }
            'Ğ' -> { up('G'); sb.append('\u0306') }
            'Ö' -> { up('O'); sb.append('\u0308') }
            'Ü' -> { up('U'); sb.append('\u0308') }
            'İ' -> { up('I'); sb.append('\u0307') }
            else -> sb.append(ch)
        }
        return sb.toString()
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
        if (LiveLyrics.floating(ctx) && !MiniLyrics.running && com.umutk.pulse.Compat.canOverlay(ctx)) MiniLyrics.start(ctx)
        val st = engine.state.value
        // lyrics are looked up only while a notification or the lyrics widget shows them
        val wanted = LiveLyrics.notification(ctx) || LiveLyrics.separate(ctx) || com.umutk.pulse.widgets.music.LyricsWidget.placed(ctx)
        // the separate lyrics notification: the line with its neighbours, replaced as the song moves
        if (LiveLyrics.separate(ctx) && st.isPlaying && st.current != null) {
            val trio = LiveLyrics.lineAt(LiveLyrics.resultFor(ctx, st.current!!), engine.positionMs + AHEAD_MS)
            postNote(st.current!!, trio)
        } else clearNote()
        var line = if (wanted && st.isPlaying) {
            st.current?.let { LiveLyrics.lineAt(LiveLyrics.resultFor(ctx, it), engine.positionMs + AHEAD_MS) }?.second?.takeIf { it.isNotBlank() }
        } else null
        // the gap between two sung lines left the notification blank for a moment; the last line stays up through it, same song only
        if (line == null && wanted && st.isPlaying && st.current?.path == lastLineTrack) line = lastLineText
        if (line != null) { lastLineText = line; lastLineTrack = st.current?.path }
        else if (st.current?.path != lastLineTrack) { lastLineText = null; lastLineTrack = st.current?.path }
        if (line != LiveLyrics.currentLine) {
            LiveLyrics.currentLine = line
            LiveLyrics.notifLine = if (LiveLyrics.notification(ctx)) line?.let { LiveLyrics.collapsed(ctx, it) } else null
            changed()
        }
    }

    /** The notification is drawn a moment after the tick: the lines are taken a little early so they do not trail the voice. */
    private val AHEAD_MS = 700

    // the last line shown and the song it belongs to, so a gap between lines keeps it instead of blanking the notification out
    private var lastLineText: String? = null
    private var lastLineTrack: String? = null

    private val nm get() = ctx.getSystemService(android.app.NotificationManager::class.java)
    private var noteText: String? = null
    private var lastCheck = 0L
    private var artFor: String? = null
    private var art: android.graphics.Bitmap? = null

    private fun postNote(t: Track, trio: Triple<String?, String, String?>?) {
        val body = trio?.second?.takeIf { it.isNotBlank() } ?: return clearNote()
        // the current line in bold letters behind a text-style arrow (no emoji picture), the neighbours plain
        val text = listOfNotNull(trio.first?.takeIf { it.isNotBlank() }, "\u25BA " + LiveLyrics.bold(trio.second), trio.third?.takeIf { it.isNotBlank() }).joinToString("\n")
        // the same line as before: nothing to do, unless the notification was swiped away (checked every few seconds), then it comes back
        val now = android.os.SystemClock.uptimeMillis()
        if (text == noteText) {
            if (now - lastCheck < 3000) return
            lastCheck = now
            if (nm.activeNotifications.any { it.id == 4106 }) return
        }
        lastCheck = now
        noteText = text
        // the whole current line is also set in a real bold style, which covers the letters the Unicode bold cannot (ç ş ğ ı ...) on phones that keep spans
        val bold = android.text.SpannableStringBuilder(text).also { sp ->
            val st = text.indexOf(LiveLyrics.bold(trio.second)).takeIf { it >= 0 }
            if (st != null) sp.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, st + LiveLyrics.bold(trio.second).length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val boldLine = android.text.SpannableString(LiveLyrics.collapsed(ctx, trio.second)).also { it.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, it.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("pulse_lyrics", tr("Lyrics", "Sözler"), android.app.NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false) }) else Unit
        val open = android.app.PendingIntent.getActivity(ctx, 4106, android.content.Intent().setClassName(ctx, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "music").putExtra("notif_open", "lyrics").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK), android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        // the album cover beside the text, read once per song (this runs on the lyrics tick, not on the main thread)
        if (artFor != t.path) { artFor = t.path; art = runCatching { (com.umutk.pulse.extras.music.trackArtLocal(ctx, t) ?: com.umutk.pulse.extras.music.trackArt(ctx, t))?.asAndroidBitmap() }.getOrNull() }
        nm.notify(4106, androidx.core.app.NotificationCompat.Builder(ctx, "pulse_lyrics").setSmallIcon(com.umutk.pulse.R.drawable.ic_notif_music).setLargeIcon(art)
            .setContentTitle(LiveLyrics.titled(ctx, t.title ?: t.path.substringAfterLast('/'))).setContentText(boldLine).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(bold))
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setContentIntent(open).setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT).build())
    }

    private fun clearNote() { if (noteText != null) { noteText = null; nm.cancel(4106) } }

    fun stop() {
        clearNote()
        scope.cancel()
        MiniLyrics.stop(ctx)
        LiveLyrics.notifLine = null
        LiveLyrics.currentLine = null
        lastLineText = null; lastLineTrack = null
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
        // how the title and the lyric line differ in the notification
        var emph by remember { mutableStateOf(LiveLyrics.emphasis(c)) }
        androidx.compose.foundation.layout.Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("underline" to tr("Underlined title", "Başlık altı çizili"), "thin" to tr("Thin line, bold when open", "İnce satır, açılınca kalın"), "bold" to tr("Always bold", "Hep kalın")).forEach { (k, n) ->
                com.umutk.pulse.PChip(emph == k, n) { emph = k; LiveLyrics.setEmphasis(c, k); com.umutk.pulse.extras.music.MusicPlaybackService.relayout() }
            }
        }
        SwitchRow(tr("Separate lyrics notification", "Ayrı söz bildirimi"), sep) { sep = it; LiveLyrics.set(c, "separate", it) }
        var autoAll by remember { mutableStateOf(LyricsAuto.on(c)) }
        SwitchRow(tr("Find lyrics for the whole library in the background", "Tüm kütüphane için sözleri arka planda bul"), autoAll) { autoAll = it; LyricsAuto.setOn(c, it) }
        var st by remember { mutableStateOf(LyricsAuto.status(c)) }
        androidx.compose.runtime.LaunchedEffect(autoAll) { while (true) { st = LyricsAuto.status(c); kotlinx.coroutines.delay(2000) } }
        if (st.second > 0) Text(tr("${st.first} of ${st.second} songs looked at, ${st.third} with lyrics", "${st.second} şarkıdan ${st.first} tanesine bakıldı, ${st.third} tanesinin sözü var"), color = T.sub, fontSize = 12.sp)
        com.umutk.pulse.PButton(tr("Search now", "Şimdi ara"), compact = true) { LyricsAuto.runNow(c); autoAll = true }
        var consec by remember { mutableStateOf(LyricsShare.consecutive(c)) }
        SwitchRow(tr("Share: only lines that follow each other", "Paylaşım: yalnız ardışık satırlar"), consec) { consec = it; LyricsShare.setConsecutive(c, it) }
    }
}
