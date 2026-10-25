package com.umutk.pulse.extras.palette.core.palette

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.palette.graphics.Palette as AndroidPalette
import com.umutk.pulse.extras.palette.core.color.Lab
import com.umutk.pulse.extras.palette.core.color.labOfPixels
import com.umutk.pulse.extras.palette.core.color.toColor
import com.umutk.pulse.extras.palette.core.color.toLab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

/**
 * Turns pixels into palettes. Three ladders, best first:
 *  1. k-means in Oklab (own implementation, perceptually correct clusters)
 *  2. AndroidX Palette swatches (fast fallback / seed for k-means)
 *  3. WallpaperColors system API (zero-permission wallpaper extraction)
 */
object PaletteExtractor {

    /** Every extractor below clamps here — a palette pulled from a photo/wallpaper should never balloon past a dozen swatches. */
    private const val MAX_COLORS = 12

    suspend fun fromBitmap(bmp: Bitmap, count: Int = 5): ColorPalette = withContext(Dispatchers.Default) {
        val k = count.coerceIn(2, MAX_COLORS)
        val small = scaleForAnalysis(bmp, 160)
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val clusters = kmeans(pixels, k)
        PaletteGenerator.normalize(
            ColorPalette(name = "From image", colors = clusters, source = "image")
        )
    }

    /** Cell traversal order when flattening a home-screen photo's grid into a palette. */
    enum class GridOrder { READING, DIAGONAL }

