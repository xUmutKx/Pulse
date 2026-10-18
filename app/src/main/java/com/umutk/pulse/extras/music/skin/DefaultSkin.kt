package com.umutk.pulse.extras.music.skin

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The built-in classic skin: every sprite sheet (main, cbuttons, titlebar, posbar, volume, balance, numbers, text,
 * shufrep, monoster, playpaus, eqmain, pledit) is painted here at skin resolution, with the same cell layout as a
 * real Winamp 2.x .wsz, so one renderer draws both. Nothing is downloaded and no artwork is bundled.
 */
internal val paintedDefaultSkin: WinampSkin by lazy {
    WinampSkin(
        main = paintMain(), buttons = paintButtons(), playlist = PlEditColors.Default, vis = DefaultVis, region = null,
        name = "Classic", titlebar = paintTitlebar(), posbar = paintPosbar(), volume = paintVolume(68, 68),
        balance = paintVolume(47, 38), numbers = paintNumbers(), text = paintText(), shufrep = paintShufrep(),
        monoster = paintMonoster(), playpaus = paintPlaypaus(), eq = paintEq(), pledit = paintPledit(),
    )
}

private fun c(rgb: Int) = rgb or (0xFF shl 24)
private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((a shr s and 255) + ((b shr s and 255) - (a shr s and 255)) * t.coerceIn(0f, 1f)).toInt()
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

private val FACE_TOP = c(0x434760)
private val FACE_BOT = c(0x2A2D3F)
private val HI = c(0x7C809C)
private val LO = c(0x14151F)
private val GREEN = c(0x00E000)
private val BTN_HI = c(0xE9EBF2)
private val BTN_LO = c(0x3B3D4D)
private val BTN_TOP = c(0xC4C7D4)
private val BTN_BOT = c(0x8C90A5)
private val INK = c(0x171822)
private val STRIPE = c(0xD2C7A2)

private class Pix(val w: Int, val h: Int) {
    val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val cv = Canvas(bmp)
    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

    fun rect(x: Int, y: Int, rw: Int, rh: Int, col: Int) {
        if (rw <= 0 || rh <= 0) return
        p.color = col
        cv.drawRect(x.toFloat(), y.toFloat(), (x + rw).toFloat(), (y + rh).toFloat(), p)
    }
    fun px(x: Int, y: Int, col: Int) = rect(x, y, 1, 1, col)
    fun vgrad(x: Int, y: Int, rw: Int, rh: Int, a: Int, b: Int) {
        for (i in 0 until rh) rect(x, y + i, rw, 1, mix(a, b, if (rh <= 1) 0f else i / (rh - 1f)))
    }
    /** Raised (hi top-left) or sunken edge around the rectangle. */
    fun edge(x: Int, y: Int, rw: Int, rh: Int, hi: Int, lo: Int) {
        rect(x, y, rw, 1, hi); rect(x, y, 1, rh, hi)
        rect(x, y + rh - 1, rw, 1, lo); rect(x + rw - 1, y, 1, rh, lo)
    }
    /** A metal key: light face, bevel, pressed = sunken and darker. */
    fun key(x: Int, y: Int, rw: Int, rh: Int, down: Boolean) {
        vgrad(x, y, rw, rh, if (down) BTN_BOT else BTN_TOP, if (down) c(0x6C7087) else BTN_BOT)
        if (down) edge(x, y, rw, rh, BTN_LO, BTN_HI) else edge(x, y, rw, rh, BTN_HI, BTN_LO)
        rect(x, y, 1, 1, INK); rect(x + rw - 1, y, 1, 1, INK); rect(x, y + rh - 1, 1, 1, INK); rect(x + rw - 1, y + rh - 1, 1, 1, INK)
    }
    /** 3x5 pixel font, advance 4. */
    fun text(s: String, x: Int, y: Int, col: Int) {
        var cx = x
        for (ch in s.uppercase()) {
            FONT[ch]?.let { g -> for (r in 0 until 5) for (k in 0 until 3) if (g[r * 3 + k] == '1') px(cx + k, y + r, col) }
            cx += 4
        }
    }
    fun image(): ImageBitmap = bmp.asImageBitmap()
}

