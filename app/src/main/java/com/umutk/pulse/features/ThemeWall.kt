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
private fun desktopWalls(lookId: String): List<Int> = when (lookId) {
    "xp" -> listOf(R.drawable.splash_xp_bliss, R.drawable.wall_xp_2, R.drawable.wall_xp_3, R.drawable.wall_xp_4, R.drawable.wall_xp_5, R.drawable.wall_xp_6, R.drawable.wall_xp_7, R.drawable.wall_xp_8, R.drawable.wall_grass)
    "win7" -> listOf(R.drawable.splash_win7_wallpaper, R.drawable.wall_w7_2, R.drawable.wall_w7_3, R.drawable.wall_w7_4, R.drawable.wall_w7_5, R.drawable.wall_w7_6, R.drawable.wall_w7_7, R.drawable.wall_w7_8, R.drawable.wall_w7_9, R.drawable.wall_w7_10, R.drawable.wall_w7_11)
    "win95" -> listOf(R.drawable.wall_w95_0, R.drawable.wall_w95_1, R.drawable.wall_w95_2, R.drawable.wall_w95_3)
    "win11" -> listOf(R.drawable.win11_wall_1, R.drawable.win11_wall_2, R.drawable.win11_wall_3, R.drawable.win11_wall_4, R.drawable.win11_wall_5, R.drawable.win11_wall_6, R.drawable.win11_wall_7, R.drawable.win11_wall_8,
        R.drawable.win11_wall_9, R.drawable.win11_wall_10, R.drawable.win11_wall_11, R.drawable.win11_wall_12, R.drawable.win11_wall_13, R.drawable.win11_wall_14, R.drawable.win11_wall_15, R.drawable.win11_wall_16, R.drawable.win11_wall_17)
    "win10" -> listOf(R.drawable.wall_w10_1, R.drawable.wall_w10_2, R.drawable.wall_w10_3, R.drawable.wall_w10_4, R.drawable.wall_w10_5)
    else -> emptyList()
}

/** Portrait phone pictures added to the looks that have them (1080 wide at most; the desktop ones above stay for a rotated screen). */
private fun mobileWalls(lookId: String): List<Int> = when (lookId) {
    "win11" -> listOf(R.drawable.mw11_1, R.drawable.mw11_2, R.drawable.mw11_3, R.drawable.mw11_4, R.drawable.mw11_5, R.drawable.mw11_6, R.drawable.mw11_7, R.drawable.mw11_8, R.drawable.mw11_9, R.drawable.mw11_10, R.drawable.mw11_11, R.drawable.mw11_12, R.drawable.mw11_13, R.drawable.mw11_14, R.drawable.mw11_15, R.drawable.mw11_16, R.drawable.mw11_17, R.drawable.mw11_18)
    "win10" -> listOf(R.drawable.mw10_1, R.drawable.mw10_2, R.drawable.mw10_3, R.drawable.mw10_4, R.drawable.mw10_5, R.drawable.mw10_6, R.drawable.mw10_7, R.drawable.mw10_8, R.drawable.mw10_9, R.drawable.mw10_10, R.drawable.mwx_dune, R.drawable.mwx_coaster)
    "win7" -> listOf(R.drawable.mwx_w7logo)
    "xp" -> listOf(R.drawable.mwx_bliss)
    else -> emptyList()
}

/** Every stock picture of a look. Windows 11 lists its phone pictures first (its default is portrait); the others keep the desktop picture first. */
fun wallsFor(lookId: String): List<Int> = if (lookId == "win11") mobileWalls(lookId) + desktopWalls(lookId) else desktopWalls(lookId) + mobileWalls(lookId)

/** Windows 11: the phone picture n (1-18) and the desktop picture w (1-17) that show the same artwork, so a turned screen swaps one for the other. 17 is the blue bloom, the default. */
private val WIN11_PHONE_TO_WIDE = mapOf(1 to 17, 4 to 10, 6 to 6, 7 to 16, 8 to 13, 9 to 1, 10 to 14, 13 to 15, 14 to 12, 17 to 9, 18 to 5)

/** Index in [wallsFor]("win11") of the picture that goes with picture [i] (phone <-> desktop), or null when it has no partner. */
fun win11Pair(i: Int): Int? {
    val phones = mobileWalls("win11").size
    return if (i < phones) WIN11_PHONE_TO_WIDE[i + 1]?.let { phones + it - 1 }
    else WIN11_PHONE_TO_WIDE.entries.firstOrNull { it.value == i - phones + 1 }?.let { it.key - 1 }
}

/** True when picture [i] of [wallsFor] is a portrait phone picture (a rotated screen uses a desktop one instead). */
fun isMobileWall(lookId: String, i: Int): Boolean = if (lookId == "win11") i < mobileWalls(lookId).size else i >= desktopWalls(lookId).size

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
    ThemeWallState.pos["$axis$lookId"] ?: appCtx?.getSharedPreferences("p", Context.MODE_PRIVATE)?.getFloat("wall${axis}_$lookId", if (axis == "z") 1f else .5f) ?: (if (axis == "z") 1f else .5f)

fun setThemeWallPos(c: Context, lookId: String, axis: String, v: Float) {
    ThemeWallState.pos["$axis$lookId"] = v
    c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putFloat("wall${axis}_$lookId", v).apply()
}

fun wallBias(lookId: String): BiasAlignment = BiasAlignment(themeWallPos(lookId, "x") * 2 - 1, themeWallPos(lookId, "y") * 2 - 1)

/** A look's wallpaper bitmap: the user's pick first, then the built-in one. Null for looks without a picture. */
@Composable
fun themeWallBitmap(lookId: String): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    val uri = themeWallpaper(lookId)
    val res = if (uri == null) builtinWall(lookId) else null
    return remember(uri, res) {
        runCatching {
            if (uri != null) ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
            else res?.let { BitmapFactory.decodeResource(ctx.resources, it, BitmapFactory.Options().apply { inSampleSize = 2 }) }
        }.getOrNull()?.asImageBitmap()
    }
}

/** A look's wallpaper, drawn with the position and zoom from the editor. Draws nothing for looks without a picture. */
@Composable
fun ThemeWallPicture(lookId: String, modifier: Modifier = Modifier) {
    val img = themeWallBitmap(lookId) ?: return
    val x = themeWallPos(lookId, "x"); val y = themeWallPos(lookId, "y"); val z = themeWallPos(lookId, "z")
    androidx.compose.foundation.Canvas(modifier) { with(Backdrop) { drawBackdrop(img, x, y, z) } }
}

/** Full-screen editor for a Windows 7 / XP wallpaper: drag and pinch, the result is saved per look. */
@Composable
fun ThemeWallEditor(lookId: String, onClose: () -> Unit) {
    val img = themeWallBitmap(lookId) ?: return
    val ctx = LocalContext.current
    CropEditor(img, themeWallPos(lookId, "x"), themeWallPos(lookId, "y"), themeWallPos(lookId, "z"), if (lookId == "win7") 14f else 0f, onClose) { x, y, z ->
        setThemeWallPos(ctx, lookId, "x", x); setThemeWallPos(ctx, lookId, "y", y); setThemeWallPos(ctx, lookId, "z", z)
    }
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
