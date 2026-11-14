package com.umutk.pulse.extras.gamehub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * A game's box in a console's case: the game's picture (its Steam cover, else its wide Steam picture cropped to the box) is drawn in the case's
 * window and the case's own frame (with its platform logo) is laid over it. Steam is the default: the Steam cover as it is.
 * The frames are from the steam-case-covers project (GPL-3.0); the windows are its measured positions (600 x 900 px).
 */
internal object CaseCover {
    val STYLES = listOf("steam", "pc", "epic", "gog", "gplay", "android", "ea", "ubisoft", "battlenet", "itch", "ps1", "ps2", "xbox", "nes", "snes", "n64", "gamecube", "wii", "wiiu", "ds", "n3ds", "plain", "ps5", "ps4", "ps3", "switch", "switch2", "xbox360", "xboxone", "xboxseries", "mix")
    /** The mix: every game gets one of these cases at random (all the cases there are), and keeps it; "Shuffle the boxes" deals them again. */
    val MIX = STYLES.filter { it != "plain" && it != "mix" }
    private val WINDOWS = mapOf(
        "steam" to intArrayOf(16, 116, 584, 884), "pc" to intArrayOf(16, 116, 584, 884), "epic" to intArrayOf(16, 116, 584, 884), "gog" to intArrayOf(16, 116, 584, 884), "gplay" to intArrayOf(16, 116, 584, 884),
        "android" to intArrayOf(16, 116, 584, 884), "ea" to intArrayOf(16, 116, 584, 884), "ubisoft" to intArrayOf(16, 116, 584, 884), "battlenet" to intArrayOf(16, 116, 584, 884), "itch" to intArrayOf(16, 116, 584, 884),
        "ps1" to intArrayOf(16, 116, 584, 884), "ps2" to intArrayOf(16, 116, 584, 884), "xbox" to intArrayOf(16, 116, 584, 884), "nes" to intArrayOf(16, 116, 584, 884), "snes" to intArrayOf(16, 116, 584, 884), "n64" to intArrayOf(16, 116, 584, 884),
        "gamecube" to intArrayOf(16, 116, 584, 884), "wii" to intArrayOf(16, 116, 584, 884), "wiiu" to intArrayOf(16, 116, 584, 884), "ds" to intArrayOf(16, 116, 584, 884), "n3ds" to intArrayOf(16, 116, 584, 884), "ps3" to intArrayOf(13, 116, 586, 886), "ps4" to intArrayOf(16, 116, 584, 884), "ps5" to intArrayOf(16, 117, 584, 884),
        "switch" to intArrayOf(16, 16, 584, 884), "switch2" to intArrayOf(16, 165, 584, 884),
        "xbox360" to intArrayOf(16, 116, 584, 884), "xboxone" to intArrayOf(16, 116, 584, 884), "xboxseries" to intArrayOf(16, 16, 584, 884),
    )
    private fun sp(c: Context) = c.getSharedPreferences("gamehub", Context.MODE_PRIVATE)

    /** The chosen box style: "steam" (the default: the blue Steam case), "plain" (the cover as it is, no case) or one of the console cases. */
    fun style(c: Context): String = sp(c).getString("boxStyle", "steam")?.takeIf { it in STYLES } ?: "steam"
    fun setStyle(c: Context, s: String) { sp(c).edit().putString("boxStyle", s).apply() }

    fun file(c: Context, pkg: String, style: String): File = File(c.filesDir, "gamehub/case_" + pkg.replace(Regex("[^A-Za-z0-9_]"), "_") + "_$style.jpg")

    /** The box of [pkg] in the chosen style, or null for the plain cover. In the mix each game has its own style (see [applyAll]). */
    fun fileFor(c: Context, pkg: String): File? {
        val one = styleOf(c, pkg)
        if (one == "plain") return null
        return file(c, pkg, one)
    }

    /** The box style of [pkg] (the one its box is made in): the chosen style, or in the mix the game's own style, picked at random the first time it is asked for. */
    @Synchronized
    fun styleOf(c: Context, pkg: String): String {
        if (style(c) != "mix") return style(c)
        val map = mixMap(c)
        map[pkg]?.let { return it }
        val pick = MIX.random()
        saveMix(c, map + (pkg to pick))
        return pick
    }

    /** In the mix, games with the same box style are put side by side (each style in the order of the library); with one chosen style there is nothing to group. */
    fun groupOf(c: Context, pkg: String): Int = if (style(c) == "mix") MIX.indexOf(styleOf(c, pkg)).coerceAtLeast(0) else 0

    private fun mixMap(c: Context): Map<String, String> =
        (sp(c).getString("boxMix", "") ?: "").split(';').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.filter { it.second in MIX }.toMap()

    private fun saveMix(c: Context, m: Map<String, String>) { sp(c).edit().putString("boxMix", m.entries.joinToString(";") { "${it.key}=${it.value}" }).apply() }

