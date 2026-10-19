package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A store's own preference file. Its keys are copied once from the shared "p" file, then only this file is written:
 * other processes (Files, Photos, Office) hold a stale copy of "p" and would write old look keys back to disk.
 */
fun ownPrefs(c: Context, name: String, keep: (String) -> Boolean = { true }): android.content.SharedPreferences {
    val own = c.getSharedPreferences(name, Context.MODE_PRIVATE)
    if (own.getBoolean("_moved", false)) return own
    val e = own.edit()
    for ((k, v) in c.getSharedPreferences("p", Context.MODE_PRIVATE).all) if (keep(k)) when (v) {
        is Boolean -> e.putBoolean(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v)
        is String -> e.putString(k, v); is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
    }
    e.putBoolean("_moved", true).apply()
    return own
}

/** The look of the music player, video player, white noise and playlists when opened from their own launcher icon: Material until the user picks another. */
object ExtraToolLook {
    val ids = listOf("material", "win11", "win10", "win7", "xp", "win95")
    private fun p(c: Context) = c.getSharedPreferences("extra_tool_look", Context.MODE_PRIVATE)
    fun get(c: Context): String = p(c).getString("look", "material")?.takeIf { it in ids } ?: "material"
    fun set(c: Context, id: String) { p(c).edit().putString("look", id).apply() }
    fun name(id: String) = when (id) {
        "material" -> "Material"; "win11" -> "Windows 11"; "win10" -> "Windows 10"; "win7" -> "Windows 7"; "xp" -> "Windows XP"; "win95" -> "Windows 95"; else -> id
    }
}

/** Present only inside [ExtraToolActivity]: the page can offer the look choice, inside Pulse itself it is null. */
class ExtraLookControl(val id: String, val set: (String) -> Unit)

val LocalExtraLook = staticCompositionLocalOf<ExtraLookControl?> { null }

/** A settings row "App look" that opens the look picker; draws nothing outside the standalone tools. */
@Composable
fun ExtraLookRow(ink: Color = T.text) {
    val c = LocalExtraLook.current ?: return
    var open by remember { mutableStateOf(false) }
    if (open) SetLookDialog(tr("Look", "Görünüm"), ExtraToolLook.ids, c.id, { ExtraToolLook.name(it) }, { c.set(it) }) { open = false }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon("palette", ink, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(tr("App look", "Uygulama görünümü"), color = ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(ExtraToolLook.name(c.id), color = ink.copy(alpha = .6f), fontSize = 14.sp)
    }
}

/** A small palette button for the headers of the standalone tools; draws nothing inside Pulse. */
@Composable
fun ExtraLookIcon(tint: Color = T.text) {
    val c = LocalExtraLook.current ?: return
    var open by remember { mutableStateOf(false) }
    if (open) SetLookDialog(tr("Look", "Görünüm"), ExtraToolLook.ids, c.id, { ExtraToolLook.name(it) }, { c.set(it) }) { open = false }
    Box(Modifier.size(40.dp).clickable { open = true }, contentAlignment = Alignment.Center) { PIcon("palette", tint, 22.dp) }
}

/**
 * One activity behind launcher entries (MusicLauncher, VideoLauncher). The entry it was
 * started through picks the page, so it always knows where it was opened from. Back from the page closes it.
 */
class ExtraToolActivity : ComponentActivity() {

    // same process as Pulse: LookHost writes the one global Look.resolved, so this tool keeps its own across pause / resume and Pulse keeps its own (MainActivity)
    private var ownResolved: PTheme? = null
    override fun onPause() { super.onPause(); ownResolved = Look.resolved }
    override fun onResume() { super.onResume(); Look.resolved = ownResolved }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Cfg / Opt are process-wide; this loads the saved ones before the first frame. The look itself is this tool's own (ExtraToolLook).
        Cfg.load(this); Opt.load(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val start = routeOf(intent)
        setContent {
            var look by remember { mutableStateOf(ExtraToolLook.get(this@ExtraToolActivity)) }
            var route by rememberSaveable { mutableStateOf(start) }
            val back: () -> Unit = { if (route != start) route = start else finish() }
            val control = remember(look) { ExtraLookControl(look) { look = it; ExtraToolLook.set(this@ExtraToolActivity, it) } }
            CompositionLocalProvider(LocalExtraLook provides control) {
                LookHost(lookId = look) { th ->
                    CompositionLocalProvider(LocalOpenTool provides { id -> if (id.isNotEmpty()) route = id else back() }) {
                        Box(Modifier.fillMaxSize().background(th.bg).statusBarsPadding().navigationBarsPadding()) { SubScreen(route, back) }
                    }
                }
            }
        }
    }

    private fun routeOf(i: Intent): String {
        val n = i.component?.className.orEmpty()
        return when {
            n.endsWith("MusicLauncher") -> "music"
            n.endsWith("VideoLauncher") -> "video"
            else -> i.getStringExtra("route") ?: "music"
        }
    }
}
