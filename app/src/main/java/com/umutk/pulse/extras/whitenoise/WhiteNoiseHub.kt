package com.umutk.pulse.extras.whitenoise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalOpenTool
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import com.umutk.pulse.extras.ambient.AmbientLast
import com.umutk.pulse.extras.ambient.AmbientMixer
import com.umutk.pulse.extras.ambient.Snd
import com.umutk.pulse.extras.store.StoreCatalog
import com.umutk.pulse.extras.store.formatMb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val CATS = listOf("nature", "water", "fire", "city", "cozy", "noise", "instruments", "birds")

private fun catName(c: String) = when (c) {
    "all" -> tr("All", "Tümü"); "nature" -> tr("Nature", "Doğa"); "water" -> tr("Water", "Su"); "fire" -> tr("Fire", "Ateş")
    "city" -> tr("City", "Şehir"); "cozy" -> tr("Cozy", "Rahat"); "noise" -> tr("Noise", "Gürültü")
    "instruments" -> tr("Instruments", "Enstrüman"); "birds" -> tr("Birds", "Kuşlar"); else -> c
}

/** White noise and ambient mixer. The sound lives in [AmbientMixer] + the foreground service, so closing the page does not stop it. */
@Composable
fun WhiteNoiseHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val openTool = LocalOpenTool.current
    val sounds by AmbientMixer.sounds.collectAsState()
    val levels by AmbientMixer.levels.collectAsState()
    val master by AmbientMixer.master.collectAsState()
    val running by AmbientMixer.running.collectAsState()
    var user by remember { mutableStateOf(UserPresets.load(ctx)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf("all") }
    var name by remember { mutableStateOf("") }
    // (pack label, size) of packs that are not installed
    var missing by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) { AmbientMixer.scan(ctx) }
        AmbientMixer.ensure(ctx, found)
        // First run: the calm preset is the pre-selected mix (levels only, nothing starts until play).
        if (AmbientMixer.levels.value.isEmpty()) {
            val last = AmbientLast.load(ctx)
            if (last.isEmpty()) selected = DefaultPreset.name
            AmbientMixer.levels.value = last.ifEmpty { DefaultPreset.levels }
        }
        val mods = withContext(Dispatchers.IO) { runCatching { StoreCatalog.load(ctx) }.getOrDefault(emptyList()) }
        val m = ArrayList<Pair<String, String>>()
        if (found.none { it.cat != "birds" && it.id !in WhiteNoiseSounds.map { s -> s.id } }) {
            val pk = mods.filter { it.kategori == "ses" && it.id.contains("ambient") }
            m += (pk.firstOrNull()?.name() ?: tr("Ambient sounds", "Ortam sesleri")) to formatMb(pk.sumOf { it.boyut }.takeIf { pk.isNotEmpty() })
        }
        if (found.none { it.cat == "birds" }) {
            val pk = mods.filter { it.kategori == "kus" }
            m += (pk.firstOrNull()?.name() ?: tr("Birds", "Kuşlar")) to formatMb(pk.sumOf { it.boyut }.takeIf { pk.isNotEmpty() })
        }
        missing = m
    }
    BackHandler(onBack = onBack)

    val cats = listOf("all") + CATS.filter { c -> sounds.any { it.cat == c } }
    val shown = sounds.filter { cat == "all" || it.cat == cat }
        .sortedBy { CALM_ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }
    val playing = running.isNotEmpty()
    val nameTaken = name.isBlank() || (BuiltInPresets + user).any { it.name.equals(name.trim(), true) }

    fun apply(p: AmbientPreset) { selected = p.name; AmbientMixer.applyMix(p.levels) }
    fun saveUser(list: List<AmbientPreset>) { user = list; UserPresets.save(ctx, list) }

    LazyColumn(
        Modifier.fillMaxSize().background(T.bg).padding(horizontal = pageGutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("back", T.text, 22.dp, Modifier.clickable { onBack() })
                Text(tr("White noise", "Beyaz gürültü"), color = T.text, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                com.umutk.pulse.extras.watchdog.BatteryExemptionButton()
                PIcon(if (playing) "pause" else "play", T.accent, 28.dp, Modifier.clickable {
                    if (playing) AmbientMixer.stopAll()
                    else AmbientMixer.applyMix(
                        (BuiltInPresets + user).firstOrNull { it.name == selected }?.levels
                            ?: AmbientLast.load(ctx).ifEmpty { DefaultPreset.levels }
                    )
                })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PIcon("volume", T.text, 22.dp)
                PSlider(master.toFloat(), { AmbientMixer.setMaster(it.roundToInt()) }, 0f..100f, modifier = Modifier.weight(1f)) {}
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (user + BuiltInPresets).forEach { p ->
                    PChip(selected == p.name, if (p.user) p.name else tr(p.en, p.tr)) { apply(p) }
                }
            }
        }
        item {
            val sel = user.firstOrNull { it.name == selected }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicTextField(
                    name, { name = it.take(40) }, singleLine = true,
                    textStyle = TextStyle(color = T.text, fontSize = 14.sp), cursorBrush = SolidColor(T.text),
                    modifier = Modifier.weight(1f).border(1.dp, T.line).padding(horizontal = 8.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (name.isEmpty()) Text(tr("Preset name", "Ön ayar adı"), color = T.sub, fontSize = 14.sp)
                        inner()
                    },
                )
                // save the playing mix (or the current levels when nothing plays) under the typed name
                PIcon("check", if (nameTaken || user.size >= 50) T.sub else T.accent, 24.dp, Modifier.clickable(enabled = !nameTaken && user.size < 50) {
                    val mix = AmbientMixer.mix().ifEmpty { levels.filterValues { it > 0 } }
                    val n = name.trim()
                    if (mix.isNotEmpty()) { saveUser(user + AmbientPreset(n, n, n, mix, true)); selected = n; name = "" }
                })
                if (sel != null) {
                    PIcon("rename", if (nameTaken) T.sub else T.text, 22.dp, Modifier.clickable(enabled = !nameTaken) {
                        val n = name.trim()
                        saveUser(user.map { if (it.name == sel.name) AmbientPreset(n, n, n, it.levels, true) else it }); selected = n; name = ""
                    })
                    PIcon("delete", T.text, 22.dp, Modifier.clickable { saveUser(user - sel); selected = null })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEach { c -> PChip(cat == c, catName(c)) { cat = c } }
            }
        }
        if (cat == "birds") item {
            val ids = shown.map { it.id }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("check", T.accent, 24.dp, Modifier.clickable { AmbientMixer.setMany(ids, true) })
                PIcon("close", T.text, 24.dp, Modifier.clickable { AmbientMixer.setMany(ids, false) })
            }
        }
        items(shown, key = { it.id }) { s -> SoundRow(s, levels[s.id] ?: 50, s.id in running) }
        items(missing) { (label, size) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(size, color = T.sub, fontSize = 13.sp)
                PIcon("download", T.accent, 24.dp, Modifier.clickable { openTool("extrastore") })
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
    }
}

@Composable
private fun SoundRow(s: Snd, level: Int, on: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PIcon("volume", T.text, 22.dp)
        Column(Modifier.weight(1f)) {
            Text(tr(s.en, s.tr), color = T.text, fontSize = 15.sp)
            PSlider(level.toFloat(), { AmbientMixer.setLevel(s.id, it.roundToInt()) }, 0f..100f, modifier = Modifier.fillMaxWidth()) {}
        }
        PSwitch(on = on, onChange = { AmbientMixer.setEnabled(s.id, it) })
    }
}
