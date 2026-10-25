package com.umutk.pulse.extras.blackout

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which greys count as surfaces, how black they get, and the cards of the pages. Ported from the BlackOut app (GPL-3.0). */

const val PRIVACY_EN = "No internet permission. Nothing leaves the phone."

@Composable
fun PrivacyNote(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F1A12)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("lock", Color(0xFF66BB6A), 20.dp) }
        Spacer(Modifier.width(10.dp))
        Text(tr("Pulse has no part in BlackOut's settings leaving the phone: they stay here, and nothing of what BlackOut sees is stored or sent.", "BlackOut'un ayarları telefondan çıkmaz; BlackOut'un gördüğü hiçbir şey saklanmaz ya da gönderilmez."), fontSize = 13.sp, color = Color(0xFFB9E4BE))
    }
}

// ---- support line and root options ----------------------------------------------------------------------------------------------------------------

object RootOpts {
    /** True when the shell test [check] succeeds on this phone. Read-only. */
    fun answers(check: String): Boolean = Privilege.run("($check) >/dev/null 2>&1 && echo yes").text.lines().any { it.trim() == "yes" }

    /** Keeps what [read] returns, the first time only (call it before the first change). */
    fun remember(sp: SharedPreferences, key: String, read: () -> String) { if (!sp.contains("orig_$key")) sp.edit().putString("orig_$key", read()).apply() }
    fun original(sp: SharedPreferences, key: String): String? = sp.getString("orig_$key", null)
    fun forget(sp: SharedPreferences, key: String) { sp.edit().remove("orig_$key").apply() }
}

@Composable
fun SupportLine(mode: Privilege.Mode, supported: Boolean?) {
    val (text, color) = when {
        mode == Privilege.Mode.None -> tr("Needs root first (Settings > Root access).", "Önce root gerekir (Ayarlar > Root erişimi).") to WarnAmber
        supported == null -> tr("Checking this phone…", "Bu telefon denetleniyor…") to T.sub
        supported -> tr("Supported on this phone. Needs root.", "Bu telefonda destekleniyor. Root gerekir.") to OkGreen
        else -> tr("Not supported on this phone (the command or setting is missing).", "Bu telefonda desteklenmiyor (komut ya da ayar yok).") to WarnAmber
    }
    BoText(text, 13, color, modifier = Modifier.padding(top = 4.dp))
}

// ---- the accessibility watcher ---------------------------------------------------------------------------------------------------------------------

/** Is the accessibility service on? With buttons to switch it on (through root, one tap, or by hand in the system settings). */
@Composable
fun AccessRow(mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(AppWatch.enabled(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }
    Column {
        BoText(if (on) tr("Accessibility service is on", "Erişilebilirlik hizmeti açık") else tr("Accessibility service is off", "Erişilebilirlik hizmeti kapalı"), 13, if (on) OkGreen else WarnAmber, bold = true)
        BoText(tr("It sees where text and images are, never what they say. Nothing is stored or sent.", "Yazıların ve resimlerin nerede olduğunu görür, ne dediklerini asla. Hiçbir şey saklanmaz ya da gönderilmez."))
        if (!on) BoButtons {
            BoBtn(tr("Turn on (root)", "Aç (root)"), mode == Privilege.Mode.Root || mode == Privilege.Mode.Shizuku) {
                scope.launch {
                    val o = withContext(Dispatchers.IO) { AppWatch.enableWithPower() }
                    on = AppWatch.enabled(ctx); msg = if (on) null else tr("Root could not switch it on: ", "Root açamadı: ") + o.text.take(100)
                }
            }
            if (AppWatch.canSelfEnable(ctx)) BoBtn(tr("Turn on (one tap)", "Aç (tek dokunuş)")) { AppWatch.enableSelf(ctx); on = AppWatch.enabled(ctx); if (!on) msg = tr("Android did not switch it on. Open settings instead.", "Android açmadı. Bunun yerine ayarları açın.") }
            BoBtn(tr("Open settings", "Ayarları aç")) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            BoLink(tr("Refresh", "Yenile")) { on = AppWatch.enabled(ctx) }
        }
        msg?.let { BoText(it, 13, BadRed) }
        if (!on && !AppWatch.canSelfEnable(ctx) && mode == Privilege.Mode.None) {
            BoText(tr("No root? Run it once from a computer or wireless adb:", "Root yok mu? Bir kez bilgisayardan ya da kablosuz adb'den çalıştırın:"), modifier = Modifier.padding(top = 8.dp))
            BoText(AppWatch.ADB_GRANT, 12, T.text, mono = true)
            BoLink(tr("Copy command", "Komutu kopyala")) { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("adb", AppWatch.ADB_GRANT)) }
        }
    }
}

fun watcherNeeded(sp: SharedPreferences): Boolean {
    fun set(k: String) = !(sp.getStringSet(k, emptySet()) ?: emptySet()).isEmpty()
    return sp.getBoolean("ly_on", false) || set("ly_apps") || set("pages_apps") || set("crush_apps")
}

/** Per-app effects need the accessibility watcher: say so on the home page when one is on and the watcher is off. */
@Composable
fun WatcherReminder(sp: SharedPreferences, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val needs = watcherNeeded(sp)
    var on by remember { mutableStateOf(AppWatch.enabled(ctx)) }
    LaunchedEffect(needs) { on = AppWatch.enabled(ctx) }
    if (!needs || on) return
    BoCard {
        BoText(tr("The watcher is off", "İzleyici kapalı"), 16, WarnAmber, bold = true)
        BoText(tr("Effects only work while the accessibility service is on.", "Etkiler yalnız erişilebilirlik hizmeti açıkken çalışır."), 14)
        AccessRow(mode)
    }
}

// ---- per-app cards -------------------------------------------------------------------------------------------------------------------------------

