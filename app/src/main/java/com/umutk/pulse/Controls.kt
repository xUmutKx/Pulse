package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Controls that follow the theme: bevelled boxes for 95, flat Windows ones for XP / 7 / 10 / 11, Material only for the Material looks. */

private val classic get() = T.tabs && T.id != "win10"

/** Windows 95 drawn like the real system (Settings switch on); off keeps the earlier 95 look. */
val win95Authentic: Boolean get() = T.id == "win95" && Opt.authentic

/** Windows 7 drawn like the real system (Settings switch on); off keeps the earlier 7 look. */
val win7Authentic: Boolean get() = T.id == "win7" && Opt.authentic

/** Windows XP drawn like the real system (Settings switch on); off keeps the earlier XP look. */
val xpAuthentic: Boolean get() = T.id == "xp" && Opt.authentic

/** Luna push-button face: white to beige, 3dp corners, #003C74 edge; [hot] = the orange inner rim of a pressed / selected button. */
fun Modifier.luna(hot: Boolean): Modifier {
    val r = RoundedCornerShape(3.dp)
    return this.then(Modifier.background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F3EE), Color(0xFFECEBE6), Color(0xFFD6D0C5))), r)
        .border(1.dp, Color(0xFF003C74), r)
        .then(if (hot) Modifier.padding(1.dp).border(2.dp, Brush.verticalGradient(listOf(Color(0xFFFFF0CF), Color(0xFFF8B330), Color(0xFFE59700))), RoundedCornerShape(2.dp)) else Modifier))
}

/** Aero push-button face: glossy grey split at half height, 3dp corners; [hot] = the pressed / selected blue glow. */
fun Modifier.aero7(hot: Boolean): Modifier {
    val r = RoundedCornerShape(3.dp)
    return this.then(Modifier.background(
        if (hot) Brush.verticalGradient(0f to Color(0xFFE5F4FC), .5f to Color(0xFFC4E5F6), .5f to Color(0xFF98D1EF), 1f to Color(0xFF68B3DB))
        else Brush.verticalGradient(0f to Color(0xFFF2F2F2), .5f to Color(0xFFEBEBEB), .5f to Color(0xFFDDDDDD), 1f to Color(0xFFCFCFCF)), r)
        .border(1.dp, if (hot) Color(0xFF2C628B) else Color(0xFF707070), r)
        .padding(1.dp).border(1.dp, Color.White.copy(alpha = if (hot) .35f else .7f), RoundedCornerShape(2.dp)))
}

@Composable
fun PSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val t = T
    when {
        oem -> OemSwitch(on, onChange, enabled)
        t.material -> Switch(on, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = Look.accent))
        classic -> PCheck(on, onChange, enabled)
        else -> { // Windows 10 / 11 toggle
            val a = if (enabled) 1f else .45f
            Box(Modifier.size(40.dp, 20.dp).background(if (on) Look.accent.copy(alpha = a) else Color.Transparent, RoundedCornerShape(10.dp))
                .border(1.dp, if (on) Look.accent.copy(alpha = a) else t.text.copy(alpha = .6f * a), RoundedCornerShape(10.dp))
                .then(if (enabled) Modifier.clickable { onChange(!on) } else Modifier)) {
                Box(Modifier.padding(start = if (on) 22.dp else 4.dp, top = 4.dp).size(12.dp).background(if (on) Color.White else t.text.copy(alpha = .8f * a), CircleShape))
            }
        }
    }
}

