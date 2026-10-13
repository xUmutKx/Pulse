package com.umutk.pulse

import androidx.compose.ui.draw.clip
import com.umutk.pulse.features.themeWallpaper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Opening screen. Each look opens its own way: Windows 7 a ring of dots, Windows 11 a sliding bar,
 * Windows 8 pulsing tiles, Windows 10 a row of dots, XP a green block bar, Windows 95 a block bar in a
 * bevel, Material a turning arc, AMOLED a pulse line drawn on black. The logo follows the look too.
 * Tap to skip.
 */
@Composable
fun Splash(th: PTheme, onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val t by rememberInfiniteTransition(label = "boot").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(200)) }
        delay(850)
        onDone()
    }
    val mono = th.id == "amoled"
    Box(
        modifier = Modifier.fillMaxSize().clickable { onDone() }.graphicsLayer { alpha = fade.value },
    ) {
        // Windows 7 gets its backdrop drawn in code (see Win7Backdrop); the other looks keep the sunset (CC0)
        // each look has its own opening background: Windows 7 is drawn, XP and Material use their real default pictures, the rest the look's own colour
        // the user's own picture wins over the look's background
        val picked = (themeWallpaper(th.id) ?: SplashPic.uri)?.let { rememberPicture(it) }
        if (picked != null) {
            Image(picked, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color(0x66000000)))
        } else when (th.id) {
            "win7" -> { Image(painterResource(R.drawable.splash_win7_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            "win11" -> { Image(painterResource(R.drawable.splash_win11_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color(0x33000000))) }
            "xp" -> { Image(painterResource(R.drawable.splash_xp_bliss), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color(0x66000000))) }
            "material" -> { Image(painterResource(R.drawable.splash_android), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Box(Modifier.fillMaxSize().background(Color(0x66000000))) }
            // look-alike backdrops drawn here, not copies of Microsoft's pictures
            "win11" -> Win11Backdrop()
            "win10" -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1E3A), Color(0xFF1F5FA8)))))
            "win95" -> Box(Modifier.fillMaxSize().background(Color(0xFF008080)))
            else -> Box(Modifier.fillMaxSize().background(th.bg))
        }
        // a light veil over the whole screen, so the wallpaper still shows through
        Box(Modifier.fillMaxSize().background(Color(0x3A000000)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 30.dp, vertical = 24.dp),
        ) {
            when (th.id) {
                "material" -> Image(painterResource(R.drawable.ic_android_logo), null, Modifier.size(168.dp), colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(th.accent))
                "amoled" -> Box(Modifier.graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }) { PulseMark(Color.White) }
                "win7" -> Box(
                    Modifier.size(210.dp).background(Brush.radialGradient(listOf(Color(0x66FFFFFF), Color.Transparent))),
                    contentAlignment = Alignment.Center,
                ) { Image(painterResource(logoFor(th.id)), null, Modifier.size(168.dp)) }
                else -> Image(painterResource(logoFor(th.id)), null, Modifier.size(168.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Pulse", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            when (th.id) {
                "win11" -> SlidingBar(th.accent, th.line, t)
                "win8" -> MetroTiles(Color.White, t)
                "win10" -> DotRow(th.accent, t)
                "win7" -> DotRing(th.accent, t)
                "xp" -> BlockBar(Color(0xFF2BB12B), Color(0xFF0A246A), t)
                "win95" -> BevelBlocks(Color(0xFF000080), th.bg, t)
                "material" -> TurningArc(th.accent, t)
                else -> PulseLine(Color.White, t)
            }
        }
    }
}

/** The logo of each Windows look: the Windows version's own mark, from Wikimedia Commons (see README credits). */
private fun logoFor(id: String): Int = when (id) {
    "win95" -> R.drawable.logo_win95
    "xp" -> R.drawable.logo_xp
    "win7" -> R.drawable.logo_win7
    "win8" -> R.drawable.logo_win8
    "win10" -> R.drawable.logo_win10
    else -> R.drawable.logo_win11
}

/** Android-style robot head drawn here, not Google's artwork. */
@Composable
private fun RobotMark(color: Color, eye: Color) {
    Canvas(Modifier.size(96.dp)) {
        val k = size.minDimension / 96f
        drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(16f * k, 24f * k), size = Size(64f * k, 64f * k))
        drawRoundRect(color, topLeft = Offset(16f * k, 56f * k), size = Size(64f * k, 28f * k), cornerRadius = CornerRadius(6f * k))
        drawCircle(eye, radius = 5f * k, center = Offset(37f * k, 52f * k))
        drawCircle(eye, radius = 5f * k, center = Offset(59f * k, 52f * k))
        drawLine(color, Offset(34f * k, 26f * k), Offset(28f * k, 12f * k), strokeWidth = 4f * k, cap = StrokeCap.Round)
        drawLine(color, Offset(62f * k, 26f * k), Offset(68f * k, 12f * k), strokeWidth = 4f * k, cap = StrokeCap.Round)
    }
}

