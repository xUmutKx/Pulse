package com.umutk.pulse.extras.keyboard

import com.umutk.pulse.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Dashboard
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private const val POPUP_W = 44

/** The whole keyboard: the bar, the keys (or the emoji, menu, clipboard and resize panels) and the pop-up over a pressed key. */
@Composable
fun KeyboardView(st: KbState, act: KbActions, themeId: String = KeyboardPrefs.theme, modifier: Modifier = Modifier) {
    val th = KeyboardThemes.of(themeId, st.dark)
    val scale = KeyboardPrefs.heightPct / 100f
    val keyH = (61f * scale).dp
    val one = KeyboardPrefs.oneHand
    // a pull down on the bar: the keys follow the finger, and let go far enough they go away
    val pull = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dens = LocalDensity.current.density
    var rootH by remember { mutableStateOf(0f) }
    Box(modifier.fillMaxWidth().background(th.bg).onSizeChanged { st.rootW = it.width.toFloat(); rootH = it.height.toFloat() }.pointerInput(Unit) {
        // a quick swipe down anywhere on the keyboard: it slides away and the keyboard closes (watched first, so the keys still get their touches)
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val t0 = d.uptimeMillis; var done = false
            while (!done) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                if (!c.pressed) break
                val dy = c.position.y - d.position.y; val dx = c.position.x - d.position.x
                if (dy > 80.dp.toPx() && abs(dx) < dy * .45f && c.uptimeMillis - t0 < 420 && st.preview?.alts.isNullOrEmpty()) {
                    done = true; c.consume(); st.trail = emptyList()
                    act.hide()
                }
            }
        }
    }) {
        Column(
            (if (one == "off") Modifier.fillMaxWidth() else Modifier.fillMaxWidth(.82f).align(if (one == "left") Alignment.TopStart else Alignment.TopEnd))
                .padding(horizontal = KeyboardPrefs.sidePad.dp).padding(bottom = KeyboardPrefs.bottomPad.dp),
        ) {
            TopBar(th, st, act, onPull = { dy -> scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) } }, onRelease = {
                // a pull down on the bar closes the keyboard at once (nothing slides: the window behind it stayed black while it moved)
                if (pull.value > 56 * dens) act.hide()
                scope.launch { pull.snapTo(0f) }
            })
            if (KeyboardPrefs.anim) AnimatedContent(st.panel, transitionSpec = { (fadeIn(tween(130)) + slideInVertically(tween(160)) { it / 14 }) togetherWith fadeOut(tween(90)) }, label = "panel") { p -> Panel(p, th, st, act, keyH) }
            else Panel(st.panel, th, st, act, keyH)
        }
        Trail(th, st)
        Preview(th, st)
    }
}

@Composable
private fun Panel(p: String, th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column {
        when (p) {
            "emoji" -> EmojiPanel(th, st, act, keyH)
            "menu" -> MenuPanel(th, st, act, keyH)
            "clip" -> ClipPanel(th, st, act, keyH)
            "resize" -> ResizePanel(th, st, keyH)
            else -> KeysPanel(th, st, act, keyH)
        }
    }
}

