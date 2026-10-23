package com.umutk.pulse.extras.launcher

import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.clickable
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.umutk.pulse.features.wallsFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** The stock picture this look's wallpaper shows: the launcher's pick for the look, else its first picture. */
fun wallpaperRes(lookId: String, landscape: Boolean = false): Int? {
    val all = wallsFor(lookId)
    if (all.isEmpty()) return null
    // "a different wallpaper every day": the day of the year picks the picture
    var i = (if (LauncherPrefs.wallDaily) java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % all.size else LauncherPrefs.wallIndex(lookId)).coerceIn(0, all.lastIndex)
    // a phone picture is tall: a rotated screen takes the first desktop picture of the look instead
    if (landscape && com.umutk.pulse.features.isMobileWall(lookId, i)) i = all.indices.firstOrNull { !com.umutk.pulse.features.isMobileWall(lookId, it) } ?: i
    // a desktop picture is wide: a portrait screen takes the first phone picture instead
    else if (!landscape && !com.umutk.pulse.features.isMobileWall(lookId, i)) i = all.indices.firstOrNull { com.umutk.pulse.features.isMobileWall(lookId, it) } ?: i
    return all[i]
}

/** The look's wallpaper behind the launcher, or [fallback] when the look has no stock picture. */
@Composable
fun LauncherWallpaper(lookId: String, fallback: Brush, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val res = wallpaperRes(lookId, land)
    val bmp = remember(res) { res?.let { BitmapFactory.decodeResource(ctx.resources, it, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() } }
    Box(modifier.fillMaxSize()) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().then(if (LauncherPrefs.wallBlur > 0) Modifier.blur(LauncherPrefs.wallBlur.dp) else Modifier), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(fallback))
        if (LauncherPrefs.wallSea) SeaWaves(Modifier.fillMaxSize())
        if (LauncherPrefs.wallDim > 0) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = LauncherPrefs.wallDim / 100f)))
        content()
    }
}

/** Same, for a looks whose fallback is a plain colour. */
@Composable
fun LauncherWallpaper(lookId: String, fallback: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    LauncherWallpaper(lookId, SolidColor(fallback), modifier, content)

/** Clock and date for the right end of a taskbar; refreshes every 15 s. */
@Composable
fun TaskbarClock(color: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000L); now = System.currentTimeMillis() } }
    val time = SimpleDateFormat(if (LauncherPrefs.clock24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat(when (LauncherPrefs.dateFmt) { "mdy" -> "MM/dd/yy"; "ymd" -> "yyyy-MM-dd"; else -> "dd/MM/yy" }, Locale.getDefault()).format(Date(now))
    Text("$time\n$date", color = color, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.End, modifier = modifier.clickable { LauncherSession.clockOpen = !LauncherSession.clockOpen })
}

/** Sets the look's picture as the phone wallpaper. False when there is no picture or the system refuses. */
fun setPhoneWallpaper(c: Context, lookId: String): Boolean = runCatching {
    val res = wallpaperRes(lookId) ?: return false
    val full = BitmapFactory.decodeResource(c.resources, res) ?: return false
    WallpaperManager.getInstance(c).setBitmap(full)
    true
}.getOrDefault(false)

/** Layered sea waves that roll along the lower part of the screen, with a soft shimmer on the water. */
@Composable
fun SeaWaves(modifier: Modifier = Modifier) {
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "sea").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "t")
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        // back to front: farther waves are higher, slower and paler
        val layers = listOf(Triple(.62f, Color(0x552E86C1), 1f), Triple(.70f, Color(0x661F6FA8), -1.3f), Triple(.79f, Color(0x80155A8A), 1.7f), Triple(.88f, Color(0x990E4268), -2.1f))
        layers.forEachIndexed { i, (level, col, speed) ->
            val path = androidx.compose.ui.graphics.Path()
            val amp = h * (.012f + i * .004f)
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 8f) {
                val y = h * level + amp * kotlin.math.sin(x / w * (5f + i) + t * speed + i) + amp * .5f * kotlin.math.sin(x / w * (11f + i * 2) - t * speed * .7f)
                path.lineTo(x, y); x += 8f
            }
            path.lineTo(w, h); path.close()
            drawPath(path, col)
            // the white foam line on the crest
            val foam = androidx.compose.ui.graphics.Path()
            x = 0f
            while (x <= w + 8f) {
                val y = h * level + amp * kotlin.math.sin(x / w * (5f + i) + t * speed + i) + amp * .5f * kotlin.math.sin(x / w * (11f + i * 2) - t * speed * .7f)
                if (x == 0f) foam.moveTo(x, y) else foam.lineTo(x, y); x += 8f
            }
            drawPath(foam, Color.White.copy(alpha = .22f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
        }
    }
}
