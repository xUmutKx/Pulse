package com.umutk.pulse

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.awaitFirstDown
import android.app.Activity
import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.outlined.Check
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Share
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.umutk.pulse.features.ThemeIcons

// ---------------------------------------------------------------------------------------------
// Office viewer foundation: the four apps, the themes, the saved choices, a shared basic chrome,
// the logo and the splash box. The Word / Excel / PowerPoint agents only read OfKind, OfTheme,
// OfPal, OAct, OfDoc and call OfChromeBasic (or draw their own chrome from OfTheme.pal).
// ---------------------------------------------------------------------------------------------

enum class OfKind(val app: String, val accent: Color, val dark: Color, val letter: String, val ext: String) {
    WORD("Word", Color(0xFF2B579A), Color(0xFF1E3E6E), "W", "docx"),
    EXCEL("Excel", Color(0xFF217346), Color(0xFF185A34), "X", "xlsx"),
    PPT("PowerPoint", Color(0xFFB7472A), Color(0xFF8C3620), "P", "pptx"),
    PDF("PDF", Color(0xFFC0392B), Color(0xFF8E2A20), "PDF", "pdf")
}

/** What the Office page / external intent hands to a screen: a readable local file (always a cache copy), its name and how to write it back. */
class OfDoc(
    val file: java.io.File,
    val name: String,
    val canEdit: Boolean,
    /** write the new file with [writer] (given a temp file), then it replaces the original; [done] gets a short message */
    val save: (writer: (java.io.File) -> Unit, done: (String) -> Unit) -> Unit,
    /** the same, into a new "(copy)" file in the Office folder */
    val saveCopy: (writer: (java.io.File) -> Unit, done: (String) -> Unit) -> Unit,
    val close: () -> Unit,
) {
    /** set true once the screen has unsaved changes: closing then asks first (the activity handles it) */
    var dirty by mutableStateOf(false)
    /** the screen's current content as plain text ("TXT olarak kaydet"); null when the kind has no text form */
    var txt: (() -> String)? = null
}

/** One colour set of one look of one app. */
class OfPal(
    val titleBg: Color, val titleInk: Color,
    val tabBg: Color, val tabInk: Color, val tabSelBg: Color, val tabSelInk: Color,
    val ribbonBg: Color, val ink: Color, val line: Color,
    val canvas: Color, val paper: Color,
    val statusBg: Color, val statusInk: Color,
    val accent: Color,
)

val OF_THEME_IDS = listOf("365", "365d", "2017", "2017d", "2013", "2013d", "2003", "95")
val OF_PICK_IDS = OF_THEME_IDS + listOf("app", "amoled", "material")

fun ofThemeName(id: String) = when (id) {
    "365" -> "Office 365"; "365d" -> "Office 365 dark"; "2017" -> "Office 2017"; "2017d" -> "Office 2017 dark"
    "2013" -> "Office 2013"; "2013d" -> "Office 2013 dark"; "2003" -> "Office 2003"; "95" -> "Office 95"; "amoled" -> "AMOLED"; "material" -> "Material"; "oneui" -> "One UI"; "coloros" -> "ColorOS"; else -> tr("App look", "Uygulama görünümü")
}

/** "app" becomes the Office look closest to the current Pulse look. */
fun ofResolveId(id: String): String {
    if (id != "app") return id
    val th = Look.current
    if (th.id == "amoled") return "amoled"
    if (th.id == "material") return "material"
    if (th.id == "oneui") return "oneui"
    if (th.id == "coloros") return "coloros"
    val base = when (th.id) { "win95" -> "95"; "xp" -> "2003"; "win7" -> "2013"; "win10" -> "2017"; else -> "365" }
    // 2013 (Windows 7) used to have no dark sibling at all: Pulse's own dark mode had no effect on Office's chrome there
    return if (th.dark && (base == "365" || base == "2017" || base == "2013")) base + "d" else base
}

/** Flat dark variant of the app colour (dark looks tint their chrome with it instead of grey). */
fun ofDarkTint(c: Color, f: Float) = androidx.compose.ui.graphics.lerp(c, Color.Black, f)

/** The chosen title bar colour laid over a look's own palette ([OfficePrefs.bars]): 0 leaves the look as it is; 1 accent, 2 the app's colour, 3 AMOLED black for the bar, the tabs, the ribbon and the status bar. */
fun ofPal(id: String, k: OfKind): OfPal {
    val b = ofPalBase(id, k)
    // the AMOLED look stays black whatever the title bar colour is set to (the app colour would paint the PDF red)
    if (ofResolveId(id) == "amoled") return b
    return when (OfficePrefs.bars) {
        1 -> OfPal(Look.accent.copy(alpha = 1f), onAccent(Look.accent), b.tabBg, b.tabInk, b.tabSelBg, b.tabSelInk, b.ribbonBg, b.ink, b.line, b.canvas, b.paper, b.statusBg, b.statusInk, b.accent)
        2 -> OfPal(k.brand, ofInkOn(k.brand), b.tabBg, b.tabInk, b.tabSelBg, b.tabSelInk, b.ribbonBg, b.ink, b.line, b.canvas, b.paper, b.statusBg, b.statusInk, b.accent)
        3 -> OfPal(Color.Black, Color.White, Color.Black, Color.White, Color(0xFF1C1C1C), Color.White, Color.Black, Color.White, Color(0xFF2A2A2A), Color.Black, b.paper, Color.Black, Color(0xFF9E9E9E), b.accent)
        else -> b
    }
}

