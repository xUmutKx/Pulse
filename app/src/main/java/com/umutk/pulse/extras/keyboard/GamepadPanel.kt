package com.umutk.pulse.extras.keyboard

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Android key codes: the D-pad, the face and shoulder buttons of a controller, and the two letter clusters the sticks stand in for
private const val K_UP = 19; private const val K_DOWN = 20; private const val K_LEFT = 21; private const val K_RIGHT = 22
private const val K_A = 96; private const val K_B = 97; private const val K_X = 99; private const val K_Y = 100
private const val K_L1 = 102; private const val K_R1 = 103; private const val K_L2 = 104; private const val K_R2 = 105
private const val K_START = 108; private const val K_SELECT = 109; private const val K_MODE = 110
private val LEFT_STICK = intArrayOf(K_UP, K_LEFT, K_DOWN, K_RIGHT)   // the arrow keys, like the D-pad: never letters
private val RIGHT_STICK = intArrayOf(K_UP, K_LEFT, K_DOWN, K_RIGHT)

/**
 * A game controller on the keyboard's own panel: D-pad, A B X Y, Select / Home / Start, L1 L2 R1 R2 and two sticks, drawn over whatever the
 * game shows (no board behind it). Every control holds its key while the finger is down, so movement is continuous and several can be pressed
 * at once. An input method can only send keys, not analog axes: both sticks and the D-pad send the arrow keys.
 * Its size follows the screen (wide when the phone lies sideways, narrower upright) and the − / + beside the shoulder buttons scale it.
 */
