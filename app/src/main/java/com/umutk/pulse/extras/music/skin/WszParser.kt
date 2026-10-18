package com.umutk.pulse.extras.music.skin

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs

// shortcut: 8 MB cap per entry as a zip-bomb guard, raise it if a real skin has a bigger bitmap
private const val MAX_ENTRY = 8 shl 20
private val WANTED = setOf(
    "main.bmp", "cbuttons.bmp", "pledit.txt", "viscolor.txt", "region.txt", "titlebar.bmp", "posbar.bmp", "volume.bmp",
    "balance.bmp", "numbers.bmp", "nums_ex.bmp", "text.bmp", "shufrep.bmp", "monoster.bmp", "playpaus.bmp", "eqmain.bmp", "pledit.bmp",
)

fun loadWsz(context: Context, uri: Uri): WinampSkin? =
    try { context.contentResolver.openInputStream(uri)?.use(::parseWsz) } catch (e: IOException) { null }

/**
 * Stable entry point for other modules (home-screen widgets): reads a .wsz file, null if it is not a skin.
 * Missing sprites come from the painted default skin, so the result can always be drawn. Safe off the main thread.
 */
fun loadWinampSkin(file: java.io.File): WinampSkin? =
    try {
        file.inputStream().use(::parseWsz).takeIf { it.fromFile }?.copy(name = file.nameWithoutExtension)
    } catch (e: IOException) { null } catch (e: SecurityException) { null }

/** A skin file of the "skins" pack with its display name (pack.json en/tr, else the file name). */
data class InstalledSkin(val name: String, val file: java.io.File)

/**
 * Skins unpacked from the "skins" pack. The store unzips to <external files>/../extras/skins (see StoreInstaller);
 * filesDir/extras/skins is read too, so either location works. Loose .wsz files in those folders count as well.
 */
fun installedSkins(context: Context, turkish: Boolean = false): List<InstalledSkin> {
    val roots = listOfNotNull(context.getExternalFilesDir(null)?.parentFile, context.filesDir).map { java.io.File(it, "extras/skins") }
    val out = ArrayList<InstalledSkin>()
    for (root in roots) {
        val names = HashMap<String, String>()
        runCatching {
            val arr = org.json.JSONObject(java.io.File(root, "pack.json").readText()).getJSONArray("skins")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                names[o.getString("file")] = (if (turkish) o.optString("tr") else o.optString("en")).ifEmpty { o.getString("id") }
            }
        }
        root.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() == "wsz" }.forEach { f ->
            val rel = f.relativeTo(root).path.replace('\\', '/')
            if (out.none { it.file.name == f.name }) out += InstalledSkin(names[rel] ?: f.nameWithoutExtension, f)
        }
    }
    return out.sortedBy { it.name.lowercase() }
}

/** Reads a .wsz (zip) stream. Missing or broken entries fall back to defaults; bad content never throws. */
fun parseWsz(input: InputStream): WinampSkin {
    val files = HashMap<String, ByteArray>()
    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/').lowercase()
                if (!e.isDirectory && name in WANTED && name !in files) readCapped(zip)?.let { files[name] = it }
                zip.closeEntry()
            }
        }
    } catch (e: IOException) {
        // corrupt archive: keep whatever was read before the error
    }
    val d = DefaultWinampSkin
    fun bmp(vararg n: String) = n.firstNotNullOfOrNull { k -> files[k]?.let { runCatching { decodeBmp(it) }.getOrNull() } }
    val main = bmp("main.bmp")
    // a file without main.bmp is not a skin; keep the defaults but mark it
    return WinampSkin(
        main = main ?: d.main,
        buttons = bmp("cbuttons.bmp") ?: d.buttons,
        playlist = files["pledit.txt"]?.let(::parsePlEdit) ?: PlEditColors.Default,
        vis = files["viscolor.txt"]?.let(::parseVis) ?: DefaultVis,
        region = files["region.txt"]?.let(::parseRegion),
        titlebar = bmp("titlebar.bmp") ?: d.titlebar,
        posbar = bmp("posbar.bmp") ?: d.posbar,
        volume = bmp("volume.bmp") ?: d.volume,
        balance = bmp("balance.bmp") ?: d.balance,
        numbers = bmp("numbers.bmp", "nums_ex.bmp") ?: d.numbers,
        text = bmp("text.bmp") ?: d.text,
        shufrep = bmp("shufrep.bmp") ?: d.shufrep,
        monoster = bmp("monoster.bmp") ?: d.monoster,
        playpaus = bmp("playpaus.bmp") ?: d.playpaus,
        eq = bmp("eqmain.bmp") ?: d.eq,
        pledit = bmp("pledit.bmp") ?: d.pledit,
        fromFile = main != null,
    )
}

