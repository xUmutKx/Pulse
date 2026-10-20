package com.umutk.pulse.extras.screenrec

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.umutk.pulse.Center
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Screen recorder page inside Pulse: start (with optional countdown), pause, stop, settings and the saved recordings. */
@Composable
fun ScreenRecHub(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }

    var st by remember { mutableStateOf(ScreenRecService.state) }
    var shownMs by remember { mutableLongStateOf(0L) }
    var countdown by remember { mutableIntStateOf(0) }
    var list by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var mic by remember { mutableStateOf(ScreenRecPrefs.mic(ctx)) }
    var countdownOn by remember { mutableStateOf(ScreenRecPrefs.countdown(ctx)) }
    var quality by remember { mutableIntStateOf(ScreenRecPrefs.quality(ctx)) }
    var fps by remember { mutableIntStateOf(ScreenRecPrefs.fps(ctx)) }
    var limit by remember { mutableIntStateOf(ScreenRecPrefs.limit(ctx)) }
    var dlg by remember { mutableStateOf("") }
    var askDelete by remember { mutableStateOf<Recording?>(null) }
    var renameFor by remember { mutableStateOf<Recording?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(ScreenRecPrefs.sort(ctx)) }

    // polls the service (it runs outside this page); the list is reloaded when a recording ends
    LaunchedEffect(Unit) {
        var last: RecState? = null
        while (true) {
            val now = ScreenRecService.state
            st = now
            shownMs = ScreenRecService.elapsedMs()
            if (last == null || (now == RecState.IDLE && last != RecState.IDLE)) {
                // the service publishes the file a moment after it reports idle
                if (last != null) delay(800)
                list = withContext(Dispatchers.IO) { RecordingList.list(ctx) }
            }
            last = now
            delay(500)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) ScreenRecService.start(ctx, r.resultCode, data, ScreenRecPrefs.config(ctx))
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askConsent(ctx, consentLauncher)
    }

    // the countdown runs 3, 2, 1 and then starts the recording
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            if (countdown > 1) { countdown -= 1 } else { countdown = 0; begin(ctx, micLauncher, consentLauncher) }
        }
    }

    val free = remember(st, list.size) { freeBytes() }
    val shown = remember(list, query, sort) {
        val base = if (query.isBlank()) list else list.filter { it.name.contains(query.trim(), true) }
        when (sort) {
            ScreenRecPrefs.SORT_NAME -> base.sortedBy { it.name.lowercase() }
            ScreenRecPrefs.SORT_SIZE -> base.sortedByDescending { it.size }
            ScreenRecPrefs.SORT_LENGTH -> base.sortedByDescending { it.durationMs }
            else -> base
        }
    }
    val dateFmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()) }

    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Screen recorder", "Ekran kaydı"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }

        // status and timer
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PIcon("sensors", if (st == RecState.RUNNING) T.accent else T.sub, 26.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            countdown > 0 -> tr("Starting", "Başlıyor")
                            st == RecState.RUNNING -> tr("Recording", "Kaydediyor")
                            st == RecState.PAUSED -> tr("Paused", "Duraklatıldı")
                            else -> tr("Ready", "Hazır")
                        },
                        color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (countdown > 0) countdown.toString() else ScreenRecService.formatElapsed(shownMs),
                        color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                countdown > 0 -> PButton(tr("Cancel", "Vazgeç"), Modifier.fillMaxWidth()) { countdown = 0 }
                st == RecState.IDLE -> PButton(tr("Start", "Başlat"), Modifier.fillMaxWidth()) {
                    if (countdownOn) { countdown = 3 } else { begin(ctx, micLauncher, consentLauncher) }
                }
                else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PButton(if (st == RecState.PAUSED) tr("Resume", "Devam") else tr("Pause", "Duraklat"), Modifier.weight(1f)) {
                        if (st == RecState.PAUSED) ScreenRecService.resume(ctx) else ScreenRecService.pause(ctx)
                    }
                    Spacer(Modifier.width(10.dp))
                    PButton(tr("Stop", "Durdur"), Modifier.weight(1f), danger = true) { ScreenRecService.stop(ctx) }
                }
            }
            if (free >= 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    tr("Free space", "Boş alan") + ": " + sizeText(free),
                    color = if (free < LOW_STORAGE) T.accent else T.sub, fontSize = 12.sp
                )
            }
        }

        // settings: each choice opens a radio dialog, the rest are switches
        Spacer(Modifier.height(16.dp))
        Text(tr("Settings", "Ayarlar"), color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        SettingSwitch("volume", tr("Microphone", "Mikrofon"), mic) { mic = it; ScreenRecPrefs.setMic(ctx, it) }
        SettingSwitch("stopwatch", tr("Countdown before start", "Başlamadan geri sayım"), countdownOn) { countdownOn = it; ScreenRecPrefs.setCountdown(ctx, it) }
        SettingChoice("image", tr("Quality", "Kalite"), qualityName(quality)) { dlg = "quality" }
        SettingChoice("sensors", tr("Frame rate", "Kare hızı"), "$fps fps") { dlg = "fps" }
        SettingChoice("stopwatch", tr("Time limit", "Süre sınırı"), limitName(limit)) { dlg = "limit" }

        // saved recordings
        Spacer(Modifier.height(16.dp))
        Text(tr("Recordings", "Kayıtlar"), color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        SettingChoice("sort", tr("Sort", "Sırala"), sortName(sort)) { dlg = "sort" }
        PField(query, { query = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(vertical = 8.dp))
        if (list.isEmpty()) Center(tr("No recordings yet", "Henüz kayıt yok"), Modifier.padding(top = 16.dp))
        else if (shown.isEmpty()) Center(tr("Nothing found", "Sonuç yok"), Modifier.padding(top = 16.dp))
        else shown.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(dateFmt.format(Instant.ofEpochMilli(r.added)), ScreenRecService.formatElapsed(r.durationMs), sizeText(r.size)).joinToString(" · "),
                        color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(Modifier.size(40.dp).clickable {
                    runCatching { ctx.startActivity(RecordingList.play(r)) }
                        .onFailure { Toast.makeText(ctx, tr("No app can play this", "Bunu açabilecek uygulama yok"), Toast.LENGTH_SHORT).show() }
                }, contentAlignment = Alignment.Center) { PIcon("play", T.text, 22.dp) }
                PButton(tr("Rename", "Yeniden adlandır"), compact = true) { renameFor = r; renameText = r.name.removeSuffix(".mp4") }
                PButton(tr("Share", "Paylaş"), compact = true) {
                    runCatching { ctx.startActivity(RecordingList.share(r)) }
                }
                Box(Modifier.size(40.dp).clickable { askDelete = r }, contentAlignment = Alignment.Center) { PIcon("delete", T.text, 22.dp) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dlg) {
        "quality" -> ChoiceDialog(tr("Quality", "Kalite"),
            listOf(ScreenRecPrefs.QUALITY_LOW to qualityName(ScreenRecPrefs.QUALITY_LOW), ScreenRecPrefs.QUALITY_MEDIUM to qualityName(ScreenRecPrefs.QUALITY_MEDIUM), ScreenRecPrefs.QUALITY_HIGH to qualityName(ScreenRecPrefs.QUALITY_HIGH)),
            quality, { quality = it; ScreenRecPrefs.setQuality(ctx, it); dlg = "" }) { dlg = "" }
        "fps" -> ChoiceDialog(tr("Frame rate", "Kare hızı"),
            ScreenRecPrefs.FPS_CHOICES.map { it to "$it fps" }, fps, { fps = it; ScreenRecPrefs.setFps(ctx, it); dlg = "" }) { dlg = "" }
        "limit" -> ChoiceDialog(tr("Time limit", "Süre sınırı"),
            ScreenRecPrefs.LIMIT_CHOICES.map { it to limitName(it) }, limit, { limit = it; ScreenRecPrefs.setLimit(ctx, it); dlg = "" }) { dlg = "" }
        "sort" -> ChoiceDialog(tr("Sort", "Sırala"),
            listOf(
                ScreenRecPrefs.SORT_NEWEST to sortName(ScreenRecPrefs.SORT_NEWEST), ScreenRecPrefs.SORT_NAME to sortName(ScreenRecPrefs.SORT_NAME),
                ScreenRecPrefs.SORT_SIZE to sortName(ScreenRecPrefs.SORT_SIZE), ScreenRecPrefs.SORT_LENGTH to sortName(ScreenRecPrefs.SORT_LENGTH),
            ), sort, { sort = it; ScreenRecPrefs.setSort(ctx, it); dlg = "" }) { dlg = "" }
    }

    askDelete?.let { r ->
        PDialog(
            title = tr("Delete recording?", "Kayıt silinsin mi?"),
            text = r.name,
            confirm = tr("Delete", "Sil"),
            onConfirm = {
                if (RecordingList.delete(ctx, r)) list = list.filterNot { it.uri == r.uri }
                askDelete = null
            },
            onDismiss = { askDelete = null },
        )
    }

    renameFor?.let { r ->
        val look = LocalWindowLook.current
        WinAlert(
            onDismissRequest = { renameFor = null },
            title = { Text(tr("Rename recording", "Kaydı yeniden adlandır"), color = look.text) },
            text = { PField(renameText, { renameText = it }, tr("Name", "Ad"), Modifier.fillMaxWidth()) },
            confirmButton = {
                PButton(tr("Save", "Kaydet")) {
                    RecordingList.rename(ctx, r, renameText)?.let { newName ->
                        list = list.map { if (it.uri == r.uri) it.copy(name = newName) else it }
                    }
                    renameFor = null
                }
            },
            dismissButton = { PButton(tr("Cancel", "Vazgeç")) { renameFor = null } },
        )
    }
}

private fun begin(ctx: Context, mic: ActivityResultLauncher<String>, consent: ActivityResultLauncher<Intent>) {
    // the mic is asked first when it is on; a refusal still records the screen without sound
    if (ScreenRecPrefs.mic(ctx) && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) mic.launch(Manifest.permission.RECORD_AUDIO)
    else askConsent(ctx, consent)
}

private fun askConsent(ctx: Context, consent: ActivityResultLauncher<Intent>) {
    consent.launch(ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
}

@Composable
private fun SettingSwitch(icon: String, title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, T.text, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(on, onChange)
    }
}

@Composable
private fun SettingChoice(icon: String, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, T.text, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = T.sub, fontSize = 14.sp)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val look = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = look.text) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PRadio(v == selected) { onPick(v) }
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = look.text, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat"), onClick = onDismiss) },
    )
}

private const val LOW_STORAGE = 1L shl 30

private fun freeBytes(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(-1L)

private fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "${b / 1024} KB"
}

private fun qualityName(q: Int): String = when (q) {
    ScreenRecPrefs.QUALITY_LOW -> tr("Low (720p)", "Düşük (720p)")
    ScreenRecPrefs.QUALITY_HIGH -> tr("High (screen size)", "Yüksek (ekran boyu)")
    else -> tr("Medium (1080p)", "Orta (1080p)")
}

private fun limitName(m: Int): String = if (m == 0) tr("No limit", "Sınır yok") else tr("$m min", "$m dk")

private fun sortName(s: Int): String = when (s) {
    ScreenRecPrefs.SORT_NAME -> tr("Name", "Ad")
    ScreenRecPrefs.SORT_SIZE -> tr("Size", "Boyut")
    ScreenRecPrefs.SORT_LENGTH -> tr("Length", "Süre")
    else -> tr("Newest first", "En yeni")
}