/** What the module reported from inside the app: is it there, can it read the settings, and did it change anything. */
@Composable
fun HookStatusBlock(r: HookReport?, wanted: Boolean) {
    Column(Modifier.padding(top = 8.dp)) {
        if (r == null) {
            BoText(tr("Not active yet", "Henüz etkin değil"), 13, WarnAmber, bold = true)
            BoText(tr("LSPosed > BlackOut > tick the app > Restart app", "LSPosed > BlackOut > uygulamayı işaretle > Uygulamayı yeniden başlat"))
            return
        }
        val age = (System.currentTimeMillis() - r.at) / 1000
        val fresh = age < 40
        BoText(if (fresh) tr("Module is running in this app", "Modül bu uygulamada çalışıyor") else tr("Module was running here ${age / 60} min ago (the app is closed now)", "Modül burada ${age / 60} dk önce çalışıyordu (uygulama şimdi kapalı)"), 13, if (fresh) OkGreen else T.sub, bold = true)
        if (r.keys == 0) BoText(tr("It cannot read BlackOut's settings, so nothing you switch on here reaches it.", "BlackOut'un ayarlarını okuyamıyor, bu yüzden burada açtığınız hiçbir şey ona ulaşmıyor."), 13, BadRed)
        else if (wanted && !r.fd && !r.smart) BoText(tr("This app was started before you switched it on. Press Restart app.", "Bu uygulama siz açmadan önce başlamıştı. Uygulamayı yeniden başlat'a basın."), 13, WarnAmber)
        BoText("Android ${r.sdk} · ${r.api} · " + tr("settings read: ", "okunan ayar: ") + (if (r.keys < 0) "?" else r.keys.toString()))
        if (r.fd && r.forced > 0) BoText(tr("Force dark switched on in ${r.forced} window(s)", "Zorla koyu ${r.forced} pencerede açıldı"), 13, OkGreen)
        if (r.smart || r.pages) BoText(tr("Repainted ${r.fills} light areas, turned ${r.texts} dark texts light", "${r.fills} açık alan yeniden boyandı, ${r.texts} koyu yazı açık yapıldı") + (if (r.fills == 0 && r.texts == 0) tr(" (nothing yet: the app may draw with its own engine)", " (henüz bir şey yok: uygulama kendi motoruyla çiziyor olabilir)") else ""), 13, if (r.fills + r.texts > 0) OkGreen else WarnAmber)
        BoText(tr("Dark greys turned black: ${r.greys}", "Siyaha çevrilen koyu gri: ${r.greys}"))
    }
}

@Composable
private fun fixAccessButton(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, mode: Privilege.Mode, restartMsg: Boolean, onMsg: (String) -> Unit) {
    BoBtn(tr("Fix settings access (root)", "Ayar erişimini düzelt (root)"), mode != Privilege.Mode.None) {
        scope.launch {
            val o = withContext(Dispatchers.IO) { Privilege.run("chmod 755 /data/data/${ctx.packageName}; chmod 755 /data/data/${ctx.packageName}/shared_prefs; chmod 644 /data/data/${ctx.packageName}/shared_prefs/blackout.xml; echo ok") }
            onMsg(if (o.text.contains("ok")) tr(if (restartMsg) "Settings are readable now. Restart the app." else "Settings are readable by the module now.", if (restartMsg) "Ayarlar artık okunabilir. Uygulamayı yeniden başlatın." else "Ayarlar artık modül tarafından okunabilir.") else tr("Failed: ", "Başarısız: ") + o.text.take(100))
        }
    }
}

/** In an app's page: make a light-only app dark (LSPosed: Android's force dark + a pure-black repaint), with a live view of what the module is doing in it. */
@Composable
fun ForceDarkCard(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var fd by remember { mutableStateOf(sp.getStringSet("fd_apps", emptySet()) ?: emptySet()) }
    var smart by remember { mutableStateOf(sp.getStringSet("smart_apps", emptySet()) ?: emptySet()) }
    var adv by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf(HookReport.read(ctx, pkg)) }
    LaunchedEffect(pkg) { while (true) { report = HookReport.read(ctx, pkg); delay(3000) } }
    fun setBoth(v: Boolean) {
        fd = if (v) fd + pkg else fd - pkg; smart = if (v) smart + pkg else smart - pkg
        sp.edit().putStringSet("fd_apps", fd).putStringSet("smart_apps", smart).apply()
    }
    BoCard {
        BoSwitchRow(tr("Make this app dark (LSPosed)", "Bu uygulamayı koyu yap (LSPosed)"), tr("For light-only apps: pages turn dark and white areas black.", "Yalnız açık temalı uygulamalar için: sayfalar koyulaşır, beyaz alanlar siyah olur."), pkg in fd && pkg in smart, bold = true) { setBoth(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoText(tr("Takes effect when the app starts again.", "Uygulama yeniden başlayınca etkili olur."), modifier = Modifier.weight(1f))
            BoBtn(tr("Restart app", "Uygulamayı yeniden başlat"), mode != Privilege.Mode.None) { scope.launch { withContext(Dispatchers.IO) { Overlays.forceStop(pkg) }; msg = tr("Stopped. Open the app again and wait a few seconds.", "Durduruldu. Uygulamayı yeniden açıp birkaç saniye bekleyin.") } }
        }
        HookStatusBlock(report, pkg in fd || pkg in smart)
        if (report != null && report!!.keys == 0) fixAccessButton(ctx, scope, mode, true) { msg = it }
        msg?.let { BoText(it) }
        BoLink(if (adv) tr("Hide the two parts", "İki parçayı gizle") else tr("Use the two parts separately", "İki parçayı ayrı kullan")) { adv = !adv }
        if (adv) {
            BoSwitchRow(tr("Force dark only", "Yalnız zorla koyu"), tr("Android's engine, about #1C1C1C.", "Android'in motoru, yaklaşık #1C1C1C."), pkg in fd) { v -> fd = if (v) fd + pkg else fd - pkg; sp.edit().putStringSet("fd_apps", fd).apply() }
            BoSwitchRow(tr("Pure black repaint only", "Yalnız saf siyah yeniden boyama"), tr("Without force dark, a light spot it misses stays light under light text.", "Zorla koyu olmadan, kaçırdığı açık bir nokta açık yazının altında açık kalır."), pkg in smart) { v -> smart = if (v) smart + pkg else smart - pkg; sp.edit().putStringSet("smart_apps", smart).apply() }
        }
    }
}

