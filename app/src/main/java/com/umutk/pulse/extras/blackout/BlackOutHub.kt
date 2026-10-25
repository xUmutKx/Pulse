package com.umutk.pulse.extras.blackout

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PNameField
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.features.lsposedIntent
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BLACK = 0xFF000000.toInt()

/** A card: a number (or none), the title, one line of what it does, then the controls. The steps run from the easy ones to the hard ones, top to bottom. */
@Composable
private fun Step(n: Int?, title: String, sub: String, content: @Composable () -> Unit) {
    com.umutk.pulse.PCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n != null) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(T.accent), contentAlignment = Alignment.Center) { Text("$n", color = com.umutk.pulse.onAccent(T.accent), fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = T.sub, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** One line of the setup list: a tick or an empty circle, what it is, and the button that sets it right. */
@Composable
private fun SetupRow(ok: Boolean, title: String, sub: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (ok) T.accent else T.sub.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
            if (ok) Text("✓", color = com.umutk.pulse.onAccent(T.accent), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = T.text, fontSize = 15.sp)
            Text(sub, color = T.sub, fontSize = 12.sp)
        }
        if (!ok && action != null) PButton(action, compact = true, onClick = onAction)
    }
}

/** The app as it is now (grey surface) beside what BlackOut makes of it (black), with the app's own icon and name on both halves. */
@Composable
private fun AppPreview(pkg: String, sp: SharedPreferences) {
    val ctx = LocalContext.current
    val label = remember(pkg) { runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    val icon: ImageBitmap? = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(160, 160).asImageBitmap() }.getOrNull() }
    val before = 0xFF2A2A2A.toInt()
    val after = Grey.fix(before, sp.getFloat("limit", 56f).toInt(), sp.getFloat("keep", 0f) / 100f, 0, sp.getBoolean("amo_soft", false))
    Row(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, T.sub.copy(alpha = .3f), RoundedCornerShape(16.dp))) {
        listOf(before to tr("Now", "Şimdi"), after to tr("BlackOut", "BlackOut")).forEach { (c, cap) ->
            Column(Modifier.weight(1f).fillMaxHeight().background(Color(c or -0x1000000)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (icon != null) Image(icon, null, Modifier.size(48.dp)) else Spacer(Modifier.size(48.dp))
                Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp))
                Text(cap, color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) T.text else T.sub, fontSize = 15.sp)
            if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 12.sp)
        }
        PSwitch(on, onChange, enabled)
    }
}

@Composable
private fun Dot(c: Int, size: Dp = 22.dp) = Box(Modifier.size(size).clip(CircleShape).background(Color(c or -0x1000000)).border(1.dp, Color(0x55FFFFFF), CircleShape))

private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_LONG).show()

/** Turns the accessibility watcher (it draws the colour bar and keeps the screen filter) on: by itself when it may write secure settings, else through root / Shizuku, else the system screen. */
private fun enableWatcher(ctx: Context, then: () -> Unit = {}) {
    when {
        AppWatch.enabled(ctx) -> then()
        AppWatch.canSelfEnable(ctx) && AppWatch.enableSelf(ctx) -> then()
        Privilege.mode != Privilege.Mode.None -> Thread { AppWatch.enableWithPower(); then() }.start()
        else -> { toast(ctx, tr("Turn on Pulse · BlackOut in Accessibility", "Erişilebilirlik'te Pulse · BlackOut'u açın")); ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Shows the floating colour bar (the dropper) for [target] ("*" = the app in front); false when the watcher is not connected yet. */
private fun openBar(ctx: Context, target: String): Boolean {
    val svc = AppWatch.live
    if (svc == null) { enableWatcher(ctx); toast(ctx, tr("The watcher is starting. Press the button again in a moment.", "İzleyici başlıyor. Biraz sonra düğmeye tekrar basın.")); return false }
    svc.openBar(target)
    return true
}

/** BlackOut inside Pulse: turns the greys of your apps black. Setup, the app list, then the advanced switches. */
@Composable
fun BlackOutHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sp = remember { BlackOutData.prefs(ctx).also { Overlays.prefs = it } }
    var page by remember { mutableStateOf("home") }
    var app by remember { mutableStateOf("") }
    BackHandler { if (page == "home") onBack() else page = if (page == "app") "apps" else "home" }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Privilege.detect(sp.getString("prefer", "auto") ?: "auto") } }
    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter).padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", T.text, 22.dp, Modifier.clickable { if (page == "home") onBack() else page = if (page == "app") "apps" else "home" })
            Text(
                when (page) { "apps" -> tr("Apps", "Uygulamalar"); "app" -> app; else -> "BlackOut" },
                color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            com.umutk.pulse.ExtraLookIcon(T.text)
        }
        when (page) {
            "apps" -> AppsPage(sp) { app = it; page = "app" }
            "app" -> AppPage(app, sp)
            else -> HomePage(sp) { page = it }
        }
    }
}

