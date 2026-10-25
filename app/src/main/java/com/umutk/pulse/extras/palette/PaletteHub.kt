package com.umutk.pulse.extras.palette

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.IconMode
import com.umutk.pulse.extras.palette.core.icon.StyleCatalog
import com.umutk.pulse.extras.palette.core.icon.TileShape
import com.umutk.pulse.extras.palette.core.icon.label
import com.umutk.pulse.extras.palette.core.palette.MoodPresets
import com.umutk.pulse.extras.palette.core.palette.PaletteExtractor
import com.umutk.pulse.extras.palette.core.palette.PaletteGenerator
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Palette: pick a palette (a mood, the wallpaper, Material You, or a random one), a style and how colours go to apps; the icons of the phone show at once in that look.
 * Saving makes the look available as an icon pack through a content provider, and the icons can be saved as pictures.
 */
@Composable
fun PaletteHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var spec by remember { mutableStateOf(PaletteStudio.spec(ctx)) }
    var note by remember { mutableStateOf("") }
    val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.IO) { PaletteStudio.apps(ctx).load() } }
    val engine = remember { PaletteStudio.engine(ctx) }
    androidx.compose.runtime.LaunchedEffect(apps) { if (apps.isNotEmpty()) engine.prepareLuminanceRanks(apps) }
    BackHandler { onBack() }
    val fg = T.text; val sub = T.sub

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", fg, 22.dp, Modifier.clickable { onBack() })
            Text("Palette", color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            com.umutk.pulse.ExtraLookIcon(fg)
        }
        // the preview stays on top while the controls scroll under it: every change shows at once
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(T.panel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val shown = remember(apps) { apps.take(10) }
            shown.chunked(5).forEachIndexed { row, list ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.forEachIndexed { col, app ->
                        val idx = row * 5 + col
                        val bmp by produceState<ImageBitmap?>(null, spec.renderKey(), app.key) { value = runCatching { engine.render(app, spec, idx, 128).asImageBitmap() }.getOrNull() }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) { bmp?.let { Image(it, null, Modifier.fillMaxSize()) } }
                            Text(app.label, color = sub, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                    repeat(5 - list.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (apps.isEmpty()) Text("…", color = sub, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // colours: the palette in use, then cards of ready palettes
            Section(tr("Colours", "Renkler"), sub) {
                Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp))) { spec.palette.colors.forEach { Box(Modifier.weight(1f).fillMaxSize().background(Color(it))) } }
                Text(spec.palette.name, color = sub, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoodPresets.all.forEach { p ->
                        val on = spec.palette.id == p.id
                        Column(Modifier.width(78.dp).clip(RoundedCornerShape(12.dp)).clickable { spec = spec.copy(palette = p) }.border(if (on) 2.dp else 1.dp, if (on) T.accent else sub.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(7.dp))) { p.colors.forEach { Box(Modifier.weight(1f).fillMaxSize().background(Color(it))) } }
                            Text(p.name, color = if (on) T.accent else fg, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PChip(false, tr("Wallpaper", "Duvar kağıdı")) { scope.launch { withContext(Dispatchers.IO) { PaletteExtractor.fromWallpaper(ctx, 5) }?.let { spec = spec.copy(palette = it) } ?: run { note = tr("The wallpaper could not be read.", "Duvar kağıdı okunamadı.") } } }
                    PChip(false, "Material You") { scope.launch { withContext(Dispatchers.IO) { PaletteExtractor.materialYou(ctx, 5) }?.let { spec = spec.copy(palette = it) } ?: run { note = tr("Material You colours need Android 12 or newer.", "Material You renkleri Android 12 ve üstü ister.") } } }
                    PChip(false, tr("Random", "Rastgele")) { spec = spec.copy(palette = PaletteGenerator.generate(PaletteGenerator.Recipe(harmony = PaletteGenerator.Harmony.values().random()))) }
                }
            }

            // the look: a small sample icon of each style
            Section(tr("Look", "Görünüm"), sub) {
                val sample = apps.firstOrNull()
                data class Choice(val key: String, val name: String, val on: Boolean, val pick: () -> Unit, val mode: IconMode, val style: String)
                val choices = listOf(
                    Choice("tiles", tr("Plain tiles", "Düz karolar"), spec.mode == IconMode.TILES, { spec = spec.copy(mode = IconMode.TILES) }, IconMode.TILES, spec.style),
                    Choice("orig", tr("Recoloured", "Yeniden renkli"), spec.mode == IconMode.ORIGINAL, { spec = spec.copy(mode = IconMode.ORIGINAL) }, IconMode.ORIGINAL, spec.style),
                ) + StyleCatalog.styles.map { st -> Choice("s_" + st.id, st.label, spec.mode == IconMode.STYLED && spec.style == st.id, { spec = spec.copy(mode = IconMode.STYLED, style = st.id) }, IconMode.STYLED, st.id) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    choices.forEach { c ->
                        val bmp by produceState<ImageBitmap?>(null, spec.palette.id, c.key, sample?.key, spec.shape, spec.assignment) {
                            value = if (sample == null) null else runCatching { engine.render(sample, spec.copy(mode = c.mode, style = c.style), 0, 112).asImageBitmap() }.getOrNull()
                        }
                        Column(Modifier.width(78.dp).clip(RoundedCornerShape(12.dp)).clickable { c.pick() }.border(if (c.on) 2.dp else 1.dp, if (c.on) T.accent else sub.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { bmp?.let { Image(it, null, Modifier.fillMaxSize()) } }
                            Text(c.name, color = if (c.on) T.accent else fg, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                if (spec.mode == IconMode.TILES) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileShape.values().forEach { sh -> PChip(spec.shape == sh, sh.name.lowercase().replace('_', ' ')) { spec = spec.copy(shape = sh) } }
                }
            }

            Section(tr("Colour of each app", "Her uygulamanın rengi"), sub) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ColorAssignment.LUMINANCE, ColorAssignment.HASH, ColorAssignment.DOMINANT, ColorAssignment.ORDER, ColorAssignment.CATEGORY, ColorAssignment.SINGLE).forEach { a ->
                        PChip(spec.assignment == a, a.label()) { spec = spec.copy(assignment = a) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                PButton(tr("Save as icon pack", "İkon paketi olarak kaydet"), compact = true) {
                    PaletteStudio.save(ctx, spec)
                    note = tr("Saved. Pick \"Pulse Palette\" as the icon pack in a launcher that reads packs from a content provider.", "Kaydedildi. Paketi içerik sağlayıcıdan okuyan bir başlatıcıda \"Pulse Palette\" olarak seçin.")
                }
                PButton(tr("Save icons as pictures", "İkonları resim olarak kaydet"), compact = true) {
                    scope.launch {
                        note = tr("Drawing the icons…", "İkonlar çiziliyor…")
                        PaletteStudio.save(ctx, spec)
                        val n = withContext(Dispatchers.IO) { runCatching { PaletteExport.zip(ctx, apps, spec) }.getOrDefault(-1) }
                        note = if (n >= 0) tr("$n icons saved in Downloads/Pulse-Palette.zip", "$n ikon Downloads/Pulse-Palette.zip içine kaydedildi") else tr("Could not save", "Kaydedilemedi")
                    }
                }
            }
            if (note.isNotEmpty()) Text(note, color = sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        // the main action stays at the bottom
        PButton(tr("Use in the Pulse launcher", "Pulse başlatıcıda kullan"), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            PaletteStudio.save(ctx, spec)
            com.umutk.pulse.extras.launcher.LauncherPrefs.iconPack = "pulse:palette"
            com.umutk.pulse.extras.launcher.LauncherPrefs.save(ctx)
            note = tr("The Pulse launcher now draws its icons in this look.", "Pulse başlatıcı artık ikonlarını bu görünümde çiziyor.")
        }
    }
}

@Composable
private fun Section(title: String, sub: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}