private val FONT: Map<Char, String> = """
A010101111101101 B110101110101110 C011100100100011 D110101101101110 E111100110100111 F111100110100100
G011100101101011 H101101111101101 I111010010010111 J001001001101010 K101101110101101 L100100100100111
M101111111101101 N110101101101101 O010101101101010 P110101110100100 Q010101101111011 R110101110101101
S011100010001110 T111010010010010 U101101101101111 V101101101101010 W101101111111101 X101101010101101
Y101101010010010 Z111001010100111 0111101101101111 1010110010010111 2110001010100111 3110001010001110
4101101111001001 5111100110001110 6011100111101111 7111001010010010 8111101111101111 9111101111001110
.000000000000010 :000010000010000 -000000111000000 (001010010010001 )100010010010100 '010010000000000
!010010010000010 _000000000000111 +000010111010000 /001001010100100 \100100010001001 [011010010010011
]110010010010110 %101001010100101 ,000000000010100 =000111000111000 #101111101111101 ?110001010000010
*000101010101000 "101101000000000 ^010101000000000 $011110011011110 &010101010101011
""".trim().split(Regex("\\s+")).associate { it[0] to it.substring(1) }

private fun face(p: Pix, w: Int, h: Int, x: Int = 0, y: Int = 0) {
    p.vgrad(x, y, w, h, FACE_TOP, FACE_BOT)
    p.rect(x, y, w, 1, HI); p.rect(x, y, 1, h, HI)
    p.rect(x, y + h - 1, w, 1, LO); p.rect(x + w - 1, y, 1, h, LO)
    p.rect(x + 1, y + h - 2, w - 2, 1, c(0x20222F)); p.rect(x + w - 2, y + 1, 1, h - 2, c(0x20222F))
}

private fun sunken(p: Pix, x: Int, y: Int, w: Int, h: Int, fill: Int = 0xFF000000.toInt()) {
    p.rect(x, y, w, h, fill)
    p.edge(x - 1, y - 1, w + 2, h + 2, LO, HI)
}

private fun paintMain(): ImageBitmap {
    val p = Pix(WINAMP_W, WINAMP_H)
    face(p, WINAMP_W, WINAMP_H)
    val vis = c(0x18212A)
    sunken(p, 12, 22, 92, 42)
    for (y in 43 until 59 step 2) for (x in 24 until 100 step 2) p.px(x, y, vis)
    sunken(p, 109, 24, 157, 12)
    sunken(p, 109, 41, 19, 11); p.text("KBPS", 130, 43, c(0xC7CADA))
    sunken(p, 154, 41, 14, 11); p.text("KHZ", 171, 43, c(0xC7CADA))
    p.rect(16, 71, 248, 11, c(0x1B1D29)); p.edge(15, 70, 250, 13, LO, HI)
    // little bolt at the lower right, like the classic logo
    for (i in 0 until 9) { p.px(254 + i, 100 - i, c(0xE0A030)); p.px(255 + i, 100 - i, c(0xB87820)); p.px(254 + i, 101 - i, c(0xE0A030)) }
    return p.image()
}

private fun glyph(p: Pix, t: Transport, ox: Int, oy: Int, w: Int) {
    val cx = ox + w / 2
    val cy = oy + 9
    fun bar(x: Int, h: Int = 7, bw: Int = 2) = p.rect(x, cy - h / 2, bw, h, INK)
    fun triR(x: Int) { for (k in 0 until 4) p.rect(x + k, cy - (3 - k), 1, 7 - 2 * k, INK) }
    fun triL(x: Int) { for (k in 0 until 4) p.rect(x + k, cy - k, 1, 1 + 2 * k, INK) }
    when (t) {
        Transport.PREV -> { bar(cx - 5); triL(cx - 3) }
        Transport.PLAY -> triR(cx - 2)
        Transport.PAUSE -> { bar(cx - 3, 8, 3); bar(cx + 1, 8, 3) }
        Transport.STOP -> p.rect(cx - 4, cy - 4, 8, 8, INK)
        Transport.NEXT -> { triR(cx - 4); bar(cx + 1) }
        Transport.EJECT -> {
            for (k in 0 until 4) p.rect(cx - k, cy - 4 + k, 2 * k + 1, 1, INK)
            p.rect(cx - 4, cy + 1, 9, 2, INK)
        }
    }
}

private fun paintButtons(): ImageBitmap {
    val p = Pix(136, 36)
    WinampButton.entries.forEach { b ->
        for (down in listOf(false, true)) {
            val y = if (down) SPRITE_H else 0
            p.key(b.sx, y, b.w, SPRITE_H, down)
            glyph(p, b.t, b.sx + if (down) 1 else 0, y + if (down) 1 else 0, b.w)
        }
    }
    return p.image()
}