private fun ofPalBase(id: String, k: OfKind): OfPal {
    val a = k.accent
    return when (ofResolveId(id)) {
        "amoled" -> OfPal(Color.Black, Color.White, Color.Black, Color.White, Color.Black, Look.accent,
            Color(0xFF111111), Color.White, Color(0xFF2A2A2A), Color.Black, Color.White, Color.Black, Color(0xFF9E9E9E), Look.accent)
        "material" -> if (Look.current.dark) OfPal(Color(0xFF141218), Color(0xFFE6E0E9), Color(0xFF141218), Color(0xFFE6E0E9), Color(0xFF141218), Look.accent,
            Color(0xFF211F26), Color(0xFFE6E0E9), Color(0xFF49454F), Color(0xFF0F0D13), Color.White, Color(0xFF141218), Color(0xFFCAC4D0), Look.accent)
        else OfPal(Color(0xFFFEF7FF), Color(0xFF1D1B20), Color(0xFFFEF7FF), Color(0xFF1D1B20), Color(0xFFFEF7FF), Look.accent,
            Color(0xFFF3EDF7), Color(0xFF1D1B20), Color(0xFFCAC4D0), Color(0xFFEDE7F3), Color.White, Color(0xFFFEF7FF), Color(0xFF49454F), Look.accent)
        // One UI (Samsung Notes): soft grey page, white rounded bars, blue accent
        "oneui" -> if (Look.current.dark) OfPal(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF3E91FF),
            Color(0xFF1C1C1E), Color(0xFFFFFFFF), Color(0xFF2C2C2E), Color(0xFF0A0A0A), Color.White, Color(0xFF000000), Color(0xFF9A9AA0), Color(0xFF3E91FF))
        else OfPal(Color(0xFFF4F4F6), Color(0xFF000000), Color(0xFFF4F4F6), Color(0xFF000000), Color(0xFFF4F4F6), Color(0xFF3E91FF),
            Color(0xFFFFFFFF), Color(0xFF1B1B1D), Color(0xFFE6E6EA), Color(0xFFEDEDF0), Color.White, Color(0xFFF4F4F6), Color(0xFF6B6B70), Color(0xFF3E91FF))
        // ColorOS: light airy panels, rounded, accent green
        "coloros" -> if (Look.current.dark) OfPal(Color(0xFF121212), Color(0xFFF2F2F2), Color(0xFF121212), Color(0xFFF2F2F2), Color(0xFF121212), Color(0xFF2EC98A),
            Color(0xFF1E1E1E), Color(0xFFF2F2F2), Color(0xFF303030), Color(0xFF0C0C0C), Color.White, Color(0xFF121212), Color(0xFFA0A0A0), Color(0xFF2EC98A))
        else OfPal(Color(0xFFF7F7F7), Color(0xFF111111), Color(0xFFF7F7F7), Color(0xFF111111), Color(0xFFF7F7F7), Color(0xFF0BB26E),
            Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0xFFE8E8E8), Color(0xFFEFEFEF), Color.White, Color(0xFFF7F7F7), Color(0xFF707070), Color(0xFF0BB26E))
        "365d" -> OfPal(ofDarkTint(a, .8f), Color(0xFFF3F2F1), ofDarkTint(a, .8f), Color(0xFFF3F2F1), ofDarkTint(a, .8f), Color.White.copy(alpha = .75f).compositeOver(a),
            ofDarkTint(a, .74f), Color(0xFFF3F2F1), ofDarkTint(a, .6f), ofDarkTint(a, .9f), Color.White, ofDarkTint(a, .8f), Color(0xFFC8C6C4), a)
        "2017" -> OfPal(a, Color.White, a, Color.White, Color(0xFFF1F1F1), a,
            Color(0xFFF1F1F1), Color(0xFF262626), Color(0xFFD4D4D4), Color(0xFFE6E6E6), Color.White, a, Color.White, a)
        "2017d" -> OfPal(ofDarkTint(a, .7f), Color.White, ofDarkTint(a, .7f), Color.White, ofDarkTint(a, .6f), Color.White,
            ofDarkTint(a, .6f), Color(0xFFEDEDED), ofDarkTint(a, .45f), ofDarkTint(a, .8f), Color.White, ofDarkTint(a, .7f), Color.White, a)
        "2013" -> OfPal(Color.White, Color(0xFF444444), Color.White, Color(0xFF444444), Color(0xFFF6F6F6), a,
            Color(0xFFF6F6F6), Color(0xFF444444), Color(0xFFD4D4D4), Color(0xFFE6E6E6), Color.White, a, Color.White, a)
        "2013d" -> OfPal(ofDarkTint(a, .7f), Color.White, ofDarkTint(a, .7f), Color.White, ofDarkTint(a, .6f), Color.White,
            ofDarkTint(a, .6f), Color(0xFFEDEDED), ofDarkTint(a, .45f), ofDarkTint(a, .8f), Color.White, ofDarkTint(a, .7f), Color.White, a)
        "2003" -> OfPal(Color(0xFF0A5BE0), Color.White, Color(0xFFECE9D8), Color.Black, Color(0xFFECE9D8), Color.Black,
            Color(0xFFECE9D8), Color.Black, Color(0xFFACA899), Color(0xFFA4BEE6), Color.White, Color(0xFFECE9D8), Color.Black, a)
        "95" -> OfPal(Color(0xFF000080), Color.White, Color(0xFFC0C0C0), Color.Black, Color(0xFFC0C0C0), Color.Black,
            Color(0xFFC0C0C0), Color.Black, Color(0xFF808080), Color(0xFF808080), Color.White, Color(0xFFC0C0C0), Color.Black, a)
        else -> OfPal(Color.White, Color(0xFF201F1E), Color.White, Color(0xFF201F1E), Color.White, a,
            Color(0xFFF3F2F1), Color(0xFF201F1E), Color(0xFFE1DFDD), Color(0xFFEDEBE9), Color.White, Color.White, Color(0xFF605E5C), a)
    }
}

/** The resolved look of one app: [id] is the picked id ("365", ..., "app"), [base] one of 365 / 2017 / 2013 / 2003 / 95. */
class OfTheme(val kind: OfKind, val id: String) {
    val rid = ofResolveId(id)
    val base = rid.removeSuffix("d")
    val dark = OfficePrefs.bars == 3 || rid.endsWith("d") || rid == "amoled" || ((rid == "material" || rid == "oneui" || rid == "coloros") && Look.current.dark)
    val classic = base == "2003" || base == "95"
    val pal = ofPal(rid, kind)
    val accent get() = kind.accent
}

fun ofTheme(k: OfKind) = OfTheme(k, OfficePrefs.themeFor(k))

/** Saved Office choices: one look per app, the folder for new files, the recent list. */
object OfficePrefs {
    private val themes = mutableStateMapOf<OfKind, String>()
    var dir by mutableStateOf("Documents")
    var recent by mutableStateOf(listOf<String>())   // "uri\u0001name\u0001kind"
    /** "Dark pages (AMOLED)": document content only (page, cells, slide) is black with light text; chrome untouched */
    var darkPages by mutableStateOf(false)
    /** Office icon in Material/AMOLED/One UI/ColorOS: false = black tile + white outline, true = tinted from the Look accent */
    var iconAccent by mutableStateOf(false)
    /** Dark PDF pages (smart invert), separate from darkPages and off by default */
    var darkPdf by mutableStateOf(false)
    /** The title bar of every Office look: 0 as the look draws it, 1 the accent colour, 2 the app's own colour (Word blue, PDF red ...), 3 AMOLED black (the tabs, the ribbon and the status bar turn black too). */
    var bars by mutableStateOf(0)
    /** Dark page colours (ARGB): background and text */
    var pageBg by mutableStateOf(0xFF000000.toInt())
    var pageFg by mutableStateOf(0xFFFFFFFF.toInt())
    val pageBgC get() = Color(pageBg)
    val pageFgC get() = Color(pageFg)

    private fun sp(c: Context) = c.getSharedPreferences("office", Context.MODE_PRIVATE)
    fun themeFor(k: OfKind): String = themes[k] ?: "365"
    /** Office look used where the user never picked one (the viewer sets "material" for files opened from Files / other apps; "of_look_default" intent extra overrides). */
    var defLook = "365"
    fun load(c: Context) {
        val p = sp(c)
        OfKind.values().forEach { themes[it] = p.getString("theme_${it.name}", defLook) ?: defLook }
        dir = p.getString("dir", "Documents") ?: "Documents"
        ClippyPrefs.load(c)
        iconAccent = p.getBoolean("icon_accent", false); darkPages = p.getBoolean("dark_pages", false); darkPdf = if (p.contains("dark_pdf")) p.getBoolean("dark_pdf", false) else p.getBoolean("dark_pages", false)
        bars = p.getInt("bars_v2", 0)
        pageBg = p.getInt("page_bg", 0xFF000000.toInt()); pageFg = p.getInt("page_fg", 0xFFFFFFFF.toInt())
        recent = (p.getString("recent", "") ?: "").split('\n').filter { it.isNotBlank() }
    }
    fun setTheme(c: Context, k: OfKind, id: String) { themes[k] = id; sp(c).edit().putString("theme_${k.name}", id).apply() }
    fun applyAll(c: Context, id: String) { val e = sp(c).edit(); OfKind.values().forEach { themes[it] = id; e.putString("theme_${it.name}", id) }; e.apply() }
    fun setIconAccent(c: Context, on: Boolean) { iconAccent = on; sp(c).edit().putBoolean("icon_accent", on).apply() }
    fun setDarkPages(c: Context, on: Boolean) { darkPages = on; sp(c).edit().putBoolean("dark_pages", on).apply(); if (!sp(c).contains("dark_pdf")) darkPdf = on } // PDF follows until its own switch is touched
    fun setBars(c: Context, v: Int) { bars = v; sp(c).edit().putInt("bars_v2", v).apply() }
    fun setDarkPdf(c: Context, on: Boolean) { darkPdf = on; sp(c).edit().putBoolean("dark_pdf", on).apply() }
    fun setPageBg(c: Context, v: Int) { pageBg = v; sp(c).edit().putInt("page_bg", v).apply() }
    fun setPageFg(c: Context, v: Int) { pageFg = v; sp(c).edit().putInt("page_fg", v).apply() }
    fun setDir(c: Context, d: String) { dir = d; sp(c).edit().putString("dir", d).apply() }
    fun addRecent(c: Context, uri: String, name: String, k: OfKind) {
        val e = "$uri\u0001$name\u0001${k.name}"
        recent = (listOf(e) + recent.filter { it.substringBefore('\u0001') != uri }).take(15)
        sp(c).edit().putString("recent", recent.joinToString("\n")).apply()
    }
    fun forget(c: Context, uri: String) { recent = recent.filter { it.substringBefore('\u0001') != uri }; sp(c).edit().putString("recent", recent.joinToString("\n")).apply() }
    val folder: java.io.File get() = java.io.File(android.os.Environment.getExternalStorageDirectory(), dir)
}

