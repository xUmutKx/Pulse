package com.umutk.pulse.extras.blackout

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PNameField
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BlackOut inside Pulse, the same pages as the BlackOut app (GPL-3.0): Home (status, the four ways, setup), Apps (every app and its own page), Colours (the dropper and
 * the colour rules), Settings (grey limit, root access, help, data, about). Four tabs along the bottom.
 */
@Composable
fun BlackOutHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { BlackOutData.prefs(ctx).also { Overlays.prefs = it } }
    var mode by remember { mutableStateOf(Privilege.mode) }
    var detected by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var limit by remember { mutableFloatStateOf(prefs.getFloat("limit", 56f)) }
    var tint by remember { mutableStateOf(prefs.getInt("tint", 0)) }
    var keep by remember { mutableFloatStateOf(prefs.getFloat("keep", 0f)) }
    var apps by remember { mutableStateOf<List<AppRow>>(emptyList()) }
    var open by remember { mutableStateOf<AppRow?>(null) }
    val results = remember { mutableStateMapOf<String, ScanResult>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    var page by rememberSaveable { mutableStateOf("home") }
    var cat by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        mode = withContext(Dispatchers.IO) { Privilege.detect(prefs.getString("prefer", "auto") ?: "auto") }
        detected = true
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val main = pm.getInstalledApplications(0).filter { it.packageName != ctx.packageName }
                .map { AppRow(it.packageName, pm.getApplicationLabel(it).toString(), it.sourceDir, pm.getLaunchIntentForPackage(it.packageName) != null) }
            // Secure Folder and other profiles: PackageManager sees only this profile, so with root / Shizuku their packages are read from the shell
            val seen = main.map { it.pkg }.toSet()
            val others = runCatching {
                Privilege.run("pm list users").text.lines().mapNotNull { Regex("UserInfo\\{(\\d+):").find(it)?.groupValues?.get(1)?.toIntOrNull() }.filter { it != 0 }
                    .flatMap { u -> Privilege.run("pm list packages --user $u").text.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() && it !in seen }.map { AppRow(it, "$it (profile $u)", "", false) } }
            }.getOrDefault(emptyList())
            (main + others).sortedBy { it.label.lowercase() }
        }
    }

    BackHandler(open != null || page != "home" || cat.isNotEmpty()) {
        if (open != null) open = null else if (page == "settings" && cat.isNotEmpty()) cat = "" else page = if (page in setOf("pages", "module", "sysfd", "global", "notes", "setup")) "home" else "home"
    }
    fun back() { if (open != null) open = null else if (page == "settings" && cat.isNotEmpty()) cat = "" else if (page == "home") onBack() else page = "home" }

    val setup = rememberSetup(prefs, mode)
    val a = open

    val title = when {
        a != null -> a.label
        page == "colors" -> tr("Colours", "Renkler")
        page == "module" -> tr("LSPosed module", "LSPosed modülü")
        page == "pages" -> tr("Dark pages", "Koyu sayfalar")
        page == "notes" -> "Samsung Notes"
        page == "sysfd" -> tr("Force dark", "Zorla koyu")
        page == "global" -> tr("All Material You apps", "Tüm Material You uygulamaları")
        page == "apps" -> tr("Apps", "Uygulamalar")
        page == "setup" -> tr("Setup", "Kurulum")
        page == "settings" -> when (cat) { "surface" -> tr("Colours", "Renkler"); "access" -> tr("Root access", "Root erişimi"); "diag" -> tr("Help", "Yardım"); "about" -> tr("About", "Hakkında"); "data" -> tr("Data", "Veri"); else -> tr("Settings", "Ayarlar") }
        else -> "BlackOut"
    }

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter).padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", T.text, 22.dp, Modifier.clickable { back() })
            if (a != null) { Spacer(Modifier.width(10.dp)); AppIcon(a.pkg, 30) }
            Text(title, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
            com.umutk.pulse.ExtraLookIcon(T.text)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (!detected) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { com.umutk.pulse.PLoader() }; return@Box }
            if (a != null) {
                AppDetail(a, results[a.pkg], errors[a.pkg], limit.toInt(), keep / 100f, mode, onScan = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { Arsc.scan(a.sourceDir, limit.toInt()) } }
                        r.onSuccess { results[a.pkg] = it; errors.remove(a.pkg) }.onFailure { errors[a.pkg] = it.message ?: it.javaClass.simpleName }
                    }
                })
                return@Box
            }
            when (page) {
                "colors" -> Column(Modifier.verticalScroll(rememberScrollState())) { ColorsPage(apps, prefs) }
                "module" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp)) { ModulePage(mode, prefs) }
                "pages" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp)) { PageCard(mode, prefs) }
                "notes" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PagesModCard("com.samsung.android.app.notes", mode)
                    EditorAppCard("com.samsung.android.app.notes", mode)
                    PageCard(mode, prefs, showMaster = false)
                    PagesAppCard("com.samsung.android.app.notes", mode)
                    InvertAppCard("com.samsung.android.app.notes", mode)
                }
                "sysfd" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SystemForceDarkCard(mode, prefs); SystemDarkThemeCard(mode, prefs); DimBlackCard(mode, prefs); InvertScreenCard(mode, prefs)
                }
                "global" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp)) { GlobalCard(mode) }
                "setup" -> SetupFlow(setup, mode, prefs, onMode = { mode = it }, onFinish = { prefs.edit().putBoolean("setup_seen", true).apply(); page = "home" })
                "apps" -> AppsList(apps, group, { group = it }, query, { query = it }, results) { open = it }
                "settings" -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (cat) {
                        "surface" -> BoCard {
                            BoText(tr("Which greys count as surfaces", "Hangi griler yüzey sayılır"), 14, T.text)
                            GreySlider(limit, { limit = it }) { prefs.edit().putFloat("limit", limit).apply() }
                            BoText(if (keep < 1f) tr("Result: pure black (#000000)", "Sonuç: saf siyah (#000000)") else tr("Result: ${keep.toInt()}% of the original brightness stays", "Sonuç: özgün parlaklığın %${keep.toInt()}'i kalır"), 14, T.text, modifier = Modifier.padding(top = 6.dp))
                            BoSlider(keep, 0f..40f, onChange = { keep = it }) { prefs.edit().putFloat("keep", keep).apply() }
                            TintPicker(tint) { tint = it; prefs.edit().putInt("tint", it).apply() }
                            GreyPreview(limit, keep, tint, prefs.getBoolean("amo_soft", false))
                            BoText(tr("Higher limit = also darkens lighter greys (cards, sheets). Re-scan an app after changing it.", "Yüksek sınır = daha açık grileri de (kartlar, sayfalar) koyulaştırır. Değiştirdikten sonra uygulamayı yeniden tarayın."))
                        }
                        "access" -> PrivilegeCard(mode,
                            onRoot = { prefs.edit().putString("prefer", "root").apply(); scope.launch { mode = withContext(Dispatchers.IO) { Privilege.detect("root") } } },
                            onCheck = { prefs.edit().putString("prefer", "auto").apply(); scope.launch { mode = withContext(Dispatchers.IO) { Privilege.detect() } } },
                            onShizuku = { Privilege.requestShizuku(); prefs.edit().putString("prefer", "shizuku").apply(); scope.launch { delay(1500); mode = withContext(Dispatchers.IO) { Privilege.detect("shizuku") } } })
                        "diag" -> {
                            var text by remember { mutableStateOf("") }
                            var busy by remember { mutableStateOf(false) }
                            BoText(tr("Shows what this phone answers to the overlay commands. Send it along when something does not work.", "Bu telefonun kaplama komutlarına ne yanıt verdiğini gösterir. Bir şey çalışmayınca birlikte gönderin."))
                            BoBtn(tr("Run diagnostics", "Tanılamayı çalıştır"), mode != Privilege.Mode.None && !busy) { busy = true; scope.launch { text = withContext(Dispatchers.IO) { Overlays.diagnose() }; busy = false } }
                            if (mode == Privilege.Mode.None) BoText(tr("Needs root or Shizuku first (Settings > Root access).", "Önce root ya da Shizuku gerekir (Ayarlar > Root erişimi)."), 13, WarnAmber)
                            if (text.isNotBlank()) BoCard { BoText(text, 13, T.text, mono = true) }
                        }
                        "data" -> DataCard(prefs)
                        "about" -> AboutPage()
                        else -> {
                            BoNavRow("tune", tr("Colours", "Renkler"), tr("Grey limit ${limit.toInt()}, keep ${keep.toInt()}%", "Gri sınırı ${limit.toInt()}, koru %${keep.toInt()}")) { cat = "surface" }
                            BoNavRow("lock", tr("Root access", "Root erişimi"), if (mode == Privilege.Mode.None) tr("Not available", "Yok") else tr("Using ${Privilege.detail}", "${Privilege.detail} kullanılıyor")) { cat = "access" }
                            BoNavRow("info", tr("Help", "Yardım"), tr("See why something does not work", "Bir şeyin neden çalışmadığını görün")) { cat = "diag" }
                            BoNavRow("save", tr("Data", "Veri"), tr("Export or import every setting", "Her ayarı dışa ya da içe aktarın")) { cat = "data" }
                            BoNavRow("info", tr("About", "Hakkında"), "BlackOut") { cat = "about" }
                        }
                    }
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MoonLogo(Modifier.size(52.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("BlackOut", color = T.accent, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.clip(RoundedCornerShape(20.dp)).clickable { page = "settings"; cat = "access" }.background(T.panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(if (mode != Privilege.Mode.None) OkGreen else BadRed))
                        Spacer(Modifier.width(8.dp))
                        Text(if (mode == Privilege.Mode.None) tr("No root or Shizuku", "Root ya da Shizuku yok") else tr("Ready · ${Privilege.detail}", "Hazır · ${Privilege.detail}"), color = T.text, fontSize = 14.sp)
                    }
                    // a stand-alone BlackOut on the phone: its settings come over in one tap
                    if (BlackOutData.standaloneInstalled(ctx) && !prefs.getBoolean("imported_standalone", false)) BoCard {
                        BoText(tr("The BlackOut app is installed", "BlackOut uygulaması yüklü"), 15, T.text, bold = true)
                        BoText(tr("Take its settings, colour rules and app lists over. Root reads them from the app; without root, export them in BlackOut and import the file under Settings > Data.", "Ayarlarını, renk kurallarını ve uygulama listelerini alın. Root bunları uygulamadan okur; root yoksa BlackOut'ta dışa aktarıp Ayarlar > Veri'den dosyayı içe aktarın."), modifier = Modifier.padding(vertical = 6.dp))
                        BoBtn(tr("Import from BlackOut", "BlackOut'tan al")) { scope.launch { withContext(Dispatchers.IO) { BlackOutData.importFromStandalone(ctx) }; limit = prefs.getFloat("limit", 56f) } }
                    }
                    if (!setup.complete) BoNavRow("check", tr("Finish the setup", "Kurulumu bitirin"), tr("A few steps, one at a time", "Birkaç adım, birer birer"), big = true, badge = tr("TODO", "YAPILACAK")) { page = "setup" }
                    WatcherReminder(prefs, mode)
                    BoNavRow("view", tr("Dark pages", "Koyu sayfalar"), tr("White pages", "Beyaz sayfalar"), big = true, badge = if (prefs.getBoolean("pg_on", false)) "ON" else "OFF") { page = "pages" }
                    BoNavRow("services", tr("LSPosed module", "LSPosed modülü"), tr("Black apps", "Siyah uygulamalar"), big = true, badge = if (Status.isActive()) "ON" else null) { page = "module" }
                    BoNavRow("tune", tr("Force dark", "Zorla koyu"), tr("Light apps", "Açık uygulamalar"), big = true, badge = if (prefs.getBoolean("fdsys_on", false)) "ON" else "OFF") { page = "sysfd" }
                    BoNavRow("palette", tr("All Material You apps", "Tüm Material You uygulamaları"), tr("The system's own greys, at once", "Sistemin kendi grileri, hepsi birden"), big = true) { page = "global" }
                    BoNavRow("apps", tr("Apps", "Uygulamalar"), "${apps.count { it.launch }}", big = true) { page = "apps" }
                    if (apps.any { it.pkg == "com.samsung.android.app.notes" }) BoNavRow("clipboard", "Samsung Notes", tr("Dark pages and dark inside a note", "Koyu sayfalar ve notun içinde koyu"), big = true) { page = "notes" }
                    BoCard {
                        BoText(tr("LSPosed: turn the Pulse module on, tick Pulse and each app in its scope, then restart that app.", "LSPosed: Pulse modülünü açın, kapsamında Pulse'u ve her uygulamayı işaretleyin, sonra o uygulamayı yeniden başlatın."), 13, T.text)
                    }
                    PrivacyNote()
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        // the four tabs
        if (a == null && page != "setup") Row(Modifier.fillMaxWidth().background(T.panel).padding(vertical = 6.dp)) {
            listOf(Triple("home", tr("Home", "Ana sayfa"), "home"), Triple("apps", tr("Apps", "Uygulamalar"), "apps"), Triple("colors", tr("Colours", "Renkler"), "palette"), Triple("settings", tr("Settings", "Ayarlar"), "settings")).forEach { (k, n, ic) ->
                val on = page == k || (k == "home" && page !in setOf("apps", "colors", "settings"))
                Column(Modifier.weight(1f).clickable { page = k; cat = "" }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PIcon(ic, if (on) T.accent else T.sub, 22.dp)
                    Text(n, color = if (on) T.accent else T.sub, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}

/** Every app you can open, plus the Google and Samsung system ones; Google first, then Samsung, then the rest. */
@Composable
private fun AppsList(apps: List<AppRow>, group: String, onGroup: (String) -> Unit, query: String, onQuery: (String) -> Unit, results: Map<String, ScanResult>, onOpen: (AppRow) -> Unit) {
    val shown = remember(apps, group, query) {
        apps.filter { (it.launch || googleApp(it.pkg) || samsungApp(it.pkg)) && (group == "all" || (group == "google" && googleApp(it.pkg)) || (group == "samsung" && samsungApp(it.pkg))) &&
            (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)) }
            .sortedWith(compareBy<AppRow>({ if (googleApp(it.pkg)) 0 else if (samsungApp(it.pkg)) 1 else 2 }, { it.label.lowercase() }))
    }
    val ctx = LocalContext.current
    val sp = remember { BlackOutData.prefs(ctx) }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        PNameField(query, onQuery, tr("Search apps", "Uygulama ara"), Modifier.fillMaxWidth())
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to tr("All apps", "Tüm uygulamalar"), "google" to "Google", "samsung" to "Samsung").forEach { (k, n) -> PChip(group == k, n) { onGroup(k) } }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { app ->
                val r = results[app.pkg]
                val rules = RecolorRules.get(sp, app.pkg).size
                val tags = listOfNotNull(
                    if (app.pkg in (sp.getStringSet("fd_apps", emptySet()) ?: emptySet())) tr("force dark", "zorla koyu") else null,
                    if (rules > 0) tr("$rules rules", "$rules kural") else null,
                    when { r == null -> null; r.namesStripped -> tr("names stripped", "adlar silinmiş"); else -> tr("${r.candidates.size} greys", "${r.candidates.size} gri") },
                ).joinToString(" · ")
                Row(Modifier.fillMaxWidth().clickable { onOpen(app) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(app.label, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BoText(if (tags.isEmpty()) app.pkg else "${app.pkg} · $tags", 11, maxLines = 1)
                    }
                    PIcon("chevron", T.sub, 20.dp, Modifier.rotate(-90f))
                }
            }
            if (shown.isEmpty()) item { BoText(tr("No matching apps.", "Eşleşen uygulama yok."), 14) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
