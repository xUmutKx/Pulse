package com.umutk.pulse.extras.music.winamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.skin.PH_UP
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.Zone
import com.umutk.pulse.extras.music.skin.blit
import com.umutk.pulse.extras.music.skin.skinZones

private const val Z_CLOSE = 1
private const val Z_ADD = 2
private const val Z_REM = 3

/**
 * Playlist window, 275 skin pixels wide, framed with the pieces of pledit.bmp and listed in the colours of
 * pledit.txt. Tapping a row plays it through [onPick]. ADD calls [onAdd], REM calls [onRemove] with the playing index.
 * Rows keep a comfortable touch height whatever the scale. [scaleDp] above 0 overrides [scale] (dp per skin pixel).
 */
@Composable
fun WinampPlaylistWindow(
    skin: WinampSkin,
    tracks: List<Track>,
    current: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Int = 1,
    heightSkin: Int = 232,
    scaleDp: Float = 0f,
    onAdd: () -> Unit = {},
    onRemove: (Int) -> Unit = {},
) {
    val dpPer = if (scaleDp > 0f) scaleDp else scale.toFloat()
    val sc = with(LocalDensity.current) { dpPer.dp.toPx() }
    val c = skin.playlist
    var pressed by remember { mutableIntStateOf(-1) }
    val list = rememberLazyListState()
    val bottom = heightSkin - 38
    val zones = remember(heightSkin) {
        listOf(
            Zone(Z_CLOSE, 250f, 0f, 25f, 20f),
            Zone(Z_ADD, 11f, bottom + 9f, 25f, 18f, padY = 4f),
            Zone(Z_REM, 40f, bottom + 9f, 25f, 18f, padY = 4f),
        )
    }
    Box(modifier.size((WINAMP_W * dpPer).dp, (heightSkin * dpPer).dp)) {
        Canvas(
            Modifier.fillMaxSize().skinZones(sc, zones, { pressed = it }) { id, _, ph ->
                if (ph == PH_UP) when (id) {
                    Z_CLOSE -> onClose()
                    Z_ADD -> onAdd()
                    Z_REM -> if (current in tracks.indices) onRemove(current)
                }
            },
        ) {
            val u = size.width / WINAMP_W
            val pl = skin.pledit
            // top: tiles across, the title piece centred, corners at both ends
            var x = 25
            while (x < 250) { blit(pl, 127, 0, minOf(25, 250 - x), 20, x.toFloat(), 0f, u); x += 25 }
            blit(pl, 0, 0, 25, 20, 0f, 0f, u)
            blit(pl, 26, 0, 100, 20, (WINAMP_W - 100) / 2f, 0f, u)
            blit(pl, 153, 0, 25, 20, 250f, 0f, u)
            // sides
            var y = 20
            while (y < bottom) {
                val h = minOf(29, bottom - y)
                blit(pl, 0, 42, 12, h, 0f, y.toFloat(), u)
                blit(pl, 31, 42, 20, h, 255f, y.toFloat(), u)
                y += 29
            }
            // bottom: 125 + 150 = 275
            blit(pl, 0, 72, 125, 38, 0f, bottom.toFloat(), u)
            blit(pl, 126, 72, 150, 38, 125f, bottom.toFloat(), u)
            // scroll thumb follows the list
            val total = tracks.size
            val vis = list.layoutInfo.visibleItemsInfo.size
            val frac = if (total > vis && vis > 0) list.firstVisibleItemIndex.toFloat() / (total - vis) else 0f
            blit(pl, 52, 53, 8, 18, 263f, 20f + frac.coerceIn(0f, 1f) * (bottom - 20 - 18), u)
            val tint = pressed
            if (tint == Z_ADD || tint == Z_REM) {
                val zx = if (tint == Z_ADD) 11f else 40f
                drawRect(Color(0x55000000), Offset(zx * u, (bottom + 9) * u), Size(25 * u, 18 * u))
            }
        }
        val rowMin = maxOf(14f * dpPer, 32f)
        LazyColumn(
            state = list,
            modifier = Modifier.padding(start = (12 * dpPer).dp, top = (20 * dpPer).dp, end = (20 * dpPer).dp, bottom = (38 * dpPer).dp)
                .fillMaxSize().background(c.normalBg),
        ) {
            itemsIndexed(tracks) { i, t ->
                val on = i == current
                Row(
                    Modifier.fillMaxWidth().heightIn(min = rowMin.dp)
                        .background(if (on) c.selectedBg else c.normalBg)
                        .clickable { onPick(i) }.padding(horizontal = (3 * dpPer).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val col = if (on) c.current else c.normal
                    val fs = maxOf(9f * dpPer, 12f).sp
                    Text(
                        "${i + 1}. ${trackLabel(t)}", color = col, fontFamily = FontFamily.SansSerif, fontSize = fs,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (t.durationSec >= 0) Text(formatTime(t.durationSec * 1000), color = col, fontFamily = FontFamily.SansSerif, fontSize = fs, maxLines = 1)
                }
            }
        }
    }
}