fun ofKindOf(name: String, mime: String? = null): OfKind? {
    val e = name.substringAfterLast('.', "").lowercase()
    return when {
        e in setOf("docx", "doc", "rtf", "txt", "odt", "dotx") -> OfKind.WORD
        e in setOf("xlsx", "xls", "xlt", "csv", "tsv", "xlsm") -> OfKind.EXCEL
        e in setOf("pptx", "ppt", "ppsx", "pps", "pot") -> OfKind.PPT
        e == "pdf" -> OfKind.PDF
        mime == null -> null
        mime.contains("wordprocessingml") || mime == "application/msword" || mime == "application/rtf" -> OfKind.WORD
        mime.contains("spreadsheetml") || mime == "application/vnd.ms-excel" || mime == "text/csv" -> OfKind.EXCEL
        mime.contains("presentationml") || mime == "application/vnd.ms-powerpoint" -> OfKind.PPT
        mime == "application/pdf" -> OfKind.PDF
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------------------------

/**
 * With the phone turned sideways the title bar, tabs, ribbon and status bar take most of the screen: they hide, a tap on the page brings them back and
 * they go again after 2 seconds. Returns (hidden now, the tap-watching modifier for the window). Switch in Office settings; on at first.
 */
@Composable
fun ofAutoHide(): Pair<Boolean, Modifier> {
    val ctx = LocalContext.current
    val on = remember { ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).getBoolean("of_autohide", true) }
    val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    var shown by remember { mutableStateOf(false) }
    var bump by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(bump, shown) { if (shown) { kotlinx.coroutines.delay(2000); shown = false } }
    val mod = if (on && land) Modifier.pointerInput(Unit) {
        // watches without taking the touch: a press and release that barely moved is a tap
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            var moved = false
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val c = e.changes.firstOrNull() ?: break
                if ((c.position - down.position).getDistance() > 30f) moved = true
                if (!c.pressed) { if (!moved) { shown = true; bump++ }; break }
            }
        }
    } else Modifier
    return (on && land && !shown) to mod
}

/** Optional own ribbon body per tab name (modern looks only); the Word screen provides its stacked Home ribbon through it. */
val LocalOfRibbon = compositionLocalOf<Map<String, @Composable () -> Unit>> { emptyMap() }

/**
 * The Word-style ribbon of every tab that has captions: the actions of a tab are grouped by [OAct.cap] (in the order they appear), each group a row of icon cells with
 * its label under the icon, a thin line between groups and the group's name under it. Tabs where no action has a caption are left to the plain row.
 */
