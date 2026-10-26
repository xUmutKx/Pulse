package com.umutk.pulse.extras.blackout

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.PChip
import com.umutk.pulse.PCheck
import com.umutk.pulse.PIcon
import com.umutk.pulse.PNameField
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An installed app as the lists show it. */
class AppRow(val pkg: String, val label: String, val sourceDir: String, val launch: Boolean = true)

/** Colours that should stay as they are: outlines, dividers, shadows, scrims, ripples, text. */
private val KEEP = Regex("outline|divider|stroke|border|scrim|shadow|ripple|text|icon|ink|hint|disabled|inverse|light|on_|_on", RegexOption.IGNORE_CASE)

fun samsungApp(pkg: String) = pkg.startsWith("com.samsung.") || pkg.startsWith("com.sec.android") || pkg.startsWith("com.sec.")
fun googleApp(pkg: String) = pkg.startsWith("com.google.") || pkg == "com.android.vending" || pkg == "com.android.chrome"

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val ctx = LocalContext.current
    val bmp: ImageBitmap? = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, Modifier.size(size.dp)) else Box(Modifier.size(size.dp).clip(CircleShape).background(T.panel))
}

/** Shows the floating bar for [target] ("*" = the app in front). False when accessibility is off, so the user is told instead of nothing happening. */
fun openColourBar(ctx: Context, target: String): Boolean {
    val svc = AppWatch.live
    if (svc == null) {
        Toast.makeText(ctx, tr("Turn on BlackOut in Accessibility first", "Önce Erişilebilirlik'te BlackOut'u açın"), Toast.LENGTH_LONG).show()
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return false
    }
    svc.openBar(target)
    return true
}

// ---- colour rules ---------------------------------------------------------------------------------------------------------------------------------

/** Colour rules of one app: what each picked colour turns into, how strongly, and a switch that turns all of the app's colour changes off. */
@Composable
fun RecolorCard(pkg: String, sp: SharedPreferences) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rules by remember(pkg) { mutableStateOf(RecolorRules.get(sp, pkg)) }
    DisposableEffect(pkg) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k == "rc_$pkg") rules = RecolorRules.get(sp, pkg) }
        sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }
    var on by remember(pkg) { mutableStateOf(RecolorRules.enabled(sp, pkg)) }
    var code by remember { mutableStateOf("") }
    var to by remember { mutableStateOf(0x000000) }
    var strength by remember { mutableFloatStateOf(100f) }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val refused = Overlays.refused(pkg)
    val src = if (code.length == 6) code.toInt(16) or -0x1000000 else null
    val pct = strength.toInt()
    val blended = RecolorRules.mix(src ?: 0xFFFFFFFF.toInt(), to or -0x1000000, pct)

    fun setOn(v: Boolean) {
        on = v; sp.edit().putBoolean("rcon_$pkg", v).apply()
        if (Privilege.mode != Privilege.Mode.None && !refused) scope.launch { withContext(Dispatchers.IO) { Overlays.setEnabled(pkg, v) } }
    }
    fun applyNow() {
        busy = true; msg = ""
        scope.launch {
            val (ok, text) = withContext(Dispatchers.IO) {
                val cands = runCatching { Arsc.scan(ctx.packageManager.getApplicationInfo(pkg, 0).sourceDir, all = true).candidates }.getOrDefault(emptyList())
                val items = cands.filter { !it.name.contains("obfuscated", true) }.mapNotNull { c -> rules.firstOrNull { RecolorRules.matches(c.color, it) }?.let { c.name to RecolorRules.mix(c.color, it.to, it.strength) } }
                if (items.isEmpty()) return@withContext false to "No colour of this app matches the rules."
                val r = Overlays.apply(pkg, items)
                if (r.ok) { items.forEach { (n, col) -> Overlays.saveOverride(sp, pkg, n, col) }; Overlays.forceStop(pkg) }
                r.ok to r.message
            }
            msg = if (ok) "$text " + tr("The app was restarted.", "Uygulama yeniden başlatıldı.") else text
            busy = false
        }
    }

    BoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Colours (${rules.size})", "Renkler (${rules.size})"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                PIcon("chevron", T.sub, 22.dp, Modifier.rotate(if (open) 180f else 0f))
            }
            Box(Modifier.padding(start = 8.dp)) { PSwitch(on, { setOn(it) }) }
        }
        BoText(if (on) tr("Colour changes are on for this app", "Bu uygulamada renk değişiklikleri açık") else tr("Off: this app keeps its own colours", "Kapalı: bu uygulama kendi renklerini korur"), 12)
        BoBtn(tr("Pick on screen", "Ekrandan seç"), modifier = Modifier.padding(top = 6.dp)) { openColourBar(ctx, pkg) }
        if (open) {
            BoText(tr("One colour turns into another inside this app. Strength 100% replaces it; lower values blend it towards the target.", "Bir renk bu uygulamada başka bir renge dönüşür. Güç %100 onu değiştirir; düşük değerler hedefe doğru karıştırır."), modifier = Modifier.padding(top = 6.dp))
            rules.forEach { r ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoDot(r.from); BoText("→"); BoDot(r.to)
                    BoText("#%06X  ±%d%%  %d%%".format(r.from and 0xFFFFFF, r.tol, r.strength), 13, T.text, mono = true, modifier = Modifier.weight(1f))
                    PIcon("close", T.sub, 18.dp, Modifier.clickable { RecolorRules.put(sp, pkg, rules - r); rules = RecolorRules.get(sp, pkg) })
                }
            }
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Black" to 0x000000, "Charcoal" to 0x121212, "Graphite" to 0x1E1F22, "Navy" to 0x0B1220, "Warm" to 0x1A1410, "Forest" to 0x0C1510).forEach { (n, c) -> PChip(to == c, n) { to = c } }
            }
            PNameField(code, { code = it.removePrefix("#").take(6).uppercase() }, tr("Source colour, e.g. FFFFFF", "Kaynak renk, örn. FFFFFF"), Modifier.fillMaxWidth().padding(top = 6.dp))
            BoText(tr("Strength $pct%", "Güç %$pct"), 13, T.text, modifier = Modifier.padding(top = 6.dp))
            BoSlider(strength, 0f..100f, onChange = { strength = it })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoDot(src ?: 0xFFFFFF, 40.dp); BoText("→", 20, T.text); BoDot(blended, 40.dp); BoText("→", 20); BoDot(to, 40.dp)
                BoText("#%06X".format(blended and 0xFFFFFF), 13, mono = true)
            }
            BoButtons {
                BoBtn(tr("Save rule", "Kuralı kaydet"), src != null) { RecolorRules.add(sp, pkg, src ?: 0, to or -0x1000000, 12, pct); code = ""; rules = RecolorRules.get(sp, pkg) }
                BoBtn(tr("White → this", "Beyaz → bu")) { RecolorRules.add(sp, pkg, 0xFFFFFF or -0x1000000, to or -0x1000000, 6, pct); rules = RecolorRules.get(sp, pkg) }
            }
            if (!refused) BoBtn(tr("Apply now (root)", "Şimdi uygula (root)"), Privilege.mode != Privilege.Mode.None && rules.isNotEmpty() && !busy, Modifier.padding(top = 6.dp)) { applyNow() }
            if (busy) BoProgress(null, Modifier.padding(top = 6.dp))
            if (msg.isNotEmpty()) BoText(msg, 12)
        }
    }
}

