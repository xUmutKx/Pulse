package com.umutk.pulse.extras.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.T
import com.umutk.pulse.bevel

/**
 * A player skin: colours and shapes of the library, the top bar, the seek bar and the transport.
 * [seek] picks the seek bar drawing (see SkinSeek), [btn] the button chrome (see SkinBtn).
 */
class VSkin(
    val id: String, val name: String,
    val bg: Color, val fg: Color, val sub: Color, val accent: Color,
    val top: Brush, val topFg: Color, val bottom: Brush,
    val round: Dp, val bevel: Boolean = false, val gloss: Boolean = false,
    /** big play/pause with previous/next in the middle; false puts the transport in the bottom bar */
    val centered: Boolean = true,
    val seek: Int = 0, val btn: Int = 0,
    val icons: com.umutk.pulse.extras.ui.IconStyle = com.umutk.pulse.extras.ui.IconSet.styleFor(id),
)

private val scrim = Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
private val scrimUp = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
private fun solid(c: Long) = SolidColor(Color(c))

private val baseSkins = listOf(
    VSkin("next", "Next Player", Color(0xFF0F1115), Color(0xFFE3E2E6), Color(0xFFA9ADB5), Color(0xFFA8C7FA), scrim, Color.White, scrimUp, 12.dp),
    VSkin("material", "Material You", Color(0xFF141218), Color(0xFFE6E0E9), Color(0xFFCAC4D0), Color(0xFFD0BCFF), scrim, Color.White, solid(0xF0211F26), 28.dp, centered = false, seek = 11, btn = 1),
    VSkin("wmp", "Media Player 11", Color(0xFF0B1B33), Color.White, Color(0xFF9DB8DD), Color(0xFF4FA3FF),
        Brush.verticalGradient(listOf(Color(0xFF3A74BD), Color(0xFF0B2A52))), Color.White,
        Brush.verticalGradient(listOf(Color(0xFF2B5FA3), Color(0xFF0A2347), Color(0xFF123A6E))), 8.dp, gloss = true, centered = false, seek = 1, btn = 3),
    VSkin("vlc", "VLC", Color(0xFF1B1B1B), Color.White, Color(0xFFB0B0B0), Color(0xFFFF8800), solid(0xE6000000), Color.White, solid(0xE6000000), 4.dp, centered = false, seek = 5),
    VSkin("mx", "MX Player", Color.Black, Color.White, Color(0xFFBBBBBB), Color(0xFF3F8EFC), scrim, Color.White, scrimUp, 6.dp, centered = false, seek = 3),
    VSkin("winamp", "Winamp", Color.Black, Color(0xFF00E000), Color(0xFF008800), Color(0xFF00E000),
        Brush.verticalGradient(listOf(Color(0xFF4A4F5C), Color(0xFF1E2028))), Color(0xFFC8D0E0),
        Brush.verticalGradient(listOf(Color(0xFF3A3E4A), Color(0xFF1A1C24))), 0.dp, centered = false, seek = 6, btn = 6),
    VSkin("poweramp", "Poweramp", Color.Black, Color(0xFFEEEEEE), Color(0xFF8A8A8A), Color(0xFFFF9500), SolidColor(Color.Transparent), Color(0xFFEEEEEE), scrimUp, 4.dp, seek = 4),
    VSkin("minimal", "Minimal", Color.Black, Color.White, Color(0xFF9A9A9A), Color.White, SolidColor(Color.Transparent), Color.White, SolidColor(Color.Transparent), 0.dp, seek = 10),
    VSkin("amoled", "AMOLED", Color.Black, Color.White, Color(0xFF9A9A9A), Color.White, SolidColor(Color.Transparent), Color.White, SolidColor(Color.Transparent), 8.dp, seek = 3),
    VSkin("classic", "Windows 95", Color(0xFFC0C0C0), Color.Black, Color(0xFF404040), Color(0xFF000080),
        Brush.horizontalGradient(listOf(Color(0xFF000080), Color(0xFF1084D0))), Color.White, SolidColor(Color(0xFFC0C0C0)), 0.dp, bevel = true, centered = false, seek = 2, btn = 2),
    VSkin("xp", "Windows XP", Color(0xFFECE9D8), Color.Black, Color(0xFF555555), Color(0xFF316AC5),
        Brush.verticalGradient(listOf(Color(0xFF0058EE), Color(0xFF3A93FF), Color(0xFF0054E3))), Color.White,
        solid(0xFFECE9D8), 3.dp, centered = false, seek = 7, btn = 4),
    VSkin("win7", "Windows 7", Color(0xFFDDE8F5), Color(0xFF1E395B), Color(0xFF5A7391), Color(0xFF2E9BE0),
        Brush.verticalGradient(listOf(Color(0xFFDCEBFB), Color(0xFF9CC3EA))), Color(0xFF10294A),
        Brush.verticalGradient(listOf(Color(0xFFF1F7FD), Color(0xFFBDD6F0))), 6.dp, gloss = true, centered = false, seek = 8, btn = 3),
    VSkin("flat", "Windows 10", Color(0xFF1F1F1F), Color.White, Color(0xFFA0A0A0), Color(0xFF0078D4), solid(0xCC1F1F1F), Color.White, solid(0xCC1F1F1F), 0.dp, seek = 3, btn = 7),
    VSkin("win11", "Windows 11", Color(0xFF202020), Color.White, Color(0xFFC5C5C5), Color(0xFF60CDFF), solid(0xCC2B2B2B), Color.White, solid(0xE6202020), 8.dp, centered = false, seek = 9, btn = 5),
)

