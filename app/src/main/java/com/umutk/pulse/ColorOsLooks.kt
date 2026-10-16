package com.umutk.pulse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** ColorOS 14/15 own components: green capsule switch, thin slider with ringed thumb, filled fields, oval nav indicator, centred dialog. */

@Composable
fun ColorOsSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
    val acc = Look.accent.copy(alpha = 1f)
    val off = if (T.dark) Color(0xFF48484C) else Color(0xFFD9D9DC)
    val track by animateColorAsState(if (on) acc else off)
    val pad by animateDpAsState(if (on) 23.dp else 3.dp)
    Box(Modifier.size(50.dp, 28.dp).then(if (enabled) Modifier else Modifier.alpha(.45f)).background(track, CircleShape)
        .then(if (enabled) Modifier.clickable { onChange(!on) } else Modifier)) {
        Box(Modifier.padding(start = pad, top = 3.dp).size(22.dp).background(Color.White, CircleShape))
    }
}

@Composable
fun ColorOsSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, onDone: () -> Unit) {
    val acc = Look.accent.copy(alpha = 1f)
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
        detectTapGestures { o -> onChange(range.start + (o.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)); onDone() }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = onDone) { c, _ -> onChange(range.start + (c.position.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)) }
    }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(T.text.copy(alpha = .12f), CircleShape))
        Box(Modifier.fillMaxWidth(frac).height(4.dp).background(acc, CircleShape))
        Box(Modifier.offset((maxWidth - 22.dp) * frac).size(22.dp).background(Color.White, CircleShape).border(5.dp, acc, CircleShape))
    }
}

@Composable
fun ColorOsField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val t = T
    BasicTextField(value, onChange, modifier.heightIn(min = 44.dp).background(t.text.copy(alpha = .06f), CircleShape),
        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Send else ImeAction.Default), keyboardActions = KeyboardActions(onSend = { onDone?.invoke() }),
        textStyle = TextStyle(color = t.text, fontSize = 15.sp), cursorBrush = SolidColor(Look.accent),
        decorationBox = { inner -> Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("search", t.sub, 18.dp); Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = t.sub, fontSize = 15.sp); inner() }
        } })
}

/** Rail / nav entry: icon in an oval accent-tinted indicator, label under it. */
@Composable
fun ColorOsNavItem(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val t = T; val acc = Look.accent.copy(alpha = 1f)
    Column(Modifier.padding(vertical = 4.dp).width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp, 32.dp).background(if (on) acc.copy(alpha = .16f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { PNavIcon(icon, if (on) acc else t.sub, 22.dp) }
        Text(label, color = if (on) acc else t.sub, fontSize = 11.sp, maxLines = 1, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/** Settings-style page title: bold, compact. */
@Composable
fun ColorOsTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = T.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); trailing()
    }
}

/** Centred rounded card, text centred, full-width capsule confirm and a quiet capsule cancel under it. */
@Composable
fun ColorOsDialog(w: PTheme, onDismiss: () -> Unit, title: (@Composable () -> Unit)?, text: (@Composable () -> Unit)?, confirm: @Composable () -> Unit, dismissBtn: (@Composable () -> Unit)?, containerColor: Color?) {
    val acc = Look.accent.copy(alpha = 1f)
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().background(containerColor ?: w.panel, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (title != null) CompositionLocalProvider(LocalContentColor provides w.text) { androidx.compose.material3.ProvideTextStyle(TextStyle(color = w.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)) { title() } }
            if (text != null) { Spacer(Modifier.height(10.dp)); CompositionLocalProvider(LocalContentColor provides w.sub) { androidx.compose.material3.ProvideTextStyle(TextStyle(color = w.sub, fontSize = 14.sp, textAlign = TextAlign.Center)) { text() } } }
            Spacer(Modifier.height(22.dp))
            CompositionLocalProvider(InOemDialog provides true) {
                Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).background(acc, CircleShape), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides onAccent(acc)) { confirm() }
                }
                if (dismissBtn != null) Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides acc) { dismissBtn() }
                }
            }
        }
    }
}

/** Theme-picker thumbnail: compact bold title, grouped rounded cards, green capsule nav. */
@Composable
fun ColorOsPreview(th: PTheme) {
    Box(Modifier.fillMaxWidth().aspectRatio(1.3f).background(th.bg).border(1.dp, th.line)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.padding(top = 2.dp).size(34.dp, 6.dp).background(th.text, CircleShape))
            repeat(2) { Column(Modifier.fillMaxWidth().background(th.panel, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(th.accent.copy(alpha = .25f), CircleShape)); Spacer(Modifier.width(4.dp))
                    Box(Modifier.weight(1f).height(3.dp).background(th.sub.copy(alpha = .4f), CircleShape)); Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(11.dp, 6.dp).background(if (it == 0) th.accent else th.line, CircleShape))
                } }
            } }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().background(th.panel, CircleShape).padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { Box(Modifier.size(if (it == 1) 16.dp else 7.dp, 7.dp).background(if (it == 1) th.accent else th.sub.copy(alpha = .4f), CircleShape)) }
            }
        }
    }
}
