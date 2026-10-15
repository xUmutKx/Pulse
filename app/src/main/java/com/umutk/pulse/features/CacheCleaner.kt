package com.umutk.pulse.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import com.umutk.pulse.WinAlert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import com.umutk.pulse.PLoader
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Cleaner
import com.umutk.pulse.Guard
import com.umutk.pulse.Junk
import com.umutk.pulse.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val tabs = listOf("Apps", "Leftovers", "System")

@Composable
private fun CacheCleanerContent(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<List<List<CleanRow>?>>(listOf(null, null, null)) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf("") }

    fun scan() {
        scope.launch {
            data = listOf(null, null, null)
            selected = emptySet()
            for (i in 0..2) {
                val r = withContext(Dispatchers.IO) {
                    runCatching { when (i) { 0 -> CleanerRules.appJunk(ctx); 1 -> CleanerRules.corpses(ctx); else -> CleanerRules.system() } }.getOrDefault(emptyList())
                }
                data = data.toMutableList().also { it[i] = r }
            }
        }
    }
    LaunchedEffect(Unit) { scan() }

    val list = data[tab].orEmpty()
    // Selection keys carry the tab so a row id never clashes between categories.
    fun key(t: Int, r: CleanRow) = "$t/${r.id}"
    val chosen = list.filter { key(tab, it) in selected }
    val chosenBytes = chosen.sumOf { it.size }

    fun deleteChosen() {
        val targets = chosen.flatMap { it.items }
        val rows = chosen.size
        val t = tab
        confirm = false
        scope.launch {
            var freed = 0L
            targets.forEachIndexed { i, f ->
                progress = i to targets.size
                freed += withContext(Dispatchers.IO) { runCatching { CleanerRules.delete(ctx, f) }.getOrDefault(0L) }
            }
            progress = null
            result = "Freed ${Guard.gb(freed)} from $rows items"
            scan()
            tab = t
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Cache cleaner", style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, name ->
                val rows = data[i]
                com.umutk.pulse.PChip(tab == i, if (rows == null) name else "$name ${Guard.gb(rows.sumOf { it.size })}") { tab = i }
            }
        }
        Text(
            "Reclaimable: ${Guard.gb(list.sumOf { it.size })} in ${list.size} items",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Selected: ${Guard.gb(chosenBytes)} in ${chosen.size} items",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val all = list.isNotEmpty() && chosen.size == list.size
            TextButton(
                enabled = list.isNotEmpty() && progress == null,
                onClick = {
                    val keys = list.map { key(tab, it) }.toSet()
                    selected = if (all) selected - keys else selected + keys
                },
            ) { Text(if (all) "Select none" else "Select all") }
            Button(
                onClick = { confirm = true },
                enabled = chosen.isNotEmpty() && progress == null,
            ) { Text("Clean selected") }
        }

        progress?.let { (done, total) ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                com.umutk.pulse.Busy(if (total == 0) 0f else done.toFloat() / total)
                Text("Cleaning ${done + 1} of $total", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (result.isNotEmpty() && progress == null) {
            Text(result, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }

        when {
            data[tab] == null -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                PLoader(Modifier.padding(end = 12.dp))
                Text("Scanning…")
            }
            list.isEmpty() -> Text(
                if (tab == 0 && Shell.root != true) "Nothing found. The app needs all-files access to read Android/data." else "Nothing found.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items(list, key = { key(tab, it) }) { row ->
                    val k = key(tab, row)
                    val checked = k in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = progress == null) {
                                selected = if (checked) selected - k else selected + k
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = progress == null)
                        if (row.pkg != null) AppIcon(row.pkg, 36.dp) else Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.sub, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(Guard.gb(row.size), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (confirm) {
        WinAlert(
            onDismissRequest = { confirm = false },
            title = { Text("Clean ${chosen.size} items?") },
            text = { Text(if (tab == 0) "Frees about ${Guard.gb(chosenBytes)}. Only cache and re-creatable junk folders are emptied; app data is kept." else "Frees about ${Guard.gb(chosenBytes)}. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { deleteChosen() }) { Text("Clean") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Storage screens sit on the transparent page, so their text is white rather than the theme's dark text. */
@Composable
fun CacheCleanerScreen(onBack: () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides com.umutk.pulse.T.text) { CacheCleanerContent(onBack) }
}
