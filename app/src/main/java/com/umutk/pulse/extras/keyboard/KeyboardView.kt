package com.umutk.pulse.extras.keyboard

import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.EmojiSymbols
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.KeyboardTab
import androidx.compose.material.icons.rounded.KeyboardCapslock
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import com.umutk.pulse.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
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
/** The running hue (0..360) of the moving colour light; read only while drawing, so the keys do not recompose with it. */
private val LocalHue = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.State<Float>?> { null }

@Composable
fun KeyboardView(st: KbState, act: KbActions, themeId: String = KeyboardPrefs.theme, modifier: Modifier = Modifier) {
    val th = KeyboardThemes.of(themeId, st.dark)
    val hue: androidx.compose.runtime.State<Float>? = if (KeyboardPrefs.rgb > 0) androidx.compose.animation.core.rememberInfiniteTransition(label = "rgb").animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(7000, easing = androidx.compose.animation.core.LinearEasing)), label = "hue") else null
    androidx.compose.runtime.CompositionLocalProvider(LocalHue provides hue) { KeyboardViewBody(th, st, act, modifier) }
}

@Composable
private fun KeyboardViewBody(th: KbTheme, st: KbState, act: KbActions, modifier: Modifier) {
    val scale = KeyboardPrefs.heightPct / 100f
    val keyH = (61f * scale).dp
    val one = KeyboardPrefs.oneHand
    // a pull down on the bar: the keys follow the finger, and let go far enough they go away
    val pull = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dens = LocalDensity.current.density
    var rootH by remember { mutableStateOf(0f) }
    val rootHue = LocalHue.current
    Box(modifier.fillMaxWidth().background(th.bg)
        .then(if (KeyboardPrefs.rgb == 1 && rootHue != null) Modifier.drawWithContent { drawContent(); val h = rootHue.value; drawRect(androidx.compose.ui.graphics.Brush.sweepGradient(List(8) { i -> Color.hsv((h + i * 45f) % 360f, .9f, 1f) }), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())) } else Modifier)
        .onSizeChanged { st.rootW = it.width.toFloat(); rootH = it.height.toFloat() }.pointerInput(Unit) {
        // a quick swipe down anywhere on the keyboard: it slides away and the keyboard closes (watched first, so the keys still get their touches)
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            st.swiped = false
            val t0 = d.uptimeMillis; var done = false
            while (!done) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                if (!c.pressed) break
                val dy = c.position.y - d.position.y; val dx = c.position.x - d.position.x
                if (dy > 80.dp.toPx() && abs(dx) < dy * .45f && c.uptimeMillis - t0 < 420 && st.preview?.alts.isNullOrEmpty()) {
                    done = true; c.consume(); st.trail = emptyList(); st.swiped = true; st.preview = null
                    act.hide()
                }
            }
        }
    }) {
        Column(
            (if (KeyboardPrefs.floating) Modifier.fillMaxWidth(.7f).align(Alignment.TopStart).offset { IntOffset((st.rootW * .3f * KeyboardPrefs.floatX).roundToInt(), 0) }
                else if (one == "off") Modifier.fillMaxWidth() else Modifier.fillMaxWidth(.82f).align(if (one == "left") Alignment.TopStart else Alignment.TopEnd))
                .padding(horizontal = KeyboardPrefs.sidePad.dp).padding(bottom = KeyboardPrefs.bottomPad.dp),
        ) {
            // the floating keyboard is narrower and slides sideways by its handle
            if (KeyboardPrefs.floating) Box(Modifier.fillMaxWidth().height(20.dp).pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = { KeyboardPrefs.saveFloat() }) { c, dx -> c.consume(); KeyboardPrefs.floatX = (KeyboardPrefs.floatX + dx / (st.rootW * .3f).coerceAtLeast(1f)).coerceIn(0f, 1f) }
            }, contentAlignment = Alignment.Center) { Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(th.sub.copy(alpha = .6f))) }
            if (!st.numpad) Box { TopBar(th, st, act, onPull = { dy -> scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) } }, onRelease = {
                // a pull down on the bar closes the keyboard at once (nothing slides: the window behind it stayed black while it moved)
                if (pull.value > 56 * dens) act.hide()
                scope.launch { pull.snapTo(0f) }
            }); if (KeyboardPrefs.topBright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.topBright) / 100f * .85f))) }
            if (KeyboardPrefs.anim) AnimatedContent(st.panel, transitionSpec = { (fadeIn(tween(130)) + slideInVertically(tween(160)) { it / 14 }) togetherWith fadeOut(tween(90)) }, label = "panel") { p -> Panel(p, th, st, act, keyH) }
            else Panel(st.panel, th, st, act, keyH)
        }
        // the brightness: a black veil over the keys (the pop-up above stays bright)
        if (KeyboardPrefs.bright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.bright) / 100f * .85f)))
        Trail(th, st)
        Preview(th, st)
    }
}