/** In an app's page: only the pages inside this app swap light and dark (LSPosed), the rest of the app stays as it is. */
@Composable
fun PagesModCard(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(sp.getStringSet("pgmod_apps", emptySet()) ?: emptySet()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf(HookReport.read(ctx, pkg)) }
    LaunchedEffect(pkg) { while (true) { report = HookReport.read(ctx, pkg); delay(3000) } }
    BoCard {
        BoSwitchRow(tr("Dark pages, only the pages (LSPosed)", "Koyu sayfalar, yalnız sayfalar (LSPosed)"), tr("Only the pages: big light pictures and white areas turn dark. Toolbars and icons stay.", "Yalnız sayfalar: büyük açık resimler ve beyaz alanlar koyulaşır. Araç çubukları ve ikonlar kalır."), pkg in apps, bold = true) { v -> apps = if (v) apps + pkg else apps - pkg; sp.edit().putStringSet("pgmod_apps", apps).apply() }
        HookStatusBlock(report, pkg in apps)
        PageViewPicker(pkg, mode)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BoText(tr("Takes effect when the app starts again.", "Uygulama yeniden başlayınca etkili olur."), modifier = Modifier.weight(1f))
            BoBtn(tr("Restart app", "Uygulamayı yeniden başlat"), mode != Privilege.Mode.None) { scope.launch { withContext(Dispatchers.IO) { Overlays.forceStop(pkg) }; msg = tr("Stopped. Open the app again.", "Durduruldu. Uygulamayı yeniden açın.") } }
        }
        msg?.let { BoText(it) }
    }
}

/** In an app's page: apply Dark pages (white pages to your colour, text to white) only while this app is open. */
@Composable
fun PagesAppCard(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf(sp.getStringSet("pages_apps", emptySet()) ?: emptySet()) }
    val scope = rememberCoroutineScope()
    BoCard {
        BoSwitchRow(tr("Dark pages", "Koyu sayfalar"), tr("A filter over the screen, only while this app is open.", "Yalnız bu uygulama açıkken ekranın üstünde bir filtre."), pkg in apps, mode != Privilege.Mode.None) { v ->
            apps = if (v) apps + pkg else apps - pkg; sp.edit().putStringSet("pages_apps", apps).apply()
            // the watcher is what notices the app opening; with root it can be switched on right here
            if (v && !AppWatch.enabled(ctx)) scope.launch { withContext(Dispatchers.IO) { AppWatch.enableWithPower() } }
        }
        if (mode == Privilege.Mode.None) BoText(tr("Needs root or Shizuku.", "Root ya da Shizuku gerekir."), 13, WarnAmber)
        if (pkg in apps && !AppWatch.enabled(ctx)) { Spacer(Modifier.height(6.dp)); AccessRow(mode) }
    }
}

/** In an app's page: Android's own colour inversion while this app is open. */
@Composable
fun InvertAppCard(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(sp.getStringSet("inv_apps", emptySet()) ?: emptySet()) }
    BoCard {
        BoSwitchRow(tr("Invert", "Ters çevir"), tr("Android's own inversion while this app is open; photos turn negative too.", "Bu uygulama açıkken Android'in kendi ters çevirmesi; fotoğraflar da negatif olur."), pkg in apps, mode != Privilege.Mode.None) { v ->
            apps = if (v) apps + pkg else apps - pkg; sp.edit().putStringSet("inv_apps", apps).apply()
            if (v && !AppWatch.enabled(ctx)) scope.launch { withContext(Dispatchers.IO) { AppWatch.enableWithPower() } }
        }
    }
}

/** Samsung Notes style apps: the list's small previews are drawn through Android's Canvas, the note itself by the app's own engine. */
@Composable
fun EditorAppCard(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf(sp.getStringSet("editor_apps", emptySet()) ?: emptySet()) }
    val scope = rememberCoroutineScope()
    BoCard {
        BoSwitchRow(tr("Dark inside a note", "Notun içinde koyu"), tr("Notes: Dark pages works on the list screen; toolbars may stay light.", "Notlar: Koyu sayfalar liste ekranında çalışır; araç çubukları açık kalabilir."), pkg in apps, mode != Privilege.Mode.None) { v ->
            apps = if (v) apps + pkg else apps - pkg; sp.edit().putStringSet("editor_apps", apps).apply()
            if (v && !AppWatch.enabled(ctx)) scope.launch { withContext(Dispatchers.IO) { AppWatch.enableWithPower() } }
        }
        if (mode == Privilege.Mode.None) BoText(tr("Needs root or Shizuku.", "Root ya da Shizuku gerekir."), 13, WarnAmber)
    }
}