/** The Colours page: the dropper for the app in front, and the apps that have colour rules. */
@Composable
fun ColorsPage(apps: List<AppRow>, sp: SharedPreferences) {
    val ctx = LocalContext.current
    var withRules by remember { mutableStateOf(RecolorRules.apps(sp)) }
    DisposableEffect(Unit) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, k -> if (k != null && k.startsWith("rc_")) withRules = RecolorRules.apps(sp) }
        sp.registerOnSharedPreferenceChangeListener(l)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(l) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoCard {
            BoText(tr("The colour bar", "Renk çubuğu"), 16, T.text, bold = true)
            BoText(tr("Touch a colour on any screen and say what it should become. The bar goes over everything: open an app, drag the ring on a colour, pick the new colour, save. Android 11 or newer; no root.", "Herhangi bir ekranda bir renge dokunun, neye dönüşeceğini söyleyin. Çubuk her şeyin üstünde durur: bir uygulama açın, halkayı bir rengin üstüne sürükleyin, yeni rengi seçin, kaydedin. Android 11 ve üstü; root gerekmez."))
            BoBtn(tr("Open the colour bar", "Renk çubuğunu aç"), modifier = Modifier.padding(top = 8.dp)) {
                if (openColourBar(ctx, "*")) ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        if (withRules.isEmpty()) BoText(tr("No app has a colour rule yet.", "Henüz renk kuralı olan uygulama yok."))
        withRules.forEach { pkg ->
            val name = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) { AppIcon(pkg, 28); Text("  $name", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            RecolorCard(pkg, sp)
        }
    }
}

// ---- every colour of an app ----------------------------------------------------------------------------------------------------------------------

private fun lumOf(c: Int): Float = (.299f * ((c shr 16) and 255) + .587f * ((c shr 8) and 255) + .114f * (c and 255)) / 255f
private fun isBg(name: String) = name.contains("bg", true) || name.contains("background", true) || name.contains("backdrop", true)
private fun isGreyish(c: Int): Boolean { val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255; return maxOf(r, g, b) - minOf(r, g, b) <= 60 }
private fun isText(name: String) = name.contains("text", true) || name.contains("title", true) || name.contains("label", true) || name.contains("hint", true)

/** Every solid colour the app defines (whites and accents too); each one can be set by hand. Changes are overlays, kept per app. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AllColorsCard(app: AppRow, mode: Privilege.Mode) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    // a scan is kept for the app (until its apk changes), so opening the card again does not read the apk again; nothing scans by itself
    var all by remember(app.pkg) { mutableStateOf<List<Candidate>?>(scanned[app.pkg to app.sourceDir]) }
    var picked by remember(app.pkg) { mutableStateOf(setOf<String>()) }
    var busy by remember(app.pkg) { mutableStateOf(false) }
    var msg by remember(app.pkg) { mutableStateOf("") }
    var editing by remember(app.pkg) { mutableStateOf<Candidate?>(null) }
    var made by remember(app.pkg) { mutableStateOf(Overlays.overrides(sp, app.pkg)) }
    var query by remember(app.pkg) { mutableStateOf("") }
    var open by remember(app.pkg) { mutableStateOf(false) }
    var onlyDark by remember(app.pkg) { mutableStateOf(true) }
    var pickedFrom by remember(app.pkg) { mutableStateOf("FFFFFF") }
    var showAll by remember(app.pkg) { mutableStateOf(false) }
    val canOv = !Overlays.refused(app.pkg)

    BoCard {
        Row(Modifier.fillMaxWidth().clickable {
            open = !open
        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("All colours", "Tüm renkler"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 22.dp, Modifier.rotate(if (open) 180f else 0f))
        }
        if (canOv) BoText(tr("Tap a colour in the list, then tap a colour below to turn it into that. Needs BlackOut in Settings > Accessibility.", "Listeden bir renge dokunun, sonra aşağıdan bir renge dokunarak ona çevirin. Ayarlar > Erişilebilirlik'te BlackOut gerekir."), 12)
        else if (open) BoText(tr("This app is not overlayable on this phone, so its colours cannot be changed with overlays. Use the LSPosed route: turn BlackOut on in LSPosed and tick this app in its scope.", "Bu uygulama bu telefonda kaplanamaz; renkleri kaplamalarla değişmez. LSPosed yolunu kullanın: LSPosed'da BlackOut'u açın ve bu uygulamayı kapsamda işaretleyin."), 12)
        if (open && canOv) SwapRows(sp, app.pkg, pickedFrom, { pickedFrom = it }) { msg = it }
        if (open && canOv) BoButtons {
            BoBtn(if (all == null) tr("Show all colours", "Tüm renkleri göster") else tr("Refresh", "Yenile"), !busy && mode != Privilege.Mode.None) {
                busy = true
                scope.launch { all = withContext(Dispatchers.IO) { runCatching { Arsc.scan(app.sourceDir, all = true).candidates }.getOrDefault(emptyList()) }.also { scanned[app.pkg to app.sourceDir] = it }; busy = false }
            }
            if (made.isNotEmpty()) BoBtn(tr("Restore original", "Aslına döndür"), !busy) {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Overlays.remove(app.pkg) }
                    made.keys.forEach { Overlays.saveOverride(sp, app.pkg, it, null) }
                    made = emptyMap(); msg = r.message; busy = false
                }
            }
        }
        if (open && busy) BoProgress(null, Modifier.padding(top = 6.dp))
        if (open && canOv) BoSwitchRow(tr("Only dark colours", "Yalnız koyu renkler"), "", onlyDark) { onlyDark = it }

        // one tap for the usual cases; R8 hides some resource names ("..._obfuscated"): those go in by value as a rule
        fun quick(cands: List<Candidate>, col: Int, label: String) {
            if (cands.isEmpty()) { msg = "Nothing to change"; return }
            busy = true
            scope.launch {
                val (hidden, named) = cands.partition { it.name.contains("obfuscated", true) }
                hidden.forEach { RecolorRules.add(sp, app.pkg, it.color, col, 3) }
                var ok = true; var text = ""
                if (named.isNotEmpty()) {
                    val r = withContext(Dispatchers.IO) { Overlays.apply(app.pkg, named.map { it.name to col }) }
                    ok = r.ok; text = r.message
                    if (r.ok) { named.forEach { Overlays.saveOverride(sp, app.pkg, it.name, col) }; Overlays.forceStop(app.pkg) }
                }
                made = Overlays.overrides(sp, app.pkg)
                msg = if (ok) "$label (${cands.size}). " + tr("Reopen the app. If nothing changes, the app does not use these colours.", "Uygulamayı yeniden açın. Değişmezse uygulama bu renkleri kullanmıyordur.") else text
                busy = false
            }
        }
        if (open && canOv) all?.let { list ->
            PNameField(query, { query = it }, tr("Search colours", "Renklerde ara"), Modifier.fillMaxWidth().padding(top = 6.dp))
            BoButtons {
                BoBtn(tr("Backgrounds → black", "Zeminler → siyah"), !busy && mode != Privilege.Mode.None) { quick(list.filter { isBg(it.name) && lumOf(it.color) > .8f }, 0xFF000000.toInt(), "Backgrounds made black") }
                BoBtn(tr("Text → white", "Yazı → beyaz"), !busy && mode != Privilege.Mode.None) { quick(list.filter { (isText(it.name) || it.name.contains("obfuscated", true)) && lumOf(it.color) in .25f..0.6f && isGreyish(it.color) }, 0xFFFFFFFF.toInt(), "Text made white") }
            }
            if (msg.isNotEmpty()) BoText(msg, 12)
        }
        if (open && canOv) all?.sortedByDescending { isBg(it.name) }?.filter { (query.isBlank() || it.name.contains(query, true)) && (!onlyDark || lumOf(it.color) < .5f) }?.let { list -> list.take(if (showAll) list.size else 8) to list.size }?.let { (rows, total) ->
            // a long press starts a selection: more colours are added with a tap, then one colour below turns all of them at once
            if (picked.isNotEmpty()) {
                BoText(tr("${picked.size} selected: turn them all into", "${picked.size} seçili: hepsini şuna çevir"), 12)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0xFF000000.toInt(), 0xFF121212.toInt(), 0xFF1E1E1E.toInt(), 0xFF0B1020.toInt(), 0xFFFFFFFF.toInt()).forEach { col ->
                        BoDot(col, 32.dp) { if (!busy) { quick(list0(all, picked), col, tr("Selected colours changed", "Seçili renkler değişti")); picked = emptySet() } }
                    }
                }
                BoButtons {
                    BoBtn(tr("Select all shown", "Görünenlerin hepsini seç")) { picked = picked + rows.map { it.name } }
                    BoBtn(tr("Clear", "Temizle")) { picked = emptySet() }
                }
            }
            rows.forEach { c ->
                val now = made[c.name] ?: c.color
                val on = c.name in picked
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) T.accent.copy(alpha = .22f) else Color.Transparent)
                    .combinedClickable(enabled = mode != Privilege.Mode.None, onClick = { if (picked.isNotEmpty()) picked = if (on) picked - c.name else picked + c.name else { pickedFrom = "%06X".format(c.color and 0xFFFFFF); editing = c } },
                        onLongClick = { picked = if (on) picked - c.name else picked + c.name }).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    BoDot(c.color); BoText("→", modifier = Modifier.padding(horizontal = 6.dp)); BoDot(now)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BoText("#%06X → #%06X".format(c.color and 0xFFFFFF, now and 0xFFFFFF), 11, mono = true)
                    }
                }
            }
            if (total > 8) BoLink(if (showAll) tr("Show fewer", "Daha az göster") else tr("Show all colours ($total)", "Tüm renkleri göster ($total)")) { showAll = !showAll }
        }
    }

    editing?.let { c ->
        ColorEditor(app, c, sp, Overlays.overrides(sp, app.pkg)[c.name] ?: c.color,
            onDone = { col, text -> editing = null; msg = text; if (col != null) made = Overlays.overrides(sp, app.pkg) },
            onCancel = { editing = null })
    }
}

@Composable
private fun ColorEditor(app: AppRow, c: Candidate, sp: SharedPreferences, now: Int, onDone: (Int?, String) -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var hex by remember(c.name) { mutableStateOf("%06X".format(now and 0xFFFFFF)) }
    var busy by remember(c.name) { mutableStateOf(false) }
    val presets = listOf(0xFF000000.toInt(), 0xFF121212.toInt(), 0xFF1E1E1E.toInt(), 0xFF0B1020.toInt(), 0xFFFFFFFF.toInt(), 0xFF8C9EFF.toInt())
    WinAlert(onDismissRequest = onCancel,
        title = { Text(c.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { presets.forEach { col -> BoDot(col, 34.dp) { hex = "%06X".format(col and 0xFFFFFF) } } }
                PNameField(hex, { hex = it.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(6) }, "#", Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            BoBtn(tr("Apply", "Uygula"), !busy && hex.length == 6) {
                val v = hex.toIntOrNull(16) ?: return@BoBtn
                val col = v or -0x1000000
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Overlays.apply(app.pkg, listOf(c.name to col)) }
                    if (r.ok) { Overlays.saveOverride(sp, app.pkg, c.name, col); Overlays.forceStop(app.pkg) }
                    busy = false
                    Toast.makeText(ctx, if (r.ok) tr("Overlay on for ${c.name}. The app restarts; if the screen does not change, this app does not use that colour: try Dark pages.", "${c.name} için kaplama açık. Uygulama yeniden başlar; ekran değişmezse bu uygulama o rengi kullanmıyordur: Koyu sayfaları deneyin.") else r.message, Toast.LENGTH_LONG).show()
                    onDone(if (r.ok) col else null, r.message)
                }
            }
        },
        dismissButton = { BoBtn(tr("Cancel", "Vazgeç")) { onCancel() } })
}

/** Any colour can be turned into another: type the source and the new colour (hex). */
@Composable
private fun SwapRows(sp: SharedPreferences, pkg: String, from: String, onFrom: (String) -> Unit, onMsg: (String) -> Unit) {
    var to by remember(pkg) { mutableStateOf("000000") }
    fun hex(v: String) = (v.toIntOrNull(16) ?: 0) or -0x1000000
    fun clean(v: String) = v.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(6)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { PNameField(from, { onFrom(clean(it)) }, tr("from #", "şundan #"), Modifier.fillMaxWidth()) }
        BoDot(if (from.length == 6) hex(from) else 0xFF000000.toInt(), 34.dp)
        BoText("→")
        BoDot(if (to.length == 6) hex(to) else 0xFF000000.toInt(), 34.dp)
        Box(Modifier.weight(1f)) { PNameField(to, { to = clean(it) }, tr("to #", "şuna #"), Modifier.fillMaxWidth()) }
    }
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0xFF000000.toInt(), 0xFF121212.toInt(), 0xFF0B1020.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8FAFC.toInt()).forEach { col ->
            BoDot(col, 30.dp) { if (from.length == 6) { RecolorRules.add(sp, pkg, hex(from), col, 6); onMsg("#$from → #%06X. ".format(col and 0xFFFFFF) + tr("Reopen the app.", "Uygulamayı yeniden açın.")) } else onMsg(tr("Pick a colour from the list first.", "Önce listeden bir renk seçin.")) }
        }
    }
    BoButtons {
        BoBtn(tr("Apply", "Uygula"), from.length == 6 && to.length == 6) { RecolorRules.add(sp, pkg, hex(from), hex(to), 0); onMsg("#$from → #$to. " + tr("Reopen the app.", "Uygulamayı yeniden açın.")) }
        BoBtn(tr("Remove", "Kaldır")) {
            val key = hex(from) and 0xFFFFFF
            RecolorRules.put(sp, pkg, RecolorRules.get(sp, pkg).filter { (it.from and 0xFFFFFF) != key })
            onMsg(tr("Removed #$from. Reopen the app.", "#$from kaldırıldı. Uygulamayı yeniden açın."))
        }
    }
}