@Composable
private fun Panel(p: String, th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column(Modifier.onSizeChanged { if (p == "keys" && it.height > 0) st.keysH = it.height }) {
        when (p) {
            "emoji" -> EmojiPanel(th, st, act, keyH)
            "menu" -> MenuPanel(th, st, act, keyH)
            "clip" -> ClipPanel(th, st, act, keyH)
            "resize" -> ResizePanel(th, st, keyH)
            "keyset" -> KeysetPanel(th, st, act, keyH)
            else -> if (st.numpad) NumPad(th, st, act, keyH) else KeysPanel(th, st, act, keyH)
        }
    }
}

/** The number pad of number, phone, date and numeric-password fields: big digits and nothing else. */
@Composable
private fun NumPad(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val h = keyH * .95f
    listOf("123", "456", "789").forEach { line ->
        Row(Modifier.fillMaxWidth()) { line.forEach { d -> Key(th, st, act, KbKey(d.toString(), KeyKind.CHAR, 1f), h) } }
    }
    Row(Modifier.fillMaxWidth()) {
        Key(th, st, act, KbKey(".", KeyKind.CHAR, 1f), h)
        Key(th, st, act, KbKey("0", KeyKind.CHAR, 1f), h)
        Key(th, st, act, KbKey("", KeyKind.DELETE, 1f), h)
    }
    Row(Modifier.fillMaxWidth()) {
        Key(th, st, act, KbKey("*", KeyKind.CHAR, 1f), h * .8f)
        Key(th, st, act, KbKey("#", KeyKind.CHAR, 1f), h * .8f)
        Key(th, st, act, KbKey("", KeyKind.ENTER, 1f), h * .8f)
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
        } else if (st.inline.isNotEmpty()) {
            // a password manager has something for this field (login, password, card): its own suggestions take the bar
            Row(Modifier.weight(1f).fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                st.inline.forEach { v ->
                    androidx.compose.ui.viewinterop.AndroidView(factory = { c ->
                        android.widget.FrameLayout(c).also { f -> (v.parent as? android.view.ViewGroup)?.removeView(v); f.addView(v) }
                    })
                }
            }
        } else {
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                st.slots.forEachIndexed { i, s ->
                    Box(Modifier.weight(1f).fillMaxSize().combinedClickable(enabled = s.isNotEmpty(), onClick = { if (st.banAsk == i) act.banSlot(i) else act.slot(i) }, onLongClick = { act.slotLong(i) }), contentAlignment = Alignment.Center) {
                        if (st.banAsk == i) Icon(Icons.Rounded.Block, tr("Never suggest", "Bir daha önerme"), tint = th.accent, modifier = Modifier.size(26.dp))
                        else if (s.startsWith("\u0001")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(kbIcon(th, "ContentPaste"), null, tint = th.sub, modifier = Modifier.size(16.dp))
                            Text(s.drop(1).replace('\n', ' ').take(14) + if (s.length > 15) "…" else "", color = th.sub, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                        } else Text(s, color = if (i == 1) th.text else th.sub, fontSize = 20.sp, fontWeight = if (i == 1) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
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
    "incognito" -> kbIcon(th, "VisibilityOff"); "arrows" -> kbIcon(th, "OpenWith"); "proRow" -> kbIcon(th, "Dashboard"); "keyset" -> kbIcon(th, "Keyboard"); "numbers" -> kbIcon(th, "Pin")
    "theme" -> kbIcon(th, "Palette"); "settings" -> kbIcon(th, "Settings"); "lang", "languages" -> kbIcon(th, "Language"); "onehand" -> kbIcon(th, "TouchApp")
    "autocorrect" -> kbIcon(th, "Spellcheck"); "glide" -> kbIcon(th, "Gesture")
    "voice" -> Icons.Rounded.Mic; "translate" -> Icons.Rounded.Translate; else -> kbIcon(th, "Keyboard")
}

private fun toolOn(id: String, st: KbState) = when (id) { "incognito" -> st.incognito; "autocorrect" -> KeyboardPrefs.autocorrect; "onehand" -> KeyboardPrefs.oneHand != "off"; "glide" -> KeyboardPrefs.glide; "arrows" -> KeyboardPrefs.arrows; "keyset" -> st.panel == "keyset"; "proRow" -> KeyboardPrefs.proRow; "numbers" -> KeyboardPrefs.numberRow; else -> false }

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
        // split keys (for a tablet): a gap in the middle of each row, so both thumbs reach their half
        val gapW = if (KeyboardPrefs.split) 2f else 0f
        Row(Modifier.fillMaxWidth()) { rows[0].forEachIndexed { i, k -> if (gapW > 0 && i == rows[0].size / 2) Spacer(gapW); Key(th, st, act, k, keyH) } }
        Row(Modifier.fillMaxWidth()) {
            val sp = (n1 - rows[1].size) / 2f
            if (sp > 0) Spacer(sp); rows[1].forEachIndexed { i, k -> if (gapW > 0 && i == rows[1].size / 2) Spacer(gapW); Key(th, st, act, k, keyH) }; if (sp > 0) Spacer(sp)
        }
        Row(Modifier.fillMaxWidth()) {
            val side = ((n1 - rows[2].size) / 2f).coerceIn(1f, 2f)
            val rest = n1 - rows[2].size - 2 * side
            Key(th, st, act, KbKey("", KeyKind.SHIFT, side), keyH)
            if (rest > 0) Spacer(rest / 2f)
            rows[2].forEachIndexed { i, k -> if (gapW > 0 && i == rows[2].size / 2) Spacer(gapW); Key(th, st, act, k, keyH) }
            if (rest > 0) Spacer(rest / 2f)
            Key(th, st, act, KbKey("", KeyKind.DELETE, side), keyH)
        }
    } else {
        val src = if (st.page == 1) KeyboardLayouts.symbols1 else KeyboardLayouts.symbols2
        src.forEachIndexed { r, line ->
            Row(Modifier.fillMaxWidth()) {
                val side = if (st.page == 1) 1f else 1.5f
                if (r == 2) Key(th, st, act, KbKey(if (st.page == 1) "{&=" else "1/2", KeyKind.SYMBOLS2, side), keyH)
                val w = if (r == 2) (n1 - 2 * side) / line.length else n1 / line.length
                line.forEach { ch -> Key(th, st, act, KbKey(ch.toString(), KeyKind.CHAR, w, KeyboardLayouts.symbolAlts[ch].orEmpty()), keyH) }
                if (r == 2) Key(th, st, act, KbKey("", KeyKind.DELETE, side), keyH)
            }
        }
    }
    // the optional compact bottom row: lower keys and a Shift key at the left
    val low = KeyboardPrefs.bottomShift
    val bh = if (low) keyH * .8f else keyH
    Box {
    Row(Modifier.fillMaxWidth()) {
        if (low && st.page == 0) Key(th, st, act, KbKey("", KeyKind.SHIFT, 1f), bh)
        Key(th, st, act, KbKey(if (st.page == 0) "?123" else "abc", KeyKind.MODE, if (low) 1.25f else 1.5f), bh)
        if (KeyboardPrefs.emojiKey && st.page == 0) Key(th, st, act, KbKey("", KeyKind.EMOJI, 1f), bh)
        Key(th, st, act, KbKey(",", KeyKind.COMMA, 1f, if (KeyboardPrefs.emojiKey) KeyboardLayouts.popupComma else listOf("😀") + KeyboardLayouts.popupComma), bh)
        Key(th, st, act, KbKey(if (KeyboardPrefs.spaceLabel) lang.name else "", KeyKind.SPACE, 4f), bh)
        Key(th, st, act, KbKey(".", KeyKind.PERIOD, 1f, KeyboardLayouts.popupPeriod), bh)
        Key(th, st, act, KbKey("", KeyKind.ENTER, 1.5f), bh)
    }
    if (KeyboardPrefs.bottomBright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.bottomBright) / 100f * .85f)))
    }
    if (KeyboardPrefs.proRow && KeyboardPrefs.proBottom) Box { ExtraRow(th, st, act, keyH); if (KeyboardPrefs.bottomBright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.bottomBright) / 100f * .85f))) }
}

