package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track

/** A named, ready-made look. [build] gets the system accent (Material You) for the skins that follow it. */
class PowerampSkin(val id: String, val name: String, val build: (accent: Color, onAccent: Color) -> PowerampStyle)

/** Default accent: light grey on dark skins. The system accent is only used by the "Accent" skin. */
internal val NeutralAccent = Color(0xFFE6E6E6)
private val dimDark = Color(0xFF9A9A9A)
private val dimLight = Color(0xFF4A4A4A)

private fun dark(
    id: String, name: String, bg: Color, accent: Color?, to: Color? = null,
    seek: PowerampSeek = PowerampSeek.WAVE, tabs: PowerampTabs = PowerampTabs.PILL,
    buttons: PowerampButtons = PowerampButtons.PLAIN, corner: Int = 16, panel: Color = Color(0xFF1A1A1A),
    chip: Color = Color(0xFF2B2B2B), dim: Color = dimDark, text: Color = Color.White, onAccent: Color = Color.Black,
) = PowerampSkin(id, name) { a, oa ->
    PowerampStyle(
        text = text, textDim = dim, panel = panel, accent = accent ?: a, onAccent = if (accent == null) oa else onAccent,
        backdrop = PowerampBackdrop.SOLID, solid = bg, gradientTo = to, chip = chip,
        artCorner = corner.dp, seek = seek, tabs = tabs, buttons = buttons,
    )
}

private fun light(
    id: String, name: String, bg: Color, accent: Color?, to: Color? = null,
    seek: PowerampSeek = PowerampSeek.LINE, tabs: PowerampTabs = PowerampTabs.PILL,
    buttons: PowerampButtons = PowerampButtons.ROUND, corner: Int = 16, onAccent: Color = Color.White,
) = PowerampSkin(id, name) { a, oa ->
    PowerampStyle(
        text = Color.Black, textDim = dimLight, panel = Color.Black.copy(alpha = .08f), accent = accent ?: Color(0xFF424242),
        onAccent = if (accent == null) Color.White else onAccent, backdrop = PowerampBackdrop.SOLID, solid = bg, gradientTo = to,
        chip = Color(0xFFDADADA), artCorner = corner.dp, seek = seek, tabs = tabs, buttons = buttons,
    )
}

private fun blur(
    id: String, name: String, accent: Color?, buttons: PowerampButtons = PowerampButtons.PLAIN,
    seek: PowerampSeek = PowerampSeek.WAVE, tabs: PowerampTabs = PowerampTabs.PILL, corner: Int = 16,
    panel: Color = Color.White.copy(alpha = .10f), chip: Color = Color.White.copy(alpha = .16f),
) = PowerampSkin(id, name) { a, oa ->
    PowerampStyle(
        panel = panel, accent = accent ?: a, onAccent = if (accent == null) oa else Color.Black,
        backdrop = PowerampBackdrop.BLURRED_ART, chip = chip, artCorner = corner.dp, seek = seek, tabs = tabs, buttons = buttons,
    )
}

