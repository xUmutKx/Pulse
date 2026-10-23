package com.umutk.pulse.extras.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.ArrowUpward as ArrowUpFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private const val POPUP_W = 44

/** The whole keyboard: the bar, the keys (or the emoji, menu, clipboard and resize panels) and the pop-up over a pressed key. */
@Composable
fun KeyboardView(st: KbState, act: KbActions, themeId: String = KeyboardPrefs.theme, modifier: Modifier = Modifier) {
    val th = KeyboardThemes.of(themeId, st.dark)
    val scale = KeyboardPrefs.heightPct / 100f
    val keyH = (52f * scale).dp
    val one = KeyboardPrefs.oneHand
    Box(modifier.fillMaxWidth().background(th.bg).onSizeChanged { st.rootW = it.width.toFloat() }) {
        Column(
            (if (one == "off") Modifier.fillMaxWidth() else Modifier.fillMaxWidth(.82f).align(if (one == "left") Alignment.TopStart else Alignment.TopEnd))
                .padding(horizontal = KeyboardPrefs.sidePad.dp).padding(bottom = KeyboardPrefs.bottomPad.dp),
        ) {
            TopBar(th, st, act)
            when (st.panel) {
                "emoji" -> EmojiPanel(th, st, act, keyH)
                "menu" -> MenuPanel(th, st, act, keyH)
                "clip" -> ClipPanel(th, st, act, keyH)
                "resize" -> ResizePanel(th, st, keyH)
                else -> KeysPanel(th, st, act, keyH)
            }
        }
        Preview(th, st)
    }
}