/** For apps with stripped resource names: deepen the dark greys of the whole screen, but only while this app is in front. */
@Composable
fun CrushCard(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(sp.getStringSet("crush_apps", emptySet()) ?: emptySet()) }
    var level by remember { mutableFloatStateOf(sp.getInt("crush_level", 30).toFloat()) }
    var watching by remember { mutableStateOf(AppWatch.enabled(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }
    BoCard {
        BoText(tr("This app hides its colours", "Bu uygulama renklerini gizliyor"), 15, T.text, bold = true)
        BoText(tr("Overlays cannot recolour it. With root, BlackOut can darken the screen while this app is open.", "Kaplamalar onu yeniden renklendiremez. Root ile BlackOut, bu uygulama açıkken ekranı koyulaştırabilir."))
        BoSwitchRow(tr("Darken while this app is open", "Bu uygulama açıkken koyulaştır"), "", pkg in apps) { v -> apps = if (v) apps + pkg else apps - pkg; sp.edit().putStringSet("crush_apps", apps).apply() }
        BoText(tr("Greys up to ${level.toInt()} / 255 go pure black. White stays white.", "${level.toInt()} / 255'e kadar griler saf siyah olur. Beyaz beyaz kalır."), 13, T.text)
        BoSlider(level, 8f..80f, onChange = { level = it }, onDone = { sp.edit().putInt("crush_level", level.toInt()).apply() })
        LevelPreview(level)
        BoButtons {
            BoBtn(tr("Try now", "Şimdi dene"), mode != Privilege.Mode.None) { scope.launch { withContext(Dispatchers.IO) { PageDark.applyCrush(level.toInt()) } } }
            BoBtn(tr("Undo", "Geri al"), mode != Privilege.Mode.None) { scope.launch { withContext(Dispatchers.IO) { PageDark.clear() } } }
        }
        BoText(if (watching) tr("Watcher is on. It switches by itself.", "İzleyici açık. Kendiliğinden geçiş yapar.") else tr("Watcher is off. Turn it on so this only applies to this app.", "İzleyici kapalı. Bunun yalnız bu uygulamada uygulanması için açın."), 13, if (watching) OkGreen else WarnAmber, modifier = Modifier.padding(top = 6.dp))
        if (!watching) BoButtons {
            BoBtn(tr("Turn on (root)", "Aç (root)"), mode != Privilege.Mode.None) {
                scope.launch {
                    val o = withContext(Dispatchers.IO) { AppWatch.enableWithPower() }
                    watching = AppWatch.enabled(ctx); msg = if (watching) null else tr("Could not switch it on: ", "Açılamadı: ") + o.text.take(120)
                }
            }
            BoBtn(tr("Open accessibility", "Erişilebilirliği aç")) { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        msg?.let { BoText(it, 13, BadRed) }
    }
}

/** For pages an app draws with its own view: list the big views of the app in front, tick the one that is the page. */
@Composable
fun PageViewPicker(pkg: String, mode: Privilege.Mode) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(sp.getStringSet("pgview_$pkg", emptySet()) ?: emptySet()) }
    var found by remember { mutableStateOf(sp.getStringSet("pgcand_$pkg", emptySet())?.toList()?.sortedByDescending { it.substringAfterLast('|').toLongOrNull() ?: 0L } ?: emptyList()) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 8.dp)) {
        BoText(tr("Page view (another way)", "Sayfa görünümü (başka bir yol)"), 14, T.text, bold = true)
        BoText(tr("Page not changing? Tap Find, open a page, then tick the big view.", "Sayfa değişmiyor mu? Bul'a dokunun, bir sayfa açın, sonra büyük görünümü işaretleyin."))
        BoBtn(if (busy) tr("Waiting…", "Bekleniyor…") else tr("Find the page view", "Sayfa görünümünü bul"), mode != Privilege.Mode.None && !busy, Modifier.padding(top = 6.dp)) {
            busy = true
            scope.launch {
                android.widget.Toast.makeText(ctx, tr("Switch to the app now…", "Şimdi uygulamaya geçin…"), android.widget.Toast.LENGTH_LONG).show()
                delay(8000)
                val out = withContext(Dispatchers.IO) { Privilege.run("dumpsys activity top") }
                val dm = ctx.resources.displayMetrics
                val area = dm.widthPixels.toLong() * dm.heightPixels
                val re = Regex("^\\s*([\\w.$]+)\\{[0-9a-f]+ \\S+ \\S+ (\\d+),(\\d+)-(\\d+),(\\d+)")
                var inApp = false
                val list = LinkedHashMap<String, Long>()
                out.text.lineSequence().forEach { ln ->
                    if (ln.contains("ACTIVITY ")) inApp = ln.contains(" $pkg/")
                    if (!inApp) return@forEach
                    val m = re.find(ln) ?: return@forEach
                    val cls = m.groupValues[1]
                    if (cls.startsWith("android.widget.") || cls.startsWith("android.view.") || cls.startsWith("com.android.internal.")) return@forEach
                    val a = (m.groupValues[4].toLong() - m.groupValues[2].toLong()) * (m.groupValues[5].toLong() - m.groupValues[3].toLong())
                    if (a * 5 >= area) list[cls] = maxOf(list[cls] ?: 0L, a)
                }
                found = list.map { "${it.key}|${it.value}" }.sortedByDescending { it.substringAfterLast('|').toLong() }
                sp.edit().putStringSet("pgcand_$pkg", found.toSet()).apply()
                android.widget.Toast.makeText(ctx, if (found.isEmpty()) tr("Nothing found. Was the app in front?", "Bir şey bulunamadı. Uygulama öndeydi mi?") else tr("${found.size} views found", "${found.size} görünüm bulundu"), android.widget.Toast.LENGTH_LONG).show()
                busy = false
            }
        }
        found.forEach { e ->
            val cls = e.substringBeforeLast('|')
            Row(Modifier.fillMaxWidth().clickable { picked = if (cls in picked) picked - cls else picked + cls; sp.edit().putStringSet("pgview_$pkg", picked).apply() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                com.umutk.pulse.PCheck(cls in picked, { v -> picked = if (v) picked + cls else picked - cls; sp.edit().putStringSet("pgview_$pkg", picked).apply() })
                Column(Modifier.padding(start = 8.dp)) {
                    BoText(cls.substringAfterLast('.'), 14, T.text)
                    BoText(cls.substringBeforeLast('.', ""), 11, maxLines = 1)
                }
            }
        }
    }
}

// ---- whole-phone cards ----------------------------------------------------------------------------------------------------------------------------

/** Android's own "Override force-dark" (developer options) through root, for every app that has a light theme. */
@Composable
fun SystemForceDarkCard(mode: Privilege.Mode, sp: SharedPreferences) {
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(sp.getBoolean("fdsys_on", false)) }
    var now by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    fun read() { scope.launch { now = withContext(Dispatchers.IO) { Privilege.run("getprop debug.hwui.force_dark").text.trim().ifBlank { "(not set)" } } } }
    LaunchedEffect(mode) { if (mode != Privilege.Mode.None) { read(); supported = withContext(Dispatchers.IO) { RootOpts.answers("[ \"\$(getprop ro.build.version.sdk)\" -ge 29 ]") } } }
    BoCard {
        BoSwitchRow(if (on) tr("Force dark is ON (all light apps)", "Zorla koyu AÇIK (tüm açık uygulamalar)") else tr("Force dark is off", "Zorla koyu kapalı"), tr("Same as Developer options > Override force-dark. Needs root, no LSPosed.", "Geliştirici seçenekleri > Zorla koyuyu geçersiz kıl ile aynı. Root gerekir, LSPosed gerekmez."), on, mode != Privilege.Mode.None && supported != false, bold = true) { v ->
            on = v; sp.edit().putBoolean("fdsys_on", v).apply()
            scope.launch {
                val o = withContext(Dispatchers.IO) {
                    if (v) RootOpts.remember(sp, "fd") { Privilege.run("getprop debug.hwui.force_dark").text.trim() }
                    Privilege.run("setprop debug.hwui.force_dark ${if (v) "true" else "false"}; getprop debug.hwui.force_dark")
                }
                now = o.text.trim().ifBlank { "(not set)" }
                msg = if (o.text.trim() == (if (v) "true" else "false")) tr("Done. Apps pick it up when they start: close them in recents and open them again.", "Tamam. Uygulamalar başlarken alır: son uygulamalardan kapatıp yeniden açın.") else tr("The phone did not take it: ", "Telefon kabul etmedi: ") + o.text.take(120)
            }
        }
        SupportLine(mode, supported)
        now?.let { BoText("Android: debug.hwui.force_dark = $it", modifier = Modifier.padding(top = 6.dp)) }
        BoBtn(tr("Reset to original", "Aslına döndür"), mode != Privilege.Mode.None, Modifier.padding(top = 8.dp)) {
            scope.launch {
                val (orig, o) = withContext(Dispatchers.IO) {
                    val orig = RootOpts.original(sp, "fd") ?: "false"
                    RootOpts.forget(sp, "fd")
                    orig to Privilege.run("setprop debug.hwui.force_dark ${if (orig == "true") "true" else "false"}; getprop debug.hwui.force_dark")
                }
                on = orig == "true"; sp.edit().putBoolean("fdsys_on", on).apply()
                now = o.text.trim().ifBlank { "(not set)" }; msg = tr("Back to the setting from before BlackOut.", "BlackOut'tan önceki ayara dönüldü.")
            }
        }
        msg?.let { BoText(it, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Root: the system's own dark theme (Settings > Display > Dark theme). */
@Composable
fun SystemDarkThemeCard(mode: Privilege.Mode, sp: SharedPreferences) {
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(sp.getBoolean("sysdark_on", false)) }
    var msg by remember { mutableStateOf<String?>(null) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(mode) { if (mode != Privilege.Mode.None) supported = withContext(Dispatchers.IO) { RootOpts.answers("cmd uimode night") } }
    BoCard {
        BoSwitchRow(if (on) tr("System dark theme is ON", "Sistem koyu teması AÇIK") else tr("System dark theme is off", "Sistem koyu teması kapalı"), tr("Same as Settings > Display > Dark theme. Needs root. Apps that follow the system theme switch too.", "Ayarlar > Ekran > Koyu tema ile aynı. Root gerekir. Sistem temasını izleyen uygulamalar da geçer."), on, mode != Privilege.Mode.None && supported != false, bold = true) { v ->
            on = v; sp.edit().putBoolean("sysdark_on", v).apply()
            scope.launch {
                val o = withContext(Dispatchers.IO) {
                    RootOpts.remember(sp, "night") { if (Privilege.run("cmd uimode night 2>/dev/null").text.contains("yes", ignoreCase = true)) "yes" else "no" }
                    Privilege.run("cmd uimode night ${if (v) "yes" else "no"} 2>/dev/null || settings put secure ui_night_mode ${if (v) 2 else 1}; cmd uimode night 2>/dev/null")
                }
                msg = if (o.text.contains(if (v) "yes" else "no", ignoreCase = true)) tr("Done.", "Tamam.") else tr("The phone did not take it: ", "Telefon kabul etmedi: ") + o.text.take(120)
            }
        }
        SupportLine(mode, supported)
        BoBtn(tr("Reset to original", "Aslına döndür"), mode != Privilege.Mode.None, Modifier.padding(top = 8.dp)) {
            scope.launch {
                val (orig, o) = withContext(Dispatchers.IO) {
                    val orig = RootOpts.original(sp, "night") ?: "no"
                    RootOpts.forget(sp, "night")
                    orig to Privilege.run("cmd uimode night $orig 2>/dev/null || settings put secure ui_night_mode ${if (orig == "yes") 2 else 1}; cmd uimode night 2>/dev/null")
                }
                on = orig == "yes"; sp.edit().putBoolean("sysdark_on", on).apply()
                msg = tr("Back to the setting from before BlackOut: ", "BlackOut'tan önceki ayara dönüldü: ") + o.text.trim().take(60)
            }
        }
        msg?.let { BoText(it, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Root: Android's "Reduce bright colors" (extra dim) with a strength slider, for a dim black screen. Android 11+. */
@Composable
fun DimBlackCard(mode: Privilege.Mode, sp: SharedPreferences) {
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(false) }
    var level by remember { mutableStateOf(sp.getInt("dim_level", 50).toFloat()) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val key = "reduce_bright_colors_activated"
    LaunchedEffect(mode) {
        if (mode != Privilege.Mode.None) {
            val (sup, value) = withContext(Dispatchers.IO) { RootOpts.answers("[ \"\$(getprop ro.build.version.sdk)\" -ge 30 ]") to Privilege.run("settings get secure $key").text.trim() }
            supported = sup; on = value == "1"
        }
    }
    fun push(v: Boolean, l: Int) {
        scope.launch {
            val o = withContext(Dispatchers.IO) {
                if (v) RootOpts.remember(sp, "dim") { Privilege.run("settings get secure $key").text.trim() }
                Privilege.run("settings put secure reduce_bright_colors_level $l; settings put secure $key ${if (v) 1 else 0}; settings get secure $key")
            }
            msg = if (o.text.trim().lines().last().trim() == (if (v) "1" else "0")) null else tr("The phone did not take it: ", "Telefon kabul etmedi: ") + o.text.take(120)
        }
    }
    BoCard {
        BoSwitchRow(if (on) tr("Dim black is ON (${level.toInt()}%)", "Loş siyah AÇIK (%${level.toInt()})") else tr("Dim black is off", "Loş siyah kapalı"), tr("Same as Settings > Accessibility > Reduce bright colors. Dims the whole screen below black.", "Ayarlar > Erişilebilirlik > Parlak renkleri azalt ile aynı. Tüm ekranı siyahın altına kadar karartır."), on, mode != Privilege.Mode.None && supported == true, bold = true) { v -> on = v; push(v, level.toInt()) }
        SupportLine(mode, supported)
        BoText(tr("Strength", "Güç"), 14, T.text, modifier = Modifier.padding(top = 8.dp))
        BoSlider(level, 0f..100f, mode != Privilege.Mode.None && supported == true, { level = it }) { sp.edit().putInt("dim_level", level.toInt()).apply(); if (on) push(true, level.toInt()) }
        BoBtn(tr("Reset to original", "Aslına döndür"), mode != Privilege.Mode.None, Modifier.padding(top = 8.dp)) {
            scope.launch {
                val (orig, o) = withContext(Dispatchers.IO) {
                    val orig = RootOpts.original(sp, "dim") ?: "0"
                    RootOpts.forget(sp, "dim")
                    orig to Privilege.run("settings put secure $key ${if (orig == "1") 1 else 0}; settings get secure $key")
                }
                on = orig == "1"
                msg = tr("Back to the setting from before BlackOut: ", "BlackOut'tan önceki ayara dönüldü: ") + o.text.trim().lines().last().trim()
            }
        }
        msg?.let { BoText(it, modifier = Modifier.padding(top = 4.dp)) }
    }
}

/** Android's own whole-screen colour inversion, switched through root. Off unless you switch it on. */
@Composable
fun InvertScreenCard(mode: Privilege.Mode, sp: SharedPreferences) {
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(false) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var now by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val key = "accessibility_display_inversion_enabled"
    LaunchedEffect(mode) {
        if (mode != Privilege.Mode.None) {
            val (sup, value) = withContext(Dispatchers.IO) { RootOpts.answers("[ \"\$(getprop ro.build.version.sdk)\" -ge 24 ]") to Privilege.run("settings get secure $key").text.trim() }
            supported = sup; now = value; on = value == "1"
            sp.edit().putBoolean("inv_on", on).apply()
        }
    }
    BoCard {
        BoSwitchRow(if (on) tr("Invert the whole screen is ON", "Tüm ekranı ters çevir AÇIK") else tr("Invert the whole screen is off", "Tüm ekranı ters çevir kapalı"), tr("Same as Settings > Accessibility > Color inversion. Photos and the status bar turn negative too; Android cannot exclude them.", "Ayarlar > Erişilebilirlik > Renk tersine çevirme ile aynı. Fotoğraflar ve durum çubuğu da negatif olur; Android bunları hariç tutamaz."), on, mode != Privilege.Mode.None && supported == true, bold = true) { v ->
            on = v; sp.edit().putBoolean("inv_on", v).apply()
            scope.launch {
                val o = withContext(Dispatchers.IO) {
                    if (v) RootOpts.remember(sp, "inv") { Privilege.run("settings get secure $key").text.trim() }
                    Privilege.run("settings put secure $key ${if (v) 1 else 0}; settings get secure $key")
                }
                now = o.text.trim()
                msg = if (now == (if (v) "1" else "0")) null else tr("The phone did not take it: ", "Telefon kabul etmedi: ") + o.text.take(120)
            }
        }
        SupportLine(mode, supported)
        now?.let { BoText("Android: $key = $it", modifier = Modifier.padding(top = 4.dp)) }
        BoBtn(tr("Reset to original", "Aslına döndür"), mode != Privilege.Mode.None, Modifier.padding(top = 8.dp)) {
            scope.launch {
                val (orig, o) = withContext(Dispatchers.IO) {
                    val orig = RootOpts.original(sp, "inv") ?: "0"
                    RootOpts.forget(sp, "inv")
                    orig to Privilege.run("settings put secure $key ${if (orig == "1") 1 else 0}; settings get secure $key")
                }
                on = orig == "1"; sp.edit().putBoolean("inv_on", on).apply()
                now = o.text.trim(); msg = tr("Back to the setting from before BlackOut.", "BlackOut'tan önceki ayara dönüldü.")
            }
        }
        msg?.let { BoText(it, modifier = Modifier.padding(top = 4.dp)) }
    }
}

// ---- previews and pickers -----------------------------------------------------------------------------------------------------------------------

/** Dark greys of well-known app styles for the preview: page, top bar, card, sheet, chip. */
class MockLook(val name: String, val greys: LongArray)
val MockLooks = listOf(
    MockLook("Material", longArrayOf(0xFF121212, 0xFF1F1F1F, 0xFF2C2C2C, 0xFF424242, 0xFF5A5A5A)),
    MockLook("Play Store", longArrayOf(0xFF131314, 0xFF1E1F20, 0xFF282A2C, 0xFF333537, 0xFF444746)),
    MockLook("Chat app", longArrayOf(0xFF0B141A, 0xFF1F2C34, 0xFF202C33, 0xFF2A3942, 0xFF3B4A54)),
    MockLook("One UI", longArrayOf(0xFF000000, 0xFF141414, 0xFF252525, 0xFF2F2F2F, 0xFF444444)),
    MockLook("Telegram", longArrayOf(0xFF0E1621, 0xFF17212B, 0xFF1D2733, 0xFF242F3D, 0xFF2B5278)),
)

/** A tiny dark-theme app in the greys apps commonly use, optionally pushed through [fix]. */
@Composable
private fun MockDarkApp(fix: ((Int) -> Int)?, modifier: Modifier, look: MockLook = MockLooks[0]) {
    fun c(v: Long) = Color(fix?.invoke(v.toInt()) ?: v.toInt())
    val pal = look.greys
    Canvas(modifier.height(170.dp).clip(RoundedCornerShape(10.dp))) {
        val w = size.width; val h = size.height
        drawRect(c(pal[0]))
        drawRect(c(pal[1]), size = Size(w, h * .13f))
        drawRect(Color(0xFFE6E6E6), Offset(w * .08f, h * .045f), Size(w * .40f, h * .04f))
        for (i in 0..1) {
            val y = h * (.19f + i * .24f)
            drawRoundRect(c(pal[2]), Offset(w * .06f, y), Size(w * .88f, h * .20f), CornerRadius(14f))
            drawRect(Color(0xFFDADADA), Offset(w * .12f, y + h * .05f), Size(w * .50f, h * .03f))
            drawRect(Color(0xFF9A9A9A), Offset(w * .12f, y + h * .11f), Size(w * .70f, h * .025f))
        }
        drawRoundRect(c(pal[3]), Offset(0f, h * .70f), Size(w, h * .30f + 20f), CornerRadius(22f))
        drawRoundRect(c(pal[4]), Offset(w * .08f, h * .77f), Size(w * .30f, h * .08f), CornerRadius(30f))
        drawRoundRect(Color(0xFF8AB4F8), Offset(w * .56f, h * .86f), Size(w * .36f, h * .09f), CornerRadius(30f))
    }
}

/** What the grey limit means, in colours: a strip of greys (top: what an app asks for, bottom: what it gets) and a small dark app before and after. */
@Composable
fun GreyPreview(limit: Float, keep: Float, tint: Int = 0, soft: Boolean = false) {
    val l = limit.toInt(); val k = keep / 100f
    var look by remember { mutableStateOf(0) }
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
            for (v in 16..112 step 8) {
                val c = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                val out = Grey.fix(c, l, k, tint, soft)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(c)))
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(out)))
                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (out != c) T.accent else Color.Transparent))
                }
            }
        }
        BoText(tr("Top row: the grey an app asks for. Bottom row: what it gets. A dot marks every grey that changes.", "Üst sıra: uygulamanın istediği gri. Alt sıra: aldığı. Her değişen griyi bir nokta işaretler."))
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MockLooks.forEachIndexed { i, lk -> PChip(look == i, lk.name) { look = i } }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MockDarkApp(null, Modifier.weight(1f), MockLooks[look])
            MockDarkApp({ Grey.fix(it, l, k, tint, soft) }, Modifier.weight(1f), MockLooks[look])
        }
        BoText(tr("Pick an app look. Left: before. Right: the same app with this setting.", "Bir uygulama görünümü seçin. Sol: önce. Sağ: aynı uygulama bu ayarla."), modifier = Modifier.padding(top = 4.dp))
    }
}

