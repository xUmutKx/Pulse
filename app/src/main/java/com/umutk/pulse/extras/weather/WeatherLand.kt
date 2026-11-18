package com.umutk.pulse.extras.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Drawn scenery behind the weather: a landscape (mountains, sea, city, forest, street, highland) under a sky that follows the hour of the day (sunrise, morning, noon,
 * afternoon, sunset, dusk, night) and the weather (clouds, rain, snow, fog, storm). It paints on a plain android Canvas, so the weather page and the home-screen widget
 * draw the very same picture.
 */
object WeatherLand {
    enum class Land(val key: String, val en: String, val tr: String) {
        MOUNTAIN("mountain", "Mountains", "Dağ"), SEA("sea", "Sea", "Deniz"), CITY("city", "City", "Şehir"),
        FOREST("forest", "Forest", "Orman"), STREET("street", "Street", "Sokak"), PLATEAU("plateau", "Highland", "Yayla")
    }

    /**
     * The scenery to draw. A chosen one is kept; "region" reads the place (high ground is mountains or highland, low ground by the coast is the sea, the rest city or forest,
     * by the height above the sea the forecast brings); anything else is random, a new pick every three hours, the same one at that time on the page and in the widgets.
     */
    fun pick(pref: String, place: String, elev: Double = Double.NaN): Land {
        val all = Land.values()
        all.firstOrNull { it.key == pref }?.let { return it }
        if (pref == "region" && !elev.isNaN()) return when {
            elev > 1300.0 -> Land.MOUNTAIN
            elev > 650.0 -> Land.PLATEAU
            elev < 60.0 -> Land.SEA
            (place.hashCode() and 1) == 0 -> Land.CITY
            else -> Land.FOREST
        }
        val seed = System.currentTimeMillis() / 10_800_000L + place.hashCode()
        return all[((seed xor (seed ushr 7)).toInt() and 0x7fffffff) % all.size]
    }

    /** The hour now at the place (from the forecast's own clock, moved on by the time since it was read) and that day's sunrise and sunset, in hours. */
    class Clock(val hour: Float, val rise: Float, val set: Float)

    fun clockOf(f: Forecast?): Clock {
        fun hm(s: String): Float? = runCatching { val t = s.substringAfter('T'); t.substring(0, 2).toInt() + t.substring(3, 5).toInt() / 60f }.getOrNull()
        val cal = java.util.Calendar.getInstance()
        var now = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
        if (f != null) hm(f.time)?.let { base ->
            val el = ((System.currentTimeMillis() - f.at) / 3_600_000f).coerceIn(0f, 6f)
            now = (base + el) % 24f
        }
        val d = f?.days?.firstOrNull { it.date == f.time.take(10) } ?: f?.days?.firstOrNull()
        val rise = d?.sunrise?.let { hm(it) } ?: 6.5f
        val set = d?.sunset?.let { hm(it) } ?: 19.5f
        return if (set - rise < 2f) Clock(now, 6.5f, 19.5f) else Clock(now, rise, set)
    }

    private class Key(val h: Float, val top: Int, val bot: Int, val night: Float)

    private fun keys(rise: Float, set: Float): List<Key> {
        val mid = (rise + set) / 2f
        return listOf(
            Key(rise - 1.6f, 0xFF0B1234.toInt(), 0xFF1B2757.toInt(), 1f),
            Key(rise - .7f, 0xFF232C6E.toInt(), 0xFF7A5A9E.toInt(), .7f),
            Key(rise, 0xFF3E5AA8.toInt(), 0xFFFFB07A.toInt(), .25f),
            Key(rise + 1.5f, 0xFF3F8FE6.toInt(), 0xFFC4E4FF.toInt(), 0f),
            Key(mid, 0xFF2B7FDB.toInt(), 0xFF8FCBFF.toInt(), 0f),
            Key(set - 2f, 0xFF3A86D6.toInt(), 0xFFFFE5B0.toInt(), 0f),
            Key(set - .2f, 0xFF4B4A9E.toInt(), 0xFFFF8F5E.toInt(), .2f),
            Key(set + .7f, 0xFF2A2A70.toInt(), 0xFFB65A92.toInt(), .55f),
            Key(set + 1.8f, 0xFF0B1234.toInt(), 0xFF1B2757.toInt(), 1f),
        )
    }

    private fun mix(a: Int, b: Int, t: Float) = ColorUtils.blendARGB(a, b, t.coerceIn(0f, 1f))
    private fun frac(x: Float) = x - floor(x)
    private fun gray(c: Int, k: Float): Int {
        val l = ((Color.red(c) * .3f + Color.green(c) * .59f + Color.blue(c) * .11f) * k).toInt().coerceIn(0, 255)
        return Color.rgb(l, (l + 4).coerceAtMost(255), (l + 10).coerceAtMost(255))
    }
    private fun grayOf(s: Sky) = when (s) { Sky.CLEAR -> 0f; Sky.PARTLY -> .2f; Sky.CLOUD -> .55f; Sky.FOG -> .65f; Sky.DRIZZLE -> .6f; Sky.RAIN -> .72f; Sky.SNOW -> .42f; Sky.STORM -> .88f }
    private fun darkOf(s: Sky) = when (s) { Sky.RAIN -> .78f; Sky.DRIZZLE -> .85f; Sky.STORM -> .6f; Sky.CLOUD -> .92f; else -> 1f }

