@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.umutk.pulse.extras.keyboard
import androidx.compose.foundation.layout.Spacer

import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import com.umutk.pulse.extras.keyboard.KeyboardEffects.boardWash
import com.umutk.pulse.extras.keyboard.KeyboardEffects.keyWash
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
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.toPath
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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private const val POPUP_W = 44

/** The whole keyboard: the bar, the keys (or the emoji, menu, clipboard and resize panels) and the pop-up over a pressed key. */
/** The running hue (0..360) of the moving colour light; read only while drawing, so the keys do not recompose with it. */
private val LocalHue = androidx.compose.runtime.compositionLocalOf<androidx.compose.runtime.State<Float>?> { null }

/** True inside the window of the accessibility keyboard: that window is only as big as the keyboard, so the tall window of the floating mode is not used there. */
val LocalOverlayKb = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun KeyboardView(st: KbState, act: KbActions, themeId: String = KeyboardPrefs.theme, modifier: Modifier = Modifier) {
    val th = KeyboardThemes.of(themeId, st.dark).tuned()
    val hue: androidx.compose.runtime.State<Float>? = if (KeyboardPrefs.rgbNow > 0) androidx.compose.animation.core.rememberInfiniteTransition(label = "rgb").animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween((14000 * 100 / KeyboardPrefs.rgbSpeed.coerceIn(25, 300)), easing = androidx.compose.animation.core.LinearEasing)), label = "hue") else null
    androidx.compose.runtime.CompositionLocalProvider(LocalHue provides hue) { KeyboardViewBody(th, st, act, modifier) }
}

@Composable
private fun KeyboardViewBody(th: KbTheme, st: KbState, act: KbActions, modifier: Modifier) {
    // turned sideways the keyboard would fill the screen: the keys are lower there (about 60%)
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val land = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // a phone turned sideways takes a much smaller keyboard (a tablet keeps its size)
    val phoneLand = land && cfg.screenWidthDp < 600
    val scale = KeyboardPrefs.heightAt(land) / 100f * (if (phoneLand) .52f else if (land) .6f else 1f)
    androidx.compose.runtime.SideEffect { if (st.land != land) st.land = land }
    // (the Samsung look: its rows are a little lower than ours, as measured on the real keyboard)
    // the phone keypad has four rows, not five: its keys take the room, and the Old phone look is taller still
    val keyH = (61f * scale * (if (th.samsung) .85f else 1f) * (if (KeyboardPrefs.multiTap) 1.2f else 1f) * (if (th.id == "retro") 1.1f else 1f) * (if (KeyboardPrefs.floatNow && !LocalOverlayKb.current) KeyboardPrefs.floatScale else 1f)).dp
    val one = KeyboardPrefs.oneHand
    // a pull down on the bar: the keys follow the finger, and let go far enough they go away
    val pull = remember { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dens = LocalDensity.current.density
    // a hardware keyboard is attached: only the suggestion bar shows, at the foot of the screen
    // (its other pages, the clipboard, the lock and the rest, open in full as on the screen keyboard, without the letter keys; the translation bar takes the height it needs and sits clear of the screen's foot)
    if (st.hardware && !st.hwOpen && st.panel == "keys") {
        Column(modifier.fillMaxWidth().background(th.bg).padding(bottom = if (st.trans) 18.dp else 0.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).heightIn(min = 52.dp)) { TopBar(th, st, act, onPull = { }, onRelease = { }) }
                // the keyboard button: opens the full keyboard (for typing with the screen, and the clipboard and the rest of its keys)
                BarIcon(th, kbIcon(th, "Keyboard")) { st.hwOpen = true }
            }
        }
        return
    }
    var rootH by remember { mutableStateOf(0f) }
    // easter eggs (typed words, see PulseKeyboardService): a barrel roll, a flip, a rainbow wash
    val eggSpin = remember { Animatable(0f) }
    var eggRainbow by remember { mutableStateOf(false) }
    LaunchedEffect(st.eggN) {
        when (st.egg) {
            "roll" -> { eggSpin.snapTo(0f); eggSpin.animateTo(360f, androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)); eggSpin.snapTo(0f) }
            "flip" -> { eggSpin.animateTo(180f, androidx.compose.animation.core.tween(500)); kotlinx.coroutines.delay(2500); eggSpin.animateTo(0f, androidx.compose.animation.core.tween(500)) }
            "rainbow", "party" -> { eggRainbow = true; if (st.egg == "party") { eggSpin.animateTo(8f, androidx.compose.animation.core.tween(120)); repeat(6) { eggSpin.animateTo(if (it % 2 == 0) -8f else 8f, androidx.compose.animation.core.tween(160)) }; eggSpin.animateTo(0f, androidx.compose.animation.core.tween(120)) }; kotlinx.coroutines.delay(5000); eggRainbow = false }
        }
    }
    // the floating keyboard: its own height, and the handle's place in the window (the drag is read on the window, which stays still)
    val float = KeyboardPrefs.floatNow && !LocalOverlayKb.current
    var kbH by remember { mutableStateOf(0f) }
    // the bar that stays when the keyboard slides down out of the window
    val barPx = with(LocalDensity.current) { kbBarHeight().toPx() }
    var handleRect by remember { mutableStateOf<Rect?>(null) }
    var gripRect by remember { mutableStateOf<Rect?>(null) }
    val rootHue = LocalHue.current
    // the gamepad paints no board of its own: its buttons float over whatever the game shows
    Box(modifier.fillMaxWidth().graphicsLayer { rotationZ = eggSpin.value }.then(if (float) Modifier.height((androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * .85f).dp) else if (st.panel == "gamepad") Modifier.background(Color.Black) else Modifier.background(th.bg).boardWash(KeyboardPrefs.boardFxNow))
        .then(if (!float && KeyboardPrefs.rgbNow == 1 && rootHue != null) Modifier.drawWithContent { drawContent(); val h = rootHue.value; drawRect(androidx.compose.ui.graphics.Brush.sweepGradient(List(8) { i -> Color.hsv((h + i * 45f) % 360f, .9f, 1f) }), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())) } else Modifier)
        .onSizeChanged { st.rootW = it.width.toFloat(); rootH = it.height.toFloat() }.pointerInput(float, barPx) {
        // the floating keyboard moves by its handle: the finger is followed on the window (which stays still), so the drag is smooth wherever the keyboard goes
        if (!float) return@pointerInput
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val hr = handleRect
            if (hr == null || !hr.contains(d.position) || gripRect?.contains(d.position) == true) return@awaitEachGesture
            d.consume()
            var last = d.position
            var moved = 0f
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                if (!c.pressed) break
                val dx = c.position.x - last.x; val dy = c.position.y - last.y
                last = c.position
                moved += abs(dx) + abs(dy)
                c.consume()
                // down: the keyboard may slide out until only its top bar is left in the window; up: to the top
                val minY = -((kbH - barPx) / (rootH - kbH).coerceAtLeast(1f)).coerceAtLeast(0f)
                KeyboardPrefs.floatY = (KeyboardPrefs.floatY - dy / (rootH - kbH).coerceAtLeast(1f)).coerceIn(minY, 1f)
                val free = (1f - .92f * KeyboardPrefs.floatScale)
                // the keyboard is 92 percent wide: its free room is the other 8, from the left edge to the right one
                KeyboardPrefs.floatX = (KeyboardPrefs.floatX + dx / (st.rootW * free).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            KeyboardPrefs.saveFloat()
            // a tap on the handle (no drag): the line turns into an X; a tap on the X closes the keyboard. A drag only moves it (and puts the X back to a line),
            // so a touch on the handle never closes the keyboard by itself
            if (moved < 12.dp.toPx()) { if (st.floatArmed) { st.floatArmed = false; act.hide() } else st.floatArmed = true }
            else st.floatArmed = false
        }
    }.pointerInput(Unit) {
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
                if (!float && st.panel == "keys" && !st.cursorDrag && dy > 80.dp.toPx() && abs(dx) < dy * .45f && c.uptimeMillis - t0 < 420 && st.preview?.alts.isNullOrEmpty()) {
                    done = true; c.consume(); st.trail = emptyList(); st.swiped = true; st.preview = null
                    act.hide()
                }
            }
        }
    }) {
        // the picture behind the keys (Settings > Effects), under a black layer of the chosen strength
        KbPicture(KeyboardPrefs.bgPath, KeyboardPrefs.bgDim)
        if (KeyboardPrefs.boardFxNow == 7 || eggRainbow) RgbWash()
        Column(
            (if (float) Modifier.fillMaxWidth(.92f * KeyboardPrefs.floatScale).align(Alignment.BottomStart)
                .offset { IntOffset((st.rootW * (1f - .92f * KeyboardPrefs.floatScale) * KeyboardPrefs.floatX).roundToInt(), -((rootH - kbH).coerceAtLeast(0f) * KeyboardPrefs.floatY).roundToInt()) }
                .onSizeChanged { kbH = it.height.toFloat() }
                .onGloballyPositioned { c ->
                    // the keyboard's place tells the system which part of the window takes touches; the rest of the window belongs to the app
                    val bnd = c.boundsInRoot()
                    val r = intArrayOf(bnd.left.roundToInt(), bnd.top.roundToInt(), bnd.right.roundToInt(), bnd.bottom.roundToInt())
                    if (!r.contentEquals(st.floatRect)) { st.floatRect = r; st.refreshInsets?.invoke() }
                }
                .clip(RoundedCornerShape(16.dp)).background(th.bg).boardWash(KeyboardPrefs.boardFxNow)
                else if (one == "off") Modifier.fillMaxWidth() else Modifier.fillMaxWidth(.82f).align(if (one == "left") Alignment.TopStart else Alignment.TopEnd))
                .padding(horizontal = if (float) 12.dp else if (th.samsung && !phoneLand) 6.dp else if (phoneLand || (KeyboardPrefs.emojiFull && st.panel == "emoji")) 2.dp else (if (KeyboardPrefs.compact) maxOf(KeyboardPrefs.sidePad, cfg.screenWidthDp * (100 - KeyboardPrefs.compactPct) / 200) else KeyboardPrefs.sidePad).coerceAtMost(((cfg.screenWidthDp - 140) / 2).coerceAtLeast(2)).dp).padding(bottom = KeyboardPrefs.bottomPad.dp),
        ) {
            // the floating keyboard is 90 percent wide and moves by its handle anywhere on the screen (the drag itself is read above, on the window)
            // the handle: one line; tapped, its two halves turn into an X (an animation, no icon), which closes the keyboard on a second tap; after three seconds it is a line again
            if (float) {
                LaunchedEffect(st.floatArmed) { if (st.floatArmed) { kotlinx.coroutines.delay(3000); st.floatArmed = false } }
                val x by animateFloatAsState(if (st.floatArmed) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = .7f, stiffness = 500f), label = "floatX")
                val lineCol = th.sub.copy(alpha = .8f)
                val xCol = th.text
                Box(Modifier.fillMaxWidth().height(30.dp).onGloballyPositioned { handleRect = it.boundsInRoot() }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(width = 60.dp, height = 26.dp)) {
                        val c = center
                        val len = (56.dp.toPx() + (22.dp.toPx() - 56.dp.toPx()) * x)
                        val col = androidx.compose.ui.graphics.lerp(lineCol, xCol, x)
                        val w = 5.dp.toPx() + 1.dp.toPx() * x
                        listOf(1f, -1f).forEach { sgn ->
                            rotate(45f * sgn * x, c) {
                                drawLine(col, androidx.compose.ui.geometry.Offset(c.x - len / 2, c.y), androidx.compose.ui.geometry.Offset(c.x + len / 2, c.y), w, StrokeCap.Round)
                            }
                        }
                    }
                    // the resize grip at the right end of the handle: dragging it makes the keyboard smaller or bigger
                    Box(Modifier.align(Alignment.CenterEnd).size(34.dp).onGloballyPositioned { gripRect = it.boundsInRoot() }.pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { KeyboardPrefs.saveFloat() }) { c, d ->
                            c.consume()
                            val full = (st.rootW * .92f).coerceAtLeast(1f)
                            KeyboardPrefs.floatScale = (KeyboardPrefs.floatScale + d.x / full).coerceIn(.55f, 1f)
                        }
                    }, contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(14.dp)) {
                            val w = size.width
                            listOf(.35f, .7f, 1f).forEach { f -> drawLine(th.sub.copy(alpha = .8f), androidx.compose.ui.geometry.Offset(w, w * (1f - f)), androidx.compose.ui.geometry.Offset(w * (1f - f), w), 2.dp.toPx(), StrokeCap.Round) }
                        }
                    }
                }
            }
            // the number pad has no bar, except while the password manager has a saved login or password to offer for the field
            if (!st.numpad || st.emailBar || st.inline.isNotEmpty()) Box { TopBar(th, st, act, onPull = { dy -> scope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) } }, onRelease = {
                // a pull down on the bar closes the keyboard at once (nothing slides: the window behind it stayed black while it moved)
                if (pull.value > 56 * dens) act.hide()
                scope.launch { pull.snapTo(0f) }
            }); if (KeyboardPrefs.topBright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.topBright) / 100f))) }
            if (KeyboardPrefs.animOn) AnimatedContent(st.panel, transitionSpec = { (fadeIn(tween(130)) + slideInVertically(tween(160)) { it / 14 }) togetherWith fadeOut(tween(90)) }, label = "panel") { p -> Panel(p, th, st, act, keyH) }
            else Panel(st.panel, th, st, act, keyH)
        }
        // the brightness: a black veil over the keys (the pop-up above stays bright)
        if (!float && KeyboardPrefs.bright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.bright) / 100f)))
        Trail(th, st)
        PressFx(th, st)
        Preview(th, st)
        // private typing: the hat mark sits at the bottom middle, so it is seen whatever the keyboard shows
        // private typing: a big faint hat behind the keys, only on the keys page; it shows for a moment after every key and fades out
        if (st.incognito && st.panel == "keys") {
            val hat = remember { androidx.compose.animation.core.Animatable(0f) }
            // switching private typing on (or coming back to the keys) shows the big mark for a moment; every key after that flashes it faintly
            LaunchedEffect(st.incognito, st.panel) { hat.snapTo(.5f); kotlinx.coroutines.delay(900); hat.animateTo(0f, androidx.compose.animation.core.tween(900)) }
            LaunchedEffect(st.keyTick) { if (st.keyTick > 0) { hat.snapTo(.2f); hat.animateTo(0f, androidx.compose.animation.core.tween(1400)) } }
            com.umutk.pulse.extras.IncognitoGlyph(th.sub, 150.dp, Modifier.align(Alignment.Center).graphicsLayer { alpha = hat.value })
        }
    }
}

@Composable
private fun Panel(p: String, th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column(Modifier.onSizeChanged { if (p == "keys" && it.height > 0) st.keysH = it.height }) {
        when (p) {
            // the emoji page is opaque: what is behind it (the gamepad, the arrows of the bar) does not show through
            "emoji" -> if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) {
                // a wide screen shows two pages side by side: the emoji (or symbols, or text and art) on the left, the keys on the right
                // the keys are rows that stack in a column of their own (a Box would lay them on top of each other, and only the last row would show)
                val eth = emojiTh(th)
                val emoji: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = { Box(Modifier.weight(1f).background(eth.bg)) { EmojiPanel(eth, st, act, keyH, split = true) } }
                val keys: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = { Column(Modifier.weight(1f)) { KeysPanel(th, st, act, keyH) } }
                Row(Modifier.fillMaxWidth().background(th.bg)) {
                    if (KeyboardPrefs.emojiRight) { keys(); emoji() } else { emoji(); keys() }
                }
            } else { val eth = emojiTh(th); Box(Modifier.fillMaxWidth().background(eth.bg)) { EmojiPanel(eth, st, act, keyH) } }
            "menu" -> MenuPanel(th, st, act, keyH)
            "clip" -> { if (st.numpad) NumPanelClose(th, st); ClipPanel(th, st, act, keyH) }
            "gamepad" -> GamepadPanel(th, act)
            "auth" -> { if (st.numpad) NumPanelClose(th, st); AuthPanel(th, st, act, keyH) }
            "vault" -> VaultPanel(th, st, act, keyH)
            "dict" -> DictPanel(th, st, act, keyH)
            "stickers" -> StickerPanel(th, st, act, keyH)
            "voice" -> VoicePanel(th, st, act, keyH)
            "resize" -> ResizePanel(th, st, keyH)
            "keyset" -> KeysetPanel(th, st, act, keyH)
            "layout" -> LayoutPanel(th, st, keyH)
            else -> when {
                st.forceLayout == "game" -> GamePad(th, act, keyH)
                st.forceLayout == "numpad" || (st.numpad && st.forceLayout == null) -> NumPad(th, st, act, keyH)
                else -> KeysPanel(th, st, act, keyH)
            }
        }
    }
}