// ---- the bar ---------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun TopBar(th: KbTheme, st: KbState, act: KbActions) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        BarIcon(th, if (st.toolbar) Icons.Outlined.Close else Icons.Outlined.ExpandMore) { st.toolbar = !st.toolbar }
        if (st.toolbar) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                BarIcon(th, Icons.Outlined.ContentPaste) { act.menu("clip") }
                BarIcon(th, Icons.Outlined.EmojiEmotions) { act.emojiPanel() }
                BarIcon(th, Icons.Outlined.SelectAll) { act.edit("selectAll") }
                BarIcon(th, Icons.Outlined.ContentCopy) { act.edit("copy") }
                BarIcon(th, Icons.Outlined.ContentCut) { act.edit("cut") }
                BarIcon(th, Icons.Outlined.MoreHoriz) { act.menu("menu") }
            }
        } else {
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                st.slots.forEachIndexed { i, s ->
                    Box(Modifier.weight(1f).fillMaxSize().clickable(enabled = s.isNotEmpty()) { act.slot(i) }, contentAlignment = Alignment.Center) {
                        Text(s, color = if (i == 1) th.text else th.sub, fontSize = 20.sp, fontWeight = if (i == 1) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIcon(th: KbTheme, icon: ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).border(1.5.dp, th.sub, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = th.text, modifier = Modifier.size(20.dp)) }
    }
}

// ---- the keys --------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun KeysPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val up = st.shift > 0
    val digits = !KeyboardPrefs.numberRow && KeyboardPrefs.longPressSymbols
    val langs = KeyboardPrefs.langList()
    val lang = KeyboardLayouts.lang(st.lang)
    val rows = KeyboardLayouts.letterRows(lang.code, up, digits)
    val n1 = rows[0].size.toFloat()
    if (KeyboardPrefs.arrows) {
        Row(Modifier.fillMaxWidth()) {
            ActionKey(th, keyH * .8f, Icons.Outlined.ArrowBack, true) { act.arrow(21) }
            ActionKey(th, keyH * .8f, Icons.Outlined.ArrowUpward, true) { act.arrow(19) }
            ActionKey(th, keyH * .8f, Icons.Outlined.ArrowDownward, true) { act.arrow(20) }
            ActionKey(th, keyH * .8f, Icons.Outlined.ArrowForward, true) { act.arrow(22) }
            ActionKey(th, keyH * .8f, Icons.Outlined.SkipPrevious, false) { act.arrow(122) }
            ActionKey(th, keyH * .8f, Icons.Outlined.SkipNext, false) { act.arrow(123) }
            ActionKey(th, keyH * .8f, Icons.Outlined.SelectAll, false) { act.edit("selectAll") }
            ActionKey(th, keyH * .8f, Icons.Outlined.ContentCopy, false) { act.edit("copy") }
            ActionKey(th, keyH * .8f, Icons.Outlined.ContentPaste, false) { act.edit("paste") }
        }
    }
    if (st.page == 0) {
        if (KeyboardPrefs.numberRow) Row(Modifier.fillMaxWidth()) {
            "1234567890".forEach { d -> Key(th, st, act, KbKey(d.toString(), KeyKind.CHAR, n1 / 10f, KeyboardLayouts.symbolAlts[d].orEmpty()), keyH, small = true) }
        }
        Row(Modifier.fillMaxWidth()) { rows[0].forEach { Key(th, st, act, it, keyH) } }
        Row(Modifier.fillMaxWidth()) {
            val sp = (n1 - rows[1].size) / 2f
            if (sp > 0) Spacer(sp); rows[1].forEach { Key(th, st, act, it, keyH) }; if (sp > 0) Spacer(sp)
        }
        Row(Modifier.fillMaxWidth()) {
            val side = ((n1 - rows[2].size) / 2f).coerceIn(1f, 2f)
            val rest = n1 - rows[2].size - 2 * side
            Key(th, st, act, KbKey("", KeyKind.SHIFT, side), keyH)
            if (rest > 0) Spacer(rest / 2f)
            rows[2].forEach { Key(th, st, act, it, keyH) }
            if (rest > 0) Spacer(rest / 2f)
            Key(th, st, act, KbKey("", KeyKind.DELETE, side), keyH)
        }
    } else {
        val src = if (st.page == 1) KeyboardLayouts.symbols1 else KeyboardLayouts.symbols2
        src.forEachIndexed { r, line ->
            Row(Modifier.fillMaxWidth()) {
                if (r == 2) Key(th, st, act, KbKey(if (st.page == 1) "2/2" else "1/2", KeyKind.SYMBOLS2, 1.5f), keyH)
                val w = if (r == 2) (n1 - 3f) / line.length else n1 / line.length
                line.forEach { ch -> Key(th, st, act, KbKey(ch.toString(), KeyKind.CHAR, w, KeyboardLayouts.symbolAlts[ch].orEmpty()), keyH) }
                if (r == 2) Key(th, st, act, KbKey("", KeyKind.DELETE, 1.5f), keyH)
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Key(th, st, act, KbKey(if (st.page == 0) "?123" else "ABC", KeyKind.MODE, 1.5f), keyH)
        if (langs.size > 1) Key(th, st, act, KbKey("", KeyKind.LANG, 1f), keyH)
        if (KeyboardPrefs.emojiKey) Key(th, st, act, KbKey("", KeyKind.EMOJI, 1f), keyH)
        Key(th, st, act, KbKey(",", KeyKind.COMMA, 1f, if (KeyboardPrefs.emojiKey) KeyboardLayouts.popupComma else listOf("😀") + KeyboardLayouts.popupComma), keyH)
        Key(th, st, act, KbKey(if (KeyboardPrefs.spaceLabel) lang.name else "", KeyKind.SPACE, 4f), keyH)
        Key(th, st, act, KbKey(".", KeyKind.PERIOD, 1f, KeyboardLayouts.popupPeriod), keyH)
        Key(th, st, act, KbKey("", KeyKind.ENTER, 1.5f), keyH)
    }
}

@Composable
private fun RowScope.Spacer(w: Float) { Box(Modifier.weight(w)) }

/** A key of the arrow row: a little special key that repeats while held when [repeat]. */
@Composable
private fun RowScope.ActionKey(th: KbTheme, h: androidx.compose.ui.unit.Dp, icon: ImageVector, repeat: Boolean, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier.weight(1f).height(h).pointerInput(repeat) {
            awaitEachGesture {
                val d = awaitFirstDown(requireUnconsumed = false); d.consume(); down = true; tap()
                var wait = 380L
                while (true) {
                    val ev = withTimeoutOrNull(wait) { awaitPointerEvent() }
                    if (ev == null) { if (repeat) tap(); wait = 70L; continue }
                    val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                    c.consume()
                    if (c.changedToUp() || !c.pressed) break
                }
                down = false
            }
        }.padding(KeyboardPrefs.keyGap.dp / 2),
    ) {
        Box(Modifier.fillMaxSize().keyLook(th, th.special, down, RoundedCornerShape(th.radius.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = th.specialText, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun RowScope.Key(th: KbTheme, st: KbState, act: KbActions, k: KbKey, keyH: androidx.compose.ui.unit.Dp, small: Boolean = false) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    var down by remember { mutableStateOf(false) }
    val actNow by rememberUpdatedState(act)
    val kNow by rememberUpdatedState(k)
    val longMs = LocalViewConfiguration.current.longPressTimeoutMillis
    Box(
        Modifier.weight(k.weight).height(if (small) keyH * .78f else keyH).onGloballyPositioned { rect = it.boundsInRoot() }
            .pointerInput(k.kind, k.label) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false); d.consume()
                    down = true
                    val key = kNow; val a = actNow
                    a.feedback()
                    val big = key.kind == KeyKind.CHAR || key.kind == KeyKind.COMMA || key.kind == KeyKind.PERIOD
                    if (big && KeyboardPrefs.keyPopups) st.preview = KbPreview(rect, key.label)
                    var long = false
                    var sel = -1
                    var spaceDragged = false
                    var lastStep = 0
                    var quick = false
                    var startX = d.position.x
                    var curX = d.position.x
                    var wait = longMs
                    var deleted = false
                    if (key.kind == KeyKind.DELETE) { a.backspace(); deleted = true; wait = 380L }
                    while (true) {
                        val ev = withTimeoutOrNull(wait) { awaitPointerEvent() }
                        if (ev == null) {
                            // the long press (or the next repeat of the delete key)
                            when (key.kind) {
                                KeyKind.DELETE -> { if (!quick) a.backspace(); wait = 55L }
                                KeyKind.SPACE -> { if (KeyboardPrefs.cursorControl) { long = true; spaceDragged = true; startX = curX; lastStep = 0; a.feedback() }; wait = Long.MAX_VALUE / 4 }
                                KeyKind.SHIFT -> { long = true; wait = Long.MAX_VALUE / 4 }
                                else -> if (key.alts.isNotEmpty() && (big)) {
                                    long = true; sel = 0; wait = Long.MAX_VALUE / 4
                                    st.preview = KbPreview(rect, key.label, key.alts, 0); a.feedback()
                                } else wait = Long.MAX_VALUE / 4
                            }
                            continue
                        }
                        val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                        c.consume()
                        curX = c.position.x
                        if (c.changedToUp() || !c.pressed) {
                            when {
                                key.kind == KeyKind.DELETE -> if (quick) a.quickDeleteEnd(true)
                                long && sel >= 0 && key.alts.isNotEmpty() -> a.type(key.alts[sel.coerceIn(0, key.alts.lastIndex)])
                                spaceDragged -> {}
                                long && key.kind == KeyKind.SHIFT -> a.edit("capslock")
                                !long -> when (key.kind) {
                                    KeyKind.CHAR, KeyKind.COMMA, KeyKind.PERIOD -> a.type(key.label)
                                    KeyKind.SPACE -> a.space()
                                    KeyKind.SHIFT -> a.shift()
                                    KeyKind.ENTER -> a.enter()
                                    KeyKind.MODE -> a.page(if (st.page == 0) 1 else 0)
                                    KeyKind.SYMBOLS2 -> a.page(if (st.page == 1) 2 else 1)
                                    KeyKind.EMOJI -> a.emojiPanel()
                                    KeyKind.LANG -> a.lang()
                                    else -> {}
                                }
                            }
                            break
                        }
                        val dx = c.position.x - startX
                        when {
                            key.kind == KeyKind.DELETE && KeyboardPrefs.quickDelete && !quick && dx < -28.dp.toPx() -> { quick = true; a.quickDeleteStart(); startX = c.position.x }
                            key.kind == KeyKind.DELETE && quick -> a.quickDelete((-dx / 14.dp.toPx()).toInt().coerceAtLeast(0))
                            spaceDragged && key.kind == KeyKind.SPACE -> {
                                val step = (dx / 12.dp.toPx()).toInt()
                                if (step != lastStep) { a.moveCursor(step - lastStep); lastStep = step }
                            }
                            long && key.alts.isNotEmpty() -> {
                                val count = key.alts.size
                                val w = POPUP_W.dp.toPx()
                                val left = popupLeft(rect, count, st.rootW, w)
                                val i = ((rect.left + c.position.x - left) / w).toInt().coerceIn(0, count - 1)
                                if (i != sel) { sel = i; st.preview = KbPreview(rect, key.label, key.alts, i); a.feedback() }
                            }
                        }
                    }
                    down = false
                    st.preview = null
                }
            }.padding(KeyboardPrefs.keyGap.dp / 2),
    ) {
        val special = k.kind != KeyKind.CHAR && k.kind != KeyKind.SPACE
        val bg = when (k.kind) { KeyKind.ENTER -> th.accent; KeyKind.CHAR, KeyKind.SPACE -> th.key; else -> th.special }
        val fg = when (k.kind) { KeyKind.ENTER -> th.onAccent; KeyKind.CHAR, KeyKind.SPACE -> th.text; else -> th.specialText }
        Box(Modifier.fillMaxSize().keyLook(th, bg, down, RoundedCornerShape(th.radius.dp)), contentAlignment = Alignment.Center) {
            val big = if (KeyboardPrefs.largeText) 27f else 22f
            val fs = (big * (.85f + .15f * KeyboardPrefs.heightPct / 100f)).sp
            when (k.kind) {
                KeyKind.SHIFT -> Icon(if (st.shift == 2) Icons.Filled.KeyboardCapslock else if (st.shift == 1) Icons.Filled.ArrowUpFilled else Icons.Outlined.ArrowUpward, null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.DELETE -> Icon(Icons.Outlined.Backspace, null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.ENTER -> Icon(when (st.enter) { "send" -> Icons.Outlined.Send; "search" -> Icons.Outlined.Search; "go", "done" -> Icons.Outlined.Check; "next" -> Icons.Outlined.ArrowForward; else -> Icons.Outlined.KeyboardReturn }, null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.EMOJI -> Icon(Icons.Outlined.EmojiEmotions, null, tint = fg, modifier = Modifier.size(24.dp))
                KeyKind.LANG -> Icon(Icons.Outlined.Language, null, tint = fg, modifier = Modifier.size(24.dp))
                KeyKind.SPACE -> Text(k.label, color = th.sub, fontSize = 12.sp, maxLines = 1)
                KeyKind.MODE, KeyKind.SYMBOLS2 -> Text(k.label, color = fg, fontSize = 15.sp, maxLines = 1)
                else -> {
                    if (!special && k.alts.isNotEmpty() && KeyboardPrefs.longPressSymbols && !small && k.alts.first().length == 1 && k.alts.first()[0].isDigit()) {
                        Text(k.alts.first(), color = th.sub, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
                    }
                    Text(k.label, color = fg, fontSize = if (small) (fs.value * .82f).sp else fs, maxLines = 1)
                }
            }
        }
    }
}

private fun popupLeft(rect: Rect, count: Int, rootW: Float, w: Float): Float {
    val total = count * w
    return (rect.center.x - total / 2f).coerceIn(0f, (rootW - total).coerceAtLeast(0f))
}

/** The fill, border and 3D edge of a key, per theme; pressed keys take a tint of the accent. */
private fun Modifier.keyLook(th: KbTheme, color: Color, pressed: Boolean, shape: RoundedCornerShape): Modifier {
    val c = if (pressed) lerp(color, th.accent, .4f) else color
    return if (th.bevel) this.drawBehind {
        val s = 2.dp.toPx(); val w = size.width; val h = size.height
        drawRect(c)
        val hi = if (pressed) Color(0xFF808080) else Color.White; val lo = if (pressed) Color.White else Color(0xFF808080)
        drawRect(hi, Offset.Zero, Size(w, s)); drawRect(hi, Offset.Zero, Size(s, h))
        drawRect(lo, Offset(0f, h - s), Size(w, s)); drawRect(lo, Offset(w - s, 0f), Size(s, h))
        drawRect(Color.Black, Offset(0f, h - 1.dp.toPx()), Size(w, 1.dp.toPx())); drawRect(Color.Black, Offset(w - 1.dp.toPx(), 0f), Size(1.dp.toPx(), h))
    } else this.clip(shape).background(c, shape).then(if (th.border != Color.Transparent) Modifier.border(1.dp, th.border, shape) else Modifier)
}

// ---- the pop-up over a pressed key -----------------------------------------------------------------------------------------------------------------

@Composable
private fun Preview(th: KbTheme, st: KbState) {
    val p = st.preview ?: return
    val shape = RoundedCornerShape((th.radius + 4).dp)
    val dens = androidx.compose.ui.platform.LocalDensity.current
    if (p.alts.isEmpty()) {
        val w = with(dens) { maxOf(p.rect.width, 52.dp.toPx()) }; val h = p.rect.height * 1.15f
        val x = (p.rect.center.x - w / 2f).coerceIn(0f, (st.rootW - w).coerceAtLeast(0f)); val y = p.rect.top - h - 2f
        Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.size(with(dens) { w.toDp() }, with(dens) { h.toDp() }).clip(shape).background(th.popup, shape), contentAlignment = Alignment.Center) {
            Text(p.label, color = th.text, fontSize = 30.sp, maxLines = 1)
        }
    } else {
        val w = with(dens) { POPUP_W.dp.toPx() }
        val left = popupLeft(p.rect, p.alts.size, st.rootW, w); val y = p.rect.top - p.rect.height * 1.15f - 2f
        Row(Modifier.offset { IntOffset(left.roundToInt(), y.roundToInt()) }.height(with(dens) { (p.rect.height * 1.15f).toDp() }).clip(shape).background(th.popup, shape)) {
            p.alts.forEachIndexed { i, s ->
                Box(Modifier.width(POPUP_W.dp).fillMaxSize().background(if (i == p.sel) th.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                    Text(s, color = if (i == p.sel) th.onAccent else th.text, fontSize = 22.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---- panels ----------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun EmojiPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    var tab by remember { mutableStateOf(if (st.recents.isEmpty()) 1 else 0) }
    val groups = listOf("🕘" to st.recents) + KeyboardLayouts.emoji
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        groups.forEachIndexed { i, g ->
            Box(Modifier.weight(1f).fillMaxSize().clickable { tab = i }.then(if (i == tab) Modifier.background(th.special) else Modifier), contentAlignment = Alignment.Center) { Text(g.first, fontSize = 20.sp) }
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxWidth().height(keyH * 3f)) {
        items(groups[tab.coerceIn(0, groups.lastIndex)].second) { e ->
            Box(Modifier.height(44.dp).clickable { act.emoji(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 26.sp) }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Key(th, st, act, KbKey("ABC", KeyKind.MODE, 2f), keyH * .85f)
        Spacer(5f)
        Key(th, st, act, KbKey("", KeyKind.DELETE, 2f), keyH * .85f)
    }
}

private class MenuItem(val id: String, val icon: ImageVector, val label: String, val on: Boolean = false)

@Composable
private fun MenuPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val items = listOf(
        MenuItem("settings", Icons.Outlined.Settings, "Settings"), MenuItem("theme", Icons.Outlined.Palette, "Themes"),
        MenuItem("languages", Icons.Outlined.Language, "Languages"), MenuItem("resize", Icons.Outlined.AspectRatio, "Resize"),
        MenuItem("incognito", Icons.Outlined.VisibilityOff, "Incognito", st.incognito), MenuItem("autocorrect", Icons.Outlined.Spellcheck, "Autocorrect", KeyboardPrefs.autocorrect),
        MenuItem("onehand", Icons.Outlined.TouchApp, "One-handed", KeyboardPrefs.oneHand != "off"), MenuItem("arrows", Icons.Outlined.OpenWith, "Arrows", KeyboardPrefs.arrows),
        MenuItem("numbers", Icons.Outlined.Pin, "Numbers", KeyboardPrefs.numberRow), MenuItem("clip", Icons.Outlined.ContentPaste, "Clipboard"),
        MenuItem("emoji", Icons.Outlined.EmojiEmotions, "Emoji"), MenuItem("back", Icons.Outlined.Keyboard, "Keys"),
    )
    Column(Modifier.fillMaxWidth().height(keyH * 4f).background(th.menu)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { m ->
                    Column(Modifier.weight(1f).fillMaxSize().clickable { act.menu(m.id) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(m.icon, null, tint = if (m.on) th.accent else th.text, modifier = Modifier.size(26.dp))
                        Text(m.label, color = if (m.on) th.accent else th.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().height(keyH * 4f).background(th.menu)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(st.clips) { s ->
                Text(s, color = th.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().clickable { act.paste(s) }.padding(horizontal = 14.dp, vertical = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth()) { Key(th, st, act, KbKey("ABC", KeyKind.MODE, 2f), keyH * .85f); Spacer(5f); Key(th, st, act, KbKey("", KeyKind.DELETE, 2f), keyH * .85f) }
    }
}

@Composable
private fun ResizePanel(th: KbTheme, st: KbState, keyH: androidx.compose.ui.unit.Dp) {
    val cols = SliderDefaults.colors(thumbColor = th.accent, activeTrackColor = th.accent, inactiveTrackColor = th.special)
    Column(Modifier.fillMaxWidth().height(keyH * 4f).background(th.menu).padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
        Slider(KeyboardPrefs.heightPct.toFloat(), { KeyboardPrefs.heightPct = it.roundToInt() }, valueRange = 70f..140f, colors = cols)
        Slider(KeyboardPrefs.bottomPad.toFloat(), { KeyboardPrefs.bottomPad = it.roundToInt() }, valueRange = 0f..60f, colors = cols)
        Slider(KeyboardPrefs.sidePad.toFloat(), { KeyboardPrefs.sidePad = it.roundToInt() }, valueRange = 0f..40f, colors = cols)
        Box(Modifier.align(Alignment.End).clip(RoundedCornerShape(20.dp)).background(th.accent).clickable { st.panel = "keys" }.padding(horizontal = 22.dp, vertical = 8.dp)) {
            Icon(Icons.Outlined.Check, null, tint = th.onAccent, modifier = Modifier.size(22.dp))
        }
    }
}
