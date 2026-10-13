package com.umutk.pulse.features

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.GlassRoot
import com.umutk.pulse.GlassStyle
import com.umutk.pulse.GlassSurface
import com.umutk.pulse.T

/** Glass settings: how soft the blur is and how strong the tint is. Saved with the Backdrop prefs. */
object GlassPrefs {
    /** Shrink factor of the blurred copy (GlassRoot blurDiv): 4 is sharp, 40 is very soft. */
    var blur by mutableStateOf(12)
    /** Tint over the blurred backdrop, 0..1. */
    var tint by mutableStateOf(0.2f)
    private const val PREFS = "p"

    fun load(c: Context) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        blur = p.getInt("glass_blur", 12).coerceIn(4, 40)
        tint = p.getFloat("glass_tint", 0.2f).coerceIn(0f, 1f)
    }

    fun save(c: Context) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("glass_blur", blur).putFloat("glass_tint", tint).apply()
    }

    fun style(): GlassStyle = GlassStyle(tintAlpha = tint)
}

/** Live preview for the glass settings: a small wallpaper with a glass panel on it. Blurred once per setting change, not per frame. */
@Composable
fun GlassPreview() {
    GlassRoot(
        wallpaperKey = "glass-preview",
        modifier = Modifier.fillMaxWidth().height(110.dp),
        blurDiv = GlassPrefs.blur,
        wallpaper = {
            drawRect(Color(0xFF12506B))
            drawCircle(Color(0xFFFFD6E8), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.2f, size.height * 0.35f))
            drawCircle(Color(0xFF3B1F6B), radius = size.minDimension * 0.45f, center = Offset(size.width * 0.85f, size.height * 0.8f))
            drawCircle(Color(0xFFF9C6DD), radius = size.minDimension * 0.2f, center = Offset(size.width * 0.6f, size.height * 0.2f))
        },
    ) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            GlassSurface(Modifier.fillMaxSize(), GlassPrefs.style(), contentAlignment = Alignment.Center) {
                Text("Glass ${GlassPrefs.blur} / ${(GlassPrefs.tint * 100).toInt()}%", color = T.text, fontSize = 14.sp)
            }
        }
    }
}
