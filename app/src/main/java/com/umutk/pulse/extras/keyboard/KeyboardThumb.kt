package com.umutk.pulse.extras.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The direction keyboard (Thumb-Key style, ordinary swipe keys): nine big letter keys, each typing its centre letter on a tap and
 * another character when the finger is dragged towards it. The directions are N, NE, E, SE, S, SW, W and NW.
 */
private class DirKey(val c: String, val d: Map<String, String> = emptyMap())

private val DIR_NAMES = listOf("E", "SE", "S", "SW", "W", "NW", "N", "NE")

private fun dirKeys(tr: Boolean): List<DirKey> = listOf(
    DirKey("s", buildMap { put("SE", "w"); if (tr) put("N", "ş") }),
    DirKey("r", buildMap { put("S", "g"); if (tr) put("N", "ğ") }),
    DirKey("o", buildMap { put("SW", "u"); if (tr) put("N", "ö") }),
    DirKey("n", buildMap { put("E", "m"); if (tr) put("N", "ı") }),
    DirKey("h", mapOf("N" to "q", "NW" to "j", "NE" to "b", "W" to "k", "E" to "p", "SW" to "v", "S" to "x", "SE" to "y")),
    DirKey("a", mapOf("W" to "l", "N" to "!shift", "S" to "!caps") + if (tr) mapOf("NE" to "ü") else emptyMap()),
    DirKey("t", buildMap { put("NE", "c"); if (tr) put("N", "ç") }),
    DirKey("i", mapOf("N" to "f", "NE" to "'", "E" to "z", "SE" to "-", "S" to ".", "W" to "*", "NW" to ",", "SW" to "?")),
    DirKey("e", buildMap { put("NW", "d"); if (tr) put("N", "ü") }),
)

@Composable
internal fun ThumbPanel(th: KbTheme, st: KbState, act: KbActions, keyH: Dp) {
    val tr = KeyboardLayouts.lang(st.lang).code.startsWith("tr")
    val loc = KeyboardLayouts.lang(st.lang).locale
    val keys = dirKeys(tr)
    val up = st.shift > 0
    val big = keyH * 1.15f
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(3f)) {
            for (r in 0 until 3) Row(Modifier.fillMaxWidth()) {
                for (cI in 0 until 3) {
                    val k = keys[r * 3 + cI]
                    DirCell(th, Modifier.weight(1f), big, k.c, k.d, up, loc, act, onCmd = { cmd ->
                        when (cmd) { "!shift" -> act.shift(); "!caps" -> act.edit("capslock") }
                    }, onTap = { act.type(if (up) k.c.uppercase(loc) else k.c) }, onChar = { ch -> act.type(if (up) ch.uppercase(loc) else ch) })
                }
            }
            Row(Modifier.fillMaxWidth()) {
                DirCell(th, Modifier.weight(1f), big * .8f, " ", emptyMap(), false, loc, act, special = true, spaceKey = true, onTap = { act.space() }, onChar = {}, onCmd = {})
            }
        }
        Column(Modifier.weight(1f)) {
            DirCell(th, Modifier.fillMaxWidth(), big, "", mapOf("N" to "!settings", "E" to "!menu", "W" to "!lang", "S" to "!voice"), false, loc, act, special = true, icon = Icons.Rounded.EmojiEmotions,
                onTap = { act.emojiPanel() }, onChar = {}, onCmd = { c -> when (c) { "!settings" -> act.menu("settings"); "!menu" -> act.menu("menu"); "!lang" -> act.lang(); "!voice" -> act.voice() } })
            DirCell(th, Modifier.fillMaxWidth(), big, "#", mapOf("NW" to "!selectAll", "N" to "!copy", "NE" to "!cut", "SW" to "!undo", "S" to "!paste", "SE" to "!redo"), false, loc, act, special = true,
                onTap = { act.page(1) }, onChar = {}, onCmd = { c -> act.edit(c.drop(1)) })
            DirCell(th, Modifier.fillMaxWidth(), big, "", emptyMap(), false, loc, act, special = true, icon = Icons.Rounded.Backspace, repeat = true, onTap = { act.backspace() }, onChar = {}, onCmd = {})
            DirCell(th, Modifier.fillMaxWidth(), big * .8f, "", emptyMap(), false, loc, act, special = true, icon = Icons.Rounded.KeyboardReturn, onTap = { act.enter() }, onChar = {}, onCmd = {})
        }
    }
}