@Composable
private fun RowScope.Spacer(w: Float) { Box(Modifier.weight(w)) }

/** A key set of its own, opened from the menu: modifiers, the F keys, the navigation keys and the arrows, in rows the width of the keyboard; "abc" goes back to the letters. */
@Composable
private fun KeysetPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val rows = listOf(
        listOf("esc", "tab", "ctrl", "alt", "shiftm", "win"),
        listOf("f1", "f2", "f3", "f4", "f5", "f6"),
        listOf("f7", "f8", "f9", "f10", "f11", "f12"),
        listOf("home", "end", "pgup", "pgdn", "ins", "del"),
        listOf("left", "up", "down", "right", "selectAll", "copy"),
    )
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth()) {
            r.mapNotNull { ExtraKeys.byId(it) }.forEach { d ->
                val level = when (d.id) { "ctrl" -> st.ctrl; "alt" -> st.alt; "win" -> st.win; "shiftm" -> st.shiftMod; else -> 0 }
                ExtraCell(th, 0.dp, keyH * .8f, d, level, Modifier.weight(1f)) { act.extraKey(d.id) }
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(keyH * .8f).padding(2.dp).clip(RoundedCornerShape(th.radius.dp)).background(th.special).clickable { st.panel = "keys" }, contentAlignment = Alignment.Center) { Text("abc", color = th.specialText, fontSize = 15.sp) }
    }
}

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

