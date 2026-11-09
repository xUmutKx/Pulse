package com.umutk.pulse.extras.ai.cc

import com.umutk.pulse.R
import com.umutk.pulse.tr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun PrefSlider(label: String, pref: Prefs.S, def: Int, range: IntRange, unit: String = "") {
    val saved by pref.flow.collectAsState()
    var v by remember { mutableFloatStateOf((saved.toIntOrNull() ?: def).coerceIn(range).toFloat()) }
    Text("$label: ${v.toInt()}$unit", style = MaterialTheme.typography.bodyLarge)
    Slider(v, { v = it }, valueRange = range.first.toFloat()..range.last.toFloat(), onValueChangeFinished = { pref.value = v.toInt().toString() })
}

@Composable
private fun PrefSwitch(label: String, pref: Prefs.S) {
    val on by pref.flow.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(on == "1", { pref.value = if (it) "1" else "0" })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrefChips(pref: Prefs.S, options: List<Pair<String, String>>) {
    val cur by pref.flow.collectAsState()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (k, l) -> FilterChip(cur == k, { pref.value = k }, { Text(l) }) } }
}

@Composable
private fun Sub(t: String) = Text(t, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

/** Settings of the "Buddy" overlay: the mascot that roams over every app and talks in a speech bubble. */
@Composable
fun BuddySettings() {
    Sub(tr("Mascot", "Maskot"))
    PrefSlider(tr("Size", "Boyut"), Prefs.buddySize, 76, 40..180, " dp")
    PrefSlider(tr("Opacity", "Saydamlık"), Prefs.buddyOpacity, 100, 20..100, "%")
    Text(tr("Tap on the mascot", "Maskota dokununca"), style = MaterialTheme.typography.bodyLarge)
    PrefChips(Prefs.buddyTap, listOf("bubble" to tr("Chat bubble", "Sohbet balonu"), "chat" to tr("Open the app", "Uygulamayı aç"), "poke" to tr("Poke it", "Dürt")))
    Text(tr("The chat bubble opens a small chat over whatever you are doing; Back closes it. A long press does the other thing (poke).", "Sohbet balonu, o an yaptığın işin üstünde küçük bir sohbet açar; Geri kapatır. Uzun basış diğer işi yapar (dürtme)."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrefSwitch(tr("Idle tricks (blink, hop, look around)", "Boşta hareketler (göz kırpma, zıplama, bakınma)"), Prefs.buddyIdle)
    PrefSwitch(tr("Snap to the nearest side when dropped", "Bırakınca en yakın kenara yapış"), Prefs.buddySnap)
    PrefSwitch(tr("Gravity (it falls to the bottom)", "Yer çekimi (alta düşer)"), Prefs.buddyGravity)
    PrefSlider(tr("Bounciness", "Zıplama"), Prefs.buddyBounce, 65, 0..95, "%")
    Sub(tr("Sleep", "Uyku"))
    PrefSlider(tr("Falls asleep after (0 = never)", "Şu kadar sonra uyur (0 = hiç)"), Prefs.buddySleepAfter, 60, 0..600, " " + tr("s", "sn"))
    PrefSwitch(tr("Pull up a blanket", "Battaniye çeksin"), Prefs.buddyBlanket)
    PrefSwitch(tr("Hide while asleep (it comes back when Claude works)", "Uyurken gizlen (Claude çalışınca geri gelir)"), Prefs.buddyHideAsleep)
    Sub(tr("Speech bubble", "Konuşma balonu"))
    PrefSwitch(tr("Show the speech bubble", "Konuşma balonunu göster"), Prefs.buddyBubble)
    if (Prefs.buddyBubble.flow.collectAsState().value == "1") {
        PrefSwitch(tr("Say what Claude is doing while it works", "Claude çalışırken ne yaptığını söylesin"), Prefs.buddyBubbleWork)
        Text(tr("Shape", "Biçim"), style = MaterialTheme.typography.bodyLarge)
        Sub(tr("Camera ring", "Kamera halkası"))
        PrefChips(Prefs.ringStyle, listOf("new" to tr("New", "Yeni"), "old" to tr("Old", "Eski")))
        PrefChips(Prefs.buddyBubbleShape, listOf("pill" to tr("Pill", "Hap"), "soft" to tr("Soft", "Yumuşak"), "sharp" to tr("Sharp", "Köşeli")))
        Text(tr("Colour", "Renk"), style = MaterialTheme.typography.bodyLarge)
        PrefChips(Prefs.buddyBubbleTone, listOf("light" to tr("Light", "Açık"), "dark" to tr("Dark", "Koyu"), "orange" to tr("Orange", "Turuncu")))
        PrefSwitch(tr("Little tail pointing at the mascot", "Maskotu gösteren küçük kuyruk"), Prefs.buddyTail)
        PrefSlider(tr("Text size", "Yazı boyutu"), Prefs.buddyBubbleSize, 13, 9..24, " sp")
        PrefSlider(tr("Width", "Genişlik"), Prefs.buddyBubbleWidth, 230, 120..360, " dp")
        PrefSlider(tr("Lines", "Satır sayısı"), Prefs.buddyBubbleLines, 5, 1..12)
        PrefSlider(tr("Opacity", "Saydamlık"), Prefs.buddyBubbleOpacity, 96, 30..100, "%")
        PrefSlider(tr("Stays after an answer (0 = until tapped)", "Cevaptan sonra kalma süresi (0 = dokunana kadar)"), Prefs.buddyBubbleHold, 12, 0..60, " " + tr("s", "sn"))
    }
}