private val genSeek = intArrayOf(0, 3, 4, 5, 9, 10, 11, 1)
private val genBtn = intArrayOf(0, 1, 5, 7, 3, 1, 0, 5)
private val genRound = intArrayOf(4, 8, 14, 20, 28, 0)

/** One skin per palette: bar style (solid, gradient, floating over the video), seek bar, button chrome and corner size cycle with the index, so neighbours differ. */
private val generatedSkins = videoPalettes.mapIndexed { i, p ->
    val bg = Color(p.bg); val fg = Color(p.fg); val ac = Color(p.accent)
    val style = if (p.light) i % 2 else i % 3
    val floating = style == 2
    VSkin(
        "g$i", p.name, bg, if (floating) Color.White else fg, Color(p.sub), ac,
        when (style) { 0 -> SolidColor(bg.copy(alpha = .92f)); 1 -> Brush.verticalGradient(listOf(ac.copy(alpha = .55f).compositeOver(bg), bg)); else -> scrim },
        if (floating) Color.White else fg,
        when (style) { 0 -> SolidColor(bg.copy(alpha = .92f)); 1 -> Brush.verticalGradient(listOf(bg, ac.copy(alpha = .45f).compositeOver(bg))); else -> scrimUp },
        genRound[i * 5 % genRound.size].dp, centered = i % 3 != 1, seek = genSeek[i * 3 % genSeek.size], btn = genBtn[i * 5 % genBtn.size], icons = com.umutk.pulse.extras.ui.IconSet.styleForIndex(i),
    )
}

val videoSkins = baseSkins + generatedSkins

fun skinOf(id: String): VSkin = videoSkins.firstOrNull { it.id == id } ?: videoSkins[0]