/** Striped window bar with a centred label, like the classic title bars. */
private fun bar(p: Pix, x: Int, y: Int, w: Int, h: Int, label: String, active: Boolean) {
    val top = if (active) c(0x5A5F7C) else c(0x3A3D50)
    p.vgrad(x, y, w, h, top, if (active) c(0x353850) else c(0x2A2C3B))
    p.rect(x, y, w, 1, if (active) HI else c(0x4C4F66)); p.rect(x, y + h - 1, w, 1, LO)
    val tw = label.length * 4 + 6
    val tx = x + (w - tw) / 2
    val s = if (active) STRIPE else c(0x6E6A5A)
    for (k in 0 until 3) {
        p.rect(x + 4, y + 3 + k * 3, tx - x - 8, 1, s)
        p.rect(tx + tw + 4, y + 3 + k * 3, x + w - (tx + tw) - 8, 1, s)
    }
    p.text(label, tx + 3, y + (h - 5) / 2 + 1, if (active) c(0xE8E8F2) else c(0x9A9CAC))
}

private fun paintTitlebar(): ImageBitmap {
    val p = Pix(302, 58)
    bar(p, 27, 0, 275, 14, "WINAMP", true)
    bar(p, 27, 15, 275, 14, "WINAMP", false)
    for ((y, act) in listOf(29 to true, 42 to false)) {
        bar(p, 27, y, 275, 14, "", act)
        // shade mode: two recessed displays (title, time) and the mini transport keys, as in the classic strip
        p.rect(27 + 64, y + 3, 56, 8, 0xFF000000.toInt()); p.rect(27 + 123, y + 3, 43, 8, 0xFF000000.toInt())
        val ink = if (act) c(0xE8E8F2) else c(0x8A8C9C)
        p.px(27 + 144, y + 5, GREEN); p.px(27 + 144, y + 8, GREEN)
        val x0 = 27 + 169
        p.rect(x0, y + 4, 1, 6, ink); for (k in 0 until 3) p.rect(x0 + 1 + k, y + 7 - k, 1, 1 + 2 * k, ink)       // prev
        for (k in 0 until 3) p.rect(x0 + 10 + k, y + 4 + k, 1, 6 - 2 * k, ink)                                   // play
        p.rect(x0 + 15, y + 4, 2, 6, ink); p.rect(x0 + 18, y + 4, 2, 6, ink)                                      // pause
        p.rect(x0 + 24, y + 5, 5, 5, ink)                                                                         // stop
        for (k in 0 until 3) p.rect(x0 + 33 + k, y + 4 + k, 1, 6 - 2 * k, ink); p.rect(x0 + 37, y + 4, 1, 6, ink) // next
    }
    for (down in listOf(false, true)) {
        val y = if (down) 9 else 0
        p.key(18, y, 9, 9, down); p.px(21, y + 3, INK); p.px(25, y + 3, INK); p.px(22, y + 4, INK); p.px(24, y + 4, INK)
        p.px(23, y + 5, INK); p.px(22, y + 6, INK); p.px(24, y + 6, INK); p.px(21, y + 7, INK); p.px(25, y + 7, INK)
        val sx = if (down) 9 else 0
        p.key(sx, 18, 9, 9, down); p.rect(sx + 2, 21, 5, 1, INK); p.rect(sx + 2, 23, 5, 3, INK)
    }
    return p.image()
}

private fun paintPosbar(): ImageBitmap {
    val p = Pix(307, 10)
    p.rect(0, 0, 248, 10, c(0x1B1D29)); p.edge(0, 0, 248, 10, LO, HI)
    p.key(248, 0, 29, 10, false); p.key(278, 0, 29, 10, true)
    p.rect(256, 3, 13, 4, c(0x555972)); p.rect(286, 3, 13, 4, c(0x555972))
    return p.image()
}

