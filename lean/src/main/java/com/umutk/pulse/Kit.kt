package com.umutk.pulse

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.features.ThemeIcons

/*
 * What Pulse's look system (PTheme, Controls, OemLooks, ColorOsLooks, OneUiLooks, Fluent*, Mica: copied as they are by lean/build.gradle.kts) needs from the rest of Pulse, for the lean apps.
 * The look itself is not rewritten here: every look (Windows 95, XP, 7, 10, 11, Material, AMOLED, One UI, ColorOS ...) draws as in Pulse. PButton, PCard and the icon groups below are copies of
 * Pulse's (Pages.kt, Ui.kt); the icon draws from the Fluent vectors and the Material drawables (the bitmap packs of 95 / XP / 7 are not in a lean app, the vector icon is drawn instead).
 */

// ---- the small parts of Pulse that the look files ask for ----------------------------------------------------------------------------------------------------------------

/** The resolved look, as everywhere in Pulse. */
val T get() = Look.current

/** Pulse's options, as far as the look files read them: the old Windows pop-ups on, AMOLED black behind Windows 11 dark. */
object Opt {
    var amoledBg by mutableStateOf(true)
    val authentic: Boolean get() = true
    val oldPopups: Boolean get() = true
    var lang by mutableStateOf("auto")
}

/** English or Turkish by the phone's language (Pulse's own tr also reads its language setting). */
fun tr(en: String, turkish: String = en): String = if (java.util.Locale.getDefault().language == "tr") turkish else en

fun pageName(id: String, en: String): String = en
class Pg(val id: String, val label: String, val icon: String)
class MenuHost
@Composable fun MenuOverflow(menus: MenuHost, tint: Color) {}

val pageGutter: Dp get() = if (T.material) 16.dp else 12.dp
val pageBg: Color get() = T.bg

@Composable
fun Center(text: String, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = T.sub, fontSize = 14.sp) }

/** An icon of the left menu: the look's own icon. */
@Composable
fun PNavIcon(k: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) { PIcon(k, tint, size, modifier) }