private val baseSkins: List<PowerampSkin> = listOf(
    // the real thing: black, bare glyphs, waveform seek bar, floating pill dock
    PowerampSkin("black", "Black") { a, oa -> PowerampStyle(accent = a, onAccent = oa, panel = Color(0xFF1A1A1A)) },
    dark("you", "Accent", Color.Black, null),
    dark("mint", "Mint", Color.Black, Color(0xFF1DE9A8)),
    blur("art", "Album art", null),
    blur("glass", "Glass", Color.White, PowerampButtons.ROUND, panel = Color.White.copy(alpha = .18f), chip = Color.White.copy(alpha = .22f)),
    blur("art-round", "Album art soft", null, PowerampButtons.SOFT, PowerampSeek.LINE, corner = 32),
    dark("material", "Material", Color(0xFF121212), null, buttons = PowerampButtons.ROUND, seek = PowerampSeek.LINE, panel = Color(0xFF232323)),
    light("material-light", "Material light", Color(0xFFF6F6F8), null),
    dark("neon-pink", "Neon pink", Color.Black, Color(0xFFFF2E93), buttons = PowerampButtons.SOFT),
    dark("neon-cyan", "Neon cyan", Color.Black, Color(0xFF00E5FF), buttons = PowerampButtons.SOFT, seek = PowerampSeek.BARS),
    dark("sunset", "Sunset", Color(0xFF2B0A3D), Color(0xFFFFB347), to = Color(0xFFB8452E), buttons = PowerampButtons.ROUND, onAccent = Color(0xFF2B0A3D)),
    dark("ocean", "Ocean", Color(0xFF001F3F), Color(0xFF4FD1C5), to = Color(0xFF0A5C73), buttons = PowerampButtons.ROUND, seek = PowerampSeek.LINE),
    dark("forest", "Forest", Color(0xFF0B2B1A), Color(0xFF8BD450), to = Color(0xFF1C5A33), buttons = PowerampButtons.SOFT),
    dark("amber", "Retro amber", Color.Black, Color(0xFFFFB000), seek = PowerampSeek.BARS, tabs = PowerampTabs.FLAT, buttons = PowerampButtons.SQUARE, corner = 0, panel = Color(0xFF1C1500), chip = Color(0xFF2A2000)),
    dark("terminal", "Terminal", Color.Black, Color(0xFF33FF33), seek = PowerampSeek.BARS, tabs = PowerampTabs.FLAT, buttons = PowerampButtons.SQUARE, corner = 0, panel = Color(0xFF0A1A0A), chip = Color(0xFF102510), dim = Color(0xFF3FA03F)),
    dark("matrix", "Matrix", Color.Black, Color(0xFF00FF41), to = Color(0xFF062406), seek = PowerampSeek.BARS, tabs = PowerampTabs.UNDERLINE, corner = 4, chip = Color(0xFF0E260E)),
    dark("vinyl", "Vinyl", Color(0xFF1B1109), Color(0xFFD9A441), buttons = PowerampButtons.ROUND, corner = 400, panel = Color(0xFF2A1D10), chip = Color(0xFF362716), seek = PowerampSeek.LINE),
    dark("minimal", "Minimal", Color(0xFF111111), Color.White, seek = PowerampSeek.LINE, tabs = PowerampTabs.UNDERLINE, corner = 4, panel = Color(0xFF111111), chip = Color(0xFF222222)),
    dark("mono", "Mono", Color.Black, Color.White, seek = PowerampSeek.BARS, buttons = PowerampButtons.SQUARE, corner = 8),
    light("paper", "Paper", Color(0xFFF4EFE6), Color(0xFFC0392B), seek = PowerampSeek.LINE, tabs = PowerampTabs.UNDERLINE, buttons = PowerampButtons.PLAIN, corner = 2),
    light("sky", "Sky", Color(0xFFE8F1FB), Color(0xFF1E88E5), tabs = PowerampTabs.FLAT, seek = PowerampSeek.WAVE),
    light("candy", "Candy", Color(0xFFFFE4F1), Color(0xFFFF4081), buttons = PowerampButtons.SOFT, seek = PowerampSeek.WAVE, corner = 28),
    light("mint-light", "Mint light", Color(0xFFE6F7F1), Color(0xFF009688), buttons = PowerampButtons.SQUARE, seek = PowerampSeek.BARS),
    dark("royal", "Royal", Color(0xFF0E0A1F), Color(0xFFB388FF), to = Color(0xFF291866), buttons = PowerampButtons.SOFT),
    dark("crimson", "Crimson", Color.Black, Color(0xFFFF3B30), buttons = PowerampButtons.ROUND, onAccent = Color.White),
    dark("lava", "Lava", Color(0xFF1A0000), Color(0xFFFF5722), to = Color(0xFF5A1500), seek = PowerampSeek.BARS, buttons = PowerampButtons.SQUARE),
    dark("gold", "Gold", Color.Black, Color(0xFFD4AF37), buttons = PowerampButtons.SOFT, seek = PowerampSeek.LINE, panel = Color(0xFF1C1810)),
    dark("midnight", "Midnight", Color(0xFF0D1B2A), Color(0xFF3FA9F5), seek = PowerampSeek.LINE, buttons = PowerampButtons.ROUND, tabs = PowerampTabs.FLAT, panel = Color(0xFF16273B), chip = Color(0xFF1F3550)),
    dark("solarized", "Solarized", Color(0xFF002B36), Color(0xFF2AA198), buttons = PowerampButtons.SOFT, seek = PowerampSeek.BARS, panel = Color(0xFF073642), chip = Color(0xFF0B4452)),
    light("win95", "Windows 95", Color(0xFFC0C0C0), Color(0xFF000080), seek = PowerampSeek.BARS, tabs = PowerampTabs.FLAT, buttons = PowerampButtons.SQUARE, corner = 0),
    dark("classic", "Classic orange", Color(0xFF1E1E1E), Color(0xFFFF9800), seek = PowerampSeek.BARS, tabs = PowerampTabs.FLAT, buttons = PowerampButtons.SOFT, corner = 8, panel = Color(0xFF2C2C2C), chip = Color(0xFF383838)),
)

