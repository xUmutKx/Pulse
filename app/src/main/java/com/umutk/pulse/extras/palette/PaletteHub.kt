package com.umutk.pulse.extras.palette

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", fg, 22.dp, Modifier.clickable { onBack() })
            Text("Palette", color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        // the colours in use
        Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(12.dp))) { spec.palette.colors.forEach { Box(Modifier.weight(1f).fillMaxSize().background(Color(it))) } }
        Text(spec.palette.name, color = sub, fontSize = 12.sp)

        Text(tr("Palette", "Palet"), color = sub, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoodPresets.all.forEach { p -> PChip(spec.palette.id == p.id, p.name) { spec = spec.copy(palette = p) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Wallpaper", "Duvar kağıdı"), compact = true) { scope.launch { withContext(Dispatchers.IO) { PaletteExtractor.fromWallpaper(ctx, 5) }?.let { spec = spec.copy(palette = it) } ?: run { note = tr("The wallpaper could not be read", "Duvar kağıdı okunamadı") } } }
            PButton("Material You", compact = true) { scope.launch { withContext(Dispatchers.IO) { PaletteExtractor.materialYou(ctx, 5) }?.let { spec = spec.copy(palette = it) } ?: run { note = tr("Material You colours need Android 12", "Material You renkleri için Android 12 gerekir") } } }
            PButton(tr("Random", "Rastgele"), compact = true) { spec = spec.copy(palette = PaletteGenerator.generate(PaletteGenerator.Recipe(harmony = PaletteGenerator.Harmony.values().random()))) }
        }

        Text(tr("Look", "Görünüm"), color = sub, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(spec.mode == IconMode.TILES, tr("Plain tiles", "Düz karolar")) { spec = spec.copy(mode = IconMode.TILES) }
            PChip(spec.mode == IconMode.ORIGINAL, tr("Recoloured icons", "Yeniden renkli ikonlar")) { spec = spec.copy(mode = IconMode.ORIGINAL) }
            StyleCatalog.styles.forEach { s -> PChip(spec.mode == IconMode.STYLED && spec.style == s.id, s.label) { spec = spec.copy(mode = IconMode.STYLED, style = s.id) } }
        }
        if (spec.mode == IconMode.TILES) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TileShape.values().forEach { sh -> PChip(spec.shape == sh, sh.name.lowercase().replace('_', ' ')) { spec = spec.copy(shape = sh) } }
        }

        Text(tr("Colour of each app", "Her uygulamanın rengi"), color = sub, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ColorAssignment.LUMINANCE, ColorAssignment.HASH, ColorAssignment.DOMINANT, ColorAssignment.ORDER, ColorAssignment.CATEGORY, ColorAssignment.SINGLE).forEach { a ->
                PChip(spec.assignment == a, a.label()) { spec = spec.copy(assignment = a) }
            }
        }

        // the preview: the first apps of the phone in this look
        Text(tr("Preview", "Önizleme"), color = sub, fontSize = 13.sp)
        val shown = remember(apps) { engine.let { apps.take(20) } }
        shown.chunked(5).forEachIndexed { row, list ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEachIndexed { col, app ->
                    val idx = row * 5 + col
                    val bmp by produceState<ImageBitmap?>(null, spec.renderKey(), app.key) { value = runCatching { engine.render(app, spec, idx, 128).asImageBitmap() }.getOrNull() }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(com.umutk.pulse.T.panel, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
                        }
                        Text(app.label, color = sub, fontSize = 10.sp, maxLines = 1)
                    }
                }
                repeat(5 - list.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            PButton(tr("Save as icon pack", "İkon paketi olarak kaydet")) {
                PaletteStudio.save(ctx, spec)
                note = tr("Saved. Pick \"Pulse Palette\" as the icon pack in a launcher that reads packs from a content provider.", "Kaydedildi. Paketi içerik sağlayıcıdan okuyan bir başlatıcıda \"Pulse Palette\" olarak seçin.")
            }
            PButton(tr("Use in the Pulse launcher", "Pulse başlatıcıda kullan")) {
                PaletteStudio.save(ctx, spec)
                com.umutk.pulse.extras.launcher.LauncherPrefs.iconPack = "pulse:palette"
                com.umutk.pulse.extras.launcher.LauncherPrefs.save(ctx)
                note = tr("The Pulse launcher now draws its icons in this look.", "Pulse başlatıcı artık ikonlarını bu görünümde çiziyor.")
            }
            PButton(tr("Save icons as pictures", "İkonları resim olarak kaydet")) {
                scope.launch {
                    note = tr("Drawing the icons…", "İkonlar çiziliyor…")
                    PaletteStudio.save(ctx, spec)
                    val n = withContext(Dispatchers.IO) { runCatching { PaletteExport.zip(ctx, apps, spec) }.getOrDefault(-1) }
                    note = if (n >= 0) tr("$n icons saved in Downloads/Pulse-Palette.zip", "$n ikon Downloads/Pulse-Palette.zip içine kaydedildi") else tr("Could not save", "Kaydedilemedi")
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = sub, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
    }
}