/** Volume (68 wide) or balance (38 wide, drawn at x=9): 28 frames of 15 rows plus the thumbs at y=422. */
private fun paintVolume(bmpW: Int, grooveW: Int): ImageBitmap {
    val p = Pix(bmpW, 433)
    val gx = bmpW - grooveW
    val bal = grooveW < 50
    for (i in 0 until 28) {
        val y = i * 15
        p.rect(gx, y + 3, grooveW, 7, c(0x1B1D29)); p.edge(gx, y + 2, grooveW, 9, LO, HI)
        val fill = if (bal) (i * (grooveW / 2 - 2) / 27) else (i * (grooveW - 2) / 27)
        val start = if (bal) grooveW / 2 else 1
        for (k in 0 until fill) {
            val t = k / (grooveW - 2f)
            val col = if (t < .5f) mix(c(0x20C010), c(0xD8C020), t * 2) else mix(c(0xD8C020), c(0xE03010), (t - .5f) * 2)
            if (bal) { p.rect(gx + start + k, y + 4, 1, 5, col); p.rect(gx + start - k - 1, y + 4, 1, 5, col) }
            else p.rect(gx + start + k, y + 4, 1, 5, col)
        }
    }
    p.key(15, 422, 14, 11, false); p.key(0, 422, 14, 11, true)
    return p.image()
}

private fun paintNumbers(): ImageBitmap {
    val p = Pix(108, 13)
    // seven segments: top, top-right, bottom-right, bottom, bottom-left, top-left, middle
    val segs = intArrayOf(0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011)
    val on = GREEN
    for (d in 0 until 10) {
        val x = d * 9
        val m = segs[d]
        fun s(bit: Int, sx: Int, sy: Int, w: Int, h: Int) { if (m shr (6 - bit) and 1 == 1) p.rect(x + sx, sy, w, h, on) }
        s(0, 2, 0, 5, 2); s(1, 7, 1, 2, 5); s(2, 7, 7, 2, 5); s(3, 2, 11, 5, 2); s(4, 0, 7, 2, 5); s(5, 0, 1, 2, 5); s(6, 2, 5, 5, 2)
    }
    p.rect(99 + 2, 5, 5, 2, on)
    return p.image()
}

private fun paintText(): ImageBitmap {
    val p = Pix(155, 18)
    val rows = listOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ\"@", "0123456789 .:()-'!_+\\/[]^&%,=$#", "   ?* ")
    rows.forEachIndexed { r, s -> s.forEachIndexed { i, ch -> p.text(ch.toString(), i * 5 + 1, r * 6, GREEN) } }
    // the ellipsis cell
    for (k in 0 until 3) p.px(10 * 5 + 1 + k, 1 * 6 + 4, GREEN)
    return p.image()
}

private fun paintShufrep(): ImageBitmap {
    val p = Pix(92, 85)
    fun btn(x: Int, y: Int, w: Int, h: Int, label: String, on: Boolean, down: Boolean) {
        p.key(x, y, w, h, down)
        p.text(label, x + (w - label.length * 4) / 2 + (if (down) 1 else 0), y + (h - 5) / 2 + (if (down) 1 else 0), if (on) c(0x007A00) else INK)
        if (on) p.rect(x + 2, y + h - 3, w - 4, 1, c(0x00B000))
    }
    for (s in 0..3) {
        val on = s >= 2
        val down = s % 2 == 1
        btn(28, s * 15, 47, 15, "SHUFFLE", on, down)
        btn(0, s * 15, 28, 15, "REPEAT", on, down)
    }
    for (s in 0..3) {
        val on = s >= 2
        val down = s % 2 == 1
        btn(if (down) 46 else 0, 61 + if (on) 12 else 0, 23, 12, "EQ", on, down)
        btn(if (down) 69 else 23, 61 + if (on) 12 else 0, 23, 12, "PL", on, down)
    }
    return p.image()
}

private fun paintMonoster(): ImageBitmap {
    val p = Pix(56, 24)
    val dim = c(0x4A4E5E)
    p.text("STEREO", 2, 3, GREEN); p.text("STEREO", 2, 15, dim)
    p.text("MONO", 31, 3, GREEN); p.text("MONO", 31, 15, dim)
    return p.image()
}

private fun paintPlaypaus(): ImageBitmap {
    val p = Pix(42, 9)
    for (k in 0 until 4) p.rect(2 + k, 1 + k, 1, 7 - 2 * k, GREEN)
    p.rect(9 + 2, 1, 2, 7, GREEN); p.rect(9 + 5, 1, 2, 7, GREEN)
    p.rect(18 + 1, 1, 7, 7, GREEN)
    return p.image()
}