    /**
     * Paints the scene over [w] x [h]. [t] (0..1) is the quick loop (twinkling, rain, snow); [drift] (0..1) the slow one of the clouds.
     */
    fun draw(c: Canvas, w: Float, h: Float, land: Land, sky: Sky, ck: Clock, t: Float = .3f, drift: Float = .2f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val ks = keys(ck.rise, ck.set)
        var hh = ck.hour
        if (hh < ks[0].h) hh += 24f
        val i = ks.indexOfLast { it.h <= hh }.coerceAtLeast(0)
        val a = ks[i]
        val b = ks.getOrNull(i + 1) ?: Key(ks[0].h + 24f, ks[0].top, ks[0].bot, ks[0].night)
        val f = ((hh - a.h) / (b.h - a.h)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        var top = mix(a.top, b.top, f); var bot = mix(a.bot, b.bot, f)
        val night = a.night + (b.night - a.night) * f
        val g = grayOf(sky); val dk = darkOf(sky)
        top = mix(top, gray(top, dk), g); bot = mix(bot, gray(bot, dk), g)
        // each scenery has its own mood: the sky is pulled toward a colour of its own (violet mountains, teal sea, red-orange city, green forest, bright highland)
        run {
            val gc: Triple<Int, Int, Float>? = when (land) {
                Land.MOUNTAIN -> Triple(0xFF5A3FC0.toInt(), 0xFFFF8FA8.toInt(), .30f)
                Land.SEA -> Triple(0xFF0E7FA8.toInt(), 0xFFFFC58A.toInt(), .32f)
                Land.CITY -> Triple(0xFFC2412D.toInt(), 0xFFFFB347.toInt(), .38f)
                Land.FOREST -> Triple(0xFF1E8A6E.toInt(), 0xFFD6F0A0.toInt(), .30f)
                Land.STREET -> null
                Land.PLATEAU -> Triple(0xFF6FB6FF.toInt(), 0xFFFFF1C2.toInt(), .35f)
            }
            if (gc != null) {
                val dayE = sin(((ck.hour - ck.rise) / (ck.set - ck.rise)).coerceIn(0f, 1f) * PI.toFloat())
                val am = gc.third * (1f - night * .55f) * (1f - g * .6f) * (1f - .45f * dayE * (1f - night))
                top = mix(top, gc.first, am); bot = mix(bot, gc.second, am)
            }
        }
        val snow = sky == Sky.SNOW
        val snowAmt = if (snow) .68f else 0f
        val horizon = h * when (land) { Land.SEA -> .60f; Land.CITY -> .74f; Land.STREET -> .62f; Land.MOUNTAIN -> .72f; Land.FOREST -> .74f; Land.PLATEAU -> .70f }
        val u = (h / 300f).coerceAtLeast(.6f)
        val vis = (1f - g * 1.15f).coerceIn(0f, 1f)

        // sky
        p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(top, mix(top, bot, .55f), bot), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null

        // stars
        if (night > .25f && vis > 0f) {
            p.color = Color.WHITE
            for (k in 0 until 50) {
                val tw = .5f + .5f * sin((t * 2f + k * .37f) * 2f * PI.toFloat())
                p.alpha = ((night - .2f) * 255f * vis * (.35f + .65f * tw)).toInt().coerceIn(0, 255)
                c.drawCircle(frac(sin(k * 12.9898f + 1.3f) * 43758.547f) * w, frac(sin(k * 78.233f + 5.1f) * 12345.6f) * horizon * .8f, (.8f + (k % 3) * .5f) * u, p)
            }
            p.alpha = 255
        }

        // sun by day, moon by night
        val dayLen = ck.set - ck.rise
        val sp = (ck.hour - ck.rise) / dayLen
        var sunX = -1f
        var sunReflect = false
        if (sp > -.08f && sp < 1.08f) {
            val q = sp.coerceIn(0f, 1f)
            val e = sin(q * PI.toFloat())
            val sx = w * (.12f + .76f * q); val sy = horizon - e * horizon * .72f
            val col = mix(0xFFFF8A4A.toInt(), 0xFFFFF3C4.toInt(), e * 1.2f)
            val r = h * .07f * (1.25f - .25f * e)
            p.shader = RadialGradient(sx, sy, h * .55f, intArrayOf(ColorUtils.setAlphaComponent(col, (120 * vis).toInt()), 0x00000000), null, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, h * .55f, p); p.shader = null
            p.color = col; p.alpha = (255 * vis).toInt(); c.drawCircle(sx, sy, r, p); p.alpha = 255
            sunX = sx; sunReflect = vis > .2f
        } else {
            val q = (((ck.hour - ck.set + 24f) % 24f) / (24f - dayLen)).coerceIn(0f, 1f)
            val mx = w * (.12f + .76f * q); val my = horizon - sin(q * PI.toFloat()) * horizon * .66f
            val r = h * .06f
            p.shader = RadialGradient(mx, my, h * .4f, intArrayOf(ColorUtils.setAlphaComponent(0xFFDDE6FF.toInt(), (90 * vis).toInt()), 0x00000000), null, Shader.TileMode.CLAMP)
            c.drawCircle(mx, my, h * .4f, p); p.shader = null
            p.color = 0xFFF4F1DE.toInt(); p.alpha = (255 * vis).toInt(); c.drawCircle(mx, my, r, p)
            p.color = 0xFFC9C5AE.toInt(); p.alpha = (110 * vis).toInt()
            c.drawCircle(mx - r * .3f, my - r * .2f, r * .25f, p); c.drawCircle(mx + r * .35f, my + r * .25f, r * .2f, p); c.drawCircle(mx + r * .1f, my - r * .5f, r * .14f, p)
            p.alpha = 255
            sunX = mx; sunReflect = vis > .2f
        }

        // clouds: each one is a single shape (its puffs are joined), so the overlaps do not show
        val nCloud = when (sky) { Sky.CLEAR -> 2; Sky.PARTLY -> 4; Sky.CLOUD -> 7; Sky.FOG -> 3; Sky.DRIZZLE -> 7; Sky.RAIN -> 8; Sky.SNOW -> 6; Sky.STORM -> 8 }
        var cloudCol = mix(Color.WHITE, bot, .25f)
        cloudCol = mix(cloudCol, 0xFF3A4260.toInt(), g * .55f)
        cloudCol = mix(cloudCol, 0xFF1A2040.toInt(), night * .7f)
        val cloudA = if (sky == Sky.CLEAR) .45f else .85f
        for (k in 0 until nCloud) {
            val depth = (k % 3) / 2f
            val cw = h * (.55f + depth * .55f) * (if (sky == Sky.CLEAR) .6f else 1f)
            val x = (((drift * (1 + k % 3)) + k * .29f) % 1f) * (w + cw * 1.2f) - cw * .6f
            val y = horizon * (.12f + ((k * .37f) % 1f) * .55f)
            val ch = cw * .42f
            val cp = Path()
            cp.addCircle(x - cw * .28f, y, ch * .55f, Path.Direction.CW); cp.addCircle(x - cw * .05f, y - ch * .3f, ch * .72f, Path.Direction.CW); cp.addCircle(x + cw * .22f, y - ch * .05f, ch * .6f, Path.Direction.CW)
            cp.addRoundRect(RectF(x - cw * .42f, y - ch * .08f, x + cw * .42f, y + ch * .5f), ch * .3f, ch * .3f, Path.Direction.CW)
            p.color = cloudCol; p.alpha = (cloudA * (.6f + depth * .4f) * 255).toInt()
            c.drawPath(cp, p)
            p.color = mix(cloudCol, Color.WHITE, .5f); p.alpha = (cloudA * 128).toInt()
            c.drawCircle(x - cw * .05f, y - ch * .4f, ch * .5f, p)
        }
        p.alpha = 255

        // colour of a layer of the land: far ones melt into the sky, near ones are dark; night darkens them; snow whitens them
        fun tint(depth: Float, base: Int, snowK: Float = 0f): Int {
            var col = mix(bot, base, .30f + .60f * depth)
            col = mix(col, 0xFF05070F.toInt(), night * .45f * (.4f + depth * .6f))
            return mix(col, 0xFFF1F6FF.toInt(), snowAmt * snowK)
        }
        fun noise(x: Float, s: Float) = sin(x + s) * .5f + sin(x * 2.3f + s * 1.7f) * .3f + sin(x * 4.7f + s * .6f) * .2f
        fun ridge(base: Float, amp: Float, freq: Float, seed: Float, color: Int) {
            val path = Path(); path.moveTo(0f, h)
            val step = w / 64f
            var x = 0f
            while (x <= w + step) { path.lineTo(x, base - amp * (noise(x / w * freq * 6.2832f, seed) + 1f) / 2f); x += step }
            path.lineTo(w + step, h); path.close()
            p.shader = LinearGradient(0f, base - amp, 0f, base + h * .12f, mix(color, Color.WHITE, .10f), mix(color, bot, .30f), Shader.TileMode.CLAMP)
            c.drawPath(path, p); p.shader = null
        }
        // pointed peaks (triangle waves), with a snow line when [cap]
        fun peaks(base: Float, amp: Float, freq: Float, seed: Float, color: Int, cap: Boolean) {
            fun tri(z: Float): Float { val q = (z / (2f * PI.toFloat())) % 1f; val qq = if (q < 0f) q + 1f else q; return 1f - abs(qq * 2f - 1f) }
            val path = Path(); path.moveTo(0f, h)
            val step = w / 120f
            var x = 0f
            while (x <= w + step) {
                val q = x / w * freq * 6.2832f
                val n = tri(q + seed) * .62f + tri(q * 2.3f + seed * 1.7f) * .28f + tri(q * 5.3f + seed * .4f) * .10f
                path.lineTo(x, base - amp * Math.pow(n.toDouble(), 1.25).toFloat()); x += step
            }
            path.lineTo(w + step, h); path.close()
            if (cap) {
                val snowC = mix(mix(Color.WHITE, bot, .25f), color, .1f)
                p.shader = LinearGradient(0f, base - amp, 0f, base - amp * .55f, intArrayOf(snowC, snowC, color, color), floatArrayOf(0f, .45f, .62f, 1f), Shader.TileMode.CLAMP)
            } else p.color = color
            c.drawPath(path, p); p.shader = null
        }
        fun pine(x: Float, y: Float, s: Float, color: Int) {
            p.color = color
            for (j in 0 until 4) {
                val ty = y - s * (.12f + j * .22f); val half = s * (.30f - j * .055f)
                val path = Path(); path.moveTo(x + sin(t * 2f * PI.toFloat() + x * .05f) * s * .015f * (j + 1), ty - s * .30f); path.lineTo(x - half, ty + s * .06f); path.lineTo(x + half, ty + s * .06f); path.close()
                c.drawPath(path, p)
            }
            c.drawRect(x - s * .03f, y - s * .06f, x + s * .03f, y + s * .08f, p)
            if (snow) { p.color = mix(color, Color.WHITE, .7f); p.alpha = 200; c.drawCircle(x, y - s * .80f, s * .06f, p); p.alpha = 255 }
        }
        fun rnd(k: Int, s: Float) = frac(sin(k * 12.9898f + s) * 43758.547f)
        fun skyline(baseY: Float, hMin: Float, hMax: Float, wMin: Float, wMax: Float, seed: Float, color: Int, lit: Boolean) {
            var x = -wMin; var k = 0
            while (x < w) {
                val bw = wMin + rnd(k, seed) * (wMax - wMin); val bh = hMin + rnd(k + 50, seed) * (hMax - hMin)
                p.color = color; c.drawRect(x, baseY - bh, x + bw, h, p)
                if (snow) { p.color = Color.WHITE; p.alpha = 220; c.drawRect(x, baseY - bh - u * 1.5f, x + bw, baseY - bh + u, p); p.alpha = 255 }
                if (lit && night > .15f) {
                    val ww = u * 3.4f; val gap = u * 6.5f
                    var wy = baseY - bh + gap
                    var row = 0
                    while (wy < baseY - gap) {
                        var wx = x + gap * .6f; var col = 0
                        while (wx + ww < x + bw - gap * .4f) {
                            if (rnd(k * 31 + row * 7 + col, seed + 3f) > .55f) {
                                p.color = 0xFFFFD27A.toInt(); p.alpha = ((night - .2f).coerceIn(0f, 1f) * 235).toInt(); c.drawRect(wx, wy, wx + ww, wy + ww * 1.3f, p); p.alpha = 255
                            }
                            wx += gap; col++
                        }
                        wy += gap * 1.25f; row++
                    }
                }
                x += bw + u * (.5f + rnd(k + 9, seed) * 2f); k++
            }
        }
        fun glow(gx: Float, gy: Float, r: Float, col: Int, a: Int) {
            p.shader = RadialGradient(gx, gy, r, intArrayOf(ColorUtils.setAlphaComponent(col, a), ColorUtils.setAlphaComponent(col, 0)), null, Shader.TileMode.CLAMP)
            c.drawCircle(gx, gy, r, p); p.shader = null
        }

        when (land) {
            Land.MOUNTAIN -> {
                peaks(horizon, h * .40f, .8f, 1.1f, tint(.25f, 0xFF4A5878.toInt(), .3f), true)
                peaks(horizon + h * .07f, h * .30f, 1.15f, 4f, tint(.5f, 0xFF39455E.toInt(), .3f), true)
                ridge(horizon + h * .14f, h * .13f, 1.6f, 7.3f, tint(.8f, 0xFF1F3A34.toInt(), 1f))
                p.color = tint(1f, 0xFF10241C.toInt(), 1f); c.drawRect(0f, h * .92f, w, h, p)
                for (k in 0 until 14) pine(w * (k / 13f) + noise(k * 1.7f, 2f) * w * .02f, h * (.95f + rnd(k, 5f) * .03f), h * (.16f + rnd(k + 3, 5f) * .08f), tint(1f, 0xFF10241C.toInt(), .7f))
            }
            Land.SEA -> {
                ridge(horizon + 2f, h * .08f, 1.2f, 2.2f, tint(.35f, 0xFF3C5A6A.toInt(), .5f))
                val wTop = mix(bot, 0xFF0B4A72.toInt(), .35f); val wBot = mix(0xFF0A3558.toInt(), 0xFF031829.toInt(), night * .6f + .2f)
                p.shader = LinearGradient(0f, horizon, 0f, h, wTop, wBot, Shader.TileMode.CLAMP); c.drawRect(0f, horizon, w, h, p); p.shader = null
                if (sunReflect) {
                    p.color = mix(0xFFFFE9A8.toInt(), 0xFFEAF0FF.toInt(), night)
                    p.strokeWidth = h * .006f; p.strokeCap = Paint.Cap.ROUND
                    for (k in 0 until 14) {
                        val y = horizon + (k + 1) * (h - horizon) / 15.5f; val half = (5f + k * 4f) * u * (.5f + .5f * sin(t * 6.2832f * 2f + k * 1.3f))
                        p.alpha = (170 * (1f - k / 15f) * vis).toInt().coerceIn(0, 255)
                        c.drawLine(sunX - half, y, sunX + half, y, p)
                    }
                    p.alpha = 255
                }
                p.color = Color.WHITE; p.strokeWidth = u * 1.2f
                for (k in 0 until 7) { val y = horizon + (k + .7f) * (h - horizon) / 8f; p.alpha = 40; c.drawLine(w * (.1f + frac(k * .37f) * .5f), y, w * (.3f + frac(k * .37f) * .6f), y, p) }
                p.alpha = 255
                // a little boat
                val bx = w * .30f; val by = horizon + (h - horizon) * .16f + sin(t * 4f * PI.toFloat()) * u * 1.4f
                p.color = tint(.9f, 0xFF10202C.toInt()); val hull = Path(); hull.moveTo(bx - u * 14f, by); hull.lineTo(bx + u * 14f, by); hull.lineTo(bx + u * 9f, by + u * 6f); hull.lineTo(bx - u * 9f, by + u * 6f); hull.close(); c.drawPath(hull, p)
                p.color = mix(Color.WHITE, bot, .35f); val sail = Path(); sail.moveTo(bx, by - u * 22f); sail.lineTo(bx + u * 12f, by - u * 2f); sail.lineTo(bx, by - u * 2f); sail.close(); c.drawPath(sail, p)
                p.color = mix(Color.WHITE, bot, .55f); val sail2 = Path(); sail2.moveTo(bx - u * 2f, by - u * 16f); sail2.lineTo(bx - u * 11f, by - u * 2f); sail2.lineTo(bx - u * 2f, by - u * 2f); sail2.close(); c.drawPath(sail2, p)
                ridge(h * .99f, h * .13f, .9f, 5.5f, tint(1f, 0xFF0C1620.toInt(), .8f))
            }
            Land.CITY -> {
                skyline(horizon, h * .10f, h * .26f, w * .04f, w * .09f, 1f, tint(.3f, 0xFF4C566E.toInt(), .1f), false)
                skyline(horizon + h * .05f, h * .12f, h * .34f, w * .05f, w * .11f, 7f, tint(.6f, 0xFF2D364C.toInt(), .15f), true)
                // a mosque with a dome and two slim minarets
                run {
                    val mx = w * .66f; val by = horizon + h * .05f; p.color = tint(.55f, 0xFF3A4560.toInt(), .1f)
                    c.drawArc(RectF(mx - u * 28f, by - u * 50f, mx + u * 28f, by + u * 6f), 180f, 180f, true, p)
                    c.drawRect(mx - u * 34f, by - u * 22f, mx + u * 34f, by + u * 38f, p)
                    c.drawArc(RectF(mx - u * 30f, by - u * 26f, mx - u * 10f, by - u * 6f), 180f, 180f, true, p); c.drawArc(RectF(mx + u * 10f, by - u * 26f, mx + u * 30f, by - u * 6f), 180f, 180f, true, p)
                    for (sd in intArrayOf(-1, 1)) {
                        val nx = mx + sd * u * 46f
                        c.drawRect(nx - u * 3f, by - u * 88f, nx + u * 3f, by + u * 42f, p); c.drawRect(nx - u * 5f, by - u * 60f, nx + u * 5f, by - u * 56f, p)
                        val tip = Path(); tip.moveTo(nx, by - u * 108f); tip.lineTo(nx - u * 4f, by - u * 88f); tip.lineTo(nx + u * 4f, by - u * 88f); tip.close(); c.drawPath(tip, p)
                    }
                }
                // a tower with an aerial
                run {
                    val tx = w * .22f; p.color = tint(.65f, 0xFF2D364C.toInt()); c.drawRect(tx - u * 5f, horizon - h * .36f, tx + u * 5f, h, p)
                    p.strokeWidth = u * 1.4f; c.drawLine(tx, horizon - h * .36f, tx, horizon - h * .46f, p)
                    if (night > .15f && sin(t * 6f * PI.toFloat()) > -.2f) { p.color = 0xFFFF5252.toInt(); c.drawCircle(tx, horizon - h * .46f, u * 2f, p) }
                }
                skyline(horizon + h * .11f, h * .10f, h * .22f, w * .05f, w * .13f, 13f, tint(.95f, 0xFF171C2A.toInt(), .15f), true)
                p.color = tint(1f, 0xFF0C1018.toInt(), .8f); c.drawRect(0f, h * .93f, w, h, p)
            }
            Land.FOREST -> {
                ridge(horizon, h * .10f, 1.2f, 3.3f, tint(.2f, 0xFF2F6B4F.toInt(), .5f))
                for (k in 0 until 16) pine(w * (k / 15f) + noise(k * 2.1f, 1f) * w * .03f, horizon + h * .03f, h * (.15f + rnd(k, 9f) * .05f), tint(.22f, 0xFF2F6B4F.toInt(), .8f))
                p.shader = LinearGradient(0f, horizon - h * .04f, 0f, horizon + h * .16f, intArrayOf(0x00FFFFFF, ColorUtils.setAlphaComponent(mix(Color.WHITE, bot, .4f), 115), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, horizon - h * .04f, w, horizon + h * .16f, p); p.shader = null
                for (k in 0 until 12) pine(w * (k / 11f) + noise(k * 1.3f, 4f) * w * .04f + w * .02f, horizon + h * .14f, h * (.21f + rnd(k, 2f) * .06f), tint(.5f, 0xFF245A40.toInt(), .8f))
                p.color = tint(.8f, 0xFF17402E.toInt(), 1f); c.drawRect(0f, h * .88f, w, h, p)
                for (k in 0 until 8) pine(w * (k / 7f) + w * .05f * noise(k * 1.1f, 6f), h * 1.0f, h * (.34f + rnd(k, 4f) * .08f), tint(1f, 0xFF0C2418.toInt(), .8f))
            }
            Land.STREET -> {
                // a row of townhouses seen from the other side of the road
                val gy = h * .80f
                val cols = intArrayOf(0xFFE7B8A0.toInt(), 0xFFA9C4D8.toInt(), 0xFFE9D7A0.toInt(), 0xFFB7D3B0.toInt(), 0xFFD8A9B8.toInt(), 0xFFC9B7E0.toInt())
                var x = -w * .02f; var k = 0
                while (x < w) {
                    val bw = w * (.24f + rnd(k, 3f) * .10f); val bh = h * (.34f + rnd(k + 9, 3f) * .12f); val flat = rnd(k + 4, 3f) > .5f
                    val body = tint(.78f, cols[k % cols.size], .2f)
                    p.color = body; c.drawRect(x, gy - bh, x + bw, gy, p)
                    p.color = mix(body, Color.BLACK, .28f)
                    val roof = Path()
                    if (flat) c.drawRect(x - u * 2f, gy - bh - u * 5f, x + bw + u * 2f, gy - bh + u, p)
                    else { roof.moveTo(x - u * 3f, gy - bh); roof.lineTo(x + bw / 2f, gy - bh - bw * .30f); roof.lineTo(x + bw + u * 3f, gy - bh); roof.close(); c.drawPath(roof, p) }
                    if (snow) {
                        p.color = Color.WHITE; p.alpha = 230
                        if (flat) c.drawRect(x - u * 2f, gy - bh - u * 7f, x + bw + u * 2f, gy - bh - u * 4f, p)
                        else { val sr = Path(); sr.moveTo(x - u * 3f, gy - bh); sr.lineTo(x + bw / 2f, gy - bh - bw * .30f); sr.lineTo(x + bw + u * 3f, gy - bh); sr.lineTo(x + bw * .9f, gy - bh); sr.lineTo(x + bw / 2f, gy - bh - bw * .24f); sr.lineTo(x + bw * .1f, gy - bh); sr.close(); c.drawPath(sr, p) }
                        p.alpha = 255
                    }
                    val ww = bw * .17f; val wh = bh * .18f
                    for (r in 0 until 2) for (q in 0 until 2) {
                        val wx = x + bw * (.2f + q * .42f); val wy = gy - bh + bh * (.12f + r * .30f)
                        val on = night > .15f && rnd(k * 9 + r * 3 + q, 5f) > .35f
                        p.color = if (on) 0xFFFFD27A.toInt() else mix(0xFF20304A.toInt(), bot, .4f); c.drawRect(wx, wy, wx + ww, wy + wh, p)
                        p.color = mix(body, Color.BLACK, .3f); c.drawRect(wx - u, wy + wh, wx + ww + u, wy + wh + u * 1.5f, p)
                    }
                    p.color = mix(body, Color.BLACK, .45f); c.drawRect(x + bw * .42f, gy - bh * .30f, x + bw * .58f, gy, p)
                    x += bw + u * 1.5f; k++
                }
                // pavement, road and its dashes
                p.color = tint(.85f, 0xFF6B6F7A.toInt(), .6f); c.drawRect(0f, gy, w, gy + h * .06f, p)
                p.color = mix(tint(.9f, 0xFF3A3E4A.toInt(), .6f), Color.BLACK, .1f); c.drawRect(0f, gy + h * .06f, w, h, p)
                p.color = Color.WHITE; p.alpha = 90; c.drawRect(0f, gy + h * .06f, w, gy + h * .06f + u * 1.5f, p)
                p.color = 0xFFF2E9C8.toInt(); p.alpha = 205
                for (q in 0 until 5) c.drawRect(w * (.04f + q * .21f), h * .93f, w * (.15f + q * .21f), h * .93f + u * 3f, p)
                p.alpha = 255
                // a street lamp
                run {
                    val px = w * .12f; val py = gy + h * .04f; val ph = h * .40f; p.color = tint(.95f, 0xFF161922.toInt())
                    p.strokeWidth = u * 2.6f; p.strokeCap = Paint.Cap.ROUND; c.drawLine(px, py, px, py - ph, p)
                    p.strokeWidth = u * 2.2f; c.drawLine(px, py - ph, px + w * .05f, py - ph, p)
                    val lx = px + w * .05f; val ly = py - ph + u * 3f
                    if (night > .2f || sky == Sky.RAIN || sky == Sky.STORM || sky == Sky.CLOUD) glow(lx, ly, h * .22f, 0xFFFFD98A.toInt(), 190)
                    p.color = 0xFFFFF1C2.toInt(); c.drawCircle(lx, ly, u * 3.5f, p)
                }
            }
            Land.PLATEAU -> {
                peaks(horizon, h * .30f, .9f, 3.1f, tint(.2f, 0xFF6A7C96.toInt(), .4f), true)
                ridge(horizon + h * .04f, h * .16f, 1.1f, 8.2f, tint(.45f, 0xFF4F8A4A.toInt(), 1f))
                // chalets on the middle hill
                for (k in 0 until 3) {
                    val hx = w * (.18f + k * .3f + rnd(k, 3f) * .06f); val hy = horizon + h * .035f + rnd(k, 8f) * h * .02f; val s2 = h * .045f
                    p.color = tint(.6f, 0xFF5A3E2E.toInt()); c.drawRect(hx - s2, hy - s2 * .8f, hx + s2, hy, p)
                    p.color = tint(.55f, 0xFF3A2A24.toInt(), 1f); val roof = Path(); roof.moveTo(hx - s2 * 1.25f, hy - s2 * .75f); roof.lineTo(hx, hy - s2 * 1.7f); roof.lineTo(hx + s2 * 1.25f, hy - s2 * .75f); roof.close(); c.drawPath(roof, p)
                    if (night > .25f || g > .5f) { p.color = 0xFFFFD27A.toInt(); p.alpha = 230; c.drawRect(hx - s2 * .25f, hy - s2 * .55f, hx + s2 * .25f, hy - s2 * .15f, p); p.alpha = 255 }
                }
                ridge(horizon + h * .12f, h * .12f, .9f, 2.7f, tint(.75f, 0xFF3E7A3E.toInt(), 1f))
                for (k in 0 until 6) {
                    val tx = w * (.08f + k * .17f + rnd(k, 1f) * .05f); val ty = horizon + h * .16f + rnd(k, 4f) * h * .04f
                    p.color = tint(.8f, 0xFF3A2A20.toInt()); c.drawRect(tx - u, ty - u * 4f, tx + u, ty + u * 6f, p)
                    p.color = tint(.8f, 0xFF2D6A34.toInt(), .9f); c.drawCircle(tx, ty - u * 9f, u * 9f, p)
                }
                ridge(h * 1.01f, h * .2f, .8f, 6.6f, tint(1f, 0xFF265A2E.toInt(), 1f))
            }
        }

        // depth and life: a haze at the horizon, rays of the low sun, birds by day, fireflies by night, smoke of a chimney
        run {
            val hz = Paint(Paint.ANTI_ALIAS_FLAG)
            hz.shader = LinearGradient(0f, horizon - h * .10f, 0f, horizon + h * .10f, intArrayOf(0x00FFFFFF, ColorUtils.setAlphaComponent(mix(bot, Color.WHITE, .2f), (77 * (1f - g * .5f)).toInt()), 0x00FFFFFF), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, horizon - h * .10f, w, horizon + h * .10f, hz)
        }
        if (sunX >= 0f && vis > .2f && land != Land.STREET && sp > -.08f && sp < 1.08f) {
            val qs = sp.coerceIn(0f, 1f); val lowSun = 1f - minOf(1f, sin(qs * PI.toFloat()) * 1.4f)
            val sx = sunX; val sy = horizon - sin(qs * PI.toFloat()) * horizon * .72f
            c.save(); c.clipRect(0f, 0f, w, horizon)
            val rp = Paint(Paint.ANTI_ALIAS_FLAG); rp.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
            for (r in 0 until 6) {
                val ang = PI.toFloat() / 2f + (r - 2.5f) * .22f + sin(t * 2f * PI.toFloat() + r) * .015f
                rp.color = ColorUtils.setAlphaComponent(mix(0xFFFFB36B.toInt(), 0xFFFFF3C4.toInt(), 1f - lowSun), (14f * vis * (.5f + lowSun)).toInt())
                val ray = Path(); ray.moveTo(sx, sy)
                ray.lineTo(sx + kotlin.math.cos(ang - .05f) * h * 1.2f, sy + sin(ang - .05f) * h * 1.2f); ray.lineTo(sx + kotlin.math.cos(ang + .05f) * h * 1.2f, sy + sin(ang + .05f) * h * 1.2f); ray.close()
                c.drawPath(ray, rp)
            }
            c.restore()
        }
        if (night < .4f && (sky == Sky.CLEAR || sky == Sky.PARTLY || sky == Sky.CLOUD) && (land == Land.MOUNTAIN || land == Land.SEA || land == Land.PLATEAU || land == Land.FOREST)) {
            p.color = 0xB31E2840.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = u * 1.3f; p.strokeCap = Paint.Cap.ROUND
            for (k in 0 until 3) {
                val bx = frac(drift * 3f + k * .31f) * (w + 60f * u) - 30f * u; val by = horizon * (.22f + k * .09f) + sin(drift * 40f + k) * u * 3f
                val fl = sin(t * 8f * PI.toFloat() + k) * u * 1.6f
                val wing = Path(); wing.moveTo(bx - u * 5f, by - u * 2f + fl); wing.quadTo(bx - u * 2f, by - u * 3f, bx, by); wing.quadTo(bx + u * 2f, by - u * 3f, bx + u * 5f, by - u * 2f + fl)
                c.drawPath(wing, p)
            }
            p.style = Paint.Style.FILL
        }
        if (night > .45f && (land == Land.FOREST || land == Land.PLATEAU)) {
            for (k in 0 until 14) {
                val fx = frac(sin(k * 91.7f) * 437.5f) * w + sin((t + k * .13f) * 2f * PI.toFloat()) * u * 8f
                val fy = h * (.62f + frac(sin(k * 17.3f) * 911.3f) * .3f) + kotlin.math.cos((t * 2f + k * .2f) * 2f * PI.toFloat()) * u * 5f
                val a2 = ((.4f + .6f * abs(sin((t * 2f + k * .37f) * PI.toFloat()))) * (night - .3f)).coerceIn(0f, 1f)
                glow(fx, fy, u * 8f, 0xFFFFF08A.toInt(), (90 * a2).toInt())
                p.color = 0xFFFFF08A.toInt(); p.alpha = (255 * a2).toInt(); c.drawCircle(fx, fy, u * 1.8f, p); p.alpha = 255
            }
        }
        if (land == Land.PLATEAU && variant == 0) {
            val hx = w * (.18f + rnd(0, 3f) * .06f); val hy = horizon + h * .035f + rnd(0, 8f) * h * .02f
            for (k in 0 until 4) {
                val ph = frac(t + k * .25f)
                p.color = mix(Color.WHITE, bot, .3f); p.alpha = (90 * (1f - ph)).toInt()
                c.drawCircle(hx + h * .03f + ph * u * 14f + sin(ph * 6f) * u * 3f, hy - h * .075f - ph * h * .12f, u * (2f + ph * 4f), p)
            }
            p.alpha = 255
        }
        // fog: soft bands laid over the land
        if (sky == Sky.FOG) for (k in 0..3) {
            val y = h * (.26f + k * .17f) + sin((t + k * .25f) * 2f * PI.toFloat()) * u * 3f
            p.shader = LinearGradient(0f, y, 0f, y + h * .18f, intArrayOf(0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, y, w, y + h * .18f, p); p.shader = null
        }
        // rain
        if (sky == Sky.RAIN || sky == Sky.DRIZZLE || sky == Sky.STORM) {
            val n = if (sky == Sky.DRIZZLE) 22 else 44
            p.color = 0xFFB8D8FF.toInt(); p.alpha = 190; p.strokeWidth = (if (sky == Sky.DRIZZLE) 1.2f else 2f) * u; p.strokeCap = Paint.Cap.ROUND
            for (k in 0 until n) {
                val sx = frac(k * .6180339f) * w; val sy = ((t * (if (sky == Sky.DRIZZLE) 1f else 2f) + frac(k * .381966f) * 3f) % 1f) * h
                c.drawLine(sx, sy, sx - h * .02f, sy + h * .09f, p)
            }
            p.alpha = 255
        }
        // snow falling
        if (snow) {
            p.color = Color.WHITE; p.alpha = 235
            for (k in 0 until 38) {
                val sx = frac(k * .6180339f) * w + sin((t + frac(k * .3f)) * 2f * PI.toFloat()) * 8f * u
                val sy = ((t + frac(k * .381966f) * 2.3f) % 1f) * h
                c.drawCircle(sx, sy, (1.6f + frac(k * .77f) * 2.2f) * u, p)
            }
            p.alpha = 255
        }
        // a flash of lightning
        if (sky == Sky.STORM && t > .93f) { p.color = Color.WHITE; p.alpha = 90; c.drawRect(0f, 0f, w, h, p); p.alpha = 255 }
    }
}

/** The weather page's scenery: the chosen landscape under the sky of this hour and this weather, with the weather moving in it. */
@Composable
fun WeatherSky(f: Forecast, landPref: String, modifier: Modifier = Modifier) {
    val land = remember(f.place, landPref, System.currentTimeMillis() / 10_800_000L) { WeatherLand.pick(landPref, f.place, f.elev) }
    val ck = remember(f) { WeatherLand.clockOf(f) }
    val sky = skyOf(f.code)
    val t by rememberInfiniteTransition(label = "land").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val drift by rememberInfiniteTransition(label = "landDrift").animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart), label = "d")
    ComposeCanvas(modifier) {
        drawIntoCanvas { WeatherLand.draw(it.nativeCanvas, size.width, size.height, land, sky, ck, t, drift) }
    }
}
