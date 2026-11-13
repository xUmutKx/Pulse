package com.umutk.pulse.extras.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.tr

/**
 * The note's tool bar, the way a handwriting notes app has it: keyboard, pen, marker, eraser, lasso, undo and redo, the text row, a photo and the
 * rest of the note's actions. A tool that is already on opens its settings (the panel above the bar). Every glyph is tinted by the look.
 */
@Composable
fun InkBar(
    mode: InkMode, onMode: (InkMode) -> Unit,
    canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit,
    textOn: Boolean, onText: () -> Unit, onPhoto: () -> Unit, moreOn: Boolean, onMore: () -> Unit,
    penTint: Color = T.text, markerTint: Color = Color(0xFFFDD835),
) {
    // a floating bar: it does not touch the edges, it has a shadow and a gap under it
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
    Row(
        Modifier.shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(T.panel)
            .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        BarBtn(mode == InkMode.KEYBOARD, tr("Keyboard", "Klavye"), true, { onMode(InkMode.KEYBOARD) }) { PIcon("keyboard", it, 18.dp) }
        PenTool3D(0, penTint, mode == InkMode.PEN, tr("Pen", "Kalem")) { onMode(InkMode.PEN) }
        PenTool3D(1, markerTint, mode == InkMode.MARKER, tr("Marker", "Fosforlu kalem")) { onMode(InkMode.MARKER) }
        PenTool3D(2, Color(0xFFF48FB1), mode == InkMode.ERASER, tr("Eraser", "Silgi")) { onMode(InkMode.ERASER) }
        BarBtn(mode == InkMode.LASSO, tr("Lasso", "Kement"), true, { onMode(InkMode.LASSO) }) { LassoGlyph(it) }
        BarBtn(false, tr("Undo", "Geri al"), canUndo, onUndo) { PIcon("undo", it, 18.dp) }
        BarBtn(false, tr("Redo", "Yinele"), canRedo, onRedo) { PIcon("redo", it, 18.dp) }
        BarBtn(textOn, tr("Text", "Yazı"), true, onText) { Text("Aa", color = it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        BarBtn(false, tr("Add photo", "Fotoğraf ekle"), true, onPhoto) { PIcon("image", it, 18.dp) }
        BarBtn(moreOn, tr("More", "Diğer"), true, onMore) { PIcon(if (moreOn) "close" else "add", it, 18.dp) }
    }
    }
}

/**
 * A tool drawn as an upright pen with a round body (shaded across, like a cylinder, with a bright streak) and a coloured tip, as the pen tray of a
 * notes app shows them: the one that is on stands up out of the bar. [kind]: 0 pen, 1 marker (a chisel tip), 2 eraser (a soft block).
 */
@Composable
private fun PenTool3D(kind: Int, tint: Color, on: Boolean, desc: String, onClick: () -> Unit) {
    // the pen that is on springs up out of the bar (a little overshoot), and every pen sinks a little while it is pressed
    val lift by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = .45f, stiffness = 380f), label = "penLift")
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) .88f else 1f, androidx.compose.animation.core.spring(dampingRatio = .5f, stiffness = 600f), label = "penPress")
    Box(Modifier.width(26.dp).height(44.dp).graphicsLayer { scaleX = press; scaleY = press; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(.5f, 1f) }
        .clickable(interactionSource = src, indication = null) { onClick() }.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val up = lift * 7.dp.toPx()
            val bw = w * .62f; val x0 = (w - bw) / 2f
            val top = h * .36f - up
            val bodyH = h - top + 8.dp.toPx()
            val shade = Brush.horizontalGradient(listOf(Color(0xFF7C7C82), Color(0xFFF2F2F7), Color(0xFFCFCFD4), Color(0xFF86868C)), startX = x0, endX = x0 + bw)
            val r = bw * .22f
            // the body
            drawRoundRect(shade, Offset(x0, top), Size(bw, bodyH), CornerRadius(r, r))
            // the bright streak down the round body
            drawRoundRect(Color.White.copy(alpha = .55f), Offset(x0 + bw * .2f, top + 6.dp.toPx()), Size(bw * .1f, bodyH - 12.dp.toPx()), CornerRadius(bw * .05f, bw * .05f))
            // the ring between body and tip, and the tip in the colour of the ink
            val ring = Brush.horizontalGradient(listOf(tint.copy(alpha = .55f), Color.White.copy(alpha = .8f), tint.copy(alpha = .9f)), startX = x0, endX = x0 + bw)
            drawRect(ring, Offset(x0, top), Size(bw, h * .06f))
            val tip = Path()
            when (kind) {
                0 -> { tip.moveTo(x0 + bw * .1f, top); tip.lineTo(w / 2f, top - h * .27f); tip.lineTo(x0 + bw * .9f, top); tip.close() }
                1 -> { tip.moveTo(x0 + bw * .08f, top); tip.lineTo(x0 + bw * .24f, top - h * .17f); tip.lineTo(x0 + bw * .76f, top - h * .17f); tip.lineTo(x0 + bw * .92f, top); tip.close() }
                else -> { tip.moveTo(x0, top); tip.lineTo(x0, top - h * .13f); tip.lineTo(x0 + bw, top - h * .13f); tip.lineTo(x0 + bw, top); tip.close() }
            }
            val tipBrush = Brush.horizontalGradient(listOf(tint.copy(alpha = .75f), tint, tint.copy(alpha = .85f)), startX = x0, endX = x0 + bw)
            drawPath(tip, tipBrush)
            drawLine(Color.White.copy(alpha = .45f), Offset(x0 + bw * .32f, top - h * .02f), Offset(x0 + bw * .42f, top - if (kind == 0) h * .15f else h * .09f), strokeWidth = bw * .08f, cap = StrokeCap.Round)
        }
    }
}