/** The number pad has no bar, so the clipboard and the codes opened from it get a close key that brings the digits back. */
@Composable
private fun NumPanelClose(th: KbTheme, st: KbState) {
    Row(Modifier.fillMaxWidth().height(36.dp).background(th.bg), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        BarIcon(th, kbIcon(th, "Close")) { st.panel = "keys" }
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
        Key(th, st, act, KbKey("+", KeyKind.CHAR, 1f), h)
        Key(th, st, act, KbKey("0", KeyKind.CHAR, 1f), h)
        Key(th, st, act, KbKey("", KeyKind.DELETE, 1f), h)
    }
    Row(Modifier.fillMaxWidth()) {
        Key(th, st, act, KbKey("*", KeyKind.CHAR, 1f), h * .8f)
        Key(th, st, act, KbKey("#", KeyKind.CHAR, 1f), h * .8f)
        // the bottom right key: the field's action, or the character chosen in Settings > Keyboard > Number pad
        val ek = KeyboardPrefs.numEnter
        Key(th, st, act, if (ek == "enter") KbKey("", KeyKind.ENTER, 1f) else KbKey(ek, KeyKind.CHAR, 1f), h * .8f)
    }
}

/** A chosen layout, over whatever the field itself would ask for. "Default" clears the pick, so the field decides again. */
@Composable
private fun LayoutPanel(th: KbTheme, st: KbState, keyH: androidx.compose.ui.unit.Dp) {
    // each layout has its own icon; the rows share the height evenly
    val options = listOf(Triple(null, tr("Default", "Varsayılan"), "Keyboard"), Triple("numpad", tr("Numbers", "Sayılar"), "Pin"), Triple("game", tr("Game controls", "Oyun tuşları"), "SportsEsports"), Triple("gamepad", tr("Gamepad", "Gamepad"), "SportsEsports"))
    Column(Modifier.fillMaxWidth().height(keyH * 4f), verticalArrangement = Arrangement.SpaceEvenly) {
        Text(tr("Key layout", "Tuş düzeni"), color = th.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp))
        options.forEach { (v, label, icon) ->
            // the gamepad is one of the layouts: it opens its own panel
            val on = if (v == "gamepad") st.panel == "gamepad" else st.forceLayout == v
            Row(
                Modifier.fillMaxWidth().clickable { if (v == "gamepad") st.panel = "gamepad" else { st.forceLayout = v; st.panel = "keys" } }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (icon == "SportsEsports") Icons.Rounded.SportsEsports else kbIcon(th, icon), null, tint = if (on) th.accent else th.text, modifier = Modifier.size(22.dp))
                Text(label, color = if (on) th.accent else th.text, fontSize = 16.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f).padding(start = 12.dp))
                if (on) Icon(kbIcon(th, "Check"), null, tint = th.accent, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().clickable { st.panel = "keyset" }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(kbIcon(th, "Keyboard"), null, tint = th.text, modifier = Modifier.size(22.dp))
            Text(tr("Key set", "Tuş seti"), color = th.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
    }
}

/** One key of the Game layout, its share of the row's width given by [w]. */
@Composable
private fun RowScope.GCell(th: KbTheme, act: KbActions, h: androidx.compose.ui.unit.Dp, id: String, w: Float = 1f) {
    val d = ExtraKeys.byId(id) ?: return
    ExtraCell(th, 0.dp, h, d, 0, Modifier.weight(w), hold = holdOf(d, act)) { act.extraKey(d.id) }
}

/** A WASD cluster for movement and a handful of action keys for the other hand: real key codes, so a game reads them like a hardware keyboard. */
@Composable
private fun GamePad(th: KbTheme, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val h = keyH * .9f
    Row(Modifier.fillMaxWidth()) {
        // the WASD cluster on the left, three rows so W sits centred above A/S/D
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) { Spacer(1f); GCell(th, act, h, "gW"); Spacer(1f) }
            Row(Modifier.fillMaxWidth()) { GCell(th, act, h, "gA"); GCell(th, act, h, "gS"); GCell(th, act, h, "gD") }
        }
        // the action keys on the right
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) { GCell(th, act, h, "gQ"); GCell(th, act, h, "gE") }
            Row(Modifier.fillMaxWidth()) { GCell(th, act, h, "gShift"); GCell(th, act, h, "gCtrl") }
        }
    }
    Row(Modifier.fillMaxWidth()) { GCell(th, act, h, "gSpace", 2f); GCell(th, act, h, "gEnter", 1f) }
}

