package com.umutk.pulse.extras.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.umutk.pulse.R

/*
 * The small pixel mascot of the chat, taken from the Claude Chat app (xUmutKx/ClaudeChat): awake it blinks now and then, while the model works it dances
 * (four stepped frames: centre, tilt left, centre, tilt right), and a tap makes it do a little move (one of the waiting poses of that app).
 */

internal val MASCOT_POSES = listOf("jump", "spin", "flip", "dance", "bounce", "wobble", "backflip", "rocket", "hop", "sway")

private fun poseMs(p: String): Int = when (p) { "jump", "hop" -> 620; "spin", "flip", "backflip" -> 1400; "rocket", "dance" -> 900; else -> 1300 }

private fun applyPose(l: GraphicsLayerScope, p: String, t: Float) {
    val s = kotlin.math.sin(2 * Math.PI * t).toFloat()
    val a = kotlin.math.abs(s); val half = kotlin.math.abs(kotlin.math.sin(Math.PI * t)).toFloat()
    val px = { v: Float -> v * l.density }
    when (p) {
        "jump" -> l.translationY = -a * px(14f)
        "hop" -> l.translationY = -half * px(10f)
        "dance" -> { l.translationX = s * px(8f); l.translationY = -a * px(6f); l.rotationZ = s * 8f }
        "spin" -> l.rotationY = t * 360f
        "sway" -> { l.translationX = s * px(6f); l.rotationZ = s * 3f }
        "bounce" -> l.translationY = -a * px(22f)
        "flip" -> l.rotationX = t * 360f
        "wobble" -> l.rotationZ = s * 6f
        "rocket" -> { l.translationY = -t * px(20f); l.translationX = kotlin.math.sin(2 * Math.PI * t * 8).toFloat() * px(1.5f) }
        "backflip" -> { l.translationY = -half * px(18f); l.rotationX = -t * 360f }
        else -> {}
    }
}

private val DANCE_ROT = floatArrayOf(0f, -12f, 0f, 12f)
private val DANCE_UP = floatArrayOf(1f, 0f, 1f, 0f)

/** [working]: the model is answering (it dances); [pose]: a move chosen by a tap (null = none). */
@Composable
internal fun AiMascot(working: Boolean, pose: String?, modifier: Modifier) {
    var closed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(2200L + (0..3800).random()); closed = true; kotlinx.coroutines.delay(140); closed = false } }
    val img = if (closed && !working && pose == null) R.drawable.ai_mascot_sleep else R.drawable.ai_mascot
    if (pose != null) {
        val k by rememberInfiniteTransition(label = "pose").animateFloat(0f, 1f, infiniteRepeatable(tween(poseMs(pose), easing = LinearEasing), RepeatMode.Restart), label = "k")
        Box(modifier.graphicsLayer { applyPose(this, pose, k) }) { Image(painterResource(img), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit) }
        return
    }
    if (working) {
        var frame by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(280L); frame = (frame + 1) % DANCE_ROT.size } }
        Box(modifier.graphicsLayer { rotationZ = DANCE_ROT[frame]; translationY = -DANCE_UP[frame] * 3.dp.toPx() }) { Image(painterResource(img), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit) }
        return
    }
    Image(painterResource(img), null, modifier, contentScale = ContentScale.Fit)
}