    /** The box picture of [art] in [style], 600 x 900. [art] is cropped to fill the case's window. */
    fun compose(c: Context, art: Bitmap, style: String): Bitmap? {
        val win = WINDOWS[style] ?: return null
        val frame = c.assets.open("cases/$style.png").use { BitmapFactory.decodeStream(it) } ?: return null
        val out = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(0xFF14151A.toInt())
        val x0 = win[0]; val y0 = win[1]; val ww = win[2] - win[0]; val wh = win[3] - win[1]
        // fill the window: the middle of the art, cropped to the window's proportions, drawn into the window
        val cropW = art.width.toFloat().coerceAtMost(art.height * ww / wh.toFloat())
        val cropH = cropW * wh / ww
        val left = (art.width - cropW) / 2f; val top = (art.height - cropH) / 2f
        val src = android.graphics.Rect(left.toInt(), top.toInt(), (left + cropW).toInt(), (top + cropH).toInt())
        cv.drawBitmap(art, src, RectF(x0.toFloat(), y0.toFloat(), (x0 + ww).toFloat(), (y0 + wh).toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        cv.drawBitmap(frame, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        frame.recycle()
        return out
    }

    /** The picture a game's box is made from: the one picked by hand, else Steam's cover, its wide Steam picture, or the store picture of Google Play. */
    fun source(c: Context, pkg: String): File? = coverFile(c, pkg).takeIf { it.isFile } ?: steamCoverFile(c, pkg).takeIf { it.isFile }
        ?: steamHeroFile(c, pkg).takeIf { it.isFile } ?: playCoverFile(c, pkg).takeIf { it.isFile }

    /**
     * A picture for a game that has no cover at all, made from its icon and name: the icon blown up, blurred and darkened behind, the icon as a rounded tile, the name under it.
     * It goes into the console's frame like any cover, so every box of a console style is a real case (a Switch box beside a Switch box), not a plain card beside it.
     */
    private fun iconArt(c: Context, pkg: String, name: String): Bitmap? {
        val icon = GameArt.icon(c, pkg, 512)?.asAndroidBitmap() ?: return null
        val w = 600; val h = 900
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        // blurred by scaling down and up again
        val small = Bitmap.createScaledBitmap(icon, 12, 18, true)
        cv.drawBitmap(small, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        small.recycle()
        cv.drawColor(0x77000000)
        val s = 380f; val x = (w - s) / 2f; val y = 190f
        val clip = android.graphics.Path().apply { addRoundRect(RectF(x, y, x + s, y + s), s * .22f, s * .22f, android.graphics.Path.Direction.CW) }
        cv.save(); cv.clipPath(clip)
        cv.drawBitmap(icon, null, RectF(x, y, x + s, y + s), Paint(Paint.FILTER_BITMAP_FLAG)); cv.restore()
        val tp = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 50f; isFakeBoldText = true; setShadowLayer(8f, 0f, 3f, 0xAA000000.toInt()) }
        val lay = android.text.StaticLayout.Builder.obtain(name, 0, name.length, tp, w - 120).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setMaxLines(3)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        cv.save(); cv.translate(60f, y + s + 40f); lay.draw(cv); cv.restore()
        return out
    }

    /** Makes the box of game [pkg] in [style] from its Steam cover (else its wide Steam picture, else a picture made of its icon and name). False when there is nothing to make it from. */
    fun make(c: Context, pkg: String, style: String): Boolean {
        if (style == "plain") return false
        val cover = source(c, pkg)
        // the built-in games and the games of your own have a picture of their own: it goes into the case too
        val own: Bitmap? = when {
            MegaGames.isMega(pkg) -> runCatching { c.assets.open("megacovers/" + pkg.removePrefix(MegaGames.PREFIX) + ".webp").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            HtmlGames.isHtml(pkg) -> HtmlGames.coverFile(c, HtmlGames.idOf(pkg)).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.absolutePath) }
            else -> null
        }
        val art = own ?: (if (cover != null) BitmapFactory.decodeFile(cover.absolutePath) else iconArt(c, pkg, loadGames(c).firstOrNull { it.pkg == pkg }?.name ?: pkg)) ?: return false
        val box = compose(c, art, style) ?: return false
        val f = file(c, pkg, style); f.parentFile?.mkdirs()
        FileOutputStream(f).use { box.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        box.recycle(); art.recycle()
        return true
    }

    /** Makes the box of every game that has a Steam picture, in the chosen style. */
    fun applyAll(c: Context, pkgs: List<String>, style: String): Int {
        if (style == "mix") {
            // dealt at random (a different order every time), round a shuffled order of the cases: an even share of each, and no two runs alike
            val order = pkgs.distinct().shuffled()
            val styles = MIX.shuffled()
            val map = order.mapIndexed { i, pkg -> pkg to styles[i % styles.size] }.toMap()
            saveMix(c, map)
            return map.count { (pkg, st) -> make(c, pkg, st) }
        }
        var n = 0
        for (pkg in pkgs) if (make(c, pkg, style)) n++
        return n
    }
}
