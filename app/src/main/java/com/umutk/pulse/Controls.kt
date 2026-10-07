package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

@Composable
fun PSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val t = T
    when {
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
    val shape = RoundedCornerShape(if (t.id == "win11") 4.dp else 0.dp)
    Box(Modifier.padding(4.dp).size(18.dp).background(if (on && !classic) Look.accent else Color.White, shape)
        .then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, if (on && !classic) Look.accent else t.text.copy(alpha = .6f), shape))
        .then(if (enabled) Modifier.clickable { onChange(!on) } else Modifier), contentAlignment = Alignment.Center) {
        if (on) Text("✓", color = if (classic) Color.Black else Color.White, fontSize = 13.sp)
    }
}

@Composable
fun PRadio(on: Boolean, onClick: () -> Unit) {
    val t = T
    if (t.material) { RadioButton(on, onClick, colors = RadioButtonDefaults.colors(selectedColor = Look.accent)); return }
    Box(Modifier.padding(4.dp).size(18.dp).background(if (classic) Color.White else Color.Transparent, CircleShape)
        .border(1.dp, if (on && !classic) Look.accent else t.text.copy(alpha = .7f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(if (classic) 7.dp else 10.dp).background(if (classic) Color.Black else Look.accent, CircleShape))
    }
}

@Composable
fun PChip(on: Boolean, label: String, onClick: () -> Unit) {
    val t = T
    if (t.material) { FilterChip(on, onClick, { Text(label) }); return }
    if (t.bevel) {
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
fun PField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val t = T
    if (t.material) {
        OutlinedTextField(value, onChange, modifier, singleLine = true, placeholder = { Text(hint) }, shape = RoundedCornerShape(t.radius.coerceAtMost(14.dp)),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = t.text, unfocusedTextColor = t.text, focusedBorderColor = Look.accent, unfocusedBorderColor = t.line, cursorColor = Look.accent))
        return
    }
    val shape = RoundedCornerShape(t.radius.coerceAtMost(8.dp))
    BasicTextField(value, onChange, modifier.background(if (t.dark) Color.White.copy(alpha = .06f) else Color.White, shape).then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, t.line, shape)),
        singleLine = true, textStyle = TextStyle(color = if (t.dark) t.text else Color.Black, fontSize = 14.sp), cursorBrush = SolidColor(Look.accent),
        decorationBox = { inner -> Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("search", t.sub, 16.dp); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = t.sub, fontSize = 14.sp); inner() }
        } })
}

@Composable
fun PSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, onDone: () -> Unit) {
    val t = T
    if (t.material) { Slider(value, onChange, valueRange = range, onValueChangeFinished = onDone, colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent)); return }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
        detectTapGestures { o -> onChange(range.start + (o.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)); onDone() }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = onDone) { c, _ -> onChange(range.start + (c.position.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)) }
    }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(if (classic) 4.dp else 3.dp).background(t.line))
        Box(Modifier.fillMaxWidth(frac).height(if (classic) 4.dp else 3.dp).background(if (classic) t.line else Look.accent))
        Box(Modifier.offset(maxWidth * frac - 8.dp).size(if (classic) 10.dp else 16.dp, 18.dp).then(if (t.bevel) Modifier.background(t.bg).bevel(true) else Modifier.background(if (classic) t.bg else Look.accent, if (classic) RoundedCornerShape(2.dp) else CircleShape).then(if (classic) Modifier.border(1.dp, t.line, RoundedCornerShape(2.dp)) else Modifier)))
    }
}

/** A dialog drawn as a small themed window instead of a Material AlertDialog. */
@Composable
fun PDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val t = T
    androidx.compose.ui.window.Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().background(t.bg, RoundedCornerShape(t.radius.coerceAtMost(12.dp))).then(if (t.bevel) Modifier.bevel(true) else Modifier.border(1.dp, t.line, RoundedCornerShape(t.radius.coerceAtMost(12.dp))))) {
            if (t.title != null) Box(Modifier.fillMaxWidth().background(t.title).padding(8.dp)) { Text(title, color = t.titleText, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
            else Text(title, color = t.text, fontSize = 18.sp, modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp))
            Text(text, color = if (t.tabs && t.id != "win10") Color.Black else t.sub, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                PButton("Cancel", onClick = onDismiss); Spacer(Modifier.width(8.dp)); PButton(confirm, danger = true, onClick = onConfirm)
            }
        }
    }
}
