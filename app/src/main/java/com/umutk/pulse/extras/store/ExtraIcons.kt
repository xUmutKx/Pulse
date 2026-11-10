package com.umutk.pulse.extras.store

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.Look
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.palette.iconpack.IconPackCache
import com.umutk.pulse.extras.palette.iconpack.InstalledIconPacks
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Icons chosen by hand for the extra apps (and, from Palette, for any app):
 *  - "pack|<package>|<drawable>": a picture from an icon pack installed on the phone (read from that pack at run time, nothing is copied into Pulse);
 *  - "make|<style>|<glyph>|<background>|<ink>|<shape>": drawn by Pulse's icon maker from its own Fluent glyphs (MIT): "line" is a line icon on a plain
 *    background (black by default, the Blaux Black look), "glass" a frosted, lit disc in the chosen colour with a white glyph (the Material glass look).
 * The choice is shown in the Extra apps list and can be put on the home screen as a shortcut with that picture.
 */
object ExtraIcons {
    private fun sp(c: Context) = c.getSharedPreferences("extra_icons", Context.MODE_PRIVATE)
    /** The chosen specs by row key, as state: the list redraws when one changes. */
    val specs = mutableStateMapOf<String, String>()
    private var loaded = false
    fun load(c: Context) { if (loaded) return; loaded = true; sp(c).all.forEach { (k, v) -> (v as? String)?.let { specs[k] = it } } }
    fun set(c: Context, key: String, spec: String?) {
        if (spec.isNullOrEmpty()) { specs.remove(key); sp(c).edit().remove(key).apply() } else { specs[key] = spec; sp(c).edit().putString(key, spec).apply() }
    }

    private val cache = android.util.LruCache<String, Bitmap>(48)

    /** The picture of a spec at [px] pixels, or null when it cannot be drawn (the pack was removed, an unknown glyph). Call it off the main thread. */
    fun render(c: Context, spec: String, px: Int = 192): Bitmap? {
        val key = "$spec@$px"
        cache.get(key)?.let { return it }
        val p = spec.split('|')
        val b = runCatching {
            when (p.getOrNull(0)) {
                "pack" -> packPicture(c, p[1], p[2], p.getOrElse(3) { "" }, px)
                "make" -> make(p[1], p[2], p[3].toLong(16).toInt(), p[4].toLong(16).toInt(), p.getOrElse(5) { "squircle" }, px)
                else -> null
            }
        }.getOrNull()
        if (b != null) cache.put(key, b)
        return b
    }