// 24 more skins from the shared palettes, cycling seek bar, dock, buttons and cover corner so they differ in shape too
private val generatedSkins: List<PowerampSkin> = run {
    val seeks = PowerampSeek.values(); val docks = PowerampTabs.values(); val btns = PowerampButtons.values()
    val corners = intArrayOf(16, 28, 8, 0, 400)
    com.umutk.pulse.extras.music.musicPalettes.filterIndexed { i, _ -> i % 5 != 4 }.take(24).mapIndexed { n, p ->
        val to = if (n % 3 == 0) p.bgColor.copy(red = (p.bgColor.red * .6f), green = (p.bgColor.green * .6f), blue = (p.bgColor.blue * .6f)) else null
        if (p.dark) dark("g-" + p.id, p.name, p.bgColor, p.accentColor, to = to, seek = seeks[n % 3], tabs = docks[(n / 2) % 3], buttons = btns[n % 4], corner = corners[n % 5],
            onAccent = if (p.accentColor.luminance() > .18f) Color.Black else Color.White)
        else light("g-" + p.id, p.name, p.bgColor, p.accentColor, seek = seeks[n % 3], tabs = docks[(n / 2) % 3], buttons = btns[n % 4], corner = corners[n % 5],
            onAccent = if (p.accentColor.luminance() > .18f) Color.Black else Color.White)
    }
}

val powerampSkins: List<PowerampSkin> = baseSkins + generatedSkins

fun powerampSkin(id: String): PowerampSkin = powerampSkins.firstOrNull { it.id == id } ?: powerampSkins[0]

/** Saved skin choice. */
object PowerampPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("pulse_poweramp", Context.MODE_PRIVATE)
    fun skin(c: Context): String = sp(c).getString("skin", "black") ?: "black"
    fun setSkin(c: Context, id: String) = sp(c).edit().putString("skin", id).apply()
    /** Now-playing page only: true = blurred album cover behind it, false = black. Every other page is black. */
    fun playerArt(c: Context): Boolean = sp(c).getBoolean("player_art", true)
    fun setPlayerArt(c: Context, on: Boolean) = sp(c).edit().putBoolean("player_art", on).apply()
}

/** Poweramp look with the saved skin applied; the library page has the skin chooser. */
@Composable
fun PowerampPlayer(engine: MusicPlayerEngine, library: List<Track>, art: (Track) -> ImageBitmap?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var id by remember { mutableStateOf(PowerampPrefs.skin(ctx)) }
    val c = MaterialTheme.colorScheme
    PowerampApp(
        engine, library, powerampSkin(id).let { if (it.id == "you") it.build(c.primary, c.onPrimary) else it.build(NeutralAccent, Color.Black) }, art, modifier,
        skinId = id, onSkin = { id = it; PowerampPrefs.setSkin(ctx, it) },
    )
}

internal fun Modifier.skinBackdrop(s: PowerampStyle): Modifier {
    val g = s.gradientTo
    return when {
        s.backdrop == PowerampBackdrop.SOLID && g != null -> background(Brush.verticalGradient(listOf(s.solid, g)))
        s.backdrop == PowerampBackdrop.SOLID -> background(s.solid)
        else -> background(Color.Black)
    }
}

/** Horizontal strip of miniature players, one per skin. No captions: the name is only the accessibility label. */
@Composable
fun PowerampSkinStrip(selectedId: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(powerampSkins, key = { it.id }) { sk ->
            SkinMini(sk.build(if (sk.id == "you") MaterialTheme.colorScheme.primary else NeutralAccent, Color.Black), sk.id == selectedId, sk.name) { onSelect(sk.id) }
        }
    }
}

