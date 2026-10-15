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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
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
 * Opening screen. Each look opens its own way: Windows 7 "Starting Windows" then Welcome, Windows 11 a sliding bar,
 * Windows 8 pulsing tiles, Windows 10 the five-dot ring, XP the boot bar then welcome, Windows 95 a block bar in a
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
    // XP and 7 boot on black, then show their welcome step on the wallpaper
    var welcome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(200)) }
        when (th.id) {
            "xp", "win7" -> { delay(1100); welcome = true; delay(900) }
            "win10" -> delay(1800)
            else -> delay(850)
        }
        onDone()
    }
    val mono = th.id == "amoled"
    val root = Modifier.fillMaxSize().clickable { onDone() }.graphicsLayer { alpha = fade.value }
    when (th.id) {
        "win10" -> { Box(root) { Win10Boot() }; return }
        "xp" -> { Box(root) { if (welcome) XpWelcome() else XpBoot(t) }; return }
        "win7" -> { Box(root) { if (welcome) Win7Welcome() else Win7Boot(t) }; return }
    }
    Box(
        modifier = root,
    ) {
        // the opening screen is the look's own colour or a drawn backdrop, never a wallpaper picture: wallpapers live in the theme's window and are placed by hand there
        when (th.id) {
            "win11" -> Image(painterResource(R.drawable.splash_win11_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            "win95" -> Box(Modifier.fillMaxSize().background(Color(0xFF008080)))
            else -> Box(Modifier.fillMaxSize().background(th.bg))
        }
        // a light veil over the whole screen, so the wallpaper still shows through
        Box(Modifier.fillMaxSize().background(Color(0x3A000000)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.Center)
                // a faint translucent layer behind the logo, name and loading bar, so they stand apart from the picture
                .background(Color(0x33000000), RoundedCornerShape(22.dp))
                .padding(horizontal = 30.dp, vertical = 24.dp),
        ) {
            when (th.id) {
                "amoled", "material" -> Box(Modifier.graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }) { PulseMark(Color.White) }
                "win95" -> Image(painterResource(logoFor(th.id)), null, Modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp).size(140.dp))
                else -> Image(painterResource(logoFor(th.id)), null, Modifier.size(168.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Pulse", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(28.dp))
            when (th.id) {
                "win11" -> SlidingBar(th.accent, th.line, t)
                "win8" -> MetroTiles(Color.White, t)
                "win95" -> BevelBlocks(Color(0xFF000080), th.bg, t)
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

/** Windows 10 boot: dark hero-style light beams, the four-pane logo, and the five-dot spinner. */
@Composable
private fun Win10Boot() {
    val p by rememberInfiniteTransition(label = "w10").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Restart),
        label = "p",
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Color.Black)
            val glow = Offset(w * .5f, h * .4f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0A3D7A), Color(0xFF041A38), Color.Transparent), center = glow, radius = h * .55f), radius = h * .55f, center = glow)
            // light beams fanning out from behind the logo, like the hero wallpaper
            for (i in 0 until 7) {
                val a = (-60 + i * 20).toDouble() * PI / 180.0
                val tip = Offset(glow.x + (h * cos(a)).toFloat(), glow.y + (h * sin(a)).toFloat())
                val beam = Path().apply {
                    moveTo(glow.x, glow.y)
                    lineTo(tip.x, tip.y - h * .06f); lineTo(tip.x, tip.y + h * .06f); close()
                }
                drawPath(beam, Brush.linearGradient(listOf(Color(0x553FA9FF), Color.Transparent), start = glow, end = tip))
                val left = Path().apply {
                    moveTo(glow.x, glow.y)
                    lineTo(2 * glow.x - tip.x, tip.y - h * .06f); lineTo(2 * glow.x - tip.x, tip.y + h * .06f); close()
                }
                drawPath(left, Brush.linearGradient(listOf(Color(0x333FA9FF), Color.Transparent), start = glow, end = Offset(2 * glow.x - tip.x, tip.y)))
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_win10), null, Modifier.size(132.dp))
            Spacer(Modifier.height(96.dp))
            Win10Spinner(p)
        }
    }
}

// Windows 10 spinner keyframes: phase -> clockwise angle from the top, from the classic CSS clone of the boot ring
private val w10Keys = floatArrayOf(0f, .07f, .30f, .39f, .70f, .75f)
private val w10Angles = floatArrayOf(225f, 345f, 455f, 690f, 815f, 945f)