@Composable
fun PCheck(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val t = T
    if (t.material) { Checkbox(on, onChange, enabled = enabled, colors = CheckboxDefaults.colors(checkedColor = Look.accent)); return }
    val id = t.id
    val acc = Look.accent.copy(alpha = 1f) // win11 Look.accent is half see-through; checks are solid
    Box(Modifier.size(40.dp).toggleable(on, enabled, Role.Checkbox) { onChange(it) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(when (id) { "win95" -> 13.dp; "xp", "win7" -> 16.dp; else -> 20.dp }).alpha(if (enabled) 1f else .45f)) {
            val s = size.width; val px = 1.dp.toPx()
            fun tick(col: Color, w: Float, round: Boolean = false) = drawPath(Path().apply {
                moveTo(s * .24f, s * .52f); lineTo(s * .42f, s * .70f); lineTo(s * .76f, s * .30f)
            }, col, style = Stroke(w, cap = if (round) StrokeCap.Round else StrokeCap.Square, join = if (round) StrokeJoin.Round else StrokeJoin.Miter))
            fun edge(col: Color, inset: Float) = drawRect(col, Offset(inset + px / 2, inset + px / 2), Size(s - 2 * inset - px, s - 2 * inset - px), style = Stroke(px))
            when (id) {
                "win95" -> { // white well, sunken 2 px edge: grey/black top-left, white/light grey bottom-right
                    drawRect(Color.White)
                    fun l(a: Offset, b: Offset, c: Color) = drawLine(c, a, b, px)
                    val o = px / 2; val i = px * 1.5f
                    l(Offset(0f, o), Offset(s, o), Color(0xFF808080)); l(Offset(o, 0f), Offset(o, s), Color(0xFF808080))
                    l(Offset(px, i), Offset(s - px, i), Color.Black); l(Offset(i, px), Offset(i, s - px), Color.Black)
                    l(Offset(0f, s - o), Offset(s, s - o), Color.White); l(Offset(s - o, 0f), Offset(s - o, s), Color.White)
                    l(Offset(px, s - i), Offset(s - px, s - i), Color(0xFFDFDFDF)); l(Offset(s - i, px), Offset(s - i, s - px), Color(0xFFDFDFDF))
                    if (on) tick(Color.Black, 2 * px)
                }
                "xp" -> { // navy edge, soft grey-to-white well, green tick
                    drawRect(Brush.linearGradient(listOf(Color(0xFFDCDCD7), Color.White), Offset.Zero, Offset(s, s)))
                    edge(Color(0xFF1C5180), 0f)
                    if (on) tick(Color(0xFF21A121), 2.2f * px)
                }
                "win7" -> { // light box, grey edge, glossy blue-grey inner square
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFF6F6F6), Color(0xFFDEDEDE))))
                    edge(Color(0xFF8E8F8F), 0f)
                    drawRect(Brush.linearGradient(listOf(Color(0xFFCBCFD5), Color(0xFFF6F6F6)), Offset.Zero, Offset(s, s)), Offset(3 * px, 3 * px), Size(s - 6 * px, s - 6 * px))
                    edge(Color(0xFFAEB3B9), 2 * px)
                    if (on) tick(Color(0xFF1E395B), 2 * px)
                }
                "win10" -> { // square, 2 px edge in the text colour; checked = accent fill
                    if (on) drawRect(acc) else drawRect(t.text, Offset(px, px), Size(s - 2 * px, s - 2 * px), style = Stroke(2 * px))
                    if (on) tick(onAccent(acc), 1.6f * px)
                }
                else -> { // Windows 11 (and the glass looks): rounded, thin grey edge, accent fill with a Fluent tick
                    val r = CornerRadius(4.dp.toPx())
                    if (on) drawRoundRect(acc, cornerRadius = r) else {
                        drawRoundRect(t.text.copy(alpha = .06f), cornerRadius = r)
                        drawRoundRect(t.text.copy(alpha = .5f), Offset(px / 2, px / 2), Size(s - px, s - px), r, Stroke(px))
                    }
                    if (on) tick(onAccent(acc), 1.4f * px, round = true)
                }
            }
        }
    }
}

@Composable
fun PRadio(on: Boolean, onClick: () -> Unit) {
    val t = T
    if (t.material) { RadioButton(on, onClick, colors = RadioButtonDefaults.colors(selectedColor = Look.accent)); return }
    if (win95Authentic) { // 12 px white well, sunken edge (grey/black top-left, white/light grey bottom-right), black dot
        Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(12.dp)) {
                val px = 1.dp.toPx(); val r = size.width / 2
                drawCircle(Color.White, r)
                drawArc(Color(0xFF808080), 135f, 180f, false, style = Stroke(px), topLeft = Offset(px / 2, px / 2), size = Size(size.width - px, size.height - px))
                drawArc(Color.Black, 135f, 180f, false, style = Stroke(px), topLeft = Offset(1.5f * px, 1.5f * px), size = Size(size.width - 3 * px, size.height - 3 * px))
                drawArc(Color.White, -45f, 180f, false, style = Stroke(px), topLeft = Offset(px / 2, px / 2), size = Size(size.width - px, size.height - px))
                drawArc(Color(0xFFDFDFDF), -45f, 180f, false, style = Stroke(px), topLeft = Offset(1.5f * px, 1.5f * px), size = Size(size.width - 3 * px, size.height - 3 * px))
                if (on) drawCircle(Color.Black, 2f * px)
            }
        }
        return
    }
    if (xpAuthentic) { // XP radio: 13 px, navy ring, grey-to-white well, green dot
        Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(13.dp)) {
                val px = 1.dp.toPx(); val r = size.width / 2
                drawCircle(Brush.linearGradient(listOf(Color(0xFFDCDCD7), Color.White), Offset.Zero, Offset(size.width, size.height)), r)
                drawCircle(Color(0xFF1C5180), r - px / 2, style = Stroke(px))
                if (on) drawCircle(Brush.radialGradient(listOf(Color(0xFF8CE68C), Color(0xFF21A121)), center = Offset(r * .8f, r * .8f), radius = r * .5f), r * .38f)
            }
        }
        return
    }
    Box(Modifier.padding(4.dp).size(18.dp).background(if (classic) Color.White else Color.Transparent, CircleShape)
        .border(1.dp, if (on && !classic) Look.accent else t.text.copy(alpha = .7f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(if (classic) 7.dp else 10.dp).background(if (classic) Color.Black else Look.accent, CircleShape))
    }
}