/** The path of a swipe over the letters. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.Trail(th: KbTheme, st: KbState) {
    // the swipe-typing path is no longer drawn (the blue line); the keys it passes still spell the word
    val t = st.trail
    if (true || t.size < 2) return
    Canvas(Modifier.matchParentSize()) {
        val path = Path().apply { moveTo(t[0].x, t[0].y); for (i in 1 until t.size) lineTo(t[i].x, t[i].y) }
        drawPath(path, th.accent.copy(alpha = .75f), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---- the bar ---------------------------------------------------------------------------------------------------------------------------------------

/** The height of the suggestion bar: a third lower on a phone turned sideways. */
@Composable
private fun kbBarHeight(): androidx.compose.ui.unit.Dp {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    // the floating keyboard keeps its bar slim, so its keys get the room
    if (KeyboardPrefs.floatNow && !LocalOverlayKb.current) return 31.dp
    return if (cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && cfg.screenWidthDp < 600) 30.dp else if (KeyboardPrefs.theme == "samsung") 49.dp else 44.dp
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TopBar(th: KbTheme, st: KbState, act: KbActions, onPull: (Float) -> Unit, onRelease: () -> Unit) {
    if (st.trans) { TransBar(th, st, act); return }
    // the emoji page: the bar above takes the categories, with only a back button at its left
    if (st.panel == "emoji" && st.emojiQuery == null) { val eth = emojiTh(th); Box(Modifier.background(eth.bg)) { EmojiTopBar(eth, st, act) }; return }
    // the Samsung look: while there is nothing to suggest the bar holds its round tool buttons (the smart fix, emoji, translate, clipboard, settings, more)
    val samBar = th.samsung && st.panel == "keys" && st.inline.isEmpty() && st.slots.none { it.isNotEmpty() }
    // a field that takes suggestions keeps its bar at full height while nothing is suggested (a word the dictionary does not know): the bar used to shrink to a thin handle and
    // grow again with the next word, and the keys jumped up and down with it. Only a field without suggestions (a password box, a number field), or the setting off, gives the room to the keys
    val keepBar = KeyboardPrefs.suggestions && (!st.plain || st.terminal) && st.panel == "keys"
    val barHasContent = samBar || keepBar || st.toolbar || st.panel != "keys" || st.inline.isNotEmpty() || st.slots.any { it.isNotEmpty() }
    // no suggestions: no container, only a short handle line just above the keys (the space of an empty bar is given to the keys)
    if (!barHasContent) {
        Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(th.sub.copy(alpha = .35f)))
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(kbBarHeight()).pointerInput(Unit) {
            // a pull down on the bar closes the keyboard
            detectVerticalDragGestures(onDragEnd = { onRelease() }, onDragCancel = { onRelease() }) { change, dy -> change.consume(); onPull(dy) }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!samBar && (KeyboardPrefs.barTools || st.panel != "keys")) BarIcon(th, if (st.toolbar || st.panel != "keys") kbIcon(th, "Close") else kbIcon(th, "ExpandMore")) { if (st.panel != "keys") st.panel = "keys" else st.toolbar = !st.toolbar }
        if ((st.toolbar && KeyboardPrefs.barTools) || samBar) {
            // the icons share the width evenly; only when they would be smaller than a finger's reach does the row scroll
            val shown = if (th.samsung) listOf("aifix", "emoji", "translate", "clip", "settings") else KeyboardPrefs.toolbarItems.split(',').filter { id -> id != "menu" && ExtraKeys.toolbar.any { it.first == id } }
            BoxWithConstraints(Modifier.weight(1f)) {
                val slot = if (shown.isEmpty()) maxWidth else maxOf(44.dp, maxWidth / shown.size)
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    shown.forEach { id ->
                        Box(Modifier.width(slot), contentAlignment = Alignment.Center) {
                            BarIcon(th, toolIcon(th, id), on = toolOn(id, st)) { when (id) { "selectAll", "copy", "cut", "paste", "undo", "redo" -> act.edit(id); "emoji" -> act.emojiPanel(); "lang" -> act.lang(); else -> act.menu(id) } }
                        }
                    }
                }
            }
            // the three dots always sit at the right end, drawn in Fluent
            if (th.samsung || KeyboardPrefs.toolbarItems.split(',').contains("menu")) FluentBarIcon(th, "more", on = toolOn("menu", st)) { act.menu("menu") }
        } else if (st.inline.isNotEmpty()) {
            // a password manager has something for this field (login, password, card): its own suggestions take the bar, close together,
            // each on a grey rounded back; one alone sits in the middle, several scroll with an arrow at each end
            val scroll = rememberScrollState()
            val sc = rememberCoroutineScope()
            val one = st.inline.size == 1
            // each chip is a real system view (the password manager draws it): key() on the view itself keeps it from being torn
            // down and rebuilt on every recomposition (every pixel of a swipe on the bar is one) — that teardown was the glitch where
            // the grey back vanished mid-drag while the chip's own text, a separate rendered surface, stayed put a frame longer
            @Composable fun chips() = st.inline.forEach { v ->
                key(v) {
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(lerp(th.key, Color(0xFF808080), .07f))) {
                        androidx.compose.ui.viewinterop.AndroidView(factory = { c ->
                            // the chip's picture is a surface of its own; it stays on top of the window (the default), because a surface behind the window
                            // gets no touches: a tap on the password manager's chip then did nothing and nothing was filled in
                            if (android.os.Build.VERSION.SDK_INT >= 30) runCatching { (v as? android.widget.inline.InlineContentView)?.setZOrderedOnTop(true) }
                            android.widget.FrameLayout(c).also { f -> (v.parent as? android.view.ViewGroup)?.removeView(v); f.addView(v) }
                        })
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (!one) Box(Modifier.size(32.dp).clickable { sc.launch { scroll.animateScrollBy(-260f) } }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronLeft, null, tint = th.sub, modifier = Modifier.size(22.dp)) }
                if (one) Row(Modifier.weight(1f).fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { chips() }
                else Row(Modifier.weight(1f).fillMaxSize().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) { chips() }
                if (!one) Box(Modifier.size(32.dp).clickable { sc.launch { scroll.animateScrollBy(260f) } }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronRight, null, tint = th.sub, modifier = Modifier.size(22.dp)) }
            }
        } else if (st.emailBar) {
            EmailChips(st, th, act)
        } else {
            Row(Modifier.weight(1f).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // long words get more of the bar; when three would not fit whole, the last one steps aside so no word is cut with dots
                val plainLen = st.slots.map { if (it.startsWith("\u0001") || it.startsWith("\u0002")) 0 else it.length }
                val crowded = false && st.slots.count { it.isNotEmpty() } == 3 && plainLen.sum() > 26 && !st.slots[2].startsWith("\u0001") && !st.slots[2].startsWith("\u0002")
                st.slots.forEachIndexed { i, s0 ->
                    val s = if (crowded && i == 2) "" else s0
                    // the copied item and the photo in the right slot get a wider place to tap
                    val wt = if (s.isEmpty() && crowded && i == 2) 0.001f else 1f
                    Box(Modifier.weight(wt).fillMaxSize().combinedClickable(enabled = s.isNotEmpty(), onClick = { if (st.banAsk == i) act.banSlot(i) else act.slot(i) }, onLongClick = { act.longFeedback(); act.slotLong(i) }), contentAlignment = Alignment.Center) {
                        if ((st.previewText != null || st.previewShot) && i == 2) SlotPreview(st, th)
                        if (st.banAsk == i) Icon(Icons.Rounded.Block, tr("Never suggest", "Bir daha önerme"), tint = th.accent, modifier = Modifier.size(26.dp))
                        else if (s.startsWith("\u0002")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            // the photo keeps its own shape (a portrait one stands upright), inside a box as tall as the bar allows
                            st.shotThumb?.let { Box(contentAlignment = Alignment.Center) {
                                // a stack of pictures: two sheets peek out behind the first, and a number says how many
                                // the sheets behind are the same picture, turned a little each way like a pile that was dropped; the top one is the real picture, whole
                                if (st.shotCount > 1) {
                                    Image(it, null, Modifier.height(36.dp).widthIn(max = 54.dp).rotate(-11f).offset((-3).dp, (-1).dp).clip(RoundedCornerShape(5.dp)).border(1.dp, th.bg, RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop, alpha = .75f)
                                    if (st.shotCount > 2) Image(it, null, Modifier.height(36.dp).widthIn(max = 54.dp).rotate(9f).offset(4.dp, (-2).dp).clip(RoundedCornerShape(5.dp)).border(1.dp, th.bg, RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop, alpha = .85f)
                                }
                                Image(it, null, Modifier.height(38.dp).widthIn(max = 58.dp).rotate(if (st.shotCount > 1) -3f else 0f).clip(RoundedCornerShape(5.dp)).then(if (st.shotCount > 1) Modifier.border(1.dp, th.bg, RoundedCornerShape(5.dp)) else Modifier), contentScale = ContentScale.Fit)
                                if (st.shotCount > 1) Text("${st.shotCount}", color = th.onAccent, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.BottomEnd).background(th.accent, CircleShape).padding(horizontal = 5.dp, vertical = 1.dp))
                                // a video: a small black disc with a white play mark in the middle, seen on a white picture too
                                if (st.shotVideo) Canvas(Modifier.size(16.dp)) {
                                    drawCircle(Color.Black.copy(alpha = .78f))
                                    val r = size.minDimension / 2f
                                    drawPath(androidx.compose.ui.graphics.Path().apply {
                                        moveTo(center.x - r * .32f, center.y - r * .45f); lineTo(center.x + r * .5f, center.y); lineTo(center.x - r * .32f, center.y + r * .45f); close()
                                    }, Color.White)
                                }
                            } }
                            // a file: a small black tile with the colour and glyph of its kind (the same as in Files); the letters of its kind when the kind has none
                            if (st.shotThumb == null && !st.shotVideo && st.shotFile.isNotEmpty()) {
                                val badge = com.umutk.pulse.features.exBadgeOf(st.shotFile)
                                val tint = badge?.first ?: th.sub
                                Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black).border(1.dp, tint.copy(alpha = .6f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                    if (badge != null) Icon(badge.second, null, tint = tint, modifier = Modifier.size(18.dp))
                                    else Text(st.shotFile.take(3), color = tint, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
                                }
                                // its name beside the tile, as much of it as fits (like a row of Files)
                                if (st.shotFileName.isNotEmpty()) Text(st.shotFileName, color = th.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 6.dp).widthIn(max = 120.dp))
                            }
                            // a video with no picture of its own: the play mark alone, a little bigger
                            if (st.shotThumb == null && st.shotVideo) Canvas(Modifier.size(26.dp)) {
                                drawCircle(Color.Black.copy(alpha = .78f))
                                val r = size.minDimension / 2f
                                drawPath(androidx.compose.ui.graphics.Path().apply {
                                    moveTo(center.x - r * .32f, center.y - r * .45f); lineTo(center.x + r * .5f, center.y); lineTo(center.x - r * .32f, center.y + r * .45f); close()
                                }, Color.White)
                            }
                        }
                        else if (s.startsWith("\u0003")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            // a file named by what is being typed: its picture when it has one (a photo, a page, a cover), else a small tile of its kind (the colour and glyph of Files), and its name
                            val fname = s.drop(1)
                            val badge = com.umutk.pulse.features.exBadgeOf(fname.substringAfterLast('.', ""))
                            val tint = badge?.first ?: th.sub
                            val thumb = st.fileThumb
                            if (thumb != null) Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, tint.copy(alpha = .55f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black).border(1.dp, tint.copy(alpha = .6f), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                if (badge != null) Icon(badge.second, null, tint = tint, modifier = Modifier.size(17.dp))
                                else Text(fname.substringAfterLast('.', "").take(3).uppercase(), color = tint, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
                            }
                            Text(fname, color = th.sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                        }
                        else if (s.startsWith("\u0001")) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(kbIcon(th, "ContentPaste"), null, tint = th.sub, modifier = Modifier.size(16.dp))
                            Text(s.drop(1).replace('\n', ' ').take(14) + if (s.length > 15) "…" else "", color = th.sub, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            // with a hardware keyboard: the function key that picks this word is written small in front of it
                            if (st.hardware && s.isNotEmpty()) Text("F${i + 1}", color = th.sub.copy(alpha = .55f), fontSize = 10.sp, modifier = Modifier.padding(end = 4.dp))
                            Text(s, color = if (i == 1) th.text else th.sub, fontSize = 20.sp, fontWeight = if (i == 1) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

internal val emojiTabIconsPublic get() = emojiTabIcons
private val emojiTabIcons get() = listOf(Icons.Rounded.AccessTime, Icons.Rounded.EmojiEmotions, Icons.Rounded.Person, Icons.Rounded.Pets, Icons.Rounded.Restaurant, Icons.Rounded.DirectionsCar,
    Icons.Rounded.SportsSoccer, Icons.Rounded.Lightbulb, Icons.Rounded.Tag, Icons.Rounded.Flag)

/** The order of the emoji page's tabs (ids: 0 recent, 1..9 the Unicode groups, 10 kaomoji, 11 symbols and presets); the pages follow it. */
private val EMOJI_ORDER = listOf(0, 1, 11, 2, 3, 10, 4, 5, 6, 7, 8, 9, 12)

/** The bar above the emoji page: a back button, then the categories (recent, faces, people, animals, food, travel, sport, objects, symbols, flags, kaomoji, signs). */
@Composable
private fun EmojiTopBar(th: KbTheme, st: KbState, act: KbActions) {
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        // (the Samsung look: "ABC" leads back to the letters, and the delete key sits at the other end of the categories)
        if (th.samsung) Box(Modifier.padding(horizontal = 6.dp).height(34.dp).clip(RoundedCornerShape(9.dp)).background(th.special).clickable { act.page(0) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("ABC", color = th.text, fontSize = 14.sp) }
        else BarIcon(th, kbIcon(th, "ArrowBack")) { act.page(0) }
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            // recent, faces, then the symbols and presets beside the smiley, people, animals, the kaomoji between animals and food, the rest
            EMOJI_ORDER.forEach { i ->
                if (i == 10) Box(Modifier.width(48.dp).fillMaxHeight().clickable { st.emojiTab = 10 }, contentAlignment = Alignment.Center) { Text("(ツ)", color = if (st.emojiTab == 10) th.text else th.sub, fontSize = 15.sp) }
                else if (i == 12) Box(Modifier.width(48.dp).fillMaxHeight().clickable { st.emojiTab = 12 }, contentAlignment = Alignment.Center) {
                    Text("GIF", color = if (st.emojiTab == 12) th.text else th.sub, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (st.emojiTab == 12) Box(Modifier.align(Alignment.BottomCenter).width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(th.text))
                }
                else if (i == 11) Box(Modifier.width(44.dp).fillMaxHeight().clickable { st.emojiTab = 11 }, contentAlignment = Alignment.Center) { Text("✦", color = if (st.emojiTab == 11) th.text else th.sub, fontSize = 20.sp) }
                else Box(Modifier.width(44.dp).fillMaxHeight().clickable { st.emojiTab = i }, contentAlignment = Alignment.Center) {
                    Icon(emojiTabIcons[i], null, tint = if (i == st.emojiTab) th.text else th.sub, modifier = Modifier.size(24.dp))
                    if (i == st.emojiTab) Box(Modifier.align(Alignment.BottomCenter).width(26.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(th.text))
                }
            }
        }
        if (th.samsung) Box(Modifier.size(44.dp).clickable { act.backspace() }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Backspace"), null, tint = th.text, modifier = Modifier.size(24.dp)) }
    }
  }
}

/** The translation bar: the two languages at the ends with the swap button between them, and the translation of what is typed below; a tap puts it in. */
@Composable
private fun TransBar(th: KbTheme, st: KbState, act: KbActions) {
    fun name(c: String) = java.util.Locale(c).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }.ifEmpty { c.uppercase() }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            BarIcon(th, kbIcon(th, "Close")) { act.transOp("close") }
            Box(Modifier.weight(1f).fillMaxHeight().clickable { act.transOp("from") }.padding(start = 6.dp), contentAlignment = Alignment.CenterStart) { Text(name(st.transFrom), color = th.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
            Box(Modifier.size(40.dp).clickable { act.transOp("swap") }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SwapHoriz, null, tint = th.accent, modifier = Modifier.size(24.dp)) }
            Box(Modifier.weight(1f).fillMaxHeight().clickable { act.transOp("to") }.padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) { Text(name(st.transTo), color = th.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
        // one row, two halves: what is typed at the left (the keys write here), its translation at the right (a tap puts it in the text box)
        Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(end = 6.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (st.transIn.isEmpty()) tr("Type here…", "Buraya yaz…") else st.transIn + "▏", color = if (st.transIn.isEmpty()) th.sub else th.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.width(1.dp).fillMaxHeight(.6f).background(th.sub.copy(alpha = .4f)))
            Box(Modifier.weight(1f).fillMaxHeight().clickable(enabled = st.transOut.isNotBlank()) { act.transOp("apply") }.padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(if (st.transOut.isNotBlank()) st.transOut else if (st.transBusy) "…" else tr("Translation", "Çeviri"), color = if (st.transOut.isNotBlank()) th.accent else th.sub, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A round bar button with a Fluent icon (the Fluent path of [key] drawn in the bar's colours). */
@Composable
private fun FluentBarIcon(th: KbTheme, key: String, on: Boolean = false, onClick: () -> Unit) {
    val paths = remember(key) { (com.umutk.pulse.FluentPaths.map[key] ?: emptyList()).map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() } }
    val tint = if (on) th.accent else th.text
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        // (the Samsung look: a filled round button, no ring)
        Box((if (th.samsung) Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) th.accent.copy(alpha = .28f) else lerp(th.menu, th.text, .1f)) else Modifier.size(30.dp).border(1.5.dp, if (on) th.accent else th.sub, RoundedCornerShape(15.dp))), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                scale(size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
            }
        }
    }
}

@Composable
private fun BarIcon(th: KbTheme, icon: ImageVector, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        // (the Samsung look: a filled round button, no ring)
        Box((if (th.samsung) Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) th.accent.copy(alpha = .28f) else lerp(th.menu, th.text, .1f)) else Modifier.size(30.dp).border(1.5.dp, if (on) th.accent else th.sub, RoundedCornerShape(15.dp))), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (on) th.accent else th.text, modifier = Modifier.size(if (th.samsung) 16.dp else 20.dp)) }
    }
}

internal fun toolIcon(th: KbTheme, id: String): ImageVector = when (id) {
    "clip", "paste" -> kbIcon(th, "ContentPaste"); "auth" -> Icons.Rounded.Lock; "vault" -> Icons.Rounded.Key; "dict" -> Icons.Rounded.MenuBook; "gif" -> Icons.Rounded.Gif; "stickers" -> Icons.Rounded.StickyNote2; "grammar" -> Icons.Rounded.Spellcheck; "scan" -> Icons.Rounded.DocumentScanner; "calc" -> Icons.Rounded.Calculate; "convert" -> Icons.Rounded.SwapHoriz; "media" -> Icons.Rounded.PlayArrow; "mediaNext" -> Icons.Rounded.SkipNext; "gamepad" -> Icons.Rounded.SportsEsports; "emoji" -> kbIcon(th, "EmojiEmotions"); "selectAll" -> kbIcon(th, "SelectAll"); "copy" -> kbIcon(th, "ContentCopy")
    "cut" -> kbIcon(th, "ContentCut"); "undo" -> kbIcon(th, "Undo"); "redo" -> kbIcon(th, "Redo"); "menu" -> kbIcon(th, "MoreHoriz"); "resize" -> kbIcon(th, "AspectRatio")
    "incognito" -> kbIcon(th, "VisibilityOff"); "arrows" -> kbIcon(th, "OpenWith"); "proRow" -> kbIcon(th, "Dashboard"); "keyset" -> kbIcon(th, "Keyboard"); "numbers" -> kbIcon(th, "Pin")
    "layout" -> kbIcon(th, "Dashboard")
    "theme" -> kbIcon(th, "Palette"); "settings" -> kbIcon(th, "Settings"); "lang", "languages" -> kbIcon(th, "Language"); "onehand" -> kbIcon(th, "TouchApp")
    "autocorrect" -> kbIcon(th, "Spellcheck"); "glide" -> kbIcon(th, "Gesture"); "aifix" -> androidx.compose.material.icons.Icons.Rounded.AutoAwesome
    "voice" -> Icons.Rounded.Mic; "translate" -> Icons.Rounded.Translate; "email" -> Icons.Rounded.AlternateEmail; else -> kbIcon(th, "Keyboard")
}

/** What a long press on this kind of key does, from Settings > Keyboard. */
private fun holdOf(k: KeyKind): String = when (k) {
    KeyKind.COMMA -> KeyboardPrefs.commaHold
    KeyKind.PERIOD -> KeyboardPrefs.periodHold
    // the emoji key: holding it opens the GIFs (when no other action is chosen for it), and a small GIF mark sits on it
    KeyKind.EMOJI -> KeyboardPrefs.bottomHold.let { if (it == "none") "gif" else it }
    KeyKind.MENU, KeyKind.LANG -> KeyboardPrefs.bottomHold
    else -> "none"
}

/** The icon of what the letter [ch] does with the Ctrl or Win that waits (copy, paste, lock ...); null for the others (Alt gives a character, written). */
private fun chordIcon(st: KbState, ch: Char): ImageVector? {
    val c = ch.lowercaseChar()
    if (st.win > 0 && st.ctrl == 0) return when (c) { 'd' -> Icons.Rounded.Home; 'l' -> Icons.Rounded.Lock; 'a' -> Icons.Rounded.Tune; 'n' -> Icons.Rounded.Notifications; else -> null }
    if (st.ctrl > 0 && st.alt == 0 && st.win == 0) return when (c) {
        'a' -> Icons.Rounded.SelectAll; 'c' -> Icons.Rounded.ContentCopy; 'x' -> Icons.Rounded.ContentCut; 'v' -> Icons.Rounded.ContentPaste; 'y' -> Icons.Rounded.Redo; 'z' -> Icons.Rounded.Undo; else -> null
    }
    return null
}

/** What the letter [ch] does with the Ctrl, Alt or Win that waits: a short name, or the character Alt gives (null: nothing special). */
private fun chordHint(st: KbState, ch: Char): String? {
    val c = ch.lowercaseChar()
    if (st.win > 0 && st.ctrl == 0) when (c) { 'd' -> return tr("Home", "Ana ekran"); 'l' -> return tr("Lock", "Kilit"); 'a' -> return tr("Quick", "Hızlı"); 'n' -> return tr("Notif.", "Bildirim") }
    if (st.ctrl > 0 && st.alt == 0 && st.win == 0) when (c) {
        'a' -> return tr("All", "Hepsi"); 'c' -> return tr("Copy", "Kopya"); 'x' -> return tr("Cut", "Kes"); 'v' -> return tr("Paste", "Yapıştır"); 'y' -> return tr("Redo", "İleri"); 'z' -> return tr("Undo", "Geri al")
    }
    if (st.alt > 0 && st.ctrl == 0 && st.win == 0) {
        // the character the key gives with Alt (Alt+S: ß)
        val code = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(charArrayOf(c))?.firstOrNull()?.keyCode ?: return null
        val u = android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD).get(code, android.view.KeyEvent.META_ALT_ON)
        if (u > 32 && u.toChar() != c) return u.toChar().toString()
    }
    return null
}

/** The name of what holding [key] does, for its bubble (null: nothing, or its own pop-up characters). */
private fun holdName(key: KbKey, st: KbState): String? {
    val id = when (key.kind) {
        KeyKind.COMMA, KeyKind.PERIOD, KeyKind.MENU, KeyKind.EMOJI, KeyKind.LANG -> holdOf(key.kind).takeIf { it != "none" }
        KeyKind.CHAR -> if ((st.numpad || st.forceLayout == "numpad") && key.label in listOf("1", "2", "3")) when (key.label) { "1" -> "clip"; "2" -> "auth"; else -> "vault" } else null
        else -> null
    } ?: return null
    return cornerName(id)
}

/** A key of the key bar or a menu tile by its name (Esc, Tab, Copy, Clipboard ...). */
private fun cornerName(id: String): String =
    ExtraKeys.byId(id)?.label ?: (ExtraKeys.menu + ExtraKeys.toolbar).firstOrNull { it.first == id }?.second ?: when (id) { "voice" -> tr("Voice", "Ses"); else -> id }

private fun runHold(a: KbActions, id: String) = when (id) { "emoji" -> a.emojiPanel(); "voice" -> a.voice(); "lang" -> a.lang(); else -> a.menu(id) }

/** The small icon at the top of a key whose long press runs an action. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.HoldHint(th: KbTheme, kind: KeyKind, listening: Boolean = false) {
    val h = holdOf(kind)
    // the speech icon turns to the accent while it listens
    if (h != "none") Icon(toolIcon(th, h), null, tint = if (listening) th.accent else th.text.copy(alpha = .7f), modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(15.dp))
}

private fun toolOn(id: String, st: KbState) = when (id) { "incognito" -> st.incognito; "autocorrect" -> KeyboardPrefs.autocorrect; "onehand" -> KeyboardPrefs.oneHand != "off"; "glide" -> KeyboardPrefs.glide; "arrows" -> KeyboardPrefs.arrows; "keyset" -> st.panel == "keyset"; "proRow" -> (if (KeyboardPrefs.theme == "samsung") KeyboardPrefs.samProRow else KeyboardPrefs.proRowAt(st.land)); "numbers" -> KeyboardPrefs.numberRowAt(st.land); "layout" -> st.panel == "layout" || st.forceLayout != null; else -> false }

/** The keyboard is drawn for a screen turned sideways (its own height and rows, see KeyboardPrefs.heightAt). */
@Composable
private fun kbLand(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

// ---- the keys --------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun KeysPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val up = st.shift > 0
    val land = kbLand()
    // (the Samsung look has its number row when held upright)
    val numRow = KeyboardPrefs.numberRowAt(land) || (th.samsung && !land)
    val digits = !numRow && KeyboardPrefs.longPressSymbols
    val langs = KeyboardPrefs.langList()
    val lang = KeyboardLayouts.lang(if (st.lang in langs) st.lang else langs.first())
    val rows = run { KeyboardPrefs.layoutRev; KeyboardLayouts.letterRows(lang.code, up, digits) }
    val n1 = rows[0].size.toFloat()
    // the direction keyboard: nine swipe keys (letters page only; the symbol pages stay the ordinary ones)
    if (KeyboardPrefs.dirLayout && st.page == 0) { ThumbPanel(th, st, act, keyH); return }
    val extraRow = if (th.samsung) KeyboardPrefs.samProRow else KeyboardPrefs.proRowAt(land)
    if (extraRow && !KeyboardPrefs.proBottom) ExtraRow(th, st, act, keyH)
    if (KeyboardPrefs.arrows) {
        // the key bar: Fn (its twelve keys open from it), Shift, the arrows, Home / End, Page up / down, Esc, Tab and the editing keys
        if (st.fnOpen) {
            listOf((1..6), (7..12)).forEach { r ->
                Row(Modifier.fillMaxWidth()) { r.forEach { n -> ExtraKeys.byId("f$n")?.let { d -> ExtraCell(th, 0.dp, keyH * .8f, d, 0, Modifier.weight(1f)) { act.extraKey(d.id); st.fnOpen = false } } } }
            }
        } else BoxWithConstraints(Modifier.fillMaxWidth()) {
            // the arrows are left out: the bottom extra row already has them by default, and the same four icons twice just crowded the bar
            val ids = listOf("fn", "shiftm", "home", "end", "pgup", "pgdn", "esc", "tab", "selectAll", "copy", "paste")
            val w = maxWidth / 10
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ids.mapNotNull { ExtraKeys.byId(it) }.forEach { d ->
                    val level = when (d.id) { "shiftm" -> st.shiftMod; else -> 0 }
                    ExtraCell(th, w, keyH * .8f, d, level, hold = holdOf(d, act)) { act.extraKey(d.id) }
                }
            }
        }
    }
    if (st.page == 0 && KeyboardPrefs.multiTap) {
        // the phone keypad: three keys a row, the same key again gives the next letter on it
        MultiTap.rows(lang.code).forEach { r -> Row(Modifier.fillMaxWidth()) { r.forEach { chars -> Key(th, st, act, KbKey(chars, KeyKind.MULTI, n1 / 3f), keyH) } } }
        Row(Modifier.fillMaxWidth()) {
            Key(th, st, act, KbKey("", KeyKind.SHIFT, n1 / 3f), keyH)
            Key(th, st, act, KbKey("0", KeyKind.CHAR, n1 / 3f), keyH)
            Key(th, st, act, KbKey("", KeyKind.DELETE, n1 / 3f), keyH)
        }
    } else if (st.page == 0) {
        if (numRow) Row(Modifier.fillMaxWidth()) {
            "1234567890".forEach { d -> Key(th, st, act, KbKey(d.toString(), KeyKind.CHAR, n1 / 10f, KeyboardLayouts.symbolAlts[d].orEmpty()), keyH, small = true) }
        }
        // split keys (for a tablet): a gap in the middle of each row, so both thumbs reach their half
        // the keys are split only when the "Split keys" setting is on, tablet or phone: so the screen matches the setting (joined by default)
        val gapW = if (KeyboardPrefs.split) KeyboardPrefs.splitGap / 10f else 0f
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
    if (th.samsung) Row(Modifier.fillMaxWidth()) {
        // the Samsung bottom row: !#1, comma, the space bar with the language's name, period, the action key (the emoji live in the toolbar)
        // (the widths measured on the real keyboard, in letter keys: 1.5, 1, 5, 1, 1.5)
        Key(th, st, act, KbKey(if (st.page == 0) "!#1" else "ABC", KeyKind.MODE, 1.5f), bh)
        Key(th, st, act, KbKey(",", KeyKind.COMMA, 1f, listOf("😀") + KeyboardLayouts.popupComma), bh)
        Key(th, st, act, KbKey(lang.name, KeyKind.SPACE, 5f), bh)
        Key(th, st, act, KbKey(".", KeyKind.PERIOD, 1f, KeyboardLayouts.popupPeriod), bh)
        Key(th, st, act, KbKey("", KeyKind.ENTER, 1.5f), bh)
    } else Row(Modifier.fillMaxWidth()) {
        if (low && st.page == 0) Key(th, st, act, KbKey("", KeyKind.SHIFT, 1f), bh)
        Key(th, st, act, KbKey(if (st.page == 0) "?123" else "abc", KeyKind.MODE, (if (low) 1.0f else 1.2f) * (1f - KeyboardPrefs.modeShrink / 100f)), bh)
        // the key beside ?123: the three-dot menu, the emoji panel, or nothing (the emoji stay under a long press on the comma)
        if (KeyboardPrefs.bottomKey != "none" && st.page == 0) Key(th, st, act, KbKey("", if (KeyboardPrefs.bottomKey == "emoji") KeyKind.EMOJI else KeyKind.MENU, 1f), bh)
        Key(th, st, act, KbKey(",", KeyKind.COMMA, 1f - KeyboardPrefs.commaShrink / 100f, if (KeyboardPrefs.bottomKey == "emoji") KeyboardLayouts.popupComma else listOf("😀") + KeyboardLayouts.popupComma), bh)
        Key(th, st, act, KbKey(if (KeyboardPrefs.spaceLabel) lang.name else "", KeyKind.SPACE, if (low) 4.25f else 4.3f), bh)
        Key(th, st, act, KbKey(".", KeyKind.PERIOD, 1f, KeyboardLayouts.popupPeriod), bh)
        Key(th, st, act, KbKey("", KeyKind.ENTER, 1.5f), bh)
    }
    if (KeyboardPrefs.bottomBright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.bottomBright) / 100f)))
    }
    if (extraRow && KeyboardPrefs.proBottom) Box { ExtraRow(th, st, act, keyH); if (KeyboardPrefs.bottomBright < 100) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = (100 - KeyboardPrefs.bottomBright) / 100f))) }
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
                ExtraCell(th, 0.dp, keyH * .8f, d, level, Modifier.weight(1f), hold = holdOf(d, act)) { act.extraKey(d.id) }
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f).height(keyH * .8f).padding(2.dp).clip(RoundedCornerShape(th.radius.dp)).background(th.special).clickable { st.panel = "keys" }, contentAlignment = Alignment.Center) { Text("abc", color = th.specialText, fontSize = 15.sp) }
    }
}