/** Angle of a dot at this phase, or null while it is hidden at the end of the cycle. */
private fun w10Angle(phase: Float): Float? {
    val q = (phase % 1f + 1f) % 1f
    for (k in 0 until 5) if (q < w10Keys[k + 1]) {
        val x = (q - w10Keys[k]) / (w10Keys[k + 1] - w10Keys[k])
        val e = when (k) { 0, 4 -> 1f - (1f - x) * (1f - x); 2 -> x * x * (3f - 2f * x); else -> x }
        return w10Angles[k] + (w10Angles[k + 1] - w10Angles[k]) * e
    }
    return null
}

/** Five white dots chasing round a ring; each trails the one before by 240 ms of a 5.5 s cycle. */
@Composable
private fun Win10Spinner(p: Float) {
    Canvas(Modifier.size(44.dp)) {
        val r = size.minDimension / 2f - 3.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        for (i in 0 until 5) {
            val deg = w10Angle(p - i * 0.24f / 5.5f) ?: continue
            val a = Math.toRadians(deg.toDouble())
            drawCircle(Color.White, radius = 2.6.dp.toPx(), center = Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat()))
        }
    }
}

/** XP boot: black screen, the logo, and three blue blocks running through a rounded box. */
@Composable
private fun XpBoot(t: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_xp), null, Modifier.background(Color.White, RoundedCornerShape(18.dp)).padding(16.dp).size(168.dp))
            Spacer(Modifier.height(40.dp))
            Canvas(Modifier.size(width = 130.dp, height = 18.dp)) {
                val rad = CornerRadius(size.height / 3f)
                drawRoundRect(Color(0xFFB2B2B2), cornerRadius = rad, style = Stroke(width = 1.5.dp.toPx()))
                val inset = 3.dp.toPx()
                val bw = 8.dp.toPx(); val gap = 2.dp.toPx()
                val run = size.width - 2 * inset
                clipRect(inset, inset, size.width - inset, size.height - inset) {
                    for (k in 0 until 3) {
                        // the group slides right and re-enters from the left
                        val x = inset - 3 * (bw + gap) + (t * (run + 3 * (bw + gap))) + k * (bw + gap)
                        drawRect(Brush.verticalGradient(listOf(Color(0xFF9DB8F5), Color(0xFF2D5FD8), Color(0xFF1A3F9E)), startY = inset, endY = size.height - inset), topLeft = Offset(x, inset), size = Size(bw, size.height - 2 * inset))
                    }
                }
            }
        }
    }
}

/** XP welcome step on the logon background. */
@Composable
private fun XpWelcome() {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.xp_logon_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text("welcome", Modifier.align(Alignment.Center), color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
            style = TextStyle(shadow = Shadow(Color(0x88000000), Offset(3f, 3f), 4f)))
    }
}

/** Windows 7 "Starting Windows": the coloured logo with a breathing glow on black. */
@Composable
private fun Win7Boot(t: Float) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(220.dp).background(Brush.radialGradient(listOf(Color.White.copy(alpha = .12f + .25f * pingPong(t)), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(R.drawable.logo_win7), null, Modifier.size(150.dp).graphicsLayer { alpha = .75f + .25f * pingPong(t) }) }
            Spacer(Modifier.height(24.dp))
            Text("Starting Windows", color = Color(0xFFE8E8E8), fontSize = 20.sp)
        }
    }
}

/** Windows 7 welcome step on the uploaded wallpaper. */
@Composable
private fun Win7Welcome() {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.splash_win7_wallpaper), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text("Welcome", Modifier.align(Alignment.Center), color = Color.White, fontSize = 34.sp,
            style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(2f, 2f), 6f)))
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

/** The pulse line spans the whole width (so it sits centred under the logo), drawn left to right, then starts over. */
@Composable
private fun PulseLine(color: Color, t: Float) {
    Canvas(Modifier.size(width = 200.dp, height = 48.dp)) {
        val w = size.width; val h = size.height
        // same beat as the logo mark, x stretched over the width, y 34..68 mapped onto the height
        val pts = listOf(0f to 54f, .12f to 54f, .24f to 40f, .40f to 68f, .55f to 34f, .70f to 54f, 1f to 54f)
        val p = Path().apply {
            pts.forEachIndexed { n, (x, y) -> val px = x * w; val py = (y - 30f) / 42f * h; if (n == 0) moveTo(px, py) else lineTo(px, py) }
        }
        val st = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(p, color.copy(alpha = 0.25f), style = st)
        clipRect(right = w * t) { drawPath(p, color, style = st) }
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
