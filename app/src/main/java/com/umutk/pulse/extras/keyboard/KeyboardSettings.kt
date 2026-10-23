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
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
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
    // the keyboard stays at the bottom, as it does on the phone, so every change shows at once
    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Keyboard", "Klavye"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
        PCard {
            Title(tr("Theme", "Tema"))
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
        PCard {
            Title(tr("Languages", "Diller"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardLayouts.all.forEach { l ->
                    val on = l.code in KeyboardPrefs.langList()
                    PChip(on, l.name) {
                        val cur = KeyboardPrefs.langList()
                        KeyboardPrefs.langs = (if (on) cur - l.code else cur + l.code).ifEmpty { cur }.joinToString(","); save()
                        // a language that is switched on without a word list gets one now
                        if (!on && !KeyboardDictionary.hasList(ctx, l.code)) scope.launch { withContext(Dispatchers.IO) { KeyboardDictionary.download(ctx, l.code) }; rev++ }
                    }
                }
            }
            Text(tr("Every language that is on corrects and suggests together: a Turkish keyboard with English on fixes both. A language without a word list downloads one.", "Açık olan her dil birlikte düzeltir ve öneri verir: İngilizce de açıksa Türkçe klavye ikisini de düzeltir. Sözlüğü olmayan dil kendi sözlüğünü indirir."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Switch(tr("Number row", "Sayı satırı"), "", KeyboardPrefs.numberRow) { KeyboardPrefs.numberRow = it; save() }
            Switch(tr("Arrow keys", "Ok tuşları"), tr("Arrows, home, end, select all, copy, paste", "Oklar, başa, sona, tümünü seç, kopyala, yapıştır"), KeyboardPrefs.arrows) { KeyboardPrefs.arrows = it; save() }
            Switch(tr("Emoji key", "Emoji tuşu"), "", KeyboardPrefs.emojiKey) { KeyboardPrefs.emojiKey = it; save() }
            Switch(tr("Large key text", "Büyük tuş yazısı"), "", KeyboardPrefs.largeText) { KeyboardPrefs.largeText = it; save() }
            Switch(tr("Key pop-ups", "Tuş balonları"), "", KeyboardPrefs.keyPopups) { KeyboardPrefs.keyPopups = it; save() }
            Switch(tr("Long-press symbols", "Uzun basınca semboller"), tr("Accents and digits", "Aksanlar ve rakamlar"), KeyboardPrefs.longPressSymbols) { KeyboardPrefs.longPressSymbols = it; save() }
            Switch(tr("Language on the space key", "Boşluk tuşunda dil adı"), "", KeyboardPrefs.spaceLabel) { KeyboardPrefs.spaceLabel = it; save() }
        }

        // swipes, extra keys and the toolbar
        PCard {
            Title(tr("Swipes and keys", "Kaydırma ve tuşlar"))
            Switch(tr("Swipe typing", "Kaydırarak yazma"), tr("Slide over the letters to type a word", "Harflerin üstünde kaydırarak kelime yaz"), KeyboardPrefs.glide) { KeyboardPrefs.glide = it; save() }
            Switch(tr("Flick up for the symbol", "Yukarı kaydırınca sembol"), tr("A short flick up on a key types its first long-press character", "Tuşu kısaca yukarı kaydırmak uzun basış karakterini yazar"), KeyboardPrefs.flick) { KeyboardPrefs.flick = it; save() }
            Switch(tr("Animations", "Animasyonlar"), "", KeyboardPrefs.anim) { KeyboardPrefs.anim = it; save() }
            Switch(tr("Extra key row", "Ek tuş satırı"), tr("Esc, Tab, Ctrl, Alt, Win, arrows, F-keys", "Esc, Tab, Ctrl, Alt, Win, oklar, F tuşları"), KeyboardPrefs.proRow) { KeyboardPrefs.proRow = it; save() }
            if (KeyboardPrefs.proRow) {
                Switch(tr("Under the space row", "Boşluk satırının altında"), "", KeyboardPrefs.proBottom) { KeyboardPrefs.proBottom = it; save() }
                ItemPicker(ExtraKeys.all.map { it.id to it.label }, KeyboardPrefs.proKeys) { KeyboardPrefs.proKeys = it; save() }
            }
            Text(tr("Toolbar", "Araç çubuğu"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            ItemPicker(ExtraKeys.toolbar, KeyboardPrefs.toolbarItems) { KeyboardPrefs.toolbarItems = it; save() }
            Text(tr("Menu (the three dots)", "Menü (üç nokta)"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            ItemPicker(ExtraKeys.menu, KeyboardPrefs.menuItems) { KeyboardPrefs.menuItems = it; save() }
        }

        // size
        PCard {
            Title(tr("Size", "Boyut"))
            Text("${tr("Height", "Yükseklik")}: ${KeyboardPrefs.heightPct}%", color = T.sub, fontSize = 12.sp)
            PSlider((KeyboardPrefs.heightPct - 70) / 100f, { KeyboardPrefs.heightPct = 70 + (it * 100).roundToInt() }, 0f..1f, onDone = { save() })
            Text("${tr("Bottom space", "Alt boşluk")}: ${KeyboardPrefs.bottomPad} dp", color = T.sub, fontSize = 12.sp)
            PSlider(KeyboardPrefs.bottomPad / 60f, { KeyboardPrefs.bottomPad = (it * 60).roundToInt() }, 0f..1f, onDone = { save() })
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
        PCard {
            Title(tr("Typing", "Yazım"))
            Switch(tr("Suggestions", "Öneriler"), tr("Three words above the keys", "Tuşların üstünde üç kelime"), KeyboardPrefs.suggestions) { KeyboardPrefs.suggestions = it; save() }
            Switch(tr("Autocorrect", "Otomatik düzeltme"), tr("Words are corrected to the middle suggestion as you type", "Yazdıkça kelime ortadaki öneriye düzeltilir"), KeyboardPrefs.autocorrect) { KeyboardPrefs.autocorrect = it; save() }
            Switch(tr("Undo autocorrect on backspace", "Geri silmede düzeltmeyi geri al"), tr("Backspace after a correction brings back what you typed", "Düzeltmeden sonra geri silmek yazdığınızı geri getirir"), KeyboardPrefs.undoOnBackspace) { KeyboardPrefs.undoOnBackspace = it; save() }
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
        PCard {
            Title(tr("Feel", "His"))
            Switch(tr("Vibrate on keypress", "Tuşta titreşim"), "", KeyboardPrefs.haptic) { KeyboardPrefs.haptic = it; save() }
            if (KeyboardPrefs.haptic) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12 to tr("Light", "Hafif"), 24 to tr("Strong", "Güçlü")).forEach { (v, n) -> PChip(KeyboardPrefs.hapticMs == v, n) { KeyboardPrefs.hapticMs = v; save() } }
            }
            Switch(tr("Sound on keypress", "Tuşta ses"), "", KeyboardPrefs.sound) { KeyboardPrefs.sound = it; save() }
        }

        // word lists for the corrections and suggestions
        PCard {
            Title(tr("Dictionaries", "Sözlükler"))
            listOf("en" to "English", "tr" to "Türkçe", "de" to "Deutsch", "fr" to "Français", "es" to "Español").forEach { (code, name) ->
                val has = remember(rev) { KeyboardDictionary.hasList(ctx, code) }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (busy == code) Text("…", color = T.sub, fontSize = 15.sp)
                    else {
                        if (has) PIcon("check", T.sub, 20.dp, Modifier.padding(end = 10.dp))
                        PButton(if (has) tr("Update", "Güncelle") else tr("Download", "İndir"), compact = true) {
                            busy = code
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { KeyboardDictionary.download(ctx, code) }
                                busy = null; rev++
                                if (!ok) android.widget.Toast.makeText(ctx, tr("Could not download", "İndirilemedi"), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            Text(tr("English and Türkçe come with the app; a download replaces them with a fresh list.", "English ve Türkçe uygulamayla gelir; indirme onları yeni bir listeyle değiştirir."), color = T.sub, fontSize = 12.sp)
        }

        // data
        PCard {
            Title(tr("Data", "Veri"))
            Text("${tr("Learned words", "Öğrenilen kelimeler")}: $learned", color = T.sub, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Forget words", "Kelimeleri unut"), compact = true, danger = true) { askForget = true }
                PButton(tr("Factory settings", "Fabrika ayarları"), compact = true, danger = true) { askReset = true }
            }
            Text(tr("Word lists from FrequencyWords (OpenSubtitles), CC BY-SA 4.0.", "Sözlükler: FrequencyWords (OpenSubtitles), CC BY-SA 4.0."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) { KeyboardView(st, acts) }
    }
    if (askForget) PDialog(tr("Forget the learned words?", "Öğrenilen kelimeler unutulsun mu?"), "", tr("Forget", "Unut"), { KeyboardDictionary.forgetUser(ctx); learned = 0; askForget = false }, { askForget = false })
    if (askReset) PDialog(tr("Back to factory settings?", "Fabrika ayarlarına dönülsün mü?"), tr("Theme, size, typing options and learned words are reset.", "Tema, boyut, yazım seçenekleri ve öğrenilen kelimeler sıfırlanır."), tr("Reset", "Sıfırla"), { KeyboardPrefs.factoryReset(ctx); learned = 0; askReset = false }, { askReset = false })
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