    /** A pack picture in a colour: "bw" makes it grey; a colour (8 hex digits, ARGB) makes its whole shape that colour; "" keeps the picture as it is. */
    fun tinted(b: Bitmap, t: String): Bitmap {
        if (t.isEmpty()) return b
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        if (t == "bw") pt.colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        else pt.colorFilter = android.graphics.PorterDuffColorFilter(t.toLong(16).toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(b, 0f, 0f, pt)
        return out
    }

    /** A pack picture in its look: "glass:<colour>" is the Material glass tile (transparent around it), the other looks as [tinted]. */
    fun packPicture(c: Context, pkg: String, name: String, t: String, px: Int): Bitmap? {
        val d = IconPackCache.get(c, pkg)?.drawable(name) ?: return null
        if (t.startsWith("glass")) return glassPicture(d, t.substringAfter(':', ""), px)
        return tinted(d.toBitmap(px, px, Bitmap.Config.ARGB_8888), t)
    }

    /** The Material glass look of a pack picture: the maker's glass disc in the colour (the accent when [hex] is empty), the picture in its own colours on it. */
    fun glassPicture(d: android.graphics.drawable.Drawable, hex: String, px: Int): Bitmap {
        val colour = hex.takeIf { it.length == 8 }?.toLong(16)?.toInt() ?: Look.accent.toArgb()
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val f = px.toFloat()
        glassDisc(cv, RectF(0f, 0f, f, f), f * .24f, colour, f)
        val inset = (px * .2f).toInt()
        d.setBounds(inset, inset, px - inset, px - inset)
        d.draw(cv)
        return b
    }

    fun makeSpec(style: String, glyph: String, bg: Int, ink: Int, shape: String) =
        "make|$style|$glyph|${Integer.toHexString(bg)}|${Integer.toHexString(ink)}|$shape"

    /** The icon maker: a Fluent glyph (24-unit paths) on a background shape, as a line icon or a glass disc. */
    fun make(style: String, glyph: String, bg: Int, ink: Int, shape: String, px: Int): Bitmap? {
        val paths = com.umutk.pulse.FluentPaths.map[glyph] ?: return null
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = RectF(0f, 0f, px.toFloat(), px.toFloat())
        val rad = when (shape) { "circle" -> px / 2f; "square" -> px * .08f; else -> px * .24f }
        if (style == "glass") glassDisc(cv, r, rad, bg, px.toFloat()) else {
            pt.color = bg; cv.drawRoundRect(r, rad, rad, pt)
        }
        // the glyph: 56% of the icon, centred; the glass one gets a faint shadow under it
        val s = px * .56f / 24f
        cv.save(); cv.translate((px - 24f * s) / 2f, (px - 24f * s) / 2f); cv.scale(s, s)
        val gl = paths.mapNotNull { runCatching { androidx.core.graphics.PathParser.createPathFromPathData(it) }.getOrNull() }
        if (style == "glass") {
            pt.color = 0x33000000; cv.save(); cv.translate(.35f, .5f); gl.forEach { cv.drawPath(it, pt) }; cv.restore()
        }
        pt.color = ink; gl.forEach { cv.drawPath(it, pt) }
        cv.restore()
        return b
    }

    /** The Material glass disc: the colour, lighter at the top left and deeper at the bottom right, a soft white sheen and a thin bright rim. Outside it stays see-through. */
    private fun glassDisc(cv: Canvas, r: RectF, rad: Float, bg: Int, px: Float) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        val light = blend(bg, 0xFFFFFFFF.toInt(), .35f); val deep = blend(bg, 0xFF000000.toInt(), .35f)
        pt.shader = LinearGradient(0f, 0f, px, px, light, deep, Shader.TileMode.CLAMP)
        cv.drawRoundRect(r, rad, rad, pt)
        pt.shader = RadialGradient(px * .3f, px * .12f, px * .75f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        cv.drawRoundRect(r, rad, rad, pt)
        pt.shader = null; pt.style = Paint.Style.STROKE; pt.strokeWidth = px * .018f; pt.color = 0x55FFFFFF
        val ri = RectF(r).apply { inset(pt.strokeWidth / 2, pt.strokeWidth / 2) }
        cv.drawRoundRect(ri, rad, rad, pt)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Puts a shortcut with [bmp] on the home screen that starts [intent]; false when the launcher does not take pinned shortcuts. */
    fun pin(c: Context, id: String, label: String, bmp: Bitmap, intent: Intent): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(c)) return false
        val info = ShortcutInfoCompat.Builder(c, "icon_$id").setShortLabel(label).setIcon(IconCompat.createWithBitmap(bmp))
            .setIntent(intent.setAction(intent.action ?: Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).build()
        return runCatching { ShortcutManagerCompat.requestPinShortcut(c, info, null) }.getOrDefault(false)
    }
}

/** The chosen picture of row [key] at [size], or [fallback] (the look's own icon) when none is chosen. */
@Composable
internal fun ExtraIcon(key: String, size: Dp, fallback: @Composable () -> Unit) {
    val ctx = LocalContext.current
    remember { ExtraIcons.load(ctx) }
    val spec = ExtraIcons.specs[key]
    if (spec == null) { fallback(); return }
    val bmp by produceState<Bitmap?>(null, spec) { value = withContext(Dispatchers.Default) { ExtraIcons.render(ctx, spec) } }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, Modifier.size(size)) else fallback()
}

