package com.umutk.pulse.extras.palette

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.palette.core.icon.WallpaperStyle
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import com.umutk.pulse.extras.palette.core.wallpaper.WallpaperGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstract art rendered on-device, straight from [palette] — unlike a stock
 * photo (which can only approximate a mood), these are guaranteed to share
 * the exact same colours, and need no network or API of any kind.
 */
@Composable
fun AbstractPaletteArtRow(palette: ColorPalette, onPicked: (Bitmap) -> Unit) {
    val variants = remember(palette.id) {
        WallpaperStyle.entries.flatMap { style -> listOf(style to 1L, style to 7L) }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(variants, key = { "${it.first}-${it.second}" }) { (style, seed) ->
            var thumb by remember(palette.id, style, seed) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(palette.id, style, seed) {
                thumb = withContext(Dispatchers.Default) {
                    WallpaperGenerator.generate(palette, style, 200, 200, seed)
                }
            }
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(com.umutk.pulse.T.panel)
                    .clickable(enabled = thumb != null) { thumb?.let(onPicked) }
            ) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(), null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
                    )
                }
            }
        }
    }
}