private fun readCapped(zip: ZipInputStream): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (true) {
        val n = zip.read(buf)
        if (n < 0) break
        if (out.size() + n > MAX_ENTRY) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

private fun text(b: ByteArray) = String(b, Charsets.ISO_8859_1)

/** key=value lines, keys lowercased; sections and comments are skipped. */
private fun ini(t: String): Map<String, String> = t.lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith(";") && !it.startsWith("[") && '=' in it }
    .associate { line -> line.substringBefore('=').trim().lowercase() to line.substringAfter('=').trim() }

private fun hexColor(s: String): Color? =
    s.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000L or it) }

private fun parsePlEdit(b: ByteArray): PlEditColors {
    val m = ini(text(b))
    val d = PlEditColors.Default
    fun col(key: String, def: Color) = m[key]?.let(::hexColor) ?: def
    return PlEditColors(col("normal", d.normal), col("current", d.current), col("normalbg", d.normalBg), col("selectedbg", d.selectedBg))
}

/** viscolor.txt: one "r,g,b" per line, first 24 used; missing lines take the default. */
private fun parseVis(b: ByteArray): List<Color> {
    val parsed = text(b).lineSequence().mapNotNull { line ->
        val p = line.trim().split(Regex("[,\\s]+")).take(3).mapNotNull { it.toIntOrNull()?.coerceIn(0, 255) }
        if (p.size == 3) Color(p[0], p[1], p[2]) else null
    }.toList()
    return List(DefaultVis.size) { parsed.getOrNull(it) ?: DefaultVis[it] }
}

/** region.txt [Normal] PointList="x,y x,y ..." as a polygon; anything else means no region. */
private fun parseRegion(b: ByteArray): List<Offset>? {
    var inNormal = false
    var firstCount = 0
    for (raw in text(b).lineSequence()) {
        val line = raw.trim()
        if (line.startsWith("[")) { inNormal = line.equals("[Normal]", ignoreCase = true); continue }
        if (inNormal && line.lowercase().startsWith("numpoints=")) {
            firstCount = line.substringAfter('=').split(',').firstOrNull()?.trim()?.toIntOrNull() ?: 0
        }
        if (inNormal && line.lowercase().startsWith("pointlist=")) {
            // coordinates are x,y pairs separated by commas and spaces; NumPoints gives the length of each polygon
            val nums = line.substringAfter('=').split(Regex("[,\\s]+")).mapNotNull { it.toFloatOrNull() }
            val pts = nums.chunked(2).filter { it.size == 2 }.map { Offset(it[0], it[1]) }
            return pts.take(firstCount.takeIf { it >= 3 } ?: pts.size).takeIf { it.size >= 3 }
        }
    }
    return null
}

private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
private fun le32(b: ByteArray, o: Int) = le16(b, o) or (le16(b, o + 2) shl 16)

/** BGR bytes at o as ARGB; Winamp's magenta is the transparent key. */
private fun bgrAt(b: ByteArray, o: Int): Int {
    val c = ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o].toInt() and 0xFF)
    return if (c == 0xFF00FF) 0 else ((0xFF shl 24) or c)
}

