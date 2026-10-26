package com.umutk.pulse.extras.lyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr
import java.io.File

/** Sharing a few lines of the lyrics as a picture: the colours come from the cover, the text colour follows the card colour. */
object LyricsShare {
    private fun prefs(c: Context) = c.getSharedPreferences(LiveLyrics.PREFS, Context.MODE_PRIVATE)

    /** Off (the default): any lines can be picked. On: only lines that follow each other. */
    fun consecutive(c: Context) = prefs(c).getBoolean("share_consecutive", false)
    fun setConsecutive(c: Context, v: Boolean) { prefs(c).edit().putBoolean("share_consecutive", v).apply() }

    const val MAX_LINES = 10
    /** The colour of the cover that stands out most; a calm blue-grey when there is no cover. */
    fun baseColor(art: Bitmap?): Int {
        if (art == null) return 0xFF3A6A78.toInt()
        val p = runCatching { androidx.palette.graphics.Palette.from(art).clearFilters().generate() }.getOrNull()
        return p?.vibrantSwatch?.rgb ?: p?.mutedSwatch?.rgb ?: p?.dominantSwatch?.rgb ?: 0xFF3A6A78.toInt()
    }

    private fun readable(bg: Int) = if (ColorUtils.calculateLuminance(bg) > .5) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()

    /** Five colours of the cover, then black and white. */
    const val STYLES = 7

    /** The seven card colours: five from the cover (vivid ones first, never two that look alike), black, white. */
    fun palette(art: Bitmap?): List<Int> {
        val out = ArrayList<Int>()
        fun add(c: Int?) {
            if (c == null || out.size >= 5) return
            val hsl = FloatArray(3).also { ColorUtils.colorToHSL(c, it) }
            // a near-black or near-white swatch is left to the black and white circles
            if (hsl[2] < .08f || hsl[2] > .94f) return
            if (out.none { o -> rgbDist(o, c) < 46 }) out.add(c or 0xFF000000.toInt())
        }
        if (art != null) {
            val p = runCatching { androidx.palette.graphics.Palette.from(art).maximumColorCount(24).clearFilters().generate() }.getOrNull()
            if (p != null) {
                add(p.vibrantSwatch?.rgb); add(p.darkVibrantSwatch?.rgb); add(p.lightVibrantSwatch?.rgb)
                add(p.mutedSwatch?.rgb); add(p.darkMutedSwatch?.rgb); add(p.lightMutedSwatch?.rgb); add(p.dominantSwatch?.rgb)
                p.swatches.sortedByDescending { it.population }.forEach { add(it.rgb) }
            }
        }
        // a cover with few colours (or none): tints of the base colour fill the rest
        val base = baseColor(art)
        var k = 0
        while (out.size < 5 && k < 12) { add(ColorUtils.blendARGB(base, if (k % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), .18f + .12f * (k / 2))); k++ }
        while (out.size < 5) out.add(base)
        return out + 0xFF000000.toInt() + 0xFFFFFFFF.toInt()
    }

    private fun rgbDist(a: Int, b: Int): Int {
        val dr = android.graphics.Color.red(a) - android.graphics.Color.red(b)
        val dg = android.graphics.Color.green(a) - android.graphics.Color.green(b)
        val db = android.graphics.Color.blue(a) - android.graphics.Color.blue(b)
        return Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
    }

