package com.umutk.pulse.extras.video

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import android.util.TypedValue
import com.umutk.pulse.tr
import java.io.File
import java.nio.charset.Charset

internal val subExts = listOf("srt", "ass", "ssa", "vtt", "ttml", "xml")

internal fun subMimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
    "ass", "ssa" -> MimeTypes.TEXT_SSA
    "vtt" -> MimeTypes.TEXT_VTT
    "ttml", "xml" -> MimeTypes.APPLICATION_TTML
    else -> MimeTypes.APPLICATION_SUBRIP
}

private val tsRe = Regex("(\\d+):(\\d{2}):(\\d{2})([,.])(\\d{3})")
private val assRe = Regex("^(Dialogue:[^,]*,)(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2}),(\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})")

private fun shiftAss(g: List<String>, d: Long): String {
    fun one(h: String, m: String, s: String, cs: String): String {
        val t = (((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 100 + cs.toLong() + d / 10).coerceAtLeast(0)
        return "%d:%02d:%02d.%02d".format(t / 360000, t / 6000 % 60, t / 100 % 60, t % 100)
    }
    return g[1] + one(g[2], g[3], g[4], g[5]) + "," + one(g[6], g[7], g[8], g[9])
}

/** [text] with every cue moved by [delayMs] (SRT/VTT "h:mm:ss,mmm" times and ASS Dialogue lines; other formats come back unchanged). */
internal fun shiftSub(text: String, ext: String, delayMs: Long): String {
    if (delayMs == 0L) return text
    return when (ext) {
        "srt", "vtt" -> text.lines().joinToString("\n") { l ->
            if ("-->" !in l) l else tsRe.replace(l) { m ->
                val t = (((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 + m.groupValues[5].toLong() + delayMs).coerceAtLeast(0)
                "%02d:%02d:%02d%s%03d".format(t / 3600000, t / 60000 % 60, t / 1000 % 60, m.groupValues[4], t % 1000)
            }
        }
        "ass", "ssa" -> text.lines().joinToString("\n") { l ->
            val m = assRe.find(l)
            if (m == null) l else shiftAss(m.groupValues, delayMs) + l.substring(m.value.length)
        }
        else -> text
    }
}

/**
 * Reads a subtitle in [enc], moves it by [delayMs] and writes a UTF-8 copy into the cache.
 * Returns the copy, or null when the file cannot be read.
 */
internal fun prepareSub(ctx: Context, src: Uri, name: String, enc: String, delayMs: Long): Uri? = runCatching {
    val bytes = (if (src.scheme == "file") File(src.path!!).inputStream() else ctx.contentResolver.openInputStream(src))?.use { it.readBytes() } ?: return null
    val cs = runCatching { Charset.forName(enc) }.getOrDefault(Charsets.UTF_8)
    val ext = name.substringAfterLast('.', "srt").lowercase()
    val text = shiftSub(String(bytes, cs).removePrefix("﻿"), ext, delayMs)
    val out = File(ctx.cacheDir, "sub_" + (src.toString().hashCode().toLong() and 0xffffffffL) + "." + ext)
    out.writeText(text, Charsets.UTF_8)
    Uri.fromFile(out)
}.getOrNull()

private val dirCache = HashMap<String, List<File>>()

/** Subtitle files next to a local video, e.g. Movie.srt or Movie.en.srt (needs all-files access; empty without it). */
internal fun siblingSubs(ctx: Context, v: VideoItem, prefs: VideoPrefs): List<MediaItem.SubtitleConfiguration> {
    if (!prefs.subAuto || !v.local || v.path.isBlank() || !com.umutk.pulse.Compat.isStorageManager()) return emptyList()
    val dir = File(Environment.getExternalStorageDirectory(), v.path)
    val files = dirCache.getOrPut(dir.path) { dir.listFiles()?.filter { it.extension.lowercase() in subExts }.orEmpty() }
    val base = v.name.substringBeforeLast('.')
    return files.filter { it.name.startsWith(base + ".") }.mapNotNull { f ->
        val lang = f.nameWithoutExtension.removePrefix(base).trim('.').takeIf { it.length in 2..3 }
        val uri = if (prefs.subEnc == "UTF-8") Uri.fromFile(f) else prepareSub(ctx, Uri.fromFile(f), f.name, prefs.subEnc, 0) ?: return@mapNotNull null
        MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(subMimeOf(f.name)).setLanguage(lang).setLabel(f.name)
            .setSelectionFlags(if (lang != null && lang == prefs.prefSub) C.SELECTION_FLAG_DEFAULT else 0).build()
    }
}

internal val subColors = listOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF69F0AE.toInt(), 0xFF4DD0E1.toInt(), 0xFFFFA726.toInt(), 0xFFF48FB1.toInt())
internal fun subColorNames() = listOf(tr("White", "Beyaz"), tr("Yellow", "Sarı"), tr("Green", "Yeşil"), tr("Cyan", "Camgöbeği"), tr("Orange", "Turuncu"), tr("Pink", "Pembe"))
internal fun subFontNames() = listOf(tr("Sans", "Sans"), tr("Serif", "Serif"), tr("Mono", "Mono"), tr("Casual", "Günlük"), tr("Cursive", "El yazısı"))
internal fun subEdgeNames() = listOf(tr("Outline", "Kenar"), tr("Shadow", "Gölge"), tr("Raised", "Kabartma"), tr("Depressed", "Çukur"))
private val subFamilies = listOf("sans-serif", "serif", "monospace", "casual", "cursive")

internal fun subTypeface(p: VideoPrefs): Typeface =
    Typeface.create(subFamilies[p.subFont.coerceIn(subFamilies.indices)], if (p.subBold) Typeface.BOLD else Typeface.NORMAL)

/** Subtitle look from the saved options: colour, font, bold, edge kind and background. */
internal fun subStyle(prefs: VideoPrefs): CaptionStyleCompat = CaptionStyleCompat(
    subColors[prefs.subColor.coerceIn(subColors.indices)],
    if (prefs.subBg) (prefs.subBgAlpha.coerceIn(0, 100) * 255 / 100 shl 24) else Color.TRANSPARENT, Color.TRANSPARENT,
    if (!prefs.subEdge) CaptionStyleCompat.EDGE_TYPE_NONE else when (prefs.subEdgeKind) {
        2 -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW; 3 -> CaptionStyleCompat.EDGE_TYPE_RAISED; 4 -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED; else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
    }, Color.BLACK, subTypeface(prefs),
)

/** Applies every subtitle option to the player's subtitle view. */
internal fun SubtitleView.applySubPrefs(p: VideoPrefs) {
    setStyle(subStyle(p))
    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, p.subSize.toFloat())
    setApplyEmbeddedStyles(!p.subIgnoreStyle); setApplyEmbeddedFontSizes(!p.subIgnoreStyle)
    setBottomPaddingFraction(p.subPos.coerceIn(0, 40) / 100f)
    val m = (p.subMargin * resources.displayMetrics.density).toInt()
    setPadding(m, 0, m, 0)
}