/** The extra row (and a second one when it is on): Esc, Tab, Ctrl, Alt, Win, arrows, F-keys, ... as many as the user put there. The modifier keys wait for the next key. */
@Composable
private fun ExtraRow(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    @Composable fun one(list: String) {
        val defs = list.split(',').mapNotNull { ExtraKeys.byId(it) }
        if (defs.isEmpty()) return
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = if (defs.size * 42 <= maxWidth.value) maxWidth / defs.size else 46.dp
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                defs.forEach { d ->
                    val level = when (d.id) { "ctrl" -> st.ctrl; "alt" -> st.alt; "win" -> st.win; "shiftm" -> st.shiftMod; else -> 0 }
                    ExtraCell(th, w, keyH * .78f, d, level, hold = holdOf(d, act)) { act.extraKey(d.id) }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        one(KeyboardPrefs.proKeys)
        if (KeyboardPrefs.proRow2) {
            // the F keys come up here when Fn is pressed
            if (st.fnOpen) {
                listOf((1..6), (7..12)).forEach { r -> Row(Modifier.fillMaxWidth()) { r.forEach { n -> ExtraKeys.byId("f$n")?.let { d -> ExtraCell(th, 0.dp, keyH * .78f, d, 0, Modifier.weight(1f)) { act.extraKey(d.id); st.fnOpen = false } } } } }
            } else one(KeyboardPrefs.proKeys2)
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

/** A modifier key's hold handler (see KbActions.modHold); none for the other keys. */
private fun holdOf(d: ExtraKeyDef, act: KbActions): ((Boolean) -> Unit)? = if (d.mod) { down -> act.modHold(d.id, down) } else null

@Composable
private fun ExtraCell(th: KbTheme, w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp, d: ExtraKeyDef, level: Int, modifier: Modifier = Modifier, hold: ((Boolean) -> Unit)? = null, onTap: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    val holdNow by rememberUpdatedState(hold)
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
                } else if (holdNow != null) {
                    // a modifier: held while another key is pressed it works like a real keyboard's; a quick tap alone switches it on for the next key
                    holdNow?.invoke(true)
                    waitForUpOrCancellation()
                    holdNow?.invoke(false)
                } else {
                    // a drag along the row scrolls it: only a finger that lifts where it came down is a tap
                    val up = waitForUpOrCancellation()
                    if (up != null) tap()
                }
                down = false
            }
        }.padding(KeyboardPrefs.gap.dp / 2),
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
                val d = awaitFirstDown(requireUnconsumed = false); d.consume(); down = true
                // the key must not stay lit when the touch is cancelled or the tap throws
                try {
                    tap()
                    var due = android.os.SystemClock.uptimeMillis() + 380L
                    while (true) {
                        val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                        if (ev == null) { if (repeat) tap(); due = android.os.SystemClock.uptimeMillis() + 70L; continue }
                        val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                        c.consume()
                        if (c.changedToUp() || !c.pressed) break
                    }
                } finally { down = false }
            }
        }.padding(KeyboardPrefs.gap.dp / 2),
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
    // a key whose corners can be swiped: a letter (its own switch), or a number / symbol key with a preset chosen
    val cornerKey = isLetter || (k.kind == KeyKind.CHAR && k.label.length == 1 && !k.label[0].isLetter() && KeyboardPrefs.numCorners != "off" && KeyCorners.of(k.label) != null)
    val cornerNow by rememberUpdatedState(cornerKey)
    fun cornersOn() = if (isLetterNow) KeyboardPrefs.corners else KeyboardPrefs.numCorners != "off"
    val view = androidx.compose.ui.platform.LocalView.current
    val bg = if (th.samsung) (when (k.kind) { KeyKind.CHAR, KeyKind.MULTI, KeyKind.SPACE, KeyKind.COMMA, KeyKind.PERIOD -> th.key; else -> th.special })
        else when (k.kind) { KeyKind.CHAR, KeyKind.MULTI, KeyKind.SPACE, KeyKind.ENTER -> lerp(th.key, androidx.compose.ui.graphics.Color(0xFF808080), .07f); else -> th.special }
    // a cap of the person's own: its colour replaces the theme's (the press still lightens it)
    val capKey = when (k.kind) { KeyKind.SPACE -> "space"; KeyKind.ENTER -> "enter"; KeyKind.SHIFT -> "shift"; KeyKind.DELETE -> "delete"; else -> k.label.lowercase().takeIf { k.kind == KeyKind.CHAR && it.length == 1 } }
    val own = capKey?.let { KeyboardPrefs.capColors[it] }?.let { androidx.compose.ui.graphics.Color(it) }
    val bgNow = own ?: bg
    val fill by animateColorAsState(if (down) lerp(bgNow, th.text, .2f) else bgNow, tween(if (KeyboardPrefs.animOn) 90 else 0), label = "key")
    val pressedScale = when (KeyboardPrefs.motionFxNow) { 1 -> 1.1f; 2 -> .86f; 3 -> .9f; else -> .93f }
    val grow by animateFloatAsState(if (down && KeyboardPrefs.animOn) pressedScale else 1f, if (KeyboardPrefs.motionFxNow == 3) androidx.compose.animation.core.spring(dampingRatio = .35f, stiffness = 600f) else tween(70), label = "keyScale")
    Box(
        Modifier.weight(k.weight).height(if (small) keyH * (if (th.samsung) .86f else .78f) else keyH).onGloballyPositioned { rect = it.boundsInRoot(); if (isLetter) st.letterRects[KeyboardLayouts.lower(k.label, st.lang)] = rect }
            // not keyed on the label: a shift change renames every letter key, and a restart would drop a touch that is under way (the key then stayed shown as pressed and never typed)
            .pointerInput(k.kind) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false); d.consume()
                    down = true
                    if (kNow.kind != KeyKind.MULTI) MultiTap.reset()
                    // the key-press effect starts where this key is
                    if (KeyboardPrefs.pressFxNow > 0) { if (st.bursts.size > 4) st.bursts.removeAt(0); st.bursts.add(KbBurst(rect, System.nanoTime() / 1_000_000)) }
                    // however the touch ends (up, cancel, the key leaving the screen, an action that throws) the key must not stay shown as pressed
                    try {
                    val key = kNow; val a = actNow
                    a.feedback()
                    val big = key.kind == KeyKind.CHAR || key.kind == KeyKind.COMMA || key.kind == KeyKind.PERIOD
                    if (big && KeyboardPrefs.keyPopups) st.preview = KbPreview(rect, key.label)
                    // a key whose hold does something (the comma's voice, the key beside ?123, the pad's 1-2-3): its bubble names what holding it will do
                    holdName(key, st)?.let { st.preview = KbPreview(rect, key.label, listOf(it), 0) }
                    // a corner swipe shows the symbol (or the key) it will type while the finger is still down
                    var hinting = false
                    var long = false
                    // a key whose long press runs an action (the comma's voice typing): the pop-up characters never open for it
                    var held = false
                    var sel = -1
                    var spaceDragged = false
                    var lastStep = 0
                    var quick = false
                    var delRepeats = 0
                    var startX = d.position.x
                    var startY = d.position.y
                    var lastLine = 0
                    var curX = d.position.x
                    var curY = d.position.y
                    // the long press and the repeats fall due at a fixed time: the finger's small moves must not push them back
                    var due = android.os.SystemClock.uptimeMillis() + longMs
                    var glide = false
                    val seq = StringBuilder()
                    val startRoot = Offset(rect.left + d.position.x, rect.top + d.position.y)
                    var curRoot = startRoot
                    // circles and round trips on a letter: how far the finger went, how much the path turned (degrees) and its farthest point
                    var pathLen = 0f
                    var turned = 0.0
                    var segAng = Double.NaN
                    var maxOut = 0f
                    var lastPt = startRoot
                    val canGlide = isLetterNow && KeyboardPrefs.glide && !KeyboardPrefs.corners && !KeyboardPrefs.circleGestures && st.page == 0 && st.panel == "keys"
                    // a tap deletes its letter on release; held, the first repeat already takes a whole word (no single letter first)
                    if (key.kind == KeyKind.DELETE) due = android.os.SystemClock.uptimeMillis() + 380L
                    while (true) {
                        if (st.swiped) { st.preview = null; break }
                        val ev = withTimeoutOrNull((due - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1L)) { awaitPointerEvent() }
                        if (ev == null) {
                            // the long press (or the next repeat of the delete key)
                            val now = android.os.SystemClock.uptimeMillis()
                            when (key.kind) {
                                KeyKind.DELETE -> {
                                    // held long enough: a whole word per repeat instead of a letter, with a tick under the finger each time
                                    if (!quick) { delRepeats++; a.deleteWord(); view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); due = now + maxOf(55L, 230L - delRepeats * 22L) } else due = now + 55L
                                }
                                KeyKind.SPACE -> { if (KeyboardPrefs.cursorControl) { long = true; spaceDragged = true; st.cursorDrag = true; startX = curX; startY = curY; lastStep = 0; lastLine = 0; a.feedback() }; due = Long.MAX_VALUE / 2 }
                                KeyKind.SHIFT -> { long = true; due = Long.MAX_VALUE / 2 }
                                // the comma, the period and the key beside ?123 run the action chosen for them in Settings; "none" leaves the key's own pop-up characters
                                KeyKind.COMMA, KeyKind.PERIOD, KeyKind.MENU, KeyKind.EMOJI, KeyKind.LANG -> {
                                    val h = holdOf(key.kind)
                                    if (h != "none") { long = true; held = true; st.preview = null; a.feedback(); runHold(a, h) }
                                    else if (!glide && key.alts.isNotEmpty() && big) { long = true; sel = 0; st.preview = KbPreview(rect, key.label, key.alts, 0); a.feedback() }
                                    due = Long.MAX_VALUE / 2
                                }
                                // the number pad's digits have no pop-up characters, so the hold is read here: 1 opens the clipboard, 2 the authenticator (it runs at once, the release then does nothing)
                                else -> if (key.kind == KeyKind.CHAR && (st.numpad || st.forceLayout == "numpad") && (key.label == "1" || key.label == "2" || key.label == "3")) {
                                    long = true; held = true; st.preview = null; a.feedback(); due = Long.MAX_VALUE / 2
                                    a.menu(when (key.label) { "1" -> "clip"; "2" -> "auth"; else -> "vault" })
                                } else if (!glide && key.alts.isNotEmpty() && big) {
                                    long = true; sel = 0; due = Long.MAX_VALUE / 2
                                    st.preview = KbPreview(rect, key.label, key.alts, 0); a.feedback()
                                } else due = Long.MAX_VALUE / 2
                            }
                            continue
                        }
                        val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                        c.consume()
                        curX = c.position.x
                        curY = c.position.y
                        curRoot = Offset(rect.left + c.position.x, rect.top + c.position.y)
                        if (KeyboardPrefs.circleGestures && isLetterNow) {
                            val seg = curRoot - lastPt
                            if (seg.getDistance() > 3.dp.toPx()) {
                                val ang = Math.toDegrees(kotlin.math.atan2(seg.y.toDouble(), seg.x.toDouble()))
                                if (!segAng.isNaN()) { var dA = ang - segAng; while (dA > 180) dA -= 360; while (dA < -180) dA += 360; turned += dA }
                                segAng = ang; pathLen += seg.getDistance(); lastPt = curRoot
                                maxOut = maxOf(maxOut, (curRoot - startRoot).getDistance())
                            }
                        }
                        if (!long && !glide && cornersOn() && cornerNow && st.page == 0 && !c.changedToUp()) {
                            val cn = KeyCorners.pick(key.label, curRoot - startRoot, 22.dp.toPx())
                            if (cn != null) {
                                val show = if (cn.length > 1 && cn.startsWith("@")) cornerName(cn.drop(1)) else cn
                                if (!hinting || st.preview?.alts?.firstOrNull() != show) st.preview = KbPreview(rect, key.label, listOf(show), 0)
                                hinting = true
                            } else if (hinting) { hinting = false; st.preview = if (big && KeyboardPrefs.keyPopups) KbPreview(rect, key.label) else null }
                        }
                        if (c.changedToUp() || !c.pressed) {
                            if (st.swiped) { st.preview = null; break }
                            val up = (startRoot.y - curRoot.y)
                            // corner swipes: the symbol or key written on the corner the key was swiped toward
                            val corner = if (cornersOn() && cornerNow && st.page == 0 && !long) KeyCorners.pick(key.label, curRoot - startRoot, 22.dp.toPx()) else null
                            // a circle drawn on the letter: its capital; out and back again: its first long-press character
                            val gesture = if (KeyboardPrefs.circleGestures && isLetterNow && st.page == 0 && !long) when {
                                abs(turned) >= 300.0 && pathLen > 50.dp.toPx() -> KeyboardLayouts.lang(st.lang).let { l -> key.label.uppercase(l.locale) }
                                maxOut > 30.dp.toPx() && (curRoot - startRoot).getDistance() < 16.dp.toPx() && abs(turned) < 250.0 -> key.alts.firstOrNull()
                                else -> null
                            } else null
                            when {
                                gesture != null -> { st.touch = null; a.type(gesture) }
                                corner != null && !(KeyboardPrefs.circleGestures && maxOut > 30.dp.toPx() && (curRoot - startRoot).getDistance() < 16.dp.toPx()) -> { st.touch = null; if (corner.startsWith("@")) a.extraKey(corner.drop(1)) else a.type(corner) }
                                key.kind == KeyKind.DELETE -> if (quick) a.quickDeleteEnd(true) else if (delRepeats == 0) a.backspace()
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
                                    KeyKind.MULTI -> {
                                        val (ch, again) = MultiTap.press(key.label)
                                        if (again) a.backspace()
                                        a.type(if (st.shift > 0) ch.uppercase() else ch)
                                    }
                                    KeyKind.SPACE -> a.space()
                                    KeyKind.SHIFT -> a.shift()
                                    KeyKind.ENTER -> a.enter()
                                    KeyKind.MODE -> a.page(if (st.page == 0) 1 else 0)
                                    KeyKind.SYMBOLS2 -> a.page(if (st.page == 1) 2 else 1)
                                    KeyKind.EMOJI -> a.emojiPanel()
                                    KeyKind.MENU -> a.menu("menu")
                                    KeyKind.LANG -> a.lang()
                                    else -> {}
                                }
                            }
                            break
                        }
                        val dx = c.position.x - startX
                        when {
                            // corner swipes: the pop-up shows what a release here would type
                            cornersOn() && cornerNow && st.page == 0 && !long -> {
                                val t = KeyCorners.pick(key.label, curRoot - startRoot, 22.dp.toPx())
                                if (KeyboardPrefs.keyPopups) st.preview = KbPreview(rect, if (t != null) KeyCorners.show(t) else key.label)
                            }
                            // a swipe over the letters: the keys it passes spell the word
                            canGlide && !long && (glide || (curRoot - startRoot).getDistance() > 30.dp.toPx()) -> {
                                if (!glide) { glide = true; st.preview = null; seq.append(KeyboardLayouts.lower(key.label, st.lang)); st.trail = listOf(startRoot) }
                                st.letterRects.entries.firstOrNull { it.value.contains(curRoot) }?.key?.let { l -> if (seq.isEmpty() || seq.last().toString() != l) seq.append(l) }
                                if (st.trail.size < 400) st.trail = st.trail + curRoot
                            }
                            key.kind == KeyKind.DELETE && KeyboardPrefs.quickDelete && !quick && dx < -28.dp.toPx() -> { quick = true; a.quickDeleteStart(); startX = c.position.x }
                            key.kind == KeyKind.DELETE && quick -> a.quickDelete((-dx / 14.dp.toPx()).toInt().coerceAtLeast(0))
                            // sliding along the space key moves the cursor, held first or not
                            key.kind == KeyKind.SPACE && KeyboardPrefs.cursorControl && !spaceDragged && (abs(dx) > 18.dp.toPx() || abs(c.position.y - startY) > 18.dp.toPx()) -> { spaceDragged = true; st.cursorDrag = true; long = true; startX = c.position.x; startY = c.position.y; lastStep = 0; lastLine = 0; a.feedback() }
                            spaceDragged && key.kind == KeyKind.SPACE -> {
                                // across: exactly a letter per unit of slide for the first six letters (it stops where the finger is, it never runs past a letter), and only after that the slide
                                // speeds up, gently, so a long swipe still crosses a line of text; and the cursor never takes more than four letters in one step: it follows the finger
                                val unit = 12.dp.toPx() * 100f / KeyboardPrefs.cursorSens
                                val n = dx / unit
                                val an = kotlin.math.abs(n)
                                val chars = (if (an <= 6f) n else kotlin.math.sign(n) * (6f + (an - 6f) * (1f + (an - 6f) / 24f))).toInt()
                                val stepNow = (chars - lastStep).coerceIn(-4, 4)
                                if (stepNow != 0) { a.moveCursor(stepNow); lastStep += stepNow }
                                // up and down: the cursor goes to the line above or below, at the same column
                                val lineUnit = 20.dp.toPx() * 100f / KeyboardPrefs.cursorSens
                                // only a slide that goes more up or down than across changes the line (a sideways slide that wobbles stays on its line)
                                val dyNow = c.position.y - startY
                                val lines = if (abs(dyNow) > abs(dx) * .8f) (dyNow / lineUnit).toInt() else lastLine
                                if (lines != lastLine) { a.moveLine(lines - lastLine); lastLine = lines }
                            }
                            long && !held && key.alts.isNotEmpty() -> {
                                val count = key.alts.size
                                val w = minOf(POPUP_W.dp.toPx(), st.rootW * .96f / count)
                                val left = popupLeft(rect, count, st.rootW, w)
                                val i = ((rect.left + c.position.x - left) / w).toInt().coerceIn(0, count - 1)
                                if (i != sel) { sel = i; st.preview = KbPreview(rect, key.label, key.alts, i); a.feedback() }
                            }
                        }
                    }
                    } finally {
                        down = false
                        st.cursorDrag = false
                        st.preview = null
                        st.trail = emptyList()
                    }
                }
            }.padding(horizontal = if (th.samsung) 3.dp else KeyboardPrefs.gap.dp * .75f, vertical = if (th.samsung) 6.dp else KeyboardPrefs.gap.dp / 2),
    ) {
        val special = k.kind != KeyKind.CHAR && k.kind != KeyKind.SPACE
        val fg = if (th.samsung && k.kind == KeyKind.ENTER) th.accent else when (k.kind) { KeyKind.CHAR, KeyKind.MULTI, KeyKind.SPACE, KeyKind.ENTER -> th.text; else -> th.specialText }
        val keyHue = LocalHue.current
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = grow; scaleY = grow }.keyLook(th, fill, down, RoundedCornerShape(th.radius.dp)).keyWash(KeyboardPrefs.keyFxNow, th.accent, with(androidx.compose.ui.platform.LocalDensity.current) { th.radius.dp.toPx() })
            .then(if (KeyboardPrefs.rgbNow == 2 && keyHue != null) Modifier.drawWithContent { drawContent(); val h = (keyHue.value + rect.left / maxOf(1f, st.rootW) * 300f) % 360f; drawRoundRect(Color.hsv(h, .9f, 1f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(th.radius.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())) } else Modifier)
            // the chasing light: a bright arc runs round the edge of the key (turning once per colour cycle, each key a little ahead of the one beside it), the rest of the edge stays faint
            .then(if (KeyboardPrefs.rgbNow == 3 && keyHue != null) Modifier.drawWithContent {
                drawContent()
                val c0 = Color.hsv((keyHue.value + rect.left / maxOf(1f, st.rootW) * 120f) % 360f, .9f, 1f)
                rotate(keyHue.value + rect.left / maxOf(1f, st.rootW) * 90f, center) {
                    drawRoundRect(androidx.compose.ui.graphics.Brush.sweepGradient(0f to Color.Transparent, .55f to c0.copy(alpha = .08f), .85f to c0.copy(alpha = .9f), 1f to c0, center = center),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(th.radius.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                }
            } else Modifier), contentAlignment = Alignment.Center) {
            // the picture chosen for this cap, under its letter
            capKey?.let { ck -> KeyboardPrefs.capPics[ck]?.let { path -> CapPicture(path, th.radius) } }
            val big = if (KeyboardPrefs.largeText) 27f else 22f
            // (the Samsung look: the letters are 27 sp and the digits 24 sp, measured on the real keyboard)
            val fs = if (th.samsung) 27.sp else (big * (.85f + .15f * KeyboardPrefs.heightAt(kbLand()) / 100f)).sp
            when (k.kind) {
                KeyKind.SHIFT -> ShiftGlyph(st.shift, fg)
                KeyKind.DELETE -> Icon(kbIcon(th, "Backspace"), null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.ENTER -> Icon(when (st.enter) { "send" -> kbIcon(th, "Send"); "search" -> kbIcon(th, "Search"); "go", "done" -> kbIcon(th, "Check"); "next" -> kbIcon(th, "ArrowForward"); else -> kbIcon(th, "KeyboardReturn") }, null, tint = fg, modifier = Modifier.size(26.dp))
                KeyKind.EMOJI -> { Icon(kbIcon(th, "EmojiEmotions"), null, tint = fg, modifier = Modifier.size(24.dp)); HoldHint(th, k.kind) }
                KeyKind.MENU -> { Icon(kbIcon(th, "MoreHoriz"), null, tint = fg, modifier = Modifier.size(24.dp)); HoldHint(th, k.kind) }
                KeyKind.LANG -> { Icon(kbIcon(th, "Language"), null, tint = fg, modifier = Modifier.size(24.dp)); HoldHint(th, k.kind) }
                KeyKind.SPACE -> { SpaceDeco(); Text(k.label, color = if (th.samsung) th.text else th.sub, fontSize = if (th.samsung) 14.sp else 12.sp, maxLines = 1) }
                KeyKind.MODE, KeyKind.SYMBOLS2 -> Text(k.label, color = fg, fontSize = if (th.samsung) 18.sp else 15.sp, maxLines = 1)
                KeyKind.MULTI -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(k.label.last().toString(), color = fg, fontSize = (fs.value * .8f).sp, maxLines = 1)
                    Text(k.label.dropLast(1).let { if (st.shift > 0) it.uppercase() else it }, color = th.sub, fontSize = 11.sp, maxLines = 1)
                }
                else -> {
                    // corner swipes: the small labels on the corners say what a swipe toward them gives
                    if (((isLetter && KeyboardPrefs.corners) || (!isLetter && cornerKey)) && KeyboardPrefs.cornerHints && st.page == 0 && !small) KeyCorners.of(k.label)?.let { cc ->
                        val ink = th.text.copy(alpha = .6f)
                        if (cc.nw.isNotEmpty()) Text(KeyCorners.glyph(cc.nw) ?: KeyCorners.show(cc.nw), color = ink, fontSize = if (KeyCorners.glyph(cc.nw) != null) 11.sp else 9.sp, fontFamily = if (KeyCorners.glyph(cc.nw) != null) kbIconFont else null, maxLines = 1, modifier = Modifier.align(Alignment.TopStart).padding(top = 2.dp, start = 4.dp))
                        if (cc.ne.isNotEmpty()) Text(KeyCorners.glyph(cc.ne) ?: KeyCorners.show(cc.ne), color = ink, fontSize = if (KeyCorners.glyph(cc.ne) != null) 11.sp else 9.sp, fontFamily = if (KeyCorners.glyph(cc.ne) != null) kbIconFont else null, maxLines = 1, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp))
                        if (cc.sw.isNotEmpty()) Text(KeyCorners.glyph(cc.sw) ?: KeyCorners.show(cc.sw), color = ink, fontSize = if (KeyCorners.glyph(cc.sw) != null) 11.sp else 9.sp, fontFamily = if (KeyCorners.glyph(cc.sw) != null) kbIconFont else null, maxLines = 1, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 1.dp, start = 4.dp))
                        if (cc.se.isNotEmpty()) Text(KeyCorners.glyph(cc.se) ?: KeyCorners.show(cc.se), color = ink, fontSize = if (KeyCorners.glyph(cc.se) != null) 11.sp else 9.sp, fontFamily = if (KeyCorners.glyph(cc.se) != null) kbIconFont else null, maxLines = 1, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 1.dp, end = 4.dp))
                        if (cc.s.isNotEmpty()) Text(KeyCorners.glyph(cc.s) ?: KeyCorners.show(cc.s), color = ink, fontSize = if (KeyCorners.glyph(cc.s) != null) 11.sp else 9.sp, fontFamily = if (KeyCorners.glyph(cc.s) != null) kbIconFont else null, maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp))
                    }
                    if (!KeyboardPrefs.corners && !special && k.alts.isNotEmpty() && KeyboardPrefs.longPressSymbols && !small && k.alts.first().length == 1 && k.alts.first()[0].isDigit()) {
                        Text(k.alts.first(), color = th.text.copy(alpha = .75f), fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp))
                    }
                    // the number pad's 1, 2 and 3: a tiny icon in the lower left says what holding the key opens (clipboard, authenticator, passwords)
                    if (k.kind == KeyKind.CHAR && (st.numpad || st.forceLayout == "numpad") && (k.label == "1" || k.label == "2" || k.label == "3")) {
                        val hint = when (k.label) { "1" -> kbIcon(th, "ContentPaste"); "2" -> Icons.Rounded.Lock; else -> Icons.Rounded.Key }
                        Icon(hint, null, tint = th.text.copy(alpha = .45f), modifier = Modifier.align(Alignment.BottomStart).padding(start = 7.dp, bottom = 5.dp).size(13.dp))
                    }
                    // Ctrl, Alt or Win waiting (or held): each letter says what that chord will do or type (Win+L: lock, Alt+S: ß)
                    if (k.kind == KeyKind.CHAR && !small && k.label.length == 1 && (st.ctrl > 0 || st.alt > 0 || st.win > 0)) chordHint(st, k.label[0])?.let { h ->
                        val ic = chordIcon(st, k.label[0])
                        if (ic != null) Icon(ic, null, tint = th.accent, modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(15.dp))
                        else Text(h, color = th.accent, fontSize = if (h.length > 2) 9.sp else 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp))
                    }
                    // a small icon above the comma or the period says what its long press does
                    if ((k.kind == KeyKind.COMMA || k.kind == KeyKind.PERIOD) && !small) HoldHint(th, k.kind, st.listening)
                    Text(k.label, color = fg, fontSize = if (small) (if (th.samsung) 24.sp else (fs.value * .82f).sp) else fs, maxLines = 1)
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

