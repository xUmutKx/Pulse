package com.umutk.pulse.extras.video

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PDialog
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.extras.ui.SliderRow
import com.umutk.pulse.extras.ui.SwitchRow
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal fun aspectNames() = listOf(tr("Fit", "Sığdır"), tr("Stretch", "Uzat"), tr("Crop", "Kırp"), "16:9", "4:3")
internal fun orientNames() = listOf(tr("System", "Sistem"), tr("Landscape", "Yatay"), tr("Portrait", "Dikey"), tr("By video", "Videoya göre"), tr("Landscape flipped", "Ters yatay"), tr("Any direction", "Her yön"))
internal fun repeatNames() = listOf(tr("Off", "Kapalı"), tr("One", "Tek"), tr("All", "Hepsi"))
internal fun speedName(s: Float) = (if (s % 1f == 0f) "${s.toInt()}" else "$s") + "x"
internal fun langName(code: String) = if (code.isBlank()) tr("Auto", "Otomatik") else Locale(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }
internal val langCodes = listOf("", "en", "tr", "de", "fr", "es", "it", "pt", "ru", "ar", "ja", "ko", "zh", "hi", "nl", "pl", "uk")
internal val encodings = listOf("UTF-8", "windows-1254", "ISO-8859-9", "windows-1252", "ISO-8859-1", "windows-1251", "windows-1256", "UTF-16", "Shift_JIS", "GBK", "EUC-KR")

/** Bumped after every edit so the rows read the saved values again. */
private val tick = mutableIntStateOf(0)

@Composable
private fun Sw(title: String, get: () -> Boolean, set: (Boolean) -> Unit, done: () -> Unit) {
    tick.intValue
    SwitchRow(title, get()) { set(it); done() }
}

/** A row with the current value; a tap opens the radio list. */
@Composable
private fun Pick(title: String, opts: List<String>, cur: () -> Int, done: () -> Unit, set: (Int) -> Unit) {
    tick.intValue
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = T.text, fontSize = 15.sp)
        Text(opts.getOrElse(cur()) { "" }, color = T.sub, fontSize = 13.sp)
    }
    if (open) PickDialog(title, opts.mapIndexed { i, n -> n to (i == cur()) }, { open = false }) { set(it); open = false; done() }
}

/** Choice among numbers: [get] is the saved value. */
@Composable
private fun Num(title: String, values: List<Int>, get: () -> Int, fmt: (Int) -> String, done: () -> Unit, set: (Int) -> Unit) {
    tick.intValue
    Pick(title, values.map(fmt), { values.indexOf(get()).let { if (it < 0) values.indexOfFirst { v -> v >= get() }.coerceAtLeast(0) else it } }, done) { set(values[it]) }
}

@Composable
private fun Sld(title: String, unit: String, range: IntRange, get: () -> Int, done: () -> Unit, set: (Int) -> Unit) {
    tick.intValue
    SliderRow("$title  ${get()}$unit", get().toFloat(), range.first.toFloat()..range.last.toFloat()) { set(it.toInt().coerceIn(range)); done() }
}

@Composable
private fun Act(title: String, value: () -> String = { "" }, onClick: () -> Unit) {
    tick.intValue
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = T.text, fontSize = 15.sp)
        value().let { if (it.isNotEmpty()) Text(it, color = T.sub, fontSize = 13.sp) }
    }
}