/** What the black level does: every tone gets [level] darker. */
@Composable
fun LevelPreview(level: Float) {
    val l = level.toInt()
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
            for (v in 0..144 step 12) {
                val o = (v - l).coerceAtLeast(0)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF000000.toInt() or (v shl 16) or (v shl 8) or v)))
                    Box(Modifier.fillMaxWidth().height(24.dp).background(Color(0xFF000000.toInt() or (o shl 16) or (o shl 8) or o)))
                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape).background(if (o == 0 && v > 0) T.accent else Color.Transparent))
                }
            }
        }
        BoText(tr("Top row: the screen now. Bottom row: with this level. A dot marks the greys that become pure black.", "Üst sıra: şimdiki ekran. Alt sıra: bu seviyeyle. Nokta, saf siyah olan grileri işaretler."))
    }
}

/** The grey limit slider with its colour in sight: a swatch of the chosen grey on the left, black on the right. */
@Composable
fun GreySlider(limit: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val v = limit.toInt()
    val grey = Color(v / 255f, v / 255f, v / 255f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(grey).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(6.dp)))
        Text("  " + tr("up to", "şuna kadar") + " #%02X%02X%02X".format(v, v, v), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(6.dp)))
    }
    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(Color(24 / 255f, 24 / 255f, 24 / 255f), Color(96 / 255f, 96 / 255f, 96 / 255f)))))
    BoSlider(limit, 24f..96f, onChange = onChange, onDone = onDone)
    BoText(tr("Left: nearly black. Right: lighter grey.", "Sol: neredeyse siyah. Sağ: daha açık gri."), 12)
}

