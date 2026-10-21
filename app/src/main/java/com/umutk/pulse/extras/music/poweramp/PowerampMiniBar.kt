package com.umutk.pulse.extras.music.poweramp

import com.umutk.pulse.extras.music.shownArtist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr

/**
 * Thin now-playing bar above the tab pill: small cover and title on the left (tap opens the now-playing page),
 * play/pause on the right. Shown by the shell only while a track is loaded and a list page is open.
 */
@Composable
fun PowerampMiniBar(
    track: Track,
    art: ImageBitmap?,
    playing: Boolean,
    style: PowerampStyle,
    pill: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (pill) 8.dp else 0.dp)
            .let { if (pill) it.clip(RoundedCornerShape(16.dp)) else it }
            .height(56.dp).padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics { role = Role.Button }.clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(style.chip), contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxWidth().height(44.dp), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.displayName(), color = style.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.shownArtist(), color = style.textDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).semantics { role = Role.Button; contentDescription = if (playing) tr("Pause", "Duraklat") else tr("Play", "Oynat") }
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = style.text, modifier = Modifier.size(28.dp))
        }
    }
}
