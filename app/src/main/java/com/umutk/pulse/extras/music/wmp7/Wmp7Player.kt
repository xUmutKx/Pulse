package com.umutk.pulse.extras.music.wmp7

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.skin.Transport
import com.umutk.pulse.extras.music.skin.transportGlyph
import com.umutk.pulse.tr

/** What the player shows. [levels] are visualiser bar heights 0..1; empty draws the idle line. */
data class Wmp7Now(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val levels: List<Float>,
)

/** Callbacks. The caller maps them to MusicPlayerEngine calls. */
data class Wmp7Actions(
    val onPrev: () -> Unit,
    val onPlayPause: () -> Unit,
    val onStop: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (ms: Int) -> Unit,
    val onMode: (Wmp7Mode) -> Unit,
    val onMenu: (index: Int) -> Unit,
    val onNav: (index: Int) -> Unit,
)

/**
 * Windows Media Player 7 layout. FULL: menu bar, left media guide, visualisation, bottom transport.
 * COMPACT: small window with title, visualisation, seek and transport. [menus] and [nav] are caller labels.
 * A [nav] label starting with "#" is a collapsible group header (Now playing, Library, Playlists); the rows after
 * it belong to it. [Wmp7Actions.onNav] and [navSelected] always use indices into the full [nav] list. Headers are not selectable.
 * Under 480 dp the tree folds into a drop-down strip above the visualisation.
 */
