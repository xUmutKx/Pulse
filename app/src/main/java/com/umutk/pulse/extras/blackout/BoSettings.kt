package com.umutk.pulse.extras.blackout

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- the LSPosed module page ------------------------------------------------------------------------------------------------------------------------

/** LSPosed module: how to switch it on, its settings and whether it is active. */
@Composable
fun ModulePage(mode: Privilege.Mode, sp: SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(sp.getBoolean("lsp_on", true)) }
    var limit by remember { mutableStateOf(sp.getFloat("limit", 56f)) }
    var keep by remember { mutableStateOf(sp.getFloat("keep", 0f)) }
    var tint by remember { mutableStateOf(sp.getInt("tint", 0)) }
    var active by remember { mutableStateOf(Status.isActive()) }
    var last by remember { mutableStateOf(HookReport.newest(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }
    var test by remember { mutableStateOf(sp.getBoolean("hook_test", false)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoCard { BoSwitchRow(tr("Show a test message", "Deneme iletisi göster"), tr("When the module runs in an app, a short message appears inside that app. No logs needed.", "Modül bir uygulamada çalışınca o uygulamanın içinde kısa bir ileti çıkar. Kayıt gerekmez."), test) { v -> test = v; sp.edit().putBoolean("hook_test", v).apply() } }
        BoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoStatus(active, tr("Module is active in ${Status.framework().ifEmpty { "an Xposed framework" }} (${Status.api()})", "Modül ${Status.framework().ifEmpty { "bir Xposed çerçevesinde" }} etkin (${Status.api()})"), tr("Module not detected in Pulse yet", "Modül Pulse içinde henüz algılanmadı"), Modifier.weight(1f))
                BoLink(tr("Refresh", "Yenile")) { active = Status.isActive(); last = HookReport.newest(ctx) }
            }
            BoText(last?.let { tr("Last report from an app: ${(System.currentTimeMillis() - it.at) / 1000} s ago, ${it.api}", "Bir uygulamadan son rapor: ${(System.currentTimeMillis() - it.at) / 1000} sn önce, ${it.api}") } ?: tr("No app has reported yet.", "Henüz hiçbir uygulama rapor vermedi."), modifier = Modifier.padding(top = 4.dp))
            BoText(tr("Dark greys of the ticked apps go pure black.", "İşaretli uygulamaların koyu grileri saf siyah olur."), modifier = Modifier.padding(top = 4.dp))
            BoText(tr("LSPosed > BlackOut (Pulse): switch it on, tick Pulse and the apps, restart them.", "LSPosed > Pulse modülü: açın, Pulse'u ve uygulamaları işaretleyin, onları yeniden başlatın."), 13, T.text, modifier = Modifier.padding(top = 4.dp))
            BoButtons { BoBtn(tr("Open LSPosed / Vector", "LSPosed / Vector'ü aç")) { com.umutk.pulse.features.openLSPosed(ctx) } }
            msg?.let { BoText(it, modifier = Modifier.padding(top = 4.dp)) }
        }
        BoCard {
            // one switch sets up the whole look: turning it on while the grey threshold and tint are still at their plain defaults
            // also picks a threshold that catches AMOLED surfaces well and a blue-black tint, instead of a flat, unconfigured black
            BoSwitchRow(tr("Apply in the ticked apps", "İşaretli uygulamalarda uygula"), "", on, bold = true) { v ->
                on = v; sp.edit().putBoolean("lsp_on", v).apply()
                if (v && limit <= 56f && tint == 0) {
                    limit = 64f; tint = 0x050A18
                    sp.edit().putFloat("limit", limit).putInt("tint", tint).apply()
                }
            }
            BoText(tr("Which greys count as surfaces", "Hangi griler yüzey sayılır"), 13, T.text, modifier = Modifier.padding(top = 6.dp))
            GreySlider(limit, { limit = it }) { sp.edit().putFloat("limit", limit).apply() }
            BoText(if (keep < 1f) tr("Result: pure black", "Sonuç: saf siyah") else tr("Result: ${keep.toInt()}% of the brightness stays", "Sonuç: parlaklığın %${keep.toInt()}'i kalır"), 13, T.text)
            BoSlider(keep, 0f..40f, onChange = { keep = it }) { sp.edit().putFloat("keep", keep).apply() }
            TintPicker(tint) { tint = it; sp.edit().putInt("tint", it).apply() }
            var soft by remember { mutableStateOf(sp.getBoolean("amo_soft", false)) }
            BoSwitchRow(tr("Soft whites", "Yumuşak beyaz"), tr("Pure white becomes light grey. Less glare on AMOLED.", "Saf beyaz açık gri olur. AMOLED'de daha az parlama."), soft) { soft = it; sp.edit().putBoolean("amo_soft", it).apply() }
            GreyPreview(limit, keep, tint, soft)
            var web by remember { mutableStateOf(sp.getBoolean("web_black", true)) }
            BoSwitchRow(tr("Web pages inside apps too", "Uygulama içi web sayfaları da"), tr("Dark grey backgrounds of web content turn black too.", "Web içeriğinin koyu gri arka planları da siyah olur."), web) { web = it; sp.edit().putBoolean("web_black", it).apply() }
            BoText(tr("Changes apply when an app starts. If an app ignores them:", "Değişiklikler uygulama başlarken uygulanır. Bir uygulama onları yok sayarsa:"))
            BoBtn(tr("Fix settings access (root)", "Ayar erişimini düzelt (root)"), mode != Privilege.Mode.None, Modifier.padding(top = 6.dp)) {
                scope.launch {
                    val o = withContext(Dispatchers.IO) { Privilege.run("chmod 755 /data/data/${ctx.packageName}; chmod 755 /data/data/${ctx.packageName}/shared_prefs; chmod 644 /data/data/${ctx.packageName}/shared_prefs/blackout.xml; echo ok") }
                    msg = if (o.text.contains("ok")) tr("Settings are readable by the module now.", "Ayarlar artık modül tarafından okunabilir.") else tr("Failed: ", "Başarısız: ") + o.text.take(100)
                }
            }
        }
        BoCard {
            val fd = sp.getStringSet("fd_apps", emptySet())?.size ?: 0
            val pg = sp.getStringSet("pgmod_apps", emptySet())?.size ?: 0
            BoText(tr("Apps without a dark theme", "Koyu teması olmayan uygulamalar"), 16, T.text, bold = true)
            BoText(tr("Light-only apps: open them in Apps and switch on Force dark.", "Yalnız açık temalı uygulamalar: Uygulamalar'dan açın ve Zorla koyu'yu açın."))
            BoText(tr("Force dark: $fd apps · Only the pages: $pg apps", "Zorla koyu: $fd uygulama · Yalnız sayfalar: $pg uygulama"), 13, T.text, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---- root access and the settings categories -----------------------------------------------------------------------------------------------------

@Composable
fun PrivilegeCard(mode: Privilege.Mode, onRoot: () -> Unit, onCheck: () -> Unit, onShizuku: () -> Unit) {
    BoCard {
        BoStatus(mode != Privilege.Mode.None, tr("Ready: using ${Privilege.detail}", "Hazır: ${Privilege.detail} kullanılıyor"), tr("No root / Shizuku access", "Root / Shizuku erişimi yok"))
        if (mode == Privilege.Mode.None) {
            BoText(tr("Needs root or Shizuku.", "Root ya da Shizuku gerekir."), modifier = Modifier.padding(top = 6.dp))
            if (Privilege.detail.isNotBlank()) BoText(Privilege.detail, 13, WarnAmber, modifier = Modifier.padding(top = 6.dp))
        }
        BoButtons {
            BoBtn(tr("Use root", "Root kullan")) { onRoot() }
            BoBtn(tr("Use Shizuku", "Shizuku kullan")) { onShizuku() }
            BoBtn(tr("Auto", "Otomatik")) { onCheck() }
        }
    }
}

@Composable
fun DataCard(sp: SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) msg = runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(BlackOutData.export(sp).toByteArray()) }; tr("Saved.", "Kaydedildi.") }.getOrElse { tr("Save failed: ", "Kaydedilemedi: ") + it.message }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) msg = runCatching { BlackOutData.import(sp, ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }); tr("Loaded. Reopen the page.", "Yüklendi. Sayfayı yeniden açın.") }.getOrElse { tr("Load failed: ", "Yüklenemedi: ") + it.message }
    }
    val auto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            sp.edit().putString("backup_uri", uri.toString()).apply()
            msg = runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(BlackOutData.export(sp).toByteArray()) }; tr("Auto-backup on: every change is saved to that file.", "Otomatik yedek açık: her değişiklik o dosyaya kaydedilir.") }.getOrElse { tr("Auto-backup failed: ", "Otomatik yedek başarısız: ") + it.message }
        }
    }
    BoCard {
        BoText(tr("Save or load every BlackOut setting and colour rule as one JSON file.", "Her BlackOut ayarını ve renk kuralını tek bir JSON dosyası olarak kaydedin ya da yükleyin."), 14, T.text)
        BoButtons {
            BoBtn(tr("Export settings", "Ayarları dışa aktar")) { save.launch("blackout-settings.json") }
            BoBtn(tr("Import settings", "Ayarları içe aktar")) { load.launch(arrayOf("application/json", "text/*")) }
        }
        BoButtons { BoBtn(tr("Auto-backup file", "Otomatik yedek dosyası")) { auto.launch("blackout-autobackup.json") } }
        if (BlackOutData.standaloneInstalled(ctx)) BoButtons {
            BoBtn(tr("Import from the BlackOut app", "BlackOut uygulamasından al")) { scope.launch { msg = withContext(Dispatchers.IO) { BlackOutData.importFromStandalone(ctx) } } }
        }
        if (msg.isNotEmpty()) BoText(msg, 13, T.text, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Settings > About. */
@Composable
fun AboutPage() {
    val ctx = LocalContext.current
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "about")
    val glow by t.animateFloat(0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(T.accent.copy(alpha = .22f * glow), size.minDimension * .5f)
                drawCircle(T.accent.copy(alpha = .12f * glow), size.minDimension * .36f)
            }
            MoonLogo(Modifier.size(120.dp))
        }
        Text("BlackOut", color = T.accent, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("by UmutK", color = T.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        BoCard {
            listOf("Pulse" to ver, "Android" to android.os.Build.VERSION.RELEASE, tr("Device", "Cihaz") to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}").forEach { (k, v) ->
                Row(Modifier.padding(vertical = 3.dp)) { BoText(k, 14, modifier = Modifier.weight(1f)); BoText(v, 14, T.text) }
            }
        }
        BoBtn("GitHub · xUmutKx/BlackOut", modifier = Modifier.fillMaxWidth()) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xUmutKx/BlackOut")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        PrivacyNote()
    }
}

