package com.drdisagree.glasslayer.ui.oneui

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drdisagree.glasslayer.data.source.InspectReceiver
import com.drdisagree.glasslayer.data.source.OneUiKeys
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.data.source.XposedServiceSource
import com.drdisagree.glasslayer.ui.theme.GlassLayerTheme

/**
 * The One UI page: which apps get glass (beside System UI), how often the glass is caught while nothing moves, and the inspector that writes down the
 * views of Samsung's screens (saved to Download/GlassLayer to be shared, so hooks for those exact classes can be written).
 */
class OneUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        XposedServiceSource.init()
        setContent { GlassLayerTheme { OneUiScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun OneUiScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val service by XposedServiceSource.service.collectAsState()
    val prefs: SharedPreferences? = remember(service) { runCatching { service?.getRemotePreferences(PrefKeys.GROUP) }.getOrNull() }
    var tick by remember { mutableIntStateOf(0) }
    fun put(block: (SharedPreferences.Editor) -> Unit) { XposedServiceSource.edit(block); tick++ }
    fun bool(key: String, def: Boolean): Boolean { tick; return runCatching { prefs?.getBoolean(key, def) }.getOrNull() ?: def }
    fun num(key: String, def: Float): Float { tick; return runCatching { prefs?.getFloat(key, def) }.getOrNull() ?: def }
    val c = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹") }
            Text("One UI", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        if (prefs == null) Text("The Xposed service is not connected: enable the module in LSPosed and open the app again.", color = c.error, fontSize = 13.sp)

        Section("Apps")
        Text("System UI always gets the glass. These apps get it too when they are on; a change applies when the app is opened again.", color = c.onSurfaceVariant, fontSize = 12.sp)
        OneUiKeys.APPS.forEach { (pkg, name) ->
            val on = bool(OneUiKeys.appKey(pkg), true)
            Row(Modifier.fillMaxWidth().clickable(enabled = prefs != null) { put { it.putBoolean(OneUiKeys.appKey(pkg), !on) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = 16.sp)
                    Text(pkg, color = c.onSurfaceVariant, fontSize = 11.sp)
                }
                Switch(on, { v -> put { it.putBoolean(OneUiKeys.appKey(pkg), v) } }, enabled = prefs != null)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("Glass while nothing moves")
        Text("While no finger is down and no glass surface moves, the glass is caught this many times a second (it follows at full rate as soon as something moves).", color = c.onSurfaceVariant, fontSize = 12.sp)
        val idle = num(OneUiKeys.IDLE_FPS, OneUiKeys.DEFAULT_IDLE_FPS.toFloat()).toInt()
        listOf(0 to "Off (always the full rate)", 2 to "2 per second", 4 to "4 per second", 8 to "8 per second", 15 to "15 per second").forEach { (v, label) ->
            Row(Modifier.fillMaxWidth().clickable(enabled = prefs != null) { put { it.putFloat(OneUiKeys.IDLE_FPS, v.toFloat()) } }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(idle == v, { put { it.putFloat(OneUiKeys.IDLE_FPS, v.toFloat()) } }, enabled = prefs != null)
                Text(label, fontSize = 15.sp)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Section("Inspector")
        val inspector = bool(OneUiKeys.INSPECTOR, false)
        Row(Modifier.fillMaxWidth().clickable(enabled = prefs != null) { put { it.putBoolean(OneUiKeys.INSPECTOR, !inspector) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Write down the screens", fontSize = 16.sp)
                Text("Each window of System UI and each screen of the apps above is written down (its views, ids and backgrounds). Restart System UI after switching it on, then open the shade, quick settings, the volume panel, the lock screen and the apps.", color = c.onSurfaceVariant, fontSize = 12.sp)
            }
            Switch(inspector, { v -> put { it.putBoolean(OneUiKeys.INSPECTOR, v) } }, enabled = prefs != null)
        }
        val saved = remember(tick) { InspectReceiver.dir(ctx).listFiles()?.filter { it.isFile }.orEmpty() }
        Text(if (saved.isEmpty()) "Nothing written down yet." else "Written down: " + saved.joinToString { it.nameWithoutExtension + " (" + it.length() / 1024 + " KB)" }, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                val ok = saveAll(ctx)
                Toast.makeText(ctx, if (ok) "Saved to Download/GlassLayer" else "Nothing to save", Toast.LENGTH_SHORT).show()
            }, enabled = saved.isNotEmpty()) { Text("Save to Downloads") }
            OutlinedButton(onClick = { saved.forEach { it.delete() }; tick++ }, enabled = saved.isNotEmpty()) { Text("Clear") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) = Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))

/** Every written-down screen in one text file in Download/GlassLayer (no storage permission needed for the app's own file there). */
private fun saveAll(ctx: Context): Boolean = runCatching {
    val files = InspectReceiver.dir(ctx).listFiles()?.filter { it.isFile }.orEmpty()
    if (files.isEmpty()) return@runCatching false
    val text = files.joinToString("\n") { "##### " + it.name + "\n" + it.readText() }
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "GlassLayer-inspect-" + System.currentTimeMillis() + ".txt")
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GlassLayer")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return@runCatching false
    true
}.getOrDefault(false)
