package com.umutk.pulse.extras.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    // the live keyboard at the bottom is shown only when asked for (the button at the top), so it does not take the room of the settings
    var showKb by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var dataNote by remember { mutableStateOf("") }
    val exportData = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { KeyboardBackup.export(ctx, uri) }.getOrDefault(-1) }
            dataNote = if (n >= 0) tr("Saved ($n files)", "Kaydedildi ($n dosya)") else tr("Could not save", "Kaydedilemedi")
        }
    }
    val importData = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) { runCatching { KeyboardBackup.import(ctx, uri) }.getOrDefault(-1) }
            dataNote = if (n >= 0) tr("Loaded ($n files)", "Yüklendi ($n dosya)") else tr("This is not a keyboard backup", "Bu bir klavye yedeği değil")
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
        }

        // themes
        KbSection("palette", tr("Theme", "Tema")) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyboardThemes.ids.forEach { id -> ThemeTile(id, dark, KeyboardPrefs.theme == id) { KeyboardPrefs.theme = id; save() } }
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
            Switch(tr("Direction keys", "Yön tuşları"), tr("Nine big keys: drag a key towards a character to type it", "Dokunun veya bir karaktere doğru kaydırın: dokuz büyük tuş"), KeyboardPrefs.dirLayout) { KeyboardPrefs.dirLayout = it; save() }
            Switch(tr("Extra button", "Ek tuş"), tr("A compact bottom row with a Shift key at the left", "Solda Shift tuşu olan, daha sıkışık alt satır"), KeyboardPrefs.bottomShift) { KeyboardPrefs.bottomShift = it; save() }
            Switch(tr("Floating keyboard", "Yüzen klavye"), tr("A narrower keyboard that slides sideways by the handle on top", "Üstteki tutamakla yana kayan, daha dar klavye"), KeyboardPrefs.floating) { KeyboardPrefs.floating = it; save() }
            Switch(tr("Large key text", "Büyük tuş yazısı"), "", KeyboardPrefs.largeText) { KeyboardPrefs.largeText = it; save() }
            Switch(tr("Key pop-ups", "Tuş balonları"), "", KeyboardPrefs.keyPopups) { KeyboardPrefs.keyPopups = it; save() }
            Switch(tr("Long-press symbols", "Uzun basınca semboller"), tr("Accents and digits", "Aksanlar ve rakamlar"), KeyboardPrefs.longPressSymbols) { KeyboardPrefs.longPressSymbols = it; save() }
            Switch(tr("Language on the space key", "Boşluk tuşunda dil adı"), "", KeyboardPrefs.spaceLabel) { KeyboardPrefs.spaceLabel = it; save() }
        }

        // swipes, extra keys and the toolbar
        KbSection("keyboard", tr("Swipes and keys", "Kaydırma ve tuşlar")) {
            Switch(tr("Swipe typing", "Kaydırarak yazma"), tr("Slide over the letters to type a word", "Harflerin üstünde kaydırarak kelime yaz"), KeyboardPrefs.glide) { KeyboardPrefs.glide = it; save() }
            Switch(tr("Flick up for the symbol", "Yukarı kaydırınca sembol"), tr("A short flick up on a key types its first long-press character", "Tuşu kısaca yukarı kaydırmak uzun basış karakterini yazar"), KeyboardPrefs.flick) { KeyboardPrefs.flick = it; save() }
            Switch(tr("Animations", "Animasyonlar"), "", KeyboardPrefs.anim) { KeyboardPrefs.anim = it; save() }
            Switch(tr("Extra key row", "Ek tuş satırı"), tr("Esc, Tab, Ctrl, Alt, Win, arrows, F-keys", "Esc, Tab, Ctrl, Alt, Win, oklar, F tuşları"), KeyboardPrefs.proRow) { KeyboardPrefs.proRow = it; save() }
            if (KeyboardPrefs.proRow) {
                Switch(tr("Under the space row", "Boşluk satırının altında"), "", KeyboardPrefs.proBottom) { KeyboardPrefs.proBottom = it; save() }
                Switch(tr("Icons on the keys", "Tuşlarda ikon"), "", KeyboardPrefs.proIcons) { KeyboardPrefs.proIcons = it; save() }
                ItemPicker(ExtraKeys.all.map { it.id to it.label }, KeyboardPrefs.proKeys) { KeyboardPrefs.proKeys = it; save() }
                Switch(tr("Second extra row", "İkinci ek satır"), tr("Fn, Home, End, Page keys, editing keys", "Fn, Home, End, Page tuşları, düzenleme tuşları"), KeyboardPrefs.proRow2) { KeyboardPrefs.proRow2 = it; save() }
                if (KeyboardPrefs.proRow2) ItemPicker(ExtraKeys.all.map { it.id to it.label }, KeyboardPrefs.proKeys2) { KeyboardPrefs.proKeys2 = it; save() }
            }
            Text(tr("Toolbar", "Araç çubuğu"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            ItemPicker(ExtraKeys.toolbar, KeyboardPrefs.toolbarItems) { KeyboardPrefs.toolbarItems = it; save() }
            Text(tr("Menu (the three dots)", "Menü (üç nokta)"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            ItemPicker(ExtraKeys.menu, KeyboardPrefs.menuItems) { KeyboardPrefs.menuItems = it; save() }
        }

        // size
        KbSection("height", tr("Size", "Boyut")) {
            Text("${tr("Height", "Yükseklik")}: ${KeyboardPrefs.heightPct}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.heightPct - 70) / 100f, { KeyboardPrefs.heightPct = 70 + (it * 100).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Bottom space", "Alt boşluk")}: ${KeyboardPrefs.bottomPad} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.bottomPad / 60f, { KeyboardPrefs.bottomPad = (it * 60).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Gamepad dead zone", "Gamepad ölü bölge")}: ${KeyboardPrefs.padDead}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.padDead - 5) / 45f, { KeyboardPrefs.padDead = 5 + (it * 45).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Gamepad sticks height", "Gamepad joystick yüksekliği")}: ${KeyboardPrefs.padLift}", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.padLift / 40f, { KeyboardPrefs.padLift = (it * 40).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Side space", "Yan boşluk")}: ${KeyboardPrefs.sidePad} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.sidePad / 40f, { KeyboardPrefs.sidePad = (it * 40).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Space between keys", "Tuşlar arası boşluk")}: ${KeyboardPrefs.keyGap} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.keyGap / 8f, { KeyboardPrefs.keyGap = (it * 8).roundToInt() }, 0f..1f, onDone = { save() })
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("off" to tr("Full width", "Tam genişlik"), "left" to tr("Left hand", "Sol el"), "right" to tr("Right hand", "Sağ el")).forEach { (k, n) ->
                    PChip(KeyboardPrefs.oneHand == k, n) { KeyboardPrefs.oneHand = k; save() }
                }
            }
        }

        // typing
        KbSection("edit", tr("Typing", "Yazım")) {
            Switch(tr("Suggestions", "Öneriler"), tr("Three words above the keys", "Tuşların üstünde üç kelime"), KeyboardPrefs.suggestions) { KeyboardPrefs.suggestions = it; save() }
            Switch(tr("Autocorrect", "Otomatik düzeltme"), tr("Words are corrected to the middle suggestion as you type", "Yazdıkça kelime ortadaki öneriye düzeltilir"), KeyboardPrefs.autocorrect) { KeyboardPrefs.autocorrect = it; save() }
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
            Switch(tr("Quick prediction insert", "Hızlı tahmin ekleme"), tr("Space puts in the middle prediction, even before the next word is started", "Boşluk, yeni kelime başlamadan da ortadaki tahmini ekler"), KeyboardPrefs.quickPrediction) { KeyboardPrefs.quickPrediction = it; save() }
            Switch(tr("Quick period", "Hızlı nokta"), tr("Double tap on space puts in a period", "Boşluğa çift dokunuş nokta koyar"), KeyboardPrefs.quickPeriod) { KeyboardPrefs.quickPeriod = it; save() }
            Switch(tr("Auto capitalise", "Otomatik büyük harf"), tr("Capital letter at the start of a sentence", "Cümle başında büyük harf"), KeyboardPrefs.autoCap) { KeyboardPrefs.autoCap = it; save() }
            Switch(tr("Auto space", "Otomatik boşluk"), tr("A space after , ; : ! ?", "Virgül, noktalı virgül, iki nokta, ünlem ve sorudan sonra boşluk"), KeyboardPrefs.autoSpace) { KeyboardPrefs.autoSpace = it; save() }
            Switch(tr("Cursor control", "İmleç denetimi"), tr("Hold space and slide to move the cursor", "Boşluğa basılı tutup kaydırınca imleç gider"), KeyboardPrefs.cursorControl) { KeyboardPrefs.cursorControl = it; save() }
            Switch(tr("Quick delete", "Hızlı silme"), tr("Slide from the delete key to select and delete text", "Silme tuşundan kaydırarak seç ve sil"), KeyboardPrefs.quickDelete) { KeyboardPrefs.quickDelete = it; save() }
            Switch(tr("Emoji predictions", "Emoji tahminleri"), tr("Emoji on the suggestion bar", "Öneri çubuğunda emoji"), KeyboardPrefs.emojiPredictions) { KeyboardPrefs.emojiPredictions = it; save() }
            Switch(tr("Learn from what I type", "Yazdıklarımdan öğren"), tr("Not in password, email and web address fields", "Parola, e-posta ve adres alanlarında kapalıdır"), KeyboardPrefs.learn) { KeyboardPrefs.learn = it; save() }
        }

        // feel
        KbSection("effects", tr("Effects", "Efektler")) {
            @Composable fun chips(title: String, names: List<Pair<String, String>>, cur: Int, set: (Int) -> Unit) {
                Text(title, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { i, n -> PChip(cur == i, tr(n.first, n.second)) { set(i); save() } }
                }
            }
            chips(tr("Keyboard colour effect", "Klavye renk efekti"), KeyboardEffects.boardNames, KeyboardPrefs.boardFx) { KeyboardPrefs.boardFx = it }
            chips(tr("Key colour effect", "Tuş renk efekti"), KeyboardEffects.keyNames, KeyboardPrefs.keyFx) { KeyboardPrefs.keyFx = it }
            chips(tr("Key motion effect", "Tuş hareket efekti"), KeyboardEffects.motionNames, KeyboardPrefs.motionFx) { KeyboardPrefs.motionFx = it }
            chips(tr("Touch sound", "Dokunma sesi"), KeyboardEffects.soundNames, KeyboardPrefs.soundKind) { KeyboardPrefs.soundKind = it; KeyboardPrefs.sound = true; KeyboardEffects.play(it) }
        }
        KbSection("vibrate", tr("Feel", "His")) {
            Switch(tr("Vibrate on keypress", "Tuşta titreşim"), "", KeyboardPrefs.haptic) { KeyboardPrefs.haptic = it; save() }
            if (KeyboardPrefs.haptic) {
                Text("${tr("Vibration strength", "Titreşim gücü")}: ${KeyboardPrefs.hapticMs}", color = T.sub, fontSize = 12.sp)
                PSlider((KeyboardPrefs.hapticMs - 1) / 39f, { KeyboardPrefs.hapticMs = 1 + (it * 39).roundToInt() }, 0f..1f, onDone = { save() })
            }
            Text("${tr("Brightness", "Parlaklık")}: ${KeyboardPrefs.bright}%", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            PSlider((KeyboardPrefs.bright - 5) / 95f, { KeyboardPrefs.bright = 5 + (it * 95).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Brightness of the space row and extra keys", "Boşluk satırı ve ek tuşların parlaklığı")}: ${KeyboardPrefs.bottomBright}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.bottomBright - 5) / 95f, { KeyboardPrefs.bottomBright = 5 + (it * 95).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Brightness of the top bar", "Üst çubuğun parlaklığı")}: ${KeyboardPrefs.topBright}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.topBright - 5) / 95f, { KeyboardPrefs.topBright = 5 + (it * 95).roundToInt() }, 0f..1f, onDone = { save() })
            Text(tr("Moving colour light", "Hareketli renk ışığı"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to tr("Off", "Kapalı"), 1 to tr("Around the keyboard", "Klavyenin çevresi"), 2 to tr("Around every key", "Her tuşun çevresi")).forEach { (v, n) -> PChip(KeyboardPrefs.rgb == v, n) { KeyboardPrefs.rgb = v; save() } }
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

        // statistics: what the keyboard has learned and where the fingers land
        KbSection("tune", tr("Statistics", "İstatistik")) {
            remember { KeyboardDictionary.ensureUser(ctx) }
            val st2 = remember(learned, rev) { KeyboardDictionary.stats().toMap() }
            @Composable fun row(label: String, v: Int?) = Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${v ?: 0}", color = T.text, fontSize = 13.sp)
            }
            row(tr("Letters typed", "Yazılan harf"), st2["letters"])
            row(tr("Learned words", "Öğrenilen kelime"), st2["words"])
            row(tr("Learned typos", "Öğrenilen yazım hatası"), st2["typos"])
            row(tr("Undone corrections", "Geri alınan düzeltme"), st2["rejects"])
            row(tr("Word pairs", "Kelime çifti"), st2["pairs"])
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
            // everything the keyboard knows about the person in one file, and back
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

/** A card of the settings that opens and closes from its header (icon, title, chevron). */
@Composable
private fun KbSection(icon: String, title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(false) }
    PCard(Modifier.padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(icon, T.accent, 22.dp)
            Text(title, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (open) 90f else 0f })
        }
        if (open) content()
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Switch(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = T.text, fontSize = 15.sp)
            if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 12.sp)
        }
        PSwitch(on, onChange)
    }
}

/** A little picture of a theme: its background and three rows of keys. */
@Composable
private fun ThemeTile(id: String, dark: Boolean, picked: Boolean, onClick: () -> Unit) {
    val th = KeyboardThemes.of(id, dark)
    Column(Modifier.width(92.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(8.dp)).background(th.bg).border(if (picked) 2.dp else 1.dp, if (picked) T.text else T.sub.copy(alpha = .4f), RoundedCornerShape(8.dp)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { r ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(if (r == 1) 8 else 9) { i -> Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape((th.radius / 3).dp)).background(if (r == 2 && i == 8) th.accent else th.key)) }
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