// ---- the guided setup ----------------------------------------------------------------------------------------------------------------------------

/** What BlackOut needs, read from the real checks each time. */
class SetupState(val module: Boolean, val privilege: Boolean, val watcherNeeded: Boolean, val watcher: Boolean, val recheck: () -> Unit) {
    val moduleOrRoot get() = module || privilege
    val watcherOk get() = !watcherNeeded || watcher
    val complete get() = moduleOrRoot && watcherOk
}

/** Reads the checks again every time the page comes back to the front, so a step done in LSPosed or Settings shows at once. */
@Composable
fun rememberSetup(prefs: SharedPreferences, mode: Privilege.Mode): SetupState {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick.intValue++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return remember(tick.intValue, mode) { SetupState(Status.isActive() || HookReport.newest(ctx) != null, mode != Privilege.Mode.None, watcherNeeded(prefs), AppWatch.enabled(ctx)) { tick.intValue++ } }
}

/** Guided setup: one step at a time, one main button per step, then a final check. */
@Composable
fun SetupFlow(st: SetupState, mode: Privilege.Mode, prefs: SharedPreferences, onMode: (Privilege.Mode) -> Unit, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ok = listOf(st.moduleOrRoot, st.watcherOk, st.privilege)
    val total = 4
    var cur by rememberSaveable { mutableStateOf(ok.indexOf(false).let { if (it < 0) total - 1 else it }) }
    fun turnOnWatcher() {
        when {
            mode != Privilege.Mode.None -> scope.launch { withContext(Dispatchers.IO) { AppWatch.enableWithPower() }; st.recheck() }
            AppWatch.canSelfEnable(ctx) -> { AppWatch.enableSelf(ctx); st.recheck() }
            else -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    fun grantPower() {
        if (Privilege.shizukuRunning()) {
            Privilege.requestShizuku(); prefs.edit().putString("prefer", "shizuku").apply()
            scope.launch { delay(1500); onMode(withContext(Dispatchers.IO) { Privilege.detect("shizuku") }) }
        } else {
            prefs.edit().putString("prefer", "root").apply()
            scope.launch { onMode(withContext(Dispatchers.IO) { Privilege.detect("root") }) }
        }
    }
    val powerLabel = if (Privilege.shizukuRunning()) tr("Use Shizuku", "Shizuku kullan") else tr("Use root", "Root kullan")
    val watcherLabel = if (mode != Privilege.Mode.None || AppWatch.canSelfEnable(ctx)) tr("Turn on", "Aç") else tr("Open accessibility", "Erişilebilirliği aç")

    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoText(tr("Setup · Step ${cur + 1} of $total", "Kurulum · Adım ${cur + 1} / $total"), 14, T.accent, bold = true)
        BoProgress((cur + 1) / total.toFloat())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (cur) {
                0 -> {
                    Heading(tr("Turn BlackOut on in LSPosed", "LSPosed'da BlackOut'u açın"))
                    Body(tr("Open LSPosed or Vector, switch the Pulse module on, and tick Pulse and each app you want darkened in its scope. Then restart those apps. No LSPosed? Use root in step 3.", "LSPosed ya da Vector'ü açın, Pulse modülünü etkinleştirin, kapsamında Pulse'u ve koyulaştırmak istediğiniz her uygulamayı işaretleyin. Sonra o uygulamaları yeniden başlatın. LSPosed yok mu? 3. adımda root kullanın."))
                    BoStatus(ok[0], tr("The module runs, or root is set.", "Modül çalışıyor ya da root hazır."), tr("Not done yet.", "Henüz yapılmadı."))
                }
                1 -> if (!st.watcherNeeded) {
                    Heading(tr("Accessibility is not needed", "Erişilebilirlik gerekmiyor"))
                    Body(tr("None of your switched-on colour features use it. You can continue.", "Açtığınız renk özelliklerinin hiçbiri onu kullanmıyor. Devam edebilirsiniz."))
                } else {
                    Heading(tr("Turn on the accessibility service", "Erişilebilirlik hizmetini açın"))
                    Body(tr("The colour picker and the per-app effects read where text and images are through the accessibility service. It never stores or sends what it sees.", "Renk seçici ve uygulama bazlı etkiler, yazıların ve resimlerin yerini erişilebilirlik hizmetiyle okur. Gördüğünü asla saklamaz ya da göndermez."))
                    BoStatus(ok[1], tr("The accessibility service is on.", "Erişilebilirlik hizmeti açık."), tr("Not on yet.", "Henüz açık değil."))
                }
                2 -> {
                    Heading(tr("Root or Shizuku (optional)", "Root veya Shizuku (isteğe bağlı)"))
                    Body(tr("The Apply buttons for overlays need root or Shizuku. Without them the LSPosed route still works.", "Kaplamalar için Uygula düğmeleri root ya da Shizuku ister. Onlarsız LSPosed yolu yine çalışır."))
                    BoStatus(ok[2], tr("Using ${Privilege.detail}", "${Privilege.detail} kullanılıyor"), tr("Not set. ", "Ayarlı değil. ") + Privilege.detail)
                }
                else -> {
                    Heading(tr("Final check", "Son denetim"))
                    CheckRow(tr("LSPosed module or root", "LSPosed modülü ya da root"), ok[0], tr("Done", "Tamam"), tr("Not done", "Yapılmadı"))
                    CheckRow(tr("Accessibility", "Erişilebilirlik"), ok[1], if (st.watcherNeeded) tr("Done", "Tamam") else tr("Not needed", "Gerekmiyor"), tr("Not on", "Açık değil"))
                    CheckRow(tr("Root or Shizuku", "Root veya Shizuku"), ok[2], tr("Done", "Tamam"), tr("Optional, not set", "İsteğe bağlı, ayarsız"))
                    if (!st.complete) Body(tr("Finish is locked until the required steps are done.", "Gerekli adımlar bitene kadar Bitir kilitli."))
                }
            }
        }
        val last = cur == total - 1
        val stepDone = ok.getOrElse(cur) { true }
        when {
            last -> BigButton(tr("Finish", "Bitir"), st.complete, onFinish)
            stepDone -> BigButton(tr("Next", "İleri")) { cur++ }
            cur == 0 -> BigButton(tr("Open LSPosed / Vector", "LSPosed / Vector'ü aç")) { com.umutk.pulse.features.openLSPosed(ctx) }
            cur == 1 -> BigButton(watcherLabel) { turnOnWatcher() }
            else -> BigButton(powerLabel) { grantPower() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (cur > 0) BoLink(tr("Back", "Geri")) { cur-- } else Box(Modifier.height(1.dp))
            if (!last) BoLink(if (cur == 2) tr("Skip (optional)", "Atla (isteğe bağlı)") else tr("Skip", "Atla")) { cur++ }
        }
    }
}

@Composable private fun Heading(t: String) = Text(t, color = T.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
@Composable private fun Body(t: String) = Text(t, color = T.sub, fontSize = 16.sp)

@Composable
private fun CheckRow(label: String, done: Boolean, yes: String, no: String) {
    BoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = T.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(if (done) yes else no, color = if (done) OkGreen else WarnAmber, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BigButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BoBtn(label, enabled, Modifier.fillMaxWidth(), onClick) }
}