// ---- the colour walkthrough ---------------------------------------------------------------------------------------------------------------------

private class SetupStep(val title: String, val text: String, val hint: String)

private val SETUP_STEPS get() = listOf(
    SetupStep(tr("Turn on dark pages", "Koyu sayfaları açın"),
        tr("In this app's page, switch on the dark pages option. Pages that paint themselves (Samsung Notes, readers) need it.", "Bu uygulamanın sayfasında koyu sayfalar seçeneğini açın. Kendini boyayan sayfalar (Samsung Notes, okuyucular) buna ihtiyaç duyar."),
        tr("Still light? Try the Pages section and pick the screen you see, then reopen the app.", "Hâlâ açık mı? Sayfalar bölümünü deneyin, gördüğünüz ekranı seçin, sonra uygulamayı yeniden açın.")),
    SetupStep(tr("Make the whites black", "Beyazları siyah yapın"),
        tr("Open Colours below. Tap Pick on screen, drag the ring over a white area of the app, then Save. Near-white shades match too.", "Aşağıdaki Renkleri açın. Ekrandan seç'e dokunun, halkayı uygulamanın beyaz bir alanına sürükleyin, sonra Kaydet. Beyaza yakın tonlar da eşleşir."),
        tr("Nothing changed? Lower the tolerance chip (±%) to 6%, pick the white again, and reopen the app.", "Değişmedi mi? Tolerans çipini (±%) %6'ya indirin, beyazı yeniden seçin ve uygulamayı yeniden açın.")),
    SetupStep(tr("Check the tick marks", "Onay işaretlerini denetleyin"),
        tr("If checkmarks or ticks look wrong, pick their colour the same way and set it to white or black.", "Onay işaretleri yanlış görünüyorsa, rengini aynı şekilde seçin ve beyaz ya da siyah yapın."),
        tr("Still wrong? Pick the tick's colour from its centre, not its edge.", "Hâlâ yanlış mı? İşaretin rengini kenarından değil, ortasından seçin.")),
    SetupStep(tr("Check the red and green crosses", "Kırmızı ve yeşil çarpıları denetleyin"),
        tr("Pick the red or green cross and turn it grey, then reopen the app.", "Kırmızı ya da yeşil çarpıyı seçin ve griye çevirin, sonra uygulamayı yeniden açın."),
        tr("Only one cross changes? Pick each one; each icon has its own colour.", "Yalnız bir çarpı mı değişiyor? Her birini seçin; her ikonun kendi rengi var.")),
)

