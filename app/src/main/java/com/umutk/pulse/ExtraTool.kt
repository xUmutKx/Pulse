package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
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

/** The look of the music player, video player, white noise and playlists when opened from their own launcher icon: AMOLED black until the user picks another. */
object ExtraToolLook {
    val ids = listOf("amoled", "material", "win11", "win10", "win7", "xp", "win95")
    private fun p(c: Context) = c.getSharedPreferences("extra_tool_look", Context.MODE_PRIVATE)
    fun get(c: Context): String = p(c).getString("look", "amoled")?.takeIf { it in ids } ?: "amoled"
    fun set(c: Context, id: String) { p(c).edit().putString("look", id).apply() }
    fun name(id: String) = when (id) {
        "amoled" -> "AMOLED"; "material" -> "Material"; "win11" -> "Windows 11"; "win10" -> "Windows 10"; "win7" -> "Windows 7"; "xp" -> "Windows XP"; "win95" -> "Windows 95"; else -> id
    }
}

/**
 * The own look store of one newer standalone tool (Voice, Notes, Weather): Material until the user picks another.
 * [apply] puts it on the shared Look before the first frame; AppLookGuard.leave restores Pulse's look.
 */
class ToolLook(private val file: String, private val default: String = "material") {
    fun get(c: Context): String = c.getSharedPreferences(file, Context.MODE_PRIVATE).getString("look", default)?.takeIf { it in ExtraToolLook.ids } ?: default
    fun set(c: Context, id: String) { c.getSharedPreferences(file, Context.MODE_PRIVATE).edit().putString("look", id).apply(); apply(c) }
    fun apply(c: Context) {
        // AMOLED is not one of Themes: Material underneath, dark, with the black windows (LookHost draws the black page)
        val id = get(c)
        Look.theme = Themes.firstOrNull { it.id == id } ?: Themes.first { it.id == "material" }
        Look.mode = if (id == "amoled") "dark" else "system"; Look.windowTheme = if (id == "amoled") "amoled" else "win"; Look.dynamic = id != "amoled"; Look.accentIdx = -1
    }
}
val VoiceLook = ToolLook("voice_look")
// Notes opens black (AMOLED) until another look is picked
val NotesLook = ToolLook("notes_look", "amoled")
val WeatherLook = ToolLook("weather_look")
val PaintLook = ToolLook("paint_look")
// the calculator opens in the Windows 11 look until another is picked
val CalculatorLook = ToolLook("calculator_look", "win11")
// LocalSend opens in Material until another look is picked (AMOLED is in the look list)
val LocalSendLook = ToolLook("localsend_look")
// the keyboard's settings open in Material until another look is picked (the keyboard's own theme is chosen inside)
val KeyboardLook = ToolLook("keyboard_look")

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
 * One activity behind launcher entries (MusicLauncher, VideoLauncher, WhiteNoiseLauncher). The entry it was
 * started through picks the page, so it always knows where it was opened from. Back from the page closes it.
 */
open class ExtraToolActivity : ComponentActivity() {