/** Seek bar drawn per skin (see VSkin.seek): M3 round, glossy blue, bevel, thin line, Poweramp bar, VLC orange, Winamp LEDs, XP trackbar, Win7 glass, Win11 round. */
@Composable
fun SkinSeek(value: Float, max: Float, s: VSkin, onChange: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val frac = (value / max.coerceAtLeast(1f)).coerceIn(0f, 1f)
    Canvas(
        modifier.height(32.dp)
            .pointerInput(max) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f) * max); onDone() } }
            .pointerInput(max) { detectHorizontalDragGestures(onDragEnd = onDone) { c, _ -> onChange((c.position.x / size.width).coerceIn(0f, 1f) * max) } },
    ) {
        val w = size.width; val cy = size.height / 2; val x = w * frac
        fun track(h: Float, back: Color, front: Brush, r: Float = h / 2) {
            drawRoundRect(back, Offset(0f, cy - h / 2), Size(w, h), CornerRadius(r))
            drawRoundRect(front, Offset(0f, cy - h / 2), Size(x.coerceAtLeast(h), h), CornerRadius(r))
        }
        when (s.seek) {
            2 -> {
                drawRect(Color.White, Offset(0f, cy - 2.dp.toPx()), Size(w, 4.dp.toPx()))
                drawRect(Color(0xFF808080), Offset(0f, cy - 2.dp.toPx()), Size(w - 1.dp.toPx(), 3.dp.toPx()))
                val tw = 11.dp.toPx(); val th = 21.dp.toPx(); val l = x - tw / 2; val t = cy - th / 2
                drawRect(Color(0xFFC0C0C0), Offset(l, t), Size(tw, th))
                drawRect(Color.White, Offset(l, t), Size(tw, 1.dp.toPx())); drawRect(Color.White, Offset(l, t), Size(1.dp.toPx(), th))
                drawRect(Color(0xFF404040), Offset(l, t + th - 1.dp.toPx()), Size(tw, 1.dp.toPx())); drawRect(Color(0xFF404040), Offset(l + tw - 1.dp.toPx(), t), Size(1.dp.toPx(), th))
            }
            1 -> {
                track(7.dp.toPx(), Color(0xFF06142A), Brush.verticalGradient(listOf(Color(0xFF9BD0FF), s.accent, Color(0xFF1C6FD0)), cy - 4.dp.toPx(), cy + 4.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF8EC4FF), Color(0xFF2C78D0)), Offset(x - 3.dp.toPx(), cy - 3.dp.toPx()), 10.dp.toPx()), 8.dp.toPx(), Offset(x, cy))
            }
            3 -> {
                track(2.dp.toPx(), Color(0x55FFFFFF), SolidColor(s.accent), 0f)
                drawCircle(Color.White, 6.dp.toPx(), Offset(x, cy))
            }
            4 -> {
                track(3.dp.toPx(), Color(0xFF333333), SolidColor(s.accent), 0f)
                drawRect(s.accent, Offset(x - 2.dp.toPx(), cy - 9.dp.toPx()), Size(4.dp.toPx(), 18.dp.toPx()))
            }
            5 -> {
                track(5.dp.toPx(), Color(0xFF4A4A4A), SolidColor(s.accent))
                drawCircle(s.accent, 8.dp.toPx(), Offset(x, cy)); drawCircle(Color(0xFF1B1B1B), 3.dp.toPx(), Offset(x, cy))
            }
            6 -> { // Winamp: green LED segments and a grey slider
                val seg = 5.dp.toPx(); val gap = 2.dp.toPx(); val n = (w / (seg + gap)).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    val on = i.toFloat() / n < frac
                    drawRect(if (on) Brush.verticalGradient(listOf(Color(0xFF80FF40), Color(0xFF00A000))) else SolidColor(Color(0xFF14301A)), Offset(i * (seg + gap), cy - 7.dp.toPx()), Size(seg, 14.dp.toPx()))
                }
                val tw = 12.dp.toPx(); val l = (x - tw / 2).coerceIn(0f, w - tw)
                drawRect(Brush.verticalGradient(listOf(Color(0xFFB0B4C0), Color(0xFF5A5E6C))), Offset(l, cy - 10.dp.toPx()), Size(tw, 20.dp.toPx()))
                drawRect(Color(0xFF202228), Offset(l, cy - 10.dp.toPx()), Size(tw, 20.dp.toPx()), style = Stroke(1.dp.toPx()))
            }
            7 -> { // XP trackbar: sunken groove and a pointer-shaped blue-edged thumb
                drawRect(Color(0xFFFFFFFF), Offset(0f, cy - 2.dp.toPx()), Size(w, 4.dp.toPx()))
                drawRect(Color(0xFF9D9D92), Offset(0f, cy - 2.dp.toPx()), Size(w, 4.dp.toPx()), style = Stroke(1.dp.toPx()))
                val tw = 11.dp.toPx(); val th = 22.dp.toPx(); val l = x - tw / 2; val t = cy - th / 2
                val p = Path().apply { moveTo(l, t); lineTo(l + tw, t); lineTo(l + tw, t + th * .65f); lineTo(l + tw / 2, t + th); lineTo(l, t + th * .65f); close() }
                drawPath(p, Brush.verticalGradient(listOf(Color.White, Color(0xFFC9D9F5)), t, t + th))
                drawPath(p, Color(0xFF316AC5), style = Stroke(1.2.dp.toPx()))
            }
            8 -> { // Windows 7 glass: green progress in a glossy groove
                val h = 9.dp.toPx()
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE2E8EE), Color(0xFFC4CFDA)), cy - h / 2, cy + h / 2), Offset(0f, cy - h / 2), Size(w, h), CornerRadius(h / 2))
                drawRoundRect(Color(0xFF7C8FA3), Offset(0f, cy - h / 2), Size(w, h), CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFB8F0B0), Color(0xFF3CC03C), Color(0xFF1E9A1E)), cy - h / 2, cy + h / 2), Offset(1.dp.toPx(), cy - h / 2 + 1.dp.toPx()), Size((x - 2.dp.toPx()).coerceAtLeast(h - 2.dp.toPx()), h - 2.dp.toPx()), CornerRadius(h / 2))
                drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFBFD6EE)), Offset(x, cy - 2.dp.toPx()), 10.dp.toPx()), 8.dp.toPx(), Offset(x, cy))
                drawCircle(Color(0xFF6C86A4), 8.dp.toPx(), Offset(x, cy), style = Stroke(1.dp.toPx()))
            }
            9 -> { // Windows 11: thin rounded bar, round thumb with a ring
                track(4.dp.toPx(), s.fg.copy(alpha = .25f), SolidColor(s.accent))
                drawCircle(s.fg, 9.dp.toPx(), Offset(x, cy)); drawCircle(s.accent, 5.dp.toPx(), Offset(x, cy))
            }
            10 -> {
                track(1.5f.dp.toPx(), Color(0x44FFFFFF), SolidColor(Color.White))
                drawCircle(Color.White, 3.dp.toPx(), Offset(x, cy))
            }
            11 -> { // Material You expressive: thick groove with a gap and a pill handle
                val h = 10.dp.toPx(); val g = 6.dp.toPx()
                drawRoundRect(s.accent.copy(alpha = .3f), Offset((x + g).coerceAtMost(w), cy - h / 2), Size((w - x - g).coerceAtLeast(0f), h), CornerRadius(h / 2))
                drawRoundRect(s.accent, Offset(0f, cy - h / 2), Size((x - g).coerceAtLeast(h), h), CornerRadius(h / 2))
                drawRoundRect(s.accent, Offset(x - 2.dp.toPx(), cy - 13.dp.toPx()), Size(4.dp.toPx(), 26.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
            else -> {
                track(4.dp.toPx(), s.accent.copy(alpha = .3f), SolidColor(s.accent))
                drawCircle(s.accent, 8.dp.toPx(), Offset(x, cy))
            }
        }
    }
}

