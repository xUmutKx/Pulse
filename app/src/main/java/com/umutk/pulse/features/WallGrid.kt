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
import androidx.compose.foundation.verticalScroll
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
fun WallThumb(resId: Int, selected: Boolean, modifier: Modifier = Modifier, aspect: Float = 16f / 10f, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val img = remember(resId) {
        runCatching { BitmapFactory.decodeResource(ctx.resources, resId, BitmapFactory.Options().apply { inSampleSize = 16; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }) }.getOrNull()?.asImageBitmap()
    }
    val shape = RoundedCornerShape(if (T.bevel) 0.dp else 6.dp)
    Box(modifier.then(if (T.bevel) Modifier.background(T.bg).bevel(!selected).padding(3.dp) else Modifier)) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape).background(T.panel)
            .then(if (T.bevel) Modifier else Modifier.border(if (selected) 3.dp else 1.dp, if (selected) Look.accent else T.line, shape))
            .clickable(onClick = onClick)) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** The look's stock pictures as a grid of previews; [selected] is the index in wallsFor(lookId), -1 for none. Phone pictures stand upright in their own rows, desktop ones lie. */
@Composable
fun WallGrid(lookId: String, selected: Int, onPick: (Int) -> Unit) {
    val walls = wallsFor(lookId)
    val phone = walls.indices.filter { isMobileWall(lookId, it) }
    val desk = walls.indices.filter { !isMobileWall(lookId, it) }
    @Composable fun section(list: List<Int>, columns: Int, aspect: Float) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.chunked(columns).forEach { chunk ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { i -> WallThumb(walls[i], i == selected, Modifier.weight(1f), aspect) { onPick(i) } }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (phone.isNotEmpty()) {
            if (desk.isNotEmpty()) Text(tr("Phone", "Telefon"), color = T.sub, fontSize = 12.sp)
            section(phone, 4, 10f / 16f)
        }
        if (desk.isNotEmpty()) {
            if (phone.isNotEmpty()) Text(tr("Desktop", "Masaüstü"), color = T.sub, fontSize = 12.sp)
            section(desk, 3, 16f / 10f)
        }
    }
}

/** The launcher's wallpaper chooser: the preview grid in a window of the current look. */
@Composable
fun WallPickDialog(lookId: String, selected: Int, onDismiss: () -> Unit, onPickFile: ((java.io.File) -> Unit)? = null, onPick: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()
            .background(if (T.bevel) T.bg else T.panel)
            .then(if (T.bevel) Modifier.bevel(true).padding(3.dp) else Modifier.border(1.dp, T.line, RoundedCornerShape(T.radius)))
            .padding(16.dp)) {
            Text(tr("Wallpaper", "Duvar kağıdı"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val ctx = LocalContext.current
            androidx.compose.runtime.LaunchedEffect(lookId) { com.umutk.pulse.extras.store.HdWalls.packFor(lookId)?.let { com.umutk.pulse.extras.store.PackAuto.ensure(ctx, it.first) } }
            Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) { WallGrid(lookId, selected) { onPick(it) } }
            // the higher-quality versions of these pictures: a download, here where the wallpaper is chosen
            com.umutk.pulse.extras.store.HdWallpaperRow(lookId)
            if (onPickFile != null) com.umutk.pulse.extras.store.MoreWallpapersStrip(lookId, onPickFile)
        }
    }
}