    // same process as Pulse: LookHost writes the one global Look.resolved, so this tool keeps its own across pause / resume and Pulse keeps its own (MainActivity)
    private var ownResolved: PTheme? = null
    // a tool with its own look gives Pulse's look back on pause, before Pulse resumes (on stop it would be too late and Pulse would show this look)
    override fun onPause() { super.onPause(); ownResolved = Look.resolved; if (toolLook != null) AppLookGuard.leave(this) }
    override fun onResume() { enter(); super.onResume(); Look.resolved = ownResolved }
    override fun onStop() { super.onStop(); if (toolLook != null) AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); if (toolLook != null) AppLookGuard.leave(this) }

    // Voice, Notes and Weather have their own look store; the other tools keep ExtraToolLook
    private var toolLook: ToolLook? = null
    private fun enter() { toolLook?.apply(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // every tool is one card in the recent apps: a second start of the same plain tool (any entry: menu, store, launcher, widget) brings the open one forward
        // a notification's request for a page of the music player is handed to the window in the process
        if (savedInstanceState == null) intent.getStringExtra("notif_open")?.let { if (routeOf(intent) == "music") com.umutk.pulse.extras.music.MusicNav.request = it }
        if (savedInstanceState == null && this::class == ExtraToolActivity::class && intent.getStringExtra("frame") == null &&
            intent.extras?.keySet().orEmpty().all { it == "route" || it == "notif_open" }) {
            val mine = routeOf(intent)
            val other = runCatching { getSystemService(android.app.ActivityManager::class.java).appTasks.firstOrNull { t ->
                t.taskInfo.taskId != taskId && t.taskInfo.baseIntent.let { b -> b.component?.className.orEmpty().endsWith("ExtraToolActivity") || b.component?.className.orEmpty().endsWith("Launcher") }
                    && routeOf(t.taskInfo.baseIntent) == mine && t.taskInfo.baseIntent.getStringExtra("frame") == null
            } }.getOrNull()
            if (other != null) { other.moveToFront(); runCatching { overridePendingTransition(0, 0) }; finishAndRemoveTask(); return }
        }
        // Cfg / Opt are process-wide; this loads the saved ones before the first frame. The look itself is this tool's own (ExtraToolLook or a ToolLook).
        Cfg.load(this); Opt.load(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val start = routeOf(intent)
        val store = when (start) { "voice" -> VoiceLook; "notes" -> NotesLook; "weather" -> WeatherLook; "paint" -> PaintLook; "calculator" -> CalculatorLook; "localsend" -> LocalSendLook; "keyboard" -> KeyboardLook; else -> null }
        toolLook = store
        enter()
        setContent {
            var look by remember { mutableStateOf(store?.get(this@ExtraToolActivity) ?: ExtraToolLook.get(this@ExtraToolActivity)) }
            var route by rememberSaveable { mutableStateOf(start) }
            val back: () -> Unit = { if (route != start) route = start else finish() }
            val control = remember(look) {
                ExtraLookControl(look) { look = it; if (store != null) store.set(this@ExtraToolActivity, it) else ExtraToolLook.set(this@ExtraToolActivity, it) }
            }
            CompositionLocalProvider(LocalExtraLook provides control) {
                // started from the launcher in a Windows look: the page sits in that look's window, with the same look inside
                val frame = intent.getStringExtra("frame")
                val shown = if (frame != null && frame.removeSuffix("black") in ExtraToolLook.ids) frame.removeSuffix("black") else look
                LookHost(lookId = shown) { th ->
                    CompositionLocalProvider(LocalOpenTool provides { id -> if (id.isNotEmpty()) route = id else back() }) {
                        if (frame != null) com.umutk.pulse.extras.launcher.FramedWindow(frame, intent.getStringExtra("title") ?: route.substringAfter(':').replaceFirstChar { it.uppercase() }, { finish() }, if (route != start) back else null) {
                            Box(Modifier.fillMaxSize().background(th.bg)) { SubScreen(route, back) }
                        }
                        else Box(Modifier.fillMaxSize().background(th.bg).statusBarsPadding().navigationBarsPadding().imePadding()) { SubScreen(route, back) }
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
            n.endsWith("WhiteNoiseLauncher") -> "whitenoise"
            n.endsWith("VoiceLauncher") -> "voice"
            n.endsWith("NotesLauncher") -> "notes"
            n.endsWith("WeatherLauncher") -> "weather"
            n.endsWith("PaintLauncher") -> "paint"
            n.endsWith("CalculatorLauncher") -> "calculator"
            n.endsWith("MessagesLauncher") || n.endsWith("MsgComposeActivity") || n.endsWith("MsgBubbleActivity") -> "messages"
            n.endsWith("ScreenRecLauncher") -> "screenrec"
            n.endsWith("NotifHistLauncher") -> "notifhist"
            n.endsWith("ExtraStoreLauncher") -> "extrastore"
            n.endsWith("LocalSendLauncher") -> "localsend"
            n.endsWith("PaletteLauncher") -> "palette"
            n.endsWith("BlackOutLauncher") -> "blackout"
            n.endsWith("KeyboardLauncher") || n.endsWith("KeyboardSettingsActivity") -> "keyboard"
            // another app's launcher entry (Files, Calendar, Contacts, Photos...) is never the music player: it must not match the "already open" check
            n.endsWith("Launcher") -> n.substringAfterLast('.')
            else -> i.getStringExtra("route") ?: "music"
        }
    }
}

/** The keyboard's settings, started by the system's keyboard list (the gear of the input method). */
class KeyboardSettingsActivity : ExtraToolActivity()

/** The tool activity in a see-through window: the launcher's framed Pulse windows (see FramedWindow). */
class FramedToolActivity : ExtraToolActivity()

/**
 * Opens a tool page by its "route" extra. The tool pages start as documents of their own (each tap would add one more window to the recent apps), so
 * the one that is already open is brought to the front; only when there is none, a new one starts. A request for a sheet ("open") always starts a new one.
 */
class ToolOpenActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val route = intent.getStringExtra("route").orEmpty()
        val extras = intent.extras
        // a notification's request for a page of the music player is handed over in the process, so the open window is reused
        intent.getStringExtra("notif_open")?.let { if (route == "music") com.umutk.pulse.extras.music.MusicNav.request = it }
        val plain = extras == null || extras.keySet().all { it == "route" || (it == "notif_open" && route == "music") }
        val am = getSystemService(android.app.ActivityManager::class.java)
        val open = if (plain && route.isNotEmpty()) am.appTasks.firstOrNull { t ->
            val b = t.taskInfo.baseIntent
            b.getStringExtra("route") == route || b.component?.className.orEmpty().let { n -> route == "music" && n.endsWith("MusicLauncher") }
        } else null
        if (open != null) open.moveToFront()
        else startActivity(Intent(this, ExtraToolActivity::class.java).also { i -> extras?.let { i.putExtras(it) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
