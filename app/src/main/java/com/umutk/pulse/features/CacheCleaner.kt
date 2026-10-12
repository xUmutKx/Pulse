package com.umutk.pulse.features

import android.content.pm.PackageManager
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

/** One app cache. [id] (the folder path) tells apart the same app in two users. */
private class CacheApp(val id: String, val pkg: String, val label: String, val junk: Junk)

@Composable
fun CacheCleanerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<CacheApp>?>(null) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirm by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf("") }

    fun scan() {
        scope.launch {
            apps = null
            selected = emptySet()
            apps = withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                Cleaner.caches().map { j ->
                    val pkg = j.file.parentFile?.name.orEmpty()
                    CacheApp(j.file.path, pkg, labelOf(pm, pkg), j)
                }
            }
        }
    }
    LaunchedEffect(Unit) { scan() }

    val list = apps.orEmpty()
    val chosen = list.filter { it.id in selected }
    val chosenBytes = chosen.sumOf { it.junk.size }

    fun deleteChosen() {
        val targets = chosen
        confirm = false
        scope.launch {
            var freed = 0L
            targets.forEachIndexed { i, app ->
                progress = i to targets.size
                freed += withContext(Dispatchers.IO) { Cleaner.clearCache(app.junk) }
            }
            progress = null
            result = "Freed ${Guard.gb(freed)} from ${targets.size} apps"
            scan()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Cache cleaner", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Reclaimable: ${Guard.gb(list.sumOf { it.junk.size })} in ${list.size} apps",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Selected: ${Guard.gb(chosenBytes)} in ${chosen.size} apps",
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = list.isNotEmpty() && progress == null,
                onClick = { selected = if (selected.size == list.size) emptySet() else list.map { it.id }.toSet() },
            ) { Text(if (selected.size == list.size && list.isNotEmpty()) "Select none" else "Select all") }
            Button(
                onClick = { confirm = true },
                enabled = chosen.isNotEmpty() && progress == null,
            ) { Text("Clean selected") }
        }

        progress?.let { (done, total) ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Cleaning ${done + 1} of $total", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (result.isNotEmpty() && progress == null) {
            Text(result, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }

        when {
            apps == null -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp))
                Text("Measuring caches…")
            }
            list.isEmpty() -> Text(
                if (Shell.root == true) "No app caches found." else "No app caches found. The app needs all-files access to read Android/data.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                items(list, key = { it.id }) { app ->
                    val checked = app.id in selected
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = progress == null) {
                                selected = if (checked) selected - app.id else selected + app.id
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = progress == null)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(Guard.gb(app.junk.size), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear cache of ${chosen.size} apps?") },
            text = { Text("Frees about ${Guard.gb(chosenBytes)}. Only cache folders are deleted; app data is kept.") },
            confirmButton = { TextButton(onClick = { deleteChosen() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

private fun labelOf(pm: PackageManager, pkg: String): String =
    runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