@Composable
fun Wmp7Player(
    mode: Wmp7Mode,
    skin: Wmp7Skin,
    now: Wmp7Now,
    menus: List<String>,
    nav: List<String>,
    navSelected: Int,
    actions: Wmp7Actions,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        Wmp7Mode.FULL -> BoxWithConstraints(modifier) {
            val narrow = maxWidth < 480.dp
            var open by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize().frameBg(skin)) {
                Caption(skin)
                MenuBar(skin, menus, mode, actions)
                if (narrow) {
                    val cur = nav.getOrNull(navSelected)?.removePrefix("#").orEmpty()
                    Row(
                        Modifier.fillMaxWidth().background(skin.treeBg).clickable { open = !open }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Arrow(skin.accent, open)
                        Spacer(Modifier.width(8.dp))
                        Text(cur, color = skin.treeText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (open) Tree(skin, nav, navSelected, { open = false; actions.onNav(it) }, Modifier.fillMaxWidth().heightIn(max = 180.dp))
                    Stage(skin, now, Modifier.weight(1f).fillMaxWidth().padding(6.dp))
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Tree(skin, nav, navSelected, actions.onNav, Modifier.width(160.dp).fillMaxHeight())
                        Stage(skin, now, Modifier.weight(1f).fillMaxHeight().padding(6.dp))
                    }
                }
                Controls(skin, now, actions, Modifier.fillMaxWidth().padding(6.dp))
            }
        }
        Wmp7Mode.COMPACT -> Column(modifier.widthIn(max = 340.dp).frameBg(skin).padding(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(now.title, color = skin.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(now.artist, color = skin.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ModeButton(skin, Wmp7Mode.FULL, actions.onMode)
            }
            Viz(skin, now.levels, now.playing, Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp))
            Controls(skin, now, actions, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Caption(skin: Wmp7Skin) {
    if (skin.caption.isEmpty()) return
    Box(
        Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(skin.accent, Color(0xFFA6CAF0)))).padding(horizontal = 6.dp, vertical = 4.dp),
    ) { Text(skin.caption, color = Color.White, fontSize = 12.sp, maxLines = 1) }
}

private fun Modifier.frameBg(skin: Wmp7Skin): Modifier =
    if (skin.frame2 == skin.frame) background(skin.frame) else background(Brush.verticalGradient(listOf(skin.frame, skin.frame2)))

@Composable
private fun Stage(skin: Wmp7Skin, now: Wmp7Now, modifier: Modifier) {
    Column(modifier) {
        Viz(skin, now.levels, now.playing, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(now.title, color = skin.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now.artist, color = skin.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MenuBar(skin: Wmp7Skin, menus: List<String>, mode: Wmp7Mode, a: Wmp7Actions) {
    val tabs = skin.style == Wmp7Style.GLOSS || skin.style == Wmp7Style.ORB || skin.style == Wmp7Style.AERO
    Row(
        Modifier.fillMaxWidth().background(skin.menuBg).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        menus.forEachIndexed { i, label ->
            Text(
                label, color = skin.menuText, fontSize = 13.sp, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(if (tabs) 10.dp else 0.dp)).clickable { a.onMenu(i) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        ModeButton(skin, if (mode == Wmp7Mode.FULL) Wmp7Mode.COMPACT else Wmp7Mode.FULL, a.onMode)
    }
}

/** Icon shows the window it switches to: a big frame for full, a small one for compact. */
@Composable
private fun ModeButton(skin: Wmp7Skin, target: Wmp7Mode, onMode: (Wmp7Mode) -> Unit) {
    val label = if (target == Wmp7Mode.FULL) tr("Full mode", "Tam mod") else tr("Compact mode", "Kompakt mod")
    Box(
        Modifier.size(36.dp).clickable { onMode(target) }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val k = if (target == Wmp7Mode.COMPACT) .6f else 1f
            drawRect(skin.menuText, Offset.Zero, Size(size.width * k, size.height * k), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun Arrow(c: Color, open: Boolean) {
    Canvas(Modifier.size(10.dp)) {
        val w = size.width
        drawPath(Path().apply {
            if (open) { moveTo(0f, w * .25f); lineTo(w, w * .25f); lineTo(w / 2, w * .85f) }
            else { moveTo(w * .25f, 0f); lineTo(w * .25f, w); lineTo(w * .85f, w / 2) }
            close()
        }, c)
    }
}

@Composable
private fun Tree(skin: Wmp7Skin, items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val folded = remember { mutableStateListOf<Int>() }
    // rows hidden by a folded "#" header above them
    val rows = remember(items, folded.toList()) {
        var hide = false
        items.mapIndexedNotNull { i, l ->
            if (l.startsWith("#")) { hide = i in folded; i } else if (hide) null else i
        }
    }
    LazyColumn(modifier.background(skin.treeBg)) {
        itemsIndexed(rows) { _, i ->
            val label = items[i]
            if (label.startsWith("#")) {
                Row(
                    Modifier.fillMaxWidth().clickable { if (i in folded) folded.remove(i) else folded.add(i) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Arrow(skin.accent, i !in folded)
                    Spacer(Modifier.width(6.dp))
                    Text(label.drop(1), color = skin.treeText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                val on = i == selected
                val fg = if (on) skin.treeSelText else skin.treeText
                Row(
                    Modifier.fillMaxWidth().background(if (on) skin.treeSel else skin.treeBg)
                        .clickable { onSelect(i) }.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Canvas(Modifier.size(10.dp)) { drawRect(fg) }
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Viz(skin: Wmp7Skin, levels: List<Float>, playing: Boolean, modifier: Modifier) {
    Canvas(modifier.background(skin.vizBg)) {
        val base = size.height
        if (!playing || levels.isEmpty()) {
            drawRect(skin.vizBars[0], Offset(0f, base - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
            return@Canvas
        }
        val bw = size.width / levels.size
        val gap = bw * .25f
        levels.forEachIndexed { i, lv ->
            val l = lv.coerceIn(0f, 1f)
            val h = l * base
            val c = skin.vizBars[(l * (skin.vizBars.size - 1)).toInt()]
            val o = Offset(i * bw + gap / 2, base - h)
            val sz = Size(bw - gap, h)
            when (skin.style) {
                Wmp7Style.FLAT, Wmp7Style.CLASSIC -> drawRect(c, o, sz)
                Wmp7Style.FLAT_THIN -> drawRoundRect(c, Offset(o.x + sz.width * .3f, o.y), Size(sz.width * .4f, h), CornerRadius(2.dp.toPx()))
                else -> { // glass bar with a bright cap
                    drawRect(Brush.verticalGradient(listOf(c, skin.vizBars[0]), startY = o.y, endY = base), o, sz)
                    drawRect(Color.White.copy(alpha = .8f), o, Size(sz.width, 2.dp.toPx().coerceAtMost(h)))
                }
            }
        }
    }
}

@Composable
private fun Controls(skin: Wmp7Skin, now: Wmp7Now, a: Wmp7Actions, modifier: Modifier) {
    Column(if (skin.bar.isSpecified) modifier.background(skin.bar) else modifier) {
        Seek(skin, now.positionMs, now.durationMs, a.onSeek, Modifier.fillMaxWidth().height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${wmpTime(now.positionMs)} / ${wmpTime(now.durationMs)}",
                color = skin.barText, fontSize = 12.sp, maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            TransportRow(skin, now.playing, a)
        }
    }
}

@Composable
private fun TransportRow(skin: Wmp7Skin, playing: Boolean, a: Wmp7Actions) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        TransportBtn(skin, Transport.PREV, tr("Previous", "Önceki"), false, a.onPrev)
        if (playing) TransportBtn(skin, Transport.PAUSE, tr("Pause", "Duraklat"), true, a.onPlayPause)
        else TransportBtn(skin, Transport.PLAY, tr("Play", "Oynat"), true, a.onPlayPause)
        TransportBtn(skin, Transport.STOP, tr("Stop", "Durdur"), false, a.onStop)
        TransportBtn(skin, Transport.NEXT, tr("Next", "Sonraki"), false, a.onNext)
    }
}

/** Button painted per era: flat tile, glossy ball, glowing orb, thin ring, or a grey bevel. */
@Composable
private fun TransportBtn(skin: Wmp7Skin, t: Transport, label: String, main: Boolean, onClick: () -> Unit) {
    val s = skin.style
    val big = main && (s == Wmp7Style.ORB || s == Wmp7Style.AERO || s == Wmp7Style.GLOSS)
    val d = if (big) 48.dp else 40.dp
    val round = s != Wmp7Style.FLAT && s != Wmp7Style.CLASSIC
    Box(
        Modifier.size(d).clip(if (round) CircleShape else RoundedCornerShape(4.dp))
            .drawBehind { paintBtn(skin, big) }
            .clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (big) 20.dp else 16.dp)) { transportGlyph(t, if (big && (s == Wmp7Style.ORB || s == Wmp7Style.AERO)) Color.White else skin.btnFg) }
    }
}

private fun DrawScope.paintBtn(skin: Wmp7Skin, big: Boolean) {
    val w = size.width
    when (skin.style) {
        Wmp7Style.FLAT -> drawRect(skin.btn)
        Wmp7Style.FLAT_THIN -> drawCircle(skin.btnFg, w / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        Wmp7Style.CLASSIC -> {
            drawRect(skin.btn)
            drawRect(Color.White, Offset.Zero, Size(w, 1.5.dp.toPx())); drawRect(Color.White, Offset.Zero, Size(1.5.dp.toPx(), w))
            drawRect(Color(0xFF404040), Offset(0f, w - 1.5.dp.toPx()), Size(w, 1.5.dp.toPx()))
            drawRect(Color(0xFF404040), Offset(w - 1.5.dp.toPx(), 0f), Size(1.5.dp.toPx(), w))
        }
        Wmp7Style.GLOSS -> {
            drawCircle(Brush.verticalGradient(listOf(Color.White, skin.btn, skin.accent)))
            drawOval(Color.White.copy(alpha = .55f), Offset(w * .15f, w * .06f), Size(w * .7f, w * .4f))
        }
        Wmp7Style.ORB -> {
            val c = if (big) skin.accent else skin.btn
            drawCircle(Brush.radialGradient(listOf(c, Color.Black), center = Offset(w / 2, w * .35f), radius = w * .75f))
            drawCircle(skin.accent.copy(alpha = if (big) .9f else .35f), w / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            drawOval(Color.White.copy(alpha = .25f), Offset(w * .2f, w * .08f), Size(w * .6f, w * .32f))
        }
        Wmp7Style.AERO -> {
            val c = if (big) skin.accent else skin.btn
            drawCircle(Brush.verticalGradient(listOf(Color.White, c, c.copy(alpha = .8f).compositeOver(Color.Black))))
            drawCircle(Color.White.copy(alpha = .7f), w / 2 - .5.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawOval(Color.White.copy(alpha = .5f), Offset(w * .12f, w * .05f), Size(w * .76f, w * .4f))
        }
    }
}

/** Tap or drag to seek. The thumb follows the finger while dragging and seeks on release. */
@Composable
private fun Seek(skin: Wmp7Skin, positionMs: Int, durationMs: Int, onSeek: (Int) -> Unit, modifier: Modifier) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val fromPos = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val frac = drag ?: fromPos
    Canvas(
        modifier
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures(onTap = { o -> seek((o.x / size.width * durationMs).toInt()) })
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag?.let { seek((it * durationMs).toInt()) }; drag = null },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { c, _ -> drag = (c.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
    ) {
        val cy = size.height / 2
        val th = 4.dp.toPx()
        val r = if (skin.style == Wmp7Style.FLAT || skin.style == Wmp7Style.CLASSIC) CornerRadius(0f) else CornerRadius(th / 2)
        drawRoundRect(skin.seekTrack, Offset(0f, cy - th / 2), Size(size.width, th), r)
        drawRoundRect(skin.seekFill, Offset(0f, cy - th / 2), Size(size.width * frac, th), r)
        val c = Offset(size.width * frac, cy)
        when (skin.style) {
            Wmp7Style.CLASSIC -> drawRect(skin.frame, Offset(c.x - 4.dp.toPx(), cy - 9.dp.toPx()), Size(8.dp.toPx(), 18.dp.toPx()))
            Wmp7Style.GLOSS, Wmp7Style.ORB, Wmp7Style.AERO -> {
                drawCircle(Brush.verticalGradient(listOf(Color.White, skin.seekFill), cy - 7.dp.toPx(), cy + 7.dp.toPx()), 7.dp.toPx(), c)
            }
            else -> drawCircle(skin.seekFill, radius = 6.dp.toPx(), center = c)
        }
    }
}
