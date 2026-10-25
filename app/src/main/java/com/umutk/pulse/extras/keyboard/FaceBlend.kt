package com.umutk.pulse.extras.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Emoji mix, the real one: a face is a few key points (what its eyes, brows and mouth are, what is around it, the tint of the skin), and two faces are joined by
 * taking the brows and the mouth of the first and the eyes of the second, with everything around both. The result is drawn on one default template face (a round
 * face with a soft light), so nothing is made pair by pair and nothing of another app's artwork is used: the faces are vector shapes drawn here.
 */
object FaceBlend {
    class Face(val eyes: String, val brows: String, val mouth: String, val extras: List<String>, val tint: String)

    // eyes, brows, mouth, extras (joined by +), tint (y yellow, r red, b blue, g green, o orange, p purple)
    private val table: Map<String, String> = buildMap {
        fun f(spec: String, vararg es: String) { es.forEach { put(it, spec) } }
        f("open,none,smile,,y", "🙂", "🙃")
        f("open,none,big,,y", "😃", "😀")
        f("happy,none,big,,y", "😄", "😁", "😆")
        f("squint,none,big,,y", "😆")
        f("happy,none,big,sweat,y", "😅")
        f("happy,none,big,tears,y", "😂", "🤣")
        f("happy,none,smile,blush,y", "😊")
        f("happy,none,smile,halo,y", "😇")
        f("wink,none,smile,,y", "😉")
        f("happy,none,smile,blush,y", "😌")
        f("hearts,none,big,,y", "😍")
        f("open,none,smile,hearts+blush,y", "🥰")
        f("open,none,kiss,hearts,y", "😘", "😗", "😙", "😚")
        f("open,none,tongue,,y", "😋", "😛")
        f("wink,none,tongue,,y", "😜")
        f("squint,none,tongue,,y", "😝")
        f("wide,none,tongue,,y", "🤪")
        f("open,raised,neutral,,y", "🤨")
        f("wide,flat,neutral,,y", "🧐")
        f("wide,none,smile,,y", "🤓")
        f("shades,none,smile,,y", "😎")
        f("stars,none,big,,y", "🤩")
        f("open,none,big,sparkles,y", "🥳")
        f("half,raised,smirk,,y", "😏")
        f("half,flat,neutral,,y", "😒")
        f("open,sad,frown,,y", "😞", "😔", "😟", "😕")
        f("open,sad,frown,,y", "🙁", "☹️")
        f("squint,sad,frown,,y", "😣", "😖")
        f("squint,sad,shout,,y", "😫", "😩")
        f("wide,sad,frown,tear,y", "🥺")
        f("sad,sad,frown,tear,y", "😢")
        f("sad,sad,cry,tears,y", "😭")
        f("open,angry,frown,steam,y", "😤")
        f("open,angry,frown,,r", "😠")
        f("open,angry,shout,steam,r", "😡")
        f("squint,angry,shout,steam,r", "🤬")
        f("wide,none,o,,y", "🤯", "😮", "😲")
        f("open,sad,o,,y", "😦", "😧", "😯")
        f("wide,none,frown,blush,y", "😳")
        f("open,none,grit,sweat,o", "🥵")
        f("squint,none,grit,,b", "🥶")
        f("wide,none,cry,,b", "😱", "😨")
        f("open,sad,frown,sweat,b", "😰", "😥", "😓")
        f("happy,none,smile,hearts,y", "🤗")
        f("open,raised,smirk,,y", "🤔")
        f("happy,none,smile,,y", "🤭")
        f("open,none,zip,,y", "🤫", "🤐")
        f("open,none,neutral,,y", "😶", "😐")
        f("open,flat,neutral,,y", "😑")
        f("open,none,grit,,y", "😬")
        f("rolled,none,neutral,,y", "🙄")
        f("closed,none,open,zzz,y", "😴", "🥱")
        f("closed,none,neutral,zzz,y", "😪")
        f("xx,none,neutral,,y", "😵")
        f("spiral,none,wavy,,y", "😵‍💫", "🥴")
        f("squint,none,wavy,,g", "🤢", "🤮")
        f("squint,sad,wavy,,g", "🤧", "😷", "🤒", "🤕")
        f("open,none,open,,y", "🥱")
        f("dollar,none,big,,y", "🤑")
        f("open,angry,grit,steam,p", "😈", "👿")
        f("closed,none,smile,blush,y", "☺️", "😚")
    }