fun ofCaptionRibbons(t: OfTheme, acts: List<OAct>): Map<String, @Composable () -> Unit> =
    acts.filter { it.group != "File" && it.group != "Quick" }.groupBy { it.group }.filter { (_, l) -> l.any { it.cap != null } }.mapValues { (tab, l) ->
        val groups = l.groupBy { it.cap ?: tab }.toList()
        val body: @Composable () -> Unit = {
            val pal = t.pal
            val ink = ofInkOn(if (t.base == "365" || t.base == "2017") ofBelow(t) else pal.ribbonBg)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
                groups.forEachIndexed { gi, (cap, items) ->
                    if (gi > 0) Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(64.dp).background(pal.line))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row {
                            items.forEach { a ->
                                Column(Modifier.width(54.dp).clip(RoundedCornerShape(4.dp)).background(if (a.on) pal.accent.copy(alpha = .22f) else Color.Transparent).alpha(if (a.enabled) 1f else .4f)
                                    .clickable(enabled = a.enabled, onClick = a.click).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(a.icon, a.label, Modifier.size(22.dp), ink)
                                    Text(a.label, color = ink, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Text(cap, color = ink.copy(alpha = .6f), fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
        body
    }

/** One ribbon button / menu item. [group] becomes the ribbon tab (modern looks) or the menu (2003 / 95). [png] is a ThemeIcons key tried first in the 2003 / 95 looks. */
class OAct(val label: String, val group: String, val icon: ImageVector, val on: Boolean = false, val enabled: Boolean = true, val png: String? = null, val cap: String? = null, val click: () -> Unit)

/**
 * The shared themed window: title bar, ribbon tabs + ribbon (365 / 2017 / 2013) or menu bar + toolbar (2003 / 95), canvas and status bar.
 * The three per-app agents may call this and add their own tabs/groups, or draw a whole own chrome from [OfTheme.pal].
 */
@Composable
fun OfChromeBasic(t: OfTheme, title: String, acts: List<OAct>, status: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    if (t.base == "365") { OfChromeM365(t, title, acts, status, onBack, content = content); return }
    val pal = t.pal; val view = LocalView.current; val ctx = LocalContext.current
    val is17 = t.base == "2017"; val brand = ofBrand(t); val below = ofBelow(t)
    SideEffect {
        val w = (view.context as Activity).window
        // the system bars take the title bar's colour too: the status bar is painted, not left to the phone's theme
        val (bt, bb) = ofBars(t)
        w.statusBarColor = bt.toArgb(); w.navigationBarColor = bb.toArgb()
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = bt.luminance() > .5f; isAppearanceLightNavigationBars = bb.luminance() > .5f }
    }
    val fileOps = LocalOfFile.current
    var fileOpen by remember { mutableStateOf(false) }; var info by remember { mutableStateOf(false) }
    val fileItems = ofFileItems(acts, onBack) { info = true }
    val groups = (listOfNotNull("File".takeIf { fileOps != null }) + acts.map { it.group }.filter { it != "Quick" }).distinct()
    var tab by remember(groups) { mutableStateOf(if ("Home" in groups) "Home" else groups.firstOrNull { it != "File" } ?: "") }
    var menu by remember { mutableStateOf("") }
    val (autoHid, tapWatch) = ofAutoHide()
    // the arrow in the title bar hides the whole top block by hand; the little handle at the top brings it back
    var manualHid by remember { mutableStateOf(false) }
    val hid = autoHid || manualHid
    // a tap on the screen (anywhere the page or the background takes it) shows or hides the top block, like the bar does
    Box(Modifier.fillMaxSize().then(tapWatch).pointerInput(Unit) { detectTapGestures(onTap = { manualHid = !manualHid }) }) {
    Column(Modifier.fillMaxSize().background(if (t.classic) pal.ribbonBg else if (is17) below else pal.titleBg).then(if (is17) Modifier.navigationBarsPadding() else Modifier.systemBarsPadding())) {
        // title bar
        if (!hid) if (t.classic) {
            val band = if (OfficePrefs.bars != 0) Brush.horizontalGradient(listOf(pal.titleBg, pal.titleBg)) else if (t.base == "95") Brush.horizontalGradient(listOf(Color(0xFF000080), Color(0xFF1084D0))) else Brush.horizontalGradient(listOf(Color(0xFF0058EE), Color(0xFF3A93FF)))
            Row(Modifier.fillMaxWidth().height(30.dp).background(band).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OfLogo(t.kind, 18.dp); Spacer(Modifier.width(6.dp))
                Text(title, color = if (OfficePrefs.bars != 0) pal.titleInk else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(Modifier.size(22.dp).then(if (t.base == "95") Modifier.background(Color(0xFFC0C0C0)).bevel(true) else Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFFD9543C)).border(1.dp, Color.White, RoundedCornerShape(3.dp))).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(14.dp), if (t.base == "95") Color.Black else Color.White)
                }
            }
            // menu bar
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp)) {
                groups.forEach { g ->
                    Box {
                        Text(if (g == "Home") "Edit" else g, color = pal.ink, fontSize = 13.sp, modifier = Modifier.clickable { menu = g }.padding(horizontal = 9.dp, vertical = 5.dp))
                        DropdownMenu(menu == g, { menu = "" }, modifier = Modifier.background(pal.ribbonBg)) {
                            (if (g == "File" && fileOps != null) fileItems else acts.filter { it.group == g }).forEach { a ->
                                DropdownMenuItem(text = { Text(a.label, color = pal.ink, fontSize = 13.sp) }, enabled = a.enabled, onClick = { menu = ""; a.click() },
                                    leadingIcon = { OfActIcon(t, a, 18.dp, pal.ink) })
                            }
                        }
                    }
                }
            }
            // toolbar
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).then(if (t.base == "95") Modifier.bevel(true) else Modifier.border(1.dp, pal.line)).horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                var last = ""
                acts.forEach { a ->
                    if (last.isNotEmpty() && last != a.group) Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(22.dp).background(pal.line))
                    last = a.group
                    Box(Modifier.padding(horizontal = 1.dp).size(28.dp).alpha(if (a.enabled) 1f else .4f)
                        .then(if (t.base == "95") Modifier.background(pal.ribbonBg).bevel(!a.on) else if (a.on) Modifier.background(Color(0xFFC1D2EE)).border(1.dp, Color(0xFF316AC5)) else Modifier)
                        .clickable(enabled = a.enabled, onClick = a.click), contentAlignment = Alignment.Center) { OfActIcon(t, a, 20.dp, pal.ink) }
                }
            }
        } else {
            val titleInk = if (is17) ofInkOn(ofTop(t)) else pal.titleInk
            Row(Modifier.fillMaxWidth().then(if (is17) Modifier.background(ofTop(t)).statusBarsPadding() else Modifier).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(22.dp), titleInk) }
                OfTile(t.kind, 22.dp, is17); Spacer(Modifier.width(8.dp))
                OfTitle(title, titleInk)
                Box(Modifier.size(40.dp).clickable { manualHid = true }, contentAlignment = Alignment.Center) { Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(50)).background(titleInk)) }
                acts.filter { it.group == "Quick" }.forEach { a -> Box(Modifier.size(40.dp).alpha(if (a.enabled) 1f else .4f).clickable(enabled = a.enabled, onClick = a.click), contentAlignment = Alignment.Center) { Icon(a.icon, null, Modifier.size(20.dp), titleInk) } }
            }
            Row(Modifier.fillMaxWidth().then(if (is17) Modifier.background(below) else Modifier).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
                groups.forEach { g ->
                    val sel = g == tab && g != "File"
                    val tInk = if (is17) (if (t.dark) Color(0xFFEDEDED) else Color(0xFF262626)) else ofInkOn(pal.titleBg)
                    val tSel = if (is17) (if (t.dark) Color.White else brand) else pal.tabSelInk
                    val shape = if (t.base == "365") RoundedCornerShape(8.dp) else if (is17) RectangleShape else RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    val file = (t.base == "2013" || is17) && g == "File"
                    Box(Modifier.padding(horizontal = 1.dp).clip(shape)
                        .background(if (file) brand else if (sel && t.base != "365") pal.tabSelBg else Color.Transparent)
                        .clickable { if (g == "File") fileOpen = true else tab = g }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(if (g == "File") tr("File", "Dosya") else g, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (file) Color.White else if (sel) tSel else tInk)
                        if (sel && (t.base == "365" || is17)) Box(Modifier.align(Alignment.BottomCenter).width(18.dp).height(2.dp).background(tSel, RoundedCornerShape(1.dp)))
                    }
                }
            }
            val custom = LocalOfRibbon.current[tab]
            val ribbon: @Composable () -> Unit = {
                if (custom != null) custom() else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp)) {
                    acts.filter { it.group == tab && it.group != "Quick" }.forEach { a ->
                        Column(Modifier.padding(horizontal = 2.dp).widthIn(min = 54.dp).clip(RoundedCornerShape(if (t.base == "365") 8.dp else 2.dp))
                            .background(if (a.on) pal.accent.copy(alpha = .18f) else Color.Transparent).alpha(if (a.enabled) 1f else .4f)
                            .clickable(enabled = a.enabled, onClick = a.click).padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            OfActIcon(t, a, 22.dp, ofInkOn(if (is17) below else pal.ribbonBg))
                            Text(a.label, color = ofInkOn(if (is17) below else pal.ribbonBg), fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
            if (t.base == "365") Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(pal.ribbonBg)) { ribbon() }
            else Box(Modifier.fillMaxWidth().background(if (is17) below else pal.ribbonBg).then(if (t.base == "2013" || is17) Modifier.border(1.dp, pal.line) else Modifier)) { ribbon() }
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(pal.canvas), content = content)
        // status bar
        if (!hid) Row(Modifier.fillMaxWidth().height(24.dp).background(if (is17) below else pal.statusBg).then(if (t.base == "95") Modifier.bevel(true) else if (t.base == "2003") Modifier.border(1.dp, pal.line) else Modifier).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = if (is17) (if (t.dark) Color(0xFFC8C6C4) else Color(0xFF605E5C)) else pal.statusInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
        if (ClippyPrefs.on && (t.base == "95" || t.base == "2003")) {
        val dirty = fileOps?.dirty?.invoke() == true; var was by remember { mutableStateOf(dirty) }; var saved by remember { mutableStateOf(false) }
        LaunchedEffect(dirty) { saved = was && !dirty; was = dirty }
        ClippyView(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 6.dp, bottom = 34.dp), saved)
    }
    if (manualHid) Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .55f)).clickable { manualHid = false }.padding(horizontal = 16.dp, vertical = 2.dp)) {
            Box(Modifier.width(26.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White))
        }
    }
    OfFileLayer(t, fileItems, fileOpen, { fileOpen = false }, info) { info = false }
    }
}

/** Mica blur behind the see-through Office window (Android 12+); off again when the chrome leaves. */
@Composable
private fun OfMicaBlur(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        val w = (view.context as? Activity)?.window
        if (on && w != null && android.os.Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius(40)
        onDispose { if (w != null && android.os.Build.VERSION.SDK_INT >= 31) w.setBackgroundBlurRadius(0) }
    }
}

/** Title bar colour of the Microsoft 365 look: Word blue, Excel green, PowerPoint red-orange, PDF red. */
val OfKind.brand: Color get() = when (this) { OfKind.WORD -> Color(0xFF2B579A); OfKind.EXCEL -> Color(0xFF217346); OfKind.PPT -> Color(0xFFC43E1C); OfKind.PDF -> Color(0xFFB30B00) }

/**
 * Shared Microsoft 365 window for every app (Excel, Word, PowerPoint, PDF): brand-coloured title bar (back, logo, [title], acts of group "Quick" such as undo / redo on the right),
 * scrollable tab row, slim ribbon strip of icon buttons for the selected tab (tap the selected tab again to fold it), [content], grey status bar.
 * [tabs] lists the tab order (default: the groups of [acts] in order of appearance, "Quick" excluded); [extra] is an optional row under the ribbon (Excel puts its formula bar there).
 */