@Composable
fun PChip(on: Boolean, label: String, onClick: () -> Unit) {
    val t = T
    if (oem) {
        val acc = Look.accent.copy(alpha = 1f)
        Box(Modifier.heightIn(min = 40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Box(Modifier.background(if (on) (if (oneUi) acc.copy(alpha = .2f) else acc) else t.text.copy(alpha = .07f), CircleShape).padding(horizontal = 16.dp, vertical = 7.dp)) {
                Text(label, color = if (on) (if (oneUi) acc else onAccent(acc)) else t.text, fontSize = 13.sp, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal)
            }
        }
        return
    }
    if (t.material) { FilterChip(on, onClick, { Text(label) }, shape = CircleShape, colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent, selectedContainerColor = Color.Transparent, selectedLabelColor = t.text)); return }
    if (win95Authentic) { // a 95 push button that stays pushed in: sunken bevel, text moved 1 px, no bold
        Box(Modifier.heightIn(min = 40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Box(Modifier.defaultMinSize(minHeight = 23.dp).background(Color(0xFFC0C0C0)).bevel(!on).padding(horizontal = 10.dp, vertical = 4.dp).offset(if (on) 1.dp else 0.dp, if (on) 1.dp else 0.dp), contentAlignment = Alignment.Center) {
                Text(label, color = Color.Black, fontSize = 12.sp)
            }
        }
    } else if (win7Authentic) { // Aero toggle button: grey gloss, the selected one keeps the pressed blue glow
        Box(Modifier.heightIn(min = 40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Box(Modifier.defaultMinSize(minHeight = 26.dp).aero7(on).padding(horizontal = 11.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(label, color = Color.Black, fontSize = 12.sp)
            }
        }
    } else if (xpAuthentic) { // Luna toggle button: the selected one keeps the orange rim
        Box(Modifier.heightIn(min = 40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Box(Modifier.defaultMinSize(minHeight = 26.dp).luna(on).padding(horizontal = 11.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text(label, color = Color.Black, fontSize = 12.sp)
            }
        }
    } else if (t.bevel) {
        Box(Modifier.background(t.bg).bevel(!on).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp).offset(if (on) 1.dp else 0.dp, if (on) 1.dp else 0.dp)) {
            Text(label, color = Color.Black, fontSize = 13.sp, fontWeight = if (on) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
        }
    } else {
        val shape = RoundedCornerShape(t.radius.coerceAtMost(8.dp))
        Box(Modifier.background(if (on) Look.accent.copy(alpha = if (classic) .25f else .16f) else Color.Transparent, shape).border(1.dp, if (on) Look.accent else t.line, shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(label, color = t.text, fontSize = 13.sp)
        }
    }
}

@Composable
fun PField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val t = T
    if (colorOs) { ColorOsField(value, onChange, hint, modifier, onDone); return }
    if (oneUi) { OneUiField(value, onChange, hint, modifier, onDone); return }
    if (win95Authentic) { // 95 edit box: white, 2 px sunken edge, small black text, no icon
        BasicTextField(value, onChange, modifier.heightIn(min = 40.dp).background(Color.White).bevel(false),
            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Send else ImeAction.Default), keyboardActions = KeyboardActions(onSend = { onDone?.invoke() }),
            textStyle = TextStyle(color = Color.Black, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif), cursorBrush = SolidColor(Color.Black),
            decorationBox = { inner -> Box(Modifier.padding(horizontal = 6.dp, vertical = 11.dp), contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(hint, color = Color(0xFF808080), fontSize = 12.sp); inner() } })
        return
    }
    val shape = if (t.material) CircleShape else if (xpAuthentic) RoundedCornerShape(0.dp) else RoundedCornerShape(t.radius.coerceAtMost(8.dp)) // M3 search / text field: full pill
    val fill = if (t.material) t.line.copy(alpha = .35f) else if (t.dark) Color.White.copy(alpha = .06f) else Color.White
    // win7 authentic: the Aero text box's grey-blue edge
    val edge = if (t.material) Modifier else if (t.bevel) Modifier.bevel(false) else if (win7Authentic) Modifier.border(1.dp, Color(0xFFABADB3), shape) else Modifier.border(1.dp, t.line, shape)
    BasicTextField(value, onChange, modifier.background(fill, shape).then(edge),
        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Send else ImeAction.Default), keyboardActions = KeyboardActions(onSend = { onDone?.invoke() }), textStyle = TextStyle(color = if (t.material || t.dark) t.text else Color.Black, fontSize = if (t.material) 16.sp else 14.sp), cursorBrush = SolidColor(Look.accent),
        decorationBox = { inner -> Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("search", t.sub, 16.dp); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = t.sub, fontSize = 14.sp); inner() }
        } })
}

