package com.umutk.pulse.extras.music

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
fun MusicSettingsSections(engine: MusicPlayerEngine, which: SettingsFor, onRescan: suspend () -> Unit, extra: @Composable () -> Unit = {}) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
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

    SettingsSection(tr("Sound", "Ses")) {
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
    SettingsSection(tr("Library", "Kütüphane")) {
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

    var resume by remember { mutableStateOf(MusicSettings.resume(c)) }
    var screenOn by remember { mutableStateOf(MusicSettings.keepScreenOn(c)) }
    var stats by remember { mutableStateOf(MusicSettings.countStats(c)) }
    var nArt by remember { mutableStateOf(MusicSettings.notificationArt(c)) }
    var lyricsFont by remember { mutableIntStateOf(MusicSettings.lyricsFont(c)) }
    var coverSwipe by remember { mutableStateOf(CoverGestures.swipeChangesTrack(c)) }
    var onlineCovers by remember { mutableStateOf(MusicSettings.onlineCovers(c)) }
    var btAuto by remember { mutableStateOf(BluetoothAutoplay.enabled(c)) }
    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SettingsSection(tr("Playback", "Çalma")) {
        SwitchRow(tr("Resume last queue", "Son kuyruğa devam"), resume) { resume = it; MusicSettings.setResume(c, it) }
        SwitchRow(tr("Swipe cover to change track", "Kapakta kaydırarak parça değiştir"), coverSwipe) { coverSwipe = it; CoverGestures.setSwipeChangesTrack(c, it) }
        SwitchRow(tr("Download missing album art", "Eksik albüm kapaklarını internetten indir"), onlineCovers) {
            onlineCovers = it; MusicSettings.setOnlineCovers(c, it)
            // turning it on looks for the covers that are missing now, not only after the next library scan
            if (it) CoverFetch.backfill(c, MusicLibrary.tracks.value)
        }
        SwitchRow(tr("Play when Bluetooth audio connects", "Bluetooth ses cihazı bağlanınca çal"), btAuto) {
            btAuto = it; BluetoothAutoplay.setEnabled(c, it)
            // the receiver lives in the music service, so the switch starts it; BLUETOOTH_CONNECT is needed to read the device class
            if (it) { askBt.launch(Manifest.permission.BLUETOOTH_CONNECT); runCatching { c.startService(Intent(c, MusicPlaybackService::class.java)) } }
        }
        SwitchRow(tr("Keep screen on", "Ekranı açık tut"), screenOn) { screenOn = it; MusicSettings.setKeepScreenOn(c, it) }
        SwitchRow(tr("Count plays", "Çalmaları say"), stats) { stats = it; MusicSettings.setCountStats(c, it) }
        SwitchRow(tr("Cover in notification", "Bildirimde kapak"), nArt) { nArt = it; MusicSettings.setNotificationArt(c, it) }
        SliderRow("${tr("Lyrics size", "Söz boyutu")}  $lyricsFont", lyricsFont.toFloat(), 12f..30f) { lyricsFont = it.toInt(); MusicSettings.setLyricsFont(c, lyricsFont) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("timer", T.sub, 22.dp)
            listOf(0 to tr("Off", "Kapalı"), 15 to "15", 30 to "30", 60 to "60", -1 to tr("Track end", "Parça sonu")).forEach { (m, l) ->
                PChip(false, l) { if (m < 0) engine.setSleep(0, true) else engine.setSleep(m) }
            }
        }
        Row(Modifier.clickable { com.umutk.pulse.extras.music.stats.MusicStats.clear(c) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("history", T.sub, 22.dp); Text(tr("Reset statistics", "İstatistikleri sıfırla"), color = T.text, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }

    com.umutk.pulse.extras.music.notif.NotificationSettingsSection()
    com.umutk.pulse.extras.lyrics.LiveLyricsSettingsSection()

    if (which == SettingsFor.POWERAMP) SettingsSection("Poweramp") {
        // the skin gallery (families on top) lives in the music theme picker of MusicHub
        // the now-playing backdrop; the preview shows both choices (every other page is black)
        PlayerBgPicker(c)
        PowerampLineRow(c)
    }
    extra()
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