private fun glyph(v: String, up: Boolean, loc: java.util.Locale) = when (v) {
    "!shift" -> "⇧"; "!caps" -> "⇪"; "!copy" -> "⧉"; "!cut" -> "✂"; "!paste" -> "⎘"; "!undo" -> "↶"; "!redo" -> "↷"; "!selectAll" -> "▣"
    "!settings" -> "⚙"; "!menu" -> "⋯"; "!lang" -> "🌐"; "!voice" -> "🎤"
    else -> if (up) v.uppercase(loc) else v
}

@Composable
private fun DirCell(
    th: KbTheme, modifier: Modifier, h: Dp, center: String, dirs: Map<String, String>, up: Boolean, loc: java.util.Locale, act: KbActions,
    special: Boolean = false, spaceKey: Boolean = false, icon: ImageVector? = null, repeat: Boolean = false,
    onTap: () -> Unit, onChar: (String) -> Unit, onCmd: (String) -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf<String?>(null) }
    val tapNow by rememberUpdatedState(onTap); val charNow by rememberUpdatedState(onChar); val cmdNow by rememberUpdatedState(onCmd); val dirsNow by rememberUpdatedState(dirs)
    val actNow by rememberUpdatedState(act)
    val th0 = LocalDensity.current.density * 20f
    val bg = if (special) th.special else th.key
    val shape = RoundedCornerShape(th.radius.dp)
    Box(
        modifier.height(h).padding(2.dp).clip(shape).background(if (down) Color.White.copy(alpha = .18f).compositeOver(bg) else bg).border(BorderStroke(1.dp, th.text.copy(alpha = .35f)), shape)
            .pointerInput(spaceKey, repeat) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false); d.consume()
                    down = true; sel = null
                    actNow.feedback()
                    val start = d.position
                    var lastCur = 0
                    var cursorMode = false
                    var due = android.os.SystemClock.uptimeMillis() + 380L
                    var repeated = false
                    try {
                        while (true) {
                            val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                            if (ev == null) { if (repeat) { tapNow(); repeated = true; due = android.os.SystemClock.uptimeMillis() + 55L } else due = Long.MAX_VALUE / 2; continue }
                            val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                            c.consume()
                            val dx = c.position.x - start.x; val dy = c.position.y - start.y
                            if (c.changedToUp() || !c.pressed) {
                                val s = sel
                                if (spaceKey) { if (!cursorMode) tapNow() }
                                else if (repeat) { if (!repeated) tapNow() }
                                else if (s != null) { val v = dirsNow[s]; if (v != null) { if (v.startsWith("!")) cmdNow(v) else charNow(v) } }
                                else tapNow()
                                break
                            }
                            if (spaceKey) {
                                if (cursorMode || kotlin.math.abs(dx) > th0) { cursorMode = true; val step = (dx / (th0 * .6f)).toInt(); if (step != lastCur) { actNow.moveCursor(step - lastCur); lastCur = step } }
                                continue
                            }
                            if (hypot(dx, dy) > th0) {
                                val ang = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).let { if (it < 0) it + 360 else it }
                                val name = DIR_NAMES[(((ang + 22.5) / 45).toInt()) % 8]
                                val n = if (dirsNow.containsKey(name)) name else null
                                if (n != sel) { sel = n; if (n != null) actNow.feedback() }
                            } else sel = null
                        }
                    } finally { down = false; sel = null }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = th.specialText, modifier = Modifier.size(26.dp))
        else if (center.isNotBlank()) Text(if (up) center.uppercase(loc) else center, color = if (special) th.specialText else th.text, fontSize = 30.sp)
        if (!spaceKey) dirsNow.forEach { (dName, v) ->
            val al = when (dName) {
                "N" -> Alignment.TopCenter; "NE" -> Alignment.TopEnd; "E" -> Alignment.CenterEnd; "SE" -> Alignment.BottomEnd
                "S" -> Alignment.BottomCenter; "SW" -> Alignment.BottomStart; "W" -> Alignment.CenterStart; else -> Alignment.TopStart
            }
            Text(glyph(v, up, loc), color = if (sel == dName) th.accent else th.text.copy(alpha = .75f), fontSize = if (sel == dName) 18.sp else 13.sp,
                modifier = Modifier.align(al).padding(horizontal = 7.dp, vertical = 4.dp))
        }
    }
}
