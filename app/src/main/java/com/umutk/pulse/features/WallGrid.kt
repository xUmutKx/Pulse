package com.umutk.pulse.features

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.umutk.pulse.Look
import com.umutk.pulse.T
import com.umutk.pulse.bevel
import com.umutk.pulse.tr

/** One stock picture as a thumbnail tile: sampled down once, sunken (95) or accent-edged when [selected]. */
@Composable
fun WallThumb(resId: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val img = remember(resId) {
        runCatching { BitmapFactory.decodeResource(ctx.resources, resId, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull()?.asImageBitmap()
    }
    val shape = RoundedCornerShape(if (T.bevel) 0.dp else 6.dp)
    Box(modifier.then(if (T.bevel) Modifier.background(T.bg).bevel(!selected).padding(3.dp) else Modifier)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(shape).background(T.panel)
            .then(if (T.bevel) Modifier else Modifier.border(if (selected) 3.dp else 1.dp, if (selected) Look.accent else T.line, shape))
            .clickable(onClick = onClick)) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** The look's stock pictures as a grid of previews; [selected] is the index in wallsFor(lookId), -1 for none. */
@Composable
fun WallGrid(lookId: String, selected: Int, onPick: (Int) -> Unit) {
    val columns = 3
    val walls = wallsFor(lookId)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        walls.chunked(columns).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEachIndexed { col, res ->
                    val i = row * columns + col
                    WallThumb(res, i == selected, Modifier.weight(1f)) { onPick(i) }
                }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The launcher's wallpaper chooser: the preview grid in a window of the current look. */
@Composable
fun WallPickDialog(lookId: String, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()
            .background(if (T.bevel) T.bg else T.panel)
            .then(if (T.bevel) Modifier.bevel(true).padding(3.dp) else Modifier.border(1.dp, T.line, RoundedCornerShape(T.radius)))
            .padding(16.dp)) {
            Text(tr("Wallpaper", "Duvar kağıdı"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            WallGrid(lookId, selected) { onPick(it) }
        }
    }
}