private val BG_SWATCHES = listOf(0xFF000000, 0xFF121212, 0xFFFFFFFF, 0xFF0B1A3A, 0xFF1B5E20, 0xFF4A148C, 0xFFB71C1C, 0xFF00B0FF).map { it.toInt() }
/** The colours of the Material Glass presets: a frosted, lit disc in the colour, with a white glyph. */
private val GLASS_COLORS = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B, 0xFF1E88E5, 0xFF8E24AA, 0xFFEC407A).map { it.toInt() }
private val INK_SWATCHES = listOf(0xFFFFFFFF, 0xFF00E5FF, 0xFF76FF03, 0xFFFFD600, 0xFFFF6D00, 0xFFFF4081, 0xFFB388FF, 0xFF000000).map { it.toInt() }

/**
 * Choosing an icon: the look's own (default), a picture from an installed icon pack found by words (Turkish words are searched in English too),
 * or one made by the icon maker. [onSave] gets the spec (null = default); [onPin] puts it on the home screen.
 */
@Composable
internal fun IconChooser(name: String, glyph: String, current: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit, onPin: ((Bitmap) -> Unit)?) {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(if (current?.startsWith("pack") == true) "pack" else "make") }
    var spec by remember { mutableStateOf(current) }
    // icon pack
    val packs = remember { InstalledIconPacks.list(ctx) }
    var pack by remember { mutableStateOf(current?.split('|')?.takeIf { it[0] == "pack" }?.get(1) ?: packs.firstOrNull()?.pkg) }
    var query by remember { mutableStateOf(name) }
    // the colour of the pack pictures: the picture as it is, grey, or one colour
    var tint by remember { mutableStateOf(current?.split('|')?.takeIf { it[0] == "pack" }?.getOrNull(3) ?: "") }
    fun tinted(base: String?, t: String): String? = base?.takeIf { it.startsWith("pack|") }?.split('|')?.take(3)?.joinToString("|")?.let { if (t.isEmpty()) it else "$it|$t" }
    fun setTint(t: String) { tint = t; spec = tinted(spec, t) ?: spec }
    val glassArgb = tint.substringAfter("glass:", "").takeIf { it.length == 8 }?.toLong(16)?.toInt() ?: Look.accent.toArgb()
    // maker
    val p0 = current?.split('|')?.takeIf { it[0] == "make" }
    var style by remember { mutableStateOf(p0?.get(1) ?: "line") }
    var glyphKey by remember { mutableStateOf(p0?.get(2) ?: glyph) }
    var bg by remember { mutableStateOf(p0?.get(3)?.toLong(16)?.toInt() ?: 0xFF000000.toInt()) }
    var ink by remember { mutableStateOf(p0?.get(4)?.toLong(16)?.toInt() ?: Look.accent.toArgb()) }
    var shape by remember { mutableStateOf(p0?.getOrNull(5) ?: "squircle") }
    var glyphQuery by remember { mutableStateOf("") }
    fun makeNow() { spec = ExtraIcons.makeSpec(style, glyphKey, bg, ink, shape) }
    val preview by produceState<Bitmap?>(null, spec) { value = spec?.let { s -> withContext(Dispatchers.Default) { ExtraIcons.render(ctx, s) } } }
    WinAlert(onDismissRequest = onDismiss,
        title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(if (spec?.contains("glass") == true) Color.Transparent else Color.Black), contentAlignment = Alignment.Center) {
                        val pv = preview
                        if (spec != null && pv != null) Image(pv.asImageBitmap(), null, Modifier.size(64.dp)) else PIcon(glyph, Color.White, 40.dp)
                    }
                }
                // the review: the same icon in each look, on black (a tap picks that look)
                val variants = remember(spec, glyphKey, tab) {
                    val s = spec
                    if (s != null && s.startsWith("pack|")) {
                        val base = s.split('|').take(3).joinToString("|")
                        listOf(tr("Colour", "Renkli") to base, tr("B&W", "Siyah beyaz") to "$base|bw",
                            tr("Accent", "Vurgu") to "$base|${"%08x".format(Look.accent.toArgb())}", tr("White", "Beyaz") to "$base|ffffffff",
                            tr("Material glass", "Material cam") to "$base|glass:${"%08x".format(Look.accent.toArgb())}")
                    } else listOf("AMOLED" to ExtraIcons.makeSpec("line", glyphKey, 0xFF000000.toInt(), Look.accent.toArgb(), "squircle"),
                        "Material Glass" to ExtraIcons.makeSpec("glass", glyphKey, Look.accent.toArgb(), 0xFFFFFFFF.toInt(), "squircle"),
                        tr("Light", "Açık") to ExtraIcons.makeSpec("line", glyphKey, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), "squircle"),
                        tr("Dark", "Koyu") to ExtraIcons.makeSpec("line", glyphKey, 0xFF121212.toInt(), 0xFFFFFFFF.toInt(), "squircle"))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    variants.forEach { (label, vs) ->
                        Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable { spec = vs }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ReviewIcon(ctx, vs, 52.dp)
                            Text(label, color = if (spec == vs) Look.accent else T.sub, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                // the three choices on their own row (scrolls when narrow), so no label is squeezed into a column
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PChip(spec == null, tr("Default", "Varsayılan")) { spec = null }
                    PChip(tab == "pack", tr("Icon pack", "İkon paketi")) { tab = "pack" }
                    if (tab == "pack") packs.forEach { pk -> PChip(pack == pk.pkg, pk.label) { pack = pk.pkg } }
                    PChip(tab == "make", tr("Maker", "Üretici")) { tab = "make"; makeNow() }
                }
                if (tab == "pack") {
                    if (packs.isEmpty()) Text(tr("No icon pack is installed", "Yüklü ikon paketi yok"), color = T.sub, fontSize = 13.sp)
                    else {
                        Text(tr("Look", "Görünüm"), color = T.sub, fontSize = 12.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PChip(tint.isEmpty(), tr("Original", "Orijinal")) { setTint("") }
                            PChip(tint == "bw", tr("Black & white", "Siyah beyaz")) { setTint("bw") }
                            PChip(tint.startsWith("glass"), tr("Material glass", "Material cam")) { setTint("glass:" + "%08x".format(glassArgb)) }
                        }
                        Text(tr("Colour", "Renk"), color = T.sub, fontSize = 12.sp)
                        Swatches((GLASS_COLORS + Look.accent.toArgb() + 0xFFFFFFFF.toInt()), if (tint.startsWith("glass")) glassArgb else if (tint.length == 8) tint.toLong(16).toInt() else 0) { c ->
                            val hex = "%08x".format(c)
                            setTint(if (tint.startsWith("glass")) "glass:$hex" else hex)
                        }
                        PField(query, { query = it }, tr("Search: ice cream, dondurma…", "Ara: dondurma, ice cream…"), Modifier.fillMaxWidth())
                        val pk = pack
                        val found by produceState<List<String>>(emptyList(), pk, query) {
                            value = if (pk == null) emptyList() else withContext(Dispatchers.Default) { IconPackCache.get(ctx, pk)?.search(query, 40).orEmpty() }
                        }
                        if (found.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = T.sub, fontSize = 13.sp)
                        found.chunked(5).forEach { line ->
                            Row(Modifier.fillMaxWidth()) {
                                line.forEach { n ->
                                    val s = "pack|$pk|$n" + (if (tint.isEmpty()) "" else "|$tint")
                                    val bm by produceState<Bitmap?>(null, s) { value = withContext(Dispatchers.Default) { ExtraIcons.render(ctx, s, 128) } }
                                    Box(Modifier.weight(1f).padding(3.dp).clip(RoundedCornerShape(12.dp)).background(if (tint.startsWith("glass")) Color.Transparent else Color.Black)
                                        .border(if (spec == s) 2.dp else 0.dp, Look.accent, RoundedCornerShape(12.dp)).clickable { spec = s }.padding(4.dp), contentAlignment = Alignment.Center) {
                                        bm?.let { Image(it.asImageBitmap(), n, Modifier.size(44.dp)) } ?: Spacer(Modifier.size(44.dp))
                                    }
                                }
                                repeat(5 - line.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    // presets: one tap sets the style and the colours (AMOLED black, Material Glass, light, dark), then each part can still be changed below
                    Text(tr("Presets", "Hazır stiller"), color = T.sub, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PChip(style == "line" && bg == 0xFF000000.toInt(), "AMOLED") { style = "line"; bg = 0xFF000000.toInt(); ink = Look.accent.toArgb(); makeNow() }
                        PChip(style == "glass" && bg == Look.accent.toArgb(), tr("Material Glass", "Material Cam")) { style = "glass"; bg = Look.accent.toArgb(); ink = 0xFFFFFFFF.toInt(); makeNow() }
                        PChip(style == "line" && bg == 0xFFFFFFFF.toInt(), tr("Light", "Açık")) { style = "line"; bg = 0xFFFFFFFF.toInt(); ink = 0xFF000000.toInt(); makeNow() }
                        PChip(style == "line" && bg == 0xFF121212.toInt(), tr("Dark", "Koyu")) { style = "line"; bg = 0xFF121212.toInt(); ink = 0xFFFFFFFF.toInt(); makeNow() }
                    }
                    Text(tr("Glass colour", "Cam rengi"), color = T.sub, fontSize = 12.sp)
                    Swatches(GLASS_COLORS + Look.accent.toArgb(), if (style == "glass") bg else 0) { style = "glass"; bg = it; ink = 0xFFFFFFFF.toInt(); makeNow() }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PChip(style == "line", tr("Line", "Çizgi")) { style = "line"; makeNow() }
                        PChip(style == "glass", tr("Glass", "Cam")) { style = "glass"; if (bg == 0xFF000000.toInt()) bg = Look.accent.toArgb(); if (ink == bg) ink = 0xFFFFFFFF.toInt(); makeNow() }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("squircle" to tr("Rounded", "Yuvarlak köşe"), "circle" to tr("Circle", "Daire"), "square" to tr("Square", "Kare")).forEach { (k, l) -> PChip(shape == k, l) { shape = k; makeNow() } }
                    }
                    Text(tr("Background", "Arka plan"), color = T.sub, fontSize = 12.sp)
                    Swatches(BG_SWATCHES + Look.accent.toArgb(), bg) { bg = it; makeNow() }
                    Text(tr("Glyph colour", "Simge rengi"), color = T.sub, fontSize = 12.sp)
                    Swatches(INK_SWATCHES + Look.accent.toArgb(), ink) { ink = it; makeNow() }
                    PField(glyphQuery, { glyphQuery = it }, tr("Glyph", "Simge"), Modifier.fillMaxWidth())
                    // without a search, the glyphs that fit the name come first (Music player: music, play), the app's own glyph before them
                    val keys = remember(glyphQuery, name, glyph) {
                        val q = glyphQuery.lowercase().trim()
                        val words = if (q.isEmpty()) {
                            name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }.flatMap { w -> listOf(w) + (com.umutk.pulse.extras.palette.iconpack.IconWords.en[w] ?: emptyList()) }
                        } else listOf(q) + (com.umutk.pulse.extras.palette.iconpack.IconWords.en[q] ?: emptyList())
                        val all = com.umutk.pulse.FluentPaths.map.keys
                        val hits = all.filter { k -> words.isNotEmpty() && words.any { k.lowercase().contains(it) } }.sorted()
                        val own = listOf(glyph).filter { it in all && q.isEmpty() }
                        (own + hits).distinct().ifEmpty { all.sorted() }.take(40)
                    }
                    keys.chunked(6).forEach { line ->
                        Row(Modifier.fillMaxWidth()) {
                            line.forEach { k ->
                                Box(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(10.dp)).background(if (glyphKey == k) Look.accent.copy(alpha = .25f) else Color.Transparent)
                                    .clickable { glyphKey = k; makeNow() }.padding(6.dp), contentAlignment = Alignment.Center) { PIcon(k, T.text, 24.dp) }
                            }
                            repeat(6 - line.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (onPin != null) TextButton({ val s = spec; if (s != null) Thread { ExtraIcons.render(ctx, s, 256)?.let { b -> android.os.Handler(android.os.Looper.getMainLooper()).post { onPin(b) } } }.start() }, enabled = spec != null) {
                    PIcon("home", T.text, 18.dp); Spacer(Modifier.width(6.dp)); Text(tr("Add to home screen", "Ana ekrana ekle"))
                }
            }
        },
        confirmButton = { TextButton({ onSave(spec) }) { Text(tr("Save", "Kaydet")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "İptal")) } })
}

/** One look of the review: the icon on black at [size]. */
@Composable
private fun ReviewIcon(ctx: Context, spec: String, size: Dp) {
    val bm by produceState<Bitmap?>(null, spec) { value = withContext(Dispatchers.Default) { ExtraIcons.render(ctx, spec, 128) } }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(if (spec.contains("glass")) Color.Transparent else Color.Black), contentAlignment = Alignment.Center) {
        bm?.let { Image(it.asImageBitmap(), null, Modifier.size(size - 8.dp)) }
    }
}