@Composable
internal fun GamepadPanel(th: KbTheme, act: KbActions) {
    val cfg = LocalConfiguration.current
    val land = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    var scale by remember { mutableStateOf(KeyboardPrefs.padScale) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth.value
        val fit = minOf(if (land) cfg.screenHeightDp * .7f else cfg.screenWidthDp * .7f, w * .6f)
        val u = (fit * scale / 100f).coerceAtMost(w * .5f).dp
        Column(Modifier.fillMaxWidth()) {
            // the top: the D-pad on the left, the face buttons on the right, Select, Home and Start between them
            Row(Modifier.fillMaxWidth().height(u * .5f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(start = u * .06f).size(u * .5f)) {
                    PadBtn(th, act, K_UP, u * .18f, u * .18f, Modifier.align(Alignment.TopCenter), label = "▲")
                    PadBtn(th, act, K_DOWN, u * .18f, u * .18f, Modifier.align(Alignment.BottomCenter), label = "▼")
                    PadBtn(th, act, K_LEFT, u * .18f, u * .18f, Modifier.align(Alignment.CenterStart), label = "◄")
                    PadBtn(th, act, K_RIGHT, u * .18f, u * .18f, Modifier.align(Alignment.CenterEnd), label = "►")
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(u * .04f), verticalAlignment = Alignment.CenterVertically) {
                    PadBtn(th, act, K_SELECT, u * .17f, u * .09f, round = false, label = "SEL")
                    PadBtn(th, act, K_MODE, u * .13f, u * .13f, icon = Icons.Rounded.Home)
                    PadBtn(th, act, K_START, u * .17f, u * .09f, round = false, label = "START")
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(end = u * .06f).size(u * .5f)) {
                    PadBtn(th, act, K_Y, u * .18f, u * .18f, Modifier.align(Alignment.TopCenter), label = "Y")
                    PadBtn(th, act, K_A, u * .18f, u * .18f, Modifier.align(Alignment.BottomCenter), label = "A")
                    PadBtn(th, act, K_X, u * .18f, u * .18f, Modifier.align(Alignment.CenterStart), label = "X")
                    PadBtn(th, act, K_B, u * .18f, u * .18f, Modifier.align(Alignment.CenterEnd), label = "B")
                }
            }
            // the sticks under the D-pad and the face buttons
            Row(Modifier.fillMaxWidth().padding(top = u * .04f), horizontalArrangement = Arrangement.SpaceBetween) {
                PadStick(th, act, u * .4f, LEFT_STICK, Modifier.padding(start = u * .1f))
                PadStick(th, act, u * .4f, RIGHT_STICK, Modifier.padding(end = u * .1f))
            }
            // the shoulder buttons on either side of the size control
            Row(Modifier.fillMaxWidth().padding(top = u * .04f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Column(verticalArrangement = Arrangement.spacedBy(u * .03f)) {
                    PadBtn(th, act, K_L1, u * .22f, u * .1f, round = false, label = "L1")
                    PadBtn(th, act, K_L2, u * .22f, u * .1f, round = false, label = "L2")
                }
                Spacer(Modifier.width(u * .05f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(u * .02f)) {
                    PadMini(th, u * .1f, "+") { scale = (scale + 10).coerceAtMost(150); KeyboardPrefs.padScale = scale; KeyboardPrefs.saveFloat() }
                    Text("$scale%", color = th.text, fontSize = (u.value * .06f).coerceAtLeast(9f).sp)
                    PadMini(th, u * .1f, "−") { scale = (scale - 10).coerceAtLeast(60); KeyboardPrefs.padScale = scale; KeyboardPrefs.saveFloat() }
                }
                Spacer(Modifier.width(u * .05f))
                Column(verticalArrangement = Arrangement.spacedBy(u * .03f)) {
                    PadBtn(th, act, K_R1, u * .22f, u * .1f, round = false, label = "R1")
                    PadBtn(th, act, K_R2, u * .22f, u * .1f, round = false, label = "R2")
                }
            }
        }
    }
}

/** One button: its key goes down with the finger and up when it lifts, so it can be held and pressed together with the others. */
@Composable
private fun PadBtn(th: KbTheme, act: KbActions, code: Int, w: Dp, h: Dp, modifier: Modifier = Modifier, round: Boolean = true, label: String = "", icon: ImageVector? = null) {
    var down by remember { mutableStateOf(false) }
    val actNow by rememberUpdatedState(act)
    val shape = if (round) CircleShape else RoundedCornerShape(minOf(w, h) * .3f)
    Box(
        modifier.size(w, h).clip(shape)
            .background(if (down) th.accent.copy(alpha = .6f) else Color.Transparent)
            .border(2.dp, if (down) th.accent else th.text.copy(alpha = .9f), shape)
            .pointerInput(code) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false); d.consume()
                    down = true; actNow.feedback(); actNow.padKey(code, true)
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                            c.consume()
                            if (!c.pressed) break
                        }
                    } finally { down = false; actNow.padKey(code, false) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = th.text, modifier = Modifier.size(minOf(w, h) * .6f))
        else Text(label, color = th.text, fontSize = (minOf(w, h).value * .42f).coerceAtLeast(8f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** A stick: the thumb follows the finger inside the ring; pushed past a third of the way it holds the key of that direction (two at a diagonal). */
@Composable
private fun PadStick(th: KbTheme, act: KbActions, dia: Dp, keys: IntArray, modifier: Modifier = Modifier) {
    var off by remember { mutableStateOf(Offset.Zero) }
    val actNow by rememberUpdatedState(act)
    val radius = with(LocalDensity.current) { (dia / 2).toPx() }
    Box(
        modifier.size(dia).clip(CircleShape).border(2.dp, th.text.copy(alpha = .6f), CircleShape)
            .pointerInput(keys) {
                awaitEachGesture {
                    val d = awaitFirstDown(requireUnconsumed = false); d.consume()
                    val mid = Offset(this.size.width / 2f, this.size.height / 2f)
                    val held = BooleanArray(4)
                    fun update(pos: Offset) {
                        var v = pos - mid
                        val len = v.getDistance()
                        val reach = radius * .62f
                        if (len > reach) v = v * (reach / len)
                        off = v
                        val t = radius * KeyboardPrefs.padDead / 100f
                        val want = booleanArrayOf(v.y < -t, v.x < -t, v.y > t, v.x > t)
                        for (i in 0..3) if (want[i] != held[i]) { held[i] = want[i]; actNow.padKey(keys[i], want[i]) }
                    }
                    actNow.feedback()
                    update(d.position)
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull { it.id == d.id } ?: break
                            c.consume()
                            if (!c.pressed) break
                            update(c.position)
                        }
                    } finally {
                        off = Offset.Zero
                        for (i in 0..3) if (held[i]) { held[i] = false; actNow.padKey(keys[i], false) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { IntOffset(off.x.roundToInt(), off.y.roundToInt()) }.size(dia * .42f).clip(CircleShape).background(th.text.copy(alpha = .85f)))
    }
}

/** The small − / + of the size control. */
@Composable
private fun PadMini(th: KbTheme, size: Dp, label: String, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).border(1.dp, th.text.copy(alpha = .7f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = th.text, fontSize = (size.value * .6f).coerceAtLeast(9f).sp, fontWeight = FontWeight.Bold)
    }
}
