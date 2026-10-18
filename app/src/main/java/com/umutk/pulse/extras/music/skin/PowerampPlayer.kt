package com.umutk.pulse.extras.music.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Poweramp-style now-playing screen. Background follows [SkinLook.background]: black, a blurred album
 * cover, or a solid colour. [progress] is 0..1. Transport buttons are drawn with shapes.
 */
@Composable
fun PowerampPlayer(
    look: SkinLook,
    title: String,
    artist: String,
    art: ImageBitmap?,
    playing: Boolean,
    progress: Float,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tk = look.tokens
    // Text follows the backdrop, not the window look: a black Win 95 text colour would vanish on black.
    val fg = if (look.background == SkinBackground.SOLID && look.solid.luminance() > .5f) Color.Black else Color.White
    val sub = fg.copy(alpha = .7f)
    val column = Modifier.fillMaxWidth().widthIn(max = 360.dp)
    Box(modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(if (look.background == SkinBackground.SOLID) look.solid else Color.Black))
        if (look.background == SkinBackground.ALBUM_BLUR && art != null) {
            Image(art, null, Modifier.matchParentSize().blur(32.dp), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)))
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                column.aspectRatio(1f).clip(RoundedCornerShape(tk.radius)).background(tk.panel),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(tk.accent, radius = size.minDimension * .3f) }
                }
            }
            Column(column, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Canvas(column.height(4.dp)) {
                drawRect(fg.copy(alpha = .25f))
                drawRect(tk.accent, size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                TransportBtn(Transport.PREV, fg, onPrev)
                TransportBtn(if (playing) Transport.PAUSE else Transport.PLAY, fg, onPlayPause)
                TransportBtn(Transport.NEXT, fg, onNext)
            }
        }
    }
}

@Composable
private fun TransportBtn(t: Transport, fg: Color, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(fg.copy(alpha = .12f))
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { transportGlyph(t, fg) }
    }
}