private fun paintEq(): ImageBitmap {
    val p = Pix(275, 315)
    face(p, 275, 116)
    sunken(p, 86, 17, 113, 19, c(0x0A0B10))
    val amber = c(0xE8B020)
    p.text("PREAMP", 14, 104, c(0xC7CADA))
    for ((y, s) in listOf(43 to "+12 DB", 70 to "+0 DB", 96 to "-12 DB")) {
        p.text(s, 44 - s.length * 4 / 2 + 6, y - 2, amber)
        for (x in 78 until 262 step 12) p.rect(x, y, 4, 1, c(0x6B6F88))
    }
    bar(p, 0, 134, 275, 14, "EQUALIZER", true)
    bar(p, 0, 149, 275, 14, "EQUALIZER", false)
    fun led(x: Int, y: Int, label: String, w: Int, on: Boolean, down: Boolean) {
        p.key(x, y, w, 12, down)
        p.rect(x + 3, y + 4, 3, 4, if (on) c(0x00C000) else c(0x2B3A2B))
        p.text(label, x + 8, y + 3, INK)
    }
    for (s in 0..3) {
        val on = s >= 2
        val down = s % 2 == 1
        val x = listOf(10, 128, 69, 187)[s]
        led(x, 119, "ON", 26, on, down)
        led(listOf(36, 154, 95, 213)[s], 119, "AUTO", 32, on, down)
    }
    for (i in 0 until 28) {
        val x = 13 + (i % 14) * 15
        val y = 164 + (i / 14) * 65
        p.rect(x, y, 14, 63, c(0x1E2030)); p.edge(x, y, 14, 63, LO, HI)
        val base = if (i <= 14) mix(c(0x20C010), c(0xE0C020), i / 14f) else mix(c(0xE0C020), c(0xE02010), (i - 14) / 13f)
        for (k in 0 until 57) p.rect(x + 5, y + 3 + k, 4, 1, mix(base, c(0x151620), k / 56f * .5f))
    }
    p.key(0, 164, 11, 11, false); p.key(0, 176, 11, 11, true)
    for (down in listOf(false, true)) {
        val y = if (down) 176 else 164
        p.key(224, y, 44, 12, down); p.text("PRESETS", 224 + 8 + (if (down) 1 else 0), y + 3 + (if (down) 1 else 0), INK)
    }
    return p.image()
}

private fun paintPledit(): ImageBitmap {
    val p = Pix(280, 186)
    for ((y, act) in listOf(0 to true, 21 to false)) {
        bar(p, 0, y, 25, 20, "", act); bar(p, 26, y, 100, 20, "PLAYLIST", act)
        bar(p, 127, y, 25, 20, "", act); bar(p, 153, y, 25, 20, "", act)
    }
    // close cross in the top right corner (25x20 cell, drawn at x=153)
    for (k in 0 until 7) { p.px(153 + 13 + k, 6 + k, INK); p.px(153 + 19 - k, 6 + k, INK) }
    for (k in 0 until 3) { p.px(153 + 13 + k, 6 + k, c(0xE8E8F2)); p.px(153 + 19 - k, 6 + k, c(0xE8E8F2)) }
    // side tiles
    p.vgrad(0, 42, 12, 29, FACE_TOP, FACE_BOT); p.rect(0, 42, 1, 29, HI); p.rect(11, 42, 1, 29, LO)
    p.vgrad(31, 42, 20, 29, FACE_TOP, FACE_BOT); p.rect(50, 42, 1, 29, LO)
    p.rect(31 + 8, 42, 8, 29, c(0x14151F)); p.edge(31 + 7, 42, 10, 29, LO, HI)
    p.key(52, 53, 8, 18, false); p.key(61, 53, 8, 18, true)
    // bottom pieces, 38 high
    face(p, 125, 38, 0, 72); face(p, 150, 38, 126, 72); face(p, 25, 38, 179, 0)
    for ((x, s) in listOf(11 to "ADD", 40 to "REM", 69 to "SEL", 98 to "MISC")) {
        p.key(x, 72 + 9, 25, 18, false); p.text(s, x + (25 - s.length * 4) / 2 + 1, 72 + 9 + 7, INK)
    }
    p.key(126 + 104, 72 + 9, 23, 18, false); p.text("LIST", 126 + 106, 72 + 11, INK); p.text("OPTS", 126 + 106, 72 + 17, INK)
    return p.image()
}
