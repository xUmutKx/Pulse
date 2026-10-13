package com.umutk.pulse.features

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.umutk.pulse.R

/** The picture behind the Windows 11 windows: the phone wallpaper, the opening picture or your own, decoded once per choice (not animated). Off = plain colours. */
object Backdrop {
    /** Default: off, plain colours. The wallpapers are picked in Settings when wanted. */
    var mode by mutableStateOf("off")
    /** Where the picture shows: "app" (behind the whole app, Windows 11 only) or "downloads" (a strip at the top of the Downloads page). */
    var scope by mutableStateOf("downloads")
    var image by mutableStateOf<ImageBitmap?>(null)
    /** Blur of the picture behind the whole app, in dp (0 = sharp). */
    var blur by mutableStateOf(24f)
    /** Which part of the picture shows when it is cropped to the screen: "top", "center" or "bottom". */
    var align by mutableStateOf("center")
    private var custom: String? = null
    private const val PREFS = "p"
    /** The 16 bundled Windows 11 wallpapers (from the uploaded pack), named pack1..pack16 in the settings. */
    private val PACK = intArrayOf(R.drawable.win11_wall_1, R.drawable.win11_wall_2, R.drawable.win11_wall_3, R.drawable.win11_wall_4, R.drawable.win11_wall_5, R.drawable.win11_wall_6, R.drawable.win11_wall_7, R.drawable.win11_wall_8, R.drawable.win11_wall_9, R.drawable.win11_wall_10, R.drawable.win11_wall_11, R.drawable.win11_wall_12, R.drawable.win11_wall_13, R.drawable.win11_wall_14, R.drawable.win11_wall_15, R.drawable.win11_wall_16)

    fun load(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = p.getString("backdrop", "off") ?: "off"
        scope = p.getString("backdrop_scope", "downloads") ?: "downloads"
        custom = p.getString("backdrop_uri", null)
        blur = p.getFloat("backdrop_blur", 24f)
        align = p.getString("backdrop_align", "center") ?: "center"
        image = render(c)
    }

    /** Saves the choice and renders the blurred picture; call off the main thread. */
    fun set(c: Context, m: String, uri: Uri? = null) {
        if (uri != null) runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        custom = uri?.toString() ?: custom
        mode = m
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("backdrop", m).putString("backdrop_uri", custom).apply()
        image = render(c)
    }

    private fun source(c: Context): Bitmap? = when (mode) {
        "phone" -> runCatching { (WallpaperManager.getInstance(c).drawable as? BitmapDrawable)?.bitmap }.getOrNull()
        "opening" -> BitmapFactory.decodeResource(c.resources, R.drawable.splash_win11_wallpaper)
        "custom" -> custom?.let { runCatching { c.contentResolver.openInputStream(Uri.parse(it))?.use { s -> BitmapFactory.decodeStream(s) } }.getOrNull() }
        else -> if (mode.startsWith("pack")) mode.removePrefix("pack").toIntOrNull()?.let { i -> BitmapFactory.decodeResource(c.resources, PACK[(i - 1).coerceIn(0, PACK.size - 1)]) } else null
    }

    /** Blur by shrinking to a sixteenth and stretching back: soft, cheap, drawn once per choice. */
    private fun render(c: Context): ImageBitmap? {
        val b = source(c) ?: return null
        val small = Bitmap.createScaledBitmap(b, (b.width / 16).coerceAtLeast(1), (b.height / 16).coerceAtLeast(1), true)
        return Bitmap.createScaledBitmap(small, 960, 600, true).asImageBitmap()
    }

    fun saveBlur(c: Context) { c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("backdrop_blur", blur).apply() }

    fun setAlign(c: Context, v: String) { align = v; c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("backdrop_align", v).apply() }

    fun alignment(): androidx.compose.ui.Alignment = when (align) {
        "top" -> androidx.compose.ui.Alignment.TopCenter
        "bottom" -> androidx.compose.ui.Alignment.BottomCenter
        else -> androidx.compose.ui.Alignment.Center
    }

    fun setScope(c: Context, v: String) { scope = v; c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("backdrop_scope", v).apply() }
}
