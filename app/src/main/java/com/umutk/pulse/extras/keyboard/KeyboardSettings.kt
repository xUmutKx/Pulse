package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.LaunchedEffect
import com.umutk.pulse.extras.keyboard.KeyboardEffects.keyWash
import com.umutk.pulse.extras.keyboard.KeyboardEffects.boardWash
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraLookRow
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import androidx.compose.material3.Icon
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.Look
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Pulse keyboard's settings: set-up, a live picture of the keyboard, themes (with a custom one), size, typing and the learned words. */
@Composable
fun KeyboardSettings(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    KeyboardPrefs.load(ctx)
    val save = { KeyboardPrefs.save(ctx) }
    val st = remember { KbState().also { it.slots = listOf("to", "the", "that") } }
    var test by remember { mutableStateOf("") }
    var askReset by remember { mutableStateOf(false) }
    var askForget by remember { mutableStateOf(false) }
    var learned by remember { mutableStateOf(KeyboardDictionary.learnedCount()) }
    val dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    st.dark = dark

    val acts = remember { PreviewKbActions(st) }
    // the picked press effect plays on a few keys of the preview by itself, so it is seen without typing
    LaunchedEffect(KeyboardPrefs.pressFx, KeyboardPrefs.fxColor) {
        if (KeyboardPrefs.pressFx <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        repeat(4) {
            st.letterRects.values.randomOrNull()?.let { r -> if (st.bursts.size > 4) st.bursts.removeAt(0); st.bursts.add(KbBurst(r, System.nanoTime() / 1_000_000)) }
            kotlinx.coroutines.delay(380)
        }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    // the live keyboard at the bottom is shown only when asked for (the button at the top), so it does not take the room of the settings
    var showKb by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var dataNote by remember { mutableStateOf("") }
    val exportData = androidx.activity.compose.rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { KeyboardBackup.export(ctx, uri, KbBackupPw.pw) }.getOrDefault(-1) }
            dataNote = if (n >= 0) tr("Saved ($n files)", "Kaydedildi ($n dosya)") else tr("Could not save", "Kaydedilemedi")
        }
    }
    val importData = androidx.activity.compose.rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { KeyboardBackup.import(ctx, uri, KbBackupPw.pw) }.getOrDefault(-1) }
            dataNote = if (n == -2) tr("Wrong or missing password", "Şifre yanlış ya da boş") else if (n >= 0) tr("Loaded ($n files)", "Yüklendi ($n dosya)") else tr("This is not a keyboard backup", "Bu bir klavye yedeği değil")
            learned = KeyboardDictionary.learnedCount(); rev++
        }
    }
    // the keyboard stays at the bottom, as it does on the phone, so every change shows at once
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Keyboard", "Klavye"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(40.dp).clickable { showKb = !showKb }, contentAlignment = Alignment.Center) { PIcon("keyboard", if (showKb) T.accent else T.text, 22.dp) }
        }
        ExtraLookRow()

        // set-up: switch the keyboard on, then pick it
        PCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Turn on", "Etkinleştir"), Modifier.weight(1f)) { ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                PButton(tr("Choose", "Seç"), Modifier.weight(1f)) { (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
            }
            Spacer(Modifier.height(8.dp))
            PField(test, { test = it }, tr("Try it here", "Burada dene"), Modifier.fillMaxWidth())
            // keeps Pulse Keyboard the default after Pulse is force-stopped (set back through root or Shizuku when Pulse opens)
            var keepDefault by remember { mutableStateOf(KeyboardDefault.keep(ctx)) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Keep Pulse as the keyboard", "Pulse'u klavye olarak tut"), color = T.text, fontSize = 15.sp)
                    Text(tr("Needs root or Shizuku", "Root ya da Shizuku gerekir"), color = T.sub, fontSize = 12.sp)
                }
                PSwitch(keepDefault, { on ->
                    keepDefault = on
                    KeyboardDefault.setKeep(ctx, on)
                    if (on) Thread { runCatching { KeyboardDefault.restore(ctx.applicationContext) } }.start()
                })
            }
        }

        // themes
        // search the settings: the sections open and only the matching switches show
        PField(KbFind.q, { KbFind.q = it }, tr("Search settings", "Ayarlarda ara"), Modifier.fillMaxWidth().padding(vertical = 6.dp))
        // the features that matter most, all in one place: pick what you want (each one is also in its own section below)
        KbSection("tune", tr("Pick your features", "Özelliklerini seç")) {
            Switch(tr("Suggestions", "Öneriler"), tr("The bar above the keys offers words", "Tuşların üstündeki çubuk kelime önerir"), KeyboardPrefs.suggestions) { KeyboardPrefs.suggestions = it; save() }
            Switch(tr("Autocorrect", "Otomatik düzeltme"), tr("Words are corrected to the middle suggestion as you type", "Yazdıkça kelime ortadaki öneriye düzeltilir"), KeyboardPrefs.autocorrect) { KeyboardPrefs.autocorrect = it; save() }
            Switch(tr("Learn from what I type", "Yazdıklarımdan öğren"), tr("New words and names are kept on the phone", "Yeni kelimeler ve isimler telefonda tutulur"), KeyboardPrefs.learn) { KeyboardPrefs.learn = it; save() }
            Switch(tr("Capital at the start of a sentence", "Cümle başında büyük harf"), "", KeyboardPrefs.autoCap) { KeyboardPrefs.autoCap = it; save() }
            Switch(tr("Emoji suggestions", "Emoji önerileri"), tr("A word can offer its emoji", "Kelime kendi emojisini önerebilir"), KeyboardPrefs.emojiPredictions) { KeyboardPrefs.emojiPredictions = it; save() }
            Switch(tr("Mark a possible slip", "Olası yazım hatasını işaretle"), tr("A small grey dot on a word you typed that looks like a kept mistake", "Yazdığın kelime kalmış bir hataya benziyorsa küçük gri nokta"), KeyboardPrefs.slipMark) { KeyboardPrefs.slipMark = it; save() }
            Switch(tr("Swipe typing", "Kaydırarak yazma"), "", KeyboardPrefs.glide) { KeyboardPrefs.glide = it; save() }
            Switch(tr("Number row", "Sayı satırı"), "", KeyboardPrefs.numberRow) { KeyboardPrefs.numberRow = it; save() }
            Switch(tr("Symbols on long press", "Basılı tutunca semboller"), "", KeyboardPrefs.longPressSymbols) { KeyboardPrefs.longPressSymbols = it; save() }
            Switch(tr("Key pop-ups", "Tuş balonları"), "", KeyboardPrefs.keyPopups) { KeyboardPrefs.keyPopups = it; save() }
            Switch(tr("Vibration", "Titreşim"), "", KeyboardPrefs.haptic) { KeyboardPrefs.haptic = it; save() }
            Switch(tr("Key sound", "Tuş sesi"), "", KeyboardPrefs.sound) { KeyboardPrefs.sound = it; save() }
        }
        KbSection("palette", tr("Theme", "Tema")) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyboardThemes.ids.forEach { id -> ThemeTile(id, dark, KeyboardPrefs.theme == id) { KeyboardPrefs.theme = id; save() } }
            }
            // Google Keyboard: its tone and the key borders
            if (KeyboardPrefs.theme == "gboard") {
                Text(tr("Tone", "Ton"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("teal" to tr("Teal grey", "Yeşilimsi gri"), "smoke" to tr("Smoke", "Duman"), "black" to tr("Black", "Siyah"), "material" to "Material").forEach { (k, n) ->
                        PChip(KeyboardPrefs.gbTone == k, n) { KeyboardPrefs.gbTone = k; save() }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PChip(KeyboardPrefs.gbBorders, tr("Key borders", "Tuş kenarları")) { KeyboardPrefs.gbBorders = !KeyboardPrefs.gbBorders; save() }
                    PChip(KeyboardPrefs.gbGlass, tr("See-through", "Şeffaf")) { KeyboardPrefs.gbGlass = !KeyboardPrefs.gbGlass; save() }
                }
            }
            // key shape and icon family over the theme (the theme's own by default)
            Text(tr("Key corners", "Tuş köşeleri") + ": " + (if (KeyboardPrefs.keyRadius < 0) tr("Theme", "Tema") else "${KeyboardPrefs.keyRadius}"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-1 to tr("Theme", "Tema"), 0 to tr("Square", "Kare"), 4 to tr("Soft", "Yumuşak"), 10 to tr("Round", "Yuvarlak"), 24 to tr("Pill", "Hap")).forEach { (r, n) ->
                    PChip(KeyboardPrefs.keyRadius == r, n) { KeyboardPrefs.keyRadius = r; save() }
                }
            }
            Text(tr("Emoji board look", "Emoji tahtası görünümü"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("same") + KeyboardThemes.ids.filter { it != "custom" }).forEach { id ->
                    PChip(KeyboardPrefs.emojiTheme == id, if (id == "same") tr("Same", "Aynı") else KeyboardThemes.name(id)) { KeyboardPrefs.emojiTheme = id; save() }
                }
            }
            Text(tr("Icons", "Simgeler"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("theme" to tr("Theme", "Tema"), "outlined" to tr("Outlined", "Çizgi"), "filled" to tr("Filled", "Dolu"), "rounded" to tr("Rounded", "Yuvarlak"), "sharp" to tr("Sharp", "Keskin")).forEach { (k, n) ->
                    PChip(KeyboardPrefs.keyIcons == k, n) { KeyboardPrefs.keyIcons = k; save() }
                }
            }
            if (KeyboardPrefs.theme == "custom") {
                ColorEditor(tr("Background", "Arka plan"), KeyboardPrefs.cBg) { KeyboardPrefs.cBg = it; save() }
                ColorEditor(tr("Keys", "Tuşlar"), KeyboardPrefs.cKey) { KeyboardPrefs.cKey = it; save() }
                ColorEditor(tr("Special keys", "Özel tuşlar"), KeyboardPrefs.cSpecial) { KeyboardPrefs.cSpecial = it; save() }
                ColorEditor(tr("Letters", "Harfler"), KeyboardPrefs.cText) { KeyboardPrefs.cText = it; save() }
                ColorEditor(tr("Accent", "Vurgu"), KeyboardPrefs.cAccent) { KeyboardPrefs.cAccent = it; save() }
                ColorEditor(tr("Toolbar menu", "Araç çubuğu menüsü"), KeyboardPrefs.cMenu) { KeyboardPrefs.cMenu = it; save() }
                Text("${tr("Corners", "Köşeler")}: ${KeyboardPrefs.cRadius}", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                PSlider(KeyboardPrefs.cRadius / 24f, { KeyboardPrefs.cRadius = (it * 24).roundToInt() }, 0f..1f, onDone = { save() })
            }
            Spacer(Modifier.height(8.dp))
            PButton(tr("Reset theme", "Temayı sıfırla"), compact = true) { KeyboardPrefs.resetTheme(ctx) }
        }

        // languages and keys
        KbSection("language", tr("Languages", "Diller")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardLayouts.all.forEach { l ->
                    PChip(KeyboardPrefs.langList().first() == l.code, l.name) {
                        // the chosen layout is the one that shows; the other language still helps with corrections
                        val other = KeyboardLayouts.all.first { it.code != l.code }.code
                        KeyboardPrefs.langs = "${l.code},$other"; st.lang = l.code; save()
                        scope.launch { withContext(Dispatchers.IO) { KeyboardLayouts.all.forEach { x -> if (!KeyboardDictionary.hasList(ctx, x.code)) KeyboardDictionary.download(ctx, x.code) } }; rev++ }
                    }
                }
            }
            Text(tr("The chosen language shows its keys; both languages correct and suggest together. A language without a word list downloads one.", "Seçili dilin tuşları görünür; iki dil birlikte düzeltir ve öneri verir. Sözlüğü olmayan dil kendi sözlüğünü indirir."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            LayoutsBlock(save)
            Switch(tr("Number row", "Sayı satırı"), "", KeyboardPrefs.numberRow) { KeyboardPrefs.numberRow = it; save() }
            Switch(tr("Key bar", "Tuş çubuğu"), tr("Fn (F1 to F12), Shift, arrows, Home, End, Page up and down, Esc, Tab, select all, copy, paste", "Fn (F1-F12), Shift, oklar, Home, End, Page up ve down, Esc, Tab, tümünü seç, kopyala, yapıştır"), KeyboardPrefs.arrows) { KeyboardPrefs.arrows = it; save() }
            Text(tr("Key beside ?123", "?123 yanındaki tuş"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("menu" to tr("Menu (⋮)", "Menü (⋮)"), "emoji" to "Emoji", "none" to tr("None", "Yok")).forEach { (k, n) -> PChip(KeyboardPrefs.bottomKey == k, n) { KeyboardPrefs.bottomKey = k; save() } }
            }
            // what a long press on the comma, the period and that key does; the key shows a small icon of it
            listOf(
                Triple(tr("Long press on the comma", "Virgüle uzun basınca"), { KeyboardPrefs.commaHold }, { v: String -> KeyboardPrefs.commaHold = v }),
                Triple(tr("Long press on the period", "Noktaya uzun basınca"), { KeyboardPrefs.periodHold }, { v: String -> KeyboardPrefs.periodHold = v }),
                Triple(tr("Long press on the key beside ?123", "?123 yanındaki tuşa uzun basınca"), { KeyboardPrefs.bottomHold }, { v: String -> KeyboardPrefs.bottomHold = v }),
            ).forEach { (label, get, set) ->
                Text(label, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExtraKeys.hold.forEach { (k, n) -> PChip(get() == k, if (k == "none") tr("None", "Yok") else n) { set(k); save() } }
                }
                // a small key with what its long press does: the action's icon and its name, both change with the choice
                val chosen = get()
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(T.text.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Text(label.substringAfter(" ").substringBefore(" ").take(3), color = T.text, fontSize = 13.sp)
                    }
                    if (chosen != "none") Icon(com.umutk.pulse.extras.keyboard.toolIcon(keyboardThemeNow(), chosen), null, tint = T.text, modifier = Modifier.size(20.dp))
                    Text(ExtraKeys.hold.firstOrNull { it.first == chosen }?.second ?: tr("None", "Yok"), color = T.text, fontSize = 13.sp)
                }
            }
            Switch(tr("Split keys", "Bölünmüş tuşlar"), tr("A gap in the middle of the rows, for a tablet", "Satırların ortasında boşluk, tablet için"), KeyboardPrefs.split) { KeyboardPrefs.split = it; save() }
            if (KeyboardPrefs.split) {
                Text("${tr("Gap", "Boşluk")}: ${KeyboardPrefs.splitGap / 10f}", color = T.sub, fontSize = 12.sp)
                PSlider((KeyboardPrefs.splitGap - 5) / 45f, { KeyboardPrefs.splitGap = 5 + (it * 45).roundToInt() }, 0f..1f, onDone = { save() })
            }
            Switch(tr("Direction keys", "Yön tuşları"), tr("Nine big keys: drag a key towards a character to type it", "Dokunun veya bir karaktere doğru kaydırın: dokuz büyük tuş"), KeyboardPrefs.dirLayout) { KeyboardPrefs.dirLayout = it; save() }
            Switch(tr("Extra button", "Ek tuş"), tr("A compact bottom row with a Shift key at the left", "Solda Shift tuşu olan, daha sıkışık alt satır"), KeyboardPrefs.bottomShift) { KeyboardPrefs.bottomShift = it; save() }
            Switch(tr("Floating keyboard", "Yüzen klavye"), tr("A keyboard you move anywhere on the screen by the handle on top", "Üstteki tutamakla ekranın her yerine taşınan klavye"), KeyboardPrefs.floating) { KeyboardPrefs.floating = it; save() }
            if (KeyboardPrefs.floating) Switch(tr("Number row in the floating keyboard", "Yüzen klavyede sayı satırı"), tr("Off: hold a letter for its digit", "Kapalı: sayı için harfe basılı tut"), KeyboardPrefs.floatNumberRow) { KeyboardPrefs.floatNumberRow = it; save() }
            // the accessibility keyboard: over any app, switched on here; turning it on sends the phone to its accessibility settings when Pulse is not on there yet
            val accCtx = LocalContext.current
            var accOn by remember { mutableStateOf(AccessKeyboard.on(accCtx)) }
            Switch(tr("Floating keyboard (always on)", "Yüzen klavye (hep açık)"), tr("A button stays on top of every app and game; it opens the whole Pulse keyboard anywhere on the screen, with or without a text box. The phone's own keyboard still opens in text boxes. Pulse's accessibility service must be on", "Her uygulamanın ve oyunun üstünde duran bir düğme; yazı kutusu olsun olmasın ekranın her yerinde tüm Pulse klavyesini açar. Telefonun klavyesi yazı kutularında yine açılır. Pulse erişilebilirlik hizmeti açık olmalı"), accOn) {
                accOn = it
                AccessKeyboard.setOn(accCtx, it)
                if (it && !AccessKeyboard.serviceEnabled(accCtx)) accCtx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Switch(tr("Large key text", "Büyük tuş yazısı"), "", KeyboardPrefs.largeText) { KeyboardPrefs.largeText = it; save() }
            Switch(tr("Key pop-ups", "Tuş balonları"), "", KeyboardPrefs.keyPopups) { KeyboardPrefs.keyPopups = it; save() }
            Switch(tr("Long-press symbols", "Uzun basınca semboller"), tr("Accents and digits", "Aksanlar ve rakamlar"), KeyboardPrefs.longPressSymbols) { KeyboardPrefs.longPressSymbols = it; save() }
            Switch(tr("Language on the space key", "Boşluk tuşunda dil adı"), "", KeyboardPrefs.spaceLabel) { KeyboardPrefs.spaceLabel = it; save() }
        }

        // swipes, extra keys and the toolbar
        KbSection("numpad", tr("Number pad", "Sayı paneli")) {
            Title(tr("Bottom right key", "Sağ alt tuş"))
            val opts = listOf("enter", "=", ",", "/", "%", "-", "*")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opts.forEach { o ->
                    val on = KeyboardPrefs.numEnter == o
                    Box(Modifier.size(56.dp, 48.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) T.accent else T.sub.copy(alpha = .12f))
                        .clickable { KeyboardPrefs.numEnter = o; save() },
                        contentAlignment = Alignment.Center) {
                        Text(if (o == "enter") "↵" else o, color = if (on) T.bg else T.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // corner swipes on the number keys: a preset says what a swipe toward each corner types (off by default; the small marks show it)
            Title(tr("Corner swipes on the keys", "Tuşlarda köşe kaydırması"))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyCorners.NUM_PRESETS.forEach { pr ->
                    val on = KeyboardPrefs.numCorners == pr
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) T.accent else T.sub.copy(alpha = .12f)).clickable { KeyboardPrefs.numCorners = pr; save() }.padding(horizontal = 14.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text(when (pr) { "phone" -> tr("Old phone letters", "Eski telefon harfleri"); "edit" -> tr("Edit functions", "Düzenleme işlevleri"); "symbols" -> tr("Symbols", "Semboller"); "math" -> tr("Maths", "Matematik"); else -> tr("Off", "Kapalı") }, color = if (on) T.bg else T.text, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // the preview: the bottom row of the number pad as it will look
            Title(tr("Preview", "Önizleme"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("*", "#", KeyboardPrefs.numEnter).forEach { k ->
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(T.sub.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Text(if (k == "enter") "↵" else k, color = T.text, fontSize = 18.sp)
                    }
                }
            }
        }
        KbSection("keyboard", tr("Swipes and keys", "Kaydırma ve tuşlar")) {
            // the layout kind: letters as on a computer or a phone, an old phone keypad, or the PC one with Esc, Ctrl, arrows and F keys
            Text(tr("Layout type", "Düzen türü"), color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val kind = if (KeyboardPrefs.multiTap) "phone" else if (KeyboardPrefs.arrows) "pc" else "qwerty"
                listOf("qwerty" to "QWERTY", "phone" to tr("Old phone (T9)", "Eski telefon (T9)"), "pc" to "PC").forEach { (k, n) ->
                    PChip(kind == k, n) {
                        KeyboardPrefs.multiTap = k == "phone"; KeyboardPrefs.arrows = k == "pc"
                        if (k == "pc") KeyboardPrefs.proRow = true
                        save()
                    }
                }
            }
            Switch(tr("Swipe typing", "Kaydırarak yazma"), tr("Slide over the letters to type a word", "Harflerin üstünde kaydırarak kelime yaz"), KeyboardPrefs.glide) { KeyboardPrefs.glide = it; save() }
            Switch(tr("Flick up for the symbol", "Yukarı kaydırınca sembol"), tr("A short flick up on a key types its first long-press character", "Tuşu kısaca yukarı kaydırmak uzun basış karakterini yazar"), KeyboardPrefs.flick) { KeyboardPrefs.flick = it; save() }
            Switch(tr("Corner swipes", "Köşe kaydırmaları"), tr("Swipe a letter toward a corner for the symbol or key written there (Esc, Tab, Copy ...). Turns swipe typing off", "Harfi köşesine kaydırınca orada yazan sembol ya da tuş çalışır (Esc, Tab, Kopyala ...). Kaydırarak yazmayı kapatır"), KeyboardPrefs.corners) { KeyboardPrefs.corners = it; save() }
            if (KeyboardPrefs.corners) Switch(tr("Symbols on the corners", "Köşelerde semboller"), tr("The small labels around each letter", "Her harfin çevresindeki küçük yazılar"), KeyboardPrefs.cornerHints) { KeyboardPrefs.cornerHints = it; save() }
            Switch(tr("Files in the bar", "Çubukta dosyalar"), tr("Five letters of a file's name offer that file at the right of the bar", "Bir dosya adının beş harfi yazılınca o dosya çubuğun sağında önerilir"), KeyboardPrefs.fileSuggest) { KeyboardPrefs.fileSuggest = it; save() }
            Switch(tr("Shift + key", "Shift + tuş"), tr("Hold Shift with one finger and tap a key with another: its extra character is typed (/ @ ...)", "Bir parmağınla Shift'i basılı tut, diğeriyle bir tuşa dokun: tuşun ek karakteri yazılır (/ @ ...)"), KeyboardPrefs.holdShiftSymbols) { KeyboardPrefs.holdShiftSymbols = it; save() }
            Switch(tr("Circle and round trip", "Daire ve gidiş-dönüş"), tr("A circle on a letter types its capital, out and back types its accent. Turns swipe typing off", "Harfte daire büyük harfini, gidip dönmek aksanlısını yazar. Kaydırarak yazmayı kapatır"), KeyboardPrefs.circleGestures) { KeyboardPrefs.circleGestures = it; save() }
            Switch(tr("Phone keypad (multi-tap)", "Telefon tuş takımı (çoklu basış)"), tr("Letters on nine digit keys, as on an old phone", "Harfler dokuz rakam tuşunda, eski telefonlardaki gibi"), KeyboardPrefs.multiTap) { KeyboardPrefs.multiTap = it; save() }
            Switch(tr("Animations", "Animasyonlar"), "", KeyboardPrefs.anim) { KeyboardPrefs.anim = it; save() }
            Switch(tr("Smooth mode", "Akıcı mod"), tr("No animations, effects, RGB light or neural weighing", "Animasyon, efekt, RGB ışık ve sinirsel tartım yok"), KeyboardPrefs.smooth) { KeyboardPrefs.smooth = it; save() }
            // (the Samsung look has its own switch, off until it is asked for)
            if (KeyboardPrefs.theme == "samsung") Switch(tr("Extra key row (Samsung look)", "Ek tuş satırı (Samsung görünümü)"), tr("Esc, Tab, Ctrl, Alt, Win, arrows", "Esc, Tab, Ctrl, Alt, Win, oklar"), KeyboardPrefs.samProRow) { KeyboardPrefs.samProRow = it; save() }
            Switch(tr("Extra key row", "Ek tuş satırı"), tr("Esc, Tab, Ctrl, Alt, Win, arrows, F-keys", "Esc, Tab, Ctrl, Alt, Win, oklar, F tuşları"), KeyboardPrefs.proRow) { KeyboardPrefs.proRow = it; save() }
            if (KeyboardPrefs.proRow) {
                Switch(tr("Under the space row", "Boşluk satırının altında"), "", KeyboardPrefs.proBottom) { KeyboardPrefs.proBottom = it; save() }
                Switch(tr("Icons on the keys", "Tuşlarda ikon"), "", KeyboardPrefs.proIcons) { KeyboardPrefs.proIcons = it; save() }
                ItemPicker(ExtraKeys.all.map { it.id to it.label }, KeyboardPrefs.proKeys) { KeyboardPrefs.proKeys = it; save() }
                Switch(tr("Second extra row", "İkinci ek satır"), tr("Fn, Home, End, Page keys, editing keys", "Fn, Home, End, Page tuşları, düzenleme tuşları"), KeyboardPrefs.proRow2) { KeyboardPrefs.proRow2 = it; save() }
                if (KeyboardPrefs.proRow2) ItemPicker(ExtraKeys.all.map { it.id to it.label }, KeyboardPrefs.proKeys2) { KeyboardPrefs.proKeys2 = it; save() }
            }
            Text(tr("Toolbar", "Araç çubuğu"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            ItemPicker(ExtraKeys.toolbar, KeyboardPrefs.toolbarItems) { KeyboardPrefs.toolbarItems = it; KeyboardPrefs.menuItems = KeyboardPrefs.menuItems.split(',').filter { id -> id !in it.split(',') }.joinToString(","); save() }
            Text(tr("Menu (the three dots)", "Menü (üç nokta)"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            ItemPicker(ExtraKeys.menu, KeyboardPrefs.menuItems) { KeyboardPrefs.menuItems = it; KeyboardPrefs.toolbarItems = KeyboardPrefs.toolbarItems.split(',').filter { id -> id !in it.split(',') }.joinToString(","); save() }
        }

        // turned sideways: the keyboard's own height and rows there, with both keyboards drawn as they will look
        KbSection("rotate", tr("Landscape", "Yatay")) {
            OrientPreview(false, dark)
            Spacer(Modifier.height(8.dp))
            OrientPreview(true, dark)
            Text("${tr("Height", "Yükseklik")}: ${KeyboardPrefs.landHeightPct}%", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            PSlider((KeyboardPrefs.landHeightPct - 70) / 180f, { KeyboardPrefs.landHeightPct = 70 + (it * 180).roundToInt() }, 0f..1f, onDone = { save() })
            Switch(tr("Extra key row", "Ek tuş satırı"), tr("Esc, Tab, Ctrl, Alt, Win, arrows, F-keys", "Esc, Tab, Ctrl, Alt, Win, oklar, F tuşları"), KeyboardPrefs.landProRow) { KeyboardPrefs.landProRow = it; save() }
            Switch(tr("Number row", "Sayı satırı"), "", KeyboardPrefs.landNumberRow) { KeyboardPrefs.landNumberRow = it; save() }
        }

        // size
        KbSection("sizes", tr("Size", "Boyut")) {
            Text("${tr("Height", "Yükseklik")}: ${KeyboardPrefs.heightPct}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.heightPct - 70) / 180f, { KeyboardPrefs.heightPct = 70 + (it * 180).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Bottom space", "Alt boşluk")}: ${KeyboardPrefs.bottomPad} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.bottomPad / 60f, { KeyboardPrefs.bottomPad = (it * 60).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Gamepad size", "Gamepad boyutu")}: ${KeyboardPrefs.padScale}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.padScale - 60) / 90f, { KeyboardPrefs.padScale = 60 + (it * 90).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Gamepad dead zone", "Gamepad ölü bölge")}: ${KeyboardPrefs.padDead}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.padDead - 5) / 45f, { KeyboardPrefs.padDead = 5 + (it * 45).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Gamepad sticks height", "Gamepad joystick yüksekliği")}: ${KeyboardPrefs.padLift}", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.padLift / 40f, { KeyboardPrefs.padLift = (it * 40).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Side space", "Yan boşluk")}: ${KeyboardPrefs.sidePad} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.sidePad / 400f, { KeyboardPrefs.sidePad = (it * 400).roundToInt() }, 0f..1f, onDone = { save() })
            Switch(tr("Compact layout", "Derli toplu düzen"), tr("Keys close together, the keyboard narrower and in the middle, like a tablet's own keyboard", "Tuşlar bitişik, klavye daha dar ve ortada (tabletin kendi klavyesi gibi)"), KeyboardPrefs.compact) { KeyboardPrefs.compact = it; save() }
            if (KeyboardPrefs.compact) {
                Text("${tr("Keyboard width", "Klavye genişliği")}: ${KeyboardPrefs.compactPct}%", color = T.sub, fontSize = 12.sp)
                PSlider((KeyboardPrefs.compactPct - 40) / 60f, { KeyboardPrefs.compactPct = 40 + (it * 60).roundToInt() }, 0f..1f, onDone = { save() })
            }
            Switch(tr("Emoji page uses the full width", "Emoji sayfası tüm genişliği kullanır"), tr("The side space only narrows the keys; the emoji page, the symbols and text art fill the screen", "Yan boşluk yalnızca tuşları daraltır; emoji, sembol ve metin sanatı sayfası ekranı doldurur"), KeyboardPrefs.emojiFull) { KeyboardPrefs.emojiFull = it; save() }
            Switch(tr("Keys on the left, emoji on the right", "Tuşlar solda, emoji sağda"), tr("On a wide screen the emoji page and the keys sit side by side: off puts the emoji on the left, on puts them on the right", "Geniş ekranda emoji sayfası ve tuşlar yan yana durur: kapalıyken emoji solda, açıkken sağda olur"), KeyboardPrefs.emojiRight) { KeyboardPrefs.emojiRight = it; save() }
            Text("${tr("Space between keys", "Tuşlar arası boşluk")}: ${KeyboardPrefs.keyGap} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.keyGap / 8f, { KeyboardPrefs.keyGap = (it * 8).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("?123 key narrower", "?123 tuşu daha dar")}: ${KeyboardPrefs.modeShrink}%", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.modeShrink / 40f, { KeyboardPrefs.modeShrink = (it * 40).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Comma key narrower", "Virgül tuşu daha dar")}: ${KeyboardPrefs.commaShrink}%", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.commaShrink / 40f, { KeyboardPrefs.commaShrink = (it * 40).roundToInt() }, 0f..1f, onDone = { save() })
            Text(tr("The space bar gets the room these keys leave.", "Space tuşu bu tuşların bıraktığı yeri alır."), color = T.sub, fontSize = 11.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to tr("Full width", "Tam genişlik"), "left" to tr("Left hand", "Sol el"), "right" to tr("Right hand", "Sağ el")).forEach { (k, n) ->
                    PChip(KeyboardPrefs.oneHand == k, n) { KeyboardPrefs.oneHand = k; save() }
                }
            }
        }

        // typing
        KbSection("edit", tr("Typing", "Yazım")) {
            Switch(tr("Quick auth in the top bar", "Hızlı kimlik üst çubukta"), tr("Off: it sits in the ⋮ menu", "Kapalı: ⋮ menüsünde durur"), "auth" in KeyboardPrefs.toolbarItems.split(',')) { top ->
                val tb = KeyboardPrefs.toolbarItems.split(',').filter { it.isNotBlank() && it != "auth" }.toMutableList()
                val mn = KeyboardPrefs.menuItems.split(',').filter { it.isNotBlank() && it != "auth" }.toMutableList()
                if (top) tb.add(minOf(tb.indexOf("clip") + 1, tb.size).coerceAtLeast(0), "auth") else mn.add("auth")
                KeyboardPrefs.toolbarItems = tb.joinToString(","); KeyboardPrefs.menuItems = mn.joinToString(","); save()
            }
            Switch(tr("Quick auth: fingerprint first", "Hızlı kimlik: önce parmak izi"), tr("Off: the codes show at once", "Kapalı: kodlar hemen görünür"), KeyboardPrefs.authLock) { KeyboardPrefs.authLock = it; save() }
            Switch(tr("Offline voice typing (Whisper)", "Çevrimdışı sesle yazma (Whisper)"), tr("Uses the speech model of Pulse AI on the phone; press the microphone again to stop. Off: the phone's own recogniser", "Pulse AI'nin ses modelini telefonda kullanır; durdurmak için mikrofona tekrar bas. Kapalı: telefonun kendi tanıyıcısı"), KeyboardPrefs.voiceWhisper) { KeyboardPrefs.voiceWhisper = it; save() }
            if (KeyboardPrefs.voiceWhisper && com.umutk.pulse.extras.ai.aiSupported && !com.umutk.pulse.extras.ai.Transcriber.ready(ctx)) {
                // no speech model yet: one tap fetches the small Whisper model (60 MB, in Pulse's own downloader; it shows on the Downloads page)
                val m = com.umutk.pulse.extras.ai.SPEECH_MODELS[0]
                val pr by androidx.compose.runtime.produceState(-1f, ctx) { while (true) { value = com.umutk.pulse.extras.ai.Store.progress(ctx, m); kotlinx.coroutines.delay(800) } }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(T.sub.copy(alpha = .12f)).clickable { if (pr < 0f) { com.umutk.pulse.extras.ai.Store.download(ctx, m); com.umutk.pulse.extras.ai.Store.setActive(ctx, "speech", m.id) } }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pr < 0f) tr("Get the speech model (Whisper base, 60 MB)", "Ses modelini indir (Whisper base, 60 MB)") else tr("Downloading the speech model: ${(pr * 100).toInt()}%", "Ses modeli iniyor: %${(pr * 100).toInt()}"), color = T.text, fontSize = 14.sp)
                }
            }
            Switch(tr("Hybrid correction", "Hibrit düzeltme"), tr("Adds WM Keyboard's rules: acronyms and codes are left alone, a held-down letter (helllo) and a missed space (twowords) are mended", "WM Keyboard'ın kurallarını ekler: kısaltmalara ve kodlara dokunmaz, basılı kalan harfi (merhabaaa) ve unutulan boşluğu (ikikelime) düzeltir"), KeyboardPrefs.hybridFix) { KeyboardPrefs.hybridFix = it; save() }
            Switch(tr("Calculator and units: = answers", "Hesap makinesi ve birimler: = yanıtlar"), tr("12*5= gives the answer; 10 km to mi= converts (length, weight, temperature, area, volume, speed, time, data)", "12*5= sonucu verir; 10 km to mi= çevirir (uzunluk, ağırlık, sıcaklık, alan, hacim, hız, zaman, veri)"), KeyboardPrefs.calcEquals) { KeyboardPrefs.calcEquals = it; save() }
            // GIF search: the user's own Tenor key (empty: no GIF button)
            PField(KeyboardPrefs.gifKey, { KeyboardPrefs.gifKey = it.trim(); save() }, tr("Tenor key for GIF search (optional)", "GIF araması için Tenor anahtarı (isteğe bağlı)"), Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Switch(tr("Suggestions", "Öneriler"), tr("Three words above the keys", "Tuşların üstünde üç kelime"), KeyboardPrefs.suggestions) { KeyboardPrefs.suggestions = it; save() }
            Switch(tr("Tool icons", "Araç simgeleri"), tr("The button at the left of the bar that opens the row of tool icons", "Çubuğun solundaki, araç simgeleri sırasını açan düğme"), KeyboardPrefs.barTools) { KeyboardPrefs.barTools = it; save() }
            Switch(tr("Correct when typing pauses", "Yazarken durunca düzelt"), tr("A word that can only be a slip is mended without a space (so a message sent with the app's own button is corrected too)", "Ancak hata olabilecek kelime boşluk olmadan düzeltilir (uygulamanın kendi gönder tuşuyla da düzeltilmiş gider)"), KeyboardPrefs.pauseFix) { KeyboardPrefs.pauseFix = it; save() }
            Switch(tr("Highlight a corrected word", "Düzeltilen kelimeyi vurgula"), tr("The phone's own blue highlight flashes on the word that was just corrected", "Az önce düzeltilen kelimede telefonun kendi mavi vurgusu yanıp söner"), KeyboardPrefs.fixFlash) { KeyboardPrefs.fixFlash = it; save() }
            Switch(tr("Grey behind a corrected word", "Düzeltilen kelimenin arkası gri"), tr("A soft grey behind the word for a moment", "Kelimenin arkası bir an açık gri olur"), KeyboardPrefs.fixSelect) { KeyboardPrefs.fixSelect = it; save() }
            Switch(tr("Autocorrect", "Otomatik düzeltme"), tr("Words are corrected to the middle suggestion as you type", "Yazdıkça kelime ortadaki öneriye düzeltilir"), KeyboardPrefs.autocorrect) { KeyboardPrefs.autocorrect = it; save() }
            // after an automatic correction the person can undo it; how many undos make the keyboard leave that word alone for good
            Text(tr("Leave a word alone after I undo its correction", "Düzeltmesini geri alınca kelimeyi bırak") + ": ${KeyboardPrefs.undoToKeep}×", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to tr("1 time", "1 kez"), 2 to tr("2 times (default)", "2 kez (varsayılan)")).forEach { (n, label) -> PChip(KeyboardPrefs.undoToKeep == n, label) { KeyboardPrefs.undoToKeep = n; save() } }
            }
            Switch(tr("Neural correction", "Sinir ağı düzeltmesi"), tr("Corrections are weighed by the sentence, on the phone", "Düzeltmeler cümleye göre tartılır, telefonda"), KeyboardPrefs.neural) { KeyboardPrefs.neural = it; save() }
            Text("${tr("Strength", "Güç")}: " + listOf(tr("Modest", "Ölçülü"), tr("Normal", "Normal"), tr("Aggressive", "Sert"), tr("Very aggressive", "Çok sert"))[KeyboardPrefs.autoLevel.coerceIn(0, 3)], color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PSlider(KeyboardPrefs.autoLevel / 3f, { KeyboardPrefs.autoLevel = (it * 3).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Correct on its own from", "Kendiliğinden düzelt, benzerlik")}: ${KeyboardPrefs.autoRatio}%", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PSlider((KeyboardPrefs.autoRatio - 70) / 30f, { KeyboardPrefs.autoRatio = 70 + (it * 30).roundToInt() }, 0f..1f, onDone = { save() })
            Switch(tr("Comma inside a word is a slip", "Kelime içindeki virgül hatadır"), tr("A comma typed straight before a letter is taken out and the word goes on", "Harften hemen önce yazılan virgül silinir, kelime devam eder"), KeyboardPrefs.commaSlip) { KeyboardPrefs.commaSlip = it; save() }
            Text(tr("Keep clipboard items for", "Pano öğeleri şu kadar saklansın"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to tr("1 hour", "1 saat"), 3 to tr("3 hours", "3 saat"), 24 to tr("1 day", "1 gün"), 168 to tr("1 week", "1 hafta"), 0 to tr("Never delete", "Hiç silme")).forEach { (h, label) ->
                    val on = KeyboardPrefs.clipHours == h
                    Text(label, color = if (on) Color.White else T.text, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Look.accent else T.panel).clickable { KeyboardPrefs.clipHours = h; save() }.padding(vertical = 8.dp, horizontal = 2.dp))
                }
            }
            Switch(tr("Limit the number of clipboard items", "Pano öğe sayısını sınırla"), tr("Off: nothing is dropped for being too many. On: only this many unpinned items stay, the oldest go first", "Kapalı: çok diye hiçbir şey silinmez. Açık: sabitlenmemiş öğelerden yalnızca bu kadarı kalır, en eskiler gider"), KeyboardPrefs.clipLimitOn) { KeyboardPrefs.clipLimitOn = it; save() }
            if (KeyboardPrefs.clipLimitOn) {
                Text("${tr("Items kept", "Saklanan öğe")}: ${KeyboardPrefs.clipLimit}", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                PSlider(((KeyboardPrefs.clipLimit - 100) / 19900f).coerceIn(0f, 1f), { KeyboardPrefs.clipLimit = (100 + (it * 199).roundToInt() * 100).coerceIn(100, 20000) }, 0f..1f, onDone = { save() })
            }
            Switch(tr("Password manager in the bar", "Parola yöneticisi çubukta"), tr("The system autofill suggestions show in the bar; off, they come as the system's own pop-up", "Sistem otomatik doldurma önerileri çubukta görünür; kapalıysa sistemin kendi açılır penceresi çıkar"), KeyboardPrefs.inlineAutofill) { KeyboardPrefs.inlineAutofill = it; save() }
            Switch(tr("Pin button in the bar", "Çubukta sabitleme tuşu"), tr("The copied item or photo in the bar gets a pin: pinned ones stay in the clipboard and in the bar", "Çubuktaki kopyalanan öğe ya da fotoğrafta sabitleme tuşu olur: sabitlenenler panoda ve çubukta kalır"), KeyboardPrefs.quickPinBtn) { KeyboardPrefs.quickPinBtn = it; save() }
            Switch(tr("Quick prediction insert", "Hızlı tahmin ekleme"), tr("Space puts in the middle prediction, even before the next word is started", "Boşluk, yeni kelime başlamadan da ortadaki tahmini ekler"), KeyboardPrefs.quickPrediction) { KeyboardPrefs.quickPrediction = it; save() }
            Switch(tr("Quick period", "Hızlı nokta"), tr("Double tap on space puts in a period", "Boşluğa çift dokunuş nokta koyar"), KeyboardPrefs.quickPeriod) { KeyboardPrefs.quickPeriod = it; save() }
            Switch(tr("Auto capitalise", "Otomatik büyük harf"), tr("Capital letter at the start of a sentence", "Cümle başında büyük harf"), KeyboardPrefs.autoCap) { KeyboardPrefs.autoCap = it; save() }
            Switch(tr("Auto space", "Otomatik boşluk"), tr("A space after , ; : ! ?", "Virgül, noktalı virgül, iki nokta, ünlem ve sorudan sonra boşluk"), KeyboardPrefs.autoSpace) { KeyboardPrefs.autoSpace = it; save() }
            Switch(tr("Cursor control", "İmleç denetimi"), tr("Hold space and slide to move the cursor", "Boşluğa basılı tutup kaydırınca imleç gider"), KeyboardPrefs.cursorControl) { KeyboardPrefs.cursorControl = it; save() }
            if (KeyboardPrefs.cursorControl) {
                Text("${tr("Cursor sensitivity", "İmleç hassasiyeti")}: ${KeyboardPrefs.cursorSens}%", color = T.sub, fontSize = 12.sp)
                PSlider((KeyboardPrefs.cursorSens - 50) / 150f, { KeyboardPrefs.cursorSens = 50 + (it * 150).roundToInt() }, 0f..1f, onDone = { save() })
            }
            Switch(tr("Quick delete", "Hızlı silme"), tr("Slide from the delete key to select and delete text", "Silme tuşundan kaydırarak seç ve sil"), KeyboardPrefs.quickDelete) { KeyboardPrefs.quickDelete = it; save() }
            Switch(tr("Emoji predictions", "Emoji tahminleri"), tr("Emoji on the suggestion bar", "Öneri çubuğunda emoji"), KeyboardPrefs.emojiPredictions) { KeyboardPrefs.emojiPredictions = it; save() }
            Switch(tr("Detect the language of a sentence", "Cümlenin dilini algıla"), tr("The words of the sentence decide: a single English word does not switch the keyboard", "Cümlenin kelimeleri belirler: tek bir İngilizce kelime klavyeyi değiştirmez"), KeyboardPrefs.autoLang) { KeyboardPrefs.autoLang = it; save() }
            // the e-mail key (put it on the bar, in the menu, or on a long press): the domain it types after "@"
            PField(KeyboardPrefs.emailDomain, { KeyboardPrefs.emailDomain = it.trim(); save() }, tr("E-mail key adds (e.g. gmail.com)", "E-posta tuşu ekler (örn. gmail.com)"), Modifier.fillMaxWidth().padding(vertical = 6.dp))
            // what the keyboard learns from typing (never in password, e-mail and address fields)
            Text(tr("Learning", "Öğrenme"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to tr("Words", "Kelimeler"), 2 to tr("Sentences", "Cümleler"), 3 to tr("Both", "İkisi"), 0 to tr("Off", "Kapalı")).forEach { (m, n) ->
                    PChip(KeyboardPrefs.learnMode == m, n) { KeyboardPrefs.learnMode = m; KeyboardPrefs.learn = m != 0; save() }
                }
            }
            Text(when (KeyboardPrefs.learnMode) {
                1 -> tr("Learns the words, names and corrections you confirm.", "Kelimeleri, özel isimleri ve onayladığın düzeltmeleri öğrenir.")
                2 -> tr("Learns which words follow each other and your usual phrases.", "Hangi kelimenin hangisini izlediğini ve sık kullandığın ifadeleri öğrenir.")
                3 -> tr("Learns both the words and how they follow each other.", "Hem kelimeleri hem birbirini izleyişlerini öğrenir.")
                else -> tr("Nothing new is saved.", "Yeni hiçbir şey kaydedilmez.")
            }, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        // feel
        KbSection("effects", tr("Effects", "Efektler")) {
            // every option shows what it does: a small keyboard with the colour wash, a key with its colour effect, a key pressed over and over with its motion, a sound that plays on a tap
            @Composable fun chips(title: String, names: List<Pair<String, String>>, cur: Int, set: (Int) -> Unit, preview: @Composable (Int) -> Unit) {
                Text(title, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { i, n ->
                        val on = cur == i
                        val sh = RoundedCornerShape(14.dp)
                        Column(Modifier.clip(sh).background(if (on) T.accent.copy(alpha = .18f) else T.text.copy(alpha = .05f)).border(if (on) 2.dp else 1.dp, if (on) T.accent else T.text.copy(alpha = .12f), sh)
                            .clickable { set(i); save() }.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            preview(i)
                            Text(tr(n.first, n.second), color = if (on) T.text else T.sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            val keyFill = Color(0xFF2A2B31)
            val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.toPx() }
            chips(tr("Keyboard colour effect", "Klavye renk efekti"), KeyboardEffects.boardNames, KeyboardPrefs.boardFx, { KeyboardPrefs.boardFx = it }) { i ->
                Box(Modifier.size(width = 84.dp, height = 50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF15161B)).boardWash(i), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) { Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(5) { Box(Modifier.size(width = 12.dp, height = 12.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .2f))) } } }
                    }
                }
            }
            chips(tr("Key colour effect", "Tuş renk efekti"), KeyboardEffects.keyNames, KeyboardPrefs.keyFx, { KeyboardPrefs.keyFx = it }) { i ->
                Box(Modifier.size(width = 52.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(keyFill).keyWash(i, T.accent, cornerPx), contentAlignment = Alignment.Center) {
                    Text("A", color = Color.White, fontSize = 17.sp)
                }
            }
            chips(tr("Key motion effect", "Tuş hareket efekti"), KeyboardEffects.motionNames, KeyboardPrefs.motionFx, { KeyboardPrefs.motionFx = it }) { i ->
                var down by remember { mutableStateOf(false) }
                LaunchedEffect(i) { while (true) { kotlinx.coroutines.delay(750); down = true; kotlinx.coroutines.delay(280); down = false } }
                // the same press size and spring as the real key
                val pressed = when (i) { 1 -> 1.1f; 2 -> .86f; 3 -> .9f; else -> .93f }
                val sc by androidx.compose.animation.core.animateFloatAsState(if (down) pressed else 1f,
                    if (i == 3) androidx.compose.animation.core.spring(dampingRatio = .35f, stiffness = 600f) else androidx.compose.animation.core.tween(70), label = "fxm")
                Box(Modifier.size(width = 52.dp, height = 46.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 44.dp, height = 40.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(8.dp)).background(keyFill), contentAlignment = Alignment.Center) {
                        Text("A", color = Color.White, fontSize = 17.sp)
                    }
                }
            }
            // the key-press effects, in three rows: around the key (with None first), across the keyboard, the dark styles; each preview plays its effect over and over
            @Composable fun pressPreview(kind: Int) {
                val p = remember(kind) { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(kind) {
                    while (kind > 0) {
                        p.snapTo(0f)
                        p.animateTo(1f, androidx.compose.animation.core.tween(KeyboardEffects.pressMs(kind).toInt(), easing = androidx.compose.animation.core.LinearEasing))
                        kotlinx.coroutines.delay(450)
                    }
                }
                val accent = KeyboardEffects.fxTint(KeyboardPrefs.fxColor, T.accent, (System.nanoTime() / 1_000_000).toInt())
                androidx.compose.foundation.Canvas(Modifier.size(width = 84.dp, height = 50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF15161B))) {
                    val kw = size.width * .26f; val kh = size.height * .44f
                    val key = androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset((size.width - kw) / 2f, (size.height - kh) / 2f), androidx.compose.ui.geometry.Size(kw, kh))
                    drawRoundRect(keyFill, key.topLeft, key.size, androidx.compose.ui.geometry.CornerRadius(cornerPx * .6f))
                    if (kind > 0) with(KeyboardEffects) { drawPress(kind, key, p.value, accent, 7) }
                }
            }
            val pf = KeyboardPrefs.pressFx
            // every key-press effect in two rows that scroll together with one swipe
            Text(tr("Key press effect", "Tuşa basınca efekt"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 until 10, 10 until KeyboardEffects.pressNames.size).forEach { range ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        range.forEach { i ->
                            val on = KeyboardPrefs.pressFx == i
                            val sh = RoundedCornerShape(14.dp)
                            Column(Modifier.clip(sh).background(if (on) T.accent.copy(alpha = .18f) else T.text.copy(alpha = .05f)).border(if (on) 2.dp else 1.dp, if (on) T.accent else T.text.copy(alpha = .12f), sh)
                                .clickable { KeyboardPrefs.pressFx = i; save() }.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                pressPreview(i)
                                Text(tr(KeyboardEffects.pressNames[i].first, KeyboardEffects.pressNames[i].second), color = if (on) T.text else T.sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            // the colour of the effects (neon, or RGB: a new hue with every press) and how long they last
            chips(tr("Effect colour", "Efekt rengi"), KeyboardEffects.colorNames, KeyboardPrefs.fxColor, { KeyboardPrefs.fxColor = it; save() }) { i ->
                Box(Modifier.size(width = 52.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(if (i == 4) Color(0xFF15161B) else KeyboardEffects.fxTint(i, T.accent, 0)), contentAlignment = Alignment.Center) {
                    if (i == 4) Box(Modifier.size(width = 40.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(List(7) { Color.hsv(it * 55f, .85f, 1f) })))
                }
            }
            // a picture behind the keys: the phone's picker gives a copy (its first frame for a GIF), darkened by the slider
            val ctxPic = LocalContext.current
            val picPick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.GetContent()) { uri ->
                if (uri != null) runCatching {
                    val f = java.io.File(ctxPic.filesDir, "kb_picture.img")
                    ctxPic.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    KeyboardPrefs.bgPath = f.absolutePath; save()
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Keyboard picture", "Klavye resmi"), color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(tr("Choose", "Seç"), color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { picPick.launch("image/*") }.padding(8.dp))
                if (KeyboardPrefs.bgPath.isNotEmpty()) Text(tr("Remove", "Kaldır"), color = T.sub, fontSize = 14.sp, modifier = Modifier.clickable { KeyboardPrefs.bgPath = ""; save() }.padding(8.dp))
            }
            // the space bar's own picture and the small figure walking on it
            val spacePick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.GetContent()) { uri ->
                if (uri != null) runCatching {
                    val f = java.io.File(ctxPic.filesDir, "kb_space.img")
                    ctxPic.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    KeyboardPrefs.spacePic = ""; KeyboardPrefs.spacePic = f.absolutePath; save()
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Space bar picture", "Boşluk tuşu resmi"), color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(tr("Choose", "Seç"), color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { spacePick.launch("image/*") }.padding(8.dp))
                if (KeyboardPrefs.spacePic.isNotEmpty()) Text(tr("Remove", "Kaldır"), color = T.sub, fontSize = 14.sp, modifier = Modifier.clickable { KeyboardPrefs.spacePic = ""; save() }.padding(8.dp))
            }
            Text(tr("On the space bar", "Boşluk tuşunda"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("", "🐈", "🐕", "🐢", "🦆", "🐧", "🚗", "🚀", "👾", "🦖", "🐌", "⭐").forEach { e ->
                    val on = KeyboardPrefs.spaceWalker == e
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) T.accent.copy(alpha = .25f) else T.text.copy(alpha = .06f)).clickable { KeyboardPrefs.spaceWalker = e; save() }, contentAlignment = Alignment.Center) {
                        if (e.isEmpty()) PIcon("close", T.sub, 18.dp) else Text(e, fontSize = 20.sp)
                    }
                }
            }
            // the person's own figure: any emoji or letters typed, or a picture from the gallery; and a switch to turn it round when it walks backwards
            run {
                var own by remember { mutableStateOf(if (KeyboardPrefs.spaceWalker in listOf("", "🐈", "🐕", "🐢", "🦆", "🐧", "🚗", "🚀", "👾", "🦖", "🐌", "⭐")) "" else KeyboardPrefs.spaceWalker) }
                val walkPick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.GetContent()) { uri ->
                    if (uri != null) runCatching {
                        val f = java.io.File(ctxPic.filesDir, "kb_walker.img")
                        ctxPic.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                        KeyboardPrefs.spaceWalkerPic = ""; KeyboardPrefs.spaceWalkerPic = f.absolutePath; save()
                    }
                }
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.umutk.pulse.PField(own, { v -> own = v.take(4); KeyboardPrefs.spaceWalker = own; save() }, tr("Your own: an emoji or letters", "Kendin: bir emoji ya da harfler"), Modifier.weight(1f))
                    Text(tr("Picture", "Resim"), color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { walkPick.launch("image/*") }.padding(8.dp))
                    if (KeyboardPrefs.spaceWalkerPic.isNotEmpty()) Text(tr("Remove", "Kaldır"), color = T.sub, fontSize = 14.sp, modifier = Modifier.clickable { KeyboardPrefs.spaceWalkerPic = ""; save() }.padding(8.dp))
                }
                Switch(tr("Turn it round", "Ters çevir"), tr("If the figure walks backwards, switch this", "Figür geri geri yürüyorsa bunu aç"), KeyboardPrefs.spaceFlip) { KeyboardPrefs.spaceFlip = it; save() }
            }
            CapEditor(ctxPic) { save() }
            if (KeyboardPrefs.bgPath.isNotEmpty()) {
                Text("${tr("Picture darkness", "Resim karartma")}: ${KeyboardPrefs.bgDim}%", color = T.sub, fontSize = 12.sp)
                PSlider(KeyboardPrefs.bgDim / 100f, { KeyboardPrefs.bgDim = (it * 100).roundToInt() }, 0f..1f, onDone = { save() })
            }
            // the free colour: any red, green and blue
            if (KeyboardPrefs.fxColor == 5) {
                val cc = KeyboardPrefs.fxCustom
                listOf(Triple(tr("Red", "Kırmızı"), 16, Color(0xFFFF5252)), Triple(tr("Green", "Yeşil"), 8, Color(0xFF52FF7A)), Triple(tr("Blue", "Mavi"), 0, Color(0xFF5290FF))).forEach { (name, shift, tint) ->
                    val v = (cc shr shift) and 0xFF
                    Text("$name: $v", color = tint, fontSize = 12.sp)
                    PSlider(v / 255f, { x -> KeyboardPrefs.fxCustom = (cc and (0xFF shl shift).inv()) or ((x * 255).roundToInt() shl shift) or (0xFF shl 24) }, 0f..1f, onDone = { save() })
                }
            }
            Text("${tr("Effect length", "Efekt süresi")}: ${KeyboardPrefs.fxSpeed}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.fxSpeed - 40) / 260f, { KeyboardPrefs.fxSpeed = 40 + (it * 260).roundToInt() }, 0f..1f, onDone = { save() })
            chips(tr("Touch sound", "Dokunma sesi"), KeyboardEffects.soundNames, KeyboardPrefs.soundKind, { KeyboardPrefs.soundKind = it; KeyboardPrefs.sound = true; KeyboardEffects.play(it) }) { _ ->
                Box(Modifier.size(width = 52.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)).background(keyFill), contentAlignment = Alignment.Center) { PIcon("volume", Color.White, 22.dp) }
            }
        }
        KbSection("vibrate", tr("Feel", "His")) {
            Switch(tr("Vibrate on keypress", "Tuşta titreşim"), "", KeyboardPrefs.haptic) { KeyboardPrefs.haptic = it; save() }
            if (KeyboardPrefs.haptic) {
                Text("${tr("Vibration strength", "Titreşim gücü")}: ${KeyboardPrefs.hapticMs}", color = T.sub, fontSize = 12.sp)
                PSlider((KeyboardPrefs.hapticMs - 1) / 39f, { KeyboardPrefs.hapticMs = 1 + (it * 39).roundToInt() }, 0f..1f, onDone = { save() })
                VibeTestButton()
            }
            Text("${tr("Brightness", "Parlaklık")}: ${KeyboardPrefs.bright}%", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PSlider((KeyboardPrefs.bright - 5) / 95f, { KeyboardPrefs.bright = 5 + (it * 95).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Brightness of the space row and extra keys", "Boşluk satırı ve ek tuşların parlaklığı")}: ${KeyboardPrefs.bottomBright}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.bottomBright - 5) / 95f, { KeyboardPrefs.bottomBright = 5 + (it * 95).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Brightness of the top bar", "Üst çubuğun parlaklığı")}: ${KeyboardPrefs.topBright}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.topBright - 5) / 95f, { KeyboardPrefs.topBright = 5 + (it * 95).roundToInt() }, 0f..1f, onDone = { save() })
            Text(tr("Moving colour light", "Hareketli renk ışığı"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to tr("Off", "Kapalı"), 1 to tr("Around the keyboard", "Klavyenin çevresi"), 2 to tr("Around every key", "Her tuşun çevresi"), 3 to tr("Chasing light", "Dönen ışık")).forEach { (v, n) -> PChip(KeyboardPrefs.rgb == v, n) { KeyboardPrefs.rgb = v; save() } }
            }
            if (KeyboardPrefs.rgb > 0) {
                Text("${tr("Speed of the colour light", "Renk ışığının hızı")}: ${KeyboardPrefs.rgbSpeed}%", color = T.sub, fontSize = 12.sp)
                PSlider((KeyboardPrefs.rgbSpeed - 25) / 275f, { KeyboardPrefs.rgbSpeed = 25 + (it * 275).roundToInt() }, 0f..1f, onDone = { save() })
            }
            Switch(tr("Sound on keypress", "Tuşta ses"), "", KeyboardPrefs.sound) { KeyboardPrefs.sound = it; save() }
        }

        // typing practice: a word is shown, it is typed, and every slip is learned as a typo of that word
        KbSection("school", tr("Train autocorrect", "Otomatik düzeltmeyi eğit")) {
            var target by remember { mutableStateOf(KeyboardDictionary.practiceWord(KeyboardPrefs.langList().first()) ?: "") }
            var typed by remember { mutableStateOf("") }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (target.isEmpty()) {
                    val l = KeyboardPrefs.langList().first()
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KeyboardDictionary.load(ctx, l) }
                    target = KeyboardDictionary.practiceWord(l) ?: ""
                }
            }
            androidx.compose.runtime.DisposableEffect(Unit) { KeyboardPrefs.practicing = true; onDispose { KeyboardPrefs.practicing = false } }
            var right by remember { mutableIntStateOf(0) }
            var slips by remember { mutableIntStateOf(0) }
            Text(target.ifEmpty { tr("The word list is loading", "Kelime listesi yükleniyor") }, color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 6.dp))
            val next = {
                val t = typed.trim().lowercase()
                if (t.isNotEmpty() && target.isNotEmpty()) {
                    if (t == target.lowercase()) right++ else { slips++; KeyboardDictionary.learnTypo(ctx, t, target.lowercase()) }
                    learned = KeyboardDictionary.learnedCount()
                }
                typed = ""
                target = KeyboardDictionary.practiceWord(KeyboardPrefs.langList().first()) ?: target
            }
            PField(typed, { typed = it; if (it.endsWith(" ") || it.endsWith("\n")) next() }, tr("Type the word", "Kelimeyi yaz"), Modifier.fillMaxWidth(), onDone = { next() })
            Text("$right ✓   $slips ✗", color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            // a correction written by hand: the wrong word and the right one
            Text(tr("Teach a correction", "Bir düzeltme öğret"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            var wrongW by remember { mutableStateOf("") }
            var rightW by remember { mutableStateOf("") }
            var taught by remember { mutableStateOf("") }
            PField(wrongW, { wrongW = it }, tr("Wrong word", "Yanlış kelime"), Modifier.fillMaxWidth())
            PField(rightW, { rightW = it }, tr("Right word", "Doğrusu"), Modifier.fillMaxWidth().padding(top = 6.dp))
            PButton(tr("Add", "Ekle"), Modifier.padding(top = 8.dp)) {
                if (wrongW.isNotBlank() && rightW.isNotBlank()) {
                    KeyboardDictionary.teach(ctx, wrongW, rightW); taught = "${wrongW.trim()} → ${rightW.trim()}"; wrongW = ""; rightW = ""; learned = KeyboardDictionary.learnedCount()
                }
            }
            if (taught.isNotEmpty()) Text(tr("Added: ", "Eklendi: ") + taught, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // the learned words: look at them, forget one, add one by hand (a word added is never "corrected"), copy the list out and read one in
        KbSection("book", tr("Learned words", "Öğrenilen kelimeler")) {
            remember { KeyboardDictionary.ensureUser(ctx) }
            var addW by remember { mutableStateOf("") }
            var q by remember { mutableStateOf("") }
            var lnote by remember { mutableStateOf("") }
            val all = remember(learned, rev) { KeyboardDictionary.userWords() }
            val shownW = remember(all, q) { all.filter { q.isBlank() || it.first.contains(q.trim().lowercase()) }.take(120) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(addW, { addW = it }, tr("Add a word", "Kelime ekle"), Modifier.weight(1f))
                PButton(tr("Add", "Ekle"), compact = true) { if (addW.trim().length >= 2) { KeyboardDictionary.addUser(ctx, addW); addW = ""; learned = KeyboardDictionary.learnedCount(); rev++ } }
            }
            PField(q, { q = it }, tr("Search the ${all.size} words", "${all.size} kelimede ara"), Modifier.fillMaxWidth().padding(top = 6.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                shownW.forEach { (w, n) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(w, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("$n", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                        Box(Modifier.size(32.dp).clickable { KeyboardDictionary.removeUser(ctx, w); learned = KeyboardDictionary.learnedCount(); rev++ }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 18.dp) }
                    }
                }
                if (shownW.isEmpty()) Text(tr("Nothing here yet", "Henüz yok"), color = T.sub, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                PButton(tr("Copy the list", "Listeyi kopyala"), compact = true) {
                    (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("words", KeyboardDictionary.exportUser()))
                    lnote = tr("${all.size} words copied", "${all.size} kelime kopyalandı")
                }
                PButton(tr("Paste a list", "Liste yapıştır"), compact = true) {
                    val t = (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    val n = KeyboardDictionary.importText(ctx, t); learned = KeyboardDictionary.learnedCount(); rev++
                    lnote = tr("$n words taken", "$n kelime alındı")
                }
            }
            if (lnote.isNotEmpty()) Text(lnote, color = T.sub, fontSize = 12.sp)
        }

        // the e-mail addresses the bar offers from their first letters in any field: the ones typed or copied are kept by themselves, and one can be added by hand here
        // what the keyboard keeps about the user, in one place: e-mail addresses, numbers typed often, blocked suggestions
        KbSection("person", tr("Personal data", "Kişisel veriler")) {
            Text(tr("E-mail addresses", "E-posta adresleri"), color = T.sub, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            val sp = remember { ctx.getSharedPreferences("pulse_kb_emails", android.content.Context.MODE_PRIVATE) }
            var mails by remember { mutableStateOf(sp.getString("list", "").orEmpty().split('\n').filter { it.isNotBlank() }) }
            var addM by remember { mutableStateOf("") }
            fun saveMails(l: List<String>) { mails = l; sp.edit().putString("list", l.joinToString("\n")).apply() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(addM, { addM = it }, tr("Add an address", "Adres ekle"), Modifier.weight(1f))
                PButton(tr("Add", "Ekle"), compact = true) {
                    val m = addM.trim().lowercase()
                    if (Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$").matches(m)) { saveMails((listOf(m) + mails.filter { it != m }).take(12)); addM = "" }
                }
            }
            mails.forEach { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(32.dp).clickable { saveMails(mails.filter { it != m }) }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 18.dp) }
                }
            }
            if (mails.isEmpty()) Text(tr("Nothing here yet", "Henüz yok"), color = T.sub, fontSize = 13.sp)
            // numbers typed again and again (a phone number, an ID number): kept on the phone only and offered from their first digits (typed twice before, or added here)
            Text(tr("Numbers I type often", "Sık yazdığım sayılar"), color = T.sub, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            var nums by remember { mutableStateOf(KbNumbers.all(ctx).filter { it.value >= KbNumbers.KEEP_AT }.map { it.key to it.value }.sortedByDescending { it.second }) }
            var addN by remember { mutableStateOf("") }
            var on by remember { mutableStateOf(KeyboardPrefs.rememberNumbers) }
            fun reload() { nums = KbNumbers.all(ctx).filter { it.value >= KbNumbers.KEEP_AT }.map { it.key to it.value }.sortedByDescending { it.second } }
            Row(Modifier.fillMaxWidth().clickable { on = !on; KeyboardPrefs.rememberNumbers = on; save() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Remember numbers", "Sayıları hatırla"), color = T.text, fontSize = 15.sp)
                    Text(tr("A number of 7 or more digits typed twice is offered from its first two digits. Kept on the phone only, sealed with the phone's key; never from password fields, never card numbers.",
                        "7 ve üzeri haneli bir sayı iki kez yazılınca ilk iki hanesinden önerilir. Yalnızca telefonda, şifreli tutulur; şifre alanlarından ve kart numaralarından asla."), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(on, { on = it; KeyboardPrefs.rememberNumbers = it; save() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(addN, { addN = it.filter { c -> c.isDigit() }.take(19) }, tr("Add a number", "Sayı ekle"), Modifier.weight(1f))
                PButton(tr("Add", "Ekle"), compact = true) { if (addN.length in 7..19) { KbNumbers.save(ctx, KbNumbers.all(ctx) + (addN to 99)); addN = ""; reload() } }
            }
            nums.forEach { (n, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, color = T.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Box(Modifier.size(32.dp).clickable { KbNumbers.save(ctx, KbNumbers.all(ctx) - n); reload() }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 18.dp) }
                }
            }
            if (nums.isEmpty()) Text(tr("Nothing here yet", "Henüz yok"), color = T.sub, fontSize = 13.sp)
            else PButton(tr("Forget all", "Hepsini unut"), danger = true) { KbNumbers.clear(ctx); reload() }
            // the suggestions blocked from the bar (a word, a name or an e-mail address held and blocked): a tap brings one back
            Text(tr("Blocked suggestions", "Engellenen öneriler"), color = T.sub, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            var blocked by remember { mutableStateOf(KeyboardDictionary.bannedList(ctx)) }
            if (blocked.isEmpty()) Text(tr("None", "Yok"), color = T.sub, fontSize = 13.sp)
            blocked.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clickable { KeyboardDictionary.unban(ctx, w); blocked = KeyboardDictionary.bannedList(ctx) }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("restore", T.accent, 20.dp) }
                }
            }
        }

        // statistics: what the keyboard has learned and where the fingers land
        KbSection("speed", tr("Speed test", "Hız testi")) { KbSpeedTest() }
        KbSection("tune", tr("Statistics", "İstatistik")) {
            remember { KeyboardDictionary.ensureUser(ctx) }
            val st2 = remember(learned, rev) { KeyboardDictionary.stats().toMap() }
            @Composable fun row(label: String, v: Int?) = Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${v ?: 0}", color = T.text, fontSize = 13.sp)
            }
            // the figures that are measured, nothing made up: how many of the automatic corrections were kept (every way a correction was taken back counts: the undo,
            // a backspace into the word, a word retyped), how often it corrected, and what it knows. (There was a "learned" percentage made of four rings that were full
            // after a few thousand words, so it always read about 99; it is gone.)
            val words = KbStats.get(ctx, "words"); val fixes = KbStats.get(ctx, "fixes"); val undone = KbStats.get(ctx, "undone")
            val reversed = maxOf(undone, KeyboardDictionary.rejectTotal()).coerceAtMost(fixes)
            val kept: Float? = if (fixes < 20) null else ((fixes - reversed).toFloat() / fixes).coerceIn(0f, 1f)
            val per100 = if (words > 0) fixes * 100f / words else 0f
            @Composable fun line(label: String, v: String) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = T.sub, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text(v, color = T.text, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatRing(kept ?: 0f, 120.dp, 12.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (kept == null) "–" else "${(kept * 100).toInt()}%", color = T.text, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text(tr("corrections kept", "düzeltme kalıyor"), color = T.sub, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    line(tr("Corrections per 100 words", "100 kelimede düzeltme"), "%.1f".format(java.util.Locale.US, per100))
                    line(tr("Taken back", "Geri alınan"), "$reversed")
                    line(tr("Words it knows", "Bildiği kelime"), "${st2["words"] ?: 0}")
                    line(tr("Slips it fixes", "Düzelttiği hata"), "${st2["typos"] ?: 0}")
                }
            }
            if (kept == null) Text(tr("The percentage appears after 20 corrections.", "Yüzde 20 düzeltmeden sonra çıkar."), color = T.sub, fontSize = 11.sp)
            // the numbers as tiles
            val tiles = listOf(tr("Words written", "Yazılan kelime") to words, tr("Corrections", "Düzeltme") to fixes, tr("Taken back", "Geri alınan") to reversed,
                tr("Letters", "Harf") to (st2["letters"] ?: 0), tr("Learned words", "Öğrenilen kelime") to (st2["words"] ?: 0), tr("Learned typos", "Öğrenilen hata") to (st2["typos"] ?: 0))
            tiles.chunked(3).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { (l, v) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(T.accent.copy(alpha = .10f)).padding(10.dp)) {
                            Text(if (v >= 10000) "${v / 1000}k" else "$v", color = T.text, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(l, color = T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
            // the last two weeks: words written each day, the corrections drawn over them
            val dw = remember(rev) { KbStats.days(ctx, "words") }; val df = remember(rev) { KbStats.days(ctx, "fixes") }
            Text(tr("Last 14 days", "Son 14 gün"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            val top = (dw.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(70.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                dw.forEachIndexed { i, v ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / top).coerceIn(.02f, 1f)).clip(RoundedCornerShape(3.dp)).background(T.accent.copy(alpha = .35f)))
                        Box(Modifier.fillMaxWidth().fillMaxHeight((df[i].toFloat() / top).coerceIn(0f, 1f)).clip(RoundedCornerShape(3.dp)).background(T.accent))
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(T.accent.copy(alpha = .35f))); Text(" " + tr("words", "kelime") + "   ", color = T.sub, fontSize = 11.sp)
                Box(Modifier.size(10.dp).background(T.accent)); Text(" " + tr("corrections", "düzeltme"), color = T.sub, fontSize = 11.sp)
            }
            // a few of the slips it learned from you
            val slips = remember(rev) { KeyboardDictionary.someTypos(6) }
            if (slips.isNotEmpty()) {
                Text(tr("Slips it learned", "Öğrendiği hatalar"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                slips.forEach { (a, b) -> Row(Modifier.padding(vertical = 1.dp)) { Text(a, color = T.sub, fontSize = 13.sp, modifier = Modifier.width(110.dp)); Text("→  $b", color = T.text, fontSize = 13.sp) } }
            }
            if (KeyboardPrefs.neural) Text(tr("Neural model: Turkish and English, on the phone", "Sinir ağı modeli: Türkçe ve İngilizce, telefonda"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Text(tr("Where you type", "Nerelere basıyorsunuz"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            // the heat map: each key coloured by how often it was typed
            val rowsK = listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç")
            val most = rowsK.flatMap { it.toList() }.maxOfOrNull { KeyboardDictionary.keyCount(it) }?.coerceAtLeast(1) ?: 1
            rowsK.forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    r.forEach { ch ->
                        val f = KeyboardDictionary.keyCount(ch).toFloat() / most
                        Box(Modifier.padding(1.5.dp).size(width = 27.dp, height = 34.dp).clip(RoundedCornerShape(5.dp)).background(T.accent.copy(alpha = .08f + .8f * f)), contentAlignment = Alignment.Center) {
                            Text(ch.toString(), color = if (f > .5f) com.umutk.pulse.onAccent(T.accent) else T.text, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // data
        KbSection("save", tr("Data", "Veri")) {
            // what the keyboard keeps on the phone, and a way to clear it
            var sizeRev by remember { mutableIntStateOf(0) }
            val sizes = remember(sizeRev) {
                fun len(f: java.io.File): Long = if (f.isDirectory) (f.listFiles()?.sumOf { len(it) } ?: 0L) else f.length()
                listOf(len(java.io.File(ctx.filesDir, "clips")), len(java.io.File(ctx.filesDir, "kbd")))
            }
            fun mb(n: Long) = android.text.format.Formatter.formatShortFileSize(ctx, n)
            Text(tr("Clipboard pictures: ", "Pano resimleri: ") + mb(sizes[0]) + "\n" + tr("Word lists: ", "Sözlükler: ") + mb(sizes[1]), color = T.sub, fontSize = 13.sp)
            Row(Modifier.padding(top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Clear clipboard", "Panoyu temizle"), compact = true, danger = true) { ClipStore.clear(ctx); sizeRev++ }
            }
            Text("${tr("Learned words", "Öğrenilen kelimeler")}: $learned", color = T.sub, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Forget words", "Kelimeleri unut"), compact = true, danger = true) { askForget = true }
                PButton(tr("Factory settings", "Fabrika ayarları"), compact = true, danger = true) { askReset = true }
            }
            // everything the keyboard knows about the person in one file, and back (locked with a password when one is written)
            PField(KbBackupPw.pw, { KbBackupPw.pw = it }, tr("Backup password (optional)", "Yedek şifresi (isteğe bağlı)"), Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Export data", "Veriyi dışa aktar"), compact = true) { exportData.launch("Pulse-keyboard-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".zip") }
                PButton(tr("Import data", "Veriyi içe aktar"), compact = true) { importData.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
            }
            if (dataNote.isNotEmpty()) Text(dataNote, color = T.sub, fontSize = 12.sp)
            // the words SwiftKey learned, read with root from its own data folder
            var skNote by remember { mutableStateOf("") }
            PButton(tr("Take SwiftKey's words (root)", "SwiftKey kelimelerini al (root)"), compact = true) {
                skNote = tr("Reading…", "Okunuyor…")
                scope.launch {
                    val r = withContext(Dispatchers.IO) { SwiftKeyImport.run(ctx) }
                    skNote = if (r != null) tr("${r.words} words taken from ${r.files} files", "${r.files} dosyadan ${r.words} kelime alındı") else tr("Could not read SwiftKey: ", "SwiftKey okunamadı: ") + SwiftKeyImport.lastError
                    learned = KeyboardDictionary.learnedCount()
                }
            }
            if (skNote.isNotEmpty()) Text(skNote, color = T.sub, fontSize = 12.sp)
            Text(tr("Word lists from FrequencyWords (OpenSubtitles), CC BY-SA 4.0.", "Sözlükler: FrequencyWords (OpenSubtitles), CC BY-SA 4.0."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showKb) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) { KeyboardView(st, acts) }
    }
    if (askForget) PDialog(tr("Forget the learned words?", "Öğrenilen kelimeler unutulsun mu?"), "", tr("Forget", "Unut"), { KeyboardDictionary.forgetUser(ctx); learned = 0; askForget = false }, { askForget = false })
    if (askReset) PDialog(tr("Back to factory settings?", "Fabrika ayarlarına dönülsün mü?"), tr("Theme, size, typing options and learned words are reset.", "Tema, boyut, yazım seçenekleri ve öğrenilen kelimeler sıfırlanır."), tr("Reset", "Sıfırla"), { KeyboardPrefs.factoryReset(ctx); learned = 0; askReset = false }, { askReset = false })
}

/**
 * The keyboard itself as it is drawn upright ([land] false) or turned sideways, made small enough for the page: the real keyboard, laid out for that screen
 * (the long side of this screen as its width when sideways), so every setting shows at once.
 */
@Composable
private fun OrientPreview(land: Boolean, dark: Boolean) {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val long = maxOf(cfg.screenWidthDp, cfg.screenHeightDp); val short = minOf(cfg.screenWidthDp, cfg.screenHeightDp)
    val c2 = remember(cfg, land) { android.content.res.Configuration(cfg).apply {
        orientation = if (land) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
        screenWidthDp = if (land) long else short; screenHeightDp = if (land) short else long
    } }
    val st = remember { KbState().also { it.slots = listOf("to", "the", "that") } }
    st.dark = dark
    val acts = remember { PreviewKbActions(st) }
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        // the screen's outline, upright or sideways
        Box(Modifier.size(if (land) 20.dp else 12.dp, if (land) 12.dp else 20.dp).border(1.5.dp, T.sub, RoundedCornerShape(3.dp)))
        Text(if (land) tr("Landscape", "Yatay") else tr("Portrait", "Dikey"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides c2) {
        androidx.compose.ui.layout.Layout({ KeyboardView(st, acts) }, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))) { ms, cs ->
            val w = (c2.screenWidthDp * dens).roundToInt().coerceAtLeast(1)
            val p = ms.first().measure(androidx.compose.ui.unit.Constraints.fixedWidth(w))
            // (upright it is never drawn wider than half the page on a wide screen: it keeps the shape of the phone held upright)
            val sc = minOf(cs.maxWidth.toFloat() / w, 1f)
            layout(cs.maxWidth, (p.height * sc).roundToInt()) {
                p.placeWithLayer(((cs.maxWidth - w * sc) / 2f).roundToInt(), 0) { scaleX = sc; scaleY = sc; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
            }
        }
    }
}

/** A card of the settings that opens and closes from its header (icon, title, chevron). */
/** The text of the settings search (WM Keyboard's settings search): sections open and only the switches that match show. */
private object KbBackupPw { var pw by androidx.compose.runtime.mutableStateOf("") }
private object KbFind { var q by androidx.compose.runtime.mutableStateOf("") }

@Composable
private fun KbSection(icon: String, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(false) }
    val searching = KbFind.q.isNotBlank()
    PCard(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(icon, T.accent, 22.dp)
            Text(title, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (open || searching) 90f else 0f })
        }
        if (open || searching) content()
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Switch(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val q = KbFind.q.trim()
    if (q.isNotEmpty() && !title.contains(q, true) && !sub.contains(q, true)) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = T.text, fontSize = 15.sp)
            if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 12.sp)
        }
        PSwitch(on, onChange)
    }
}

/** A picture of a theme as the keyboard really draws it: its ground and three rows of lettered keys, the shift and delete keys in its special colour, the accent on the action key. */
@Composable
private fun ThemeTile(id: String, dark: Boolean, picked: Boolean, onClick: () -> Unit) {
    val th = KeyboardThemes.of(id, dark)
    // the Turkish Q keyboard when the app speaks Turkish, the English one otherwise
    val rows = if (tr("en", "tr") == "tr") listOf("qwertyuıopğü", "asdfghjklşi", "⇧zxcvbnmöç⌫") else listOf("qwertyuiop", "asdfghjkl", "⇧zxcvbnm⌫")
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(8.dp)).background(th.bg).border(if (picked) 2.dp else 1.dp, if (picked) T.text else T.sub.copy(alpha = .4f), RoundedCornerShape(8.dp)).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rows.forEachIndexed { r, keys ->
                Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = if (r == 1) 6.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    keys.forEachIndexed { i, ch ->
                        val special = ch == '⇧' || ch == '⌫'
                        val action = r == 2 && i == keys.lastIndex
                        Box(Modifier.weight(if (special) 1.4f else 1f).fillMaxSize().clip(RoundedCornerShape((th.radius / 3).dp))
                            .background(if (action) th.accent else if (special) th.special else th.key), contentAlignment = Alignment.Center) {
                            Text(ch.toString(), color = if (action) th.onAccent else if (special) th.specialText else th.text, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        Text(KeyboardThemes.name(id), color = T.text, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** One colour of the custom theme: its swatch and the hue, saturation and brightness sliders. */
@Composable
private fun ColorEditor(label: String, argb: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
    Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(argb)).border(1.dp, T.sub.copy(alpha = .5f), RoundedCornerShape(6.dp)))
        Text(label, color = T.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
        Text("#%06X".format(argb and 0xFFFFFF), color = T.sub, fontSize = 12.sp)
    }
    if (open) {
        fun put(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)) or 0xFF000000.toInt())
        PSlider(hsv[0] / 360f, { put(h = it * 360f) }, 0f..1f, onDone = {})
        PSlider(hsv[1], { put(s = it) }, 0f..1f, onDone = {})
        PSlider(hsv[2], { put(v = it) }, 0f..1f, onDone = {})
    }
}

/**
 * The keys of a row as they sit on the keyboard: hold a key and drag it to another place, tap it to take it off; the keys that are left over wait below to be added.
 */
@Composable
private fun ItemPicker(all: List<Pair<String, String>>, csv: String, onChange: (String) -> Unit) {
    val chosen = csv.split(',').filter { id -> all.any { it.first == id } }
    val cur by androidx.compose.runtime.rememberUpdatedState(chosen)
    val push by androidx.compose.runtime.rememberUpdatedState(onChange)
    var drag by remember { mutableStateOf<String?>(null) }
    var off by remember { mutableFloatStateOf(0f) }
    var picked by remember { mutableStateOf<String?>(null) }
    fun name(id: String) = all.firstOrNull { it.first == id }?.second ?: id
    val tileW = 62.dp
    val dens = androidx.compose.ui.platform.LocalDensity.current.density
    Row(
        Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(10.dp)).background(T.text.copy(alpha = .06f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chosen.forEach { id ->
            val dragging = drag == id
            Box(
                Modifier.width(tileW).height(46.dp).graphicsLayer { translationX = if (dragging) off else 0f; alpha = if (dragging) .75f else 1f; scaleX = if (dragging) 1.08f else 1f; scaleY = if (dragging) 1.08f else 1f }
                    .clip(RoundedCornerShape(8.dp)).background(if (picked == id) T.text.copy(alpha = .28f) else T.text.copy(alpha = .14f))
                    .clickable { picked = if (picked == id) null else id }
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { drag = id; off = 0f; picked = null },
                            onDragEnd = { drag = null; off = 0f }, onDragCancel = { drag = null; off = 0f },
                        ) { change, d ->
                            change.consume(); off += d.x
                            val w = tileW.toPx() + 4 * dens
                            val l = cur; val i = l.indexOf(id)
                            if (off > w * .6f && i < l.lastIndex) { push(l.toMutableList().also { it.add(i + 1, it.removeAt(i)) }.joinToString(",")); off -= w }
                            else if (off < -w * .6f && i > 0) { push(l.toMutableList().also { it.add(i - 1, it.removeAt(i)) }.joinToString(",")); off += w }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(name(id), color = T.text, fontSize = 12.sp, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (picked == id) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFC42B1C)).clickable { push((chosen - id).joinToString(",")); picked = null }, contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        if (chosen.isEmpty()) Text("—", color = T.sub, modifier = Modifier.padding(12.dp))
    }
    val rest = all.filter { it.first !in chosen }
    if (rest.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        rest.forEach { (id, n) -> PChip(false, "+ $n") { push((chosen + id).joinToString(",")) } }
    }
}

/** The keyboard's theme as the settings draw it: the one the keyboard uses now. */
private fun keyboardThemeNow(): KbTheme = KeyboardThemes.of(KeyboardPrefs.theme, true)

/** Hold to feel the vibration at the chosen strength; it stops when the finger leaves the button. */
@Composable
private fun VibeTestButton() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(
        Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(T.panel)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    vibeStart(ctx, KeyboardPrefs.hapticMs)
                    try { awaitRelease() } finally { vibeStop(ctx) }
                })
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(tr("Hold to test the vibration", "Titreşimi denemek için basılı tut"), color = T.text, fontSize = 13.sp)
    }
}

/** Keeps the phone vibrating until stopped: the chosen strength where the phone can set it, else the default strength. */
private fun vibeStart(c: Context, ms: Int) {
    val vib = c.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
    if (!vib.hasVibrator()) return
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        val amp = if (vib.hasAmplitudeControl()) (12 + ms * 5).coerceAtMost(255) else android.os.VibrationEffect.DEFAULT_AMPLITUDE
        vib.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0L, 60_000L), intArrayOf(0, amp), -1))
    } else {
        @Suppress("DEPRECATION") vib.vibrate(60_000L)
    }
}

private fun vibeStop(c: Context) {
    (c.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.cancel()
}


/** A ring that fills to [f] (0..1) in the accent, with [content] in its middle. */
@Composable
private fun StatRing(f: Float, size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val anim by androidx.compose.animation.core.animateFloatAsState(f, androidx.compose.animation.core.tween(900), label = "ring")
    val acc = T.accent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val st = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val tl = androidx.compose.ui.geometry.Offset(w / 2, w / 2); val sz = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            drawArc(acc.copy(alpha = .15f), -90f, 360f, false, tl, sz, style = st)
            drawArc(acc, -90f, 360f * anim, false, tl, sz, style = st)
        }
        content()
    }
}


/**
 * The caps of the person's own: pick a key, give it a colour (a swatch or red, green and blue) or a picture, or take a ready set (Rainbow, Sunset, Ocean, Candy, Forest, Mono, Neon)
 * that colours every key at once.
 */
@Composable
private fun CapEditor(ctx: android.content.Context, save: () -> Unit) {
    var sel by remember { mutableStateOf("a") }
    val rows = listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç")
    val own = KeyboardPrefs.capColors[sel]
    val swatches = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF6D4C41, 0xFF546E7A, 0xFF263238, 0xFFFFFFFF).map { it.toInt() }
    fun setColor(v: Int?) { KeyboardPrefs.capColors = if (v == null) KeyboardPrefs.capColors - sel else KeyboardPrefs.capColors + (sel to v); save() }
    val capPick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.GetContent()) { uri ->
        if (uri != null) runCatching {
            val f = java.io.File(ctx.filesDir, "kb_cap_" + sel.hashCode() + ".img")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            KeyboardPrefs.capPics = KeyboardPrefs.capPics - sel; KeyboardPrefs.capPics = KeyboardPrefs.capPics + (sel to f.absolutePath); save()
        }
    }
    fun mixC(a: Int, b: Int, t: Float) = androidx.compose.ui.graphics.lerp(Color(a), Color(b), t.coerceIn(0f, 1f)).toArgb()
    // ready sets: each paints all the letters by their place on the board, and the special keys with the set's first colour
    val presets: List<Pair<String, (Int, Int, Int) -> Int>> = listOf(
        "Rainbow" to { r, c, _ -> Color.hsv((c * 31f + r * 20f) % 360f, .62f, .55f).toArgb() },
        "Sunset" to { r, c, n -> mixC(0xFFB5446E.toInt(), 0xFFF2994A.toInt(), (c.toFloat() / n + r * .15f)) },
        "Ocean" to { r, c, n -> mixC(0xFF0F4C75.toInt(), 0xFF1B9AAA.toInt(), (c.toFloat() / n + r * .1f)) },
        "Candy" to { r, c, _ -> if ((r + c) % 2 == 0) 0xFFD94F94.toInt() else 0xFF3FA7C9.toInt() },
        "Forest" to { r, c, n -> mixC(0xFF1B4332.toInt(), 0xFF52B788.toInt(), (c.toFloat() / n + r * .1f)) },
        "Mono" to { r, c, _ -> if ((r + c) % 2 == 0) 0xFF2B2B33.toInt() else 0xFF3C3C48.toInt() },
        "Neon" to { r, c, _ -> if ((r + c) % 2 == 0) 0xFF0A3D4D.toInt() else 0xFF4D0A3D.toInt() },
    )
    Text(tr("Key caps", "Tuş kapakları"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { (name, f) ->
            Text(name, color = T.text, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(T.text.copy(alpha = .08f)).clickable {
                val m = HashMap<String, Int>()
                rows.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> m[ch.toString()] = f(r, c, row.length) } }
                val special = f(0, 0, 1); m["space"] = special; m["enter"] = special; m["shift"] = special; m["delete"] = special
                KeyboardPrefs.capColors = m; save()
            }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Text(tr("Clear all", "Hepsini temizle"), color = T.sub, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { KeyboardPrefs.capColors = emptyMap(); KeyboardPrefs.capPics = emptyMap(); save() }.padding(horizontal = 12.dp, vertical = 8.dp))
    }
    // the board in small: a tap picks the key to dress
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        (rows + listOf("")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (row.isEmpty()) listOf("shift", "space", "enter", "delete").forEach { key -> CapChip(key, sel == key, KeyboardPrefs.capColors[key], wide = key == "space") { sel = key } }
                else row.forEach { ch -> CapChip(ch.toString(), sel == ch.toString(), KeyboardPrefs.capColors[ch.toString()]) { sel = ch.toString() } }
            }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatches.forEach { c -> Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(c)).border(if (own == c) 2.dp else 1.dp, if (own == c) T.accent else T.text.copy(alpha = .2f), RoundedCornerShape(8.dp)).clickable { setColor(c) }) }
    }
    val cur = own ?: 0xFF303038.toInt()
    listOf(Triple(tr("Red", "Kırmızı"), 16, Color(0xFFFF5252)), Triple(tr("Green", "Yeşil"), 8, Color(0xFF52FF7A)), Triple(tr("Blue", "Mavi"), 0, Color(0xFF5290FF))).forEach { (name, shift, _) ->
        Text("$name: ${(cur shr shift) and 255}", color = T.sub, fontSize = 12.sp)
        PSlider(((cur shr shift) and 255) / 255f, { v -> setColor((0xFF shl 24) or ((cur and (255 shl shift).inv()) or ((v * 255f).roundToInt().coerceIn(0, 255) shl shift))) }, 0f..1f, onDone = { })
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Picture on this key", "Bu tuşta resim"), color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(tr("Choose", "Seç"), color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { capPick.launch("image/*") }.padding(8.dp))
        if (KeyboardPrefs.capPics.containsKey(sel)) Text(tr("Remove", "Kaldır"), color = T.sub, fontSize = 14.sp, modifier = Modifier.clickable { KeyboardPrefs.capPics = KeyboardPrefs.capPics - sel; save() }.padding(8.dp))
        if (own != null) Text(tr("Colour off", "Rengi kaldır"), color = T.sub, fontSize = 14.sp, modifier = Modifier.clickable { setColor(null) }.padding(8.dp))
    }
}

@Composable
private fun CapChip(label: String, selected: Boolean, color: Int?, wide: Boolean = false, onClick: () -> Unit) {
    val sh = RoundedCornerShape(6.dp)
    Box(Modifier.size(width = if (wide) 84.dp else 26.dp, height = 30.dp).clip(sh).background(color?.let { Color(it) } ?: T.text.copy(alpha = .08f)).border(if (selected) 2.dp else 1.dp, if (selected) T.accent else T.text.copy(alpha = .15f), sh).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(if (label.length == 1) label else label.take(3), color = if (color != null && Color(color).luminance() > .55f) Color.Black else T.text, fontSize = if (label.length == 1) 13.sp else 10.sp, maxLines = 1)
    }
}

/** The person's own letter layouts: pick one, make a new one, edit, import a layout file, share one. */
@Composable
private fun LayoutsBlock(save: () -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableStateOf(0) }
    val base = KeyboardPrefs.langList().first()
    val list = remember(rev, KeyboardPrefs.layoutRev) { KbCustomLayouts.all(ctx).filter { it.base == base } }
    val active = remember(rev, KeyboardPrefs.layoutRev) { KbCustomLayouts.activeId(ctx, base) }
    var editId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { uri ->
        if (uri != null) {
            val t = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull().orEmpty()
            val l = KbCustomLayouts.parse(t, uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Layout")
            if (l == null) note = tr("No letter rows found in that file", "Dosyada harf satırı bulunamadı")
            else { KbCustomLayouts.save(ctx, null, l.name, l.base, l.rows); note = tr("Imported: ", "İçe aktarıldı: ") + l.name; rev++; save() }
        }
    }
    Text(tr("Letter layout", "Harf düzeni"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PChip(active == null, tr("Standard", "Standart")) { KbCustomLayouts.use(ctx, base, null); rev++; save() }
        list.forEach { l -> PChip(active == l.id, l.name) { KbCustomLayouts.use(ctx, base, l.id); rev++; save() } }
        PChip(false, "+ " + tr("New", "Yeni")) { editId = null; name = ""; text = KeyboardLayouts.lang(base).rows.joinToString("\n"); editing = true }
        PChip(false, tr("Import", "İçe aktar")) { pick.launch(arrayOf("text/*", "application/octet-stream")) }
    }
    val cur = list.firstOrNull { it.id == active }
    if (cur != null && !editing) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PChip(false, tr("Edit", "Düzenle")) { editId = cur.id; name = cur.name; text = cur.rows.joinToString("\n"); editing = true }
        PChip(false, tr("Share", "Paylaş")) {
            runCatching { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, KbCustomLayouts.export(cur)), null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        PChip(false, tr("Delete", "Sil")) { KbCustomLayouts.delete(ctx, cur.id); rev++; save() }
    }
    if (editing) {
        PField(name, { name = it }, tr("Layout name", "Düzen adı"), Modifier.fillMaxWidth())
        PField(text, { text = it }, tr("One row of letters per line", "Her satıra bir harf sırası"), Modifier.fillMaxWidth())
        // the keys as they will look
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            text.lines().filter { it.isNotBlank() }.take(5).forEach { r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    KbCustomLayouts.keysOf(r).take(14).forEach { k ->
                        Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(5.dp)).background(T.text.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text(k, color = T.text, fontSize = 12.sp) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Save", "Kaydet"), compact = true) {
                val rows = text.lines().filter { it.isNotBlank() }
                if (rows.isEmpty() || rows.any { KbCustomLayouts.keysOf(it).size > 14 }) note = tr("Up to 14 keys a row, at least one row", "Satır başına en çok 14 tuş, en az bir satır")
                else { KbCustomLayouts.save(ctx, editId, name, base, rows); editing = false; note = ""; rev++; save() }
            }
            PButton(tr("Cancel", "Vazgeç"), compact = true) { editing = false; note = "" }
        }
    }
    if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
}
