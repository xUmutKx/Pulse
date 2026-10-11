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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.features.AppDetailsScreen
import com.umutk.pulse.features.AppManagerScreen
import com.umutk.pulse.features.BatteryHealthScreen
import com.umutk.pulse.features.CacheCleanerScreen
import com.umutk.pulse.features.FolderTreeScreen
import com.umutk.pulse.features.HealthScreen
import com.umutk.pulse.features.TempGraphScreen
import com.umutk.pulse.features.TypeBreakdownScreen

/** Entry list for the Tools page. Keys are the sub-screen ids used by SubScreen. */
private val toolEntries = listOf(
    "appmanager" to ("App manager" to "Apps with their functions and intent actions"),
    "typebreak" to ("File types" to "Donut chart of storage by file type"),
    "folders" to ("Folder tree" to "Size of every folder, biggest first"),
    "cache" to ("Cache cleaner" to "Clear app caches, like SD Maid"),
    "tempgraph" to ("Temperature graph" to "Sensor and battery temperature over time"),
    "battery" to ("Battery health" to "Condition, cycles and capacity"),
    "health" to ("Health" to "Steps, distance, calories and heart rate"),
)

@Composable
fun ToolsPage(open: (String) -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PHead("Tools") }
        // the folder tree, cache cleaner and cleaner read the shared storage, which Android 11+ hides until all-files access is allowed
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) item {
            PCard(Modifier.fillMaxWidth()) {
                Text("Storage tools need access to all files.", color = T.sub, fontSize = 12.sp)
                PButton("Allow all files") { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        items(toolEntries) { (key, text) ->
            PCard(Modifier.fillMaxWidth().clickable { open(key) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (key == "health") { PIcon("heart", T.text, 20.dp); Spacer(Modifier.width(8.dp)) }
                    Column {
                        Text(text.first, color = T.text, fontSize = 15.sp)
                        Text(text.second, color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Full-screen tool opened from the Tools page. "app:<package>" opens the details of one app. */
@Composable
fun SubScreen(id: String, open: (String) -> Unit, back: () -> Unit) {
    val root = Environment.getExternalStorageDirectory()
    when {
        id.startsWith("app:") -> AppDetailsScreen(id.removePrefix("app:")) { open("appmanager") }
        id == "appmanager" -> AppManagerScreen(onOpen = { open("app:$it") }, onBack = back)
        id == "typebreak" -> TypeBreakdownScreen(root, back)
        id == "folders" -> FolderTreeScreen(root, back)
        id == "cache" -> CacheCleanerScreen(back)
        id == "tempgraph" -> TempGraphScreen(back)
        id == "battery" -> BatteryHealthScreen(back)
        id == "health" -> HealthScreen(back)
    }
}