/** The RGB colour effect of the whole keyboard: a soft rainbow that turns slowly behind the keys. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.RgbWash() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "rgbWash")
    val h by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween((18000 * 100 / KeyboardPrefs.rgbSpeed.coerceIn(25, 300)), easing = androidx.compose.animation.core.LinearEasing)), label = "hue")
    Box(Modifier.matchParentSize().drawBehind {
        drawRect(androidx.compose.ui.graphics.Brush.linearGradient(List(5) { Color.hsv(((h + it * 72f) % 360f + 360f) % 360f, .8f, 1f).copy(alpha = .35f) }))
    })
}

/** Unexpected Keyboard's icon font, for the functions written on the corners of the keys. */
private val kbIconFont = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(com.umutk.pulse.R.font.kb_special))

/** A picture read at no more than [max] pixels across (a camera photo at full size made the keyboard slow to open and heavy in memory). */
internal fun kbDecode(path: String, max: Int): android.graphics.Bitmap? {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, o)
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2
    return android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}

/** What is behind the space bar: a picture (dimmed) and a small figure (an emoji) walking from one end to the other. Nothing in smooth mode. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SpaceDeco() {
    if (KeyboardPrefs.calm) return
    val pic = KeyboardPrefs.spacePic
    if (pic.isNotEmpty()) {
        val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pic) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { kbDecode(pic, 600)?.asImageBitmap() }.getOrNull() }
        }
        img?.let { androidx.compose.foundation.Image(it, null, Modifier.matchParentSize().clip(RoundedCornerShape(6.dp)).graphicsLayer { alpha = .55f }, contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
    }
    val walker = KeyboardPrefs.spaceWalker
    val walkerPic = KeyboardPrefs.spaceWalkerPic
    if (walker.isNotEmpty() || walkerPic.isNotEmpty()) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "walker")
        // one round trip: out along the bar, then back; the figure is turned toward the way it goes
        val ph by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(18000, easing = androidx.compose.animation.core.LinearEasing), androidx.compose.animation.core.RepeatMode.Restart), label = "walkerX")
        val x = if (ph < .5f) ph * 2f else 2f - ph * 2f
        val right = ph < .5f
        // most emoji look to the left; a few look to the right (and a picture of the person's own is taken to look to the right); "turn round" in the settings sets it straight
        val looksRight = walkerPic.isNotEmpty() || walker in setOf("🦖", "🐌", "🚀")
        val flip = (right != looksRight) != KeyboardPrefs.spaceFlip
        val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, walkerPic) {
            value = if (walkerPic.isEmpty()) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { kbDecode(walkerPic, 96)?.asImageBitmap() }.getOrNull() }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
            val w = maxWidth
            val mod = Modifier.align(Alignment.BottomStart).graphicsLayer { translationX = (w.toPx() - 22.dp.toPx()) * x; translationY = -2.dp.toPx(); scaleX = if (flip) -1f else 1f }
            if (walkerPic.isNotEmpty()) img?.let { androidx.compose.foundation.Image(it, null, mod.size(20.dp)) }
            else Text(walker, fontSize = 15.sp, modifier = mod)
        }
    }
}

/** A picture on one key cap, dimmed so the letter stays readable. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CapPicture(path: String, radius: Int) {
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { kbDecode(path, 160)?.asImageBitmap() }.getOrNull() }
    }
    img?.let { androidx.compose.foundation.Image(it, null, Modifier.matchParentSize().clip(RoundedCornerShape(radius.dp)).graphicsLayer { alpha = .8f }, contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
}

/** The keyboard's picture behind the keys, darkened by [dim] percent (static: the first frame of a GIF). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.KbPicture(path: String, dim: Int) {
    if (path.isEmpty()) return
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { kbDecode(path, 1280)?.asImageBitmap() }.getOrNull() }
    }
    img?.let { androidx.compose.foundation.Image(it, null, Modifier.matchParentSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
    if (img != null) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim.coerceIn(0, 100) / 100f)))
}

// ---- the pop-up over a pressed key -----------------------------------------------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PressFx(th: KbTheme, st: KbState) {
    val kind = KeyboardPrefs.pressFxNow
    if (kind <= 0 || st.bursts.isEmpty()) return
    var now by remember { androidx.compose.runtime.mutableLongStateOf(System.nanoTime() / 1_000_000) }
    val dur = KeyboardEffects.pressMs(kind)
    // one frame after another while an effect is under way; the finished ones leave the list
    LaunchedEffect(Unit) {
        while (st.bursts.isNotEmpty()) {
            androidx.compose.runtime.withFrameNanos { now = it / 1_000_000 }
            st.bursts.removeAll { now - it.t0 > dur }
        }
    }
    // the keys' places are taken in the window's coordinates: they are brought into this layer's own (the same in the keyboard, moved and scaled in the settings' preview)
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    Canvas(Modifier.matchParentSize().onGloballyPositioned { coords = it }) {
        val c = coords
        fun local(r: Rect): Rect = if (c == null || !c.isAttached) r else { val root = c.findRootCoordinates(); Rect(c.localPositionOf(root, r.topLeft), c.localPositionOf(root, r.bottomRight)) }
        with(KeyboardEffects) { st.bursts.forEach { b -> drawPress(kind, local(b.rect), ((now - b.t0) / dur).coerceIn(0f, 1f), KeyboardEffects.fxTint(KeyboardPrefs.fxColor, th.accent, b.t0.toInt()), b.t0.toInt()) } }
    }
}

@Composable
private fun Preview(th: KbTheme, st: KbState) {
    val p = st.preview ?: return
    val shape = RoundedCornerShape((th.radius + 4).dp)
    val dens = androidx.compose.ui.platform.LocalDensity.current
    val pop = remember(p.rect, p.alts.isEmpty()) { Animatable(if (KeyboardPrefs.animOn) .75f else 1f) }
    LaunchedEffect(p.rect, p.alts.isEmpty()) { pop.animateTo(1f, tween(110)) }
    if (p.alts.isEmpty()) {
        val w = with(dens) { maxOf(p.rect.width, 52.dp.toPx()) }; val h = p.rect.height * 1.15f
        val x = (p.rect.center.x - w / 2f).coerceIn(0f, (st.rootW - w).coerceAtLeast(0f)); val y = (p.rect.top - h - 2f).coerceAtLeast(0f)
        Box(Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.size(with(dens) { w.toDp() }, with(dens) { h.toDp() }).clip(shape).background(th.popup, shape), contentAlignment = Alignment.Center) {
            Text(p.label, color = th.text, fontSize = 30.sp, maxLines = 1)
        }
    } else {
        // many entries (accents and element symbols) share the width of the keyboard
        // one word (what a hold or a corner swipe will do: "Clipboard", "Esc"): the bubble is as wide as the word
        val w = if (p.alts.size == 1 && p.alts[0].length > 2) with(dens) { (p.alts[0].length * 9f + 24f).coerceAtMost(220f).dp.toPx() }
            else minOf(with(dens) { POPUP_W.dp.toPx() }, st.rootW * .96f / p.alts.size)
        val left = popupLeft(p.rect, p.alts.size, st.rootW, w); val y = (p.rect.top - p.rect.height * 1.15f - 2f).coerceAtLeast(0f)
        Row(Modifier.offset { IntOffset(left.roundToInt(), y.roundToInt()) }.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.height(with(dens) { (p.rect.height * 1.15f).toDp() }).clip(shape).background(th.popup, shape)) {
            p.alts.forEachIndexed { i, s ->
                Box(Modifier.width(with(dens) { w.toDp() }).fillMaxSize().background(if (i == p.sel) th.accent else Color.Transparent), contentAlignment = Alignment.Center) {
                    Text(s, color = if (i == p.sel) th.onAccent else th.text, fontSize = if (s.length > 2) 15.sp else if (w >= with(dens) { 40.dp.toPx() } || s.length == 1 && w >= with(dens) { 26.dp.toPx() }) 22.sp else 15.sp, maxLines = 1)
                }
            }
        }
    }
}

// ---- panels ----------------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun EmojiPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp, split: Boolean = false) {
    val ctx = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { st.artKeys = false } }
    // every emoji of Unicode and the kaomoji, read once from the app
    val all by androidx.compose.runtime.produceState(emptyList<EmojiItem>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { EmojiData.load(ctx) } }
    // 0 recent, 1..9 the nine groups, 10 kaomoji
    // the recent list shown stays as it was when the panel opened, so the emojis do not jump while tapping; it updates on the next open
    val shownRecents = remember { st.recents }
    // the category (the bar above the keyboard holds the icons) lives in the state, so the bar and the pages agree
    val tab = st.emojiTab
    val q = st.emojiQuery
    val fams by androidx.compose.runtime.produceState(emptyList<KeyboardSymbols.Family>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KeyboardSymbols.load(ctx) } }
    var g by remember { mutableStateOf(0) }
    var sub by remember { mutableStateOf(0) }
    // the emoji whose five skin tones are on offer (long press); the strip takes the place of the search box
    var toneBase by remember { mutableStateOf<String?>(null) }
    val dens = androidx.compose.ui.platform.LocalDensity.current
    // the same height as the keys, so the keyboard does not shrink; the search needs the keys under it and is taller
    if (st.artKeys && !split && q == null && tab == 11) { ArtKeysMode(th, st, act, keyH); return }
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
                Box(Modifier.size(40.dp).clickable { st.emojiQuery = null; st.gifSearch = false }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "ArrowBack"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
                Row(Modifier.weight(1f).height(32.dp).padding(end = 8.dp).clip(RoundedCornerShape(16.dp)).background(th.special).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(kbIcon(th, "Search"), null, tint = th.sub, modifier = Modifier.size(18.dp))
                    Text(if (q.isEmpty()) tr("Search emoji, kaomoji, symbols", "Emoji, kaomoji, sembol ara") else q, color = if (q.isEmpty()) th.sub else th.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
                // GIF: the same box looks for GIFs (Tenor; a key in the settings is optional)
                Box(Modifier.padding(end = 8.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (st.gifSearch) th.accent else th.special).clickable { st.gifSearch = !st.gifSearch }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("GIF", color = if (st.gifSearch) th.onAccent else th.text, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            } else if (toneBase != null) {
                val base = toneBase!!
                Row(Modifier.weight(1f).height(36.dp).padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).background(th.special), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    (listOf(-1) + (0..4)).forEach { i ->
                        val e = if (i < 0) base else EmojiTone.toned(base, i)
                        Box(Modifier.size(38.dp).clickable { EmojiTone.choose(ctx, base, i); act.emoji(e); toneBase = null }, contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp, color = th.text) }
                    }
                    Box(Modifier.size(34.dp).clickable { toneBase = null }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Close"), null, tint = th.sub, modifier = Modifier.size(18.dp)) }
                }
            } else {
                // the search box on top, like the phone's own emoji page; the category bar sits at the bottom
                Row(Modifier.weight(1f).height(36.dp).padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).background(th.special).clickable { st.gifSearch = st.emojiTab == 12; st.emojiQuery = "" }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(kbIcon(th, "Search"), null, tint = th.sub, modifier = Modifier.size(20.dp))
                    Text(if (st.emojiTab == 12) tr("Search GIFs", "GIF ara") else tr("Search emojis", "Emoji ara"), color = th.sub, fontSize = 16.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (q != null && st.gifSearch) {
            GifStrip(th, act, q)
            if (!split) KeysPanel(th, st, act, keyH)
        } else if (q != null) {
            val found = remember(q, all, fams) { EmojiData.search(all, q) + KeyboardSymbols.search(fams, q).map { EmojiItem(it.ch, 11, it.name) } }
            LazyVerticalGrid(GridCells.Adaptive(44.dp), Modifier.fillMaxWidth().height(48.dp)) {
                items(found) { it2 -> EmojiCell(it2, th, act) }
            }
            if (!split) KeysPanel(th, st, act, keyH)
        } else {
            val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = EMOJI_ORDER.indexOf(tab).coerceAtLeast(0)) { EMOJI_ORDER.size }
            // the tab follows the page only once it has settled: following it mid-swipe restarted the scroll and left the pages half way
            LaunchedEffect(tab) { val p = EMOJI_ORDER.indexOf(tab); if (p >= 0 && pager.settledPage != p) pager.animateScrollToPage(p) }
            LaunchedEffect(pager.settledPage) { st.emojiTab = EMOJI_ORDER[pager.settledPage] }
            androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), userScrollEnabled = EMOJI_ORDER[pager.settledPage] != 11) { pg ->
                val page = EMOJI_ORDER[pg]
                if (page == 11) { SymbolPage(th, act, st, fams, g, sub) { sub = it }; return@HorizontalPager }
                if (page == 12) { GifGrid(th, act); return@HorizontalPager }
                val list = if (page == 0) shownRecents.map { e -> all.firstOrNull { it.e == e } ?: EmojiItem(e, if (e.any { it.isLetterOrDigit() }) 10 else 0, "") }
                    else if (page == 10) all.filter { it.group >= 10 } else all.filter { it.group == page - 1 }
                LazyVerticalGrid(GridCells.Adaptive(if (page == 10) 92.dp else 44.dp), Modifier.fillMaxSize()) {
                    items(list) { it2 -> EmojiCell(it2, th, act, tones = page != 0 && page != 10, onTones = { e -> toneBase = e }) }
                }
            }
            // the bottom row sits a notch higher (the thumb reaches it), with a space bar between "abc" and the delete key
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).height(keyH * .8f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(56.dp).fillMaxHeight().clickable { act.page(0) }, contentAlignment = Alignment.Center) { Text("abc", color = th.text, fontSize = 17.sp) }
                // the space bar is there but unseen until it is pressed; the delete key stands higher, its foot level with the top of the space bar
                val spaceSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val spaceDown by spaceSrc.collectIsPressedAsState()
                Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 5.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .background(if (spaceDown) th.special else Color.Transparent).clickable(spaceSrc, null) { act.space() })
                Box(Modifier.width(56.dp).fillMaxHeight().offset(y = -(keyH * .4f)).clickable { act.backspace() }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Backspace"), null, tint = th.text, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

private fun familyIcon(id: String): ImageVector = when (id) {
    "Patterns" -> Icons.Rounded.AutoFixHigh; "Flowers" -> Icons.Rounded.LocalFlorist; "Hearts" -> Icons.Rounded.Favorite; "Stars" -> Icons.Rounded.AutoAwesome
    "Cute" -> Icons.Rounded.EmojiEmotions; "Moon and sky" -> Icons.Rounded.NightsStay; "Lines" -> Icons.Rounded.HorizontalRule; "Brackets" -> Icons.Rounded.DataArray
    "Arrows" -> Icons.Rounded.NorthEast; "Shapes" -> Icons.Rounded.Category; "Music" -> Icons.Rounded.MusicNote; "Symbols" -> Icons.Rounded.EmojiSymbols
    "Numbers" -> Icons.Rounded.Pin; "Punctuation" -> Icons.Rounded.FormatQuote; "Currency" -> Icons.Rounded.AttachMoney; "Math" -> Icons.Rounded.Functions
    "Cuneiform" -> Icons.Rounded.Edit; "Hieroglyphs" -> Icons.Rounded.Visibility; "Special" -> Icons.Rounded.Stars
    else -> Icons.Rounded.Apps
}

/**
 * The "Text and art" page: a box for your own text, with every style of it below (a tap copies one), the picture-to-ASCII
 * tool, and the ASCII pictures, each drawn whole.
 */
