package com.umutk.pulse.extras.ytx

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Opens a playlist over whatever page is showing (a channel's Playlists tab, the playlist search). */
val LocalPlaylistOpener = staticCompositionLocalOf<(PlaylistRef) -> Unit> { {} }

/** One playlist as a row: its picture, title and the line under it (the number of videos, or the channel). */
@Composable
fun PlaylistRow(pl: PlaylistRef, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(pl.thumbnail, Modifier.width(150.dp).aspectRatio(16f / 9f), radius = 14)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pl.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            pl.subtitle?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A row of small choices (the order of a channel's videos): the picked one filled. */
@Composable
fun ChoiceChips(names: List<String>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { i, n ->
            val on = i == selected
            Text(
                n,
                color = if (on) c.surface else c.onSurface,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(if (on) c.onSurface else c.surfaceContainerHigh).pressable { onPick(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * Series and movies, opened from YTXTube inside YTXTube's own task: Back returns to YTXTube, and the recent apps show YTXTube (a window of its own, as before,
 * sent Back to the home screen and showed up there as a Pulse window). In the black AMOLED look.
 */
fun openSeriesPage(ctx: android.content.Context) {
    runCatching {
        val i = android.content.Intent(ctx, com.umutk.pulse.YtxSeriesActivity::class.java).putExtra("route", "series").putExtra("look", "amoled")
        // the window the page is drawn in may be wrapped (a themed context): the activity under it starts the page, so it joins YTXTube's task
        var c = ctx
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        if (c is android.app.Activity) c.startActivity(i) else { i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i) }
    }
}