    /**
     * Slices a photo of a home screen into a [columns]x[rows] grid and averages
     * each cell's colour, then flattens it in [order] — the same order
     * [com.umutk.pulse.extras.palette.AppInfo] lists are shown in. Paired with
     * [com.umutk.pulse.extras.palette.core.icon.ColorAssignment.GRID], the Nth app on screen
     * gets the Nth cell's colour. It can't know which app actually sits in
     * which cell (that needs reading the real launcher, not just a photo), so
     * this approximates "my home screen's colours, in this reading order".
     */
    suspend fun fromGrid(
        ctx: Context, uri: Uri, columns: Int, rows: Int, order: GridOrder = GridOrder.READING
    ): ColorPalette? =
        withContext(Dispatchers.IO) {
            val bmp = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            }.getOrNull() ?: return@withContext null
            fromGridBitmap(bmp, columns, rows, order)
        }

    fun fromGridBitmap(bmp: Bitmap, columns: Int, rows: Int, order: GridOrder = GridOrder.READING): ColorPalette {
        val cols = columns.coerceIn(1, 10)
        val rws = rows.coerceIn(1, 12)
        val cellW = bmp.width / cols
        val cellH = bmp.height / rws
        val cells: List<Pair<Int, Int>> = when (order) {
            GridOrder.READING -> (0 until rws).flatMap { r -> (0 until cols).map { c -> r to c } }
            // Sweeps anti-diagonals (r + c constant) top-left to bottom-right.
            GridOrder.DIAGONAL -> (0..(rws - 1 + cols - 1)).flatMap { d ->
                (maxOf(0, d - (cols - 1))..minOf(rws - 1, d)).map { r -> r to (d - r) }
            }
        }
        val colors = ArrayList<Int>(cols * rws)
        for ((r, c) in cells) {
            val x = (c * cellW).coerceIn(0, bmp.width - 1)
            val y = (r * cellH).coerceIn(0, bmp.height - 1)
            val w = cellW.coerceAtLeast(1).coerceAtMost(bmp.width - x)
            val h = cellH.coerceAtLeast(1).coerceAtMost(bmp.height - y)
            colors += averageColor(bmp, x, y, w, h)
        }
        return ColorPalette(name = "My home screen", colors = colors, source = "grid")
    }

    private fun averageColor(bmp: Bitmap, x: Int, y: Int, w: Int, h: Int): Int {
        val sampleW = min(w, 24); val sampleH = min(h, 24)
        val cell = Bitmap.createScaledBitmap(
            Bitmap.createBitmap(bmp, x, y, w, h), sampleW, sampleH, true
        )
        var r = 0L; var g = 0L; var b = 0L
        val n = sampleW * sampleH
        val pixels = IntArray(n)
        cell.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        for (p in pixels) {
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    suspend fun fromUri(ctx: Context, uri: Uri, count: Int = 5): ColorPalette? =
        withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(uri).use { input ->
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(input, null, opts)
                }
            }.getOrNull()?.let { fromBitmap(it, count) }
        }

    /** Wallpaper palette without any storage permission (API 27+). */
    fun fromWallpaper(ctx: Context, count: Int = 5): ColorPalette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val wm = WallpaperManager.getInstance(ctx)
        val wc = runCatching { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() ?: return null
        val seeds = listOfNotNull(
            wc.primaryColor.toArgb(),
            wc.secondaryColor?.toArgb(),
            wc.tertiaryColor?.toArgb()
        )
        if (seeds.isEmpty()) return null
        return PaletteGenerator.normalize(
            ColorPalette(name = "Wallpaper", colors = expand(seeds, count.coerceIn(2, MAX_COLORS)), source = "wallpaper")
        )
    }

    /** Material You system tonal palette mirrored into our model (API 31+). */
    fun materialYou(ctx: Context, count: Int = 5): ColorPalette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val ids = intArrayOf(
            android.R.color.system_accent1_900, android.R.color.system_accent1_700,
            android.R.color.system_accent1_500, android.R.color.system_accent1_200,
            android.R.color.system_accent1_100, android.R.color.system_accent2_100,
            android.R.color.system_neutral1_100
        )
        val cols = ids.map { ctx.getColor(it) }
        return ColorPalette(name = "Material You", colors = trim(cols, count.coerceIn(2, MAX_COLORS)), source = "material_you")
    }

    fun androidxSwatches(bmp: Bitmap, count: Int = 5): ColorPalette {
        val p = AndroidPalette.from(bmp).maximumColorCount(24).generate()
        val cols = p.swatches.sortedByDescending { it.population }.map { it.rgb }
        return ColorPalette(name = "Swatches", colors = trim(cols, count.coerceIn(2, MAX_COLORS)), source = "image")
    }

    // ---- internals -------------------------------------------------------

    private fun scaleForAnalysis(b: Bitmap, max: Int): Bitmap {
        val s = min(1f, max.toFloat() / maxOf(b.width, b.height))
        if (s >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1),
            (b.height * s).toInt().coerceAtLeast(1), true)
    }

    /** Weighted k-means in Oklab with deterministic k-means++ seeding. */
    private fun kmeans(pixels: IntArray, k: Int, iterations: Int = 12): List<Int> {
        val labs = labOfPixels(pixels)
        if (labs.isEmpty()) return List(k) { Color.GRAY }
        val rnd = Random(1234)
        val centers = ArrayList<Lab>(k)
        centers += labs[rnd.nextInt(labs.size)]
        while (centers.size < k) {
            var bestIdx = 0; var bestD = -1f
            repeat(minOf(labs.size, 600)) {
                val idx = rnd.nextInt(labs.size)
                val d = centers.minOf { labDistance(it, labs[idx]) }
                if (d > bestD) { bestD = d; bestIdx = idx }
            }
            centers += labs[bestIdx]
        }
        val assign = IntArray(labs.size)
        repeat(iterations) {
            for (i in labs.indices) {
                var best = 0; var bd = Float.MAX_VALUE
                for (c in centers.indices) {
                    val d = labDistance(centers[c], labs[i])
                    if (d < bd) { bd = d; best = c }
                }
                assign[i] = best
            }
            val sums = Array(k) { FloatArray(4) }
            for (i in labs.indices) {
                val a = assign[i]; val l = labs[i]
                sums[a][0] += l.l; sums[a][1] += l.a; sums[a][2] += l.b; sums[a][3]++
            }
            for (c in 0 until k) if (sums[c][3] > 0) {
                val n = sums[c][3]
                centers[c] = Lab(sums[c][0] / n, sums[c][1] / n, sums[c][2] / n)
            }
        }
        return centers.sortedBy { it.l }.map { it.toColor() }
    }

    /** Grows a few seed colours into a full ramp using their own hue geometry. */
    private fun expand(seeds: List<Int>, count: Int): List<Int> {
        if (seeds.size >= count) return trim(seeds, count)
        val key = seeds.maxByOrNull { it.toLab().chroma } ?: seeds.first()
        val lab = key.toLab()
        val out = ArrayList<Int>(count)
        for (i in 0 until count) {
            val t = i / (count - 1f)
            val l = 0.06f + 0.90f * t
            val c = lab.chroma * (1f - (2 * t - 1f) * (2 * t - 1f) * 0.5f)
            out += Lab(l, c * kotlin.math.cos(lab.hue), c * kotlin.math.sin(lab.hue)).toColor()
        }
        return out
    }

    private fun trim(list: List<Int>, count: Int): List<Int> {
        if (list.isEmpty()) return List(count) { Color.GRAY }
        if (list.size <= count) return list
        // keep the most perceptually spread subset instead of the first N
        val sorted = list.sortedBy { it.toLab().l }
        return List(count) { sorted[(it * (sorted.size - 1)) / (count - 1)] }
    }
}