val TintChoices = listOf("Black" to 0x000000, "Navy" to 0x050A18, "Blue" to 0x08142C, "Teal" to 0x051616, "Forest" to 0x07120A, "Plum" to 0x120818, "Warm" to 0x140E08, "Red" to 0x1A0808)

/** Side colour for the "black": instead of #000000 the surfaces become a very dark blue, green, plum. */
@Composable
fun TintPicker(tint: Int, onPick: (Int) -> Unit) {
    BoText(tr("Colour of the dark", "Koyunun rengi"), 13, T.text, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TintChoices.forEach { (n, c) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPick(c) }) {
                BoDot(c, 36.dp, ring = c == tint)
                BoText(n, 11)
            }
        }
    }
}

// ---- dark pages (the screen filter) --------------------------------------------------------------------------------------------------------------------

private val SwatchSamples = listOf(0xFFFFFF, 0xF2F2F2, 0x9E9E9E, 0x3A3A3A, 0x000000, 0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x1E88E5)
private val PagePresets = listOf("AMOLED" to 0x000000, "Charcoal" to 0x121212, "Graphite" to 0x1E1F22, "Navy" to 0x0B1220, "Warm" to 0x1A1410, "Forest" to 0x0C1510)

/** A tiny fake web page (title, text lines, a photo, a button) drawn in normal colours, optionally pushed through a colour filter. */
@Composable
fun MockPage(cf: ColorFilter?, modifier: Modifier) {
    Canvas(modifier.height(170.dp).clip(RoundedCornerShape(10.dp))) {
        val w = size.width; val h = size.height
        fun rect(c: Color, x: Float, y: Float, rw: Float, rh: Float) = drawRect(c, Offset(x, y), Size(rw, rh), colorFilter = cf)
        rect(Color.White, 0f, 0f, w, h)
        rect(Color(0xFF111111), w * .08f, h * .07f, w * .55f, h * .07f)
        for (i in 0..3) rect(Color(0xFF222222), w * .08f, h * (.20f + i * .06f), w * (.84f - (i % 2) * .2f), h * .03f)
        rect(Color(0xFF4FA3E3), w * .08f, h * .46f, w * .84f, h * .30f)
        drawCircle(Color(0xFFFFC107), w * .06f, Offset(w * .72f, h * .54f), colorFilter = cf)
        rect(Color(0xFF3E9B4F), w * .08f, h * .64f, w * .84f, h * .12f)
        rect(Color(0xFF1A73E8), w * .08f, h * .83f, w * .34f, h * .09f)
    }
}

