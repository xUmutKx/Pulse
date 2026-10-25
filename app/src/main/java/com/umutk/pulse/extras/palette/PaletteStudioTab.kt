package com.umutk.pulse.extras.palette

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PNameField
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.Finish
import com.umutk.pulse.extras.palette.core.icon.IconMode
import com.umutk.pulse.extras.palette.core.icon.StyleCatalog
import com.umutk.pulse.extras.palette.core.icon.TileShape
import com.umutk.pulse.extras.palette.core.icon.label
import com.umutk.pulse.extras.palette.core.palette.MoodPresets
import com.umutk.pulse.extras.palette.core.palette.PaletteExtractor
import com.umutk.pulse.extras.palette.core.palette.PaletteGenerator
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr

private fun enumName(n: String) = n.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** The creative page: every control writes into the look and the strip at the top redraws at once. */
@Composable
fun StudioTab(st: PaletteState) {
    val spec = st.spec
    var selected by remember { mutableStateOf(-1) }
    var saveName by remember { mutableStateOf<String?>(null) }
    var gridOrder by remember { mutableStateOf(PaletteExtractor.GridOrder.READING) }
    val gridPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) st.paletteFromGrid(uri, order = gridOrder) }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) st.paletteFromImage(uri) }
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // title, undo, redo, reset
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Studio", "Stüdyo"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(spec.palette.name + " · " + enumName(spec.mode.name), color = T.sub, fontSize = 13.sp)
            }
            ActionChip("↶", st.undoCount > 0) { st.undo() }
            Box(Modifier.size(6.dp))
            ActionChip("↷", st.redoCount > 0) { st.redo() }
            Box(Modifier.size(6.dp))
            ActionChip(tr("Reset", "Sıfırla")) { st.resetTheme() }
        }

        // the live strip
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(T.panel)) {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(st.apps.take(24), key = { it.key }) { app -> ThemedIcon(app, spec, st.engine, st.apps.indexOf(app), 54.dp, st.overrides[app.key]) }
            }
        }

        SectionTitle(tr("Mode", "Mod"))
        ChipRow {
            PChip(spec.mode == IconMode.TILES, tr("Tiles", "Karolar")) { st.update("mode") { it.copy(mode = IconMode.TILES) } }
            PChip(spec.mode == IconMode.ORIGINAL, tr("Original icons, recoloured", "Özgün ikonlar, yeniden renkli")) { st.update("mode") { it.copy(mode = IconMode.ORIGINAL) } }
            PChip(spec.mode == IconMode.STYLED, tr("Styled glyphs", "Stilli semboller")) { st.update("mode") { it.copy(mode = IconMode.STYLED) } }
        }
        if (spec.mode == IconMode.STYLED) {
            SectionTitle(tr("Style", "Stil"))
            ChipRow { StyleCatalog.styles.forEach { s -> PChip(spec.style == s.id, s.label) { st.update("style") { it.copy(style = s.id) } } } }
        }

        // the palette
        SectionTitle(tr("Palette", "Palet"))
        PCard(Modifier.fillMaxWidth()) {
            SwatchRow(spec.palette, selected) { i -> selected = if (selected == i) -1 else i }
            Box(Modifier.size(8.dp))
            LabeledSlider(tr("Number of colours: ", "Renk sayısı: ") + spec.palette.size, spec.palette.size.toFloat(), 2f..12f) { st.resizePalette(it.toInt()) }
            Box(Modifier.size(6.dp))
            ChipRow {
                ActionChip(tr("Wallpaper", "Duvar kağıdı")) { st.paletteFromWallpaper() }
                ActionChip("Material You") { st.paletteFromMaterialYou() }
                ActionChip(tr("From a photo", "Fotoğraftan")) { imgPicker.launch(image) }
                ActionChip(tr("Generate", "Üret")) { st.generatePalette(PaletteGenerator.Harmony.entries.random(), spec.palette.size) }
                ActionChip(tr("Add colour", "Renk ekle")) { st.addColor(spec.palette.lightest) }
                ActionChip(tr("Save palette", "Paleti kaydet")) { st.savePalette(spec.palette.copy(id = java.util.UUID.randomUUID().toString(), name = spec.palette.name)) }
            }
            if (st.note == "no-wallpaper") HintText(tr("The wallpaper could not be read.", "Duvar kağıdı okunamadı."))
            if (st.note == "no-you") HintText(tr("Material You colours need Android 12 or newer.", "Material You renkleri Android 12 ve üstü ister."))
            if (selected in spec.palette.colors.indices) {
                Box(Modifier.size(12.dp))
                ColorPicker(spec.palette.colors[selected]) { c -> st.setColor(selected, c) }
                Box(Modifier.size(8.dp))
                ChipRow {
                    ActionChip(tr("Remove", "Kaldır")) { st.removeColor(selected); selected = -1 }
                    ActionChip(tr("Harmony from this", "Bundan uyum üret")) { st.paletteFromSeed(spec.palette.colors[selected]) }
                }
            }
        }

        SectionTitle(tr("Presets", "Hazır paletler"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items((MoodPresets.all + st.savedPalettes).distinctBy { it.id }, key = { it.id }) { p -> PaletteCard(p, p.id == spec.palette.id) { st.applyPalette(p) } }
        }
        if (st.savedPalettes.any { it.id == spec.palette.id }) ChipRow { ActionChip(tr("Delete this saved palette", "Kayıtlı paleti sil")) { st.deletePalette(spec.palette.id) } }
        if (st.suggested.isNotEmpty()) {
            HintText(tr("Close to your photo", "Fotoğrafınıza yakın olanlar"))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(st.suggested, key = { "s" + it.id }) { p -> PaletteCard(p, p.id == spec.palette.id) { st.applyPalette(p) } } }
        }

        // whole looks
        SectionTitle(tr("Saved looks", "Kayıtlı görünümler"))
        HintText(tr("Icon style, wallpaper and widgets bundled together. A tap on a saved look switches all of them.", "Ikon stili, duvar kağıdı ve widget'lar bir arada. Kayıtlı bir görünüme dokunmak hepsini değiştirir."))
        ChipRow {
            ActionChip(tr("Save current look", "Bu görünümü kaydet")) { saveName = "" }
            st.savedLooks.forEach { look ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PChip(look.id == spec.id, look.name) { st.applyLook(look) }
                    Box(Modifier.size(28.dp).clickable { st.deleteLook(look.id) }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 14.dp) }
                }
            }
        }
        saveName?.let { n ->
            WinAlert(onDismissRequest = { saveName = null }, title = { Text(tr("Save current look", "Bu görünümü kaydet")) },
                text = { PNameField(n, { saveName = it }, tr("Name", "Ad"), Modifier.fillMaxWidth()) },
                confirmButton = { PButton(tr("Save", "Kaydet"), compact = true) { if (n.isNotBlank()) { st.saveCurrentAsLook(n.trim()); saveName = null } } },
                dismissButton = { PButton(tr("Cancel", "Vazgeç"), compact = true) { saveName = null } })
        }

        SectionTitle(tr("Shape", "Şekil"))
        ChipRow { TileShape.entries.forEach { s -> PChip(spec.shape == s, enumName(s.name)) { st.update("shape") { it.copy(shape = s) } } } }
        SectionTitle(tr("Finish", "Yüzey"))
        ChipRow { Finish.entries.forEach { f -> PChip(spec.finish == f, enumName(f.name)) { st.update("finish") { it.copy(finish = f) } } } }

        SectionTitle(tr("Colour mapping", "Renk dağılımı"))
        ChipRow { ColorAssignment.entries.forEach { a -> PChip(spec.assignment == a, a.label()) { st.update("assignment") { it.copy(assignment = a) } } } }
        if (spec.assignment == ColorAssignment.SINGLE) {
            HintText(tr("The one colour", "Tek renk"))
            ChipRow { spec.palette.colors.forEachIndexed { i, c -> Dot(c, spec.singleColorIndex == i) { st.update("single") { s -> s.copy(singleColorIndex = i) } } } }
        }
        if (spec.assignment == ColorAssignment.GRID) {
            PCard(Modifier.fillMaxWidth()) {
                HintText(tr("Upload a screenshot of your home screen. Palette samples its colours and hands them out to your apps in the order below: it cannot know which icon sat where, only the colours and their order.", "Ana ekranınızın ekran görüntüsünü yükleyin. Palette renklerini örnekler ve aşağıdaki sırayla uygulamalarınıza dağıtır: hangi ikonun nerede durduğunu bilemez, yalnız renkleri ve sırasını."))
                Box(Modifier.size(8.dp))
                ChipRow {
                    PChip(gridOrder == PaletteExtractor.GridOrder.READING, tr("Reading order", "Okuma sırası")) { gridOrder = PaletteExtractor.GridOrder.READING }
                    PChip(gridOrder == PaletteExtractor.GridOrder.DIAGONAL, tr("Diagonal", "Çapraz")) { gridOrder = PaletteExtractor.GridOrder.DIAGONAL }
                    ActionChip(tr("Upload home screen photo", "Ana ekran fotoğrafı yükle")) { gridPicker.launch(image) }
                }
            }
        }
        SectionTitle(tr("Background colour", "Zemin rengi"))
        ChipRow {
            PChip(spec.backgroundIndex < 0, tr("Auto", "Otomatik")) { st.update("bg") { it.copy(backgroundIndex = -1) } }
            spec.palette.colors.forEachIndexed { i, c -> Dot(c, spec.backgroundIndex == i) { st.update("bg") { s -> s.copy(backgroundIndex = i) } } }
        }

        SectionTitle(tr("Fine tuning", "İnce ayar"))
        PCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LabeledSlider(tr("Corner radius", "Köşe yarıçapı"), spec.cornerRadius, 0f..0.5f) { v -> st.update("corner") { it.copy(cornerRadius = v) } }
                LabeledSlider(tr("Icon size", "İkon boyutu"), spec.iconScale, 0.5f..1f) { v -> st.update("scale") { it.copy(iconScale = v) } }
                LabeledSlider(tr("Glyph size", "Sembol boyutu"), spec.glyphScale, 0.25f..0.8f) { v -> st.update("glyph") { it.copy(glyphScale = v) } }
                LabeledSlider(tr("Gradient", "Gradyan"), spec.gradientIntensity) { v -> st.update("gradient") { it.copy(gradientIntensity = v) } }
                LabeledSlider(tr("Gradient angle", "Gradyan açısı"), spec.gradientAngle, 0f..360f) { v -> st.update("angle") { it.copy(gradientAngle = v) } }
                LabeledSlider(tr("Shadow", "Gölge"), spec.shadow) { v -> st.update("shadow") { it.copy(shadow = v) } }
                LabeledSlider(tr("Inner shadow", "İç gölge"), spec.innerShadow) { v -> st.update("inner") { it.copy(innerShadow = v) } }
                LabeledSlider(tr("Glow", "Parlama"), spec.glow) { v -> st.update("glow") { it.copy(glow = v) } }
                LabeledSlider(tr("Stroke", "Çizgi"), spec.stroke) { v -> st.update("stroke") { it.copy(stroke = v) } }
                LabeledSlider(tr("Stroke lightness", "Çizgi açıklığı"), spec.strokeLightness) { v -> st.update("strokeL") { it.copy(strokeLightness = v) } }
                LabeledSlider(tr("Noise", "Gren"), spec.noise) { v -> st.update("noise") { it.copy(noise = v) } }
                LabeledSlider(tr("Opacity", "Saydamlık"), spec.opacity, 0.2f..1f) { v -> st.update("opacity") { it.copy(opacity = v) } }
                LabeledSlider(tr("Squircle curve", "Squircle eğrisi"), spec.squircleExponent, 2f..10f) { v -> st.update("squircle") { it.copy(squircleExponent = v) } }
                LabeledSlider(tr("Colour randomness", "Renk rastgeleliği"), spec.randomness) { v -> st.update("randomness") { it.copy(randomness = v) } }
                LabeledSlider(tr("Brand hue retention", "Marka tonunu koru"), spec.hueRetention) { v -> st.update("hue") { it.copy(hueRetention = v) } }
                LabeledSlider(tr("Chroma", "Doygunluk"), spec.chroma, 0f..1.6f) { v -> st.update("chroma") { it.copy(chroma = v) } }
                LabeledSlider(tr("Contrast", "Kontrast"), spec.contrast, 0.5f..1.8f) { v -> st.update("contrast") { it.copy(contrast = v) } }
                LabeledSlider(tr("Brightness", "Parlaklık"), spec.brightness, -0.3f..0.3f) { v -> st.update("brightness") { it.copy(brightness = v) } }
                LabeledSlider(tr("Tile spacing", "Karo aralığı"), spec.tileSpacing, 0.5f..2f) { v -> st.update("spacing") { it.copy(tileSpacing = v) } }
                LabeledSlider(tr("Home screen columns: ", "Ana ekran sütunları: ") + spec.gridColumns, spec.gridColumns.toFloat(), 3f..7f) { v -> st.update("cols") { it.copy(gridColumns = v.toInt()) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Day and night", "Gündüz ve gece"), color = T.text, fontSize = 15.sp)
                        HintText(tr("Darker at night, lighter by day (set up in Settings)", "Geceleri koyu, gündüz açık (Ayarlar'da kurulur)"))
                    }
                    PSwitch(spec.dayNight, { v -> st.update("daynight") { it.copy(dayNight = v) } })
                }
            }
        }
        Box(Modifier.size(24.dp))
    }
}

@Composable
private fun Dot(c: Int, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c)).border(if (on) 3.dp else 1.dp, if (on) T.text else T.sub.copy(alpha = .4f), CircleShape).clickable { onClick() })
}
