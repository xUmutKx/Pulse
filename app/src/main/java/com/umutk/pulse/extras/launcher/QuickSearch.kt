package com.umutk.pulse.extras.launcher

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/**
 * The quick search of the home: a swipe down opens it over the home. Empty, it shows the apps opened last; typed in, it lists the apps that match
 * and offers the web search. The window is drawn in the colours of the look.
 */
@Composable
fun QuickSearch(state: LauncherState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val recents = remember(state.apps) { LauncherPrefs.usage.entries.sortedByDescending { it.value }.mapNotNull { e -> state.apps.firstOrNull { it.pkg == e.key } }.take(8) }
    val found = remember(q, state.apps) { if (q.isBlank()) emptyList() else state.apps.filter { it.label.contains(q.trim(), ignoreCase = true) }.take(30) }
    val none = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable(interactionSource = none, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp).widthIn(max = 540.dp).fillMaxWidth()
                .background(T.bg).border(1.dp, T.line).clickable(interactionSource = none, indication = null) {}.padding(12.dp),
        ) {
            PField(q, { q = it }, tr("Search apps and the web", "Uygulama ve web'de ara"), Modifier.fillMaxWidth().focusRequester(focus), onDone = {
                found.firstOrNull()?.let { state.open(it); onClose() }
            })
            if (q.isBlank()) {
                if (recents.isNotEmpty()) {
                    Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        recents.forEach { app ->
                            Column(Modifier.width(64.dp).clickable { state.open(app); onClose() }, horizontalAlignment = Alignment.CenterHorizontally) {
                                LaunchAppPicture(app, 44.dp, T.panel, T.text)
                                Text(app.label, color = T.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            } else LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                items(found, key = { it.pkg + it.label }) { app ->
                    Row(Modifier.fillMaxWidth().clickable { state.open(app); onClose() }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        LaunchAppPicture(app, 36.dp, T.panel, T.text)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().clickable {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        onClose()
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("search", T.text, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(tr("Search the web for \"${q.trim()}\"", "\"${q.trim()}\" için webde ara"), color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
