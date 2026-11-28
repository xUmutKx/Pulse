package com.umutk.pulse.features

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The search page of Files: one field, the names of the whole phone searched while typing (the Rust walk of [FileSearch], a moment after the last key), and under an empty field
 * the searches typed before. A tap on a result goes to the folder or opens the file ([onOpen]).
 */
@Composable
fun FilesSearchPage(root: File, showHidden: Boolean, onOpen: (File) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<File>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(FileSearch.history(ctx)) }
    val focus = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    LaunchedEffect(query, showHidden) {
        if (query.trim().length < 2) { results = null; busy = false; return@LaunchedEffect }
        delay(250)
        busy = true
        results = withContext(Dispatchers.IO) { FileSearch.find(root, query, showHidden) }
        busy = false
    }
    fun open(f: File) { FileSearch.remember(ctx, query); history = FileSearch.history(ctx); onOpen(f) }
    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            BasicTextField(
                query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 16.sp), cursorBrush = SolidColor(T.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { FileSearch.remember(ctx, query); history = FileSearch.history(ctx) }),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(T.panel).padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(focus),
                decorationBox = { inner -> if (query.isEmpty()) Text(tr("Search the whole phone", "Tüm telefonda ara"), color = T.sub, fontSize = 16.sp); inner() },
            )
            if (query.isNotEmpty()) Box(Modifier.size(40.dp).clickable { query = "" }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 20.dp) }
        }
        val r = results
        when {
            query.trim().length < 2 -> LazyColumn(Modifier.fillMaxSize()) {
                if (history.isNotEmpty()) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("History", "Geçmiş"), color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        PIcon("delete", T.sub, 18.dp, Modifier.clickable { FileSearch.forget(ctx); history = emptyList() })
                    }
                }
                items(history.distinct(), key = { it }) { h ->
                    Row(Modifier.fillMaxWidth().clickable { query = h }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("history", T.sub, 20.dp); Spacer(Modifier.width(14.dp))
                        Text(h, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(Modifier.size(36.dp).clickable { FileSearch.forget(ctx, h); history = FileSearch.history(ctx) }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 16.dp) }
                    }
                }
            }
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Text(if (busy) tr("Searching…", "Aranıyor…") else "", color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp)) }
            r.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Text(if (busy) tr("Searching…", "Aranıyor…") else tr("Nothing found", "Bulunamadı"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 24.dp)) }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(r, key = { it.path }) { f ->
                    Row(Modifier.fillMaxWidth().clickable { open(f) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon(if (f.isDirectory) "folder" else "details", T.text, 22.dp); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.parent.orEmpty().removePrefix(root.path), color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
