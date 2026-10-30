package com.umutk.pulse.extras.music.imgskin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.umutk.pulse.R
import kotlin.math.max
import kotlin.math.min

/** What a skin shows: the song, where it is, and the cover. [clock] runs in seconds (for the scrolling text); [levels] is set while the visualiser is on. */
class SkinState(
    val title: String, val artist: String, val playing: Boolean, val posMs: Long, val durMs: Long,
    val cover: Bitmap?, val clock: Float = 0f, val levels: List<Float>? = null,
)

/** A tappable place of a skin, in the skin's own pixels: "prev", "play", "next", "stop", "open", "vis" (the display), "seek" (a bar to touch or drag). */
class Hit(val id: String, val r: RectF)

/**
 * A window skin that is a picture (assets/wmskins/<id>.webp, the player with its music removed) with the live parts drawn over it: the text, the cover, the
 * seek bar and the play button. One drawing serves the player page and the widget, so they look the same.
 */
abstract class ImgSkin(val id: String, val name: String, val w: Int, val h: Int, val hits: List<Hit>) {
    abstract fun draw(c: Canvas, ctx: Context, st: SkinState)
    fun hit(x: Float, y: Float): Hit? = hits.lastOrNull { it.r.contains(x, y) }
    val seek: RectF? get() = hits.firstOrNull { it.id == "seek" }?.r
    fun frac(st: SkinState) = if (st.durMs > 0) (st.posMs.toFloat() / st.durMs).coerceIn(0f, 1f) else 0f
}

object ImgSkins {
    val all: List<ImgSkin> by lazy { listOf(BlobSkin, Win7Skin, PinkSkin, SilverSkin, LunaSkin, BubbleSkin, AeroSkin, NexusSkin, SymphoraSkin, CdSkin) }
    private val cache = HashMap<String, Bitmap>()

    /** The skin's picture. */
    fun base(ctx: Context, s: ImgSkin): Bitmap? = cache[s.id] ?: runCatching {
        ctx.assets.open("wmskins/${s.id}.webp").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()?.also { cache[s.id] = it }

    /** The skin and its picture painted into [c] (a canvas in skin pixels). */
    fun paint(c: Canvas, ctx: Context, s: ImgSkin, st: SkinState) {
        // the picture is stored larger than the skin's own pixels, so it stays sharp when the widget or the page is big
        base(ctx, s)?.let { c.drawBitmap(it, null, RectF(0f, 0f, s.w.toFloat(), s.h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)) }
        s.draw(c, ctx, st)
    }
}

// ---- drawing helpers -------------------------------------------------------------------------------------------------------------------------------

private fun circ(x: Float, y: Float, r: Float) = RectF(x - r, y - r, x + r, y + r)
private fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(l, t, r, b)

private fun pt(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT, tf: Typeface? = null) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color; textSize = size; textAlign = align
    typeface = tf ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** Text inside [clip] that scrolls past when it is wider than the room it has. */
private fun marquee(c: Canvas, text: String, p: Paint, clip: RectF, x: Float, y: Float, clock: Float) {
    c.save(); c.clipRect(clip)
    val tw = p.measureText(text)
    if (tw <= clip.right - x) c.drawText(text, x, y, p)
    else {
        val gap = 36f
        val off = (clock * 22f) % (tw + gap)
        c.drawText(text, x - off, y, p); c.drawText(text, x - off + tw + gap, y, p)
    }
    c.restore()
}

private fun bars(c: Canvas, area: RectF, levels: List<Float>, color: Int) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val n = levels.size.coerceAtLeast(1); val gw = area.width() / n
    levels.forEachIndexed { i, v ->
        val hh = area.height() * v.coerceIn(.04f, 1f)
        c.drawRoundRect(RectF(area.left + i * gw + gw * .15f, area.bottom - hh, area.left + (i + 1) * gw - gw * .15f, area.bottom), 2f, 2f, p)
    }
}

private fun glyph(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, playing: Boolean) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    if (playing) {
        c.drawRoundRect(RectF(cx - s * .72f, cy - s, cx - s * .12f, cy + s), s * .1f, s * .1f, p)
        c.drawRoundRect(RectF(cx + s * .12f, cy - s, cx + s * .72f, cy + s), s * .1f, s * .1f, p)
    } else c.drawPath(Path().apply { moveTo(cx - s * .7f, cy - s); lineTo(cx + s * .95f, cy); lineTo(cx - s * .7f, cy + s); close() }, p)
}