@Composable
fun OfChromeM365(t: OfTheme, title: String, acts: List<OAct>, status: String, onBack: () -> Unit, tabs: List<String>? = null, extra: (@Composable () -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    val pal = t.pal; val view = LocalView.current; val brand = ofBrand(t); val top = ofTop(t); val topInk = ofInkOn(top)
    SideEffect {
        val w = (view.context as Activity).window
        // the system bars take the title bar's colour too: the status bar is painted, not left to the phone's theme
        val (bt, bb) = ofBars(t)
        w.statusBarColor = bt.toArgb(); w.navigationBarColor = bb.toArgb()
        WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = bt.luminance() > .5f; isAppearanceLightNavigationBars = bb.luminance() > .5f }
    }
    val quick = acts.filter { it.group == "Quick" }
    val fileOps = LocalOfFile.current
    var fileOpen by remember { mutableStateOf(false) }; var info by remember { mutableStateOf(false) }
    val fileItems = ofFileItems(acts, onBack) { info = true }
    val groups = (listOfNotNull("File".takeIf { fileOps != null }) + (tabs ?: acts.map { it.group }).filter { it != "Quick" && it != "File" }).distinct()
    var tab by remember(groups) { mutableStateOf(if ("Home" in groups) "Home" else groups.firstOrNull { it != "File" } ?: "") }
    var open by remember { mutableStateOf(true) }
    // the 365 window is dark grey with white text and icons, in the light and the dark look alike
    val tabInk = Color.White
    val below = ofBelow(t)   // solid grey: translucent layers over the black window left dark text on black
    // text and icons always follow the strip they sit on: white on a dark strip, near-black on a light one
    val ink = ofInkOn(below)
    val (autoHid, tapWatch) = ofAutoHide()
    // the arrow in the title bar hides the whole top block by hand; the little handle at the top brings it back
    // the top block is a drop-down: pull the thin grip under it down from its middle to open it, push it up to close it
    var pull by remember { mutableStateOf(1f) }
    var blockH by remember { mutableStateOf(0) }
    val gripDrag = androidx.compose.foundation.gestures.rememberDraggableState { d -> pull = (pull + d / blockH.coerceAtLeast(1)).coerceIn(0f, 1f) }
    val openP = if (autoHid) 0f else pull
    val hid = openP < .5f
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides ink) {
    // a tap on the screen (anywhere the page or the background takes it) shows or hides the top block, like the bar does
    Box(Modifier.fillMaxSize().then(tapWatch).pointerInput(Unit) { detectTapGestures(onTap = { pull = if (pull > .5f) 0f else 1f }) }) {
    Column(Modifier.fillMaxSize().background(below).navigationBarsPadding()) {
        // the top block shows only the part pulled out; its top edge stays fixed
        Box(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (blockH * openP).toDp() }).clipToBounds()) {
        Column(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true).onSizeChanged { blockH = it.height }) {
        Row(Modifier.fillMaxWidth().background(top).statusBarsPadding().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(22.dp), topInk) }
            OfTile(t.kind, 22.dp, true); Spacer(Modifier.width(8.dp))
            OfTitle(title, topInk)
            Box(Modifier.size(40.dp).clickable { pull = 0f }, contentAlignment = Alignment.Center) { OfChevron(topInk) }
            quick.forEach { a -> Box(Modifier.size(40.dp).alpha(if (a.enabled) 1f else .4f).clickable(enabled = a.enabled, onClick = a.click), contentAlignment = Alignment.Center) { Icon(a.icon, null, Modifier.size(20.dp), topInk) } }
        }
        Row(Modifier.fillMaxWidth().background(below).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
            groups.forEach { g ->
                val sel = g == tab
                Box(Modifier.clickable { if (g == "File") fileOpen = true else if (sel) open = !open else { tab = g; open = true } }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text(g, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, color = if (sel) tabInk else ink.copy(alpha = .78f))
                    if (sel) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(tabInk))
                }
            }
        }
        val custom = LocalOfRibbon.current[tab]
        if (open && custom != null) Box(Modifier.fillMaxWidth().background(below).border(1.dp, pal.line)) { custom() }
        else if (open) Row(Modifier.fillMaxWidth().background(below).border(1.dp, pal.line).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            acts.filter { it.group == tab }.forEach { a ->
                Column(Modifier.padding(horizontal = 1.dp).widthIn(min = 46.dp).clip(RoundedCornerShape(4.dp)).background(if (a.on) brand.copy(alpha = .18f) else Color.Transparent)
                    .alpha(if (a.enabled) 1f else .4f).clickable(enabled = a.enabled, onClick = a.click).padding(horizontal = 5.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(a.icon, null, Modifier.size(20.dp), if (a.on) tabInk else ink)
                    Text(a.label, color = ink, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        extra?.invoke()
        }
        }
        // the grip: a thin line under the top block, pulled down from its middle to open it and pushed up to close it
        // closed: the bar is gone and only this line stays where the arrow was
        if (openP == 0f) Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
        Box(Modifier.fillMaxWidth().height(14.dp).background(if (openP == 0f) Color.Transparent else below)
            .draggable(orientation = androidx.compose.foundation.gestures.Orientation.Vertical, state = gripDrag, onDragStopped = { pull = if (pull > .5f) 1f else 0f })
            .clickable { pull = if (pull > .5f) 0f else 1f }, contentAlignment = Alignment.Center) {
            OfChevron(ink.copy(alpha = .6f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(pal.canvas), content = content)
        if (!hid) Row(Modifier.fillMaxWidth().height(24.dp).background(below).border(1.dp, pal.line).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = ink.copy(alpha = .75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    OfFileLayer(t, fileItems, fileOpen, { fileOpen = false }, info) { info = false }
    }
    }
}

// ---- File menu (shared by every look) ------------------------------------------------------------

/** What the viewer activity offers to the File menu; the screens never see it, the chrome reads it from [LocalOfFile]. */
class OfFileOps(
    val name: String, val dirty: () -> Boolean,
    val share: () -> Unit, val openOther: () -> Unit, val newBlank: () -> Unit,
    val print: (() -> Unit)?, val info: () -> List<Pair<String, String>>,
    /** opens the Save as dialog; the given trigger (the screen's "Save a copy") then writes to the chosen file */
    val saveAs: ((() -> Unit) -> Unit)? = null,
    /** "Save as TXT": the viewer asks for a .txt target and writes [OfDoc.txt] there; null when not offered */
    val saveTxt: (() -> Unit)? = null,
)
val LocalOfFile = staticCompositionLocalOf<OfFileOps?> { null }


/** The title bar colour of the 365 and 2017 windows (their own is the app's colour): the accent colour, the app's colour, or black (Settings > Office). */
internal fun ofTop(t: OfTheme): Color = if (t.base == "amoled") Color.Black else when (OfficePrefs.bars) { 3 -> Color.Black; 1 -> Look.accent.copy(alpha = 1f); else -> t.kind.brand }

/** Colour of everything under the brand title bar: white, or near-black in the dark looks. Looks that have their own surfaces keep the ribbon colour. */
/** A chevron pointing down: the handle of the Microsoft 365 top bar, pulled down to open it and pushed up to close it. */
@Composable
internal fun OfChevron(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(20.dp, 12.dp)) {
        val w = size.width; val h = size.height; val sw = 2.dp.toPx()
        drawLine(color, androidx.compose.ui.geometry.Offset(w * .08f, h * .12f), androidx.compose.ui.geometry.Offset(w * .5f, h * .9f), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Square)
        drawLine(color, androidx.compose.ui.geometry.Offset(w * .5f, h * .9f), androidx.compose.ui.geometry.Offset(w * .92f, h * .12f), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Square)
    }
}

/** The app colour for the chrome (File tab, the on-state of the toggles, the opening screen): black-grey in the AMOLED look or with the black title bar. */
internal fun ofBrand(t: OfTheme): Color = if (t.base == "amoled" || OfficePrefs.bars == 3) Color(0xFF2B2B2B) else t.kind.brand

internal fun ofBelow(t: OfTheme) = if (OfficePrefs.bars == 3 || t.base == "amoled") Color.Black else if (t.base == "365") Color(0xFF2B2B2B) else if (t.base == "2017") (if (t.dark) ofDarkTint(t.kind.brand, .8f) else Color.White) else t.pal.ribbonBg

/** Opaque colours of the system status bar (top) and navigation bar (bottom): exactly what the chrome shows in the title bar and in the bottom-most (status) bar, translucent layers composited over the canvas. */
fun ofBars(t: OfTheme): Pair<Color, Color> {
    val pal = t.pal
    return when {
        t.base == "365" -> ofTop(t) to ofBelow(t) // opaque: a see-through layer over the black window made dark ink vanish
        t.base == "2017" -> ofTop(t) to ofBelow(t)
        t.classic -> pal.ribbonBg to pal.statusBg
        else -> pal.titleBg to pal.statusBg
    }
}

/** File name (ellipsized) followed by the saved / edited state. */
@Composable
private fun RowScope.OfTitle(title: String, ink: Color) {
    val ops = LocalOfFile.current
    Row(Modifier.weight(1f).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
        if (ops != null) Text(" \u2022 " + if (ops.dirty()) tr("Edited", "Düzenlendi") else tr("Saved", "Kaydedildi"), color = ink.copy(alpha = .8f), fontSize = 12.sp, maxLines = 1)
    }
}

/** The screen's own File actions (Save, Save a copy, ...) plus the shared ones; Close goes last, Save is greyed while nothing changed. */
@Composable
private fun ofFileItems(acts: List<OAct>, onBack: () -> Unit, showInfo: () -> Unit): List<OAct> {
    val ops = LocalOfFile.current ?: return emptyList()
    fun isClose(a: OAct) = a.label == "Close" || a.label == tr("Close", "Kapat")
    val own = acts.filter { it.group == "File" }.map { a ->
        if (a.label == "Save" || a.label == tr("Save", "Kaydet")) OAct(a.label, a.group, a.icon, a.on, a.enabled && ops.dirty(), a.png, a.cap, a.click) else a
    }
    val extra = buildList {
        val cp = own.firstOrNull { it.label == "Save a copy" || it.label == tr("Save a copy", "Kopya kaydet") }
        if (cp != null && ops.saveAs != null) add(OAct(tr("Save as", "Farklı kaydet"), "File", Icons.Outlined.SaveAs, cp.enabled, click = { ops.saveAs.invoke(cp.click) }))
        ops.saveTxt?.let { t -> add(OAct(tr("Save as TXT", "TXT olarak kaydet"), "File", Icons.Outlined.TextSnippet, click = t)) }
        add(OAct(tr("Open another file", "Başka dosya aç"), "File", Icons.Outlined.FolderOpen, click = ops.openOther))
        add(OAct(tr("New", "Yeni"), "File", Icons.Outlined.NoteAdd, click = ops.newBlank))
        add(OAct(tr("Share", "Paylaş"), "File", Icons.Outlined.Share, click = ops.share))
        ops.print?.let { p -> add(OAct(tr("Print", "Yazdır"), "File", Icons.Outlined.Print, click = p)) }
        add(OAct(tr("Info", "Bilgi"), "File", Icons.Outlined.Info, click = showInfo))
    }.filter { e -> own.none { it.label == e.label } }
    return own.filterNot(::isClose) + extra + own.filter(::isClose).ifEmpty { listOf(OAct(tr("Close", "Kapat"), "File", Icons.Outlined.Close, click = onBack)) }
}

/** Full-height File panel (365 / 2017 / other modern looks) and the properties dialog. */
@Composable
private fun OfFileLayer(t: OfTheme, items: List<OAct>, backstage: Boolean, onClose: () -> Unit, info: Boolean, onInfoClose: () -> Unit) {
    val ops = LocalOfFile.current ?: return
    val pal = t.pal
    if (backstage) {
        BackHandler(true) { onClose() }
        val classic = t.classic || t.base == "2003" || t.base == "95"
        val shape = if (classic) RectangleShape else RoundedCornerShape(10.dp)
        val bg = if (classic) pal.ribbonBg else ofBelow(t)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .18f)).pointerInput(Unit) { detectTapGestures { onClose() } }) {
            Column(Modifier.statusBarsPadding().padding(top = 84.dp, start = 8.dp).width(230.dp).heightIn(max = 460.dp)
                .then(if (classic) Modifier.background(bg).bevel(true).padding(2.dp) else Modifier.clip(shape).background(bg).border(1.dp, pal.line, shape))
                .pointerInput(Unit) { detectTapGestures { } }.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                items.forEach { a ->
                    Row(Modifier.fillMaxWidth().alpha(if (a.enabled) 1f else .4f).clickable(enabled = a.enabled) { onClose(); a.click() }
                        .padding(horizontal = 14.dp, vertical = if (classic) 8.dp else 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(a.icon, null, Modifier.size(20.dp), pal.ink); Spacer(Modifier.width(12.dp))
                        Text(a.label, color = pal.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    if (info) WinAlert(onDismissRequest = onInfoClose, title = { Text(tr("Info", "Bilgi")) },
        text = { Column { ops.info().forEach { (k, v) -> Row(Modifier.padding(vertical = 3.dp)) { Text(k, fontSize = 12.sp, modifier = Modifier.width(84.dp)); Text(v, fontSize = 13.sp) } } } },
        confirmButton = { androidx.compose.material3.TextButton(onInfoClose) { Text("OK") } })
}

@Composable
internal fun OfActIcon(t: OfTheme, a: OAct, size: Dp, tint: Color) {
    val ctx = LocalContext.current
    val bmp = if (t.classic && a.png != null) ThemeIcons.bitmap(ctx, if (t.base == "95") "win95" else "xp", a.png) else null
    if (bmp != null) Image(bmp, null, Modifier.size(size)) else Icon(a.icon, null, Modifier.size(size), tint)
}

/** Title-bar tile: on the brand bar a white tile with the app letter (stays legible), otherwise the era logo of the look. */
@Composable
fun OfTile(k: OfKind, size: Dp, onBrand: Boolean, base: String = ofResolveId(OfficePrefs.themeFor(k)).removeSuffix("d")) {
    if (!onBrand) { OfLogo(k, size, base = base); return }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .24f)).background(Color.White), contentAlignment = Alignment.Center) {
        val fs = with(LocalDensity.current) { (size * (if (k.letter.length > 1) .3f else .56f)).toSp() }
        Text(k.letter, color = k.accent, fontSize = fs, fontWeight = FontWeight.Bold, maxLines = 1, style = ofCenterStyle(fs))
    }
}

/** The Office app logo drawn as vectors in the style of the Office look [base] (see ofGlyph in OfficeSuiteLogo.kt). */
@Composable
fun OfLogo(k: OfKind, size: Dp, modifier: Modifier = Modifier, base: String = ofResolveId(OfficePrefs.themeFor(k)).removeSuffix("d")) {
    val d = LocalDensity.current
    val (lb, lc) = ofLetterBox(k, base)
    Box(modifier.size(size)) {
        Canvas(Modifier.fillMaxSize()) { ofGlyph(k, base) }
        Box(Modifier.offset(size * lb[0], size * (lb[2] - .5f)).size(size * lb[1], size), contentAlignment = Alignment.Center) {
            val fs = with(d) { (size * lb[3]).toSp() }
            Text(k.letter, color = lc, fontSize = fs, fontWeight = FontWeight.Bold, maxLines = 1, style = ofCenterStyle(fs))
        }
    }
}

/** Document file icon of the app's type (ribbon with the extension); see OfDocTypeIcon. */
@Composable
fun OfFileIcon(k: OfKind, size: Dp, modifier: Modifier = Modifier, base: String = ofResolveId(OfficePrefs.themeFor(k)).removeSuffix("d"), ext: String = k.ext) = OfDocTypeIcon(ext, size, modifier, base)

/** The loading box shown for about a second while a document opens; each Office look has its own opening animation. */
@Composable
fun OfSplash(t: OfTheme, file: String = "") {
    val pal = t.pal; val k = t.kind; val base = t.base; val dk = t.dark
    val p = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(700, easing = LinearEasing)) }
    LaunchedEffect(Unit) {
        if (base == "material" || base == "amoled") enter.animateTo(1f, androidx.compose.animation.core.spring(.45f, 220f))
        else enter.animateTo(1f, tween(if (base == "oneui") 450 else 340, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val ctx = LocalContext.current
    val inf = rememberInfiniteTransition(label = "splash")
    val ph by inf.animateFloat(0f, 5f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "ph")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    val view = LocalView.current
    SideEffect { WindowCompat.getInsetsController((view.context as Activity).window, view).isAppearanceLightStatusBars = pal.canvas.luminance() > .5f }
    val opening = tr("Opening ", "Açılıyor: ") + file.ifEmpty { "…" } + if (file.isNotEmpty()) "…" else ""
    val e = enter.value
    if (base == "365" || base == "2017") {
        // 365: rounded box, logo pops in, wave of dots. 2017: square box, title fades up, thin progress line
        val bg = if (dk) androidx.compose.ui.graphics.lerp(ofBrand(t), Color.Black, .62f) else ofBrand(t)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(300.dp, 190.dp).clip(RoundedCornerShape(if (base == "365") 10.dp else 0.dp)).background(bg)) {
                Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OfLogo(k, 18.dp); Spacer(Modifier.width(6.dp)); Text("Office", color = Color.White, fontSize = 13.sp)
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (base == "365") {
                        OfFileIcon(k, 54.dp, ext = file.substringAfterLast('.', k.ext), modifier = Modifier.graphicsLayer { scaleX = .4f + .6f * e; scaleY = .4f + .6f * e; alpha = e })
                        Spacer(Modifier.height(8.dp))
                        Text(k.app, color = Color.White.copy(alpha = e), fontSize = 20.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            for (i in 0 until 5) Box(Modifier.size(5.dp).graphicsLayer { translationY = -6f * (1f - kotlin.math.abs(ph - i).coerceAtMost(1f)) * density }.alpha(.25f + .75f * (1f - kotlin.math.abs(ph - i).coerceAtMost(1f))).background(Color.White, androidx.compose.foundation.shape.CircleShape))
                        }
                    } else {
                        Text(k.app, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light, modifier = Modifier.graphicsLayer { alpha = e; translationY = (1f - e) * 18f * density })
                    }
                }
                if (base == "2017") Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p.value).height(3.dp).background(Color.White.copy(alpha = .9f)))
                Text(opening, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp, end = 12.dp))
            }
        }
        return
    }
    if (base == "2013") {
        // white card; the swoosh slides in from the right, title fades in
        val card = if (dk) Color(0xFF2D2D30) else Color.White
        val tx = if (dk) Color(0xFFEEEEEE) else Color(0xFF222222)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(300.dp, 190.dp).shadow(6.dp).background(card).clipToBounds()) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = (1f - e) * size.width }) {
                    val w = size.width; val h = size.height
                    val sw = androidx.compose.ui.graphics.Path().apply { moveTo(w * .25f, h); cubicTo(w * .5f, h * .62f, w * .75f, h * .8f, w, h * .5f); lineTo(w, h); close() }
                    drawPath(sw, Brush.horizontalGradient(listOf(ofBrand(t).copy(alpha = .15f), ofBrand(t))))
                }
                Column(Modifier.padding(start = 18.dp, top = 20.dp).alpha(e)) {
                    Text("Microsoft®", color = tx.copy(alpha = .75f), fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(k.app, color = tx, fontSize = 34.sp, fontWeight = FontWeight.Light)
                        Text(" 2013", color = tx.copy(alpha = .7f), fontSize = 20.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(tr("Starting", "Başlatılıyor") + ".".repeat(1 + ph.toInt() % 3), color = tx.copy(alpha = .85f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OfLogo(k, 16.dp); Spacer(Modifier.width(5.dp)); Text("Office", color = tx.copy(alpha = .75f), fontSize = 12.sp)
                }
                Text(tr("Cancel", "İptal"), color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).clickable { (ctx as? Activity)?.finish() }.padding(12.dp))
            }
        }
        return
    }
    val modern = base == "material" || base == "amoled" || base == "oneui" || base == "coloros"
    val boxBg = when (base) { "2003" -> Color(0xFF1A4FC8); "95" -> Color(0xFFC0C0C0); "material", "amoled" -> pal.accent; else -> pal.accent }
    val ink = if (base == "95") Color.Black else Color.White
    val shape = RoundedCornerShape(when (base) { "95" -> 0.dp; "2003" -> 6.dp; "oneui" -> 30.dp; "coloros" -> 26.dp; else -> 16.dp })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (base == "coloros") Canvas(Modifier.size(300.dp)) { // breathing glow behind the box
            drawCircle(Brush.radialGradient(listOf(pal.accent.copy(alpha = .35f * (.4f + .6f * pulse)), Color.Transparent)), radius = size.width * (.42f + .08f * pulse))
        }
        Column(Modifier.width(300.dp)
            .then(if (base == "95") Modifier.background(boxBg).bevel(true) else Modifier.shadow(8.dp, shape).clip(shape).background(boxBg))
            .then(if (base == "oneui" || base == "coloros") Modifier.graphicsLayer { alpha = e; scaleX = .88f + .12f * e; scaleY = .88f + .12f * e } else Modifier)
            .padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val ls = when (base) { "material", "amoled" -> e; "coloros" -> 1f + .06f * pulse; else -> 1f }
            Box(contentAlignment = Alignment.Center) {
                if (base == "oneui") Canvas(Modifier.size(88.dp)) {
                    val sw = 3.dp.toPx(); val st = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawArc(Color.White.copy(alpha = .2f), 0f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = st)
                    drawArc(Color.White, -90f, 360f * p.value, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = st)
                }
                OfFileIcon(k, 72.dp, ext = file.substringAfterLast('.', k.ext), modifier = Modifier.graphicsLayer { scaleX = ls; scaleY = ls })
            }
            Spacer(Modifier.height(14.dp))
            Text("Office", color = ink.copy(alpha = .75f), fontSize = 13.sp)
            Text(k.app, color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            if (file.isNotEmpty()) Text(file, color = ink.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(18.dp))
            if (t.classic) { // segmented blocks, Luna green / navy
                val n = 20; val on = (p.value * n).toInt()
                Row(Modifier.fillMaxWidth().height(14.dp).then(if (base == "95") Modifier.bevel(false) else Modifier.background(Color.White, RoundedCornerShape(3.dp))).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0 until n) Box(Modifier.weight(1f).fillMaxHeight().background(if (i < on) (if (base == "95") Color(0xFF000080) else Color(0xFF2BA52B)) else Color.Transparent))
                }
            } else if (base == "material" || base == "amoled") {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = .18f))) {
                    Box(Modifier.fillMaxWidth(p.value).fillMaxHeight().background(Color.White))
                }
            } else if (base == "coloros") { // soft pill that breathes
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = .18f))) {
                    Box(Modifier.fillMaxWidth(p.value).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .6f + .4f * pulse)))
                }
            } else Spacer(Modifier.height(4.dp))
        }
    }
}