@Composable
fun PHead(text: String, trailing: String = "", guide: String = "") {
    if (oem) { OemTitle(text); return }
    if (trailing.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(trailing, color = T.sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

// ---- copies of Pulse's own (Pages.kt, Ui.kt, MainActivity.kt) ----------------------------------------------------------------------------------------------------------

/** True where the controls lie on pure black (the video player): buttons and fields get a thin edge on black instead of a grey fill. */
val LocalBlackControls = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun PButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, compact: Boolean = false, onClick: () -> Unit) {
    val t = T
    if (oem) {
        val inDlg = InOemDialog.current
        val acc = Look.accent.copy(alpha = 1f)
        val fg = if (inDlg) LocalContentColor.current else if (danger) Color(0xFFC42B1C) else if (oneUi) t.text else onAccent(acc)
        val bg = if (inDlg) Color.Transparent else if (oneUi) t.text.copy(alpha = .08f) else if (danger) Color(0xFFC42B1C).copy(alpha = .12f) else acc
        Row(modifier.heightIn(min = 40.dp).background(bg, CircleShape).clickable(onClick = onClick).padding(horizontal = if (compact) 10.dp else 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
            Text(text, color = if (!inDlg && colorOs && danger) Color(0xFFC42B1C) else fg, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    } else if (win95Authentic) {
        // 95 push button: 75x23 grey bevel that sinks while held; the outer padding keeps a ~40 dp touch target
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(modifier.clickable(src, null, onClick = onClick).padding(vertical = 9.dp), propagateMinConstraints = true) {
            Box(Modifier.defaultMinSize(75.dp, 23.dp).background(Color(0xFFC0C0C0)).bevel(!down).padding(horizontal = 8.dp, vertical = 3.dp).offset(if (down) 1.dp else 0.dp, if (down) 1.dp else 0.dp), contentAlignment = Alignment.Center) {
                Text(text, color = Color.Black, fontSize = 12.sp, maxLines = 1)
            }
        }
    } else if (win7Authentic) {
        // Aero push button: grey gloss, blue glow while held; the outer padding keeps a ~40 dp touch target
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(modifier.clickable(src, null, onClick = onClick).padding(vertical = 7.dp), propagateMinConstraints = true) {
            Row(Modifier.defaultMinSize(75.dp, 26.dp).aero7(down).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else Color.Black); Spacer(Modifier.width(6.dp)) }
                Text(text, color = if (danger) Color(0xFFB00020) else Color.Black, fontSize = 12.sp, maxLines = 1)
            }
        }
    } else if (xpAuthentic) {
        // Luna push button: white to beige, navy edge, orange rim while held; ~40 dp touch target
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        Box(modifier.clickable(src, null, onClick = onClick).padding(vertical = 7.dp), propagateMinConstraints = true) {
            Row(Modifier.defaultMinSize(75.dp, 26.dp).luna(down).padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else Color.Black); Spacer(Modifier.width(6.dp)) }
                Text(text, color = if (danger) Color(0xFFB00020) else Color.Black, fontSize = 12.sp, maxLines = 1)
            }
        }
    } else if (t.tabs) {
        // Windows 10 gets a hint of rounding, far less than Material
        val r = if (t.id == "win10") 4.dp else t.radius
        Box(modifier.clip(RoundedCornerShape(r)).background(t.bg).then(if (t.bevel) Modifier.bevel(true) else Modifier.border(1.dp, t.line, RoundedCornerShape(r))).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (danger) Color(0xFFB00020) else t.text); Spacer(Modifier.width(6.dp)) }
            Text(text, color = InWinBtnText.current ?: if (danger) Color(0xFFB00020) else t.text, fontSize = 13.sp)
        }
    } else {
        // single outline, no accent fill: the same for every look
        val shape = if (t.material) CircleShape else RoundedCornerShape(if (t.id == "win11") 6.dp else t.radius.coerceAtMost(12.dp))
        // inside a Windows dialog button the colour is the one that reads on that button (white on the accent fill)
        val fg = InWinBtnText.current ?: if (danger) Color(0xFFC42B1C) else t.text
        if (t.material && InMatDialogBtn.current) {
            // a Material 3 dialog action: a text button, no frame (its icon before the word, as the other buttons)
            val c = if (danger) fg else MaterialTheme.colorScheme.primary
            Row(modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = c); Spacer(Modifier.width(6.dp)) }
                Text(text, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            return
        }
        if (t.material) {
            // a tonal button: a soft fill instead of a heavy outline; on a black screen (LocalBlackControls) a thin edge on black, no grey fill
            val black = LocalBlackControls.current
            Row(modifier.clip(CircleShape).then(if (black) Modifier.border(1.dp, fg.copy(alpha = .28f), CircleShape) else Modifier.background(fg.copy(alpha = if (danger) .12f else .09f)))
                .clickable(onClick = onClick).padding(horizontal = if (compact) 10.dp else 24.dp, vertical = if (compact) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                // the icon was given but never drawn here
                if (icon != null) { Icon(icon, null, Modifier.size(if (compact) 16.dp else 18.dp), tint = fg); Spacer(Modifier.width(8.dp)) }
                Text(text, color = fg, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            return
        }
        Box(modifier.border(1.dp, if (danger) fg else if (t.id == "amoled") Color.White else t.line, shape).clickable(onClick = onClick).padding(horizontal = if (compact) 8.dp else if (t.material) 24.dp else 14.dp, vertical = if (t.material) 10.dp else 6.dp), contentAlignment = Alignment.Center) {
            Text(text, color = fg, fontSize = if (compact) 12.sp else if (t.material) 14.sp else 13.sp, fontWeight = if (t.material) FontWeight.Medium else null, maxLines = 1)
        }
    }
}

/** A card in the current theme: rounded for 11 / Material, square with an edge for the old ones. */
@Composable
fun PCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = T
    val shape = RoundedCornerShape(if (colorOs) 20.dp else if (t.material) t.radius.coerceIn(16.dp, 28.dp) else t.radius) // M3 cards: 16 to 28 dp corners
    val fx = if (t.id == "win11") Look.effect else "none"
    val surface = when {
        // Material first: its glassA is for the window backdrop only; cards stay tonal with a soft elevation
        // One UI: big soft white card, no shadow, no edge; ColorOS: flat card with a hairline edge
        oneUi -> Modifier.background(t.panel, shape)
        colorOs -> Modifier.background(t.panel, shape)
        t.material -> Modifier.shadow(2.dp, shape).background(t.panel, shape)
        // Win7 group box: white with a pale blue-grey rounded edge
        win7Authentic -> Modifier.background(Color.White, shape).border(1.dp, Color(0xFFD5DFE5), shape)
        // 95 group box: grey, etched edge (grey line with a white line 1 px inside)
        win95Authentic -> Modifier.background(Color(0xFFC0C0C0)).drawBehind {
            val p = 1.dp.toPx()
            drawRect(Color.White, Offset(p, p), Size(size.width - p, size.height - p), style = Stroke(p))
            drawRect(Color(0xFF808080), Offset(0f, 0f), Size(size.width - p, size.height - p), style = Stroke(p))
        }
        // liquid glass: the panel is see-through with a light edge (Win 7 Aero keeps its bevel-free look, Win 95 its bevel)
        t.glassA > 0f -> Modifier.background(t.panel.copy(alpha = t.glassA), shape).then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, glassEdge(t), shape))
        t.tabs -> Modifier.background(t.bg, shape).then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, t.line))
        fx != "none" -> Modifier.layer(fx, shape).border(1.dp, t.line, shape)
        else -> Modifier.background(t.panel.copy(alpha = .9f), shape).border(1.dp, t.line, shape)
    }
    Column(modifier.fillMaxWidth().then(surface).padding(if (oneUi) 20.dp else if (t.material) 16.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** The icon groups of the Windows 11 look; each has its own style (Settings > Look > Icons). */
/** Set while a style preview is drawn: the icons inside use this style instead of the saved one. */
val LocalIconStyleOverride = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** Set while the left sidebar draws its items: their icons follow the sidebar's own style. */
val LocalInSidebar = androidx.compose.runtime.compositionLocalOf { false }

enum class IconCat(val id: String, val en: String, val trName: String) {
    // the left sidebar's own icons, apart from the menus and buttons above it
    SIDEBAR("sidebar", "Left sidebar", "Sol menü"),
    MENU("menu", "Menus, pages and buttons", "Menüler, sayfalar ve düğmeler"),
    APPS("apps", "Apps", "Uygulamalar"),
    TOOLS("tools", "Tools", "Araçlar"),
    SCREENS("screens", "Icons inside screens", "Ekranların içindeki ikonlar"),
}

/**
 * "original": the icon the old app had (the Windows 11 icon pack) wherever there is one, the new Fluent glyph where the old app had none;
 * "colour": the Fluent colour pictures; "line": thin outline glyphs. Apps and tools never had a pack icon, so they start with the colour pictures.
 */
object IconStyle {
    val map = androidx.compose.runtime.mutableStateMapOf<String, String>()
    fun load(sp: android.content.SharedPreferences) { IconCat.entries.forEach { c -> sp.getString("icon_${c.id}", null)?.let { map[c.id] = it } } }
    fun of(c: IconCat): String = map[c.id] ?: if (Look.winIconLine) "line" else if (c == IconCat.APPS || c == IconCat.TOOLS || Look.winIconColor) "colour" else "original"
    fun choices(c: IconCat): List<Triple<String, String, String>> = when (c) {
        IconCat.APPS, IconCat.TOOLS -> listOf(Triple("colour", "Fluent colour", "Renkli Fluent"), Triple("line", "Line", "Çizgi"))
        IconCat.SCREENS -> listOf(Triple("original", "Fluent", "Fluent"), Triple("line", "Line", "Çizgi"))
        else -> listOf(Triple("original", "Original", "Orijinal"), Triple("colour", "Fluent colour", "Renkli Fluent"), Triple("line", "Line", "Çizgi"))
    }
}

val XP_ICON_ALIAS = mapOf("power" to "run", "speed" to "performance", "bookmark" to "heart", "view" to "search", "copy" to "folder", "paste" to "folder", "rename" to "details", "extract" to "folder")

/** The Material looks follow the system dark mode, the wallpaper colours (Android 12+, when Material You is on), the chosen accent and corner size. */
@Composable
fun effective(t: PTheme): PTheme {
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    val dark = when (Look.mode) { "dark" -> true; "light" -> false; else -> sysDark }
    if (t.id == "mica2" || t.id == "acrylic2") return glassTwin(t, dark)
    if (!t.material) return if (dark) darkTwin(t) ?: t else if (t.id == "win11") lightTwin(t) else t
    if (t.id == "oneui" || t.id == "coloros") {
        val one = t.id == "oneui"
        return if (!dark) PTheme(t.id, t.name, false, t.bg, t.panel, t.text, t.sub, t.accent, t.line, t.radius, material = true, glassA = t.glassA)
        else if (one) PTheme(t.id, t.name, true, Color(0xFF000000), Color(0xFF161618), Color(0xFFF2F2F2), Color(0xFF9A9AA0), t.accent, Color(0xFF262628), t.radius, material = true, glassA = t.glassA)
        else PTheme(t.id, t.name, true, Color(0xFF0B1210), Color(0xFF16211D), Color(0xFFEEF6F1), Color(0xFF8FA79B), Color(0xFF2EDC8E), Color(0xFF24342D), t.radius, material = true, glassA = t.glassA)
    }
    val amoled = t.id == "amoled"
    val dynamic = if (!amoled && Look.dynamic && android.os.Build.VERSION.SDK_INT >= 31) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else null
    // AMOLED has one fixed accent, white: no wallpaper colour and no swatch.
    // Material You's default accent is white too (wallpaper colour only when a swatch is picked)
    val accent = if (amoled || (t.id == "material" && Look.accentIdx == -1)) Color.White else dynamic?.primary ?: MaterialAccents.getOrNull(Look.accentIdx) ?: t.accent
    val r = Look.cornerDp
    return when {
        dynamic != null -> PTheme(t.id, t.name, dark, if (dark) Color(0xFF050505) else dynamic.background, if (dark) Color(0xFF111111) else dynamic.surfaceContainer,
            dynamic.onBackground, dynamic.onSurfaceVariant, accent, dynamic.outlineVariant, r, material = true, glassA = t.glassA)
        amoled -> PTheme(t.id, t.name, true, t.bg, t.panel, t.text, t.sub, accent, t.line, r, material = true, glassA = t.glassA)
        // Material You off: the fixed Material 3 palette, light or dark
        dark -> PTheme(t.id, t.name, true, Color(0xFF141218), Color(0xFF211F26), Color(0xFFE6E1E5), Color(0xFFCAC4D0), accent, Color(0xFF49454F), r, material = true, glassA = t.glassA)
        else -> PTheme(t.id, t.name, false, t.bg, t.panel, t.text, t.sub, accent, t.line, r, material = true, glassA = t.glassA)
    }
}

// ---- the icon: the look's own picture where Pulse has one, else the Fluent vector (the same order as Pulse's PIcon, without the Office / app lock / tool pictures) -----------

@Composable
fun PIcon(k0: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (k0 == "tablet") { PIcon("phone", tint, size, modifier.graphicsLayer { rotationZ = 90f; scaleY = 1.15f }); return }
    val k = if (k0 == "applock" || k0 == "office") "lock" else k0
    val st = if (T.id == "win11" && !T.material) (LocalIconStyleOverride.current ?: IconStyle.of(IconCat.MENU)) else "original"
    val fluent = T.id == "win11" && !T.material && st != "line"
    val colour = st == "colour"
    if (fluent && colour) { val fk = if (k == "recycle") "trash" else k; if (FluentColor.map.containsKey(fk)) { FluentColorGlyph(fk, tint, size, modifier); return } }
    if (k == "recycle" && !T.material) {
        if (fluent) ThemeIcons.bitmap(ctx, "win11", "trash")?.let { Image(it, null, modifier.size(size)); return }
        BinIcon(tint, size, modifier); return
    }
    if (fluent) ThemeIcons.bitmap(ctx, "win11", k)?.let { Image(it, null, modifier.size(size)); return }
    if (fluent && FluentColor.map.containsKey(k)) { FluentColorGlyph(k, tint, size, modifier); return }
    if (k == "delete" && !T.material) { BinIcon(tint, size, modifier); return }
    if (T.id == "oneui" || T.id == "coloros") ThemeIcons.bitmap(ctx, T.id, k)?.let { Image(it, null, modifier.size(size), colorFilter = ColorFilter.tint(tint)); return }
    if (T.material) {
        // the Material Symbols drawables (mat_<key>), as in Pulse; a key without one draws its Fluent glyph below
        val id = remember(k) { ctx.resources.getIdentifier("mat_$k", "drawable", ctx.packageName) }
        if (id != 0) { Image(painterResource(id), null, modifier.size(size), colorFilter = ColorFilter.tint(tint)); return }
    }
    val pack = when (T.id) {
        "win95" -> ThemeIcons.bitmap(ctx, "win95", k)
        "xp" -> ThemeIcons.bitmap(ctx, "xp", k) ?: XP_ICON_ALIAS[k]?.let { ThemeIcons.bitmap(ctx, "xp", it) }
        "win7" -> ThemeIcons.bitmap(ctx, "win7", k) ?: ICON7_ALIAS[k]?.let { ThemeIcons.bitmap(ctx, "win7", it) }
        else -> null
    }
    if (pack != null) { Image(pack, null, modifier.size(size)); return }
    val paths = remember(k) { (FluentPaths.map[k] ?: FluentPaths.map["info"]!!).map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}


// ---- the look of the app: its own store (Material by default), resolved as Pulse's LookHost does -----------------------------------------------------------------------------

object LeanLook {
    var id by mutableStateOf("material")
    private var loaded = false
    fun load(ctx: android.content.Context) { if (loaded) return; loaded = true; id = ctx.getSharedPreferences("lean_look", 0).getString("look", "material") ?: "material" }
    fun set(ctx: android.content.Context, v: String) { id = v; ctx.getSharedPreferences("lean_look", 0).edit().putString("look", v).apply() }
}

@Composable
fun LeanLookHost(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    LeanLook.load(ctx)
    val base = Themes.firstOrNull { it.id == LeanLook.id } ?: Themes.first { it.id == "material" }
    val th = if (base.id == "amoled") amoledWindow() else effective(base)
    Look.theme = base
    Look.resolved = if (th !== base) th else null
    val acc = Look.accent
    val cs = if (th.dark) darkColorScheme(primary = acc, onPrimary = onAccent(acc), background = th.bg, surface = th.panel, onBackground = th.text, onSurface = th.text, surfaceVariant = th.panel, onSurfaceVariant = th.sub, outline = th.line)
    else lightColorScheme(primary = acc, onPrimary = onAccent(acc), background = th.bg, surface = th.panel, onBackground = th.text, onSurface = th.text, surfaceVariant = th.panel, onSurfaceVariant = th.sub, outline = th.line)
    val view = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val w = (view.context as android.app.Activity).window
        w.statusBarColor = th.bg.toArgb(); w.navigationBarColor = th.bg.toArgb()
        androidx.core.view.WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !th.dark; isAppearanceLightNavigationBars = !th.dark }
    }
    CompositionLocalProvider(LocalWindowLook provides th, LocalContentColor provides th.text, LocalExtraLook provides ExtraLookControl(LeanLook.id) { LeanLook.set(ctx, it) }) {
        MaterialTheme(colorScheme = cs) {
            Box(Modifier.fillMaxSize().background(th.wall ?: SolidColor(th.bg))) { content() }
        }
    }
}

/** The look store of the standalone tools as Pulse names it: in a lean app every look of Pulse is on the list. */
class ExtraLookControl(val id: String, val set: (String) -> Unit)
val LocalExtraLook = staticCompositionLocalOf<ExtraLookControl?> { null }
object ExtraToolLook {
    val ids: List<String> get() = Themes.map { it.id }
    fun name(id: String) = Themes.firstOrNull { it.id == id }?.name ?: id
}

/** Look chooser dialog: a radio list of the looks. */
@Composable
internal fun SetLookDialog(title: String, ids: List<String>, cur: String, name: (String) -> String, onPick: (String) -> Unit, onDismiss: () -> Unit) =
    WinAlert(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            ids.forEach { id -> Row(Modifier.fillMaxWidth().clickable { onPick(id); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PRadio(cur == id) { onPick(id); onDismiss() }; Spacer(Modifier.width(8.dp)); Text(name(id), color = LocalContentColor.current, fontSize = 15.sp)
            } }
        } },
        confirmButton = { PButton(tr("Close", "Kapat")) { onDismiss() } })

/** The app's look as one row of its settings: it opens the list of looks (every look of Pulse). */
@Composable
fun ExtraLookRow(ink: Color = T.text) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    if (open) SetLookDialog(tr("Look", "Görünüm"), ExtraToolLook.ids, LeanLook.id, { ExtraToolLook.name(it) }, { LeanLook.set(ctx, it) }) { open = false }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon("palette", ink, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(tr("App look", "Uygulama görünümü"), color = ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text((Themes.firstOrNull { it.id == LeanLook.id } ?: Themes[0]).name, color = ink.copy(alpha = .6f), fontSize = 14.sp)
    }
}

/** A small palette button for the headers: it opens the same list of looks. */
@Composable
fun ExtraLookIcon(tint: Color = T.text) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    if (open) SetLookDialog(tr("Look", "Görünüm"), ExtraToolLook.ids, LeanLook.id, { ExtraToolLook.name(it) }, { LeanLook.set(ctx, it) }) { open = false }
    Box(Modifier.size(40.dp).clickable { open = true }, contentAlignment = Alignment.Center) { PIcon("palette", tint, 22.dp) }
}
