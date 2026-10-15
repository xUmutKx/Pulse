package com.umutk.pulse.features

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.umutk.pulse.R

/** Each look's own wallpaper (a content uri the user picked) and the credit of its built-in one. */
private var appCtx: Context? = null

/** Called once from MainActivity.onCreate, before the splash reads the picks. */
fun initThemeWall(c: Context) { appCtx = c.applicationContext }

/** The user's wallpaper for this look (lookId = Look.theme.id), or null for the built-in one. */
fun themeWallpaper(lookId: String): String? =
    appCtx?.getSharedPreferences("p", Context.MODE_PRIVATE)?.getString("wall_$lookId", null)

fun setThemeWallpaper(c: Context, lookId: String, uri: String?) {
    c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("wall_$lookId", uri).apply()
}

/** The stock pictures of each look's era; index 0 is the look's default. Windows 11 keeps its own A-D packs (Backdrop). */
fun wallsFor(lookId: String): List<Int> = when (lookId) {
    "xp" -> listOf(R.drawable.splash_xp_bliss, R.drawable.wall_xp_2, R.drawable.wall_xp_3, R.drawable.wall_xp_4, R.drawable.wall_xp_5, R.drawable.wall_xp_6, R.drawable.wall_xp_7, R.drawable.wall_xp_8)
    "win7" -> listOf(R.drawable.splash_win7_wallpaper, R.drawable.wall_w7_2, R.drawable.wall_w7_3, R.drawable.wall_w7_4, R.drawable.wall_w7_5, R.drawable.wall_w7_6, R.drawable.wall_w7_7, R.drawable.wall_w7_8, R.drawable.wall_w7_9, R.drawable.wall_w7_10, R.drawable.wall_w7_11)
    "win95" -> listOf(R.drawable.wall_w95_0, R.drawable.wall_w95_1, R.drawable.wall_w95_2, R.drawable.wall_w95_3)
    "win10" -> listOf(R.drawable.wall_w10_1, R.drawable.wall_w10_2, R.drawable.wall_w10_3, R.drawable.wall_w10_4, R.drawable.wall_w10_5)
    else -> emptyList()
}

/** Index of the picked stock picture in wallsFor(lookId); observable so the picker and the window update at once. */
object WallSel { val idx = mutableStateMapOf<String, Int>() }

fun wallSel(lookId: String): Int =
    WallSel.idx[lookId] ?: appCtx?.getSharedPreferences("p", Context.MODE_PRIVATE)?.getInt("wallsel_$lookId", 0) ?: 0

fun setWallSel(c: Context, lookId: String, i: Int) {
    WallSel.idx[lookId] = i
    c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putInt("wallsel_$lookId", i).apply()
}

/** The picked stock picture of the looks that have a list (Windows 7, XP, 95, 10), Windows 11's own, else none. */
fun builtinWall(lookId: String): Int? =
    if (lookId == "win11") R.drawable.splash_win11_wallpaper else wallsFor(lookId).let { it.getOrNull(wallSel(lookId)) ?: it.firstOrNull() }

/** Where the picture sits in its window, set by hand: x and y each 0..1, 0.5 is centred. The picture is cropped to fill, so this decides what shows. */
object ThemeWallState { val pos = mutableStateMapOf<String, Float>() }

fun themeWallPos(lookId: String, axis: String): Float =
    ThemeWallState.pos["$axis$lookId"] ?: appCtx?.getSharedPreferences("p", Context.MODE_PRIVATE)?.getFloat("wall${axis}_$lookId", .5f) ?: .5f

fun setThemeWallPos(c: Context, lookId: String, axis: String, v: Float) {
    ThemeWallState.pos["$axis$lookId"] = v
    c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putFloat("wall${axis}_$lookId", v).apply()
}

fun wallBias(lookId: String): BiasAlignment = BiasAlignment(themeWallPos(lookId, "x") * 2 - 1, themeWallPos(lookId, "y") * 2 - 1)

/** A look's wallpaper as a picture: the user's pick first, then the built-in one. Draws nothing for looks without a picture. */
@Composable
fun ThemeWallPicture(lookId: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val uri = themeWallpaper(lookId)
    val picked = uri?.let { u ->
        remember(u) {
            runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
            }.getOrNull()?.asImageBitmap()
        }
    }
    val bias = wallBias(lookId)
    if (picked != null) Image(picked, null, modifier, contentScale = ContentScale.Crop, alignment = bias)
    else builtinWall(lookId)?.let { Image(painterResource(it), null, modifier, contentScale = ContentScale.Crop, alignment = bias) }
}

/** Material's own backdrop, drawn: a pale surface with soft pastel circles. */
@Composable
fun MaterialBackdrop(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF3EDF7), androidx.compose.ui.graphics.Color(0xFFEADDFF))))
        drawCircle(androidx.compose.ui.graphics.Color(0x66B5EAD7), radius = size.minDimension * .42f, center = androidx.compose.ui.geometry.Offset(size.width * .15f, size.height * .2f))
        drawCircle(androidx.compose.ui.graphics.Color(0x66FFDAC1), radius = size.minDimension * .36f, center = androidx.compose.ui.geometry.Offset(size.width * .9f, size.height * .35f))
        drawCircle(androidx.compose.ui.graphics.Color(0x66C7CEEA), radius = size.minDimension * .5f, center = androidx.compose.ui.geometry.Offset(size.width * .4f, size.height * .95f))
    }
}