@Composable
private fun Swatches(colors: List<Int>, picked: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.distinct().forEach { c ->
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (c == picked) 3.dp else 1.dp, if (c == picked) Look.accent else T.sub.copy(alpha = .5f), CircleShape).clickable { onPick(c) })
        }
    }
}

/** One row of the icon page: an app, the look's icon key for it, and what starts it (for the home-screen shortcut). */
/** Material pastel tiles behind the extra apps' icons (a row always gets the same one). */
private val PASTELS = listOf(0xFFEADDFF, 0xFFFFD8E4, 0xFFD0E4FF, 0xFFC4EED0, 0xFFFFE4C4, 0xFFFFF3B0, 0xFFD4F1F4, 0xFFF7D6E6).map { Color(it) }
/** The default pictures of Files, Photos, Calendar, Contacts and Browser (drawable-nodpi, taken from an icon pack). */
private val PACK_DEFAULTS = mapOf(
    "app:FilesLauncher" to com.umutk.pulse.R.drawable.pack_files,
    "app:PhotosLauncher" to com.umutk.pulse.R.drawable.pack_photos,
    "app:CalendarLauncher" to com.umutk.pulse.R.drawable.pack_calendar,
    "app:ContactsLauncher" to com.umutk.pulse.R.drawable.pack_contacts,
    "app:BrowserLauncher" to com.umutk.pulse.R.drawable.pack_browser,
)