/** A card telling the user why a file cannot be shown. */
@Composable
fun OfErrorCard(t: OfTheme, msg: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(if (t.classic) 0.dp else 12.dp)).background(t.pal.paper).border(1.dp, t.pal.line).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Description, null, Modifier.size(40.dp), t.kind.accent)
            Spacer(Modifier.height(8.dp))
            Text(tr("This file cannot be shown", "Bu dosya gösterilemiyor"), color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(msg, color = Color(0xFF555555), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings > Office
// ---------------------------------------------------------------------------------------------

/** A tiny drawing of a look with its name under it. */
@Composable
fun OfPreview(id: String, k: OfKind, sel: Boolean, onClick: () -> Unit) {
    val rid = ofResolveId(id); val pal = ofPal(rid, k); val classic = rid == "2003" || rid == "95"
    Column(Modifier.width(88.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(80.dp, 56.dp).clip(RoundedCornerShape(4.dp)).border(if (sel) 2.dp else 1.dp, if (sel) Look.accent else T.line, RoundedCornerShape(4.dp))) {
            val w = size.width; val h = size.height
            drawRect(pal.canvas)
            drawRect(pal.paper, Offset(w * .3f, h * .46f), Size(w * .4f, h * .54f))
            if (classic) {
                drawRect(if (rid == "95") Color(0xFF000080) else Color(0xFF0A5BE0), Offset.Zero, Size(w, h * .16f))
                drawRect(pal.ribbonBg, Offset(0f, h * .16f), Size(w, h * .26f))
                for (i in 0..5) drawRect(pal.ink.copy(alpha = .55f), Offset(w * (.04f + i * .09f), h * .27f), Size(w * .06f, h * .1f))
            } else {
                drawRect(pal.titleBg, Offset.Zero, Size(w, h * .16f))
                drawRect(pal.tabBg, Offset(0f, h * .16f), Size(w, h * .12f))
                drawRect(pal.tabSelInk, Offset(w * .06f, h * .2f), Size(w * .12f, h * .05f))
                drawRect(pal.ribbonBg, Offset(0f, h * .28f), Size(w, h * .16f))
                for (i in 0..5) drawRect(pal.ink.copy(alpha = .4f), Offset(w * (.06f + i * .1f), h * .32f), Size(w * .06f, h * .08f))
            }
            drawRect(pal.statusBg, Offset(0f, h * .92f), Size(w, h * .08f))
            // top half light, bottom half dark: the middle fades from one to the other, with a thin wire line across it
            val dark = Color(0xFF111111)
            drawRect(Brush.verticalGradient(0f to Color.Transparent, .38f to Color.Transparent, .62f to dark.copy(alpha = .78f), 1f to dark.copy(alpha = .78f)))
            drawLine(Color.White.copy(alpha = .85f), Offset(0f, h * .5f), Offset(w, h * .5f), strokeWidth = 1f)
        }
        Text(ofThemeName(id), color = T.text, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Settings > General > Office viewer: dark mode, one look for all apps, and one look per app. */
@Composable
fun OfficeSettings() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { OfficePrefs.load(ctx) }
    PCard {
        Text(tr("Office viewer", "Office görüntüleyici"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("All apps", "Tüm uygulamalar"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OF_PICK_IDS.forEach { id -> OfPreview(id, OfKind.WORD, OfKind.values().all { OfficePrefs.themeFor(it) == id }) { OfficePrefs.applyAll(ctx, id) } }
        }
    }
    PCard {
        var auto by remember { mutableStateOf(ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).getBoolean("of_autohide", true)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Hide the bars sideways", "Yan çevirince çubukları gizle"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("A tap shows them for 2 seconds", "Dokununca 2 saniye görünür"), color = T.sub, fontSize = 12.sp)
            }
            PSwitch(auto, { auto = it; ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putBoolean("of_autohide", it).apply() })
        }
    }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Office Assistant", "Office Yardımcısı"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PSwitch(ClippyPrefs.on, { ClippyPrefs.setOn(ctx, it) })
        }
        if (ClippyPrefs.on) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(ClippyPrefs.who == "clippy", "Clippy") { ClippyPrefs.setWho(ctx, "clippy") }
            PChip(ClippyPrefs.who == "links", "Links") { ClippyPrefs.setWho(ctx, "links") }
        }
    }
    PCard {
        var icDlg by remember { mutableStateOf(false) }
        SetRow({ OfSuiteLogo(20.dp, tint = T.text) }, tr("Office icon colour", "Office simge rengi"),
            if (OfficePrefs.iconAccent) tr("Accent", "Vurgu") else tr("Black outline", "Siyah çerçeve"), { icDlg = true })
        if (icDlg) SetPickDialog(tr("Office icon colour", "Office simge rengi"), listOf(false to tr("Black outline", "Siyah çerçeve"), true to tr("Accent", "Vurgu")), OfficePrefs.iconAccent, { OfficePrefs.setIconAccent(ctx, it) }, { icDlg = false })
    }
    PCard {
        var barsDlg by remember { mutableStateOf(false) }
        val barNames = listOf(0 to tr("As the look draws it", "Görünümün kendi rengi"), 1 to tr("Accent colour", "Vurgu rengi"), 2 to tr("App colour (Word blue, PDF red ...)", "Uygulama rengi (Word mavi, PDF kırmızı ...)"), 3 to tr("AMOLED black (bar, tabs, ribbon)", "AMOLED siyah (üst çubuk, sekmeler, şerit)"))
        SetRow({ Box(Modifier.size(20.dp).background(when (OfficePrefs.bars) { 3 -> Color.Black; 2 -> Color(0xFF2B579A); 1 -> Look.accent; else -> Color(0xFF8A8A8A) }, androidx.compose.foundation.shape.CircleShape).border(1.dp, T.line, androidx.compose.foundation.shape.CircleShape)) },
            tr("Title bar and ribbon", "Üst çubuk ve şerit"), barNames.first { it.first == OfficePrefs.bars }.second, { barsDlg = true })
        if (barsDlg) SetPickDialog(tr("Title bar and ribbon", "Üst çubuk ve şerit"), barNames, OfficePrefs.bars, { OfficePrefs.setBars(ctx, it) }, { barsDlg = false })
    }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Dark pages (AMOLED)", "Koyu sayfalar (AMOLED)"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PSwitch(OfficePrefs.darkPages, { OfficePrefs.setDarkPages(ctx, it) })
        }
        if (OfficePrefs.darkPages) {
            val bgs = listOf(0xFF000000.toInt() to tr("Black", "Siyah"), 0xFF121212.toInt() to tr("Dark grey", "Koyu gri"), 0xFF1A2330.toInt() to tr("Dark blue-grey", "Koyu mavi-gri"), 0xFF2B2118.toInt() to tr("Sepia-dark", "Koyu sepya"))
            val fgs = listOf(0xFFFFFFFF.toInt() to tr("White", "Beyaz"), 0xFFCFCFCF.toInt() to tr("Light grey", "Açık gri"), 0xFFF2E8D5.toInt() to tr("Warm white", "Sıcak beyaz"))
            var pick by remember { mutableStateOf(0) }
            SetRow({ Box(Modifier.size(20.dp).background(OfficePrefs.pageBgC, androidx.compose.foundation.shape.CircleShape).border(1.dp, T.line, androidx.compose.foundation.shape.CircleShape)) },
                tr("Page colour", "Sayfa rengi"), bgs.firstOrNull { it.first == OfficePrefs.pageBg }?.second, { pick = 1 })
            SetRow({ Box(Modifier.size(20.dp).background(OfficePrefs.pageFgC, androidx.compose.foundation.shape.CircleShape).border(1.dp, T.line, androidx.compose.foundation.shape.CircleShape)) },
                tr("Text colour", "Yazı rengi"), fgs.firstOrNull { it.first == OfficePrefs.pageFg }?.second, { pick = 2 })
            if (pick == 1) SetPickDialog(tr("Page colour", "Sayfa rengi"), bgs, OfficePrefs.pageBg, { OfficePrefs.setPageBg(ctx, it) }, { pick = 0 }) { Box(Modifier.size(20.dp).background(Color(it), androidx.compose.foundation.shape.CircleShape).border(1.dp, T.line, androidx.compose.foundation.shape.CircleShape)) }
            if (pick == 2) SetPickDialog(tr("Text colour", "Yazı rengi"), fgs, OfficePrefs.pageFg, { OfficePrefs.setPageFg(ctx, it) }, { pick = 0 }) { Box(Modifier.size(20.dp).background(Color(it), androidx.compose.foundation.shape.CircleShape).border(1.dp, T.line, androidx.compose.foundation.shape.CircleShape)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Dark PDF pages", "Koyu PDF sayfaları"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PSwitch(OfficePrefs.darkPdf, { OfficePrefs.setDarkPdf(ctx, it) })
        }
    }
    OfDefaultAppCard()
    FadeCollapse(rows = OfKind.values().size, key = "office_apps") { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    OfKind.values().forEach { k ->
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) { OfLogo(k, 22.dp); Spacer(Modifier.width(8.dp)); Text(k.app, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OF_PICK_IDS.forEach { id -> OfPreview(id, k, OfficePrefs.themeFor(k) == id) { OfficePrefs.setTheme(ctx, k, id) } }
            }
        }
    }
    } }
}

private val OF_DEFAULT_MIMES = listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/pdf")

/** "Default app": opens the system default-apps page; shows a check while Pulse Office answers a docx / pdf VIEW. */
@Composable
private fun OfDefaultAppCard() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lo = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lo) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lo.lifecycle.addObserver(ob); onDispose { lo.lifecycle.removeObserver(ob) }
    }
    val isDef = remember(tick) {
        OF_DEFAULT_MIMES.all { m ->
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.umutk.pulse.files/x"), m)
            runCatching { ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == ctx.packageName }.getOrDefault(false)
        }
    }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Default app", "Varsayılan uygulama"), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (isDef) Icon(Icons.Outlined.Check, null, tint = Look.accent, modifier = Modifier.size(20.dp))
        }
        PButton(tr("Set as default", "Varsayılan yap"), Modifier.fillMaxWidth()) {
            val pk = Uri.parse("package:${ctx.packageName}")
            val tries = listOf(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS", pk),
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pk))
            for (i in tries) { if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break }
        }
    }
}

/** Readable text colour on [bg]: white on dark, near-black on light. */
fun ofInkOn(bg: Color): Color = if (bg.luminance() < .5f) Color(0xFFF3F2F1) else Color(0xFF201F1E)
