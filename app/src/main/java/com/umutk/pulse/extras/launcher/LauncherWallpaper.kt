package com.umutk.pulse.extras.launcher

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
fun wallpaperRes(lookId: String): Int? {
    // Windows 11 has no stock picture list of its own: its launcher uses the app's own Windows 11 wallpapers (the glowing disc first)
    val all = if (lookId == "win11") listOf(com.umutk.pulse.R.drawable.win11_wall_2, com.umutk.pulse.R.drawable.win11_wall_1, com.umutk.pulse.R.drawable.win11_wall_3, com.umutk.pulse.R.drawable.win11_wall_4,
        com.umutk.pulse.R.drawable.win11_wall_5, com.umutk.pulse.R.drawable.win11_wall_9, com.umutk.pulse.R.drawable.win11_wall_13) else wallsFor(lookId)
    return all.getOrNull(LauncherPrefs.wallIndex(lookId)) ?: all.firstOrNull()
}

/** The look's wallpaper behind the launcher, or [fallback] when the look has no stock picture. */
@Composable
fun LauncherWallpaper(lookId: String, fallback: Brush, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val res = wallpaperRes(lookId)
    val bmp = remember(res) { res?.let { BitmapFactory.decodeResource(ctx.resources, it, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() } }
    Box(modifier.fillMaxSize()) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize().background(fallback))
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
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(now))
    Text("$time\n$date", color = color, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.End, modifier = modifier)
}

/** Sets the look's picture as the phone wallpaper. False when there is no picture or the system refuses. */
fun setPhoneWallpaper(c: Context, lookId: String): Boolean = runCatching {
    val res = wallpaperRes(lookId) ?: return false
    val full = BitmapFactory.decodeResource(c.resources, res) ?: return false
    WallpaperManager.getInstance(c).setBitmap(full)
    true
}.getOrDefault(false)