@Composable
fun PSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, thick: Boolean = false, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val t = T
    if (colorOs) { ColorOsSlider(value, onChange, range, onDone); return }
    if (oneUi) { OneUiSlider(value, onChange, range, onDone); return }
    // thick: a wide groove and a big thumb, drawn by this function for every look (Material included)
    if (t.material && !thick) { Slider(value, onChange, modifier = modifier, valueRange = range, onValueChangeFinished = onDone, colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent, inactiveTrackColor = Look.accent.copy(alpha = .24f))); return }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val groove = if (thick) 12.dp else 4.dp
    val thumbW = if (thick) 22.dp else 11.dp
    val thumbH = if (thick) 30.dp else 21.dp
    BoxWithConstraints(modifier.fillMaxWidth().height(if (thick) 40.dp else 32.dp).pointerInput(Unit) {
        detectTapGestures { o -> onChange(range.start + (o.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)); onDone() }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = onDone) { c, _ -> onChange(range.start + (c.position.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)) }
    }, contentAlignment = Alignment.CenterStart) {
        if (win95Authentic) { // 95 trackbar: sunken 4 px groove, raised 11x21 grey thumb
            Box(Modifier.fillMaxWidth().height(groove).background(Color(0xFFC0C0C0)).bevel(false))
            Box(Modifier.offset(maxWidth * frac - thumbW / 2).size(thumbW, thumbH).background(Color(0xFFC0C0C0)).bevel(true))
            return@BoxWithConstraints
        }
        if (win7Authentic) { // Win7 trackbar: pale sunken groove, glossy Aero thumb
            Box(Modifier.fillMaxWidth().height(groove).background(Color(0xFFE7EAEA)).border(1.dp, Color(0xFFB0B4B8)))
            Box(Modifier.offset(maxWidth * frac - thumbW / 2).size(thumbW, thumbH - 1.dp).aero7(false))
            return@BoxWithConstraints
        }
        if (xpAuthentic) { // XP trackbar: thin sunken groove, Luna thumb
            Box(Modifier.fillMaxWidth().height(groove).background(Color(0xFFF4F3EE)).border(1.dp, Color(0xFF9D9C99)))
            Box(Modifier.offset(maxWidth * frac - thumbW / 2).size(thumbW, thumbH).luna(false))
            return@BoxWithConstraints
        }
        val bar = if (thick) 12.dp else if (classic) 4.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(bar).background(t.line))
        Box(Modifier.fillMaxWidth(frac).height(bar).background(if (classic) t.line else Look.accent))
        Box(Modifier.offset(maxWidth * frac - (if (thick) thumbW else if (classic) 10.dp else 16.dp) / 2).size(if (thick) thumbW else if (classic) 10.dp else 16.dp, if (thick) thumbH else 18.dp).then(if (t.bevel) Modifier.background(t.bg).bevel(true) else Modifier.background(if (classic) t.bg else Look.accent, if (classic) RoundedCornerShape(2.dp) else CircleShape).then(if (classic) Modifier.border(1.dp, t.line, RoundedCornerShape(2.dp)) else Modifier)))
    }
}

/** A dialog drawn as a small themed window instead of a Material AlertDialog. */
@Composable
fun PDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, info: Boolean = false) {
    // every confirm and info box is the look's own window (95 / XP / 7 / 10 / 11 title bar, buttons and icon, or the Material dialog)
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = LocalWindowLook.current.text) },
        text = { Text(text, color = LocalWindowLook.current.sub, fontSize = 14.sp) },
        confirmButton = { PButton(confirm, danger = !info, onClick = onConfirm) },
        dismissButton = if (info) null else ({ PButton("Cancel", onClick = onDismiss) }),
        warn = !info,
    )
}