@Composable
private fun HomePage(sp: SharedPreferences, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Privilege.mode) }
    var busy by remember { mutableStateOf(false) }
    var lsp by remember { mutableStateOf(sp.getBoolean("lsp_on", true)) }
    var limit by remember { mutableStateOf(sp.getFloat("limit", 56f)) }
    var keep by remember { mutableStateOf(sp.getFloat("keep", 0f)) }
    var web by remember { mutableStateOf(sp.getBoolean("web_black", true)) }
    var soft by remember { mutableStateOf(sp.getBoolean("amo_soft", false)) }
    var pg by remember { mutableStateOf(sp.getBoolean("pg_on", false)) }
    var strength by remember { mutableStateOf(sp.getFloat("pg_strength", 1f)) }
    var msg by remember { mutableStateOf("") }
    var hookNow by remember { mutableStateOf<HookReport?>(null) }
    LaunchedEffect(Unit) { while (true) { hookNow = HookReport.newest(ctx); delay(3000) } }
    var watcher by remember { mutableStateOf(AppWatch.enabled(ctx)) }
    LaunchedEffect(Unit) { while (true) { watcher = AppWatch.enabled(ctx); delay(2000) } }
    val standalone = remember { BlackOutData.standaloneInstalled(ctx) }
    fun pushPages() { scope.launch(Dispatchers.IO) { if (Privilege.mode != Privilege.Mode.None) { if (sp.getBoolean("pg_on", false)) PageDark.apply(PageDark.fromPrefs(sp)) else PageDark.clear() } } }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) msg = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(BlackOutData.export(sp).toByteArray()) }; tr("Saved.", "Kaydedildi.") }.getOrElse { "Save failed: ${it.message}" }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) msg = runCatching { BlackOutData.import(sp, ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }); lsp = sp.getBoolean("lsp_on", true); tr("Loaded.", "Yüklendi.") }.getOrElse { "Load failed: ${it.message}" }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Turns the grey surfaces of your apps into true black, for AMOLED screens. Go down the steps; the first ones need nothing special.", "Uygulamalarınızın gri yüzeylerini AMOLED ekranlar için gerçek siyaha çevirir. Adımları sırayla izleyin; ilk adımlar özel bir şey istemez."), color = T.sub, fontSize = 13.sp)

        // a stand-alone BlackOut on the phone: its settings come over in one tap
        if (standalone && !sp.getBoolean("imported_standalone", false)) com.umutk.pulse.PCard(Modifier.fillMaxWidth()) {
            Text(tr("The BlackOut app is installed", "BlackOut uygulaması yüklü"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(tr("Take its settings, colour rules and app lists over to Pulse. Root reads them from the app; without root, export them in BlackOut and import the file under Advanced, Back up.", "Ayarlarını, renk kurallarını ve uygulama listelerini Pulse'a alın. Root bunları uygulamadan okur; root yoksa BlackOut'ta dışa aktarıp Gelişmiş > Yedekle bölümünde dosyayı içe aktarın."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            PButton(tr("Import from BlackOut", "BlackOut'tan al"), compact = true) { scope.launch { msg = withContext(Dispatchers.IO) { BlackOutData.importFromStandalone(ctx) }; lsp = sp.getBoolean("lsp_on", true); limit = sp.getFloat("limit", 56f) } }
        }

        // three things to have on, each with its own fix button
        com.umutk.pulse.PCard(Modifier.fillMaxWidth()) {
            Text(tr("Setup", "Kurulum"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val modOk = Status.isActive() || (hookNow != null && System.currentTimeMillis() - hookNow!!.at < 120_000L)
            SetupRow(modOk, tr("Module", "Modül"), tr("LSPosed or Vector: tick the apps in the module's scope", "LSPosed veya Vector: modül kapsamında uygulamaları işaretleyin"), tr("Open", "Aç")) { com.umutk.pulse.features.openLSPosed(ctx) }
            SetupRow(mode != Privilege.Mode.None, tr("Root or Shizuku", "Root veya Shizuku"), tr("For overlays, the screen filter and the system switches", "Kaplamalar, ekran filtresi ve sistem düğmeleri için"), if (Privilege.shizukuRunning()) tr("Allow", "İzin ver") else tr("Check", "Denetle")) {
                if (Privilege.shizukuRunning()) Privilege.requestShizuku()
                scope.launch { busy = true; mode = withContext(Dispatchers.IO) { Privilege.detect(sp.getString("prefer", "auto") ?: "auto") }; busy = false }
            }
            SetupRow(watcher, tr("Watcher", "İzleyici"), tr("For the colour bar and the screen filter", "Renk çubuğu ve ekran filtresi için"), tr("Turn on", "Aç")) { enableWatcher(ctx) }
        }

        Step(1, tr("Pick the apps", "Uygulamaları seçin"), tr("Each app has its own page: force dark, colour rules, overlays", "Her uygulamanın kendi sayfası var: zorla koyu, renk kuralları, kaplamalar")) {
            val ruled = remember(msg, page0) { RecolorRules.apps(sp).size }
            Text(if (ruled == 0) tr("No app has a rule yet", "Henüz kuralı olan uygulama yok") else tr("$ruled apps have colour rules", "$ruled uygulamanın renk kuralı var"), color = T.sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            PButton(tr("Open the app list", "Uygulama listesini aç"), compact = true) { go("apps") }
        }

        var adv by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { adv = !adv }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Advanced", "Gelişmiş"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 22.dp, Modifier.rotate(if (adv) 180f else 0f))
        }
        if (adv) {
        Step(null, tr("How black", "Ne kadar siyah"), tr("Every dark grey up to the limit becomes black in the apps you tick", "Sınıra kadar her koyu gri, seçtiğiniz uygulamalarda siyah olur")) {
            Text(tr("Grey limit: ", "Gri sınırı: ") + limit.toInt(), color = T.text, fontSize = 13.sp)
            PSlider((limit - 20f) / 70f, { limit = 20f + it * 70f }, 0f..1f, onDone = { sp.edit().putFloat("limit", limit).apply() })
            Text(tr("Brightness kept: ", "Korunan parlaklık: ") + keep.toInt() + "%", color = T.text, fontSize = 13.sp)
            PSlider(keep / 60f, { keep = it * 60f }, 0f..1f, onDone = { sp.edit().putFloat("keep", keep).apply() })
            // what the limit does to a strip of greys: above, what an app asks for; below, what it gets
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(0x0A, 0x14, 0x1E, 0x28, 0x32, 0x3C, 0x50, 0x80).forEach { g ->
                    val c = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Dot(c, 24.dp); Spacer(Modifier.height(3.dp)); Dot(Grey.fix(c, limit.toInt(), keep / 100f, 0, soft), 24.dp) }
                }
            }
            SwitchRow(tr("Web pages inside apps", "Uygulama içi web sayfaları"), tr("Dark grey backgrounds of web content turn black too", "Web içeriğinin koyu gri arka planları da siyah olur"), web) { web = it; sp.edit().putBoolean("web_black", it).apply() }
            SwitchRow(tr("Soft whites", "Yumuşak beyaz"), tr("Pure white turns light grey: less glare on AMOLED", "Saf beyaz açık gri olur: AMOLED'de daha az parlama"), soft) { soft = it; sp.edit().putBoolean("amo_soft", it).apply() }
        }

        Step(null, tr("Turn the module on", "Modülü açın"), tr("LSPosed or Vector: Modules, then Pulse, switch on, tick the apps", "LSPosed veya Vector: Modüller, Pulse, aç, uygulamaları işaretle")) {
            val here = Status.isActive()
            val last = hookNow
            Text(
                if (here) tr("Active: the module runs in Pulse (${Status.framework().ifBlank { "framework" }})", "Etkin: modül Pulse içinde çalışıyor (${Status.framework().ifBlank { "çerçeve" }})")
                else tr("Not active in Pulse. In LSPosed tick Pulse in the module's scope and open Pulse again.", "Pulse içinde etkin değil. LSPosed'da modül kapsamında Pulse'u işaretleyip Pulse'u yeniden açın."),
                color = if (here) T.accent else T.sub, fontSize = 13.sp)
            if (last != null) Text(tr("Last report: ${(System.currentTimeMillis() - last.at) / 1000} s ago, greys ${last.greys}", "Son rapor: ${(System.currentTimeMillis() - last.at) / 1000} sn önce, gri ${last.greys}"), color = T.sub, fontSize = 12.sp)
            SwitchRow(tr("Module on", "Modül açık"), tr("Off: no app is changed, whatever LSPosed says", "Kapalı: LSPosed ne derse desin hiçbir uygulama değişmez"), lsp) { lsp = it; sp.edit().putBoolean("lsp_on", it).apply() }
            PButton(tr("Open LSPosed / Vector", "LSPosed / Vector'ü aç"), compact = true) { com.umutk.pulse.features.openLSPosed(ctx) }
        }

        Step(null, tr("The colour bar (dropper)", "Renk çubuğu (damlalık)"), tr("Touch a colour on any screen and say what it should become", "Herhangi bir ekranda bir renge dokunun, neye dönüşeceğini söyleyin")) {
            Text(if (watcher) tr("The watcher is on", "İzleyici açık") else tr("The watcher is off: it draws the bar and reads one pixel", "İzleyici kapalı: çubuğu çizer ve tek bir piksel okur"), color = if (watcher) T.accent else T.sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (!watcher) PButton(tr("Turn the watcher on", "İzleyiciyi aç"), compact = true) { enableWatcher(ctx) }
                PButton(tr("Open the colour bar", "Renk çubuğunu aç"), compact = true) {
                    if (openBar(ctx, "*")) ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            Text(tr("The bar goes over everything. Open an app, drag the ring on a colour, pick what it becomes, save. Android 11 or newer; no root.", "Çubuk her şeyin üstünde durur. Bir uygulama açın, halkayı bir rengin üstüne sürükleyin, dönüşeceği rengi seçin, kaydedin. Android 11 ve üstü; root gerekmez."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Step(null, tr("Root or Shizuku", "Root veya Shizuku"), tr("Needed for the screen filter, overlays and the system switches below", "Aşağıdaki ekran filtresi, kaplamalar ve sistem düğmeleri için gerekir")) {
            Text(when (mode) { Privilege.Mode.Root -> tr("Root works", "Root çalışıyor"); Privilege.Mode.Shizuku -> tr("Shizuku works", "Shizuku çalışıyor"); else -> Privilege.detail.ifBlank { tr("Not checked", "Denetlenmedi") } }.take(200),
                color = if (mode != Privilege.Mode.None) T.accent else T.sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PButton(if (busy) "…" else tr("Check", "Denetle"), compact = true) { scope.launch { busy = true; mode = withContext(Dispatchers.IO) { Privilege.detect(sp.getString("prefer", "auto") ?: "auto") }; busy = false } }
                if (Privilege.shizukuRunning() && mode == Privilege.Mode.None) PButton(tr("Allow Shizuku", "Shizuku'ya izin ver"), compact = true) { Privilege.requestShizuku() }
            }
        }

        Step(null, tr("Dark pages", "Koyu sayfalar"), tr("A filter over the whole screen: white pages turn black, black text turns white", "Tüm ekranda filtre: beyaz sayfalar siyah, siyah yazılar beyaz olur")) {
            SwitchRow(tr("Dark pages", "Koyu sayfalar"), tr("Lasts until a restart; Pulse puts it back at boot", "Yeniden başlatmaya kadar sürer; Pulse açılışta geri koyar"), pg, enabled = mode != Privilege.Mode.None) {
                pg = it; sp.edit().putBoolean("pg_on", it).apply(); pushPages(); if (it) enableWatcher(ctx)
            }
            Text(tr("Page colour", "Sayfa rengi"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(tr("Black", "Siyah") to Triple(0f, 0f, 0f), tr("Navy", "Lacivert") to Triple(11f, 16f, 32f), tr("Dark grey", "Koyu gri") to Triple(18f, 18f, 18f)).forEach { (n, c) ->
                    PChip(sp.getFloat("pg_r", 0f) == c.first && sp.getFloat("pg_b", 0f) == c.third, n) {
                        sp.edit().putFloat("pg_r", c.first).putFloat("pg_g", c.second).putFloat("pg_b", c.third).apply(); pushPages(); msg = ""
                    }
                }
            }
            Text(tr("Strength: ", "Güç: ") + (strength * 100).roundToInt() + "%", color = T.text, fontSize = 13.sp)
            PSlider(strength, { strength = it }, 0f..1f, onDone = { sp.edit().putFloat("pg_strength", strength).apply(); pushPages() })
        }

        Step(null, tr("The whole phone", "Tüm telefon"), tr("Root or Shizuku: system-wide switches", "Root veya Shizuku: sistem geneli düğmeler")) {
            var fd by remember { mutableStateOf(sp.getBoolean("fdsys_on", false)) }
            var dk by remember { mutableStateOf(sp.getBoolean("sysdark_on", false)) }
            var glob by remember { mutableStateOf(sp.getBoolean("glob_pure", false)) }
            val on = mode != Privilege.Mode.None
            SwitchRow(tr("Force dark for every app", "Her uygulama için zorla koyu"), tr("Android's own setting (about #1C1C1C); apps need a restart", "Android'in kendi ayarı (yaklaşık #1C1C1C); uygulamaların yeniden başlaması gerekir"), fd, on) {
                fd = it; sp.edit().putBoolean("fdsys_on", it).apply(); scope.launch(Dispatchers.IO) { Privilege.run("setprop debug.hwui.force_dark $it") }
            }
            SwitchRow(tr("Dark theme of the system", "Sistemin koyu teması"), tr("Same as the quick tile", "Hızlı ayar düğmesiyle aynı"), dk, on) {
                dk = it; sp.edit().putBoolean("sysdark_on", it).apply(); scope.launch(Dispatchers.IO) { Privilege.run("cmd uimode night ${if (it) "yes" else "no"}") }
            }
            SwitchRow(tr("Material You greys black", "Material You grileri siyah"), tr("The dark tones Google apps build their surfaces from, through overlays", "Google uygulamalarının yüzeylerini yaptığı koyu tonlar, kaplamalarla"), glob, on) {
                glob = it; sp.edit().putBoolean("glob_pure", it).apply()
                scope.launch { msg = withContext(Dispatchers.IO) { if (it) Overlays.apply("android", Overlays.globalNames(false).map { n -> n to BLACK }).message else Overlays.remove("android").message } }
            }
        }

        Step(null, tr("Back up", "Yedekle"), tr("Settings and colour rules as one file", "Ayarlar ve renk kuralları tek dosyada")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Export", "Dışa aktar"), compact = true) { save.launch("blackout-settings.json") }
                PButton(tr("Import", "İçe aktar"), compact = true) { load.launch(arrayOf("application/json", "text/*")) }
            }
            if (standalone) PButton(tr("Import from the BlackOut app", "BlackOut uygulamasından al"), compact = true, modifier = Modifier.padding(top = 8.dp)) {
                scope.launch { msg = withContext(Dispatchers.IO) { BlackOutData.importFromStandalone(ctx) }; lsp = sp.getBoolean("lsp_on", true); limit = sp.getFloat("limit", 56f) }
            }
        }
        }
        if (msg.isNotEmpty()) Text(msg, color = T.sub, fontSize = 12.sp)
        Text(tr("No internet permission. The watcher learns which app is in front; the colour bar reads one pixel when you tap Save. Nothing is stored or sent.", "İnternet izni yok. İzleyici hangi uygulamanın önde olduğunu öğrenir; renk çubuğu Kaydet'e basınca tek piksel okur. Hiçbir şey saklanmaz ya da gönderilmez."), color = T.sub, fontSize = 11.sp)
        Spacer(Modifier.height(40.dp))
    }
}

private const val page0 = 0

private class AppRow(val pkg: String, val label: String, val first: Boolean)

@Composable
private fun AppsPage(sp: SharedPreferences, open: (String) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val rows by androidx.compose.runtime.produceState(emptyList<AppRow>()) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val ruled = RecolorRules.apps(sp).toSet()
            pm.getInstalledApplications(0).filter { pm.getLaunchIntentForPackage(it.packageName) != null || it.packageName in ruled }.filter { it.packageName != ctx.packageName }
                .map { AppRow(it.packageName, it.loadLabel(pm).toString(), it.packageName.startsWith("com.google.") || it.packageName.startsWith("com.samsung.") || it.packageName.startsWith("com.sec.")) }
                .sortedWith(compareBy<AppRow> { !it.first }.thenBy { it.label.lowercase() })
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        PNameField(q, { q = it }, tr("Search", "Ara"), Modifier.fillMaxWidth())
        Text(tr("Google and Samsung apps first", "Önce Google ve Samsung uygulamaları"), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
        val shown = rows.filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.pkg }) { a ->
                val icon: ImageBitmap? = remember(a.pkg) { runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(72, 72).asImageBitmap() }.getOrNull() }
                val rules = RecolorRules.get(sp, a.pkg).size
                val tags = listOfNotNull(
                    if (a.pkg in (sp.getStringSet("fd_apps", emptySet()) ?: emptySet())) tr("force dark", "zorla koyu") else null,
                    if (rules > 0) tr("$rules rules", "$rules kural") else null,
                ).joinToString(" · ")
                Row(Modifier.fillMaxWidth().clickable { open(a.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(a.label, color = T.text, fontSize = 15.sp, maxLines = 1)
                        Text(if (tags.isEmpty()) a.pkg else "${a.pkg} · $tags", color = T.sub, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One app, from the easy settings to the hard ones. */
@Composable
private fun AppPage(pkg: String, sp: SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = Privilege.mode
    fun set(key: String) = sp.getStringSet(key, emptySet()) ?: emptySet()
    var off by remember(pkg) { mutableStateOf(pkg in set("lsp_off")) }
    var fd by remember(pkg) { mutableStateOf(pkg in set("fd_apps")) }
    var smart by remember(pkg) { mutableStateOf(pkg in set("smart_apps")) }
    var pmod by remember(pkg) { mutableStateOf(pkg in set("pgmod_apps")) }
    var filter by remember(pkg) { mutableStateOf(pkg in set("pages_apps")) }
    var inv by remember(pkg) { mutableStateOf(pkg in set("inv_apps")) }
    var rules by remember(pkg) { mutableStateOf(RecolorRules.get(sp, pkg)) }
    var rulesOn by remember(pkg) { mutableStateOf(RecolorRules.enabled(sp, pkg)) }
    var report by remember(pkg) { mutableStateOf(HookReport.read(ctx, pkg)) }
    var msg by remember(pkg) { mutableStateOf("") }
    var busy by remember(pkg) { mutableStateOf(false) }
    val kind by androidx.compose.runtime.produceState<AppKind?>(null, pkg) { value = withContext(Dispatchers.IO) { AppKindScan.detect(ctx, pkg) } }
    LaunchedEffect(pkg) { while (true) { report = HookReport.read(ctx, pkg); rules = RecolorRules.get(sp, pkg); delay(3000) } }
    fun toggle(key: String, on: Boolean) { sp.edit().putStringSet(key, if (on) set(key) + pkg else set(key) - pkg).apply() }
    fun restart() { scope.launch { withContext(Dispatchers.IO) { Overlays.forceStop(pkg) }; msg = tr("Stopped. Open the app again and wait a few seconds.", "Durduruldu. Uygulamayı yeniden açıp birkaç saniye bekleyin.") } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppPreview(pkg, sp)
        Text(pkg, color = T.sub, fontSize = 12.sp)
        kind?.let { k ->
            com.umutk.pulse.PCard(Modifier.fillMaxWidth()) {
                Text(k.title, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(k.advice, color = T.sub, fontSize = 12.sp)
            }
        }
        val fresh = report != null && System.currentTimeMillis() - report!!.at < 120_000L
        Step(1, tr("Greys to black", "Griler siyaha"), tr("Nothing to set: tick this app in the module's scope in LSPosed", "Ayar yok: LSPosed'da bu uygulamayı modül kapsamında işaretleyin")) {
            Text(if (report == null) tr("No report yet: the module is not running in this app (tick it in LSPosed, then restart the app).", "Henüz rapor yok: modül bu uygulamada çalışmıyor (LSPosed'da işaretleyin, sonra uygulamayı yeniden başlatın).")
                else if (fresh) tr("Active, reporting now · greys changed ${report!!.greys}", "Etkin, şimdi rapor veriyor · değişen gri ${report!!.greys}") else tr("Was active, not running now", "Etkindi, şu an çalışmıyor"),
                color = if (fresh) T.accent else T.sub, fontSize = 13.sp)
            SwitchRow(tr("Off for this app", "Bu uygulama için kapalı"), tr("Keep it in LSPosed but let it be", "LSPosed'da kalsın ama dokunulmasın"), off) { off = it; toggle("lsp_off", it) }
        }
        Step(2, tr("Force dark", "Zorla koyu"), tr("For light-only apps: Android's dark engine plus a pure-black repaint", "Yalnız açık temalı uygulamalar için: Android'in koyu motoru ve saf siyah yeniden boyama")) {
            SwitchRow(tr("Make this app dark", "Bu uygulamayı koyu yap"), tr("Takes effect when the app starts again", "Uygulama yeniden başlayınca etkili olur"), fd && smart) { fd = it; smart = it; toggle("fd_apps", it); toggle("smart_apps", it) }
            if (report != null) Text(tr("forced ${report!!.forced} · fills ${report!!.fills} · texts ${report!!.texts}", "zorlanan ${report!!.forced} · dolgu ${report!!.fills} · yazı ${report!!.texts}"), color = T.sub, fontSize = 12.sp)
        }
        var more by remember(pkg) { mutableStateOf(rules.isNotEmpty() || pkg in set("pgmod_apps") || pkg in set("pages_apps") || pkg in set("inv_apps")) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { more = !more }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("More options", "Daha fazla seçenek"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 22.dp, Modifier.rotate(if (more) 180f else 0f))
        }
        if (more) {
        Step(3, tr("Colour rules", "Renk kuralları"), tr("One colour at a time: where the dropper or a code says this colour, draw that one", "Her seferinde bir renk: damlalık ya da bir kod \"şu renk\" derse, onu şu renkle çiz")) {
            SwitchRow(tr("Rules on", "Kurallar açık"), tr("Off turns every colour change of this app off", "Kapalı olunca bu uygulamanın tüm renk değişiklikleri kapanır"), rulesOn) { rulesOn = it; sp.edit().putBoolean("rcon_$pkg", it).apply() }
            rules.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(r.from); Text(" → ", color = T.sub); Dot(r.to)
                    Text("  #%06X → #%06X · ±%d%%".format(r.from and 0xFFFFFF, r.to and 0xFFFFFF, r.tol) + (if (r.strength < 100) " · ${r.strength}%" else ""), color = T.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    PIcon("close", T.sub, 18.dp, Modifier.clickable { RecolorRules.put(sp, pkg, rules - r); rules = RecolorRules.get(sp, pkg) })
                }
            }
            var code by remember(pkg) { mutableStateOf("") }
            var to by remember(pkg) { mutableStateOf(0x000000) }
            var tol by remember(pkg) { mutableStateOf(12) }
            val hexOk = code.length == 6
            PNameField(code, { code = it.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(6) }, tr("From colour, 6 digits (RRGGBB)", "Kaynak renk, 6 hane (RRGGBB)"), Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("becomes", "olsun"), color = T.sub, fontSize = 12.sp)
                listOf(0x000000, 0x121212, 0x0B1020, 0xFFFFFF).forEach { c -> Box(Modifier.clip(CircleShape).border(if (to == c) 2.dp else 0.dp, T.accent, CircleShape).clickable { to = c }.padding(3.dp)) { Dot(c, 26.dp) } }
                listOf(6, 12, 20, 30).forEach { t -> PChip(tol == t, "±$t%") { tol = t } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PButton(tr("Add", "Ekle"), compact = true) { if (hexOk) { RecolorRules.add(sp, pkg, code.toInt(16) or -0x1000000, to or -0x1000000, tol); rules = RecolorRules.get(sp, pkg); code = "" } }
                PButton(tr("White → black", "Beyaz → siyah"), compact = true) { RecolorRules.add(sp, pkg, 0xFFFFFFFF.toInt(), BLACK, 8); rules = RecolorRules.get(sp, pkg) }
                PButton(tr("Dropper", "Damlalık"), compact = true) { if (openBar(ctx, pkg)) ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            Text(tr("The rules work inside the module, so the app has to be restarted after a change.", "Kurallar modülün içinde çalışır; değişiklikten sonra uygulama yeniden başlatılmalı."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Step(4, tr("Only the pages", "Yalnız sayfalar"), tr("Notes and PDF readers: big light pictures and white areas swap, toolbars stay", "Not ve PDF okuyucular: büyük açık resimler ve beyaz alanlar tersine döner, araç çubukları kalır")) {
            SwitchRow(tr("Dark pages inside this app", "Bu uygulamada koyu sayfalar"), tr("Uses the page colour set on the home page (Advanced)", "Ana sayfadaki (Gelişmiş) sayfa rengini kullanır"), pmod) { pmod = it; toggle("pgmod_apps", it) }
        }
        Step(5, tr("Overlays", "Kaplamalar"), tr("Root or Shizuku: Android paints this app's dark greys black from outside", "Root veya Shizuku: Android bu uygulamanın koyu grilerini dışarıdan siyaha boyar")) {
            val refused = Overlays.refused(pkg)
            if (refused) Text(tr("This app does not allow overlays. Use the module and colour rules instead.", "Bu uygulama kaplamaya izin vermiyor. Modülü ve renk kurallarını kullanın."), color = T.sub, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (busy) "…" else tr("Black out its greys", "Grilerini siyah yap"), compact = true) {
                    if (mode == Privilege.Mode.None || refused || busy) { msg = tr("Needs root or Shizuku and an app that allows overlays", "Root veya Shizuku ve kaplamaya izin veren bir uygulama gerekir"); return@PButton }
                    scope.launch {
                        busy = true
                        msg = withContext(Dispatchers.IO) {
                            val info = ctx.packageManager.getApplicationInfo(pkg, 0)
                            val res = Arsc.scan(info.sourceDir, sp.getFloat("limit", 56f).toInt())
                            val items = (res.candidates.map { it.name } + Overlays.overrides(sp, pkg).keys).distinct().map { n -> n to (Overlays.overrides(sp, pkg)[n] ?: BLACK) }
                            if (res.namesStripped) "The colour names of this app are hidden; use the module instead." else Overlays.apply(pkg, items).message
                        }
                        busy = false
                    }
                }
                PButton(tr("Remove", "Kaldır"), compact = true) { scope.launch { msg = withContext(Dispatchers.IO) { Overlays.remove(pkg).message } } }
            }
        }
        Step(6, tr("Screen filter while it is open", "Açıkken ekran filtresi"), tr("Root or Shizuku and the watcher: a filter over the screen only while this app is in front", "Root veya Shizuku ve izleyici: yalnız bu uygulama öndeyken ekranda filtre")) {
            SwitchRow(tr("Dark pages while open", "Açıkken koyu sayfalar"), "", filter, enabled = mode != Privilege.Mode.None) { filter = it; toggle("pages_apps", it); if (it) enableWatcher(ctx) }
            SwitchRow(tr("Invert colours while open", "Açıkken renkleri ters çevir"), tr("Android's own inversion; photos turn negative too", "Android'in kendi ters çevirmesi; fotoğraflar da negatif olur"), inv, enabled = mode != Privilege.Mode.None) { inv = it; toggle("inv_apps", it); if (it) enableWatcher(ctx) }
        }
        }
        PButton(tr("Restart the app", "Uygulamayı yeniden başlat"), compact = true) { if (mode == Privilege.Mode.None) msg = tr("Close it from the recents screen and open it again", "Son uygulamalardan kapatıp yeniden açın") else restart() }
        if (msg.isNotEmpty()) Text(msg, color = T.sub, fontSize = 12.sp)
        Spacer(Modifier.height(40.dp))
    }
}