@Composable
private fun TextAndArtPage(entries: List<Sign>, th: KbTheme, st: KbState) {
    val ctx = LocalContext.current
    // the keys of this keyboard write the text (the app's field is not touched), so the box only shows it
    val typed = st.artIn
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    val styles = remember(typed) { FancyText.all(typed.ifBlank { "Hi test" }) }
    // the last thing copied is named for a moment, so a tap is seen
    var copied by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(copied) { if (copied != null) { delay(1200); copied = null } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (typed.isEmpty()) tr("Type your text", "Metninizi yazın") else typed + "▏",
            color = if (typed.isEmpty()) th.sub else th.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(th.special)
                .clickable(enabled = narrow) { st.artKeys = true }.padding(10.dp),
        )
        Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.Center) {
            if (copied != null) Text(tr("Copied", "Kopyalandı"), color = th.sub, fontSize = 12.sp)
        }
        styles.forEach { (name, s) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(th.special).clickable { copyArt(ctx, s); copied = s }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s, color = th.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
                Text(name, color = th.sub, fontSize = 10.sp, maxLines = 1)
            }
        }
        // the picture tool is its own screen: it copies the result, and the text box keeps what you typed
        Text(
            tr("Picture to ASCII", "Resimden ASCII"), color = th.text, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, th.sub, RoundedCornerShape(10.dp))
                .clickable { ctx.startActivity(android.content.Intent(ctx, com.umutk.pulse.extras.keyboard.AsciiMakerActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .padding(10.dp),
        )
        // the ASCII pictures: two to a line, each drawn whole, with no box around it
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val half = (maxWidth - 6.dp) / 2
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.filter { it.name.startsWith("ascii art") }.forEach { sg ->
                    val art = sg.ch.replace("⏎", "\n")
                    AsciiArtCell(art, th, Modifier.width(half)) { copyArt(ctx, art); copied = art }
                }
            }
        }
    }
}