/** The per-app colour walkthrough: Done or Skip moves to the next card; Didn't work shows a hint. Progress is kept per app. */
@Composable
fun SetupCards(app: AppRow) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val key = "setup2_${app.pkg}"
    var step by remember(app.pkg) { mutableStateOf(sp.getInt(key, 0)) }
    var failed by remember(app.pkg) { mutableStateOf(false) }
    fun go(n: Int) { step = n; failed = false; sp.edit().putInt(key, n).apply() }
    val steps = SETUP_STEPS
    BoCard {
        BoText(tr("Setup for ${app.label}", "${app.label} kurulumu"), 14, T.text, bold = true)
        if (step >= steps.size) {
            BoText(tr("All set. Reopen the app to see the result.", "Hazır. Sonucu görmek için uygulamayı yeniden açın."), 15, T.text, modifier = Modifier.padding(top = 6.dp))
            BoBtn(tr("Start over", "Baştan başla"), modifier = Modifier.padding(top = 6.dp)) { go(0) }
        } else {
            val st = steps[step]
            BoText(tr("Step ${step + 1} of ${steps.size}", "Adım ${step + 1} / ${steps.size}"), 13, T.accent, bold = true, modifier = Modifier.padding(top = 6.dp))
            Text(st.title, color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BoText(st.text, 14, T.text, modifier = Modifier.padding(top = 4.dp))
            if (failed) BoText(st.hint, 13, BadRed, modifier = Modifier.padding(top = 4.dp))
            BoButtons {
                BoBtn(tr("Done", "Tamam")) { go(step + 1) }
                BoBtn(tr("Didn't work", "Olmadı")) { failed = true }
                BoLink(tr("Skip", "Atla")) { go(step + 1) }
            }
        }
    }
}

