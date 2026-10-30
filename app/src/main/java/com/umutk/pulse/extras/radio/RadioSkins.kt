package com.umutk.pulse.extras.radio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.umutk.pulse.tr

/** The radio's skins: 0 plain (the screen only), 1 a nostalgic wood-and-chrome boombox, 2 a modern matte boombox with a lit display. Drawn here, not downloaded. */
object RadioSkins {
    val names get() = listOf(tr("Plain", "Düz"), tr("Nostalgic", "Nostaljik"), tr("Modern", "Modern"))
}

/** The boombox picture on top of the radio screen; the needle and the lit display follow [playing]. */
@Composable
fun RadioSkinHeader(skin: Int, playing: Boolean) {
    Box(Modifier.fillMaxWidth().height(150.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (skin == 1) nostalgic(playing) else modern(playing)
        }
    }
}

private fun DrawScope.nostalgic(playing: Boolean) {
    val w = size.width; val h = size.height
    // the wood case with a rounded top
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9C6A3A), Color(0xFF5E3A1B))), Offset(0f, 0f), Size(w, h), CornerRadius(26f))
    drawRoundRect(Color(0x66000000), Offset(0f, 0f), Size(w, h), CornerRadius(26f), style = Stroke(3f))
    // two chrome speakers with their grilles
    listOf(w * .2f, w * .8f).forEach { cx ->
        val cy = h * .58f; val r = h * .30f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDADADA), Color(0xFF7A7A7A)), Offset(cx, cy), r), r, Offset(cx, cy))
        drawCircle(Color(0xFF2B2B2B), r * .72f, Offset(cx, cy))
        for (i in -2..2) for (j in -2..2) drawCircle(Color(0xFFBDBDBD), 2.4f, Offset(cx + i * r * .2f, cy + j * r * .2f))
    }
    // the cream dial in the middle with station marks and a needle
    val dl = w * .33f; val dt = h * .18f; val dw = w * .34f; val dh = h * .34f
    drawRoundRect(Color(0xFFF3E7C9), Offset(dl, dt), Size(dw, dh), CornerRadius(6f))
    drawRoundRect(Color(0xFF4A2E14), Offset(dl, dt), Size(dw, dh), CornerRadius(6f), style = Stroke(2f))
    for (i in 0..10) { val x = dl + dw * (.08f + i * .084f); drawLine(Color(0xFF4A2E14), Offset(x, dt + dh * .25f), Offset(x, dt + dh * .42f), 1.5f) }
    val needle = dl + dw * (if (playing) .62f else .2f)
    drawLine(Color(0xFFC0392B), Offset(needle, dt + dh * .12f), Offset(needle, dt + dh * .82f), 2.5f)
    // two chrome knobs on the top right
    listOf(w * .9f, w * .97f).forEach { x -> drawCircle(Brush.radialGradient(listOf(Color(0xFFEEEEEE), Color(0xFF777777)), Offset(x, h * .12f), 12f), 9f, Offset(x, h * .12f)) }
}

private fun DrawScope.modern(playing: Boolean) {
    val w = size.width; val h = size.height
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF26262C), Color(0xFF0E0E12))), Offset(0f, 0f), Size(w, h), CornerRadius(22f))
    drawRoundRect(Color(0xFF3A3A44), Offset(0f, 0f), Size(w, h), CornerRadius(22f), style = Stroke(2f))
    // two speakers with a blue ring that lights while playing
    listOf(w * .2f, w * .8f).forEach { cx ->
        val cy = h * .62f; val r = h * .27f
        drawCircle(Color(0xFF111116), r, Offset(cx, cy))
        drawCircle(if (playing) Color(0xFF3DA5F5) else Color(0xFF34343E), r, Offset(cx, cy), style = Stroke(4f))
        drawCircle(Color(0xFF1B1B22), r * .5f, Offset(cx, cy))
    }
    // a lit display with the station name
    val dl = w * .36f; val dt = h * .14f; val dw = w * .28f; val dh = h * .3f
    drawRoundRect(Color(0xFF0B1F2A), Offset(dl, dt), Size(dw, dh), CornerRadius(8f))
    drawRoundRect(if (playing) Color(0xFF3DA5F5) else Color(0xFF2E4A5C), Offset(dl, dt), Size(dw, dh), CornerRadius(8f), style = Stroke(2f))
    drawRect(if (playing) Color(0x553DA5F5) else Color(0x22FFFFFF), Offset(dl + 6f, dt + dh * .55f), Size(dw * (if (playing) .7f else .15f), 4f))
}