/** One round button of the bar: the tool that is on has the accent and a soft disc; a button that cannot be used is dim. */
@Composable
private fun BarBtn(on: Boolean, desc: String, enabled: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val tint = if (!enabled) T.text.copy(alpha = .3f) else if (on) T.accent else T.text
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (on) T.accent.copy(alpha = .16f) else Color.Transparent)
            .clickable(enabled = enabled) { onClick() }.semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { content(tint) }
}

/** The settings of the tool that is on: the pen types, the size, the colours; the eraser's size; whether a finger draws. */
@Composable
fun InkPanel(
    mode: InkMode, style: Int, onStyle: (Int) -> Unit, color: Int, onColor: (Int) -> Unit, size: Float, onSize: (Float) -> Unit,
    finger: Boolean, onFinger: (Boolean) -> Unit, snap: Boolean, onSnap: (Boolean) -> Unit, onClearAll: () -> Unit, autoColor: Color,
) {
    // floating right above the bar, like a menu that opens out of the pen
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp).shadow(10.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(T.panel).padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (mode == InkMode.PEN) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PEN_STYLES.forEach { s -> PanelChip(tr(s.en, s.tr), style == s.id) { onStyle(s.id) } }
            }
        }
        if (mode != InkMode.LASSO) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Box(Modifier.size((4 + 4 * size).dp.coerceAtMost(22.dp)).clip(CircleShape).background(T.text)) }
                Slider(
                    value = size, onValueChange = onSize, valueRange = 0.5f..3f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = T.accent, activeTrackColor = T.accent),
                )
            }
        }
        if (mode == InkMode.PEN) SwatchRow(INK_PALETTE, color, autoColor, onColor)
        if (mode == InkMode.MARKER) SwatchRow(MARKER_PALETTE, color and 0x00FFFFFF or (0xFF shl 24), autoColor, onColor)
        if (mode == InkMode.ERASER) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { PanelChip(tr("Erase all", "Hepsini sil"), false, Color(0xFFE53935)) { onClearAll() } }
        PanelSwitch(tr("Finger draws", "Parmak çizer"), finger, onFinger)
        if (mode == InkMode.PEN) PanelSwitch(tr("Straight line (hold)", "Düz çizgi (basılı tut)"), snap, onSnap)
    }
}

/** What the lasso chose: how many strokes, their colours, delete, and done. */
@Composable
fun InkSelection(count: Int, onColor: (Int) -> Unit, onDelete: () -> Unit, onDone: () -> Unit, autoColor: Color) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(22.dp)).background(T.panel.copy(alpha = .95f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("$count selected", "$count seçili"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            PanelChip(tr("Delete", "Sil"), false, Color(0xFFE53935)) { onDelete() }
            Box(Modifier.width(8.dp))
            PanelChip(tr("Done", "Bitti"), false) { onDone() }
        }
        SwatchRow(INK_PALETTE, Int.MIN_VALUE, autoColor, onColor)
    }
}

@Composable
private fun SwatchRow(colors: List<Int>, chosen: Int, autoColor: Color, onColor: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { c ->
            val shown = if (c == 0) autoColor else Color(c)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(shown)
                    .border(if (chosen == c) 2.5.dp else 1.dp, if (chosen == c) T.accent else T.text.copy(alpha = .35f), CircleShape)
                    .clickable { onColor(c) }.semantics { contentDescription = if (c == 0) "Auto" else "Colour" },
            )
        }
    }
}

@Composable
private fun PanelChip(label: String, on: Boolean, tint: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) T.accent.copy(alpha = .2f) else T.text.copy(alpha = .08f)).clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) { Text(label, color = tint ?: if (on) T.accent else T.text, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun PanelSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PSwitch(on, onChange)
    }
}

/** The marker's glyph: a slanted marker with a chisel tip and a line under it. */
@Composable
private fun MarkerGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val body = Path().apply {
            moveTo(w * .34f, w * .60f); lineTo(w * .62f, w * .32f); lineTo(w * .82f, w * .52f); lineTo(w * .54f, w * .80f); close()
        }
        drawPath(body, tint, style = Stroke(w * .09f, join = StrokeJoin.Round))
        val tip = Path().apply { moveTo(w * .34f, w * .60f); lineTo(w * .20f, w * .84f); lineTo(w * .54f, w * .80f); close() }
        drawPath(tip, tint)
        drawLine(tint, Offset(w * .46f, w * .92f), Offset(w * .90f, w * .92f), strokeWidth = w * .09f, cap = StrokeCap.Round)
    }
}

/** The lasso's glyph: a dashed loop with a short tail. */
@Composable
private fun LassoGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawOval(tint, Offset(w * .10f, w * .14f), Size(w * .80f, w * .52f), style = Stroke(w * .085f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * .16f, w * .1f))))
        drawLine(tint, Offset(w * .46f, w * .66f), Offset(w * .60f, w * .90f), strokeWidth = w * .085f, cap = StrokeCap.Round)
    }
}