internal class IconRow(val key: String, val name: String, val glyph: String, val intent: Intent)

/** Extra apps > ⚙: every extra app with its icon; a tap opens the chooser. */
@Composable
internal fun ExtraIconsScreen(rows: List<IconRow>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var edit by remember { mutableStateOf<IconRow?>(null) }
    Column(Modifier.fillMaxSize()) {
        StoreHeader(tr("App icons", "Uygulama ikonları"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { edit = r }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExtraIcon(r.key, 36.dp) {
                        if (r.key == "pulse") {
                            // Pulse's own icon, as the launcher shows it
                            val own = remember { runCatching { ctx.packageManager.getApplicationIcon(ctx.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                            if (own != null) Image(own, null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
                        } else Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(PASTELS[r.key.hashCode().mod(PASTELS.size)]), contentAlignment = Alignment.Center) {
                            // every row has its pastel Material tile; the picture or the glyph sits on it
                            PACK_DEFAULTS[r.key]?.let { res -> Image(painterResource(res), r.name, Modifier.size(28.dp)) } ?: PIcon(r.glyph, Color(0xFF1C1B1F), 22.dp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(r.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    PIcon("chevron", T.sub, 18.dp)
                }
            }
            Text(tr("Icon pack pictures are read from the pack installed on the phone; Pulse does not copy them.", "İkon paketi resimleri telefonda yüklü paketten okunur; Pulse onları kopyalamaz."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
    edit?.let { r ->
        IconChooser(r.name, r.glyph, ExtraIcons.specs[r.key], onDismiss = { edit = null },
            onSave = { ExtraIcons.set(ctx, r.key, it); edit = null },
            onPin = { b -> if (!ExtraIcons.pin(ctx, r.key, r.name, b, Intent(r.intent))) android.widget.Toast.makeText(ctx, tr("The launcher does not take shortcuts", "Başlatıcı kısayol kabul etmiyor"), android.widget.Toast.LENGTH_SHORT).show() })
    }
}