    fun faceOf(e: String): Face? {
        val sp = table[e] ?: table[e.replace("️", "")] ?: return null
        val p = sp.split(',')
        return Face(p[0], p[1], p[2], p[3].split('+').filter { it.isNotEmpty() }, p[4])
    }

    /** One face out of two: brows and mouth of [a], eyes of [b] (when b has special ones), the surroundings of both. */
    fun blend(a: Face, b: Face): Face {
        val eyes = if (b.eyes != "open" || a.eyes == "open") b.eyes else a.eyes
        val brows = if (a.brows != "none") a.brows else b.brows
        // the mouth of the first, unless it is a plain line and the second smiles or shouts
        val mouth = if (a.mouth == "neutral" && b.mouth != "neutral") b.mouth else a.mouth
        val extras = (a.extras + b.extras).distinct().take(3)
        val tint = if (a.tint != "y") a.tint else b.tint
        return Face(eyes, brows, mouth, extras, tint)
    }

    /** The mixed face of two emojis as a picture, or null when one of them is not a face (the caller then lays them over each other). */
    fun render(ea: String, eb: String, size: Int): Bitmap? {
        val a = faceOf(ea) ?: return null
        val b = faceOf(eb) ?: return null
        return draw(blend(a, b), size)
    }

    private val ink = 0xFF4A2B12.toInt()

    private fun tintColors(t: String): Pair<Int, Int> = when (t) {
        "r" -> 0xFFFF6A4D.toInt() to 0xFFD9301F.toInt()
        "b" -> 0xFF9AD7FF.toInt() to 0xFF4C9AE0.toInt()
        "g" -> 0xFFC8E26A.toInt() to 0xFF7BAA2A.toInt()
        "o" -> 0xFFFFB347.toInt() to 0xFFE8531F.toInt()
        "p" -> 0xFFB58CF0.toInt() to 0xFF7A3FC4.toInt()
        else -> 0xFFFFE066.toInt() to 0xFFF2A81D.toInt()
    }