/** The cover, cut square from the middle, with round corners. */
private fun cover(c: Canvas, bmp: Bitmap, dst: RectF, radius: Float) {
    c.save()
    c.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
    val side = min(bmp.width, bmp.height)
    c.drawBitmap(bmp, Rect((bmp.width - side) / 2, (bmp.height - side) / 2, (bmp.width + side) / 2, (bmp.height + side) / 2), dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    c.restore()
}

private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, a: Int, b: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(x0, y0, x1, y1, a, b, Shader.TileMode.CLAMP) }

// ---- the five skins --------------------------------------------------------------------------------------------------------------------------------

/** A grey oval player with a green display and a round pad of buttons. */
object BlobSkin : ImgSkin("skin1", "Blob", 444, 216, listOf(
    Hit("vis", rect(96f, 52f, 262f, 122f)), Hit("seek", rect(102f, 124f, 236f, 138f)),
    Hit("open", circ(312f, 73f, 13f)), Hit("prev", circ(299f, 127f, 14f)), Hit("play", circ(334f, 112f, 16f)), Hit("next", circ(368f, 103f, 14f)), Hit("stop", circ(359f, 147f, 13f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        c.save()
        c.clipPath(Path().apply { addOval(RectF(70f, 50f, 264f, 136f), Path.Direction.CW); transform(Matrix().apply { setRotate(-11f, 167f, 93f) }) })
        val lv = st.levels
        if (lv != null) bars(c, rect(92f, 58f, 252f, 116f), lv, 0xFF4F7A3F.toInt())
        else {
            marquee(c, st.title, pt(0xFF557549.toInt(), 15f, true), rect(90f, 55f, 258f, 82f), 96f, 76f, st.clock)
            marquee(c, st.artist, pt(0xFF6A8A5C.toInt(), 12f), rect(90f, 82f, 258f, 100f), 96f, 95f, st.clock)
            c.drawText("${fmt(st.posMs)} / ${fmt(st.durMs)}", 132f, 121f, pt(0xFF6A8A5C.toInt(), 11f))
        }
        val f = frac(st)
        c.drawRoundRect(rect(106f, 128f, 234f, 131f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33224422 })
        c.drawRoundRect(rect(106f, 128f, 106f + 128f * f, 131f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4B7A3A.toInt() })
        c.drawCircle(106f + 128f * f, 129.5f, 3.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3F6B30.toInt() })
        c.restore()
        // the play key shows what it will do
        glyph(c, 334f, 112f, 7f, 0xFFF4FFEA.toInt(), st.playing)
    }
}

/** A Windows 7 style player window: black stage with the cover, a seek bar and a round blue play button. */
object Win7Skin : ImgSkin("skin2", "Windows 7", 420, 380, listOf(
    Hit("open", rect(0f, 0f, 300f, 28f)), Hit("vis", rect(108f, 72f, 310f, 274f)), Hit("seek", rect(26f, 274f, 394f, 294f)),
    Hit("prev", rect(124f, 316f, 184f, 348f)), Hit("next", rect(238f, 316f, 298f, 348f)), Hit("play", circ(210f, 331f, 27f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val white = pt(0xFFFFFFFF.toInt(), 17f, true)
        val tw = white.measureText(st.title)
        if (tw <= 290f) c.drawText(st.title, 210f - tw / 2f, 49f, white)
        else marquee(c, st.title, white, rect(60f, 30f, 360f, 54f), 66f, 49f, st.clock)
        c.drawText(st.artist, 210f, 66f, pt(0xFFFFFFFF.toInt(), 14f, false, Paint.Align.CENTER))
        val box = rect(108f, 72f, 310f, 274f)
        val lv = st.levels
        if (lv != null) { c.drawRect(box, Paint().apply { color = 0xCC000000.toInt() }); bars(c, rect(116f, 80f, 302f, 266f), lv, 0xFF36B8D8.toInt()) }
        else if (st.cover != null) cover(c, st.cover, box, 0f)
        // the seek bar
        c.drawRoundRect(rect(28f, 280f, 392f, 288f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt() })
        c.drawRoundRect(rect(28f, 280f, 28f + 364f * frac(st), 288f), 4f, 4f, lin(0f, 280f, 0f, 288f, 0xFF5F82D8.toInt(), 0xFF2D4FA0.toInt()))
        // the play button
        c.drawCircle(210f, 331f, 25f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(210f, 322f, 32f, intArrayOf(0xFF86CBFF.toInt(), 0xFF2E80E4.toInt(), 0xFF0A3A9C.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) })
        c.drawOval(rect(195f, 309f, 225f, 324f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF })
        glyph(c, 210f, 331f, 10f, 0xFFFFFFFF.toInt(), st.playing)
    }
}

/** A pink capsule with a teal dot-matrix display. */
object PinkSkin : ImgSkin("skin3", "Candy", 423, 382, listOf(
    Hit("vis", rect(132f, 100f, 306f, 200f)), Hit("seek", rect(60f, 234f, 248f, 256f)), Hit("open", circ(318f, 126f, 13f)),
    Hit("play", rect(72f, 263f, 116f, 300f)), Hit("stop", rect(118f, 263f, 162f, 300f)), Hit("prev", rect(163f, 263f, 207f, 300f)), Hit("next", rect(210f, 263f, 254f, 300f)),
)) {
    private var font: Typeface? = null
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val tf = font ?: (ResourcesCompat.getFont(ctx, R.font.sb_hachicro) ?: Typeface.MONOSPACE).also { font = it }
        val lv = st.levels
        if (lv != null) bars(c, rect(134f, 104f, 300f, 196f), lv, 0xFFD8FFF6.toInt())
        else {
            marquee(c, st.title.uppercase(), pt(0xFFD8FFF6.toInt(), 25f, false, tf = tf), rect(148f, 106f, 296f, 140f), 152f, 132f, st.clock)
            c.drawText(fmt(st.posMs), 119f, 188f, pt(0xFFD8FFF6.toInt(), 25f, false, tf = tf))
        }
        // the seek bar: a pink line and a pill that slides on it
        val f = frac(st)
        c.drawRoundRect(rect(66f, 242f, 242f, 248f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEBB6D2.toInt() })
        c.drawRoundRect(rect(66f, 242f, 66f + 176f * f, 248f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9529F.toInt() })
        val tx = 66f + (176f - 28f) * f
        c.drawRoundRect(rect(tx, 238f, tx + 28f, 252f), 6f, 6f, lin(0f, 238f, 0f, 252f, 0xFFEAF5FF.toInt(), 0xFF8FB8EE.toInt()))
        c.drawRoundRect(rect(tx, 238f, tx + 28f, 252f), 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF7D8CB8.toInt() })
        // the play key
        c.drawOval(rect(73f, 265f, 115f, 298f), lin(0f, 265f, 0f, 298f, 0xFFEAF5FF.toInt(), 0xFF9CC4F0.toInt()))
        c.drawOval(rect(73f, 265f, 115f, 298f), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f; color = 0xFF8C98C0.toInt() })
        glyph(c, 94f, 282f, 8f, 0xFF28488C.toInt(), st.playing)
    }
}