@Composable
private fun ColorSlider(name: String, v: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BoText(name, 13, T.text, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) { BoSlider(v, 0f..80f, onChange = onChange, onDone = onDone) }
        BoText(v.toInt().toString(), 13, T.text, mono = true, modifier = Modifier.width(30.dp))
    }
}

/** Turns white pages into a colour of your choice (live preview), text white, photos hue-corrected. Runs through root / Shizuku. */
@Composable
fun PageCard(mode: Privilege.Mode, prefs: SharedPreferences, showMaster: Boolean = true) {
    val scope = rememberCoroutineScope()
    var r by remember { mutableFloatStateOf(prefs.getFloat("pg_r", 0f)) }
    var g by remember { mutableFloatStateOf(prefs.getFloat("pg_g", 0f)) }
    var b by remember { mutableFloatStateOf(prefs.getFloat("pg_b", 0f)) }
    var txt by remember { mutableFloatStateOf(prefs.getFloat("pg_text", 235f)) }
    var hueSafe by remember { mutableStateOf(prefs.getBoolean("pg_hue", true)) }
    var strength by remember { mutableFloatStateOf(prefs.getFloat("pg_strength", 1f)) }
    var on by remember { mutableStateOf(prefs.getBoolean("pg_on", false)) }
    var msg by remember { mutableStateOf<String?>(null) }
    val bg = (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    val t = txt.toInt().let { (it shl 16) or (it shl 8) or it }
    val cfg = PageDark.Cfg(bg, t, hueSafe, strength)
    fun save() { prefs.edit().putFloat("pg_r", r).putFloat("pg_g", g).putFloat("pg_b", b).putFloat("pg_text", txt).putBoolean("pg_hue", hueSafe).putFloat("pg_strength", strength).apply() }
    fun push(h: Boolean = hueSafe) {
        save()
        if (on) scope.launch {
            val o = withContext(Dispatchers.IO) { PageDark.apply(PageDark.Cfg(bg, t, h, strength)) }
            msg = if (o.code == 0) null else tr("Failed: ", "Başarısız: ") + o.text
        }
    }
    Column {
        BoCard {
            BoText(tr("Preview", "Önizleme"), 16, T.text, bold = true)
            BoText(tr("Left: the page as it is. Right: how it will look.", "Sol: sayfa olduğu gibi. Sağ: nasıl görüneceği."))
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MockPage(null, Modifier.weight(1f))
                MockPage(ColorFilter.colorMatrix(ColorMatrix(PageDark.colorMatrix(cfg))), Modifier.weight(1f))
            }
        }
        BoCard {
            BoText(tr("Colours", "Renkler"), 16, T.text, bold = true)
            BoText(tr("Each colour as it is (left) and as it will be (right).", "Her renk olduğu gibi (sol) ve olacağı gibi (sağ)."))
            SwatchSamples.chunked(2).forEach { pair ->
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { src ->
                        val out = PageDark.mapColor(cfg, src)
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            BoDot(src, 28.dp); BoText("  →  "); BoDot(out, 28.dp); BoText("  #%06X".format(out), 12, T.text, mono = true)
                        }
                    }
                }
            }
        }
        BoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoText(tr("Page colour", "Sayfa rengi"), 16, T.text, bold = true, modifier = Modifier.weight(1f))
                BoDot(bg, 26.dp); BoText("  #%06X".format(bg), 13, T.text, mono = true)
            }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PagePresets.forEach { (n, c) -> PChip(bg == c, n) { r = ((c shr 16) and 255).toFloat(); g = ((c shr 8) and 255).toFloat(); b = (c and 255).toFloat(); push() } }
            }
            ColorSlider(tr("Red", "Kırmızı"), r, { r = it }) { push() }
            ColorSlider(tr("Green", "Yeşil"), g, { g = it }) { push() }
            ColorSlider(tr("Blue", "Mavi"), b, { b = it }) { push() }
            BoText(tr("Text brightness ${(txt / 255f * 100).toInt()}%", "Yazı parlaklığı %${(txt / 255f * 100).toInt()}"), 13, T.text, modifier = Modifier.padding(top = 8.dp))
            BoSlider(txt, 140f..255f, onChange = { txt = it }) { push() }
            BoText(tr("Strength ${(strength * 100).toInt()}%", "Güç %${(strength * 100).toInt()}"), 13, T.text, modifier = Modifier.padding(top = 8.dp))
            BoSlider(strength, 0f..1f, onChange = { strength = it }) { push() }
            BoSwitchRow(tr("Keep colours", "Renkleri koru"), tr("Only light and dark are swapped; colours in photos and ink keep their hue.", "Yalnız açık ve koyu yer değiştirir; fotoğraflardaki ve mürekkepteki renkler tonunu korur."), hueSafe) { hueSafe = it; push(it) }
        }
        if (showMaster) BoCard {
            BoSwitchRow(if (on) tr("Dark pages are ON", "Koyu sayfalar AÇIK") else tr("Dark pages are off", "Koyu sayfalar kapalı"), tr("Covers the whole screen until the phone restarts.", "Telefon yeniden başlayana kadar tüm ekranı kaplar."), on, mode != Privilege.Mode.None, bold = true) { v ->
                on = v; prefs.edit().putBoolean("pg_on", v).apply(); save()
                scope.launch {
                    val o = withContext(Dispatchers.IO) { if (v) PageDark.apply(cfg) else PageDark.clear() }
                    msg = if (o.code == 0) null else tr("Failed: ", "Başarısız: ") + o.text
                }
            }
            if (mode == Privilege.Mode.None) BoText(tr("Needs root or Shizuku first (Settings > Root access).", "Önce root ya da Shizuku gerekir (Ayarlar > Root erişimi)."), 13, WarnAmber)
            msg?.let { BoText(it, 13, BadRed, modifier = Modifier.padding(top = 4.dp)) }
            var fdOn by remember { mutableStateOf(false) }
            BoSwitchRow(tr("Android force dark (manual, all apps)", "Android zorla koyu (elle, tüm uygulamalar)"), "", fdOn, mode != Privilege.Mode.None) { v ->
                fdOn = v
                scope.launch { withContext(Dispatchers.IO) { Privilege.run("setprop debug.hwui.force_dark ${if (v) "true" else "false"}") } }
            }
        }
    }
}
