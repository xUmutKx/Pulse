package com.umutk.pulse

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Opens a tool (a full-screen page, see SubScreen) from inside any other page. Set by App. */
val LocalOpenTool = compositionLocalOf<(String) -> Unit> { {} }

/** A tool that used to sit on the Tools page. [key] is the id SubScreen understands. */
class ToolLink(val key: String, val icon: String, val title: String, val sub: String)

val TOOL_FOLDERS = ToolLink("folders", "folder", "Folder tree", "Size of every folder, biggest first")
val TOOL_TYPES = ToolLink("typebreak", "filetypes", "File types", "Donut chart of storage by file type")
val TOOL_CACHE = ToolLink("cache", "clean", "Cache cleaner", "Clear app caches")
val TOOL_TEMP = ToolLink("tempgraph", "thermal", "Temperature graph", "Sensor and battery temperature over time")
val TOOL_BATTERY = ToolLink("battery", "battery", "Battery health", "Condition, cycles and capacity")

/** The tool rows of one page: each card opens its tool. */
@Composable
fun ToolRows(vararg tools: ToolLink) {
    val open = LocalOpenTool.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.forEach { tool ->
            PCard(Modifier.fillMaxWidth().clickable { open(tool.key) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(tool.icon, T.text, 22.dp); Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool.title, color = T.text, fontSize = 15.sp)
                        Text(tool.sub, color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** The storage tools read the shared storage, which Android 11+ hides until all-files access is allowed. */
@Composable
fun StorageAccessCard() {
    val ctx = LocalContext.current
    var granted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) }
    // back from the system screen: look again, so the card goes away once allowed
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }
    fun ask() = ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    // the first time the files page opens without access, the system screen opens by itself
    androidx.compose.runtime.LaunchedEffect(granted) {
        val sp = ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
        if (!granted && !sp.getBoolean("files_asked", false)) { sp.edit().putBoolean("files_asked", true).apply(); runCatching { ask() } }
    }
    if (!granted) PCard(Modifier.fillMaxWidth()) {
        Text("Storage tools need access to all files.", color = T.sub, fontSize = 12.sp)
        PButton("Allow all files") { ask() }
    }
}
