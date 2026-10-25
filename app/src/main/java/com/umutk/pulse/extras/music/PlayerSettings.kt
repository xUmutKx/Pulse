package com.umutk.pulse.extras.music

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.SetPickDialog
import com.umutk.pulse.extras.music.poweramp.LineChoice
import com.umutk.pulse.extras.music.poweramp.PlayerBgPicker
import com.umutk.pulse.extras.music.poweramp.PowerampLineAction
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.extras.ui.SliderRow
import com.umutk.pulse.extras.ui.SwitchRow
import com.umutk.pulse.tr
import kotlinx.coroutines.launch

/** Which player's own section is shown. */
enum class SettingsFor { MATERIAL, POWERAMP, WINAMP, WMP }

/**
 * Settings shared by every player (sound, library, playback, lyrics) plus the section of the player that is active
 * (Poweramp: skin gallery, Winamp: skin strip). Every change is saved at once and pushed to the engine.
 */
@Composable
fun MusicSettingsSections(engine: MusicPlayerEngine, which: SettingsFor, onRescan: suspend () -> Unit, extra: @Composable () -> Unit = {}, onCat: (Boolean) -> Unit = {}, catContent: @Composable (String) -> Unit = {}) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    // the settings are grouped like Poweramp's: a list of categories with icons, each opening its own rows
    var cat by remember { mutableStateOf("") }
    // every category opens with its groups closed (the groups of one category must not keep the open state of the one before)
    com.umutk.pulse.extras.ui.SectionReset.epoch = remember(cat) { com.umutk.pulse.extras.ui.SectionReset.next() }
    androidx.activity.compose.BackHandler(cat.isNotEmpty()) { cat = "" }
    androidx.compose.runtime.LaunchedEffect(cat) { onCat(cat.isNotEmpty()) }
    if (cat.isEmpty()) { SettingsIndex(which) { cat = it }; extra(); return }
    SettingsCategoryHeader(cat) { cat = "" }
    catContent(cat)
    fun push() = engine.reloadSettings()
    var preamp by remember { mutableIntStateOf(MusicSettings.preamp(c)) }
    var rg by remember { mutableIntStateOf(MusicSettings.replayGain(c)) }
    var rgFb by remember { mutableIntStateOf(MusicSettings.rgFallback(c)) }
    var xfade by remember { mutableIntStateOf(MusicSettings.crossfade(c)) }
    var skipSil by remember { mutableStateOf(MusicSettings.skipSilence(c)) }
    var speed by remember { mutableFloatStateOf(MusicSettings.speed(c)) }
    var pitch by remember { mutableStateOf(MusicSettings.pitchFollows(c)) }
    var bass by remember { mutableIntStateOf(engine.equalizer.bassBoost) }
    var virt by remember { mutableIntStateOf(engine.equalizer.virtualizer) }

    if (cat == "audio") SettingsSection(tr("Sound", "Ses")) {
        SliderRow("${tr("Pre-amp", "Ön yükseltici")}  ${if (preamp > 0) "+" else ""}$preamp dB", preamp.toFloat(), -12f..12f) { preamp = it.toInt(); MusicSettings.setPreamp(c, preamp); push() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("Off", "Kapalı"), "ReplayGain", tr("Album", "Albüm")).forEachIndexed { i, l -> PChip(rg == i, l) { rg = i; MusicSettings.setReplayGain(c, i); push() } }
        }
        if (rg > 0) SliderRow("${tr("Untagged files", "Etiketsiz dosyalar")}  $rgFb dB", rgFb.toFloat(), -15f..0f) { rgFb = it.toInt(); MusicSettings.setRgFallback(c, rgFb); push() }
        SliderRow("${tr("Fade between tracks", "Parçalar arası geçiş")}  ${if (xfade == 0) tr("off", "kapalı") else "$xfade s"}", xfade.toFloat(), 0f..12f) { xfade = it.toInt(); MusicSettings.setCrossfade(c, xfade); push() }
        SwitchRow(tr("Skip silence", "Sessizliği atla"), skipSil) { skipSil = it; MusicSettings.setSkipSilence(c, it); push() }
        SliderRow("${tr("Speed", "Hız")}  ${"%.2f".format(speed)}x", speed, .5f..2f) { speed = (Math.round(it * 20) / 20f); MusicSettings.setSpeed(c, speed); push() }
        SwitchRow(tr("Pitch follows speed", "Ton hızı izler"), pitch) { pitch = it; MusicSettings.setPitchFollows(c, it); push() }
        if (engine.equalizer.bassSupported) SliderRow(tr("Bass boost", "Bas güçlendirme"), bass.toFloat(), 0f..1000f) { bass = it.toInt(); engine.equalizer.setBassBoost(bass); engine.equalizer.save(c) }
        if (engine.equalizer.virtualizerSupported) SliderRow(tr("Virtualizer", "Sanallaştırıcı"), virt.toFloat(), 0f..1000f) { virt = it.toInt(); engine.equalizer.setVirtualizer(virt); engine.equalizer.save(c) }
    }

    var minSec by remember { mutableIntStateOf(MusicSettings.minSeconds(c)) }
    var hidden by remember { mutableStateOf(MusicSettings.showHidden(c)) }
    var excluded by remember { mutableStateOf(MusicSettings.excluded(c).sorted()) }
    var newDir by remember { mutableStateOf("") }
    if (cat == "library") SettingsSection(tr("Library", "Kütüphane")) {
        SliderRow("${tr("Shortest track", "En kısa parça")}  $minSec s", minSec.toFloat(), 0f..120f) { minSec = it.toInt(); MusicSettings.setMinSeconds(c, minSec) }
        SwitchRow(tr("Hidden folders", "Gizli klasörler"), hidden) { hidden = it; MusicSettings.setShowHidden(c, it) }
        excluded.forEach { d ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(d, color = T.sub, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clickable { excluded = excluded - d; MusicSettings.setExcluded(c, excluded.toSet()) }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 18.dp) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 6.dp)) {
                if (newDir.isEmpty()) Text("/storage/emulated/0/…", color = T.sub, fontSize = 13.sp)
                BasicTextField(newDir, { newDir = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 14.sp), cursorBrush = SolidColor(T.text), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.size(40.dp).clickable {
                val d = newDir.trim().trimEnd('/')
                if (d.startsWith("/")) { excluded = (excluded + d).distinct().sorted(); MusicSettings.setExcluded(c, excluded.toSet()); newDir = "" }
            }, contentAlignment = Alignment.Center) { PIcon("newfolder", T.text, 22.dp) }
            Box(Modifier.size(40.dp).clickable { scope.launch { onRescan() } }, contentAlignment = Alignment.Center) { PIcon("rotate", T.text, 22.dp) }
        }
    }

    // a Poweramp backup: playlists, ratings, play counts and the cover pictures come in by themselves
    var embedCovers by remember { mutableStateOf(false) }
    var importNote by remember { mutableStateOf("") }
    val doImport: (android.net.Uri) -> Unit = { uri ->
        scope.launch {
            importNote = tr("Reading the backup…", "Yedek okunuyor…")
            if (MusicLibrary.tracks.value.isEmpty()) { importNote = tr("The library is empty: scan the music first, then load the backup", "Kütüphane boş: önce müziği tara, sonra yedeği yükle"); return@launch }
            val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PowerampImport.run(c, uri, MusicLibrary.tracks.value, embedCovers) } }
            importNote = r.getOrNull()?.let { x ->
                tr("${x.playlists} playlists, ${x.ratings} ratings, ${x.plays} play counts, ${x.covers} covers, ${x.artists} artist pictures" + (if (embedCovers) " (${x.embedded} written into files)" else "") +
                    (if (x.missing > 0) "; the rest was saved; ${x.missing} songs of the backup were not found on this phone, e.g. ${x.missingNames.joinToString(", ")}" else ""),
                    "${x.playlists} çalma listesi, ${x.ratings} puan, ${x.plays} dinlenme sayısı, ${x.covers} kapak, ${x.artists} sanatçı resmi" + (if (embedCovers) " (${x.embedded} dosyaya yazıldı)" else "") +
                    (if (x.missing > 0) "; kalanlar kaydedildi; yedekteki ${x.missing} şarkı bu telefonda bulunamadı, örn. ${x.missingNames.joinToString(", ")}" else ""))
            } ?: tr("The backup could not be read", "Yedek okunamadı")
        }
    }
    // the album_art / artist_art folders of Poweramp, picked as a folder: covers by "Artist - Title.jpg", artist pictures by "Artist.jpg"
    val pickPics = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            if (MusicLibrary.tracks.value.isEmpty()) { importNote = tr("The library is empty: scan the music first", "Kütüphane boş: önce müziği tara"); return@launch }
            importNote = tr("Reading the pictures…", "Resimler okunuyor…")
            val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PowerampImport.pictures(c, tree, MusicLibrary.tracks.value, embedCovers) } }
            importNote = r.getOrNull()?.let { (cv, ar, em) -> tr("$cv covers, $ar artist pictures" + (if (embedCovers) " ($em written into files)" else ""), "$cv kapak, $ar sanatçı resmi" + (if (embedCovers) " ($em dosyaya yazıldı)" else "")) }
                ?: tr("The pictures could not be read", "Resimler okunamadı")
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) doImport(uri) }
    // the backup files found on the phone: the system picker does not always show a file of this unknown kind
    val found by androidx.compose.runtime.produceState(emptyList<java.io.File>(), cat) {
        value = if (cat != "library") emptyList() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            (java.io.File(c.filesDir, "pa_backups").listFiles()?.filter { it.isFile }.orEmpty()) + runCatching { android.os.Environment.getExternalStorageDirectory().walkTopDown().maxDepth(4).onEnter { !it.name.startsWith(".") || it.name == ".poweramp" }.filter { it.isFile && it.name.endsWith(".poweramp-backup") }.sortedByDescending { it.lastModified() }.take(8).toList() }.getOrDefault(emptyList())
        }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            importNote = tr("Writing the backup…", "Yedek yazılıyor…")
            val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PowerampImport.export(c, uri) } }
            importNote = r.getOrNull()?.let { (l, n) -> tr("Saved: $l playlists, $n songs with a rating or plays", "Kaydedildi: $l çalma listesi, puanlı ya da dinlenmiş $n şarkı") } ?: tr("The backup could not be written", "Yedek yazılamadı")
        }
    }
    if (cat == "library") SettingsSection(tr("Poweramp backup", "Poweramp yedeği")) {
        SwitchRow(tr("Write covers into the songs too (MP3)", "Kapakları şarkıların kendisine de yaz (MP3)"), embedCovers) { embedCovers = it }
        Row(Modifier.fillMaxWidth().clickable { pickBackup.launch(arrayOf("*/*")) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("download", T.text, 22.dp)
            Text(tr("Load a .poweramp-backup", ".poweramp-backup yükle"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        }
        Row(Modifier.fillMaxWidth().clickable { pickPics.launch(null) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("image", T.text, 22.dp)
            Text(tr("Load album_art / artist_art pictures (folder)", "album_art / artist_art resimlerini yükle (klasör)"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        }
        found.forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { doImport(android.net.Uri.fromFile(f)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("document", T.sub, 20.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(f.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(f.parent.orEmpty().removePrefix(android.os.Environment.getExternalStorageDirectory().path), color = T.sub, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { exportBackup.launch("Pulse-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".poweramp-backup") }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("save", T.text, 22.dp)
            Text(tr("Save a .poweramp-backup", ".poweramp-backup olarak kaydet"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        }
        // straight from the installed Poweramp (root): all the ratings and play counts and the album_art pictures, no backup file needed
        Row(Modifier.fillMaxWidth().clickable {
            scope.launch {
                if (MusicLibrary.tracks.value.isEmpty()) { importNote = tr("The library is empty: scan the music first", "Kütüphane boş: önce müziği tara"); return@launch }
                importNote = tr("Reading Poweramp…", "Poweramp okunuyor…")
                val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PowerampImport.direct(c, MusicLibrary.tracks.value, embedCovers) } }
                importNote = r.getOrNull()?.let { x ->
                    tr("${x.ratings} ratings, ${x.plays} play counts, ${x.covers} covers",
                        "${x.ratings} puan, ${x.plays} dinlenme sayısı, ${x.covers} kapak") + (if (x.ratings == 0 && x.plays == 0 && PowerampImport.lastError.isNotBlank()) ": " + PowerampImport.lastError else "")
                } ?: tr("Poweramp's data could not be read (needs root and Poweramp installed)", "Poweramp verisi okunamadı (root ve Poweramp gerekir)") + (r.exceptionOrNull()?.let { ": " + (it.message ?: it.javaClass.simpleName) } ?: PowerampImport.lastError.let { if (it.isBlank()) "" else ": $it" })
            }
        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("phone", T.text, 22.dp)
            Text(tr("Import from Poweramp (root)", "Poweramp'tan doğrudan al (root)"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
        }
        if (importNote.isNotEmpty()) Text(importNote, color = T.sub, fontSize = 12.sp)
    }

    var resume by remember { mutableStateOf(MusicSettings.resume(c)) }
    var screenOn by remember { mutableStateOf(MusicSettings.keepScreenOn(c)) }
    var stats by remember { mutableStateOf(MusicSettings.countStats(c)) }
    var nArt by remember { mutableStateOf(MusicSettings.notificationArt(c)) }
    var lyricsFont by remember { mutableIntStateOf(MusicSettings.lyricsFont(c)) }
    var coverSwipe by remember { mutableStateOf(CoverGestures.swipeChangesTrack(c)) }
    var onlineCovers by remember { mutableStateOf(MusicSettings.onlineCovers(c)) }
    var onlineTags by remember { mutableStateOf(MusicSettings.onlineTags(c)) }
    var btAuto by remember { mutableStateOf(BluetoothAutoplay.enabled(c)) }
    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (cat in setOf("library", "look", "art", "bt", "misc", "audio")) SettingsSection(tr("Playback", "Çalma")) {
        if (cat == "library") SwitchRow(tr("Resume last queue", "Son kuyruğa devam"), resume) { resume = it; MusicSettings.setResume(c, it) }
        if (cat == "look") SwitchRow(tr("Swipe cover to change track", "Kapakta kaydırarak parça değiştir"), coverSwipe) { coverSwipe = it; CoverGestures.setSwipeChangesTrack(c, it) }
        if (cat == "art") SwitchRow(tr("Download missing album art", "Eksik albüm kapaklarını internetten indir"), onlineCovers) {
            onlineCovers = it; MusicSettings.setOnlineCovers(c, it)
            // turning it on looks for the covers that are missing now, not only after the next library scan
            if (it) CoverFetch.backfill(c, MusicLibrary.tracks.value)
        }
        if (cat == "art") SwitchRow(tr("Download missing tags (album, genre, year)", "Eksik etiketleri indir (albüm, tür, yıl)"), onlineTags) {
            onlineTags = it; MusicSettings.setOnlineTags(c, it)
            if (it) OnlineTags.backfill(c, MusicLibrary.tracks.value)
        }
        if (cat == "bt") SwitchRow(tr("Play when Bluetooth audio connects", "Bluetooth ses cihazı bağlanınca çal"), btAuto) {
            btAuto = it; BluetoothAutoplay.setEnabled(c, it)
            // the receiver lives in the music service, so the switch starts it; BLUETOOTH_CONNECT is needed to read the device class
            if (it) { askBt.launch(Manifest.permission.BLUETOOTH_CONNECT); runCatching { c.startService(Intent(c, MusicPlaybackService::class.java)) } }
        }
        if (cat == "bt") {
            var rw by remember { mutableStateOf(MusicSettings.resumeWired(c)) }
            var pu by remember { mutableStateOf(MusicSettings.pauseOnUnplug(c)) }
            var pd by remember { mutableStateOf(MusicSettings.pauseUsbDac(c)) }
            var rb by remember { mutableStateOf(MusicSettings.respondButtons(c)) }
            var af by remember { mutableStateOf(MusicSettings.audioFocus(c)) }
            SwitchRow(tr("Resume on wired headset", "Kablolu kulaklıkta devam et"), rw) { rw = it; MusicSettings.setResumeWired(c, it); if (it) runCatching { c.startService(Intent(c, MusicPlaybackService::class.java)) } }
            SwitchRow(tr("Pause on headset disconnect", "Kulaklık çıkınca duraklat"), pu) { pu = it; MusicSettings.setPauseOnUnplug(c, it); engine.reloadSettings() }
            SwitchRow(tr("Pause on USB DAC connection", "USB DAC bağlanınca duraklat"), pd) { pd = it; MusicSettings.setPauseUsbDac(c, it); if (it) runCatching { c.startService(Intent(c, MusicPlaybackService::class.java)) } }
            SwitchRow(tr("Audio focus", "Ses odağı"), af) { af = it; MusicSettings.setAudioFocus(c, it); engine.reloadSettings() }
            SwitchRow(tr("Respond to buttons", "Tuşlara yanıt ver"), rb) { rb = it; MusicSettings.setRespondButtons(c, it) }
            var pz by remember { mutableStateOf(MusicSettings.pauseAtZeroVolume(c)) }
            SwitchRow(tr("Pause when the volume is turned to zero", "Ses sıfıra indirilince duraklat"), pz) { pz = it; MusicSettings.setPauseAtZeroVolume(c, it); if (it) runCatching { c.startService(Intent(c, MusicPlaybackService::class.java)) } }
        }
        if (cat == "audio") { var ae by remember { mutableStateOf(MusicSettings.autoEq(c)) }
            SwitchRow(tr("Equalizer by genre", "Türe göre ekolayzır"), ae) { ae = it; MusicSettings.setAutoEq(c, it) } }
        if (cat == "misc") SwitchRow(tr("Keep screen on", "Ekranı açık tut"), screenOn) { screenOn = it; MusicSettings.setKeepScreenOn(c, it) }
        if (cat == "misc") {
            var smart by remember { mutableStateOf(MusicSettings.smartShuffle(c)) }
            var rand by remember { mutableFloatStateOf(MusicSettings.shuffleRandomness(c).toFloat()) }
            SwitchRow(tr("Smart shuffle", "Akıllı karıştırma"), smart) { smart = it; MusicSettings.setSmartShuffle(c, it) }
            if (smart) SliderRow(tr("Less random  ·  More random", "Daha az rastgele  ·  Daha rastgele") + "  ${rand.toInt()}%", rand / 100f, 0f..1f) { rand = it * 100f; MusicSettings.setShuffleRandomness(c, rand.toInt()) }
        }
        if (cat == "library") SwitchRow(tr("Count plays", "Çalmaları say"), stats) { stats = it; MusicSettings.setCountStats(c, it) }
        if (cat == "look") { var lf by remember { mutableStateOf(MusicSettings.lyricsFull(c)) }; SwitchRow(tr("Full screen lyrics", "Tam ekran sözler"), lf) { lf = it; MusicSettings.setLyricsFull(c, it) } }
        if (cat == "look") SliderRow("${tr("Lyrics size", "Söz boyutu")}  $lyricsFont", lyricsFont.toFloat(), 12f..30f) { lyricsFont = it.toInt(); MusicSettings.setLyricsFont(c, lyricsFont) }
        if (cat == "audio") Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("timer", T.sub, 22.dp)
            listOf(0 to tr("Off", "Kapalı"), 15 to "15", 30 to "30", 60 to "60", -1 to tr("Track end", "Parça sonu")).forEach { (m, l) ->
                PChip(false, l) { if (m < 0) engine.setSleep(0, true) else engine.setSleep(m) }
            }
        }
        if (cat == "art") Row(Modifier.clickable {
            val n = com.umutk.pulse.extras.music.poweramp.PowerampCovers.clearAll(c)
            android.widget.Toast.makeText(c, tr("$n covers cleared", "$n kapak silindi"), android.widget.Toast.LENGTH_SHORT).show()
        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("trash", T.sub, 22.dp); Text(tr("Clear saved covers", "Kaydedilen kapakları temizle"), color = T.text, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
        }
        if (cat == "library") Row(Modifier.clickable { com.umutk.pulse.extras.music.stats.MusicStats.clear(c) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("history", T.sub, 22.dp); Text(tr("Reset statistics", "İstatistikleri sıfırla"), color = T.text, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }

    if (cat == "notif") {
        SettingsSection(tr("Cover", "Kapak"), startOpen = true) { SwitchRow(tr("Cover in notification", "Bildirimde kapak"), nArt) { nArt = it; MusicSettings.setNotificationArt(c, it) } }
        com.umutk.pulse.extras.music.notif.NotificationSettingsSection()
    }
    if (cat == "misc") com.umutk.pulse.extras.lyrics.LiveLyricsSettingsSection()
    if (cat == "misc") SettingsSection(tr("Widgets", "Widget'lar")) {
        var bgMode by remember { mutableStateOf(com.umutk.pulse.extras.music.widgets.WidgetOpts.bg(c)) }
        var artPct by remember { mutableFloatStateOf(com.umutk.pulse.extras.music.widgets.WidgetOpts.artPct(c).toFloat()) }
        Text(tr("Widget background", "Widget arka planı"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("none" to tr("Transparent", "Şeffaf"), "black" to tr("Black", "Siyah"), "stars" to tr("Starry (Poweramp)", "Yıldızlı (Poweramp)")).forEach { (k, n) ->
                com.umutk.pulse.PChip(bgMode == k, n) { bgMode = k; com.umutk.pulse.extras.music.widgets.WidgetOpts.setBg(c, k); com.umutk.pulse.extras.music.widgets.WidgetOpts.refresh(c) }
            }
        }
        SliderRow(tr("Cover size", "Kapak boyutu") + "  ${artPct.toInt()}%", (artPct - 30f) / 70f, 0f..1f) {
            artPct = 30f + it * 70f
            com.umutk.pulse.extras.music.widgets.WidgetOpts.setArtPct(c, artPct.toInt()); com.umutk.pulse.extras.music.widgets.WidgetOpts.refresh(c)
        }
        // as in Poweramp's own widget settings: corners, the shadow under the cover, the round cover
        var corners by remember { mutableIntStateOf(com.umutk.pulse.extras.music.widgets.WidgetOpts.corners(c)) }
        var shadow by remember { mutableStateOf(com.umutk.pulse.extras.music.widgets.WidgetOpts.artShadow(c)) }
        var circle by remember { mutableStateOf(com.umutk.pulse.extras.music.widgets.WidgetOpts.artCircle(c)) }
        Text(tr("Corners", "Köşeler"), color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to tr("Square", "Köşeli"), 1 to tr("Rounded", "Yuvarlak köşe"), 2 to tr("Round", "Tam yuvarlak")).forEach { (k, n) ->
                com.umutk.pulse.PChip(corners == k, n) { corners = k; com.umutk.pulse.extras.music.widgets.WidgetOpts.setCorners(c, k); com.umutk.pulse.extras.music.widgets.WidgetOpts.refresh(c) }
            }
        }
        SwitchRow(tr("Cover shadow", "Kapak gölgesi"), shadow) { shadow = it; com.umutk.pulse.extras.music.widgets.WidgetOpts.setArtShadow(c, it); com.umutk.pulse.extras.music.widgets.WidgetOpts.refresh(c) }
        SwitchRow(tr("Round cover", "Yuvarlak kapak"), circle) { circle = it; com.umutk.pulse.extras.music.widgets.WidgetOpts.setArtCircle(c, it); com.umutk.pulse.extras.music.widgets.WidgetOpts.refresh(c) }
    }

    if (which == SettingsFor.POWERAMP && cat == "background") SettingsSection("Poweramp") {
        // the skin gallery (families on top) lives in the music theme picker of MusicHub
        // the now-playing backdrop; the preview shows both choices (every other page is black)
        PlayerBgPicker(c)
        PowerampLineRow(c)
        com.umutk.pulse.extras.music.poweramp.BottomLabelPicker(c)
    }
}

/** One category of the music settings: icon, name, what is inside. */
private class SetCat(val key: String, val icon: String, val en: String, val trName: String, val enSub: String, val trSub: String)

/** The round background colour of each category's icon, as Poweramp's settings list colours its groups. */
private fun catColor(key: String) = when (key) {
    "look" -> 0xFF7E57C2; "notif" -> 0xFFD81B60; "audio" -> 0xFF1E88E5; "background" -> 0xFF26A69A; "art" -> 0xFFEF6C00; "library" -> 0xFF43A047; "bt" -> 0xFF3949AB; else -> 0xFF6D4C41
}.let { androidx.compose.ui.graphics.Color(it) }

private fun settingCats(which: SettingsFor) = listOfNotNull(
    SetCat("look", "palette", "Look and Feel", "Görünüm", "Player interface, lyrics, notifications", "Oynatıcı arayüzü, sözler, bildirimler"),
    SetCat("notif", "bell", "Notification", "Bildirim", "Buttons shown, cover, a sample notification", "Görünen tuşlar, kapak, örnek bildirim"),
    SetCat("audio", "volume", "Audio", "Ses", "Crossfade, replay gain, speed, sleep timer", "Geçiş, ReplayGain, hız, uyku zamanlayıcı"),
    SetCat("background", "image", "Background", "Arka plan", "Now playing backdrop, details", "Çalan şarkı arka planı, ayrıntılar").takeIf { which == SettingsFor.POWERAMP },
    SetCat("art", "image", "Album Art", "Albüm kapağı", "Download, cache cleanup, Poweramp backup", "İndirme, önbellek temizliği, Poweramp yedeği"),
    SetCat("library", "folder", "Library", "Kütüphane", "Rescan, music folders, queue, statistics", "Yeniden tara, müzik klasörleri, kuyruk, istatistik"),
    SetCat("bt", "sensors", "Headset/Bluetooth", "Kulaklık/Bluetooth", "Resume, pause, buttons and audio focus", "Devam, duraklat, tuşlar ve ses odağı"),
    SetCat("misc", "settings", "Misc", "Çeşitli", "Screen, live lyrics, other tweaks", "Ekran, canlı sözler, diğer ayarlar"),
)

@Composable
private fun SettingsIndex(which: SettingsFor, pick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        settingCats(which).forEach { k ->
            Row(Modifier.fillMaxWidth().clickable { pick(k.key) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape).background(catColor(k.key)), contentAlignment = Alignment.Center) { PIcon(k.icon, androidx.compose.ui.graphics.Color.White, 24.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(tr(k.en, k.trName), color = T.text, fontSize = 16.sp)
                    Text(tr(k.enSub, k.trSub), color = T.sub, fontSize = 12.sp)
                }
                PIcon("chevron", T.sub, 16.dp)
            }
        }
    }
}

@Composable
private fun SettingsCategoryHeader(key: String, back: () -> Unit) {
    val k = settingCats(SettingsFor.POWERAMP).firstOrNull { it.key == key }
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clickable(onClick = back), contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
        if (k != null) { Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape).background(catColor(k.key)), contentAlignment = Alignment.Center) { PIcon(k.icon, androidx.compose.ui.graphics.Color.White, 18.dp) }; Text(tr(k.en, k.trName), color = T.text, fontSize = 17.sp, modifier = Modifier.padding(start = 10.dp)) }
    }
}

/** Full-width row: what a long press on the artist / album line of the Poweramp page does. A radio dialog picks it. */
@Composable
private fun PowerampLineRow(c: Context) {
    var cur by remember { mutableStateOf(PowerampLineAction.longPress(c)) }
    var open by remember { mutableStateOf(false) }
    val title = tr("Long press on artist line", "Sanatçı satırına uzun basınca")
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(cur.label(), color = T.sub, fontSize = 14.sp)
    }
    if (open) SetPickDialog(title, LineChoice.entries.map { it to it.label() }, cur, { cur = it; PowerampLineAction.setLongPress(c, it) }, { open = false })
}
