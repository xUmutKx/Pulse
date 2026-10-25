package com.umutk.pulse.extras.palette

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.PSlider
import com.umutk.pulse.T
import com.umutk.pulse.extras.palette.core.color.hslOf
import com.umutk.pulse.extras.palette.core.color.hslToColor
import com.umutk.pulse.extras.palette.core.color.parseHex
import com.umutk.pulse.extras.palette.core.color.toHex
import com.umutk.pulse.extras.palette.core.icon.IconEngine
import com.umutk.pulse.extras.palette.core.icon.IconOverride
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import java.util.Locale

/** The pieces the Palette pages are built from; colours and controls come from the look the page is in. */

/** One themed icon: drawn off the main thread and read from the cache, so a slider can redraw a whole grid without a stutter. */
@Composable
fun ThemedIcon(app: AppInfo, spec: ThemeSpec, engine: IconEngine, index: Int, size: Dp = 56.dp, override: IconOverride? = null, backdrop: Bitmap? = null, onClick: (() -> Unit)? = null) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.toPx().toInt() }.coerceIn(64, 256)
    val key = app.key + spec.renderKey() + (override?.hashCode() ?: 0) + px
    val bmp by produceState<Bitmap?>(initialValue = null, key1 = key) { value = engine.render(app, spec, index, px, override, backdrop) }
    Box(Modifier.size(size).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier), contentAlignment = Alignment.Center) {
        Crossfade(targetState = bmp, label = "icon") { b ->
            if (b != null) Image(b.asImageBitmap(), app.label, Modifier.size(size), contentScale = ContentScale.Fit)
            else Box(Modifier.size(size * 0.86f).clip(RoundedCornerShape(size * 0.28f)).background(T.panel))
        }
    }
}

@Composable
fun ThemedIconWithLabel(app: AppInfo, spec: ThemeSpec, engine: IconEngine, index: Int, size: Dp = 56.dp, labelColor: Color = Color.White, showLabel: Boolean = true, override: IconOverride? = null, backdrop: Bitmap? = null, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ThemedIcon(app, spec, engine, index, size, override, backdrop, onClick)
        if (showLabel) Text(app.label, color = labelColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
    }
}

/** The palette as a strip of its colours; a tap on one selects it. */
@Composable
fun SwatchRow(palette: ColorPalette, selected: Int = -1, onSelect: (Int) -> Unit = {}, height: Int = 56) {
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(16.dp))) {
        palette.colors.forEachIndexed { i, c ->
            Box(Modifier.weight(1f).fillMaxSize().background(Color(c)).clickable { onSelect(i) }.then(if (i == selected) Modifier.border(3.dp, T.text, RoundedCornerShape(6.dp)) else Modifier))
        }
    }
}

/** A ready palette as a small card: its colours and its name. */
@Composable
fun PaletteCard(p: ColorPalette, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) T.accent.copy(alpha = .18f) else T.panel).border(if (selected) 2.dp else 0.dp, if (selected) T.accent else Color.Transparent, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.clip(RoundedCornerShape(10.dp)).height(34.dp)) { p.colors.forEach { c -> Box(Modifier.size(width = 18.dp, height = 34.dp).background(Color(c))) } }
        Text(p.name, color = if (selected) T.accent else T.text, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp).width(76.dp), textAlign = TextAlign.Center)
    }
}

/** A saturation / lightness field, a hue rail and a hex box. */
@Composable
fun ColorPicker(color: Int, onChange: (Int) -> Unit) {
    val hsl = remember(color) { hslOf(color) }
    var h by remember(color) { mutableStateOf(hsl[0]) }
    var s by remember(color) { mutableStateOf(hsl[1]) }
    var l by remember(color) { mutableStateOf(hsl[2]) }
    var hex by remember(color) { mutableStateOf(color.toHex()) }
    fun emit() { val c = hslToColor(h, s, l); hex = c.toHex(); onChange(c) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(16.dp))
            .pointerInput(h) { detectTapGestures { o -> s = (o.x / size.width).coerceIn(0f, 1f); l = 1f - (o.y / size.height).coerceIn(0f, 1f); emit() } }
            .pointerInput(h) { detectDragGestures { change, _ -> s = (change.position.x / size.width).coerceIn(0f, 1f); l = 1f - (change.position.y / size.height).coerceIn(0f, 1f); emit() } }) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color(hslToColor(h, 0f, 0.5f)), Color(hslToColor(h, 1f, 0.5f)))))
                drawRect(Brush.verticalGradient(listOf(Color.White, Color.Transparent, Color.Black)))
                drawCircle(Color.White, radius = 12f, center = Offset(s * size.width, (1f - l) * size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
            }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(26.dp).clip(CircleShape).background(Brush.horizontalGradient(List(13) { Color(hslToColor(it * 30f, 1f, 0.5f)) }))
            .pointerInput(Unit) { detectTapGestures { o -> h = (o.x / size.width).coerceIn(0f, 1f) * 360f; emit() } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> h = (c.position.x / size.width).coerceIn(0f, 1f) * 360f; emit() } })
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(hslToColor(h, s, l))))
            Box(Modifier.padding(start = 12.dp)) {
                com.umutk.pulse.PNameField(hex, { v -> hex = v; parseHex(v)?.let { c -> val n = hslOf(c); h = n[0]; s = n[1]; l = n[2]; onChange(c) } }, "HEX", Modifier.fillMaxWidth())
            }
        }
    }
}

/** A slider with its name and value above it. */
@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = T.text, fontSize = 14.sp)
            Text(String.format(Locale.US, "%.2f", value), color = T.accent, fontSize = 14.sp)
        }
        PSlider(value, onChange, range, onDone = {})
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
}

@Composable
fun HintText(text: String) { Text(text, color = T.sub, fontSize = 12.sp) }

/** A row of chips that scrolls sideways. */
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

/** A tap target that does something (not a choice): drawn as a chip that is never "on". */
@Composable
fun ActionChip(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.then(if (enabled) Modifier else Modifier.background(Color.Transparent))) { PChip(false, label) { if (enabled) onClick() } }
}