    /** The card alone as a picture with see-through corners and no background: the cover, the song, the chosen lines and a small mark, on [card]. */
    fun render(title: String, artist: String, art: Bitmap?, lines: List<String>, card: Int, w: Int = 1080, photo: Boolean = false): Bitmap {
        val onPhoto = photo && art != null
        // on the cover photo itself: a light cover gets a pale veil and dark writing, a dark cover a dark veil and white writing
        val lightPhoto = onPhoto && ColorUtils.calculateLuminance(Bitmap.createScaledBitmap(art!!, 1, 1, true).getPixel(0, 0)) > .5
        val ink = if (onPhoto) (if (lightPhoto) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()) else readable(card)
        val pad = 64f
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 62f * w / 1080f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val k = w / 1080f
        val textW = (w - 2 * pad * k).toInt()
        val layouts = lines.map { StaticLayout.Builder.obtain(it, 0, it.length, text, textW).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.08f).build() }
        val gap = 34f * k
        val head = (150f + 56f) * k
        val body = layouts.sumOf { it.height.toDouble() }.toFloat() + gap * (layouts.size - 1).coerceAtLeast(0)
        val foot = (40f + 56f) * k
        val h = (pad * k + head + body + foot + pad * k).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 56f * k, 56f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
        if (onPhoto) {
            // the cover fills the card (centre crop), then the veil keeps the writing readable
            val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
            val clip = android.graphics.Path().apply { addRoundRect(full, 56f * k, 56f * k, android.graphics.Path.Direction.CW) }
            val scale = maxOf(w.toFloat() / art!!.width, h.toFloat() / art.height)
            val sw = (w / scale).toInt().coerceAtMost(art.width); val sh = (h / scale).toInt().coerceAtMost(art.height)
            val src = android.graphics.Rect((art.width - sw) / 2, (art.height - sh) / 2, (art.width + sw) / 2, (art.height + sh) / 2)
            c.save(); c.clipPath(clip)
            c.drawBitmap(art, src, full, Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawRect(full, Paint().apply { color = if (lightPhoto) 0x99FFFFFF.toInt() else 0x99000000.toInt() })
            c.restore()
        }
        var y = pad * k
        val left = pad * k
        if (art != null && !onPhoto) {
            val r = RectF(left, y, left + 150f * k, y + 150f * k)
            val clip = android.graphics.Path().apply { addRoundRect(r, 18f * k, 18f * k, android.graphics.Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(art, null, r, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
        }
        val tx = left + (if (art != null && !onPhoto) (150f + 28f) * k else 0f)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 50f * k; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(ink, 170); textSize = 42f * k; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val room = w - pad * k - tx
        c.drawText(android.text.TextUtils.ellipsize(title, tp, room, android.text.TextUtils.TruncateAt.END).toString(), tx, y + 66f * k, tp)
        c.drawText(android.text.TextUtils.ellipsize(artist, ap, room, android.text.TextUtils.TruncateAt.END).toString(), tx, y + 124f * k, ap)
        y += head
        for (l in layouts) { c.save(); c.translate(left, y); l.draw(c); c.restore(); y += l.height + gap }
        val mp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(ink, 150); textSize = 40f * k; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        c.drawText("Pulse", left, h - pad * k - 4f * k, mp)
        return bmp
    }

    /** Hands the picture to the share sheet. */
    fun share(ctx: Context, bmp: Bitmap) {
        val f = File(ctx.cacheDir, "lyrics-share/lyrics-${System.currentTimeMillis()}.png").apply { parentFile?.mkdirs() }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Step one of the share: the lines as a list; tap the ones to share (a picked line turns dark), then Continue. */
@Composable
fun LyricsSelect(res: LyricsResult, selected: Set<Int>, onToggle: (Int) -> Unit, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        Column(Modifier.fillMaxSize()) {
            Text(
                if (selected.isEmpty()) tr("Select lyrics", "Söz seç") else tr("${selected.size} lines selected", "${selected.size} satır seçildi"),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp, bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(res.lines) { i, l ->
                    if (l.text.isNotBlank()) {
                        val on = i in selected
                        Text(
                            l.text, color = if (on) Color.White else Color.White.copy(alpha = .8f), fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) Color.Black else Color.White.copy(alpha = .1f)).clickable { onToggle(i) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
                item { Box(Modifier.size(90.dp)) }
            }
        }
        Text(
            tr("Continue", "Devam"), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).clip(RoundedCornerShape(26.dp)).background(if (selected.isEmpty()) Color.White.copy(alpha = .4f) else Color.White)
                .clickable(enabled = selected.isNotEmpty()) { onContinue() }.padding(horizontal = 40.dp, vertical = 13.dp),
        )
    }
}

/** Step two: the card alone, seven colours to pick from (five of the cover, black, white), and Share. */
@Composable
fun LyricsSharePage(title: String, artist: String, art: androidx.compose.ui.graphics.ImageBitmap?, lines: List<String>, style: Int, onStyle: (Int) -> Unit, onShare: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    // a hardware bitmap cannot be read for its colours (the palette fell back to the blue-green): a software copy can
    val artBmp = remember(art) { art?.asAndroidBitmap()?.let { b -> if (android.os.Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b } }
    val colors = remember(artBmp) { LyricsShare.palette(artBmp) }
    // the last choice is the cover photo itself as the card
    val photo = artBmp != null && style == colors.size
    val card = colors[style.coerceIn(0, colors.lastIndex)]
    val bmp = remember(title, artist, artBmp, lines, card, photo) { LyricsShare.render(title, artist, artBmp, lines, card, 720, photo) }
    val img = remember(bmp) { bmp.asImageBitmap() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            colors.forEachIndexed { s, col ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(col)).border(if (s == style) 3.dp else 1.dp, if (s == style) Color.White else Color.White.copy(alpha = .4f), CircleShape).clickable { onStyle(s) })
            }
            if (art != null) Box(Modifier.size(36.dp).clip(CircleShape).border(if (photo) 3.dp else 1.dp, if (photo) Color.White else Color.White.copy(alpha = .4f), CircleShape).clickable { onStyle(colors.size) }) {
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Row(Modifier.padding(vertical = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).clickable { onShare(LyricsShare.render(title, artist, artBmp, lines, card, photo = photo)) }.padding(horizontal = 34.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("share", Color.Black, 20.dp)
            Text(tr("Share", "Paylaş"), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