/** Monochrome pulse line inside a ring. */
@Composable
private fun PulseMark(color: Color) {
    Canvas(Modifier.size(96.dp)) {
        val k = size.minDimension / 108f
        drawCircle(color, radius = 46f * k, center = Offset(54f * k, 54f * k), style = Stroke(width = 4f * k))
        drawPath(pulsePath(k), color, style = Stroke(width = 4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun pulsePath(k: Float) = Path().apply {
    moveTo(26f * k, 54f * k); lineTo(38f * k, 54f * k); lineTo(44f * k, 40f * k)
    lineTo(52f * k, 68f * k); lineTo(60f * k, 34f * k); lineTo(66f * k, 54f * k); lineTo(82f * k, 54f * k)
}

/** 0 → 1 → 0 with eased ends, so a bar can go to one side and back. */
private fun pingPong(t: Float) = 0.5f - 0.5f * cos(2f * PI.toFloat() * t)

@Composable
private fun SlidingBar(accent: Color, track: Color, t: Float) {
    Canvas(Modifier.size(width = 160.dp, height = 4.dp)) {
        drawRect(track)
        val bw = size.width * 0.3f
        drawRect(accent, topLeft = Offset(pingPong(t) * (size.width - bw), 0f), size = Size(bw, size.height))
    }
}

@Composable
private fun MetroTiles(color: Color, t: Float) {
    Canvas(Modifier.size(56.dp)) {
        val gap = size.minDimension * 0.08f
        val cell = (size.minDimension - gap) / 2f
        var i = 0
        for (y in listOf(0f, cell + gap)) for (x in listOf(0f, cell + gap)) {
            val wave = (sin(2f * PI.toFloat() * (t - i * 0.12f)) + 1f) / 2f
            drawRect(color.copy(alpha = 0.25f + 0.75f * wave), topLeft = Offset(x, y), size = Size(cell, cell))
            i++
        }
    }
}

@Composable
private fun DotRow(accent: Color, t: Float) {
    Canvas(Modifier.size(width = 120.dp, height = 24.dp)) {
        val step = size.width / 5f
        for (i in 0 until 5) {
            val wave = (sin(2f * PI.toFloat() * (t - i * 0.1f)) + 1f) / 2f
            drawCircle(accent.copy(alpha = 0.3f + 0.7f * wave), radius = 4.dp.toPx() + 3.dp.toPx() * wave, center = Offset(step * (i + 0.5f), size.height / 2f))
        }
    }
}

/** Eight dots in a ring; the head is the brightest and the ring turns once per cycle. */
@Composable
private fun DotRing(accent: Color, t: Float) {
    Canvas(Modifier.size(56.dp)) {
        val r = size.minDimension / 2f - 6f
        val dot = size.minDimension * 0.07f
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 8) {
            val head = ((t - i / 8f) % 1f + 1f) % 1f
            val a = Math.toRadians(-90.0 + i * 45.0)
            val p = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
            drawCircle(accent.copy(alpha = 0.15f + 0.85f * (1f - head)), radius = dot, center = p)
        }
    }
}

/** XP style: a row of green blocks, a short run of them lit and moving along the bar. */
@Composable
private fun BlockBar(block: Color, box: Color, t: Float) {
    Canvas(Modifier.size(width = 180.dp, height = 16.dp)) {
        drawRect(box.copy(alpha = 0.35f), size = size)
        val n = 14
        val w = size.width / n
        val head = floor(t * n).toInt()
        for (i in 0 until n) {
            val lit = ((head - i) % n + n) % n < 5
            if (lit) drawRect(block, topLeft = Offset(i * w + w * 0.15f, size.height * 0.2f), size = Size(w * 0.7f, size.height * 0.6f))
        }
    }
}

/** Windows 95 style: a sunken bevel with navy blocks that fill up and start again. */
@Composable
private fun BevelBlocks(block: Color, face: Color, t: Float) {
    Canvas(Modifier.size(width = 180.dp, height = 18.dp)) {
        drawRect(Color(0xFF808080), size = size)
        drawRect(Color.White, topLeft = Offset(2f, 2f), size = Size(size.width - 2f, size.height - 2f))
        drawRect(Color(0xFFC0C0C0), topLeft = Offset(3f, 3f), size = Size(size.width - 6f, size.height - 6f))
        val n = 12
        val w = (size.width - 12f) / n
        val filled = floor(t * (n + 1)).toInt().coerceAtMost(n)
        for (i in 0 until filled) drawRect(block, topLeft = Offset(6f + i * w + 1f, 6f), size = Size(w - 3f, size.height - 12f))
    }
}

@Composable
private fun TurningArc(accent: Color, t: Float) {
    Canvas(Modifier.size(56.dp)) {
        val stroke = 5.dp.toPx()
        val sweep = 60f + 240f * abs(sin(PI.toFloat() * t))
        drawArc(accent, startAngle = t * 360f - 90f, sweepAngle = sweep, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

/** The pulse line is drawn left to right, then starts over. */
@Composable
private fun PulseLine(color: Color, t: Float) {
    Canvas(Modifier.size(width = 180.dp, height = 60.dp)) {
        val k = size.minDimension / 108f
        val p = pulsePath(k)
        drawPath(p, color.copy(alpha = 0.25f), style = Stroke(width = 3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        clipRect(right = size.width * t) {
            drawPath(p, color, style = Stroke(width = 3f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The Windows 7 opening backdrop, drawn here like its look-alike: a bright blue sky, soft light in the top-left and a white glow on the right, and several green-white ribbons sweeping across. */
@Composable
private fun Win7Backdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.linearGradient(listOf(Color(0xFF0E6FD0), Color(0xFF2A9BE8), Color(0xFF5CC3F2)), start = Offset(0f, 0f), end = Offset(w, h)))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent), center = Offset(w * .05f, h * .10f), radius = w * .7f), radius = w * .7f, center = Offset(w * .05f, h * .10f))
        val glow = Offset(w * .82f, h * .70f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .75f), Color.White.copy(alpha = .15f), Color.Transparent), center = glow, radius = w * .55f), radius = w * .55f, center = glow)
        // the ribbons: a wide soft band, then a thin bright line on top of it
        val ribbons = listOf(
            listOf(0f to .22f, .30f to .10f, .62f to .38f, 1f to .26f) to 7f,
            listOf(0f to .48f, .35f to .40f, .70f to .58f, 1f to .50f) to 9f,
            listOf(0f to .70f, .40f to .62f, .75f to .80f, 1f to .72f) to 6f,
            listOf(0f to .90f, .45f to .84f, .80f to .94f, 1f to .88f) to 5f,
        )
        ribbons.forEachIndexed { i, (pts, thick) ->
            val p = Path().apply {
                moveTo(0f, h * pts[0].second)
                cubicTo(w * pts[1].first, h * pts[1].second, w * pts[2].first, h * pts[2].second, w, h * pts[3].second)
            }
            drawPath(p, Color(0xFFB8F5C8).copy(alpha = .30f - i * .03f), style = Stroke(width = thick.dp.toPx() * 2.4f, cap = StrokeCap.Round))
            drawPath(p, Color(0xFFCFFFD9).copy(alpha = .55f - i * .06f), style = Stroke(width = thick.dp.toPx() * .5f, cap = StrokeCap.Round))
        }
        // small sparkles near the glow
        for (k in 0 until 14) {
            val x = w * (.55f + (k * 37 % 40) / 100f); val y = h * (.45f + (k * 53 % 35) / 100f)
            drawCircle(Color.White.copy(alpha = .5f), radius = 1.2.dp.toPx(), center = Offset(x, y))
        }
    }
}

/** A dusk sky with layered towers and their reflection in still water, drawn here as a look-alike backdrop. */
@Composable
private fun Win11Backdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val horizon = h * .55f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1B1A36), Color(0xFF3E2D55), Color(0xFF8A4F6A), Color(0xFFE39A6A)), startY = 0f, endY = horizon))
        // a far, lighter band of cloud near the horizon
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55F2A48A), Color.Transparent), startY = horizon * .80f, endY = horizon), topLeft = Offset(0f, horizon * .80f), size = Size(w, horizon * .20f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1F2E), Color(0xFF0B0910)), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        // the far towers (lighter), then the near ones
        val far = Color(0xFF3A2F45); val near = Color(0xFF0C0A10)
        for (i in 0 until 16) {
            val x = w * (.02f + i * .062f)
            val hh = h * (.05f + ((i * 29) % 9) * .011f)
            drawRect(far, Offset(x, horizon - hh * .7f), Size(w * .018f, hh * .7f))
        }
        for (i in 0 until 9) {
            val x = w * (.05f + i * .11f)
            val hh = h * (.07f + ((i * 37) % 7) * .014f)
            drawRect(near, Offset(x, horizon - hh), Size(w * .022f, hh))
            drawRect(Color(0xFFFFC46B).copy(alpha = .9f), Offset(x + w * .008f, horizon - hh), Size(w * .006f, h * .004f))
            drawRect(Color(0xFFFF7A59).copy(alpha = .9f), Offset(x + w * .008f, horizon - hh * .6f), Size(w * .006f, h * .004f))
        }
        drawRect(near, Offset(0f, horizon - h * .02f), Size(w, h * .03f))
        // the reflection: broken light lines under the lights
        for (i in 0 until 18) {
            val y = horizon + h * (.015f + i * .016f)
            drawRect(Color(0xFFFFC46B).copy(alpha = .05f + (i % 3) * .02f), Offset(w * (.12f + (i % 5) * .02f), y), Size(w * (.7f - i * .025f), 1.5f))
        }
    }
}

/** The opening picture the user picked (a content uri), or none for the look's own background. */
object SplashPic {
    var uri by mutableStateOf<String?>(null)
    fun load(c: android.content.Context) { uri = c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).getString("splashUri", null) }
    fun set(c: android.content.Context, v: String?) {
        uri = v
        c.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putString("splashUri", v).apply()
    }
}

/** Decodes the user's picture once, scaled down so a large photo does not stall the start. */
@Composable
private fun rememberPicture(uri: String): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    return remember(uri) {
        runCatching {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()?.asImageBitmap()
    }
}