private fun extraIcon(id: String): ImageVector? = when (id) {
    "tab" -> Icons.Rounded.KeyboardTab; "shiftm" -> Icons.Rounded.KeyboardCapslock
    "left" -> Icons.Rounded.ArrowBack; "right" -> Icons.Rounded.ArrowForward; "up" -> Icons.Rounded.ArrowUpward; "down" -> Icons.Rounded.ArrowDownward
    "pgup" -> Icons.Rounded.KeyboardDoubleArrowUp; "pgdn" -> Icons.Rounded.KeyboardDoubleArrowDown; "home" -> Icons.Rounded.Home; "del" -> Icons.Rounded.Backspace
    "selectAll" -> Icons.Rounded.SelectAll; "copy" -> Icons.Rounded.ContentCopy; "cut" -> Icons.Rounded.ContentCut; "paste" -> Icons.Rounded.ContentPaste
    "undo" -> Icons.Rounded.Undo; "redo" -> Icons.Rounded.Redo
    else -> null
}

@Composable
private fun ExtraCell(th: KbTheme, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, d: ExtraKeyDef, level: Int, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    var down by remember { mutableStateOf(false) }
    Box(
        modifier.then(if (w > 0.dp) Modifier.width(w) else Modifier).height(h).pointerInput(d.id) {
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
            val ink = if (on) th.onAccent else th.specialText
            if (d.id == "win") androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
                val g = size.width * .08f; val q = (size.width - g) / 2
                for (i in 0..1) for (j in 0..1) drawRect(ink, androidx.compose.ui.geometry.Offset(i * (q + g), j * (q + g)), androidx.compose.ui.geometry.Size(q, q))
            } else if (KeyboardPrefs.proIcons && extraIcon(d.id) != null) Icon(extraIcon(d.id)!!, null, tint = ink, modifier = Modifier.size(20.dp))
            else Text(d.label, color = ink, fontSize = 13.sp, maxLines = 1, fontWeight = if (level == 2) FontWeight.Bold else FontWeight.Normal)
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
    LaunchedEffect(k.kind) { down = false }
    val isLetter = k.kind == KeyKind.CHAR && k.label.length == 1 && k.label[0].isLetter()
    val isLetterNow by rememberUpdatedState(isLetter)
    val bg = when (k.kind) { KeyKind.CHAR, KeyKind.SPACE, KeyKind.ENTER -> lerp(th.key, androidx.compose.ui.graphics.Color(0xFF808080), .07f); else -> th.special }
    val fill by animateColorAsState(if (down) lerp(bg, th.text, .2f) else bg, tween(if (KeyboardPrefs.anim) 90 else 0), label = "key")
    val grow by animateFloatAsState(if (down && KeyboardPrefs.anim) .93f else 1f, tween(70), label = "keyScale")
    Box(
        Modifier.weight(k.weight).height(if (small) keyH * .78f else keyH).onGloballyPositioned { rect = it.boundsInRoot(); if (isLetter) st.letterRects[k.label.lowercase()] = rect }
            // not keyed on the label: a shift change renames every letter key, and a restart would drop a touch that is under way (the key then stayed shown as pressed and never typed)
            .pointerInput(k.kind) {
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
                    val canGlide = isLetterNow && KeyboardPrefs.glide && st.page == 0 && st.panel == "keys"
                    if (key.kind == KeyKind.DELETE) { a.backspace(); due = android.os.SystemClock.uptimeMillis() + 380L }
                    while (true) {
                        if (st.swiped) { st.preview = null; break }
                        val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                        if (ev == null) {
                            // the long press (or the next repeat of the delete key)
                            val now = android.os.SystemClock.uptimeMillis()
                            when (key.kind) {
                                KeyKind.DELETE -> {
                                    // held long enough: a whole word per repeat instead of a letter
                                    if (!quick) { delRepeats++; if (delRepeats > 0) { a.deleteWord(); due = now + maxOf(55L, 230L - delRepeats * 22L) } else { a.backspace(); due = now + 55L } } else due = now + 55L
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
                            if (st.swiped) { st.preview = null; break }
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
                                        st.touch = if (key.kind == KeyKind.CHAR) curRoot else null
                                        if (KeyboardPrefs.flick && key.alts.isNotEmpty() && up > 24.dp.toPx() && big) { st.touch = null; a.type(key.alts[0]) } else a.type(key.label)
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
        val keyHue = LocalHue.current
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = grow; scaleY = grow }.keyLook(th, fill, down, RoundedCornerShape(th.radius.dp))
            .then(if (KeyboardPrefs.rgb == 2 && keyHue != null) Modifier.drawWithContent { drawContent(); val h = (keyHue.value + rect.left / maxOf(1f, st.rootW) * 300f) % 360f; drawRoundRect(Color.hsv(h, .9f, 1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(th.radius.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())) } else Modifier), contentAlignment = Alignment.Center) {
            val big = if (KeyboardPrefs.largeText) 27f else 22f
            val fs = (big * (.85f + .15f * KeyboardPrefs.heightPct / 100f)).sp
            when (k.kind) {
                KeyKind.SHIFT -> ShiftGlyph(st.shift, fg)
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
                    // the comma key holds voice typing on a long press: a small microphone above it says so
                    if (k.kind == KeyKind.COMMA && !small) Icon(Icons.Rounded.Mic, null, tint = th.text.copy(alpha = .55f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(13.dp))
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
        val x = (p.rect.center.x - w / 2f).coerceIn(0f, (st.rootW - w).coerceAtLeast(0f)); val y = (p.rect.top - h - 2f).coerceAtLeast(0f)
        Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.size(with(dens) { w.toDp() }, with(dens) { h.toDp() }).clip(shape).background(th.popup, shape), contentAlignment = Alignment.Center) {
            Text(p.label, color = th.text, fontSize = 30.sp, maxLines = 1)
        }
    } else {
        val w = with(dens) { POPUP_W.dp.toPx() }
        val left = popupLeft(p.rect, p.alts.size, st.rootW, w); val y = (p.rect.top - p.rect.height * 1.15f - 2f).coerceAtLeast(0f)
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
    val ctx = LocalContext.current
    // every emoji of Unicode and the kaomoji, read once from the app
    val all by androidx.compose.runtime.produceState(emptyList<EmojiItem>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { EmojiData.load(ctx) } }
    // 0 recent, 1..9 the nine groups, 10 kaomoji
    // the recent list shown stays as it was when the panel opened, so the emojis do not jump while tapping; it updates on the next open
    val shownRecents = remember { st.recents }
    var tab by remember { mutableStateOf(if (shownRecents.isEmpty()) 1 else 0) }
    val icons = listOf(Icons.Rounded.AccessTime, Icons.Rounded.EmojiEmotions, Icons.Rounded.Person, Icons.Rounded.Pets, Icons.Rounded.Restaurant, Icons.Rounded.DirectionsCar,
        Icons.Rounded.SportsSoccer, Icons.Rounded.Lightbulb, Icons.Rounded.Tag, Icons.Rounded.Flag)
    val q = st.emojiQuery
    val fams by androidx.compose.runtime.produceState(emptyList<KeyboardSymbols.Family>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KeyboardSymbols.load(ctx) } }
    var g by remember { mutableStateOf(0) }
    var sub by remember { mutableStateOf(0) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    // the same height as the keys, so the keyboard does not shrink; the search needs the keys under it and is taller
    val fixed = if (q == null && st.keysH > 0) Modifier.height(with(dens) { st.keysH.toDp() }) else Modifier
    Column(Modifier.fillMaxWidth().then(fixed)) {
        // on the symbol page the families sit above the search, where the thumb reaches them
        if (q == null && tab == 11) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            fams.forEachIndexed { i, f ->
                Box(Modifier.size(width = 44.dp, height = 36.dp).clickable { g = i; sub = 0 }, contentAlignment = Alignment.Center) {
                    Icon(familyIcon(f.id), tr(f.en, f.tr), tint = if (i == g) th.text else th.sub, modifier = Modifier.size(22.dp))
                    if (i == g) Box(Modifier.align(Alignment.BottomCenter).width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(th.text))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (q != null) {
                // search: the box takes the place of the tabs; the keys below type into it
                Box(Modifier.size(40.dp).clickable { st.emojiQuery = null }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "ArrowBack"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
                Row(Modifier.weight(1f).height(32.dp).padding(end = 8.dp).clip(RoundedCornerShape(16.dp)).background(th.special).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(kbIcon(th, "Search"), null, tint = th.sub, modifier = Modifier.size(18.dp))
                    Text(if (q.isEmpty()) tr("Search emoji, kaomoji, symbols", "Emoji, kaomoji, sembol ara") else q, color = if (q.isEmpty()) th.sub else th.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                // the search box on top, like the phone's own emoji page; the category bar sits at the bottom
                Row(Modifier.weight(1f).height(36.dp).padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).background(th.special).clickable { st.emojiQuery = "" }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(kbIcon(th, "Search"), null, tint = th.sub, modifier = Modifier.size(20.dp))
                    Text(tr("Search emojis", "Emoji ara"), color = th.sub, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (q != null) {
            val found = remember(q, all, fams) { EmojiData.search(all, q) + KeyboardSymbols.search(fams, q).map { EmojiItem(it.ch, 11, it.name) } }
            LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxWidth().height(48.dp)) {
                items(found) { it2 -> EmojiCell(it2, th, act) }
            }
            KeysPanel(th, st, act, keyH)
        } else {
            val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = tab) { 12 }
            LaunchedEffect(tab) { if (pager.currentPage != tab) pager.animateScrollToPage(tab) }
            LaunchedEffect(pager.currentPage) { tab = pager.currentPage }
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f)) { page ->
                if (page == 11) { SymbolPage(th, act, fams, g, sub) { sub = it }; return@HorizontalPager }
                val list = if (page == 0) shownRecents.map { e -> all.firstOrNull { it.e == e } ?: EmojiItem(e, if (e.any { it.isLetterOrDigit() }) 10 else 0, "") }
                    else if (page == 10) all.filter { it.group >= 10 } else all.filter { it.group == page - 1 }
                LazyVerticalGrid(GridCells.Adaptive(if (page == 10) 92.dp else 44.dp), Modifier.fillMaxSize()) {
                    items(list) { it2 -> EmojiCell(it2, th, act) }
                }
            }
            Row(Modifier.fillMaxWidth().height(keyH * .85f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(56.dp).fillMaxHeight().clickable { act.page(0) }, contentAlignment = Alignment.Center) { Text("abc", color = th.text, fontSize = 17.sp) }
                Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    icons.forEachIndexed { i, ic ->
                        Box(Modifier.width(44.dp).fillMaxHeight().clickable { tab = i }, contentAlignment = Alignment.Center) {
                            Icon(ic, null, tint = if (i == tab) th.text else th.sub, modifier = Modifier.size(24.dp))
                            if (i == tab) Box(Modifier.align(Alignment.BottomCenter).width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(th.text))
                        }
                    }
                    Box(Modifier.width(48.dp).fillMaxHeight().clickable { tab = 10 }, contentAlignment = Alignment.Center) { Text("(ツ)", color = if (tab == 10) th.text else th.sub, fontSize = 15.sp) }
                    Box(Modifier.width(44.dp).fillMaxHeight().clickable { tab = 11 }, contentAlignment = Alignment.Center) { Text("✦", color = if (tab == 11) th.text else th.sub, fontSize = 20.sp) }
                }
                Box(Modifier.width(56.dp).fillMaxHeight().clickable { act.backspace() }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Backspace"), null, tint = th.text, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

private fun familyIcon(id: String): ImageVector = when (id) {
    "Patterns" -> Icons.Rounded.AutoFixHigh; "Flowers" -> Icons.Rounded.LocalFlorist; "Hearts" -> Icons.Rounded.Favorite; "Stars" -> Icons.Rounded.AutoAwesome
    "Cute" -> Icons.Rounded.EmojiEmotions; "Moon and sky" -> Icons.Rounded.NightsStay; "Lines" -> Icons.Rounded.HorizontalRule; "Brackets" -> Icons.Rounded.DataArray
    "Arrows" -> Icons.Rounded.NorthEast; "Shapes" -> Icons.Rounded.Category; "Music" -> Icons.Rounded.MusicNote; "Symbols" -> Icons.Rounded.EmojiSymbols
    "Numbers" -> Icons.Rounded.Pin; "Punctuation" -> Icons.Rounded.FormatQuote; "Currency" -> Icons.Rounded.AttachMoney; "Math" -> Icons.Rounded.Functions
    "Cuneiform" -> Icons.Rounded.Edit; "Hieroglyphs" -> Icons.Rounded.Visibility
    else -> Icons.Rounded.Apps
}

/** The symbol tab: a row of icons for the families, a row of sub-categories when the family has some, and the signs; the search above finds a sign by its Unicode name. */
@Composable
private fun SymbolPage(th: KbTheme, act: KbActions, fams: List<KeyboardSymbols.Family>, g: Int, sub: Int, onSub: (Int) -> Unit) {
    val fam = fams.getOrNull(g)
    Column(Modifier.fillMaxSize()) {
        val subs = fam?.subs.orEmpty()
        if (subs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subs.forEachIndexed { i, (name, _) ->
                Text(KeyboardSymbols.subName(name), color = if (i == sub) th.text else th.sub, fontSize = 13.sp, fontWeight = if (i == sub) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (i == sub) th.special else Color.Transparent).clickable { onSub(i) }.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        // the presets are long: wider cells and smaller type
        val wide = fam?.id == "Patterns"
        LazyVerticalGrid(if (wide) GridCells.Fixed(1) else GridCells.Adaptive(48.dp), Modifier.fillMaxSize()) {
            items(subs.getOrNull(sub)?.second.orEmpty()) { sg ->
                Box(Modifier.height(44.dp).clickable { act.emoji(sg.ch) }, contentAlignment = Alignment.Center) { Text(sg.ch, fontSize = if (wide) 14.sp else 22.sp, color = th.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) }
            }
        }
    }
}

@Composable
private fun EmojiCell(it: EmojiItem, th: KbTheme, act: KbActions) {
    val kao = it.group == 10
    Box(Modifier.height(44.dp).clickable { act.emoji(it.e) }, contentAlignment = Alignment.Center) {
        Text(it.e, fontSize = if (kao) 15.sp else 26.sp, color = th.text, maxLines = 1, overflow = TextOverflow.Clip)
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ClipPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val selecting = sel.isNotEmpty()
    val now = System.currentTimeMillis()
    Column(Modifier.fillMaxWidth().height(keyH * 4f).background(th.menu)) {
        // the head: how many are kept, and either the clear / select-all buttons or the delete of the picked ones
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selecting) "${sel.size} " + tr("selected", "seçili") else tr("Clipboard", "Pano") + " · ${st.clipItems.size}", color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (selecting) {
                Text(tr("All", "Tümü"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { sel = st.clipItems.map { it.id }.toSet() }.padding(8.dp))
                Text(tr("Delete", "Sil"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { act.clipDelete(sel); sel = emptySet() }.padding(8.dp))
                Text(tr("Cancel", "Vazgeç"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { sel = emptySet() }.padding(8.dp))
            } else if (st.clipItems.any { !it.pinned }) Text(tr("Clear unpinned", "Sabitlenmeyenleri temizle"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { act.clipClear() }.padding(8.dp))
        }
        if (st.clipItems.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(tr("Copied texts and pictures appear here", "Kopyalanan metin ve resimler burada görünür"), color = th.sub, fontSize = 14.sp) }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(st.clipItems, key = { it.id }) { it2 ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { if (selecting) sel = if (it2.id in sel) sel - it2.id else sel + it2.id else act.pasteClip(it2) }, onLongClick = { sel = sel + it2.id })
                    .then(if (it2.id in sel) Modifier.background(th.special) else Modifier).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clickable { act.clipPin(it2.id) }, contentAlignment = Alignment.Center) {
                        Icon(kbIcon(th, "Gesture"), null, tint = if (it2.pinned) th.accent else th.sub.copy(alpha = .5f), modifier = Modifier.size(18.dp))
                    }
                    if (it2.image != null) ClipThumb(it2.image) else Text(it2.text, color = th.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (it2.image != null) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Text(ago(now - it2.time), color = th.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) { Key(th, st, act, KbKey("ABC", KeyKind.MODE, 2f), keyH * .85f); Spacer(5f); Key(th, st, act, KbKey("", KeyKind.DELETE, 2f), keyH * .85f) }
    }
}

private fun ago(ms: Long): String = when {
    ms < 90_000L -> tr("now", "şimdi")
    ms < 3_600_000L -> "${ms / 60_000} " + tr("min", "dk")
    ms < 86_400_000L -> "${ms / 3_600_000} " + tr("h", "sa")
    else -> "${ms / 86_400_000} " + tr("d", "g")
}

/** A kept picture, shown small. */
@Composable
private fun ClipThumb(path: String) {
    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, o)
                var ss = 1; while (o.outWidth / ss > 300) ss *= 2
                android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = ss })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) androidx.compose.foundation.Image(b, null, Modifier.height(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    else Box(Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Color.Gray.copy(alpha = .3f)))
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
        // the handles sit inside the keys' own size (the window around the keyboard is taller: nothing here may grow to it)
        Box(Modifier.matchParentSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(30.dp).pointerInput(Unit) { detectVerticalDragGestures { _, dy -> h = (h - dy / dens / 2.2f).coerceIn(70f, 170f); KeyboardPrefs.heightPct = h.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(56.dp).height(5.dp)) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp).pointerInput(Unit) { detectVerticalDragGestures { _, dy -> b = (b - dy / dens).coerceIn(0f, 60f); KeyboardPrefs.bottomPad = b.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(56.dp).height(5.dp)) }
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp).pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> sd = (sd + dx / dens).coerceIn(0f, 40f); KeyboardPrefs.sidePad = sd.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(5.dp).height(56.dp)) }
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp).pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> sd = (sd - dx / dens).coerceIn(0f, 40f); KeyboardPrefs.sidePad = sd.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(5.dp).height(56.dp)) }
        Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp)).background(th.accent).clickable { KeyboardPrefs.save(ctx); st.panel = "keys" }.padding(horizontal = 26.dp, vertical = 12.dp)) {
            Icon(kbIcon(th, "Check"), null, tint = th.onAccent, modifier = Modifier.size(24.dp))
        }
        }
    }
}


/** The Shift key: an outlined arrow when off, the arrow with a line under it for one capital, the arrow filled with the gap to the line filled too for caps lock. */
@Composable
private fun ShiftGlyph(state: Int, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
        val u = size.width / 24f
        val w = 2f * u
        val arrow = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * u, 3.5f * u); lineTo(20f * u, 12f * u); lineTo(15f * u, 12f * u)
            lineTo(15f * u, if (state == 2) 19f * u else 16f * u)
            lineTo(9f * u, if (state == 2) 19f * u else 16f * u)
            lineTo(9f * u, 12f * u); lineTo(4f * u, 12f * u); close()
        }
        if (state == 2) drawPath(arrow, color) else drawPath(arrow, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        if (state >= 1) drawRoundRect(color, androidx.compose.ui.geometry.Offset(9f * u, 19.5f * u), androidx.compose.ui.geometry.Size(6f * u, 2.2f * u), androidx.compose.ui.geometry.CornerRadius(u))
    }
}
