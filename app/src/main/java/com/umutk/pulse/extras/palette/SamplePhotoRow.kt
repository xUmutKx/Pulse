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
import com.umutk.pulse.PLoader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import com.umutk.pulse.extras.palette.core.photo.SamplePhotos
import kotlinx.coroutines.launch

/**
 * Horizontal strip of real photos (LoremFlickr — no key/auth, just a tagged
 * URL fetch), tag-matched to [palette]'s mood. Tapping a thumbnail downloads
 * the full-resolution version and hands it to [onPicked].
 */
@Composable
fun SamplePhotoRow(palette: ColorPalette, onPicked: (Bitmap) -> Unit) {
    val photos = remember(palette.id) { SamplePhotos.suggestionsFor(palette).take(12) }
    var loadingKey by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { "${it.tags}-${it.lock}" }) { photo ->
            val key = "${photo.tags}-${photo.lock}"
            var thumb by remember(key) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(key) { thumb = SamplePhotos.fetchBitmap(photo.thumbUrl()) }

            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(com.umutk.pulse.T.panel)
                    .clickable(enabled = loadingKey == null) {
                        loadingKey = key
                        scope.launch {
                            SamplePhotos.fetchBitmap(photo.fullUrl())?.let(onPicked)
                            loadingKey = null
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                thumb?.let {
                    Image(
                        it.asImageBitmap(), null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp))
                    )
                }
                if (loadingKey == key) PLoader(Modifier.size(24.dp))
            }
        }
    }
}