/** The text-and-art page with the keys: the box on top, the styles of the text as one line to scroll, and the keys, which write into the box. */
@Composable
private fun ArtKeysMode(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { st.artOn = true; onDispose { st.artOn = false } }
    val typed = st.artIn
    val styles = remember(typed) { FancyText.all(typed.ifBlank { "Hi test" }) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { st.artKeys = false }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "ArrowBack"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
            Text(
                if (typed.isEmpty()) tr("Type your text", "Metninizi yazın") else typed + "▏",
                color = if (typed.isEmpty()) th.sub else th.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp).clip(RoundedCornerShape(10.dp)).background(th.special).padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            styles.forEach { (_, s) ->
                Text(s, color = th.text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(th.special).clickable { copyArt(ctx, s) }.padding(horizontal = 10.dp, vertical = 7.dp))
            }
        }
        KeysPanel(th, st, act, keyH)
    }
}

/** Puts [s] on the clipboard, so it can be pasted into any app. */
private fun copyArt(ctx: android.content.Context, s: String) {
    runCatching { (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Pulse", s)) }
}

/**
 * One ASCII picture, whole: the monospace text is measured and set to the largest size that fits the cell's width, so the
 * picture keeps its shape and is never cut off. A tap copies the picture.
 */
@Composable
private fun AsciiArtCell(text: String, th: KbTheme, modifier: Modifier, onCopy: () -> Unit) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val base = 12f
    val lines = text.count { it == '\n' } + 1
    BoxWithConstraints(modifier.clickable { onCopy() }.padding(2.dp)) {
        val style = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = base.sp, lineHeight = (base * 1.1f).sp, color = th.text)
        val widthPx = measurer.measure(text, style).size.width.toFloat()
        val maxPx = with(density) { maxWidth.toPx() }
        val size = if (widthPx > 0f) (base * maxPx / widthPx * .96f).coerceIn(4f, 22f) else base
        Text(
            text,
            style = style.copy(fontSize = size.sp, lineHeight = (size * 1.1f).sp),
            softWrap = false,
            maxLines = lines,
            overflow = TextOverflow.Clip,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The symbol tab: a row of icons for the families, a row of sub-categories when the family has some, and the signs; the search above finds a sign by its Unicode name. */
@Composable
private fun SymbolPage(th: KbTheme, act: KbActions, st: KbState, fams: List<KeyboardSymbols.Family>, g: Int, sub: Int, onSub: (Int) -> Unit) {
    val fam = fams.getOrNull(g)
    val subsAll = fam?.subs.orEmpty()
    // a swipe sideways moves between the sub-categories of this family only (it does not leave for the neighbouring top category)
    Column(Modifier.fillMaxSize().pointerInput(g, sub, subsAll.size) {
        var dx = 0f
        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragCancel = { dx = 0f }, onDragEnd = {
            if (kotlin.math.abs(dx) > 90f) onSub((sub + if (dx < 0) 1 else -1).coerceIn(0, (subsAll.size - 1).coerceAtLeast(0)))
            dx = 0f
        }) { _, d -> dx += d }
    }) {
        val subs = fam?.subs.orEmpty()
        if (subs.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            subs.forEachIndexed { i, (name, _) ->
                Text(KeyboardSymbols.subName(name), color = if (i == sub) th.text else th.sub, fontSize = 13.sp, fontWeight = if (i == sub) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (i == sub) th.special else Color.Transparent).clickable { onSub(i) }.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        // the presets vary in length: each one takes just its own width, so two or three share a line
        val wide = fam?.id == "Patterns"
        val textArt = wide && subs.getOrNull(sub)?.first == "Text and art"
        // the text and art page: your own text in every style, the picture-to-ASCII tool, then the ASCII pictures
        // the keys type into the text box only while this page is the one on screen (a neighbouring page is composed too)
        androidx.compose.runtime.DisposableEffect(textArt, st.emojiTab, st.panel) {
            st.artOn = textArt && st.emojiTab == 11 && st.panel == "emoji"
            onDispose { st.artOn = false }
        }
        if (textArt) TextAndArtPage(subs.getOrNull(sub)?.second.orEmpty(), th, st)
        else if (wide) androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // a long preset is shrunk to the width of the keyboard, so every one shows whole
            val room = maxWidth - 12.dp
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                subs.getOrNull(sub)?.second.orEmpty().forEach { sg ->
                    val n = sg.ch.length
                    val fs = when { n > 44 -> 9.sp; n > 36 -> 10.sp; n > 28 -> 11.sp; n > 22 -> 12.sp; else -> 14.sp }
                    Text(sg.ch, fontSize = fs, color = th.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.widthIn(max = room).clip(RoundedCornerShape(10.dp)).background(th.special).clickable { act.emoji(sg.ch) }.padding(horizontal = 10.dp, vertical = 8.dp))
                }
            }
        } else LazyVerticalGrid(GridCells.Adaptive(48.dp), Modifier.fillMaxSize()) {
            items(subs.getOrNull(sub)?.second.orEmpty()) { sg ->
                Box(Modifier.height(44.dp).clickable { act.emoji(sg.ch) }, contentAlignment = Alignment.Center) { Text(sg.ch, fontSize = 22.sp, color = th.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(it: EmojiItem, th: KbTheme, act: KbActions, tones: Boolean = false, onTones: (String) -> Unit = {}) {
    val kao = it.group == 10
    val ctx = LocalContext.current
    // the tone chosen for this emoji before is the one shown (not on the recent page, which keeps what was sent)
    val shown = if (tones && !kao) EmojiTone.shown(ctx, it.e) else it.e
    val canTone = tones && !kao && EmojiTone.supports(it.e)
    Box(Modifier.height(44.dp).combinedClickable(onLongClick = if (canTone) ({ onTones(it.e) }) else null, onClick = { act.emoji(shown) }), contentAlignment = Alignment.Center) {
        Text(shown, fontSize = if (kao) 15.sp else 26.sp, color = th.text, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

private class MenuItem(val id: String, val icon: ImageVector, val label: String, val on: Boolean = false)

@Composable
private fun MenuPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val ids = KeyboardPrefs.menuItems.split(',').filter { id -> ExtraKeys.menu.any { it.first == id } }
    val items = ids.map { id -> MenuItem(id, toolIcon(th, id), ExtraKeys.menu.first { it.first == id }.second, toolOn(id, st)) } + MenuItem("back", kbIcon(th, "Keyboard"), "Keys")
    Column(Modifier.fillMaxWidth().height(panelHeight(st, keyH)).background(th.menu)) {
    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().weight(1f)) {
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
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ClipPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val ctxForClip = LocalContext.current
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val selecting = sel.isNotEmpty()
    val now = System.currentTimeMillis()
    // a long press on an item: pin it, make a shortcut of it, or pick it
    var menuFor by remember { mutableStateOf<ClipItem?>(null) }
    val naming = st.clipName
    if (naming != null) {
        val target = st.clipItems.firstOrNull { it.id == st.clipNameFor }
        Column(Modifier.fillMaxWidth().background(th.menu)) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Shortcut name", "Kısayol adı"), color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(tr("Save", "Kaydet"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { act.clipShortcutSave() }.padding(8.dp))
                Text(tr("Cancel", "Vazgeç"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { act.clipShortcutCancel() }.padding(8.dp))
            }
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).background(th.special).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (naming.isEmpty()) "\u258F" + tr("e.g. address", "örn. adres") else naming + "\u258F", color = if (naming.isEmpty()) th.sub else th.text, fontSize = 16.sp, maxLines = 1)
            }
            Text(target?.text.orEmpty().replace('\n', ' '), color = th.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            KeysPanel(th, st, act, keyH)
        }
        return
    }
    Column(Modifier.fillMaxWidth().then(if (st.clipName != null) Modifier else Modifier.height(panelHeight(st, keyH))).background(th.menu)) {
        // the head: how many are kept, and either the clear / select-all buttons or the delete of the picked ones
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selecting) "${sel.size} " + tr("selected", "seçili") else tr("Clipboard", "Pano") + " · ${st.clipItems.size}", color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (selecting) {
                Text(tr("All", "Tümü"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { sel = st.clipItems.map { it.id }.toSet() }.padding(8.dp))
                Text(tr("Delete", "Sil"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { act.clipDelete(sel); sel = emptySet() }.padding(8.dp))
                Text(tr("Cancel", "Vazgeç"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { sel = emptySet() }.padding(8.dp))
            } else {
                // the view of the items: list, two columns, or columns sized by the pictures
                Text(when (KeyboardPrefs.clipLayout) { "grid" -> "▦"; "photos" -> "▥"; else -> "☰" }, color = th.text, fontSize = 18.sp,
                    modifier = Modifier.clickable { KeyboardPrefs.clipLayout = when (KeyboardPrefs.clipLayout) { "list" -> "grid"; "grid" -> "photos"; else -> "list" }; KeyboardPrefs.save(ctxForClip) }.padding(8.dp))
                if (st.clipItems.any { !it.pinned }) Text(tr("Clear unpinned", "Sabitlenmeyenleri temizle"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { act.clipClear() }.padding(8.dp))
            }
        }
        menuFor?.let { m ->
            Row(Modifier.fillMaxWidth().height(40.dp).background(th.special).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // the row's actions: the keyboard's own icon family (line icons in the Material and AMOLED themes), in a 42 dp touch target each
                Box(Modifier.clickable { act.clipPin(m.id); menuFor = null }.size(42.dp), contentAlignment = Alignment.Center) { Icon(kbIcon(th, "PushPin"), null, tint = if (m.pinned) th.accent else th.text, modifier = Modifier.size(22.dp).rotate(45f)) }
                if (m.image == null) Box(Modifier.clickable { act.clipShortcut(m.id); menuFor = null }.size(42.dp), contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Bolt"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
                Box(Modifier.clickable { sel = sel + m.id; menuFor = null }.size(42.dp), contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Check"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
                // delete this one item (the same as the swipe, but with a button)
                Box(Modifier.clickable { act.clipDelete(setOf(m.id)); menuFor = null }.size(42.dp), contentAlignment = Alignment.Center) { Icon(kbIcon(th, "Delete"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text("✕", color = th.sub, fontSize = 16.sp, modifier = Modifier.clickable { menuFor = null }.padding(10.dp))
            }
        }
        if (st.clipItems.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(tr("Copied texts and pictures appear here", "Kopyalanan metin ve resimler burada görünür"), color = th.sub, fontSize = 14.sp) }
        else if (KeyboardPrefs.clipLayout != "list") {
            val tile: @Composable (ClipItem) -> Unit = { it2 ->
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (it2.id in sel) th.special else th.key)
                    .combinedClickable(onClick = { if (selecting) sel = if (it2.id in sel) sel - it2.id else sel + it2.id else act.pasteClip(it2) }, onLongClick = { menuFor = it2 })) {
                    if (it2.image != null) ClipThumb(it2.image, Modifier.fillMaxWidth(), full = true)
                    else Text(it2.text.take(400), color = th.text, fontSize = 14.sp, maxLines = if (KeyboardPrefs.clipLayout == "photos") 8 else 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
                    if (it2.pinned) Icon(kbIcon(th, "PushPin"), null, tint = th.accent, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp).rotate(45f))
                }
            }
            if (KeyboardPrefs.clipLayout == "photos") androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid(
                androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
                verticalItemSpacing = 6.dp, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(st.clipItems, key = { it.id }) { tile(it) }
            } else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(st.clipItems, key = { it.id }) { tile(it) }
            }
        }
        else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(st.clipItems, key = { it.id }) { it2 ->
                // roomy rows; a swipe to the right deletes the item, a swipe to the left makes a shortcut of it
                var dx by remember(it2.id) { mutableFloatStateOf(0f) }
                val threshold = with(LocalDensity.current) { 90.dp.toPx() }
                Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    // what the swipe will do shows behind the row
                    if (dx > 8f) Box(Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFFD32F2F)), contentAlignment = Alignment.CenterStart) { Icon(kbIcon(th, "Close"), null, tint = Color.White, modifier = Modifier.padding(start = 16.dp).size(22.dp)) }
                    if (dx < -8f) Box(Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).background(th.accent), contentAlignment = Alignment.CenterEnd) { Icon(kbIcon(th, "TouchApp"), null, tint = Color.White, modifier = Modifier.padding(end = 16.dp).size(22.dp)) }
                    Row(Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(dx.roundToInt(), 0) }.clip(RoundedCornerShape(14.dp)).background(if (it2.id in sel) th.special else th.key)
                        .pointerInput(it2.id) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dx > threshold) act.clipDelete(setOf(it2.id)) else if (dx < -threshold && it2.image == null) act.clipShortcut(it2.id)
                                    dx = 0f
                                },
                                onDragCancel = { dx = 0f },
                            ) { change, drag -> change.consume(); dx += drag }
                        }
                        .combinedClickable(onClick = { if (selecting) sel = if (it2.id in sel) sel - it2.id else sel + it2.id else act.pasteClip(it2) }, onLongClick = { menuFor = it2 })
                        .padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clickable { act.clipPin(it2.id) }, contentAlignment = Alignment.Center) {
                            Icon(kbIcon(th, "PushPin"), null, tint = if (it2.pinned) th.accent else th.sub.copy(alpha = .5f), modifier = Modifier.size(18.dp).rotate(45f))
                        }
                        if (it2.image != null) ClipThumb(it2.image) else Text(it2.text.take(240), color = th.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (it2.image != null) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        Text(ago(now - it2.time), color = th.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                    }
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

/** Every panel keeps the height of the keys, so the keyboard does not shrink when one opens. */
@Composable
private fun panelHeight(st: KbState, keyH: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val d = LocalDensity.current
    return if (st.keysH > 0) with(d) { st.keysH.toDp() } else keyH * 4f
}

/** A kept picture, shown small. */
@Composable
private fun ClipThumb(path: String, modifier: Modifier = Modifier, full: Boolean = false) {
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
    // in the grid views the picture takes the width of its tile and keeps its own shape (a tall one is tall, a wide one wide)
    if (b != null) androidx.compose.foundation.Image(b, null, if (full) modifier.fillMaxWidth() else Modifier.height(56.dp).clip(RoundedCornerShape(6.dp)), contentScale = if (full) androidx.compose.ui.layout.ContentScale.FillWidth else androidx.compose.ui.layout.ContentScale.Fit)
    else Box((if (full) modifier.height(90.dp) else Modifier.size(56.dp)).clip(RoundedCornerShape(6.dp)).background(Color.Gray.copy(alpha = .3f)))
}

/** Resizing on the keyboard itself: pull the top edge for the height, the bottom edge for the space below, the side edges for the space at the sides. */
@Composable
private fun ResizePanel(th: KbTheme, st: KbState, keyH: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val dens = LocalDensity.current.density
    val land = kbLand()
    var h by remember(land) { mutableFloatStateOf(KeyboardPrefs.heightAt(land).toFloat()) }
    var b by remember { mutableFloatStateOf(KeyboardPrefs.bottomPad.toFloat()) }
    var sd by remember { mutableFloatStateOf(KeyboardPrefs.sidePad.toFloat()) }
    val bar = Modifier.clip(RoundedCornerShape(3.dp)).background(th.accent)
    Box(Modifier.fillMaxWidth().border(2.dp, th.accent, RoundedCornerShape(6.dp))) {
        Column { KeysPanel(th, st, NoKbActions, keyH) }
        // the handles sit inside the keys' own size (the window around the keyboard is taller: nothing here may grow to it)
        Box(Modifier.matchParentSize()) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(30.dp).pointerInput(land) { detectVerticalDragGestures { _, dy -> h = (h - dy / dens / 2.2f).coerceIn(70f, 250f); if (land) KeyboardPrefs.landHeightPct = h.roundToInt() else KeyboardPrefs.heightPct = h.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(56.dp).height(5.dp)) }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(30.dp).pointerInput(Unit) { detectVerticalDragGestures { _, dy -> b = (b - dy / dens).coerceIn(0f, 60f); KeyboardPrefs.bottomPad = b.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(56.dp).height(5.dp)) }
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(28.dp).pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> sd = (sd + dx / dens).coerceIn(0f, 400f); KeyboardPrefs.sidePad = sd.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(5.dp).height(56.dp)) }
        Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp).pointerInput(Unit) { detectHorizontalDragGestures { _, dx -> sd = (sd - dx / dens).coerceIn(0f, 400f); KeyboardPrefs.sidePad = sd.roundToInt() } }, contentAlignment = Alignment.Center) { Box(bar.width(5.dp).height(56.dp)) }
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

/** Quick auth: the accounts of Pulse Authenticator with their codes; a tap types the code. */
@Composable
private fun AuthPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    // as tall as the keys: four rows of keys
    Column(Modifier.fillMaxWidth().height(keyH * 4f).background(th.bg).padding(8.dp)) {
        // a second clock: the codes are checked each second, and each account shows how long its code has left
        val now by androidx.compose.runtime.produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); act.authTick(); delay(1000) } }
        if (st.authCodes.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(tr("No accounts yet: add them in Pulse Authenticator", "Henüz hesap yok: Pulse Kimlik doğrulayıcı'da ekle"), color = th.sub, fontSize = 13.sp)
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(st.authCodes) { i, (name, code) ->
                val period = st.authPeriods.getOrElse(i) { 30 }.coerceAtLeast(1)
                val left = period - ((now / 1000L) % period).toInt()
                // each account is as tall as its text (twice the text of before), so the panel is not padded with empty space
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(th.key).clickable { act.authPick(code) }
                    .padding(vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = th.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(code, color = th.accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // the bar under the code empties as the code runs out
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(4.dp).background(th.sub.copy(alpha = .2f))) {
                        Box(Modifier.fillMaxWidth(left / period.toFloat()).height(4.dp).background(th.accent))
                    }
                }
            }
        }
    }
}

/** The password manager: a search box (the keys type into it) over the logins of the vault; a tap fills the field in front, a hold fills the other part (user name or password). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun VaultPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().background(th.menu)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Key, null, tint = th.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Passwords", "Şifreler") + " · ${st.vaultRows.size}", color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(tr("Close", "Kapat"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { st.panel = "keys" }.padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp)).background(th.special).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (st.vaultQuery.isEmpty()) "\u258F" + tr("Search", "Ara") else st.vaultQuery + "\u258F", color = if (st.vaultQuery.isEmpty()) th.sub else th.text, fontSize = 16.sp, maxLines = 1)
        }
        if (st.vaultRows.isEmpty()) Box(Modifier.fillMaxWidth().height(keyH * 1.2f), contentAlignment = Alignment.Center) {
            Text(tr("No login found: add them in the Pulse vault", "Giriş bulunamadı: Pulse kasasına ekle"), color = th.sub, fontSize = 13.sp)
        } else LazyColumn(Modifier.fillMaxWidth().height(keyH * 1.5f)) {
            itemsIndexed(st.vaultRows) { i, (name, user, fits) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)).background(th.key)
                    .combinedClickable(onClick = { act.vaultPick(i, false) }, onLongClick = { act.longFeedback(); act.vaultPick(i, true) }).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = th.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (user.isNotEmpty()) Text(user, color = th.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (fits) Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(th.accent))
                }
            }
        }
        KeysPanel(th, st, act, keyH)
    }
}

/** Your own stickers: the pictures in Download/Pulse/Stickers (and Pictures/Stickers), a tap sends one as a picture. */
@Composable
private fun StickerPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val files by androidx.compose.runtime.produceState(emptyList<java.io.File>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KbStickers.files(ctx) } }
    Column(Modifier.fillMaxWidth().background(th.menu)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.StickyNote2, null, tint = th.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Stickers", "Çıkartmalar") + " · ${files.size}", color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(tr("Close", "Kapat"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { st.panel = "keys" }.padding(8.dp))
        }
        if (files.isEmpty()) Box(Modifier.fillMaxWidth().height(keyH * 1.9f).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(tr("Put pictures (png, webp, gif) in Download/Pulse/Stickers", "Resimleri (png, webp, gif) Download/Pulse/Stickers klasörüne koy"), color = th.sub, fontSize = 13.sp)
        } else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(64.dp), Modifier.fillMaxWidth().height(keyH * 1.9f)) {
            items(files.size) { i ->
                val f = files[i]
                val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, f.path) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KbStickers.thumb(f) } }
                Box(Modifier.padding(3.dp).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(th.key).clickable { act.sticker(f) }, contentAlignment = Alignment.Center) {
                    bmp?.let { Image(it, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit) }
                }
            }
        }
        KeysPanel(th, st, act, keyH)
    }
}