/** A control button drawn per skin (see VSkin.btn): plain, tonal circle, bevel, glass circle, outline, soft rounded, Winamp metal, flat square. */
@Composable
fun SkinBtn(s: VSkin, onClick: () -> Unit, size: Dp = 44.dp, content: @Composable () -> Unit) {
    val m = Modifier.size(size)
    val chrome = when (s.btn) {
        1 -> m.padding(2.dp).background(s.accent.copy(alpha = .22f), CircleShape)
        2 -> m.padding(3.dp).bevel(true)
        3 -> m.padding(3.dp).background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .75f), s.accent.copy(alpha = .55f), s.accent.copy(alpha = .9f))), CircleShape).border(1.dp, Color.White.copy(alpha = .7f), CircleShape)
        4 -> m.padding(3.dp).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D4C0))), RoundedCornerShape(s.round)).border(1.dp, s.accent, RoundedCornerShape(s.round))
        5 -> m.padding(3.dp).background(s.fg.copy(alpha = .08f), RoundedCornerShape(s.round))
        6 -> m.padding(2.dp).background(Brush.verticalGradient(listOf(Color(0xFF6A6F80), Color(0xFF2A2D38))), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFF9096A8), RoundedCornerShape(2.dp))
        7 -> m.padding(2.dp).background(s.fg.copy(alpha = .1f))
        else -> m
    }
    Box(chrome.clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** The skin's own picture of the controls, for the picker: top bar, transport buttons and the seek bar at 40 %. */
@Composable
private fun SkinPreview(s: VSkin) {
    Column(Modifier.fillMaxWidth().background(s.bg, RoundedCornerShape(s.round.coerceAtMost(8.dp)))) {
        Box(Modifier.fillMaxWidth().height(8.dp).background(s.top))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SkinBtn(s, {}, 26.dp) { com.umutk.pulse.extras.ui.TIcon("prev", s.fg, 14.dp, s.icons) }
            SkinBtn(s, {}, 30.dp) { com.umutk.pulse.extras.ui.TIcon("play", s.fg, 18.dp, s.icons) }
            SkinBtn(s, {}, 26.dp) { com.umutk.pulse.extras.ui.TIcon("next", s.fg, 14.dp, s.icons) }
        }
        Box(Modifier.fillMaxWidth().background(s.bottom).padding(horizontal = 6.dp)) { SkinSeek(40f, 100f, s, {}, {}, Modifier.fillMaxWidth().height(22.dp)) }
    }
}

/** Every skin as a live preview in the shared 3-per-row gallery. */
@Composable
fun SkinPicker(current: String, onPick: (String) -> Unit) {
    val items = remember { videoSkins.map { s -> com.umutk.pulse.extras.ui.ThemeItem(s.id, s.name) { SkinPreview(s) } } }
    com.umutk.pulse.extras.ui.ThemeGrid(items, current, onPick, Modifier.padding(horizontal = 12.dp))
}