@Composable
private fun SkinMini(s: PowerampStyle, selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val blurred = s.backdrop == PowerampBackdrop.BLURRED_ART
    val bgMod = if (blurred) Modifier.background(Brush.verticalGradient(listOf(s.accent.copy(alpha = .55f), Color.Black))) else Modifier.skinBackdrop(s)
    Column(
        Modifier.width(78.dp).height(140.dp).clip(shape).then(bgMod)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) s.accent else Color.White.copy(alpha = .18f), shape)
            .semantics { contentDescription = label; role = Role.RadioButton }
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val corner = if (s.artCorner > 100.dp) 100.dp else (s.artCorner.value / 4f).dp
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(s.accent.copy(alpha = .8f), s.accent.copy(alpha = .25f)))),
        )
        Box(Modifier.padding(top = 5.dp).width(44.dp).height(5.dp).clip(CircleShape).background(s.chip))
        Box(Modifier.padding(top = 3.dp).width(32.dp).height(4.dp).clip(CircleShape).background(s.chip))
        Canvas(Modifier.padding(top = 6.dp).fillMaxWidth().height(14.dp)) {
            val n = if (s.seek == PowerampSeek.LINE) 0 else 14
            val slot = size.width / 14
            for (i in 0 until n) {
                val h = if (s.seek == PowerampSeek.BARS) .5f else (.35f + .65f * ((i * 7) % 5) / 4f)
                val col = if (i < 6) s.accent else s.textDim.copy(alpha = .4f)
                drawLine(col, Offset(slot * (i + .5f), size.height * (1 - h) / 2), Offset(slot * (i + .5f), size.height * (1 + h) / 2), slot * .5f, StrokeCap.Round)
            }
            if (n == 0) {
                drawLine(s.textDim.copy(alpha = .4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                drawLine(s.accent, Offset(0f, size.height / 2), Offset(size.width * .43f, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(s.accent, 3.dp.toPx(), Offset(size.width * .43f, size.height / 2))
            }
        }
        val btn = when (s.buttons) {
            PowerampButtons.PLAIN -> Modifier.size(16.dp).background(s.onBackdrop, RoundedCornerShape(3.dp))
            PowerampButtons.ROUND -> Modifier.size(18.dp).background(s.accent, CircleShape)
            PowerampButtons.SOFT -> Modifier.size(18.dp).background(s.accent, RoundedCornerShape(7.dp))
            PowerampButtons.SQUARE -> Modifier.size(18.dp).background(s.accent, RoundedCornerShape(2.dp))
        }
        Box(Modifier.padding(top = 4.dp).then(btn))
        Box(Modifier.weight(1f))
        val dock = when (s.tabs) {
            PowerampTabs.PILL -> Modifier.fillMaxWidth().height(12.dp).background(s.panel.copy(alpha = 1f).let { if (s.panel.alpha < 1f) s.chip else it }, CircleShape)
            else -> Modifier.fillMaxWidth().height(12.dp).background(s.chip)
        }
        Box(dock, contentAlignment = Alignment.BottomCenter) {
            if (s.tabs == PowerampTabs.UNDERLINE) Box(Modifier.width(16.dp).height(2.dp).background(s.accent))
        }
    }
}

/** Small drawing of a skin for the theme gallery: cover, title bars, seek bar, play button and dock, no text. */
@Composable
fun PowerampSkinTile(skin: PowerampSkin) {
    val c = MaterialTheme.colorScheme
    val s = skin.build(if (skin.id == "you") c.primary else NeutralAccent, if (skin.id == "you") c.onPrimary else Color.Black)
    val blurred = s.backdrop == PowerampBackdrop.BLURRED_ART
    val bgMod = if (blurred) Modifier.background(Brush.verticalGradient(listOf(s.accent.copy(alpha = .55f), Color.Black))) else Modifier.skinBackdrop(s)
    Row(Modifier.fillMaxSize().then(bgMod).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val corner = if (s.artCorner > 100.dp) 100.dp else (s.artCorner.value / 3f).dp
        Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(s.accent.copy(alpha = .85f), s.accent.copy(alpha = .25f)))))
        Column(Modifier.padding(start = 8.dp).weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth(.8f).height(5.dp).clip(CircleShape).background(s.chip))
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                if (s.seek == PowerampSeek.LINE) {
                    drawLine(s.textDim.copy(alpha = .4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(s.accent, Offset(0f, size.height / 2), Offset(size.width * .45f, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                } else {
                    val n = 12; val slot = size.width / n
                    for (i in 0 until n) {
                        val h = if (s.seek == PowerampSeek.BARS) .5f else .3f + .7f * ((i * 7) % 5) / 4f
                        drawLine(if (i < 5) s.accent else s.textDim.copy(alpha = .4f), Offset(slot * (i + .5f), size.height * (1 - h) / 2), Offset(slot * (i + .5f), size.height * (1 + h) / 2), slot * .5f, StrokeCap.Round)
                    }
                }
            }
            val btn = when (s.buttons) {
                PowerampButtons.PLAIN -> Modifier.size(12.dp).background(s.onBackdrop, RoundedCornerShape(2.dp))
                PowerampButtons.ROUND -> Modifier.size(14.dp).background(s.accent, CircleShape)
                PowerampButtons.SOFT -> Modifier.size(14.dp).background(s.accent, RoundedCornerShape(5.dp))
                PowerampButtons.SQUARE -> Modifier.size(14.dp).background(s.accent, RoundedCornerShape(1.dp))
            }
            Box(btn)
            Box(Modifier.fillMaxWidth().height(7.dp).background(s.chip, if (s.tabs == PowerampTabs.PILL) CircleShape else RoundedCornerShape(0.dp)))
        }
    }
}