/** BMP of 1, 4, 8 (also RLE), 16, 24 or 32 bits (Android's BitmapFactory cannot read BMP). Returns null on anything else. */
private fun decodeBmp(b: ByteArray): ImageBitmap? {
    if (b.size < 54 || b[0] != 0x42.toByte() || b[1] != 0x4D.toByte()) return null
    val dataOff = le32(b, 10)
    val dib = le32(b, 14)
    if (dib < 40) return null
    val w = le32(b, 18)
    val hRaw = le32(b, 22)
    val bpp = le16(b, 28)
    val comp = le32(b, 30)
    val h = abs(hRaw)
    val topDown = hRaw < 0
    if (w !in 1..4096 || h !in 1..4096 || bpp !in listOf(1, 4, 8, 16, 24, 32)) return null
    val rle = (comp == 1 && bpp == 8) || (comp == 2 && bpp == 4)
    if (comp != 0 && !rle && !(comp == 3 && (bpp == 16 || bpp == 32))) return null
    val stride = ((w * bpp + 31) / 32) * 4
    if (dataOff < 0 || dataOff > b.size || (!rle && dataOff.toLong() + stride.toLong() * h > b.size)) return null
    val palOff = 14 + dib
    val nColors = if (bpp <= 8) le32(b, 46).takeIf { it in 1..(1 shl bpp) } ?: (1 shl bpp) else 0
    if (bpp <= 8 && palOff.toLong() + nColors * 4L > b.size) return null
    fun pal(i: Int) = if (i >= nColors) (0xFF shl 24) else bgrAt(b, palOff + i * 4)

    val px = IntArray(w * h)
    if (rle) {
        // RLE8/RLE4 into palette indices, rows bottom-up as stored
        val idx = IntArray(w * h)
        var x = 0; var row = 0; var p = dataOff
        fun put(v: Int) { if (x < w && row < h) idx[row * w + x] = v; x++ }
        while (p + 1 < b.size && row < h) {
            val n = b[p].toInt() and 0xFF; val v = b[p + 1].toInt() and 0xFF; p += 2
            if (n > 0) { for (k in 0 until n) put(if (bpp == 8) v else if (k % 2 == 0) v shr 4 else v and 15) }
            else when (v) {
                0 -> { x = 0; row++ }
                1 -> break
                2 -> { if (p + 1 >= b.size) break; x += b[p].toInt() and 0xFF; row += b[p + 1].toInt() and 0xFF; p += 2 }
                else -> {
                    for (k in 0 until v) {
                        if (bpp == 8) { if (p + k < b.size) put(b[p + k].toInt() and 0xFF) }
                        else { val q = p + k / 2; if (q < b.size) { val c = b[q].toInt() and 0xFF; put(if (k % 2 == 0) c shr 4 else c and 15) } }
                    }
                    val bytes = if (bpp == 8) v else (v + 1) / 2
                    p += bytes + (bytes and 1)
                }
            }
        }
        for (r in 0 until h) for (c in 0 until w) px[(if (topDown) r else h - 1 - r) * w + c] = pal(idx[r * w + c])
    } else {
        for (row in 0 until h) {
            val src = dataOff + row * stride
            val y = if (topDown) row else h - 1 - row
            for (x in 0 until w) {
                px[y * w + x] = when (bpp) {
                    1 -> pal((b[src + x / 8].toInt() shr (7 - x % 8)) and 1)
                    4 -> pal((b[src + x / 2].toInt() shr (if (x % 2 == 0) 4 else 0)) and 15)
                    8 -> pal(b[src + x].toInt() and 0xFF)
                    16 -> { // 5-5-5
                        val v = le16(b, src + x * 2)
                        val r = ((v shr 10) and 31) * 255 / 31; val g = ((v shr 5) and 31) * 255 / 31; val bl = (v and 31) * 255 / 31
                        val c = (r shl 16) or (g shl 8) or bl
                        if (c == 0xFF00FF) 0 else (0xFF shl 24) or c
                    }
                    24 -> bgrAt(b, src + x * 3)
                    else -> bgrAt(b, src + x * 4)
                }
            }
        }
    }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
