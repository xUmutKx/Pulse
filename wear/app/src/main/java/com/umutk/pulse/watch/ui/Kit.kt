package com.umutk.pulse.watch.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedText
import kotlinx.coroutines.delay
import java.util.Date

/** The colours of Pulse Watch: true black (the watch's screen is off where it is black), white text, one accent. */
object W {
    val bg = Color.Black
    val text = Color.White
    val sub = Color(0xFFA9B0B8)
    val card = Color(0xFF1B1F24)
    val red = Color(0xFFFF5A5F)
    val green = Color(0xFF5BD68A)
    var accent by mutableStateOf(Color(0xFF4FC3F7))
}

/** A line of text in the watch's style (the plain text of Compose, so nothing of a phone theme comes along). */
@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = W.text,
    size: TextUnit = 15.sp,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(text, modifier, style = TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align), overflow = TextOverflow.Ellipsis, maxLines = maxLines)

/** How far content keeps from the edge: a round screen loses its corners, so the content of a page is narrower at the top and bottom. */
@Composable
fun edge(): Dp = if (LocalConfiguration.current.isScreenRound) 22.dp else 10.dp

/** The time along the top edge of the screen, curved like the watch's own (updated every minute). */
@Composable
fun CurvedTime() {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000L) } }
    val text = remember(now / 60_000L) { DateFormat.getTimeFormat(ctx).format(Date(now)) }
    CurvedLayout(Modifier.fillMaxSize(), anchor = 270f) {
        curvedText(text = text, style = CurvedTextStyle(fontSize = 13.sp, color = W.sub))
    }
}

/** A page of the watch: black, the time curved at the top, the content in the middle. */
@Composable
fun Page(showTime: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(W.bg)) {
        content()
        if (showTime) CurvedTime()
    }
}

/** A row of a list: a round tile with the icon, the name, and a line under it; wide, rounded, easy to hit with a finger. */
@Composable
fun Chip(label: String, icon: String? = null, secondary: String? = null, color: Color = W.card, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(W.accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) { T(icon, size = 16.sp) }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            T(label, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
            if (secondary != null) T(secondary, color = W.sub, size = 12.sp, maxLines = 1)
        }
    }
}

/** A round button with a word or a sign on it. */
@Composable
fun RoundButton(label: String, size: Dp = 52.dp, color: Color = W.card, textColor: Color = W.text, textSize: TextUnit = 18.sp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        T(label, color = textColor, size = textSize, weight = FontWeight.SemiBold, align = TextAlign.Center)
    }
}

/** A list title in the accent colour, centred. */
@Composable
fun Header(text: String) = T(text, Modifier.fillMaxWidth().padding(bottom = 4.dp), color = W.accent, size = 14.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)

/**
 * The crown (or the bezel) turns a value: [stepPx] of scrolling is one step, [onStep] gets +1 or -1 for each step. The page takes the focus when it opens,
 * so the crown works at once.
 */
@Composable
fun Modifier.crown(stepPx: Float = 48f, onStep: (Int) -> Unit): Modifier {
    val fr = remember { FocusRequester() }
    var acc by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    return this
        .onRotaryScrollEvent { e ->
            acc += e.verticalScrollPixels
            while (acc >= stepPx) { acc -= stepPx; onStep(1) }
            while (acc <= -stepPx) { acc += stepPx; onStep(-1) }
            true
        }
        .focusRequester(fr)
        .focusable()
}

/** A short tick under the finger (the crown's steps, a button). */
fun tick(c: Context, strong: Boolean = false) {
    runCatching {
        val v = c.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK))
    }
}

/** A long buzz (the end of a timer). */
fun buzz(c: Context) {
    runCatching {
        val v = c.getSystemService(Vibrator::class.java) ?: return
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1))
    }
}

/** Minutes and seconds (and hours when there are some) of [ms]. */
fun clock(ms: Long, hundredths: Boolean = false): String {
    val t = ms.coerceAtLeast(0L)
    val h = t / 3_600_000L; val m = t / 60_000L % 60; val s = t / 1000L % 60; val cs = t / 10L % 100
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (hundredths) "$base.%02d".format(cs) else base
}
