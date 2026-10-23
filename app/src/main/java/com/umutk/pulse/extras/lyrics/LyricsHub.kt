package com.umutk.pulse.extras.lyrics

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.tr

private sealed interface Page { object List : Page; object Search : Page; object Now : Page; class Open(val r: LyricsRecord) : Page }

/** Full page: saved lyrics, online search (LyricsScreen), and the lyrics of the playing track. */
@Composable
fun LyricsHub(onBack: () -> Unit) = com.umutk.pulse.WithPlayerFont { LyricsHubBody(onBack) }

@Composable
private fun LyricsHubBody(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val engine = MusicPlayerEngine.get(ctx)
    val st by engine.state.collectAsState()
    val store = remember { LyricsStore(ctx) }
    var page by remember { mutableStateOf<Page>(Page.List) }
    var items by remember { mutableStateOf(store.list()) }
    BackHandler { if (page is Page.List) onBack() else { page = Page.List; items = store.list() } }
    when (val p = page) {
        Page.List -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { PIcon("back", T.text, 22.dp) }
                Spacer(Modifier.weight(1f))
                if (st.current != null) IconButton(onClick = { page = Page.Now }) { PIcon("volume", T.text, 22.dp) }
                IconButton(onClick = { page = Page.Search }) { PIcon("search", T.text, 22.dp) }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { r ->
                    PCard(Modifier.fillMaxWidth().clickable { page = Page.Open(r) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.trackName, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(r.artistName, color = T.sub, fontSize = 13.sp)
                            }
                            IconButton(onClick = { store.delete(r, null); items = store.list() }) { PIcon("delete", T.text, 20.dp) }
                        }
                    }
                }
            }
        }
        Page.Search -> LyricsScreen("", "", null, null, { 0L }, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), { page = Page.List; items = store.list() }, store)
        Page.Now -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row { IconButton(onClick = { page = Page.List; items = store.list() }) { PIcon("back", T.text, 22.dp) } }
            val pos by produceState(0) { while (true) { value = engine.positionMs; delay(250) } }
            LyricsPanel(st.current, pos, Modifier.weight(1f).fillMaxWidth(), engine::seekTo)
        }
        is Page.Open -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row { IconButton(onClick = { page = Page.List }) { PIcon("back", T.text, 22.dp) } }
            LyricsBody(p.r, 0, null, Modifier.weight(1f).fillMaxWidth())
        }
    }
}