// ---- one app, from the easy things to the hard ones -------------------------------------------------------------------------------------------

/** One app: what the module does in it, the colour rules, overlays, dark pages and the full colour list. */
@Composable
fun AppDetail(app: AppRow, result: ScanResult?, error: String?, limit: Int, keep: Float, mode: Privilege.Mode, onScan: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableIntStateOf(-1) }
    var progress by remember { mutableStateOf("") }
    val off = remember(app.pkg) { mutableStateListOf<String>() }
    var listOpen by remember(app.pkg) { mutableStateOf(false) }
    var refused by remember(app.pkg) { mutableStateOf(Overlays.refused(app.pkg)) }
    val spx = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    LaunchedEffect(app.pkg) { if (result == null) onScan() }
    LaunchedEffect(app.pkg, mode) { if (mode != Privilege.Mode.None) active = withContext(Dispatchers.IO) { Overlays.activeCount(app.pkg) } }
    LaunchedEffect(result) { result?.candidates?.forEach { if (KEEP.containsMatchIn(it.name) && it.name !in off) off.add(it.name) } }
    val chosen = result?.candidates?.filter { it.name !in off }.orEmpty()
    val kind by androidx.compose.runtime.produceState<AppKind?>(null, app.pkg) { value = withContext(Dispatchers.IO) { AppKindScan.detect(ctx, app.pkg) } }

    Column(Modifier.fillMaxSize()) {
        if (busy) { BoProgress(null, Modifier.padding(horizontal = pageGutter)); if (progress.isNotEmpty()) BoText(progress, 13, modifier = Modifier.padding(horizontal = pageGutter, vertical = 2.dp)) }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                AppPreviewCard(app.pkg, spx)
                if (error != null) BoText(tr("Scan failed: ", "Tarama başarısız: ") + error, 13, BadRed)
                ForceDarkCard(app.pkg, mode)
                PagesModCard(app.pkg, mode)
                PagesAppCard(app.pkg, mode)
                InvertAppCard(app.pkg, mode)
                RecolorCard(app.pkg, spx)
                if (result?.namesStripped == true) CrushCard(app.pkg, mode)
                if (result != null && active >= 0) BoText(if (active > 0) tr("$active overlays are active now", "$active kaplama şimdi etkin") else tr("No BlackOut overlay active", "Etkin BlackOut kaplaması yok"), 13, if (active > 0) T.accent else T.sub)
                BoButtons {
                    if (!refused) BoBtn(tr("Apply (${chosen.size})", "Uygula (${chosen.size})"), mode != Privilege.Mode.None && chosen.isNotEmpty() && !busy && result?.namesStripped != true) {
                        busy = true; progress = "…"
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { Overlays.apply(app.pkg, chosen.map { it.name to Overlays.scaled(it.color, keep, spx.getInt("tint", 0)) }) { d, n -> progress = "$d / $n" } }
                            msg = r.message; busy = false; progress = ""; refused = Overlays.refused(app.pkg)
                            if (r.ok) {
                                active = withContext(Dispatchers.IO) { Overlays.activeCount(app.pkg) }
                                // with root or Shizuku the app is restarted here, so the new colours apply without a manual force stop
                                if (mode != Privilege.Mode.None) { withContext(Dispatchers.IO) { Overlays.forceStop(app.pkg) }; msg = r.message + " " + tr("The app was restarted.", "Uygulama yeniden başlatıldı.") }
                            }
                        }
                    }
                    if (!refused) BoBtn(tr("Remove", "Kaldır"), mode != Privilege.Mode.None && !busy) {
                        busy = true
                        scope.launch { val r = withContext(Dispatchers.IO) { Overlays.remove(app.pkg) }; msg = r.message; busy = false; active = withContext(Dispatchers.IO) { Overlays.activeCount(app.pkg) } }
                    }
                    BoBtn(tr("Restart app", "Uygulamayı yeniden başlat"), mode != Privilege.Mode.None) { scope.launch { withContext(Dispatchers.IO) { Overlays.forceStop(app.pkg) }; msg = tr("Stopped. Open the app again.", "Durduruldu. Uygulamayı yeniden açın.") } }
                }
                if (refused) BoText(tr("This app is not overlayable on this phone, so overlays cannot change its colours. Use the LSPosed route: turn BlackOut on in LSPosed and tick this app in its scope.", "Bu uygulama bu telefonda kaplanamaz; kaplamalar renklerini değiştiremez. LSPosed yolunu kullanın: LSPosed'da BlackOut'u açın ve bu uygulamayı kapsamda işaretleyin."), 13, modifier = Modifier.padding(top = 8.dp))
                msg?.let { BoText(it, 13, T.text, modifier = Modifier.padding(top = 8.dp)) }
                if (!refused) BoButtons {
                    BoLink(tr("Surfaces only", "Yalnız yüzeyler")) { off.clear(); result?.candidates?.forEach { if (KEEP.containsMatchIn(it.name)) off.add(it.name) } }
                    BoLink(tr("Select all", "Tümünü seç")) { off.clear() }
                    BoLink(tr("Select none", "Hiçbirini seçme")) { off.clear(); result?.candidates?.forEach { off.add(it.name) } }
                    BoLink(tr("Re-scan", "Yeniden tara")) { onScan() }
                }
            }
            if (result == null && error == null) item { Row(verticalAlignment = Alignment.CenterVertically) { com.umutk.pulse.PLoader(Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); BoText(tr("Reading the app's colours…", "Uygulamanın renkleri okunuyor…"), 14, T.text) } }
            if (!refused) item { BoLink(if (listOpen) tr("Hide colours", "Renkleri gizle") else tr("Show colours (${result?.candidates?.size ?: 0})", "Renkleri göster (${result?.candidates?.size ?: 0})")) { listOpen = !listOpen } }
            if (listOpen && !refused) items(result?.candidates.orEmpty(), key = { it.id }) { c ->
                val on = c.name !in off
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (on) off.add(c.name) else off.remove(c.name) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PCheck(on, { if (on) off.add(c.name) else off.remove(c.name) })
                    Spacer(Modifier.width(8.dp))
                    BoDot(c.color); BoText("→", modifier = Modifier.padding(horizontal = 6.dp)); BoDot(Overlays.scaled(c.color, keep, spx.getInt("tint", 0)))
                    Spacer(Modifier.width(10.dp))
                    Text(c.name, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            item { kind?.let { k -> BoCard { BoText(k.title, 15, T.text, bold = true); BoText(k.advice) } } }
            item { SetupCards(app) }
            item { AllColorsCard(app, mode) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The app as it is now (a grey surface) beside what BlackOut makes of it (black), with the app's own icon and name on both halves. */
@Composable
fun AppPreviewCard(pkg: String, sp: SharedPreferences) {
    val ctx = LocalContext.current
    val label = remember(pkg) { runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    val before = 0xFF2A2A2A.toInt()
    val after = Grey.fix(before, sp.getFloat("limit", 56f).toInt(), sp.getFloat("keep", 0f) / 100f, sp.getInt("tint", 0), sp.getBoolean("amo_soft", false))
    Row(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, T.sub.copy(alpha = .3f), RoundedCornerShape(16.dp))) {
        listOf(before to tr("Now", "Şimdi"), after to "BlackOut").forEach { (c, cap) ->
            Column(Modifier.weight(1f).fillMaxSize().background(Color(c or -0x1000000)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                AppIcon(pkg, 48)
                Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp))
                Text(cap, color = Color.White.copy(alpha = .55f), fontSize = 11.sp)
            }
        }
    }
}

// ---- all Material You apps at once ---------------------------------------------------------------------------------------------------------------

/** One switch for every Material You app: the dark tones of the system palette (the greys Google apps take their surfaces from) become black. */
@Composable
fun GlobalCard(mode: Privilege.Mode) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("blackout", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tinted by remember { mutableStateOf(false) }
    var pure by remember { mutableStateOf(sp.getBoolean("glob_pure", true)) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var active by remember { mutableIntStateOf(-1) }
    var supported by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(mode) {
        if (mode != Privilege.Mode.None) {
            active = withContext(Dispatchers.IO) { Overlays.activeCount("android") }
            supported = withContext(Dispatchers.IO) { RootOpts.answers("cmd overlay list --user 0") }
        }
    }
    BoCard {
        BoText(tr("All Material You apps at once", "Tüm Material You uygulamaları bir arada"), 16, T.text, bold = true)
        BoText(tr("Google apps take their greys from the system palette. Turn them black there.", "Google uygulamaları griyi sistem paletinden alır. Onları orada siyah yapın."), modifier = Modifier.padding(top = 4.dp))
        SupportLine(mode, supported)
        BoText(tr("Black level", "Siyah seviyesi"), 13, T.text, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(pure, tr("Pure black #000000", "Saf siyah #000000")) { pure = true; sp.edit().putBoolean("glob_pure", true).apply() }
            PChip(!pure, tr("Very dark grey #1A1A1A", "Çok koyu gri #1A1A1A")) { pure = false; sp.edit().putBoolean("glob_pure", false).apply() }
        }
        BoSwitchRow(tr("Also tinted containers (chips, cards)", "Renkli kapları da (çipler, kartlar)"), "", tinted) { tinted = it }
        BoButtons {
            BoBtn(tr("Apply to all", "Hepsine uygula"), mode != Privilege.Mode.None && !busy && supported != false) {
                busy = true
                scope.launch {
                    val base = Overlays.scaled(Overlays.GLOBAL_BASE, if (pure) 0f else 1f)
                    val r = withContext(Dispatchers.IO) { Overlays.apply("android", Overlays.globalNames(tinted).map { it to base }) }
                    msg = r.message; busy = false; active = withContext(Dispatchers.IO) { Overlays.activeCount("android") }
                }
            }
            BoBtn(tr("Reset to original", "Aslına döndür"), mode != Privilege.Mode.None && !busy) {
                busy = true
                scope.launch { val r = withContext(Dispatchers.IO) { Overlays.remove("android") }; msg = r.message; busy = false; active = withContext(Dispatchers.IO) { Overlays.activeCount("android") } }
            }
        }
        if (active >= 0) BoText(if (active > 0) tr("$active system tones are black now", "$active sistem tonu şimdi siyah") else tr("Not active", "Etkin değil"), 13, if (active > 0) T.accent else T.sub, modifier = Modifier.padding(top = 6.dp))
        msg?.let { BoText(it, 13, T.text, modifier = Modifier.padding(top = 4.dp)) }
        BoText(tr("Then close the app from recents and open it again.", "Sonra uygulamayı son uygulamalardan kapatıp yeniden açın."), modifier = Modifier.padding(top = 6.dp))
    }
}

/** What was read from each app's apk, kept for the session: reading it is slow, so it only happens when asked for. */
private val scanned = HashMap<Pair<String, String>, List<Candidate>>()
private fun list0(all: List<Candidate>?, names: Set<String>) = all.orEmpty().filter { it.name in names }