/** The path of a swipe over the letters. */
@Composable
private fun Trail(th: KbTheme, st: KbState) {
    // the swipe-typing path is no longer drawn (the blue line); the keys it passes still spell the word
    val t = st.trail
    if (true || t.size < 2) return
    Canvas(Modifier.fillMaxSize()) {
        val path = Path().apply { moveTo(t[0].x, t[0].y); for (i in 1 until t.size) lineTo(t[i].x, t[i].y) }
        drawPath(path, th.accent.copy(alpha = .75f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---- the bar ---------------------------------------------------------------------------------------------------------------------------------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TopBar(th: KbTheme, st: KbState, act: KbActions, onPull: (Float) -> Unit, onRelease: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).pointerInput(Unit) {
            // a pull down on the bar closes the keyboard
            detectVerticalDragGestures(onDragEnd = { onRelease() }, onDragCancel = { onRelease() }) { change, dy -> change.consume(); onPull(dy) }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(th, if (st.toolbar || st.panel != "keys") kbIcon(th, "Close") else kbIcon(th, "ExpandMore")) { if (st.panel != "keys") st.panel = "keys" else st.toolbar = !st.toolbar }
        if (st.toolbar) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                KeyboardPrefs.toolbarItems.split(',').filter { id -> ExtraKeys.toolbar.any { it.first == id } }.forEach { id ->
                    BarIcon(th, toolIcon(th, id), on = toolOn(id, st)) { when (id) { "selectAll", "copy", "cut", "paste", "undo", "redo" -> act.edit(id); "emoji" -> act.emojiPanel(); "lang" -> act.lang(); else -> act.menu(id) } }
                }
            }
        } else {
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                st.slots.forEachIndexed { i, s ->
                    Box(Modifier.weight(1f).fillMaxSize().combinedClickable(enabled = s.isNotEmpty(), onClick = { if (st.banAsk == i) act.banSlot(i) else act.slot(i) }, onLongClick = { act.slotLong(i) }), contentAlignment = Alignment.Center) {
                        Text(if (st.banAsk == i) tr("Never suggest ✕", "Bir daha önerme ✕") else s, color = if (st.banAsk == i) th.accent else if (i == 1) th.text else th.sub, fontSize = 20.sp, fontWeight = if (i == 1) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarIcon(th: KbTheme, icon: ImageVector, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).border(1.5.dp, if (on) th.accent else th.sub, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (on) th.accent else th.text, modifier = Modifier.size(20.dp)) }
    }
}

private fun toolIcon(th: KbTheme, id: String): ImageVector = when (id) {
    "clip", "paste" -> kbIcon(th, "ContentPaste"); "emoji" -> kbIcon(th, "EmojiEmotions"); "selectAll" -> kbIcon(th, "SelectAll"); "copy" -> kbIcon(th, "ContentCopy")
    "cut" -> kbIcon(th, "ContentCut"); "undo" -> kbIcon(th, "Undo"); "redo" -> kbIcon(th, "Redo"); "menu" -> kbIcon(th, "MoreHoriz"); "resize" -> kbIcon(th, "AspectRatio")
    "incognito" -> kbIcon(th, "VisibilityOff"); "arrows" -> kbIcon(th, "OpenWith"); "proRow" -> kbIcon(th, "Dashboard"); "numbers" -> kbIcon(th, "Pin")
    "theme" -> kbIcon(th, "Palette"); "settings" -> kbIcon(th, "Settings"); "lang", "languages" -> kbIcon(th, "Language"); "onehand" -> kbIcon(th, "TouchApp")
    "autocorrect" -> kbIcon(th, "Spellcheck"); "glide" -> kbIcon(th, "Gesture")
    "voice" -> Icons.Rounded.Mic; "translate" -> Icons.Rounded.Translate; else -> kbIcon(th, "Keyboard")
}

private fun toolOn(id: String, st: KbState) = when (id) { "incognito" -> st.incognito; "autocorrect" -> KeyboardPrefs.autocorrect; "onehand" -> KeyboardPrefs.oneHand != "off"; "glide" -> KeyboardPrefs.glide; "arrows" -> KeyboardPrefs.arrows; "proRow" -> KeyboardPrefs.proRow; "numbers" -> KeyboardPrefs.numberRow; else -> false }

// ---- the keys --------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun KeysPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val up = st.shift > 0
    val digits = !KeyboardPrefs.numberRow && KeyboardPrefs.longPressSymbols
    val langs = KeyboardPrefs.langList()
    val lang = KeyboardLayouts.lang(if (st.lang in langs) st.lang else langs.first())
    val rows = KeyboardLayouts.letterRows(lang.code, up, digits)
    val n1 = rows[0].size.toFloat()
    if (KeyboardPrefs.proRow && !KeyboardPrefs.proBottom) ExtraRow(th, st, act, keyH)
    if (KeyboardPrefs.arrows) {
        Row(Modifier.fillMaxWidth()) {
            ActionKey(th, keyH * .8f, kbIcon(th, "ArrowBack"), true) { act.arrow(21) }
            ActionKey(th, keyH * .8f, kbIcon(th, "ArrowUpward"), true) { act.arrow(19) }
            ActionKey(th, keyH * .8f, kbIcon(th, "ArrowDownward"), true) { act.arrow(20) }
            ActionKey(th, keyH * .8f, kbIcon(th, "ArrowForward"), true) { act.arrow(22) }
            ActionKey(th, keyH * .8f, kbIcon(th, "SkipPrevious"), false) { act.arrow(122) }
            ActionKey(th, keyH * .8f, kbIcon(th, "SkipNext"), false) { act.arrow(123) }
            ActionKey(th, keyH * .8f, kbIcon(th, "SelectAll"), false) { act.edit("selectAll") }
            ActionKey(th, keyH * .8f, kbIcon(th, "ContentCopy"), false) { act.edit("copy") }
            ActionKey(th, keyH * .8f, kbIcon(th, "ContentPaste"), false) { act.edit("paste") }
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
        if (KeyboardPrefs.emojiKey) Key(th, st, act, KbKey("", KeyKind.EMOJI, 1f), keyH)
        Key(th, st, act, KbKey(",", KeyKind.COMMA, 1f, if (KeyboardPrefs.emojiKey) KeyboardLayouts.popupComma else listOf("😀") + KeyboardLayouts.popupComma), keyH)
        Key(th, st, act, KbKey(if (KeyboardPrefs.spaceLabel) lang.name else "", KeyKind.SPACE, 4f), keyH)
        Key(th, st, act, KbKey(".", KeyKind.PERIOD, 1f, KeyboardLayouts.popupPeriod), keyH)
        Key(th, st, act, KbKey("", KeyKind.ENTER, 1.5f), keyH)
    }
    if (KeyboardPrefs.proRow && KeyboardPrefs.proBottom) ExtraRow(th, st, act, keyH)
}

@Composable
private fun RowScope.Spacer(w: Float) { Box(Modifier.weight(w)) }

/** The extra row: Esc, Tab, Ctrl, Alt, Win, arrows, F-keys, ... as many as the user put there. The modifier keys wait for the next key. */
@Composable
private fun ExtraRow(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val defs = KeyboardPrefs.proKeys.split(',').mapNotNull { ExtraKeys.byId(it) }
    if (defs.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = if (defs.size * 42 <= maxWidth.value) maxWidth / defs.size else 46.dp
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            defs.forEach { d ->
                val level = when (d.id) { "ctrl" -> st.ctrl; "alt" -> st.alt; "win" -> st.win; "shiftm" -> st.shiftMod; else -> 0 }
                ExtraCell(th, w, keyH * .78f, d, level) { act.extraKey(d.id) }
            }
        }
    }
}

@Composable
private fun ExtraCell(th: KbTheme, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, d: ExtraKeyDef, level: Int, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier.width(w).height(h).pointerInput(d.id) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false)
                down = true
                if (d.repeat) {
                    tap()
                    var due = android.os.SystemClock.uptimeMillis() + 380L
                    while (true) {
                        val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                        if (ev == null) { tap(); due = android.os.SystemClock.uptimeMillis() + 70L; continue }
                        val c = ev.changes.firstOrNull { it.id == first.id } ?: break
                        if (c.changedToUp() || !c.pressed || (c.position - first.position).getDistance() > 24.dp.toPx()) break
                    }
                } else {
                    // a drag along the row scrolls it: only a finger that lifts where it came down is a tap
                    val up = waitForUpOrCancellation()
                    if (up != null) tap()
                }
                down = false
            }
        }.padding(KeyboardPrefs.keyGap.dp / 2),
    ) {
        val on = level > 0
        Box(Modifier.fillMaxSize().keyLook(th, if (on) th.accent else if (down) lerp(th.special, th.text, .2f) else th.special, down, RoundedCornerShape(th.radius.dp)), contentAlignment = Alignment.Center) {
            Text(d.label, color = if (on) th.onAccent else th.specialText, fontSize = 13.sp, maxLines = 1, fontWeight = if (level == 2) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

/** A key of the arrow row: a little special key that repeats while held when [repeat]. */
@Composable
private fun RowScope.ActionKey(th: KbTheme, h: androidx.compose.ui.unit.Dp, icon: ImageVector, repeat: Boolean, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    var down by remember { mutableStateOf(false) }
    Box(
        Modifier.weight(1f).height(h).pointerInput(repeat) {
            awaitEachGesture {
                val d = awaitFirstDown(requireUnconsumed = false); d.consume(); down = true; tap()
                var due = android.os.SystemClock.uptimeMillis() + 380L
                while (true) {
                    val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                    if (ev == null) { if (repeat) tap(); due = android.os.SystemClock.uptimeMillis() + 70L; continue }
                    val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                    c.consume()
                    if (c.changedToUp() || !c.pressed) break
                }
                down = false
            }
        }.padding(KeyboardPrefs.keyGap.dp / 2),
    ) {
        Box(Modifier.fillMaxSize().keyLook(th, if (down) lerp(th.special, th.text, .2f) else th.special, down, RoundedCornerShape(th.radius.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = th.specialText, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun RowScope.Key(th: KbTheme, st: KbState, act: KbActions, k: KbKey, keyH: androidx.compose.ui.unit.Dp, small: Boolean = false) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    var down by remember { mutableStateOf(false) }
    val actNow by rememberUpdatedState(act)
    val kNow by rememberUpdatedState(k)
    val longMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val isLetter = k.kind == KeyKind.CHAR && k.label.length == 1 && k.label[0].isLetter()
    val bg = when (k.kind) { KeyKind.CHAR, KeyKind.SPACE, KeyKind.ENTER -> lerp(th.key, androidx.compose.ui.graphics.Color(0xFF808080), .07f); else -> th.special }
    val fill by animateColorAsState(if (down) lerp(bg, th.text, .2f) else bg, tween(if (KeyboardPrefs.anim) 90 else 0), label = "key")
    val grow by animateFloatAsState(if (down && KeyboardPrefs.anim) .93f else 1f, tween(70), label = "keyScale")
    Box(
        Modifier.weight(k.weight).height(if (small) keyH * .78f else keyH).onGloballyPositioned { rect = it.boundsInRoot(); if (isLetter) st.letterRects[k.label.lowercase()] = rect }
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
                    var delRepeats = 0
                    var startX = d.position.x
                    var curX = d.position.x
                    // the long press and the repeats fall due at a fixed time: the finger's small moves must not push them back
                    var due = android.os.SystemClock.uptimeMillis() + longMs
                    var glide = false
                    val seq = StringBuilder()
                    val startRoot = Offset(rect.left + d.position.x, rect.top + d.position.y)
                    var curRoot = startRoot
                    val canGlide = isLetter && KeyboardPrefs.glide && st.page == 0 && st.panel == "keys"
                    if (key.kind == KeyKind.DELETE) { a.backspace(); due = android.os.SystemClock.uptimeMillis() + 380L }
                    while (true) {
                        val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                        if (ev == null) {
                            // the long press (or the next repeat of the delete key)
                            val now = android.os.SystemClock.uptimeMillis()
                            when (key.kind) {
                                KeyKind.DELETE -> {
                                    // held long enough: a whole word per repeat instead of a letter
                                    if (!quick) { delRepeats++; if (delRepeats > 9) { a.deleteWord(); due = now + 170L } else { a.backspace(); due = now + 55L } } else due = now + 55L
                                }
                                KeyKind.SPACE -> { if (KeyboardPrefs.cursorControl) { long = true; spaceDragged = true; startX = curX; lastStep = 0; a.feedback() }; due = Long.MAX_VALUE / 2 }
                                KeyKind.SHIFT -> { long = true; due = Long.MAX_VALUE / 2 }
                                KeyKind.COMMA -> { long = true; st.preview = null; a.feedback(); a.voice(); due = Long.MAX_VALUE / 2 }
                                else -> if (!glide && key.alts.isNotEmpty() && big) {
                                    long = true; sel = 0; due = Long.MAX_VALUE / 2
                                    st.preview = KbPreview(rect, key.label, key.alts, 0); a.feedback()
                                } else due = Long.MAX_VALUE / 2
                            }
                            continue
                        }
                        val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                        c.consume()
                        curX = c.position.x
                        curRoot = Offset(rect.left + c.position.x, rect.top + c.position.y)
                        if (c.changedToUp() || !c.pressed) {
                            val up = (startRoot.y - curRoot.y)
                            when {
                                key.kind == KeyKind.DELETE -> if (quick) a.quickDeleteEnd(true)
                                glide && seq.length >= 3 -> a.glide(seq.toString())
                                glide && key.alts.isNotEmpty() && KeyboardPrefs.flick && up > 24.dp.toPx() -> a.type(key.alts[0])
                                glide -> a.type(key.label)
                                long && sel >= 0 && key.alts.isNotEmpty() -> a.type(key.alts[sel.coerceIn(0, key.alts.lastIndex)])
                                spaceDragged -> {}
                                long && key.kind == KeyKind.SHIFT -> a.edit("capslock")
                                !long -> when (key.kind) {
                                    KeyKind.CHAR, KeyKind.COMMA, KeyKind.PERIOD -> {
                                        // a quick flick upwards types the first long-press character
                                        if (KeyboardPrefs.flick && key.alts.isNotEmpty() && up > 24.dp.toPx() && big) a.type(key.alts[0]) else a.type(key.label)
                                    }
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
                            // a swipe over the letters: the keys it passes spell the word
                            canGlide && !long && (glide || (curRoot - startRoot).getDistance() > 30.dp.toPx()) -> {
                                if (!glide) { glide = true; st.preview = null; seq.append(key.label.lowercase()); st.trail = listOf(startRoot) }
                                st.letterRects.entries.firstOrNull { it.value.contains(curRoot) }?.key?.let { l -> if (seq.isEmpty() || seq.last().toString() != l) seq.append(l) }
                                if (st.trail.size < 400) st.trail = st.trail + curRoot
                            }
                            key.kind == KeyKind.DELETE && KeyboardPrefs.quickDelete && !quick && dx < -28.dp.toPx() -> { quick = true; a.quickDeleteStart(); startX = c.position.x }
                            key.kind == KeyKind.DELETE && quick -> a.quickDelete((-dx / 14.dp.toPx()).toInt().coerceAtLeast(0))
                            // sliding along the space key moves the cursor, held first or not
                            key.kind == KeyKind.SPACE && KeyboardPrefs.cursorControl && !spaceDragged && abs(dx) > 18.dp.toPx() -> { spaceDragged = true; long = true; startX = c.position.x; lastStep = 0; a.feedback() }
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
                    st.trail = emptyList()
                }
            }.padding(horizontal = KeyboardPrefs.keyGap.dp * .75f, vertical = KeyboardPrefs.keyGap.dp / 2),
    ) {
        val special = k.kind != KeyKind.CHAR && k.kind != KeyKind.SPACE
        val fg = when (k.kind) { KeyKind.CHAR, KeyKind.SPACE, KeyKind.ENTER -> th.text; else -> th.specialText }
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = grow; scaleY = grow }.keyLook(th, fill, down, RoundedCornerShape(th.radius.dp)), contentAlignment = Alignment.Center) {
            val big = if (KeyboardPrefs.largeText) 27f else 22f
            val fs = (big * (.85f + .15f * KeyboardPrefs.heightPct / 100f)).sp
            when (k.kind) {
                KeyKind.SHIFT -> Icon(if (st.shift == 2) kbIcon(th, "KeyboardCapslock") else if (st.shift == 1) kbIcon(th, "ArrowUpward", filled = true) else kbIcon(th, "ArrowUpward"), null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.DELETE -> Icon(kbIcon(th, "Backspace"), null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.ENTER -> Icon(when (st.enter) { "send" -> kbIcon(th, "Send"); "search" -> kbIcon(th, "Search"); "go", "done" -> kbIcon(th, "Check"); "next" -> kbIcon(th, "ArrowForward"); else -> kbIcon(th, "KeyboardReturn") }, null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.EMOJI -> Icon(kbIcon(th, "EmojiEmotions"), null, tint = fg, modifier = Modifier.size(24.dp))
                KeyKind.LANG -> Icon(kbIcon(th, "Language"), null, tint = fg, modifier = Modifier.size(24.dp))
                KeyKind.SPACE -> Text(k.label, color = th.sub, fontSize = 12.sp, maxLines = 1)
                KeyKind.MODE, KeyKind.SYMBOLS2 -> Text(k.label, color = fg, fontSize = 15.sp, maxLines = 1)
                else -> {
                    if (!special && k.alts.isNotEmpty() && KeyboardPrefs.longPressSymbols && !small && k.alts.first().length == 1 && k.alts.first()[0].isDigit()) {
                        Text(k.alts.first(), color = th.text.copy(alpha = .75f), fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
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
    val c = color
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
    val pop = remember(p.rect, p.alts.isEmpty()) { Animatable(if (KeyboardPrefs.anim) .75f else 1f) }
    LaunchedEffect(p.rect, p.alts.isEmpty()) { pop.animateTo(1f, tween(110)) }
    if (p.alts.isEmpty()) {
        val w = with(dens) { maxOf(p.rect.width, 52.dp.toPx()) }; val h = p.rect.height * 1.15f
        val x = (p.rect.center.x - w / 2f).coerceIn(0f, (st.rootW - w).coerceAtLeast(0f)); val y = p.rect.top - h - 2f
        Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.size(with(dens) { w.toDp() }, with(dens) { h.toDp() }).clip(shape).background(th.popup, shape), contentAlignment = Alignment.Center) {
            Text(p.label, color = th.text, fontSize = 30.sp, maxLines = 1)
        }
    } else {
        val w = with(dens) { POPUP_W.dp.toPx() }
        val left = popupLeft(p.rect, p.alts.size, st.rootW, w); val y = p.rect.top - p.rect.height * 1.15f - 2f
        Row(Modifier.offset { IntOffset(left.roundToInt(), y.roundToInt()) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.height(with(dens) { (p.rect.height * 1.15f).toDp() }).clip(shape).background(th.popup, shape)) {
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
    val ids = KeyboardPrefs.menuItems.split(',').filter { id -> ExtraKeys.menu.any { it.first == id } }
    val items = ids.map { id -> MenuItem(id, toolIcon(th, id), ExtraKeys.menu.first { it.first == id }.second, toolOn(id, st)) } + MenuItem("back", kbIcon(th, "Keyboard"), "Keys")
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(keyH * 4f).background(th.menu)) {
        items(items) { m ->
            Column(
                Modifier.height(keyH).clickable { when (m.id) { "selectAll", "copy", "cut", "paste", "undo", "redo" -> act.edit(m.id); "emoji" -> act.emojiPanel(); "lang" -> act.lang(); else -> act.menu(m.id) } },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Icon(m.icon, null, tint = if (m.on) th.accent else th.text, modifier = Modifier.size(24.dp))
                Text(m.label, color = if (m.on) th.accent else th.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
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

/** Resizing on the keyboard itself: pull the top edge for the height, the bottom edge for the space below, the side edges for the space at the sides. */
@Composable
private fun ResizePanel(th: KbTheme, st: KbState, keyH: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    var h by remember { mutableFloatStateOf(KeyboardPrefs.heightPct.toFloat()) }
    var b by remember { mutableFloatStateOf(KeyboardPrefs.bottomPad.toFloat()) }
    var sd by remember { mutableFloatStateOf(KeyboardPrefs.sidePad.toFloat()) }
    val bar = Modifier.clip(RoundedCornerShape(3.dp)).background(th.accent)
    Box(Modifier.fillMaxWidth().border(2.dp, th.accent, RoundedCornerShape(6.dp))) {
        Column { KeysPanel(th, st, NoKbActions, keyH) }
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(30.dp).pointerInput(Unit) { detectVerticalDragGestures { _, dy -> h = (h - dy / dens / 2.2f).coerceIn(70f, 170f); KeyboardPrefs.heightPct = h.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(56.dp).height(5.dp)) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp).pointerInput(Unit) { detectVerticalDragGestures { _, dy -> b = (b - dy / dens).coerceIn(0f, 60f); KeyboardPrefs.bottomPad = b.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(56.dp).height(5.dp)) }
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp).pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> sd = (sd + dx / dens).coerceIn(0f, 40f); KeyboardPrefs.sidePad = sd.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(5.dp).height(56.dp)) }
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp).pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> sd = (sd - dx / dens).coerceIn(0f, 40f); KeyboardPrefs.sidePad = sd.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(5.dp).height(56.dp)) }
        Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp)).background(th.accent).clickable { KeyboardPrefs.save(ctx); st.panel = "keys" }.padding(horizontal = 26.dp, vertical = 12.dp)) {
            Icon(kbIcon(th, "Check"), null, tint = th.onAccent, modifier = Modifier.size(24.dp))
        }
    }
}