/** A silver capsule with a blue display and a big round button. */
object SilverSkin : ImgSkin("skin4", "Silver", 443, 151, listOf(
    Hit("vis", rect(130f, 56f, 415f, 108f)), Hit("seek", rect(284f, 112f, 388f, 132f)), Hit("open", rect(412f, 54f, 436f, 72f)),
    Hit("play", circ(65f, 80f, 44f)), Hit("prev", rect(0f, 68f, 18f, 94f)), Hit("next", rect(110f, 68f, 130f, 94f)), Hit("stop", rect(22f, 112f, 62f, 146f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val lv = st.levels
        if (lv != null) bars(c, rect(136f, 58f, 408f, 108f), lv, 0xFFEAF3FF.toInt())
        else {
            c.drawText(if (st.playing) "Playing" else if (st.posMs > 0) "Paused" else "Stopped", 134f, 69f, pt(0xFFFFFFFF.toInt(), 13f))
            c.drawText("${fmt(st.posMs)} / ${fmt(st.durMs)}", 410f, 68f, pt(0xFFFFFFFF.toInt(), 9.5f, align = Paint.Align.RIGHT))
            marquee(c, "${st.title} • ${st.artist}", pt(0xFFFFFFFF.toInt(), 16f), rect(136f, 84f, 358f, 106f), 138f, 99f, st.clock)
        }
        // the seek slider
        val f = frac(st)
        c.drawRoundRect(rect(288f, 119f, 382f, 125f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA2AD.toInt() })
        c.drawRoundRect(rect(288f, 119f, 382f, 125f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF55595F.toInt() })
        val tx = 288f + 94f * f
        c.drawRect(rect(tx - 5f, 114f, tx + 5f, 129f), lin(0f, 114f, 0f, 129f, 0xFFB5C8E8.toInt(), 0xFF6F8BB8.toInt()))
        c.drawRect(rect(tx - 5f, 114f, tx + 5f, 129f), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF394A66.toInt() })
        glyph(c, 66f, 80f, 12f, 0xFF2C3E58.toInt(), st.playing)
    }
}

/** A Windows XP window with a cover, the artist and track fields and four big buttons. */
object LunaSkin : ImgSkin("skin5", "Luna", 422, 306, listOf(
    Hit("open", rect(0f, 0f, 40f, 32f)), Hit("vis", rect(25f, 59f, 190f, 224f)), Hit("seek", rect(277f, 196f, 394f, 222f)),
    Hit("prev", rect(34f, 241f, 89f, 296f)), Hit("play", rect(146f, 241f, 200f, 296f)), Hit("stop", rect(224f, 241f, 278f, 296f)), Hit("next", rect(334f, 241f, 389f, 296f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val box = rect(25f, 59f, 190f, 224f)
        val lv = st.levels
        if (lv != null) { c.drawRoundRect(box, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF007DA3.toInt() }); bars(c, rect(34f, 72f, 182f, 214f), lv, 0xFFE8FBFF.toInt()) }
        else if (st.cover != null) cover(c, st.cover, box, 14f)
        marquee(c, st.artist, pt(0xFF1A1A1A.toInt(), 13f), rect(277f, 70f, 392f, 88f), 282f, 83f, st.clock)
        marquee(c, st.title, pt(0xFF1A1A1A.toInt(), 13f), rect(277f, 130f, 392f, 148f), 282f, 143f, st.clock)
        c.drawText("${fmt(st.posMs)} / ${fmt(st.durMs)}", 277f, 190f, pt(0xFF2A2A2A.toInt(), 13f))
        val f = frac(st)
        c.drawRect(rect(277f, 202f, 394f, 212f), Paint().apply { color = 0xFFD4D0C4.toInt() })
        c.drawRect(rect(277f, 202f, 394f, 212f), Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF404040.toInt() })
        c.drawRect(rect(278f, 203f, 278f + 115f * f, 211f), lin(0f, 203f, 0f, 211f, 0xFF4E87E8.toInt(), 0xFF1B4FC8.toInt()))
        // the green play key, with what it will do
        val key = rect(146f, 242f, 200f, 296f)
        c.drawRoundRect(key, 9f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        c.drawRoundRect(rect(149f, 245f, 197f, 293f), 7f, 7f, lin(0f, 245f, 0f, 293f, 0xFF45B845.toInt(), 0xFF0B800B.toInt()))
        glyph(c, 173f, 269f, 13f, 0xFFFFFFFF.toInt(), st.playing)
    }
}


// ---- five more, in the Frutiger Aero / early media player spirit ---------------------------------------------------------------------------------

/** Previous / next: a bar and a triangle (or two). */
private fun skip(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, back: Boolean) {
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val d = if (back) -1f else 1f
    c.drawRect(RectF(cx + d * s * .85f - s * .14f, cy - s * .8f, cx + d * s * .85f + s * .14f, cy + s * .8f), p)
    c.drawPath(Path().apply { moveTo(cx + d * s * .6f, cy - s * .8f); lineTo(cx - d * s * .8f, cy); lineTo(cx + d * s * .6f, cy + s * .8f); close() }, p)
}

private fun orb(c: Canvas, cx: Float, cy: Float, r: Float, top: Int, bottom: Int) {
    c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy - r * .4f, r * 1.3f, intArrayOf(top, bottom), null, Shader.TileMode.CLAMP) })
    c.drawOval(RectF(cx - r * .6f, cy - r * .92f, cx + r * .6f, cy - r * .2f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x45FFFFFF })
}