    fun draw(f: Face, size: Int): Bitmap {
        val s = size.toFloat()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (light, dark) = tintColors(f.tint)
        // the template face: a round face lit from the upper left
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(s * .38f, s * .32f, s * .75f, intArrayOf(light, dark), floatArrayOf(.25f, 1f), Shader.TileMode.CLAMP) }
        c.drawCircle(s / 2f, s / 2f, s * .47f, face)
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .012f; color = Color.argb(70, 120, 60, 0) }
        c.drawCircle(s / 2f, s / 2f, s * .47f, rim)
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .03f; color = ink; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = ink }
        fun x(v: Float) = v * s
        fun p(block: Path.() -> Unit) = Path().apply(block)

        // ---- extras that sit behind the features
        if ("halo" in f.extras) c.drawOval(RectF(x(.28f), x(.01f), x(.72f), x(.1f)), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .03f; color = 0xFFFFD43B.toInt() })
        if ("blush" in f.extras) { val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 90, 110) }; c.drawOval(RectF(x(.15f), x(.52f), x(.31f), x(.62f)), bp); c.drawOval(RectF(x(.69f), x(.52f), x(.85f), x(.62f)), bp) }

        // ---- eyes
        val ly = .42f; val lx = .34f; val rx = .66f
        fun heart(cx: Float, cy: Float, w: Float, col: Int) {
            val hp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
            val path = p { moveTo(cx, cy + w * .85f); cubicTo(cx - w * 1.5f, cy - w * .1f, cx - w * .6f, cy - w * 1.2f, cx, cy - w * .35f); cubicTo(cx + w * .6f, cy - w * 1.2f, cx + w * 1.5f, cy - w * .1f, cx, cy + w * .85f); close() }
            c.drawPath(path, hp)
        }
        fun star(cx: Float, cy: Float, r: Float) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }
            val path = Path()
            for (i in 0 until 10) { val rr = if (i % 2 == 0) r else r * .45f; val an = Math.PI / 5 * i - Math.PI / 2; val px = cx + rr * cos(an).toFloat(); val py = cy + rr * sin(an).toFloat(); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
            path.close(); c.drawPath(path, sp); c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .012f; color = 0xFFB07A00.toInt() })
        }
        fun dotEye(cx: Float, cy: Float) {
            c.drawOval(RectF(x(cx - .045f), x(cy - .065f), x(cx + .045f), x(cy + .065f)), fill)
            c.drawCircle(x(cx - .012f), x(cy - .025f), s * .014f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        fun arcEye(cx: Float, cy: Float, up: Boolean) { c.drawPath(p { moveTo(x(cx - .07f), x(cy + (if (up) .03f else -.02f))); quadTo(x(cx), x(cy + (if (up) -.10f else .09f)), x(cx + .07f), x(cy + (if (up) .03f else -.02f))) }, st) }
        fun eye(cx: Float, left: Boolean, kind: String) {
            when (kind) {
                "open" -> dotEye(cx, ly)
                "wide" -> { c.drawOval(RectF(x(cx - .08f), x(ly - .1f), x(cx + .08f), x(ly + .1f)), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }); c.drawOval(RectF(x(cx - .08f), x(ly - .1f), x(cx + .08f), x(ly + .1f)), Paint(st).apply { strokeWidth = s * .012f }); c.drawCircle(x(cx), x(ly), s * .035f, fill) }
                "happy" -> arcEye(cx, ly, true)
                "closed" -> arcEye(cx, ly, false)
                "sad" -> { dotEye(cx, ly + .01f) }
                "squint" -> c.drawPath(p { if (left) { moveTo(x(cx - .06f), x(ly - .07f)); lineTo(x(cx + .05f), x(ly)); lineTo(x(cx - .06f), x(ly + .07f)) } else { moveTo(x(cx + .06f), x(ly - .07f)); lineTo(x(cx - .05f), x(ly)); lineTo(x(cx + .06f), x(ly + .07f)) } }, st)
                "wink" -> if (left) dotEye(cx, ly) else arcEye(cx, ly, true)
                "hearts" -> heart(x(cx), x(ly), s * .075f, 0xFFE0245E.toInt())
                "stars" -> star(x(cx), x(ly), s * .085f)
                "xx" -> c.drawPath(p { moveTo(x(cx - .05f), x(ly - .05f)); lineTo(x(cx + .05f), x(ly + .05f)); moveTo(x(cx + .05f), x(ly - .05f)); lineTo(x(cx - .05f), x(ly + .05f)) }, st)
                "rolled" -> { c.drawOval(RectF(x(cx - .08f), x(ly - .1f), x(cx + .08f), x(ly + .1f)), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }); c.drawCircle(x(cx), x(ly - .05f), s * .035f, fill) }
                "half" -> { dotEye(cx, ly + .015f); c.drawLine(x(cx - .065f), x(ly - .035f), x(cx + .065f), x(ly - .035f), st) }
                "shades" -> {}
                "dollar" -> { val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B8A3E.toInt(); textSize = s * .2f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }; c.drawText("$", x(cx), x(ly + .065f), tp) }
                "spiral" -> { val sp2 = Paint(st).apply { strokeWidth = s * .018f }; c.drawCircle(x(cx), x(ly), s * .065f, sp2); c.drawCircle(x(cx), x(ly), s * .03f, sp2) }
                else -> dotEye(cx, ly)
            }
        }
        if (f.eyes == "shades") {
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1F.toInt() }
            c.drawRoundRect(RectF(x(.2f), x(.34f), x(.46f), x(.5f)), s * .05f, s * .05f, gp); c.drawRoundRect(RectF(x(.54f), x(.34f), x(.8f), x(.5f)), s * .05f, s * .05f, gp)
            c.drawLine(x(.46f), x(.38f), x(.54f), x(.38f), Paint(st).apply { color = 0xFF1B1B1F.toInt() })
            c.drawLine(x(.24f), x(.37f), x(.3f), x(.36f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255); strokeWidth = s * .014f; strokeCap = Paint.Cap.ROUND })
        } else { eye(lx, true, f.eyes); eye(rx, false, f.eyes) }

        // ---- brows
        val bw = Paint(st).apply { strokeWidth = s * .032f }
        when (f.brows) {
            "angry" -> { c.drawLine(x(.22f), x(.25f), x(.43f), x(.34f), bw); c.drawLine(x(.78f), x(.25f), x(.57f), x(.34f), bw) }
            "sad" -> { c.drawLine(x(.23f), x(.33f), x(.42f), x(.25f), bw); c.drawLine(x(.77f), x(.33f), x(.58f), x(.25f), bw) }
            "raised" -> { c.drawLine(x(.23f), x(.31f), x(.43f), x(.31f), bw); c.drawPath(p { moveTo(x(.57f), x(.25f)); quadTo(x(.68f), x(.17f), x(.78f), x(.24f)) }, bw) }
            "flat" -> { c.drawLine(x(.23f), x(.29f), x(.43f), x(.29f), bw); c.drawLine(x(.57f), x(.29f), x(.77f), x(.29f), bw) }
        }

        // ---- mouth
        val cx = .5f; val my = .68f
        when (f.mouth) {
            "smile" -> c.drawPath(p { moveTo(x(.32f), x(.62f)); quadTo(x(cx), x(.80f), x(.68f), x(.62f)) }, st)
            "grin", "big" -> {
                val m = p { moveTo(x(.27f), x(.58f)); quadTo(x(cx), x(.62f), x(.73f), x(.58f)); quadTo(x(.7f), x(.86f), x(cx), x(.86f)); quadTo(x(.3f), x(.86f), x(.27f), x(.58f)); close() }
                c.drawPath(m, fill)
                c.save(); c.clipPath(m)
                c.drawRect(x(.2f), x(.57f), x(.8f), x(.66f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
                c.drawOval(RectF(x(.36f), x(.76f), x(.64f), x(.92f)), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5484D.toInt() })
                c.restore()
            }
            "neutral" -> c.drawLine(x(.38f), x(my), x(.62f), x(my), st)
            "frown" -> c.drawPath(p { moveTo(x(.34f), x(.74f)); quadTo(x(cx), x(.6f), x(.66f), x(.74f)) }, st)
            "open", "o" -> { val rr = if (f.mouth == "o") .045f else .075f; c.drawOval(RectF(x(cx - rr), x(.64f), x(cx + rr), x(.64f + rr * 2.4f)), fill) }
            "tongue" -> {
                c.drawOval(RectF(x(.4f), x(.66f), x(.6f), x(.86f)), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5484D.toInt() })
                c.drawPath(p { moveTo(x(.3f), x(.62f)); quadTo(x(cx), x(.78f), x(.7f), x(.62f)) }, st)
            }
            "kiss" -> { c.drawOval(RectF(x(.42f), x(.62f), x(.58f), x(.76f)), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5484D.toInt() }); c.drawLine(x(.43f), x(.69f), x(.57f), x(.69f), Paint(st).apply { strokeWidth = s * .012f }) }
            "grit" -> { val r = RectF(x(.3f), x(.62f), x(.7f), x(.76f)); c.drawRoundRect(r, s * .03f, s * .03f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }); c.drawRoundRect(r, s * .03f, s * .03f, Paint(st).apply { strokeWidth = s * .018f }); for (i in 1..3) c.drawLine(x(.3f + .1f * i), x(.62f), x(.3f + .1f * i), x(.76f), Paint(st).apply { strokeWidth = s * .01f }); c.drawLine(x(.3f), x(.69f), x(.7f), x(.69f), Paint(st).apply { strokeWidth = s * .01f }) }
            "zip" -> { c.drawLine(x(.3f), x(my), x(.7f), x(my), st); for (i in 0..5) c.drawLine(x(.33f + i * .065f), x(my - .03f), x(.33f + i * .065f), x(my + .03f), Paint(st).apply { strokeWidth = s * .014f }) }
            "wavy" -> c.drawPath(p { moveTo(x(.3f), x(.7f)); for (i in 0 until 4) quadTo(x(.3f + .1f * i + .05f), x(if (i % 2 == 0) .64f else .76f), x(.3f + .1f * (i + 1)), x(.7f)) }, st)
            "shout" -> { val m = p { moveTo(x(.28f), x(.78f)); quadTo(x(cx), x(.56f), x(.72f), x(.78f)); quadTo(x(cx), x(.7f), x(.28f), x(.78f)); close() }; c.drawPath(m, fill); c.drawPath(p { moveTo(x(.28f), x(.78f)); quadTo(x(cx), x(.56f), x(.72f), x(.78f)) }, st) }
            "cry" -> { val m = p { moveTo(x(.3f), x(.8f)); quadTo(x(cx), x(.58f), x(.7f), x(.8f)); quadTo(x(cx), x(.72f), x(.3f), x(.8f)); close() }; c.drawPath(m, fill) }
            "smirk" -> c.drawPath(p { moveTo(x(.38f), x(.7f)); quadTo(x(.56f), x(.76f), x(.7f), x(.6f)) }, st)
        }

        // ---- extras in front
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4FB3FF.toInt() }
        fun drop(cx: Float, cy: Float, w: Float) { c.drawPath(p { moveTo(cx, cy - w * 1.4f); quadTo(cx + w * 1.1f, cy + w * .2f, cx, cy + w); quadTo(cx - w * 1.1f, cy + w * .2f, cx, cy - w * 1.4f); close() }, blue) }
        if ("tear" in f.extras) drop(x(.3f), x(.58f), s * .035f)
        if ("tears" in f.extras) { drop(x(.2f), x(.5f), s * .04f); drop(x(.8f), x(.5f), s * .04f); drop(x(.17f), x(.62f), s * .03f); drop(x(.83f), x(.62f), s * .03f) }
        if ("sweat" in f.extras) drop(x(.84f), x(.2f), s * .04f)
        if ("hearts" in f.extras) { heart(x(.14f), x(.14f), s * .045f, 0xFFE0245E.toInt()); heart(x(.84f), x(.1f), s * .06f, 0xFFFF5C8A.toInt()); heart(x(.9f), x(.3f), s * .035f, 0xFFE0245E.toInt()) }
        if ("sparkles" in f.extras) { star(x(.12f), x(.16f), s * .07f); star(x(.88f), x(.14f), s * .06f); star(x(.9f), x(.36f), s * .04f) }
        if ("steam" in f.extras) {
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .03f; color = 0xFFE5484D.toInt(); strokeCap = Paint.Cap.ROUND }
            c.drawPath(p { moveTo(x(.74f), x(.1f)); quadTo(x(.8f), x(.15f), x(.86f), x(.1f)); moveTo(x(.74f), x(.2f)); quadTo(x(.8f), x(.15f), x(.74f), x(.1f)); moveTo(x(.86f), x(.2f)); quadTo(x(.8f), x(.15f), x(.86f), x(.1f)); moveTo(x(.74f), x(.2f)); quadTo(x(.8f), x(.25f), x(.86f), x(.2f)) }, rp)
        }
        if ("zzz" in f.extras) { val zp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4C6EF5.toInt(); isFakeBoldText = true; textAlign = Paint.Align.CENTER }; zp.textSize = s * .13f; c.drawText("Z", x(.8f), x(.2f), zp); zp.textSize = s * .09f; c.drawText("z", x(.9f), x(.1f), zp) }
        return bmp
    }
}
