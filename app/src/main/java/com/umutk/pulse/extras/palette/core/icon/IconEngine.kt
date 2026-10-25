package com.umutk.pulse.extras.palette.core.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.umutk.pulse.extras.palette.core.color.contrastRatio
import com.umutk.pulse.extras.palette.core.color.toLab
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import com.umutk.pulse.extras.palette.core.palette.nearestIndex
import com.umutk.pulse.extras.palette.AppInfo
import com.umutk.pulse.extras.palette.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.random.Random

/** Per-app user decisions that override the global theme. */
data class IconOverride(
    val key: String,
    val excluded: Boolean = false,
    val locked: Boolean = false,
    val colorIndex: Int = -1,
    val styleId: String? = null,
    val favorite: Boolean = false
)

/**
 * The orchestrator: app -> colour -> pixels.
 *
 * It is deliberately stateless apart from the cache, so it can run on the UI
 * thread's coroutine scope for a single preview tile or across every core for a
 * full 20 000-app export.
 */
class IconEngine(
    private val ctx: Context,
    private val apps: AppRepository,
    val cache: IconCache
) {

    // ---- colour assignment ------------------------------------------------

    private val luminanceRank = HashMap<String, Float>()

    /**
     * Real app icons cluster tightly around mid-bright luminance, so mapping
     * raw luminance straight onto the ramp piles most apps onto 1-2 stops.
     * Ranking by luminance and spreading ranks evenly across [0,1] guarantees
     * the full ramp gets used regardless of the actual brightness distribution.
     */
    fun prepareLuminanceRanks(apps: List<AppInfo>) {
        val sorted = apps.sortedBy { brandColor(it).toLab().l }
        sorted.forEachIndexed { i, app ->
            luminanceRank[app.packageName] = if (sorted.size <= 1) 0.5f else i / (sorted.size - 1f)
        }
    }

    fun colorFor(app: AppInfo, spec: ThemeSpec, index: Int, override: IconOverride? = null): Int {
        val p = spec.palette
        if (p.colors.isEmpty()) return 0xFF888888.toInt()
        if (override?.colorIndex != null && override.colorIndex >= 0)
            return p.colors[override.colorIndex % p.size]

        // GRID colours are pinned to a specific grid cell in insertion order —
        // sampling the lightness-sorted ramp below would scramble that mapping.
        if (spec.assignment == ColorAssignment.GRID) return p.colors[index % p.colors.size]

        val i = when (spec.assignment) {
            ColorAssignment.SINGLE -> spec.singleColorIndex
            ColorAssignment.ORDER -> index
            ColorAssignment.HASH -> stableHash(app.packageName, spec.seed)
            ColorAssignment.DOMINANT -> p.nearestIndex(brandColor(app))
            ColorAssignment.LUMINANCE -> {
                val rank = luminanceRank[app.packageName] ?: brandColor(app).toLab().l
                (rank * (p.size - 1)).toInt()
            }
            ColorAssignment.CATEGORY -> categoryIndex(app, spec)
            ColorAssignment.GRID -> index // unreachable: handled by the early return above
        }
        val jitter = if (spec.randomness > 0f)
            Random(app.packageName.hashCode().toLong() xor spec.seed)
                .nextInt(-(spec.randomness * 2).toInt().coerceAtLeast(0),
                    (spec.randomness * 2).toInt().coerceAtLeast(1))
        else 0
        return p.ramp[((i + jitter) % p.size + p.size) % p.size]
    }

    private fun stableHash(pkg: String, seed: Long): Int {
        var h = seed
        for (ch in pkg) h = h * 31 + ch.code
        return abs((h xor (h ushr 32)).toInt())
    }

    private val brandCache = HashMap<String, Int>()

    /** Dominant colour of the real icon, cached — drives DOMINANT assignment. */
    fun brandColor(app: AppInfo): Int = brandCache.getOrPut(app.packageName) {
        val d = apps.loadIcon(app) ?: return@getOrPut 0xFF888888.toInt()
        val bmp = GlyphExtractor.rasterize(d, 48)
        val px = IntArray(48 * 48)
        bmp.getPixels(px, 0, 48, 0, 0, 48, 48)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (p in px) {
            if ((p ushr 24) < 100) continue
            val lab = p.toLab()
            val w = (lab.chroma * 40f + 1f).toLong()   // weight saturated pixels
            r += ((p shr 16) and 0xFF) * w; g += ((p shr 8) and 0xFF) * w
            b += (p and 0xFF) * w; n += w
        }
        if (n == 0L) 0xFF888888.toInt()
        else android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    // ---- rendering --------------------------------------------------------

    suspend fun render(
        app: AppInfo,
        spec: ThemeSpec,
        index: Int,
        size: Int = 192,
        override: IconOverride? = null,
        backdrop: Bitmap? = null,
        useCache: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val key = cache.key(app.key + (override?.styleId ?: ""), spec.renderKey(), size)
        if (useCache) cache.get(key)?.let { return@withContext it }

        val bmp = generate(app, spec, index, size, override, backdrop)
        if (useCache) cache.put(key, bmp)
        bmp
    }

    private fun generate(
        app: AppInfo, spec: ThemeSpec, index: Int, size: Int,
        override: IconOverride?, backdrop: Bitmap?
    ): Bitmap {
        val color = colorFor(app, spec, index, override)
        val seed = app.packageName.hashCode().toLong() xor spec.seed

        if (override?.excluded == true) {
            val d = apps.loadIcon(app)
            if (d != null) return GlyphExtractor.rasterize(d, size)
        }

        return when (spec.mode) {
            IconMode.TILES -> TileRenderer.render(spec, color, size, backdrop, seed)
            IconMode.STYLED -> renderStyled(app, spec, color, size, override, backdrop, seed)
            IconMode.ORIGINAL -> renderOriginal(app, spec, color, size)
        }
    }

    private fun renderStyled(
        app: AppInfo, spec: ThemeSpec, color: Int, size: Int,
        override: IconOverride?, backdrop: Bitmap?, seed: Long
    ): Bitmap {
        val style = StyleCatalog.get(override?.styleId ?: spec.style)
        val d = apps.loadIcon(app)
        val glyph = d?.let { GlyphExtractor.extract(it, 192) }
        val p = spec.palette
        val bg = if (spec.assignment == ColorAssignment.SINGLE) color
        else blendTone(p, color, style.bgTone)
        val fg = p.sample(style.fgTone)

        // Quality gate: an unreadable glyph is worse than no glyph at all.
        if (glyph == null || glyph.quality < 0.28f || glyph.coverage < 0.03f) {
            return TileRenderer.render(spec.copy(
                shape = style.shape, cornerRadius = style.corner, finish = style.finish,
                gradientIntensity = maxOf(spec.gradientIntensity, style.gradient)
            ), bg, size, backdrop, seed)
        }
        return StyledRenderer.render(spec, style, glyph.mask, bg, fg, size, backdrop, seed)
    }

    private fun blendTone(p: ColorPalette, assigned: Int, tone: Float): Int {
        // keep the app's assigned identity but push it to the style's tonal slot
        val target = p.sample(tone)
        return com.umutk.pulse.extras.palette.core.color.mixColors(assigned, target, 0.55f)
    }

    private fun renderOriginal(app: AppInfo, spec: ThemeSpec, color: Int, size: Int): Bitmap {
        val lut = Recolorizer.buildLut(spec.palette, spec.contrast, spec.brightness, spec.chroma)
        val d: Drawable = apps.loadIcon(app)
            ?: return TileRenderer.render(spec, color, size)
        val bg = if (spec.backgroundIndex >= 0)
            spec.palette.colors[spec.backgroundIndex % spec.palette.size] else color
        return if (d is AdaptiveIconDrawable) Recolorizer.recolorAdaptive(d, spec, lut, bg, size)
        else Recolorizer.recolorLegacy(d, spec, lut, bg, size)
    }

    /** Objective score used by the "regenerate low quality icons" batch action. */
    fun qualityScore(bmp: Bitmap, spec: ThemeSpec): Float {
        val s = 32
        val small = Bitmap.createScaledBitmap(bmp, s, s, true)
        val px = IntArray(s * s)
        small.getPixels(px, 0, s, 0, 0, s, s)
        val opaque = px.filter { (it ushr 24) > 200 }
        if (opaque.isEmpty()) return 0f
        val minL = opaque.minOf { it.toLab().l }
        val maxL = opaque.maxOf { it.toLab().l }
        val range = (maxL - minL)
        val bgContrast = contrastRatio(opaque.first(), spec.palette.darkest)
        return (range * 2.2f).coerceAtMost(1f) * 0.6f + (bgContrast / 6f).coerceAtMost(1f) * 0.4f
    }

    /** Whole-library generation with bounded parallelism, for export/apply. */
    suspend fun renderAll(
        list: List<AppInfo>,
        spec: ThemeSpec,
        size: Int = 192,
        overrides: Map<String, IconOverride> = emptyMap(),
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<AppInfo, Bitmap> = coroutineScope {
        val chunkSize = 24
        val out = LinkedHashMap<AppInfo, Bitmap>()
        var done = 0
        val indexed = list.withIndex().toList()
        indexed.chunked(chunkSize).forEach { chunk ->
            val jobs = chunk.map { (idx, app) ->
                async(Dispatchers.Default) {
                    app to render(app, spec, idx, size, overrides[app.key], useCache = true)
                }
            }
            jobs.forEach { j ->
                val (a, b) = j.await()
                out[a] = b
                done++
                onProgress(done, list.size)
            }
        }
        out
    }
}

/** Rough Play-category grouping without any network: package namespace heuristics. */
private fun categoryIndex(app: AppInfo, spec: ThemeSpec): Int {
    val p = app.packageName
    val group = when {
        listOf("whatsapp", "telegram", "messeng", "signal", "discord", "slack", "sms", "mms").any { p.contains(it) } -> 0
        listOf("instagram", "tiktok", "twitter", "facebook", "snapchat", "reddit", "pinterest", "threads").any { p.contains(it) } -> 1
        listOf("spotify", "music", "youtube", "netflix", "video", "player", "podcast").any { p.contains(it) } -> 2
        listOf("camera", "gallery", "photo", "editor", "draw", "design").any { p.contains(it) } -> 3
        listOf("bank", "wallet", "pay", "finance", "invest").any { p.contains(it) } -> 4
        listOf("settings", "system", "tools", "files", "manager").any { p.contains(it) } -> 5
        else -> 6
    }
    return group % spec.palette.size.coerceAtLeast(1)
}