/** The Dictionary panel: the meanings of the word (Wiktionary) and its Wikipedia summary; Insert puts them in the text. */
@Composable
private fun DictPanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().background(th.menu)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MenuBook, null, tint = th.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(st.dictQuery.ifEmpty { tr("Dictionary", "Sözlük") }, color = th.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (st.dictText.isNotEmpty()) Text(tr("Insert", "Ekle"), color = th.accent, fontSize = 14.sp, modifier = Modifier.clickable { act.menu("dictInsert") }.padding(8.dp))
            Text(tr("Close", "Kapat"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { st.panel = "keys" }.padding(8.dp))
        }
        Box(Modifier.fillMaxWidth().height(keyH * 1.9f).padding(horizontal = 14.dp)) {
            if (st.dictBusy) Text("…", color = th.sub, fontSize = 16.sp)
            else if (st.dictText.isEmpty()) Text(tr("Select a word, or put the cursor after it, then open Dictionary", "Bir kelime seç ya da imleci sonuna koy, sonra Sözlük'ü aç"), color = th.sub, fontSize = 13.sp)
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Text(st.dictText, color = th.text, fontSize = 14.sp, lineHeight = 19.sp) }
        }
        KeysPanel(th, st, act, keyH)
    }
}

/** Speech typing: the panel covers the keys while the phone listens; the words come in as they are recognised, a tap stops it. */
@Composable
private fun VoicePanel(th: KbTheme, st: KbState, act: KbActions, keyH: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().height(keyH).background(th.bg).clickable { act.voice() }.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Mic, null, tint = if (st.listening) th.accent else th.sub, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(st.voiceText.ifEmpty { tr("Listening… tap to stop", "Dinliyor… durdurmak için dokun") }, color = th.text, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

/** The e-mail suggestions on the bar: the addresses used most, first. One or two sit in the middle; more scroll to the right. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun RowScope.EmailChips(st: KbState, th: KbTheme, act: KbActions) {
    val shown = st.slots.withIndex().filter { it.value.isNotEmpty() }
    val chip: @Composable (Int, String) -> Unit = { i, s ->
        // held like a word: it buzzes and turns into the block mark; a tap on the mark never offers it again (Settings > Blocked suggestions brings it back)
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(th.text.copy(alpha = .08f)).combinedClickable(onClick = { if (st.banAsk == i) act.banSlot(i) else act.slot(i) }, onLongClick = { act.longFeedback(); act.slotLong(i) }).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            if (st.banAsk == i) Icon(Icons.Rounded.Block, tr("Never suggest", "Bir daha önerme"), tint = th.accent, modifier = Modifier.size(22.dp))
            else Text(s, color = th.text, fontSize = 16.sp, maxLines = 1, softWrap = false)
        }
    }
    // one or two: they share the whole bar, so nothing is left empty and a long address keeps its end in view
    if (shown.size <= 2) Row(Modifier.weight(1f).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { (i, s) ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(th.text.copy(alpha = .08f)).combinedClickable(onClick = { if (st.banAsk == i) act.banSlot(i) else act.slot(i) }, onLongClick = { act.longFeedback(); act.slotLong(i) }).padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                if (st.banAsk == i) Icon(Icons.Rounded.Block, tr("Never suggest", "Bir daha önerme"), tint = th.accent, modifier = Modifier.size(22.dp))
                else Text(s, color = th.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else Row(Modifier.weight(1f).fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { (i, s) -> chip(i, s) }
    }
}

/** The pin beside the item waiting in the right slot of the bar. */
@Composable
private fun QuickPin(st: KbState, th: KbTheme, act: KbActions) {
    Box(Modifier.size(44.dp).clickable { act.quickPin() }, contentAlignment = Alignment.Center) {
        Icon(kbIcon(th, "PushPin"), null, tint = if (st.quickPinned) th.accent else th.sub.copy(alpha = .7f), modifier = Modifier.size(16.dp))
    }
}


/** The GIF page of the emoji panel: what is trending, three to a row; a tap sends one. The search box above looks for others. */
@Composable
private fun GifGrid(th: KbTheme, act: KbActions) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var gifs by remember { mutableStateOf(emptyList<KbGif.Gif>()) }
    var busy by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        gifs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { KbGif.search("", KeyboardPrefs.gifKey, if (KeyboardPrefs.langList().first() == "tr") "tr_TR" else "en_US") }.getOrDefault(emptyList()) }
        busy = false
    }
    val loader = remember { KbGif.loader(ctx) }
    if (gifs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (busy) "…" else tr("No GIFs (is there a connection?)", "GIF yok (bağlantı var mı?)"), color = th.sub, fontSize = 14.sp) }
    else LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(gifs.size) { i ->
            val g = gifs[i]
            coil.compose.AsyncImage(g.preview, null, loader, Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(th.special).clickable {
                scope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KbGif.fetch(ctx, g) } }.getOrNull()?.let { act.gif(it) } }
            }, contentScale = ContentScale.Crop)
        }
    }
}

/** A row of GIFs for the search (trending while it is empty); a tap sends one. */
@Composable
private fun GifStrip(th: KbTheme, act: KbActions, q: String) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var gifs by remember { mutableStateOf(emptyList<KbGif.Gif>()) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(q) {
        delay(350)
        busy = true
        gifs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { KbGif.search(q, KeyboardPrefs.gifKey, if (KeyboardPrefs.langList().first() == "tr") "tr_TR" else "en_US") }.getOrDefault(emptyList()) }
        busy = false
    }
    val loader = remember { KbGif.loader(ctx) }
    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.Center) {
        if (gifs.isEmpty()) Text(if (busy) "…" else tr("No GIFs", "GIF yok"), color = th.sub, fontSize = 14.sp)
        else androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 4.dp)) {
            items(gifs.size) { i ->
                val g = gifs[i]
                coil.compose.AsyncImage(g.preview, null, loader, Modifier.fillMaxHeight().width((102f * g.w / g.h.coerceAtLeast(1)).coerceIn(60f, 200f).dp).clip(RoundedCornerShape(8.dp)).background(th.special).clickable {
                    scope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { KbGif.fetch(ctx, g) } }.getOrNull()?.let { act.gif(it) } }
                }, contentScale = ContentScale.Crop)
            }
        }
    }
}

/** The emoji board's own look (Settings > Theme): another of the keyboard's themes, or the keyboard's own. */
@Composable
private fun emojiTh(th: KbTheme): KbTheme {
    val id = KeyboardPrefs.emojiTheme
    if (id == "same" || id == th.id) return th
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    return remember(id, dark, KeyboardPrefs.keyRadius, KeyboardPrefs.keyIcons) { KeyboardThemes.of(id, dark).tuned() }
}


/** The preview of the copied item held in the bar's right slot: the whole text (scrolls), the picture large, a video with a small playing animation. */
@Composable
private fun SlotPreview(st: KeyboardState, th: KeyboardTheme) {
    val close = { st.previewText = null; st.previewShot = false }
    androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter, offset = androidx.compose.ui.unit.IntOffset(0, -150), onDismissRequest = close, properties = androidx.compose.ui.window.PopupProperties(focusable = false, clippingEnabled = false)) {
        Box(Modifier.padding(8.dp).widthIn(max = 340.dp).clip(RoundedCornerShape(16.dp)).background(th.bg).border(1.dp, th.text.copy(alpha = .18f), RoundedCornerShape(16.dp)).clickable { close() }.padding(10.dp), contentAlignment = Alignment.Center) {
            val txt = st.previewText
            if (txt != null) Text(txt, color = th.text, fontSize = 14.sp, maxLines = 14, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
            else st.shotThumb?.let { bmp ->
                Box(contentAlignment = Alignment.Center) {
                    Image(bmp, null, Modifier.heightIn(max = 260.dp).widthIn(max = 320.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                    if (st.shotVideo) {
                        // a video: the play disc breathes and a bar sweeps along the bottom, so it reads as moving
                        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pv")
                        val k by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.LinearEasing)), label = "pvk")
                        Canvas(Modifier.size(44.dp)) {
                            drawCircle(Color.Black.copy(alpha = .72f), radius = size.minDimension / 2f * (0.9f + 0.1f * kotlin.math.sin(k * 6.2832f)))
                            val r = size.minDimension / 2f
                            drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(r * .78f, r * .58f); lineTo(r * 1.42f, r); lineTo(r * .78f, r * 1.42f); close() }, Color.White)
                        }
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .25f))) { Box(Modifier.fillMaxWidth(k).height(3.dp).background(th.accent)) }
                    }
                }
            }
        }
    }
}