/** An orange hand-held player with a purple dot display and a pad of blue keys. */
object BubbleSkin : ImgSkin("skin6", "Bubble", 448, 244, listOf(
    Hit("vis", rect(129f, 59f, 319f, 130f)), Hit("seek", rect(129f, 94f, 319f, 110f)),
    Hit("play", circ(48f, 92f, 22f)), Hit("next", circ(58f, 134f, 20f)), Hit("prev", circ(86f, 172f, 20f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val lcd = rect(129f, 59f, 319f, 130f)
        c.drawRoundRect(lcd, 6f, 6f, lin(0f, 59f, 0f, 130f, 0xFFB67CF0.toInt(), 0xFF9650E0.toInt()))
        val ink = 0xFFF4E8FF.toInt(); val dim = 0xFF6F32B8.toInt()
        val lv = st.levels
        if (lv != null) bars(c, rect(136f, 64f, 312f, 108f), lv, ink)
        else {
            c.drawText(fmt(st.posMs) + " / " + fmt(st.durMs), 136f, 76f, pt(ink, 11f))
            c.drawText(if (st.playing) "▶" else "❚❚", 306f, 76f, pt(ink, 11f, align = Paint.Align.RIGHT))
            marquee(c, st.artist, pt(dim, 11f), rect(134f, 80f, 316f, 92f), 136f, 90f, st.clock)
        }
        // the seek line
        val f = frac(st)
        c.drawRoundRect(rect(136f, 98f, 312f, 102f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF })
        c.drawRoundRect(rect(136f, 98f, 136f + 176f * f, 102f), 2f, 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
        // the song name on the bottom line, on a lighter band
        c.drawRect(rect(129f, 106f, 319f, 128f), Paint().apply { color = 0x66FFFFFF })
        marquee(c, st.title.uppercase(), pt(0xFF4A1A90.toInt(), 15f, true), rect(132f, 106f, 316f, 128f), 134f, 123f, st.clock)
        val blue = 0xFF2F63B2.toInt()
        glyph(c, 48f, 92f, 8f, blue, st.playing)
        skip(c, 58f, 134f, 7f, blue, false)
        skip(c, 86f, 172f, 7f, blue, true)
        val t = pt(blue, 22f, true, Paint.Align.CENTER)
        c.drawText("+", 400f, 99f, t); c.drawText("–", 390f, 141f, t)
    }
}

/** A blue and green Windows 7 style media window: the cover on a dark stage, the name, a seek bar and three keys. */
object AeroSkin : ImgSkin("skin7", "Aero", 444, 349, listOf(
    Hit("open", rect(0f, 0f, 320f, 24f)), Hit("vis", rect(141f, 21f, 302f, 183f)), Hit("seek", rect(11f, 255f, 435f, 271f)),
    Hit("prev", rect(138f, 283f, 198f, 330f)), Hit("play", circ(231f, 305f, 32f)), Hit("next", rect(264f, 283f, 326f, 330f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val box = rect(141f, 21f, 302f, 183f)
        val lv = st.levels
        if (lv != null) { c.drawRect(box, Paint().apply { color = 0xCC000000.toInt() }); bars(c, rect(148f, 28f, 295f, 176f), lv, 0xFF36E0D0.toInt()) }
        else if (st.cover != null) cover(c, st.cover, box, 0f)
        val white = pt(0xFFFFFFFF.toInt(), 22f, true, Paint.Align.CENTER)
        val tw = white.measureText(st.title)
        if (tw <= 400f) c.drawText(st.title, 222f, 220f, white) else marquee(c, st.title, pt(0xFFFFFFFF.toInt(), 22f, true), rect(16f, 198f, 430f, 228f), 20f, 220f, st.clock)
        c.drawText(st.artist, 222f, 245f, pt(0xFFFFFFFF.toInt(), 17f, false, Paint.Align.CENTER))
        val f = frac(st)
        c.drawRoundRect(rect(11f, 259f, 435f, 267f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55203848 })
        c.drawRoundRect(rect(11f, 259f, 11f + 424f * f, 267f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1F5878.toInt() })
        // the keys: two dark side pieces and a round middle one
        for (r in listOf(rect(138f, 285f, 200f, 328f), rect(262f, 285f, 326f, 328f)))
            c.drawRoundRect(r, 14f, 14f, lin(0f, r.top, 0f, r.bottom, 0xFF4A7392.toInt(), 0xFF264A66.toInt()))
        skip(c, 168f, 306f, 11f, 0xFFF2F6FA.toInt(), true); skip(c, 296f, 306f, 11f, 0xFFF2F6FA.toInt(), false)
        orb(c, 231f, 305f, 31f, 0xFF7FA6C2.toInt(), 0xFF28506E.toInt())
        glyph(c, 231f, 305f, 14f, 0xFFFFFFFF.toInt(), st.playing)
    }
}

/** A grey player with a big square screen, a green ticker line and a big round play key. */
object NexusSkin : ImgSkin("skin8", "Nexus", 470, 407, listOf(
    Hit("open", circ(454f, 190f, 16f)), Hit("vis", rect(54f, 46f, 400f, 284f)), Hit("seek", rect(204f, 320f, 404f, 336f)),
    Hit("play", circ(112f, 333f, 36f)), Hit("stop", circ(174f, 342f, 20f)), Hit("prev", rect(304f, 340f, 336f, 368f)), Hit("next", rect(358f, 340f, 414f, 368f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val screen = rect(54f, 46f, 400f, 284f)
        c.drawRect(screen, Paint().apply { color = 0xFF000000.toInt() })
        val lv = st.levels
        if (lv != null) bars(c, rect(64f, 56f, 390f, 276f), lv, 0xFF47E600.toInt())
        else if (st.cover != null) { val side = 238f; cover(c, st.cover, rect(screen.centerX() - side / 2f, screen.top, screen.centerX() + side / 2f, screen.bottom), 0f) }
        marquee(c, st.title + "  •  " + st.artist, pt(0xFF47E600.toInt(), 15f), rect(170f, 284f, 388f, 304f), 172f, 299f, st.clock)
        val f = frac(st)
        val tx = 204f + 200f * f
        c.drawRect(rect(204f, 326f, tx, 328f), Paint().apply { color = 0xFF3A3A44.toInt() })
        c.drawRect(rect(tx - 5f, 322f, tx + 5f, 332f), lin(0f, 322f, 0f, 332f, 0xFFF2F2F6.toInt(), 0xFF9A9AA6.toInt()))
        c.drawRect(rect(tx - 5f, 322f, tx + 5f, 332f), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFF4A4A55.toInt() })
        // the play key shows what it will do
        c.drawCircle(112f, 333f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(112f, 322f, 40f, intArrayOf(0xFFEFEFF4.toInt(), 0xFFB4B4C0.toInt()), null, Shader.TileMode.CLAMP) })
        glyph(c, 112f, 333f, 14f, 0xFF4A4A58.toInt(), st.playing)
    }
}

/** A big silver organic player: a screen with the cover, a ticker line and five round keys in the corner. */
object SymphoraSkin : ImgSkin("skin9", "Symphora", 480, 364, listOf(
    Hit("vis", rect(126f, 89f, 367f, 268f)), Hit("seek", rect(126f, 258f, 367f, 272f)),
    Hit("prev", circ(55f, 67f, 12f)), Hit("play", circ(75f, 52f, 12f)), Hit("stop", circ(102f, 40f, 12f)), Hit("next", circ(130f, 33f, 12f)), Hit("open", circ(157f, 28f, 12f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val screen = rect(126f, 89f, 367f, 268f)
        c.drawRect(screen, Paint().apply { color = 0xFF282828.toInt() })
        val lv = st.levels
        if (lv != null) bars(c, rect(132f, 95f, 361f, 262f), lv, 0xFFE8F4F8.toInt())
        else if (st.cover != null) cover(c, st.cover, rect(screen.centerX() - 89f, screen.top, screen.centerX() + 89f, screen.bottom), 0f)
        val f = frac(st)
        c.drawRect(rect(126f, 263f, 367f, 268f), Paint().apply { color = 0x88000000.toInt() })
        c.drawRect(rect(126f, 263f, 126f + 241f * f, 268f), Paint().apply { color = 0xFF6AD0F0.toInt() })
        // the ticker on its plate
        c.drawRect(rect(167f, 274f, 356f, 289f), Paint().apply { color = 0xFFB4BEC0.toInt() })
        marquee(c, st.title + " • " + st.artist, pt(0xFF1E2224.toInt(), 12f), rect(169f, 274f, 354f, 289f), 171f, 286f, st.clock)
        // pause shown on the play key while playing
        if (st.playing) { orb(c, 75f, 52f, 10f, 0xFFF0F4F4.toInt(), 0xFF98A4A6.toInt()); glyph(c, 75f, 52f, 5f, 0xFF2A3234.toInt(), true) }
    }
}

/** The old CD Player window: a picture box, the artist and track fields, a blue progress bar and four grey keys. */
object CdSkin : ImgSkin("skin10", "CD Player", 449, 193, listOf(
    Hit("open", rect(0f, 0f, 300f, 16f)), Hit("vis", rect(9f, 29f, 150f, 121f)), Hit("seek", rect(174f, 80f, 436f, 96f)),
    Hit("prev", rect(164f, 107f, 229f, 134f)), Hit("next", rect(234f, 107f, 300f, 134f)), Hit("play", rect(304f, 107f, 370f, 134f)), Hit("stop", rect(374f, 107f, 440f, 134f)),
)) {
    override fun draw(c: Canvas, ctx: Context, st: SkinState) {
        val box = rect(9f, 29f, 150f, 121f)
        val lv = st.levels
        if (lv != null) { c.drawRect(box, Paint().apply { color = 0xFF000000.toInt() }); bars(c, rect(14f, 34f, 145f, 116f), lv, 0xFF00D000.toInt()) }
        else if (st.cover != null) cover(c, st.cover, box, 0f)
        else c.drawRect(box, Paint().apply { color = 0xFF3C3C3C.toInt() })
        val black = pt(0xFF000000.toInt(), 14f, true)
        marquee(c, st.artist, black, rect(196f, 28f, 372f, 48f), 198f, 44f, st.clock)
        marquee(c, st.title, black, rect(196f, 49f, 372f, 69f), 198f, 65f, st.clock)
        c.drawText(fmt(st.posMs) + " / " + fmt(st.durMs), 174f, 79f, pt(0xFF000000.toInt(), 10f))
        val f = frac(st)
        c.drawRect(rect(174f, 84f, 436f, 93f), Paint().apply { color = 0xFFA8ACAC.toInt() })
        c.drawRect(rect(174f, 84f, 174f + 262f * f, 93f), Paint().apply { color = 0xFF0000A8.toInt() })
        // four keys with the old raised edge
        val keys = listOf(rect(164f, 107f, 229f, 134f), rect(234f, 107f, 300f, 134f), rect(304f, 107f, 370f, 134f), rect(374f, 107f, 440f, 134f))
        keys.forEach { k ->
            c.drawRect(k, Paint().apply { color = 0xFFC0C0C0.toInt() })
            c.drawRect(rect(k.left, k.top, k.right, k.top + 1.5f), Paint().apply { color = 0xFFFFFFFF.toInt() }); c.drawRect(rect(k.left, k.top, k.left + 1.5f, k.bottom), Paint().apply { color = 0xFFFFFFFF.toInt() })
            c.drawRect(rect(k.left, k.bottom - 1.5f, k.right, k.bottom), Paint().apply { color = 0xFF404040.toInt() }); c.drawRect(rect(k.right - 1.5f, k.top, k.right, k.bottom), Paint().apply { color = 0xFF404040.toInt() })
        }
        val ink = 0xFF000000.toInt()
        skip(c, keys[0].centerX(), 120.5f, 7f, ink, true); skip(c, keys[1].centerX(), 120.5f, 7f, ink, false)
        glyph(c, keys[2].centerX(), 120.5f, 7f, ink, st.playing)
        c.drawRect(rect(keys[3].centerX() - 6f, 114f, keys[3].centerX() + 6f, 127f), Paint().apply { color = ink })
    }
}
