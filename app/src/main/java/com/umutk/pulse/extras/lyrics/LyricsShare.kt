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
    const val STYLES = 3

    /** The colour of the cover that stands out most; a calm blue-grey when there is no cover. */
    fun baseColor(art: Bitmap?): Int {
        if (art == null) return 0xFF3A6A78.toInt()
        val p = runCatching { androidx.palette.graphics.Palette.from(art).clearFilters().generate() }.getOrNull()
        return p?.vibrantSwatch?.rgb ?: p?.mutedSwatch?.rgb ?: p?.dominantSwatch?.rgb ?: 0xFF3A6A78.toInt()
    }

    private fun readable(bg: Int) = if (ColorUtils.calculateLuminance(bg) > .5) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()

    /**
     * The card as a picture, 1080 x 1920: a card in the colour of the cover with the cover, the song, the chosen lines and a small mark, on a background that is
     * [style] 0 a fade from the cover colour to black, 1 the cover colour, 2 black.
     */
    fun render(title: String, artist: String, art: Bitmap?, lines: List<String>, style: Int, w: Int = 1080, h: Int = 1920): Bitmap {
        val base = baseColor(art)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (style) {
            0 -> bgPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(ColorUtils.blendARGB(base, 0xFF000000.toInt(), .25f), ColorUtils.blendARGB(base, 0xFF000000.toInt(), .88f), 0xFF000000.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            1 -> bgPaint.color = ColorUtils.blendARGB(base, 0xFF000000.toInt(), .2f)
            else -> bgPaint.color = 0xFF000000.toInt()
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        // the card: the cover colour, lifted a little so it reads against the background
        val card = if (style == 1) ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), .12f) else base
        val ink = readable(card)
        val pad = 64f
        val cardW = w - 2 * 80f
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 62f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val textW = (cardW - 2 * pad).toInt()
        val layouts = lines.map { StaticLayout.Builder.obtain(it, 0, it.length, text, textW).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.08f).build() }
        val gap = 34f
        val head = 150f + 56f
        val body = layouts.sumOf { it.height.toDouble() }.toFloat() + gap * (layouts.size - 1).coerceAtLeast(0)
        val foot = 40f + 56f
        val cardH = pad + head + body + foot + pad
        val top = ((h - cardH) / 2f).coerceAtLeast(120f)
        val left = 80f
        val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card }
        c.drawRoundRect(RectF(left, top, left + cardW, top + cardH), 56f, 56f, cp)
        var y = top + pad
        // cover, title, artist
        if (art != null) {
            val r = RectF(left + pad, y, left + pad + 150f, y + 150f)
            val clip = android.graphics.Path().apply { addRoundRect(r, 18f, 18f, android.graphics.Path.Direction.CW) }
            c.save(); c.clipPath(clip); c.drawBitmap(art, null, r, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
        }
        val tx = left + pad + (if (art != null) 150f + 28f else 0f)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 50f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(ink, 170); textSize = 42f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        val room = left + cardW - pad - tx
        c.drawText(android.text.TextUtils.ellipsize(title, tp, room, android.text.TextUtils.TruncateAt.END).toString(), tx, y + 66f, tp)
        c.drawText(android.text.TextUtils.ellipsize(artist, ap, room, android.text.TextUtils.TruncateAt.END).toString(), tx, y + 124f, ap)
        y += head
        // the lines
        for (l in layouts) { c.save(); c.translate(left + pad, y); l.draw(c); c.restore(); y += l.height + gap }
        // the mark
        val mp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ColorUtils.setAlphaComponent(ink, 150); textSize = 40f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        c.drawText("Pulse", left + pad, top + cardH - pad - 4f, mp)
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

/** Step two: the card as it will be shared, three backgrounds to pick from, and Share. */
@Composable
fun LyricsSharePage(title: String, artist: String, art: androidx.compose.ui.graphics.ImageBitmap?, lines: List<String>, style: Int, onStyle: (Int) -> Unit, onShare: (Bitmap) -> Unit, modifier: Modifier = Modifier) {
    val artBmp = remember(art) { art?.asAndroidBitmap() }
    val bmp = remember(title, artist, artBmp, lines, style) { LyricsShare.render(title, artist, artBmp, lines, style, 540, 960) }
    val img = remember(bmp) { bmp.asImageBitmap() }
    val base = remember(artBmp) { Color(LyricsShare.baseColor(artBmp)) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(img, null, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp, vertical = 14.dp).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Fit)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            for (s in 0 until LyricsShare.STYLES) {
                val fill = when (s) { 0 -> base.copy(alpha = .75f); 1 -> base; else -> Color.Black }
                Box(Modifier.size(40.dp).clip(CircleShape).background(fill).border(if (s == style) 3.dp else 1.dp, if (s == style) Color.White else Color.White.copy(alpha = .4f), CircleShape).clickable { onStyle(s) })
            }
        }
        Row(Modifier.padding(vertical = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).clickable { onShare(LyricsShare.render(title, artist, artBmp, lines, style)) }.padding(horizontal = 34.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("share", Color.Black, 20.dp)
            Text(tr("Share", "Paylaş"), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
