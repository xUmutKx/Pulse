package com.umutk.pulse.extras.launcher

import androidx.compose.ui.unit.dp
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
    // Windows 11 turned sideways: its own blue bloom
    if (landscape && lookId == "win11" && com.umutk.pulse.features.isMobileWall(lookId, i)) return com.umutk.pulse.R.drawable.splash_win11_wallpaper
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