/** Live look of the subtitle options. */
@Composable
private fun SubPreview(p: VideoPrefs) {
    tick.intValue
    val c = Color(subColors[p.subColor.coerceIn(subColors.indices)])
    val fam = listOf(FontFamily.SansSerif, FontFamily.Serif, FontFamily.Monospace, FontFamily.Cursive, FontFamily.Cursive)[p.subFont.coerceIn(0, 4)]
    val shadow = if (p.subEdge) Shadow(Color.Black, if (p.subEdgeKind == 2) Offset(3f, 3f) else Offset.Zero, if (p.subEdgeKind == 1) 6f else 3f) else null
    Box(Modifier.fillMaxWidth().background(Color(0xFF3A4A5A), RoundedCornerShape(10.dp)).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Text(
            "Subtitle  Altyazı", Modifier.background(if (p.subBg) Color.Black.copy(alpha = p.subBgAlpha / 100f) else Color.Transparent).padding(horizontal = 6.dp),
            style = TextStyle(color = c, fontSize = p.subSize.sp, fontFamily = fam, fontWeight = if (p.subBold) FontWeight.Bold else FontWeight.Normal, shadow = shadow),
        )
    }
}

/** Every player option in folded sections. [onChange] runs after each edit. */
@Composable
fun VideoSettings(prefs: VideoPrefs, onClose: () -> Unit, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()
    var folderDlg by remember { mutableIntStateOf(0) } // 1 exclude, 2 only these
    var uaDlg by remember { mutableStateOf(false) }
    var resetDlg by remember { mutableStateOf(false) }
    var histDlg by remember { mutableStateOf(false) }
    fun changed() { tick.intValue++; onChange() }
    val done = { changed() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) runCatching { ctx.contentResolver.openOutputStream(u)!!.use { it.write(prefs.exportJson().toByteArray()) } }
            .onSuccess { Toast.makeText(ctx, tr("Saved", "Kaydedildi"), Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, tr("Failed", "Olmadı"), Toast.LENGTH_SHORT).show() }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            val ok = runCatching { prefs.importJson(ctx.contentResolver.openInputStream(u)!!.bufferedReader().use { it.readText() }) }.getOrDefault(false)
            Toast.makeText(ctx, if (ok) tr("Restored", "Geri yüklendi") else tr("Failed", "Olmadı"), Toast.LENGTH_SHORT).show()
            if (ok) changed()
        }
    }
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(T.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            VBtn(onClose) { PIcon("back", T.text, 24.dp) }
            Text(tr("Player", "Oynatıcı"), color = T.text, fontSize = 18.sp)
        }
        Column(Modifier.verticalScroll(scroll).padding(horizontal = 16.dp)) {
            com.umutk.pulse.ExtraLookRow()
            val onOff = listOf(tr("Off", "Kapalı"), tr("On", "Açık"))
            SettingsSection(tr("Skin", "Kılıf")) { SkinPicker(prefs.skin) { prefs.skin = it; changed() } }

            SettingsSection(tr("Player", "Oynatıcı")) {
                Sw(tr("Resume position", "Kaldığı yerden"), { prefs.resumeOn }, { prefs.resumeOn = it }, done)
                Sw(tr("Play next", "Sonrakini oynat"), { prefs.autoPlay }, { prefs.autoPlay = it }, done)
                Pick(tr("Repeat", "Tekrar"), repeatNames(), { prefs.repeat }, done) { prefs.repeat = it }
                Sw(tr("Remember speed", "Hızı hatırla"), { prefs.rememberSpeed }, { prefs.rememberSpeed = it }, done)
                Pick(tr("Default speed", "Varsayılan hız"), speeds.map { speedName(it) }, { speeds.indexOf(prefs.defSpeed).coerceAtLeast(0) }, done) { prefs.defSpeed = speeds[it] }
                Pick(tr("Orientation", "Yön"), orientNames(), { prefs.orient }, done) { prefs.orient = it }
                Sw(tr("Keep screen on", "Ekran açık kalsın"), { prefs.keepAwake }, { prefs.keepAwake = it }, done)
                Sw(tr("Fast seek", "Hızlı atlama"), { prefs.fastSeek }, { prefs.fastSeek = it }, done)
                Sw(tr("Seek preview", "Atlama önizlemesi"), { prefs.seekPreview }, { prefs.seekPreview = it }, done)
                Num(tr("Seek step", "Atlama adımı"), listOf(5, 10, 15, 30, 60), { prefs.dtapSec }, { "$it s" }, done) { prefs.dtapSec = it }
                Num(tr("Hide controls", "Kontrolleri gizle"), listOf(2000, 3500, 5000, 8000, 0), { prefs.ctrlTimeout }, { if (it == 0) tr("Never", "Hiç") else "${it / 1000f} s" }, done) { prefs.ctrlTimeout = it }
                Sw(tr("Time left", "Kalan süre"), { prefs.showRemaining }, { prefs.showRemaining = it }, done)
                Sw(tr("Clock", "Saat"), { prefs.showClock }, { prefs.showClock = it }, done)
                Sw(tr("Battery", "Pil"), { prefs.showBattery }, { prefs.showBattery = it }, done)
                Sw(tr("Remember brightness", "Parlaklığı hatırla"), { prefs.rememberBright }, { prefs.rememberBright = it }, done)
                Sw(tr("Remember volume", "Sesi hatırla"), { prefs.rememberVolume }, { prefs.rememberVolume = it }, done)
                Sw(tr("Audio focus", "Ses odağı"), { prefs.audioFocus }, { prefs.audioFocus = it }, done)
                Sw(tr("Pause when headphones leave", "Kulaklık çıkınca durdur"), { prefs.pauseNoisy }, { prefs.pauseNoisy = it }, done)
                Sw(tr("Pause on calls", "Aramada durdur"), { prefs.pauseCall }, { prefs.pauseCall = it }, done)
                Sw(tr("Background audio", "Arka plan sesi"), { prefs.bg }, { prefs.bg = it }, done)
                Sw(tr("Picture in picture", "Pencere içinde pencere"), { prefs.autoPip }, { prefs.autoPip = it }, done)
            }

            SettingsSection(tr("Gestures", "Hareketler")) {
                Sw(tr("Swipe to seek", "Kaydırarak ilerlet"), { prefs.gestSeek }, { prefs.gestSeek = it }, done)
                Sw(tr("Swipe for volume", "Kaydırarak ses"), { prefs.gestVol }, { prefs.gestVol = it }, done)
                Sw(tr("Swipe for brightness", "Kaydırarak parlaklık"), { prefs.gestBright }, { prefs.gestBright = it }, done)
                Sw(tr("Pinch to zoom", "Sıkıştırarak yakınlaştır"), { prefs.gestZoom }, { prefs.gestZoom = it }, done)
                Sw(tr("Hold for speed", "Basılı tutunca hız"), { prefs.gestLong }, { prefs.gestLong = it }, done)
                Num(tr("Hold speed", "Hız"), listOf(15, 20, 30, 40), { (prefs.lpSpeed * 10).toInt() }, { speedName(it / 10f) }, done) { prefs.lpSpeed = it / 10f }
                Pick(tr("Double tap", "Çift dokunma"), listOf(tr("Off", "Kapalı"), tr("Seek", "Atla"), tr("Pause", "Duraklat"), tr("Both", "İkisi")), { prefs.dtap }, done) { prefs.dtap = it }
                Num(tr("Swipe range", "Kaydırma aralığı"), listOf(30, 60, 90, 180, 300), { prefs.seekSens }, { "$it s" }, done) { prefs.seekSens = it }
                Sld(tr("Sensitivity", "Hassasiyet"), "%", 40..250, { prefs.gestSens }, done) { prefs.gestSens = it }
            }

            SettingsSection(tr("Audio", "Ses")) {
                Pick(tr("Audio language", "Ses dili"), langCodes.map { langName(it) }, { langCodes.indexOf(prefs.prefAudio).coerceAtLeast(0) }, done) { prefs.prefAudio = langCodes[it] }
                Sw(tr("Remember choice", "Seçimi hatırla"), { prefs.rememberSel }, { prefs.rememberSel = it }, done)
                Num(tr("Volume boost", "Ses artırma"), listOf(0, 300, 600, 900, 1200, 1500), { prefs.boost }, { if (it == 0) tr("Off", "Kapalı") else "+${it / 100} dB" }, done) { prefs.boost = it }
                Sld(tr("Volume cap", "Ses sınırı"), "%", 10..100, { prefs.volCap }, done) { prefs.volCap = it }
                Sw(tr("Skip silence", "Sessizliği atla"), { prefs.skipSilence }, { prefs.skipSilence = it }, done)
                Sw(tr("Downmix to stereo", "Stereoya indir"), { prefs.downmix }, { prefs.downmix = it }, done)
                Sw(tr("Pitch correction", "Ton düzeltme"), { prefs.pitchFix }, { prefs.pitchFix = it }, done)
                Sw(tr("Equalizer", "Ekolayzır"), { prefs.eqOn }, { prefs.eqOn = it }, done)
                Act(tr("Reset equalizer", "Ekolayzırı sıfırla")) { prefs.eqLevels = ""; changed() }
            }

            SettingsSection(tr("Subtitles", "Altyazı")) {
                SubPreview(prefs)
                Pick(tr("Subtitle language", "Altyazı dili"), langCodes.map { langName(it) }, { langCodes.indexOf(prefs.prefSub).coerceAtLeast(0) }, done) { prefs.prefSub = langCodes[it] }
                Sw(tr("Select automatically", "Kendiliğinden seç"), { prefs.subAutoSel }, { prefs.subAutoSel = it }, done)
                Sw(tr("Load .srt next to the video", "Yanındaki altyazıyı yükle"), { prefs.subAuto }, { prefs.subAuto = it }, done)
                Sw(tr("Ignore file styling", "Dosya stilini yok say"), { prefs.subIgnoreStyle }, { prefs.subIgnoreStyle = it }, done)
                Pick(tr("Font", "Yazı tipi"), subFontNames(), { prefs.subFont }, done) { prefs.subFont = it }
                Num(tr("Text size", "Yazı boyu"), listOf(14, 16, 18, 20, 24, 28, 34, 40), { prefs.subSize }, { "$it" }, done) { prefs.subSize = it }
                Pick(tr("Colour", "Renk"), subColorNames(), { prefs.subColor }, done) { prefs.subColor = it }
                Sw(tr("Bold", "Kalın"), { prefs.subBold }, { prefs.subBold = it }, done)
                Sw(tr("Edge", "Kenar"), { prefs.subEdge }, { prefs.subEdge = it }, done)
                Pick(tr("Edge style", "Kenar biçimi"), subEdgeNames(), { (prefs.subEdgeKind - 1).coerceIn(0, 3) }, done) { prefs.subEdgeKind = it + 1 }
                Sw(tr("Background", "Arka plan"), { prefs.subBg }, { prefs.subBg = it }, done)
                Sld(tr("Background opacity", "Arka plan koyuluğu"), "%", 0..100, { prefs.subBgAlpha }, done) { prefs.subBgAlpha = it }
                Sld(tr("Height", "Yükseklik"), "%", 0..40, { prefs.subPos }, done) { prefs.subPos = it }
                Sld(tr("Side margin", "Yan boşluk"), " dp", 0..80, { prefs.subMargin }, done) { prefs.subMargin = it }
                Pick(tr("Encoding", "Kodlama"), encodings, { encodings.indexOf(prefs.subEnc).coerceAtLeast(0) }, done) { prefs.subEnc = encodings[it] }            }

            SettingsSection(tr("Decoder", "Çözücü")) {
                Pick(tr("Priority", "Öncelik"), listOf("HW+", "SW", "HW"), { prefs.decoder }, done) { prefs.decoder = it }
                Sw(tr("Tunnelled playback", "Tünelli oynatma"), { prefs.tunnel }, { prefs.tunnel = it }, done)
                Pick(tr("Async codec", "Eşzamansız codec"), listOf(tr("Auto", "Otomatik"), tr("On", "Açık"), tr("Off", "Kapalı")), { prefs.asyncCodec }, done) { prefs.asyncCodec = it }
            }

            SettingsSection(tr("Display", "Ekran")) {
                Pick(tr("Aspect", "En boy"), aspectNames(), { prefs.aspect }, done) { prefs.aspect = it }
                Sw(tr("Match refresh rate", "Yenileme hızını eşle"), { prefs.matchRate }, { prefs.matchRate = it }, done)
                Sw(tr("HDR output", "HDR çıkışı"), { prefs.hdrOut }, { prefs.hdrOut = it }, done)
                Sw(tr("Fixed brightness", "Sabit parlaklık"), { prefs.brightFixed }, { prefs.brightFixed = it }, done)
                Sld(tr("Brightness", "Parlaklık"), "%", 1..100, { prefs.brightLevel }, done) { prefs.brightLevel = it }
            }

            SettingsSection(tr("Library", "Kütüphane")) {
                Pick(tr("View", "Görünüm"), listOf(tr("Folders", "Klasörler"), tr("Files", "Dosyalar"), tr("Tree", "Ağaç")), { prefs.browse }, done) { prefs.browse = it }
                Pick(tr("Sort", "Sırala"), listOf(tr("Title", "Ad"), tr("Date added", "Eklenme"), tr("Length", "Süre"), tr("Size", "Boyut"), tr("Path", "Yol"), tr("Resolution", "Çözünürlük"), tr("Modified", "Değişme")), { prefs.sortBy }, done) { prefs.sortBy = it }
                Sw(tr("Descending", "Azalan"), { prefs.sortDesc }, { prefs.sortDesc = it }, done)
                Sw(tr("Grid", "Izgara"), { prefs.grid }, { prefs.grid = it }, done)
                Sw(tr("Thumbnails", "Küçük resimler"), { prefs.showThumb }, { prefs.showThumb = it }, done)
                Sw(tr("Length", "Süre"), { prefs.showDur }, { prefs.showDur = it }, done)
                Sw(tr("Extension", "Uzantı"), { prefs.showExt }, { prefs.showExt = it }, done)
                Sw(tr("Path", "Yol"), { prefs.showPath }, { prefs.showPath = it }, done)
                Sw(tr("Size", "Boyut"), { prefs.showSize }, { prefs.showSize = it }, done)
                Sw(tr("Resolution", "Çözünürlük"), { prefs.showRes }, { prefs.showRes = it }, done)
                Sw(tr("Progress bar", "İlerleme çubuğu"), { prefs.showProgress }, { prefs.showProgress = it }, done)
                Sw(tr("New marker", "Yeni işareti"), { prefs.markNew }, { prefs.markNew = it }, done)
                Sw(tr("Recent", "Son oynatılanlar"), { prefs.showRecent }, { prefs.showRecent = it }, done)
                Num(tr("Recent length", "Son listesi uzunluğu"), listOf(10, 20, 40, 100), { prefs.recentMax }, { "$it" }, done) { prefs.recentMax = it }
                Num(tr("Shortest video", "En kısa video"), listOf(0, 10, 30, 60, 300), { prefs.minDur }, { if (it == 0) tr("Any", "Hepsi") else "$it s" }, done) { prefs.minDur = it }
                Sw(tr("Show hidden folders", "Gizli klasörleri göster"), { prefs.showHidden }, { prefs.showHidden = it }, done)
                Act(tr("Hidden folders", "Gizli klasörler"), { "${prefs.hidden.size}" }) { folderDlg = 1 }
                Act(tr("Only these folders", "Yalnız bu klasörler"), { if (prefs.include.isEmpty()) tr("All", "Hepsi") else "${prefs.include.size}" }) { folderDlg = 2 }
            }

            SettingsSection(tr("Network", "Ağ")) {
                Act(tr("User agent", "Kullanıcı aracısı"), { prefs.userAgent.ifBlank { tr("Default", "Varsayılan") }.take(18) }) { uaDlg = true }
                Sld(tr("Buffer min", "Tampon en az"), " s", 5..120, { prefs.bufMin }, done) { prefs.bufMin = it; if (prefs.bufMax < it) prefs.bufMax = it }
                Sld(tr("Buffer max", "Tampon en çok"), " s", 5..300, { prefs.bufMax }, done) { prefs.bufMax = it; if (prefs.bufMin > it) prefs.bufMin = it }
                Num(tr("Start playing at", "Başlama eşiği"), listOf(5, 10, 25, 50, 100, 150), { prefs.bufPlay }, { "${it / 10f} s" }, done) { prefs.bufPlay = it }
                Sld(tr("Connect timeout", "Bağlanma süresi"), " s", 2..60, { prefs.connTimeout }, done) { prefs.connTimeout = it }
                Sld(tr("Read timeout", "Okuma süresi"), " s", 2..60, { prefs.readTimeout }, done) { prefs.readTimeout = it }
                Num(tr("Stream cache", "Yayın önbelleği"), listOf(0, 100, 250, 500, 1000, 2000), { prefs.cacheMb }, { if (it == 0) tr("Off", "Kapalı") else "$it MB" }, done) { prefs.cacheMb = it }
                Act(tr("Clear cache", "Önbelleği temizle")) { clearNetCache(ctx); changed() }
                Act(tr("Clear addresses", "Adresleri temizle"), { "${prefs.urls().size}" }) { prefs.clearUrls(); changed() }
            }

            SettingsSection(tr("Privacy", "Gizlilik")) {
                Sw(tr("Incognito", "Gizli mod"), { prefs.incognito }, { prefs.incognito = it }, done)
                Sw(tr("Lock with fingerprint or screen lock", "Parmak izi veya ekran kilidiyle kilitle"), { prefs.lockOn && canLock(ctx) }, { prefs.lockOn = it && canLock(ctx) }, done)
                Act(tr("Clear history", "Geçmişi temizle")) { histDlg = true }
            }

            SettingsSection(tr("Backup", "Yedek")) {
                Act(tr("Save settings", "Ayarları kaydet")) { export.launch("video-player-settings.json") }
                Act(tr("Restore settings", "Ayarları geri yükle")) { import.launch(arrayOf("*/*")) }
                Act(tr("Reset all", "Hepsini sıfırla")) { resetDlg = true }
            }
        }
    }

    if (folderDlg != 0) {
        val dlgMode = folderDlg
        val all by produceState(emptyList<String>()) { value = withContext(Dispatchers.IO) { queryVideos(ctx).map { fk(it) }.distinct().sorted() } }
        tick.intValue
        val sel = if (dlgMode == 1) prefs.hidden else prefs.include
        val list = (all + sel).distinct().sorted()
        PickDialog(if (dlgMode == 1) tr("Hidden folders", "Gizli klasörler") else tr("Only these folders", "Yalnız bu klasörler"), list.map { it.ifBlank { "/" } to (it in sel) }, { folderDlg = 0 }) { i ->
            val f = list[i]
            if (dlgMode == 1) prefs.hidden = if (f in sel) sel - f else sel + f else prefs.include = if (f in sel) sel - f else sel + f
            changed()
        }
    }
    if (uaDlg) TextDialog(tr("User agent", "Kullanıcı aracısı"), prefs.userAgent, { uaDlg = false }) { prefs.userAgent = it.trim(); uaDlg = false; changed() }
    if (resetDlg) PDialog(tr("Reset all", "Hepsini sıfırla"), tr("Every player option returns to its default.", "Tüm oynatıcı ayarları varsayılana döner."), tr("Reset", "Sıfırla"), { prefs.resetAll(); resetDlg = false; changed() }, { resetDlg = false })
    if (histDlg) PDialog(tr("Clear history", "Geçmişi temizle"), tr("Recent list and saved positions are removed.", "Son liste ve kayıtlı konumlar silinir."), tr("Clear", "Temizle"), { prefs.clearHistory(); histDlg = false; changed() }, { histDlg = false })
}
