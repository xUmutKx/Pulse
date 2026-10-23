package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Samsung One UI pieces: slider, filled text field, icon badge, bottom navigation shell and the theme-picker preview. */

/** One UI slider: thick rounded track, round white thumb with an accent ring. */
@Composable
fun OneUiSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, onDone: () -> Unit) {
    val acc = Look.accent.copy(alpha = 1f)
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp).pointerInput(Unit) {
        detectTapGestures { o -> onChange(range.start + (o.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)); onDone() }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = onDone) { c, _ -> onChange(range.start + (c.position.x / size.width).coerceIn(0f, 1f) * (range.endInclusive - range.start)) }
    }, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(T.text.copy(alpha = .12f), CircleShape))
        Box(Modifier.fillMaxWidth(frac).height(8.dp).background(acc, CircleShape))
        Box(Modifier.offset((maxWidth - 24.dp) * frac).size(24.dp).background(Color.White, CircleShape).border(3.dp, acc, CircleShape))
    }
}

/** One UI text field: soft filled rounded box, no edge. */
@Composable
fun OneUiField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val t = T
    BasicTextField(value, onChange, modifier.background(t.text.copy(alpha = .06f), RoundedCornerShape(24.dp)),
        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Send else ImeAction.Default), keyboardActions = KeyboardActions(onSend = { onDone?.invoke() }),
        textStyle = TextStyle(color = t.text, fontSize = 16.sp), cursorBrush = SolidColor(Look.accent),
        decorationBox = { inner -> Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("search", t.sub, 18.dp); Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { if (value.isEmpty()) Text(hint, color = t.sub, fontSize = 16.sp); inner() }
        } })
}

/** Settings-style list icon: a white glyph on a coloured circle. */
@Composable
fun OneUiBadge(icon: String, color: Color = Look.accent.copy(alpha = 1f), size: androidx.compose.ui.unit.Dp = 40.dp) {
    Box(Modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) { PNavIcon(icon, Color.White, size * .55f) }
}

/** One UI page frame: the page on the grey Settings background, the navigation as a bottom bar with a rounded pill behind the selected item. */
@Composable
fun OneUiShell(page: String, go: (String) -> Unit, menus: MenuHost, pages: List<Pg>, body: @Composable () -> Unit) {
    val t = T
    val acc = Look.accent.copy(alpha = 1f)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f)); MenuOverflow(menus, t.sub)
        }
        Box(Modifier.weight(1f)) { body() }
        Row(Modifier.fillMaxWidth().background(t.panel).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp)) {
            pages.forEach { p ->
                val on = page == p.id
                Column(Modifier.width(76.dp).clickable { go(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp, 32.dp).background(if (on) acc.copy(alpha = .18f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { PNavIcon(p.icon, if (on) acc else t.sub, 22.dp) }
                    Text(pageName(p.id, p.label), color = if (on) acc else t.sub, fontSize = 11.sp, maxLines = 1, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Mini One UI window for the theme picker: big empty title area, one rounded grouped card, pill bottom bar. */
@Composable
fun OneUiPreview(th: PTheme) {
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        Spacer(Modifier.weight(.9f))
        Box(Modifier.width(34.dp).height(5.dp).background(th.text, CircleShape))
        Spacer(Modifier.height(5.dp))
        Column(Modifier.fillMaxWidth().weight(2.4f).background(th.panel, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            repeat(2) { i -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (i == 0) th.accent else Color(0xFF34C759), CircleShape)); Spacer(Modifier.width(5.dp))
                Box(Modifier.weight(1f).height(3.dp).background(th.sub.copy(alpha = .5f), CircleShape))
                Box(Modifier.size(14.dp, 8.dp).background(if (i == 0) th.accent else th.line, CircleShape))
            } }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { i -> Box(Modifier.size(if (i == 1) 16.dp else 8.dp, 7.dp).background(if (i == 1) th.accent.copy(alpha = .3f) else th.sub.copy(alpha = .4f), CircleShape)) }
        }
    }
}
