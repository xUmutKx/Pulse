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
        FOREST("forest", "Forest", "Orman"), STREET("street", "Street", "Sokak"), PLATEAU("plateau", "Highland", "Yayla"),
        MOSQUE("mosque", "Mosque", "Cami"), VILLAGE("village", "Village", "Köy"), DESERT("desert", "Desert", "Çöl"),
        EVEREST("everest", "Everest", "Everest"), JUNGLE("jungle", "Jungle", "Cangıl"), NEON("neon", "Neon city", "Neon şehir"), MUSHROOM("mushroom", "Mushroom village", "Mantar köyü"),
        AURORA("aurora", "Northern lights", "Kutup ışıkları"), SAVANNA("savanna", "Savanna", "Savan"), FUJI("fuji", "Fuji and sakura", "Fuji ve sakura"), LAVENDER("lavender", "Lavender field", "Lavanta tarlası"),
        VOLCANO("volcano", "Volcano", "Yanardağ"), ISLAND("island", "Tropical island", "Tropik ada")
    }

    /**
     * The scenery to draw. A chosen one is kept; "region" reads the place (high ground is mountains or highland, low ground by the coast is the sea, the rest city or forest,
     * by the height above the sea the forecast brings); anything else is random, a new pick every three hours, the same one at that time on the page and in the widgets.
     */
    /** Whether today is in Ramadan (the ninth month of the Hijri year): the only time of the year the mosques are drawn. Before Android 8 there is no Hijri calendar: never. */
    fun isRamadan(nowMs: Long = System.currentTimeMillis()): Boolean = runCatching {
        if (android.os.Build.VERSION.SDK_INT < 26) return@runCatching false
        val d = java.time.chrono.HijrahDate.from(java.time.Instant.ofEpochMilli(nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate())
        d.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == 9
    }.getOrDefault(false)

    fun pick(pref: String, place: String, elev: Double = Double.NaN, sky: Sky = Sky.CLEAR): Land {
        val all = Land.values()
        all.firstOrNull { it.key == pref }?.let { return it }
        if (pref == "region" && !elev.isNaN()) return when {
            elev > 1300.0 -> Land.MOUNTAIN
            elev > 650.0 -> Land.PLATEAU
            elev < 60.0 -> Land.SEA
            (place.hashCode() and 1) == 0 -> if (isRamadan()) Land.MOSQUE else Land.CITY
            else -> Land.FOREST
        }
        // random, and fit to the weather: snow picks a snowy place, rain a city, street or sea, fog a forest or the coast, and mosques are the commonest of all
        val pool0 = when (sky) {
            Sky.SNOW -> listOf(Land.MOUNTAIN to 2, Land.FOREST to 2, Land.VILLAGE to 2, Land.MOSQUE to 2, Land.STREET to 1, Land.CITY to 1, Land.PLATEAU to 1, Land.EVEREST to 3, Land.AURORA to 3, Land.MUSHROOM to 1)
            Sky.RAIN, Sky.DRIZZLE, Sky.STORM -> listOf(Land.CITY to 2, Land.STREET to 2, Land.SEA to 2, Land.MOSQUE to 2, Land.FOREST to 1, Land.VILLAGE to 1, Land.NEON to 4, Land.JUNGLE to 3, Land.FUJI to 1)
            Sky.FOG -> listOf(Land.FOREST to 3, Land.SEA to 2, Land.MOUNTAIN to 2, Land.MOSQUE to 2, Land.VILLAGE to 1, Land.JUNGLE to 2, Land.FUJI to 2, Land.EVEREST to 1)
            Sky.CLOUD -> listOf(Land.CITY to 1, Land.MOUNTAIN to 1, Land.FOREST to 1, Land.MOSQUE to 3, Land.VILLAGE to 1, Land.STREET to 1, Land.PLATEAU to 1, Land.SEA to 1, Land.EVEREST to 2, Land.VOLCANO to 2, Land.MUSHROOM to 2, Land.NEON to 2, Land.LAVENDER to 1, Land.FUJI to 2)
            else -> listOf(Land.MOSQUE to 4, Land.SEA to 1, Land.MOUNTAIN to 1, Land.CITY to 1, Land.FOREST to 1, Land.STREET to 1, Land.PLATEAU to 1, Land.VILLAGE to 1, Land.DESERT to 2, Land.EVEREST to 2, Land.SAVANNA to 2, Land.ISLAND to 2, Land.LAVENDER to 2, Land.FUJI to 2, Land.MUSHROOM to 2, Land.VOLCANO to 1, Land.AURORA to 1)
        }
        // outside Ramadan the mosques are left out of every pool; the scenery of a place changes once a day, not every few hours
        val ram = isRamadan()
        val pool = if (ram) pool0 else pool0.filter { it.first != Land.MOSQUE }
        // the day picks, and so does a change of weather: a new kind of sky gives a new scenery the same day
        val seed = System.currentTimeMillis() / 86_400_000L + place.hashCode() + sky.ordinal * 7919
        var r = ((seed xor (seed ushr 7)).toInt() and 0x7fffffff) % pool.sumOf { it.second }
        for ((l, wt) in pool) { if (r < wt) return l; r -= wt }
        return pool.first().first
    }

    /** One of three looks of a scenery, a new one each day (the same on the page and in the widgets). */
    fun variantOf(place: String, land: Land): Int {
        // how many looks each scenery has: the newest ones have one each
        val n = when (land) { Land.MOSQUE -> 4; Land.MOUNTAIN, Land.SEA, Land.CITY, Land.FOREST, Land.STREET, Land.PLATEAU, Land.VILLAGE, Land.DESERT -> 3; else -> 1 }
        return ((place.hashCode() and 0x7fffffff) + (System.currentTimeMillis() / 86_400_000L).toInt()) % n
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
    fun draw(c: Canvas, w: Float, h: Float, land: Land, sky: Sky, ck: Clock, t: Float = .3f, drift: Float = .2f, variant: Int = 0) {
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
                Land.MOSQUE -> Triple(0xFF2E4A9A.toInt(), 0xFFFFC98A.toInt(), .30f)
                Land.VILLAGE -> Triple(0xFF3D6BB0.toInt(), 0xFFF4C58A.toInt(), .28f)
                Land.DESERT -> Triple(0xFFD0642E.toInt(), 0xFFFFD27A.toInt(), .40f)
                Land.EVEREST -> Triple(0xFF3A5AC0.toInt(), 0xFFFFB89A.toInt(), .30f)
                Land.JUNGLE -> Triple(0xFF1E8A6E.toInt(), 0xFFC8F0A0.toInt(), .30f)
                Land.NEON -> Triple(0xFF3A0A6A.toInt(), 0xFFFF2D95.toInt(), .50f)
                Land.MUSHROOM -> Triple(0xFF6FB6FF.toInt(), 0xFFFFE6B0.toInt(), .35f)
                Land.AURORA -> Triple(0xFF0A3A5A.toInt(), 0xFF7AE0C8.toInt(), .35f)
                Land.SAVANNA -> Triple(0xFFD0642E.toInt(), 0xFFFFC24A.toInt(), .42f)
                Land.FUJI -> Triple(0xFF5A3FC0.toInt(), 0xFFFFB6C8.toInt(), .32f)
                Land.LAVENDER -> Triple(0xFF7A5AC0.toInt(), 0xFFFFD6E8.toInt(), .35f)
                Land.VOLCANO -> Triple(0xFF5A1A1A.toInt(), 0xFFFF7A3A.toInt(), .38f)
                Land.ISLAND -> Triple(0xFF0E9FC8.toInt(), 0xFFFFE0A0.toInt(), .35f)
            }
            if (gc != null) {
                val dayE = sin(((ck.hour - ck.rise) / (ck.set - ck.rise)).coerceIn(0f, 1f) * PI.toFloat())
                val am = gc.third * (1f - night * .55f) * (1f - g * .6f) * (1f - .45f * dayE * (1f - night))
                top = mix(top, gc.first, am); bot = mix(bot, gc.second, am)
            }
        }
        val snow = sky == Sky.SNOW
        val snowAmt = if (snow) .68f else 0f
        val horizon = h * when (land.key + variant) {
            "city0" -> .70f; "city2" -> .70f; "forest1" -> .56f; "forest2" -> .72f; "mountain1" -> .66f; "sea2" -> .58f; "plateau1" -> .70f; "plateau2" -> .72f
            "mosque1", "desert2", "village1" -> .66f; "mosque3" -> .62f; "village2" -> .72f
            else -> when (land) { Land.SEA -> .60f; Land.CITY -> .74f; Land.STREET -> .62f; Land.MOUNTAIN -> .72f; Land.FOREST -> .74f; Land.PLATEAU -> .70f; Land.MOSQUE, Land.VILLAGE, Land.DESERT, Land.SAVANNA -> .70f; Land.EVEREST -> .74f; Land.JUNGLE, Land.LAVENDER -> .62f; Land.NEON -> .78f; Land.MUSHROOM, Land.AURORA, Land.VOLCANO -> .72f; Land.FUJI -> .66f; Land.ISLAND -> .56f }
        }
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
            // the real moon of tonight, by its true phase
            MoonArt.draw(c, mx, my, r, WeatherFmt.moonAge(), vis)
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

        // small drawing helpers of the sceneries: [col] picks the colour (and its opacity), the rest paint with it
        fun col(cc: Int, a: Float = 1f) { p.style = Paint.Style.FILL; p.color = cc; p.alpha = (a * 255f).toInt().coerceIn(0, 255) }
        fun rectF(x1: Float, y1: Float, x2: Float, y2: Float) { c.drawRect(x1, y1, x2, y2, p) }
        fun circ(x: Float, y: Float, r: Float) { c.drawCircle(x, y, r, p) }
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float, wd: Float) { p.strokeWidth = wd; p.strokeCap = Paint.Cap.ROUND; c.drawLine(x1, y1, x2, y2, p) }
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { val q = Path(); q.moveTo(x1, y1); q.lineTo(x2, y2); q.lineTo(x3, y3); q.close(); c.drawPath(q, p) }
        fun quadP(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) { val q = Path(); q.moveTo(x1, y1); q.lineTo(x2, y2); q.lineTo(x3, y3); q.lineTo(x4, y4); q.close(); c.drawPath(q, p) }
        fun dome(cx: Float, cy: Float, r: Float) { c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, true, p) }
        fun oval(cx: Float, cy: Float, rx: Float, ry: Float) { c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p) }
        fun waterFill(top: Float, topCol: Int, botCol: Int) { p.shader = LinearGradient(0f, top, 0f, h, topCol, botCol, Shader.TileMode.CLAMP); c.drawRect(0f, top, w, h, p); p.shader = null }
        // the glitter of the sun or the moon on water: short bars, wider and fainter as they come near
        fun reflect(top: Float, bottom: Float, n: Int) {
            if (!sunReflect) return
            for (k in 0 until n) {
                val y = top + (k + 1) * (bottom - top) / (n + 1.5f); val half = (5f + k * 4f) * u * (.5f + .5f * sin(t * 4f * PI.toFloat() + k * 1.3f))
                col(mix(0xFFFFE9A8.toInt(), 0xFFEAF0FF.toInt(), night), (.67f * (1f - k / (n + 1f)) * vis).coerceIn(0f, 1f)); ln(sunX - half, y, sunX + half, y, h * .006f)
            }
        }

        // figures shared by the newer sceneries: a mosque, a cypress, a house and a palm
        fun mosq(cx: Float, by: Float, sz: Float, cc: Int, mn: Int) {
            col(cc); rectF(cx - sz * .62f, by - sz * .42f, cx + sz * .62f, by + u); dome(cx, by - sz * .42f, sz * .5f)
            rectF(cx - u * 1.2f, by - sz * .92f - u * 7f, cx + u * 1.2f, by - sz * .92f); circ(cx, by - sz * .92f - u * 9f, u * 2.6f)
            for (sd in intArrayOf(-1, 1)) { dome(cx + sd * sz * .48f, by - sz * .4f, sz * .24f); dome(cx + sd * sz * .74f, by - sz * .34f, sz * .16f) }
            for (k in 0 until mn) {
                val sd = if (k % 2 == 1) 1 else -1; val rk = k / 2; val nx = cx + sd * (sz * .98f + rk * sz * .26f); val mh = sz * (1.6f - rk * .22f)
                rectF(nx - sz * .04f, by - mh, nx + sz * .04f, by + u); rectF(nx - sz * .07f, by - mh * .72f, nx + sz * .07f, by - mh * .72f + sz * .03f)
                tri(nx, by - mh - sz * .32f, nx - sz * .06f, by - mh, nx + sz * .06f, by - mh)
            }
            if (night > .12f) { col(0xFFFFD27A.toInt(), night.coerceIn(0f, 1f) * .9f); for (q in 0 until 5) rectF(cx - sz * .5f + q * sz * .25f, by - sz * .3f, cx - sz * .43f + q * sz * .25f, by - sz * .13f) }
        }
        fun cypress(x: Float, by: Float, hh: Float, cc: Int) {
            col(cc); val q = Path(); q.moveTo(x, by - hh); q.quadTo(x + hh * .16f, by - hh * .45f, x + hh * .07f, by); q.lineTo(x - hh * .07f, by); q.quadTo(x - hh * .16f, by - hh * .45f, x, by - hh); c.drawPath(q, p)
        }
        fun house(x: Float, by: Float, bw: Float, bh: Float, body: Int, roof: Int, snowy: Boolean, seed: Int) {
            col(body); rectF(x, by - bh, x + bw, by); col(roof); tri(x - u * 2f, by - bh, x + bw / 2f, by - bh - bw * .5f, x + bw + u * 2f, by - bh)
            if (snowy) { col(Color.WHITE, .92f); val q = Path(); q.moveTo(x - u * 2f, by - bh); q.lineTo(x + bw / 2f, by - bh - bw * .5f); q.lineTo(x + bw + u * 2f, by - bh); q.lineTo(x + bw * .92f, by - bh); q.lineTo(x + bw / 2f, by - bh - bw * .38f); q.lineTo(x + bw * .08f, by - bh); q.close(); c.drawPath(q, p) }
            val on = night > .12f && rnd(seed, 3f) > .25f
            if (on) col(0xFFFFD27A.toInt(), .95f) else col(mix(0xFF20304A.toInt(), bot, .4f)); rectF(x + bw * .3f, by - bh * .7f, x + bw * .7f, by - bh * .35f)
        }
        fun palm(px: Float, py: Float, lh: Float, lean: Float) {
            p.style = Paint.Style.STROKE; p.strokeWidth = u * 3.2f; p.strokeCap = Paint.Cap.ROUND; p.color = tint(.95f, 0xFF2A1E16.toInt()); p.alpha = 255
            val trunk = Path(); trunk.moveTo(px, py); trunk.quadTo(px + lean * .2f, py - lh * .55f, px + lean, py - lh); c.drawPath(trunk, p); p.style = Paint.Style.FILL
            val tx = px + lean; val ty = py - lh
            for (k in 0 until 7) {
                val a = -PI.toFloat() * .9f + k * (PI.toFloat() * .8f / 6f) + sin(t * 2f * PI.toFloat() + k) * .05f
                c.save(); c.translate(tx, ty); c.rotate(a * 57.2958f); col(tint(.95f, 0xFF1F5A34.toInt(), .2f))
                val leaf = Path(); leaf.moveTo(0f, 0f); leaf.quadTo(lh * .35f, -lh * .22f, lh * .7f, lh * .08f); leaf.quadTo(lh * .35f, -lh * .06f, 0f, u * 2f); c.drawPath(leaf, p); c.restore()
            }
        }

        when (land.key + variant) {
            "mountain0" -> {
                peaks(horizon, h * .40f, .8f, 1.1f, tint(.25f, 0xFF4A5878.toInt(), .3f), true)
                peaks(horizon + h * .07f, h * .30f, 1.15f, 4f, tint(.5f, 0xFF39455E.toInt(), .3f), true)
                ridge(horizon + h * .14f, h * .13f, 1.6f, 7.3f, tint(.8f, 0xFF1F3A34.toInt(), 1f))
                p.color = tint(1f, 0xFF10241C.toInt(), 1f); c.drawRect(0f, h * .92f, w, h, p)
                for (k in 0 until 14) pine(w * (k / 13f) + noise(k * 1.7f, 2f) * w * .02f, h * (.95f + rnd(k, 5f) * .03f), h * (.16f + rnd(k + 3, 5f) * .08f), tint(1f, 0xFF10241C.toInt(), .7f))
            }
            "sea0" -> {
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
            "city0" -> {
                // Istanbul: the old city with domes and minarets, the Galata Tower, a suspension bridge and a ferry on the water
                val hz = horizon
                ridge(hz, h * .06f, 1.0f, 2.0f, tint(.28f, 0xFF4C566E.toInt(), .1f))
                val cityCol = tint(.45f, 0xFF35405A.toInt(), .1f)
                col(cityCol)
                run { val hill = Path(); hill.moveTo(0f, hz + h * .08f); hill.quadTo(w * .22f, hz - h * .10f, w * .5f, hz + h * .03f); hill.lineTo(w * .5f, hz + h * .08f); hill.close(); c.drawPath(hill, p) }
                fun mosque(cx: Float, by: Float, sz: Float) {
                    col(cityCol); dome(cx, by - sz * .5f, sz * .5f); rectF(cx - sz * .55f, by - sz * .5f, cx + sz * .55f, by + u)
                    for (sd in intArrayOf(-1, 1)) {
                        dome(cx + sd * sz * .55f, by - sz * .42f, sz * .22f)
                        val nx = cx + sd * sz * .85f
                        rectF(nx - sz * .04f, by - sz * 1.5f, nx + sz * .04f, by + u); tri(nx, by - sz * 1.78f, nx - sz * .07f, by - sz * 1.5f, nx + sz * .07f, by - sz * 1.5f)
                    }
                }
                mosque(w * .17f, hz - h * .012f, u * 30f); mosque(w * .36f, hz + h * .012f, u * 20f)
                for (k in 0 until 16) { val bx = w * (.02f + k * .032f); val bh = h * (.02f + rnd(k, 4f) * .03f); col(cityCol); rectF(bx, hz + h * .03f - bh + sin(bx / w * 3f) * h * .02f, bx + w * .026f, hz + h * .03f - bh + sin(bx / w * 3f) * h * .02f + bh + h * .05f) }
                // the Galata Tower
                run {
                    val gx = w * .64f; val by = hz + h * .035f; val tw = u * 15f; val bh = h * .24f
                    col(tint(.5f, 0xFF3A4560.toInt(), .1f))
                    rectF(gx - tw / 2f, by - bh, gx + tw / 2f, by + h * .06f); rectF(gx - tw * .85f, by - bh, gx + tw * .85f, by - bh + u * 5f)
                    tri(gx, by - bh - u * 34f, gx - tw * .8f, by - bh, gx + tw * .8f, by - bh); rectF(gx - u, by - bh - u * 42f, gx + u, by - bh - u * 32f)
                    if (night > .15f) { col(0xFFFFD27A.toInt(), ((night - .1f).coerceIn(0f, 1f) * .9f)); for (q in 0 until 4) rectF(gx - u * 1.5f, by - bh * (.2f + q * .2f), gx + u * 1.5f, by - bh * (.2f + q * .2f) + u * 5f); rectF(gx - tw * .6f, by - bh + u, gx + tw * .6f, by - bh + u * 4f) }
                }
                val wy = hz + h * .035f
                waterFill(wy, mix(bot, 0xFF0B4A72.toInt(), .35f), mix(0xFF0A3558.toInt(), 0xFF031829.toInt(), night * .6f + .2f))
                reflect(wy, h, 12)
                // the bridge: two towers, the main cable and its hangers, and the lights along its deck
                run {
                    val dy = hz + h * .085f; val tx1 = w * .14f; val tx2 = w * .86f; val th = h * .20f; val bc = tint(.7f, 0xFF232B3C.toInt())
                    col(bc); rectF(0f, dy, w, dy + u * 3.2f)
                    for (tx in floatArrayOf(tx1, tx2)) { rectF(tx - u * 3f, dy - th, tx + u * 3f, dy + h * .05f); rectF(tx - u * 7f, dy - th * .62f, tx + u * 7f, dy - th * .62f + u * 3f) }
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * 1.4f; p.color = bc; p.alpha = 255
                    val cab = Path(); cab.moveTo(0f, dy - th * .25f); cab.quadTo(tx1 * .5f, dy - th * .9f, tx1, dy - th); cab.quadTo(w * .5f, dy + th * .1f + th * 0f, tx2, dy - th); cab.quadTo(w - tx1 * .5f, dy - th * .9f, w, dy - th * .25f); c.drawPath(cab, p)
                    p.strokeWidth = u * .7f
                    var x = tx1 + u * 8f
                    while (x < tx2) { val tq = (x - tx1) / (tx2 - tx1); val cy = (1 - tq) * (1 - tq) * (dy - th) + 2f * (1 - tq) * tq * (dy + th * .1f) + tq * tq * (dy - th); c.drawLine(x, cy, x, dy, p); x += u * 12f }
                    p.style = Paint.Style.FILL
                    if (night > .1f) { var lx = 0f; while (lx < w) { col(0xFFFFE3A0.toInt(), ((night + .1f).coerceIn(0f, 1f) * .9f)); circ(lx, dy - u, u * 1.1f); col(0xFFFFE3A0.toInt(), ((night).coerceIn(0f, 1f) * .18f)); rectF(lx - u * .5f, dy + u * 4f, lx + u * .5f, dy + u * 4f + (h - dy) * .5f * rnd((lx / u).toInt(), 2f)); lx += u * 10f } }
                }
                // a ferry crossing
                run {
                    val fx = frac(drift * 4f) * (w + 80f * u) - 40f * u; val fy = hz + h * .15f + sin(t * 4f * PI.toFloat()) * u * 1.2f
                    col(tint(.9f, 0xFF1B2230.toInt())); quadP(fx - u * 22f, fy, fx + u * 22f, fy, fx + u * 16f, fy + u * 6f, fx - u * 17f, fy + u * 6f)
                    rectF(fx - u * 12f, fy - u * 7f, fx + u * 10f, fy); rectF(fx + u * 4f, fy - u * 13f, fx + u * 8f, fy - u * 7f)
                    if (night > .15f) { col(0xFFFFE3A0.toInt(), .9f); for (q in 0 until 4) rectF(fx - u * 10f + q * u * 5f, fy - u * 5f, fx - u * 7f + q * u * 5f, fy - u * 3f) }
                }
            }
            "city1" -> {
                // a clean modern skyline: a few towers, sparse lights, a quiet waterfront
                skyline(horizon, h * .10f, h * .26f, w * .03f, w * .05f, 21f, tint(.28f, 0xFF4C566E.toInt(), .1f), false)
                val towers = arrayOf(floatArrayOf(.10f, .075f, .30f), floatArrayOf(.23f, .06f, .42f), floatArrayOf(.37f, .085f, .34f), floatArrayOf(.53f, .055f, .50f), floatArrayOf(.67f, .08f, .30f), floatArrayOf(.82f, .06f, .40f), floatArrayOf(.93f, .06f, .26f))
                towers.forEachIndexed { q, tw ->
                    val x = w * tw[0] - w * tw[1] / 2f; val bw = w * tw[1]; val bh = h * tw[2]
                    col(tint(.6f, 0xFF2B3550.toInt(), .1f)); rectF(x, horizon - bh, x + bw, h); rectF(x + bw * .18f, horizon - bh - h * .03f, x + bw * .82f, horizon - bh + u)
                    if (q % 3 == 1) {
                        col(tint(.6f, 0xFF2B3550.toInt())); ln(x + bw / 2f, horizon - bh - h * .03f, x + bw / 2f, horizon - bh - h * .10f, u * 1.3f)
                        if (night > .1f && sin(t * 6f * PI.toFloat() + q) > -.3f) { col(0xFFFF5252.toInt()); circ(x + bw / 2f, horizon - bh - h * .10f, u * 1.8f) }
                    }
                    if (night > .12f) { val rows = (bh / (u * 9f)).toInt(); for (r in 0 until rows) for (k in 0 until 3) if (rnd(q * 50 + r * 3 + k, 6f) > .62f) { col(0xFFFFD27A.toInt(), ((night - .05f).coerceIn(0f, 1f) * .85f)); rectF(x + bw * (.14f + k * .28f), horizon - bh + u * 6f + r * u * 9f, x + bw * (.30f + k * .28f), horizon - bh + u * 10f + r * u * 9f) }
                    }
                }
                col(tint(.95f, 0xFF141926.toInt(), .1f)); rectF(0f, h * .86f, w, h)
                for (k in 0 until 12) {
                    val lx = w * (k + .5f) / 12f
                    col(0xFF1A2030.toInt()); rectF(lx - u * .8f, h * .80f, lx + u * .8f, h * .86f)
                    if (night > .1f || sky == Sky.RAIN) glow(lx, h * .80f, u * 14f, 0xFFFFD98A.toInt(), 128)
                    col(0xFFFFF1C2.toInt()); circ(lx, h * .80f, u * 1.5f)
                }
            }
            "city2" -> {
                // an old hill town: tiled roofs, a dome and minarets, a castle wall, lit windows
                ridge(horizon, h * .10f, 1.2f, 1f, tint(.25f, 0xFF4C566E.toInt(), .1f))
                run {
                    col(tint(.3f, 0xFF4A5470.toInt(), .1f)); rectF(w * .04f, horizon - h * .07f, w * .24f, horizon + u)
                    for (k in 0 until 7) rectF(w * .04f + k * w * .03f, horizon - h * .09f, w * .04f + k * w * .03f + w * .016f, horizon - h * .07f)
                    rectF(w * .06f, horizon - h * .13f, w * .09f, horizon - h * .07f); rectF(w * .19f, horizon - h * .13f, w * .22f, horizon - h * .07f)
                }
                val roofs = intArrayOf(0xFFB5533C.toInt(), 0xFFC8704A.toInt(), 0xFF9E4A38.toInt(), 0xFFD08A5A.toInt())
                for (row in 0 until 3) {
                    val y0 = horizon + h * (.01f + row * .075f); val sz = h * (.065f + row * .02f)
                    var x = -sz * .3f
                    while (x < w) {
                        val hh = sz * (.6f + rnd(x.toInt() + 3, row.toFloat()) * .3f); val bw2 = sz * .95f
                        col(tint(.35f + row * .2f, 0xFFE3D2B8.toInt(), .1f)); rectF(x, y0 - hh, x + bw2, h)
                        col(tint(.4f + row * .2f, roofs[(rnd(x.toInt() + 7, row.toFloat()) * 4f).toInt().coerceIn(0, 3)])); tri(x - u * 2f, y0 - hh, x + bw2 / 2f, y0 - hh - sz * .42f, x + bw2 + u * 2f, y0 - hh)
                        if (night > .15f && rnd(x.toInt() + 11, row.toFloat()) > .4f) { col(0xFFFFD27A.toInt(), night.coerceIn(0f, 1f) * .9f); rectF(x + bw2 * .3f, y0 - hh * .7f, x + bw2 * .5f, y0 - hh * .45f) }
                        x += sz * (1.0f + rnd(x.toInt(), row.toFloat()) * .25f)
                    }
                }
                fun mini(cx: Float, by: Float, hh: Float) {
                    col(tint(.5f, 0xFFDCD0C0.toInt(), .1f)); rectF(cx - u * 3f, by - hh, cx + u * 3f, h); rectF(cx - u * 5f, by - hh * .7f, cx + u * 5f, by - hh * .7f + u * 3f)
                    col(tint(.5f, 0xFF6F7A90.toInt())); tri(cx, by - hh - u * 20f, cx - u * 4f, by - hh, cx + u * 4f, by - hh)
                }
                mini(w * .34f, horizon + h * .09f, h * .22f); mini(w * .62f, horizon + h * .09f, h * .26f); mini(w * .86f, horizon + h * .09f, h * .20f)
                run { val dx = w * .48f; val dy = horizon + h * .095f; col(tint(.5f, 0xFFDCD0C0.toInt(), .1f)); rectF(dx - u * 26f, dy - u * 20f, dx + u * 26f, h); col(tint(.5f, 0xFF6F7A90.toInt())); dome(dx, dy - u * 20f, u * 26f) }
                col(tint(1f, 0xFF2A1E1A.toInt(), .2f)); rectF(0f, h * .92f, w, h)
            }
            "forest0" -> {
                ridge(horizon, h * .10f, 1.2f, 3.3f, tint(.2f, 0xFF2F6B4F.toInt(), .5f))
                for (k in 0 until 16) pine(w * (k / 15f) + noise(k * 2.1f, 1f) * w * .03f, horizon + h * .03f, h * (.15f + rnd(k, 9f) * .05f), tint(.22f, 0xFF2F6B4F.toInt(), .8f))
                p.shader = LinearGradient(0f, horizon - h * .04f, 0f, horizon + h * .16f, intArrayOf(0x00FFFFFF, ColorUtils.setAlphaComponent(mix(Color.WHITE, bot, .4f), 115), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, horizon - h * .04f, w, horizon + h * .16f, p); p.shader = null
                for (k in 0 until 12) pine(w * (k / 11f) + noise(k * 1.3f, 4f) * w * .04f + w * .02f, horizon + h * .14f, h * (.21f + rnd(k, 2f) * .06f), tint(.5f, 0xFF245A40.toInt(), .8f))
                p.color = tint(.8f, 0xFF17402E.toInt(), 1f); c.drawRect(0f, h * .88f, w, h, p)
                for (k in 0 until 8) pine(w * (k / 7f) + w * .05f * noise(k * 1.1f, 6f), h * 1.0f, h * (.34f + rnd(k, 4f) * .08f), tint(1f, 0xFF0C2418.toInt(), .8f))
            }
            "street0" -> {
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
            "plateau0" -> {
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
            "forest1" -> {
                // a forest around a lake: the trees stand in the water upside down
                val sh = horizon + h * .03f
                ridge(horizon, h * .10f, 1.1f, 5f, tint(.2f, 0xFF2F6B4F.toInt(), .5f))
                for (k in 0 until 24) pine(w * (k / 23f) + noise(k * 2.3f, 2f) * w * .02f, sh, h * (.13f + rnd(k, 9f) * .05f), tint(.3f, 0xFF2A5E45.toInt(), .8f))
                waterFill(sh, mix(bot, 0xFF1D5A66.toInt(), .45f), mix(0xFF0E3340.toInt(), 0xFF04141C.toInt(), night * .6f + .2f))
                c.saveLayerAlpha(0f, sh, w, h, 107)
                c.translate(0f, sh); c.scale(1f, -.9f); c.translate(0f, -sh)
                for (k in 0 until 24) pine(w * (k / 23f) + noise(k * 2.3f, 2f) * w * .02f, sh, h * (.13f + rnd(k, 9f) * .05f), tint(.3f, 0xFF2A5E45.toInt(), .8f))
                c.restore()
                reflect(sh, h, 12)
                for (k in 0 until 3) {
                    val ph = frac(t + k / 3f); val rx = w * (.3f + k * .22f); val ry = sh + (h - sh) * (.35f + k * .12f)
                    p.style = Paint.Style.STROKE; p.strokeWidth = u; p.color = Color.WHITE; p.alpha = (89 * (1f - ph)).toInt()
                    c.drawOval(RectF(rx - u * (6f + ph * 26f), ry - u * (2f + ph * 8f), rx + u * (6f + ph * 26f), ry + u * (2f + ph * 8f)), p); p.style = Paint.Style.FILL
                }
                // a jetty and a rowing boat
                col(tint(.85f, 0xFF4A3626.toInt())); rectF(0f, sh + h * .19f, w * .34f, sh + h * .19f + u * 4f)
                for (k in 0 until 5) rectF(w * .03f + k * w * .07f, sh + h * .19f, w * .03f + k * w * .07f + u * 3f, sh + h * .19f + u * 14f)
                run {
                    val bx = w * .58f; val by = sh + h * .17f + sin(t * 4f * PI.toFloat()) * u * 1.2f
                    col(tint(.9f, 0xFF2A1E16.toInt())); quadP(bx - u * 18f, by, bx + u * 18f, by, bx + u * 12f, by + u * 6f, bx - u * 12f, by + u * 6f)
                }
                for (k in 0 until 6) pine(w * (if (k < 3) k * .07f + .02f else .78f + (k - 3) * .09f), h * 1.03f, h * (.40f + rnd(k, 5f) * .1f), tint(1f, 0xFF0C2418.toInt(), .8f))
                for (k in 0 until 26) {
                    val rx = (if (k < 13) k * .012f else .62f + (k - 13) * .03f) * w; val sw = sin((t + k * .1f) * 2f * PI.toFloat()) * u * 2f
                    col(tint(1f, 0xFF2C4A26.toInt())); ln(rx, h, rx + sw, h * .9f - rnd(k, 3f) * h * .05f, u * 1.3f)
                }
            }
            "forest2" -> {
                // an autumn wood: round crowns in red, orange and yellow, leaves falling
                val cols = intArrayOf(0xFFD9722F.toInt(), 0xFFC0392B.toInt(), 0xFFE6B422.toInt(), 0xFF8A9A3A.toInt(), 0xFFB5532B.toInt())
                fun crown(x: Float, y: Float, r: Float, cc: Int, sz: Float) {
                    col(tint(sz, 0xFF3A2A1C.toInt())); rectF(x - r * .07f, y - r * .2f, x + r * .07f, y + r * .7f)
                    col(tint(sz, cc, .5f))
                    val blobs = arrayOf(floatArrayOf(-.5f, .1f, .62f), floatArrayOf(.5f, .1f, .6f), floatArrayOf(0f, -.45f, .7f), floatArrayOf(-.2f, .25f, .7f), floatArrayOf(.3f, -.1f, .6f))
                    for (b in blobs) circ(x + b[0] * r + sin(t * 2f * PI.toFloat() + x) * u * .6f, y + b[1] * r - r * .4f, r * b[2])
                }
                ridge(horizon, h * .10f, 1.2f, 4f, tint(.2f, 0xFFB5703A.toInt(), .4f))
                for (k in 0 until 12) crown(w * (k / 11f) + noise(k * 2.2f, 2f) * w * .02f, horizon + h * .03f, h * (.06f + rnd(k, 9f) * .02f), cols[k % 5], .22f)
                p.shader = LinearGradient(0f, horizon - h * .03f, 0f, horizon + h * .15f, intArrayOf(0x00FFFFFF, ColorUtils.setAlphaComponent(mix(Color.WHITE, bot, .4f), 102), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, horizon - h * .03f, w, horizon + h * .15f, p); p.shader = null
                for (k in 0 until 9) crown(w * (k / 8f) + noise(k * 1.4f, 5f) * w * .04f + w * .02f, horizon + h * .16f, h * (.10f + rnd(k, 2f) * .03f), cols[(k + 2) % 5], .5f)
                col(tint(.8f, 0xFF7A4A22.toInt(), .6f)); rectF(0f, h * .88f, w, h)
                for (k in 0 until 6) crown(w * (k / 5f) + w * .06f * noise(k.toFloat(), 7f), h, h * (.2f + rnd(k, 4f) * .05f), cols[(k + 1) % 5], .9f)
                for (k in 0 until 18) {
                    val ph = frac(t + rnd(k, 1f)); val lx = rnd(k, 2f) * w + sin((t + k * .2f) * 2f * PI.toFloat()) * u * 10f; val ly = ph * h
                    c.save(); c.translate(lx, ly); c.rotate((ph * 6f + k) * 57.2958f); col(cols[k % 5], .9f); oval(0f, 0f, u * 3f, u * 1.6f); c.restore()
                }
            }
            "mountain1" -> {
                // an alpine lake under a big snowy peak, the mountain mirrored in the water
                fun mt() { peaks(horizon, h * .46f, .55f, 2.2f, tint(.25f, 0xFF4A5878.toInt(), .3f), true); peaks(horizon + h * .03f, h * .30f, .9f, 5f, tint(.5f, 0xFF39455E.toInt(), .3f), true) }
                mt()
                waterFill(horizon, mix(bot, 0xFF2A5F86.toInt(), .4f), mix(0xFF123A5A.toInt(), 0xFF04141F.toInt(), night * .6f + .2f))
                c.saveLayerAlpha(0f, horizon, w, h, 102)
                c.translate(0f, horizon); c.scale(1f, -.8f); c.translate(0f, -horizon)
                mt()
                c.restore()
                reflect(horizon, h, 10)
                for (k in 0 until 8) {
                    val y = horizon + (k + .7f) * (h - horizon) / 9f
                    col(Color.WHITE, .18f); ln(w * (.1f + frac(k * .37f) * .5f), y + sin((t + k * .1f) * 2f * PI.toFloat()) * u * .8f, w * (.3f + frac(k * .37f) * .6f), y, u * 1.2f)
                }
                ridge(h * .99f, h * .10f, .9f, 3.5f, tint(1f, 0xFF10241C.toInt(), 1f))
                for (k in 0 until 9) pine(w * (if (k < 4) k * .06f + .01f else .72f + (k - 4) * .07f), h * (.97f + rnd(k, 5f) * .04f), h * (.22f + rnd(k + 3, 5f) * .08f), tint(1f, 0xFF10241C.toInt(), .7f))
            }
            "mountain2" -> {
                // a valley: a cabin with a smoking chimney, a fence, meadow and pines
                peaks(horizon, h * .40f, .8f, .4f, tint(.25f, 0xFF4A5878.toInt(), .3f), true)
                peaks(horizon + h * .06f, h * .26f, 1.2f, 3f, tint(.5f, 0xFF39455E.toInt(), .3f), true)
                ridge(horizon + h * .10f, h * .12f, 1.4f, 6f, tint(.65f, 0xFF3E7A3E.toInt(), 1f))
                ridge(horizon + h * .20f, h * .10f, .9f, 1.3f, tint(.85f, 0xFF2F6A34.toInt(), 1f))
                run {
                    val hx = w * .34f; val hy = horizon + h * .20f; val s2 = h * .055f
                    col(tint(.8f, 0xFF6A4A32.toInt())); rectF(hx - s2, hy - s2 * .9f, hx + s2, hy)
                    col(tint(.7f, 0xFF3A2A24.toInt(), 1f)); tri(hx - s2 * 1.3f, hy - s2 * .9f, hx, hy - s2 * 1.9f, hx + s2 * 1.3f, hy - s2 * .9f); rectF(hx + s2 * .5f, hy - s2 * 1.8f, hx + s2 * .78f, hy - s2 * 1.2f)
                    if (night > .15f || g > .5f) { col(0xFFFFD27A.toInt(), .95f); rectF(hx - s2 * .3f, hy - s2 * .7f, hx + s2 * .3f, hy - s2 * .25f) }
                    for (k in 0 until 4) { val ph = frac(t + k * .25f); col(mix(Color.WHITE, bot, .3f), .4f * (1f - ph)); circ(hx + s2 * .64f + ph * u * 12f + sin(ph * 6f) * u * 3f, hy - s2 * 1.9f - ph * h * .12f, u * (2f + ph * 4f)) }
                }
                ridge(h * 1.01f, h * .16f, .8f, 6.6f, tint(1f, 0xFF265A2E.toInt(), 1f))
                col(tint(.95f, 0xFF4A3626.toInt()))
                for (k in 0 until 12) { val fx = w * (.02f + k * .08f); rectF(fx, h * .86f, fx + u * 2f, h * .93f) }
                rectF(0f, h * .88f, w, h * .88f + u * 1.6f); rectF(0f, h * .91f, w, h * .91f + u * 1.6f)
                for (k in 0 until 7) pine(w * (.72f + k * .045f), h * (1.0f + rnd(k, 5f) * .02f), h * (.24f + rnd(k + 3, 5f) * .1f), tint(1f, 0xFF10241C.toInt(), .7f))
            }
            "sea1" -> {
                // a lighthouse on a rock; at dusk and night its beam sweeps the sea
                ridge(horizon + 2f, h * .06f, 1.2f, 2.2f, tint(.35f, 0xFF3C5A6A.toInt(), .5f))
                waterFill(horizon, mix(bot, 0xFF0B4A72.toInt(), .35f), mix(0xFF0A3558.toInt(), 0xFF031829.toInt(), night * .6f + .2f))
                reflect(horizon, h, 14)
                for (k in 0 until 7) { val y = horizon + (k + .7f) * (h - horizon) / 8f; col(Color.WHITE, .16f); ln(w * (.05f + frac(k * .37f) * .5f), y, w * (.3f + frac(k * .37f) * .6f), y, u * 1.2f) }
                val lx = w * .72f; val ly = horizon + h * .20f
                col(tint(.9f, 0xFF1A222C.toInt()))
                run { val rock = Path(); rock.moveTo(w * .50f, h); rock.lineTo(w * .56f, ly + h * .05f); rock.quadTo(w * .66f, ly - h * .02f, w * .76f, ly - h * .01f); rock.quadTo(w * .92f, ly, w, ly + h * .06f); rock.lineTo(w, h); rock.close(); c.drawPath(rock, p) }
                val tw = u * 14f; val th = h * .32f
                col(tint(.7f, 0xFFEDE6DA.toInt(), .2f)); quadP(lx - tw * .7f, ly, lx + tw * .7f, ly, lx + tw * .45f, ly - th, lx - tw * .45f, ly - th)
                col(tint(.7f, 0xFFC0392B.toInt()))
                for (k in 0 until 2) { val y0 = ly - th * (.2f + k * .4f); quadP(lx - tw * (.64f - k * .09f), y0, lx + tw * (.64f - k * .09f), y0, lx + tw * (.58f - k * .09f), y0 - th * .14f, lx - tw * (.58f - k * .09f), y0 - th * .14f) }
                col(tint(.5f, 0xFF2A2F38.toInt())); rectF(lx - tw * .7f, ly - th - u * 5f, lx + tw * .7f, ly - th); tri(lx - tw * .8f, ly - th - u * 5f, lx, ly - th - u * 18f, lx + tw * .8f, ly - th - u * 5f)
                col(0xFFFFF1C2.toInt()); rectF(lx - tw * .35f, ly - th - u * 4f, lx + tw * .35f, ly - th - u)
                if (night > .2f || sky == Sky.RAIN || sky == Sky.STORM) {
                    val ang = sin(t * 2f * PI.toFloat()) * .5f - PI.toFloat() * .05f
                    val bx = lx; val by = ly - th - u * 2f
                    val beam = Path(); beam.moveTo(bx, by)
                    beam.lineTo(bx + kotlin.math.cos(ang - .07f + PI.toFloat()) * w, by + sin(ang - .07f + PI.toFloat()) * w); beam.lineTo(bx + kotlin.math.cos(ang + .07f + PI.toFloat()) * w, by + sin(ang + .07f + PI.toFloat()) * w); beam.close()
                    val bp = Paint(Paint.ANTI_ALIAS_FLAG); bp.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD); bp.color = ColorUtils.setAlphaComponent(0xFFFFF1C2.toInt(), (76f * (night + .3f).coerceIn(0f, 1f)).toInt()); c.drawPath(beam, bp)
                    glow(bx, by, u * 30f, 0xFFFFF1C2.toInt(), 178)
                }
                for (k in 0 until 6) { val ph = frac(t * 2f + k / 6f); col(Color.WHITE, .5f * (1f - ph)); ln(w * .52f + k * w * .03f, h * .97f - ph * h * .03f, w * .55f + k * w * .03f, h * .97f - ph * h * .03f, u * 1.2f) }
            }
            "sea2" -> {
                // a beach: sand in front, foam running up and back, two palms and a striped umbrella
                val sy = h * .74f
                p.shader = LinearGradient(0f, horizon, 0f, sy, mix(bot, 0xFF1288A8.toInt(), .5f), mix(0xFF1AA6B8.toInt(), 0xFF05202C.toInt(), night * .7f), Shader.TileMode.CLAMP); c.drawRect(0f, horizon, w, sy, p); p.shader = null
                reflect(horizon, sy, 9)
                val foam = sy + sin(t * 2f * PI.toFloat()) * u * 4f
                run {
                    p.shader = LinearGradient(0f, foam, 0f, h, mix(tint(.8f, 0xFFE6CFA0.toInt(), .2f), Color.WHITE, .1f), tint(1f, 0xFFB99A64.toInt(), .2f), Shader.TileMode.CLAMP)
                    val sand = Path(); sand.moveTo(0f, h); sand.lineTo(0f, foam)
                    var x = 0f; while (x <= w) { sand.lineTo(x, foam + sin(x / w * 18f + t * 2f * PI.toFloat()) * u * 2.2f); x += w / 40f }
                    sand.lineTo(w, h); sand.close(); c.drawPath(sand, p); p.shader = null
                    val fp = Path(); x = 0f; var first = true
                    while (x <= w) { val y = foam + sin(x / w * 18f + t * 2f * PI.toFloat()) * u * 2.2f - u * 2f; if (first) { fp.moveTo(x, y); first = false } else fp.lineTo(x, y); x += w / 40f }
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * 2.2f; p.color = Color.WHITE; p.alpha = 190; c.drawPath(fp, p); p.style = Paint.Style.FILL
                }
                fun palm(px: Float, py: Float, lh: Float, lean: Float) {
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * 3.2f; p.strokeCap = Paint.Cap.ROUND; p.color = tint(.95f, 0xFF2A1E16.toInt()); p.alpha = 255
                    val trunk = Path(); trunk.moveTo(px, py); trunk.quadTo(px + lean * .2f, py - lh * .55f, px + lean, py - lh); c.drawPath(trunk, p); p.style = Paint.Style.FILL
                    val tx = px + lean; val ty = py - lh
                    for (k in 0 until 7) {
                        val a = -PI.toFloat() * .9f + k * (PI.toFloat() * .8f / 6f) + sin(t * 2f * PI.toFloat() + k) * .05f
                        c.save(); c.translate(tx, ty); c.rotate(a * 57.2958f); col(tint(.95f, 0xFF1F5A34.toInt(), .2f))
                        val leaf = Path(); leaf.moveTo(0f, 0f); leaf.quadTo(lh * .35f, -lh * .22f, lh * .7f, lh * .08f); leaf.quadTo(lh * .35f, -lh * .06f, 0f, u * 2f); c.drawPath(leaf, p); c.restore()
                    }
                }
                palm(w * .12f, h * .98f, h * .42f, w * .08f); palm(w * .27f, h, h * .34f, -w * .05f)
                run {
                    val ux = w * .74f; val uy = h * .88f
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * 2f; p.color = tint(.9f, 0xFF4A3626.toInt()); p.alpha = 255; c.drawLine(ux, uy + h * .08f, ux, uy - h * .12f, p); p.style = Paint.Style.FILL
                    for (k in 0 until 6) { col(if (k % 2 == 1) tint(.9f, 0xFFFFFFFF.toInt(), .2f) else tint(.9f, 0xFFE04A3A.toInt())); tri(ux, uy - h * .14f, ux - w * .12f + k * w * .04f, uy - h * .04f, ux - w * .12f + (k + 1) * w * .04f, uy - h * .04f) }
                }
            }
            "street1" -> {
                // a narrow lane of tall old houses, warm string lights across it and cobbles
                val gy = h * .82f
                val cols = intArrayOf(0xFFC9A27E.toInt(), 0xFFB98C6A.toInt(), 0xFFD9B890.toInt(), 0xFFA9805F.toInt(), 0xFFCFA78C.toInt())
                var x = -w * .02f; var k = 0
                while (x < w) {
                    val bw = w * (.17f + rnd(k, 3f) * .07f); val bh = h * (.46f + rnd(k + 9, 3f) * .12f); val body = tint(.78f, cols[k % 5], .2f)
                    col(body); rectF(x, gy - bh, x + bw, gy)
                    col(mix(body, Color.BLACK, .3f)); tri(x - u * 2f, gy - bh, x + bw / 2f, gy - bh - bw * .34f, x + bw + u * 2f, gy - bh)
                    for (r in 0 until 3) for (q in 0 until 2) {
                        val wx = x + bw * (.2f + q * .42f); val wy = gy - bh + bh * (.1f + r * .26f); val on = night > .15f && rnd(k * 9 + r * 3 + q, 5f) > .3f
                        if (on) col(0xFFFFD27A.toInt(), .95f) else col(mix(0xFF20304A.toInt(), bot, .4f)); rectF(wx, wy, wx + bw * .18f, wy + bh * .15f)
                        col(mix(body, Color.BLACK, .35f)); rectF(wx - u, wy + bh * .15f, wx + bw * .18f + u, wy + bh * .15f + u * 1.6f)
                    }
                    col(mix(body, Color.BLACK, .45f)); rectF(x + bw * .4f, gy - bh * .2f, x + bw * .6f, gy)
                    x += bw + u; k++
                }
                p.shader = LinearGradient(0f, gy, 0f, h, tint(.85f, 0xFF6A6460.toInt(), .4f), tint(1f, 0xFF3A3532.toInt(), .4f), Shader.TileMode.CLAMP); c.drawRect(0f, gy, w, h, p); p.shader = null
                col(Color.WHITE, .07f)
                for (r in 0 until 5) for (q in 0 until 22) oval(((q + (r % 2) * .5f) / 22f) * w, gy + h * .025f + r * h * .032f, w * .016f, h * .009f)
                for (s2 in 0 until 3) {
                    val y0 = h * (.22f + s2 * .1f)
                    p.style = Paint.Style.STROKE; p.strokeWidth = u; p.color = tint(.9f, 0xFF22201E.toInt()); p.alpha = 255
                    val rope = Path(); rope.moveTo(0f, y0); rope.quadTo(w * .5f, y0 + h * .14f, w, y0); c.drawPath(rope, p); p.style = Paint.Style.FILL
                    for (b in 0 until 12) {
                        val tb = b / 11f; val bx = tb * w; val by = (1 - tb) * (1 - tb) * y0 + 2f * (1 - tb) * tb * (y0 + h * .07f) + tb * tb * y0
                        val tw2 = .6f + .4f * sin((t * 3f + b * .7f + s2) * 2f * PI.toFloat())
                        col(0xFFFFD98A.toInt(), ((.4f + .6f * (night + .25f).coerceIn(0f, 1f)) * tw2 * .55f).coerceIn(0f, 1f)); circ(bx, by + u * 3f, u * 7f)
                        col(0xFFFFF1C2.toInt()); circ(bx, by + u * 3f, u * 1.8f)
                    }
                }
            }
            "street2" -> {
                // a boulevard of shops: striped awnings, lit windows, round trees and lamps
                val gy = h * .82f
                val aw = intArrayOf(0xFFD9453A.toInt(), 0xFF2F7ED8.toInt(), 0xFF2FA86B.toInt(), 0xFFE6A323.toInt())
                val bodies = intArrayOf(0xFFDDD3C4.toInt(), 0xFFC9D4DE.toInt(), 0xFFE2CDB6.toInt(), 0xFFCFC7DC.toInt())
                val lit = night > .12f || sky == Sky.RAIN || sky == Sky.STORM
                var x = -w * .02f; var k = 0
                while (x < w) {
                    val bw = w * (.22f + rnd(k, 3f) * .06f); val bh = h * (.36f + rnd(k + 9, 3f) * .1f); val body = tint(.75f, bodies[k % 4], .2f)
                    col(body); rectF(x, gy - bh, x + bw, gy); col(mix(body, Color.BLACK, .25f)); rectF(x - u * 2f, gy - bh - u * 4f, x + bw + u * 2f, gy - bh + u)
                    for (q in 0 until 3) { val wx = x + bw * (.1f + q * .3f); if (lit) col(0xFFFFD27A.toInt(), .9f) else col(mix(0xFF20304A.toInt(), bot, .4f)); rectF(wx, gy - bh * .9f, wx + bw * .2f, gy - bh * .9f + bh * .22f) }
                    val ay = gy - bh * .5f; val ac = aw[k % 4]
                    for (q in 0 until 8) { col(tint(.8f, if (q % 2 == 1) 0xFFFFFFFF.toInt() else ac, .2f)); rectF(x + q * bw / 8f, ay, x + q * bw / 8f + bw / 8f + .5f, ay + bh * .12f) }
                    if (lit) col(0xFFFFD27A.toInt(), .95f) else col(mix(0xFF20304A.toInt(), bot, .4f)); rectF(x + bw * .12f, gy - bh * .35f, x + bw * .88f, gy)
                    col(mix(body, Color.BLACK, .4f)); rectF(x + bw * .45f, gy - bh * .35f, x + bw * .45f + u * 2f, gy)
                    x += bw + u; k++
                }
                col(tint(.85f, 0xFF7A7570.toInt(), .4f)); rectF(0f, gy, w, gy + h * .05f); col(tint(.95f, 0xFF3A3E4A.toInt(), .5f)); rectF(0f, gy + h * .05f, w, h)
                col(0xFFF2E9C8.toInt(), .8f); for (q in 0 until 5) rectF(w * (.04f + q * .21f), h * .93f, w * (.15f + q * .21f), h * .93f + u * 3f)
                for (tx in floatArrayOf(w * .18f, w * .52f, w * .86f)) {
                    col(tint(.9f, 0xFF3A2A1E.toInt())); rectF(tx - u * 2f, gy - h * .17f, tx + u * 2f, gy + h * .03f)
                    col(tint(.9f, 0xFF2D6A34.toInt(), .4f))
                    for (b in arrayOf(floatArrayOf(-.8f, 0f, 1f), floatArrayOf(.8f, 0f, 1f), floatArrayOf(0f, -.9f, 1.1f))) circ(tx + b[0] * u * 12f + sin(t * 2f * PI.toFloat() + tx) * u * .8f, gy - h * .2f + b[1] * u * 10f, u * b[2] * 12f)
                }
                for (lx in floatArrayOf(w * .07f, w * .35f, w * .69f, w * .95f)) {
                    col(tint(.95f, 0xFF161922.toInt())); ln(lx, gy + h * .03f, lx, gy - h * .26f, u * 2f)
                    if (lit) glow(lx, gy - h * .27f, h * .18f, 0xFFFFD98A.toInt(), 178)
                    col(0xFFFFF1C2.toInt()); circ(lx, gy - h * .27f, u * 3f)
                }
            }
            "plateau1" -> {
                // a meadow with grazing sheep, a rail fence and a shepherd's hut
                peaks(horizon, h * .26f, .9f, 2.6f, tint(.2f, 0xFF6A7C96.toInt(), .4f), true)
                ridge(horizon + h * .04f, h * .14f, 1.1f, 8.2f, tint(.45f, 0xFF5C9A52.toInt(), 1f))
                ridge(horizon + h * .11f, h * .12f, .9f, 3.9f, tint(.7f, 0xFF4A8A44.toInt(), 1f))
                run { val hx = w * .2f; val hy = horizon + h * .10f; val s2 = h * .04f; col(tint(.6f, 0xFF7A5A3C.toInt())); rectF(hx - s2, hy - s2 * .8f, hx + s2, hy); col(tint(.55f, 0xFF3A2A24.toInt(), 1f)); tri(hx - s2 * 1.3f, hy - s2 * .8f, hx, hy - s2 * 1.6f, hx + s2 * 1.3f, hy - s2 * .8f) }
                ridge(h * 1.01f, h * .22f, .8f, 6.6f, tint(1f, 0xFF3B7A3A.toInt(), 1f))
                fun sheep(sx: Float, sy: Float, ss: Float, ph: Float) {
                    val bob = sin((t + ph) * 2f * PI.toFloat()) * ss * .03f
                    col(tint(.9f, 0xFF2A2724.toInt())); rectF(sx - ss * .5f, sy, sx - ss * .42f, sy + ss * .34f); rectF(sx + ss * .4f, sy, sx + ss * .48f, sy + ss * .34f)
                    col(tint(.9f, 0xFFF2EFE8.toInt(), .2f))
                    for (b in arrayOf(floatArrayOf(-.4f, -.1f, .34f), floatArrayOf(0f, -.18f, .4f), floatArrayOf(.4f, -.1f, .34f), floatArrayOf(-.15f, .05f, .36f), floatArrayOf(.25f, .05f, .36f))) circ(sx + b[0] * ss, sy + b[1] * ss, b[2] * ss * .8f)
                    col(tint(.9f, 0xFF2A2724.toInt())); oval(sx + ss * .72f, sy - ss * .02f + bob * 3f, ss * .2f, ss * .15f)
                }
                arrayOf(floatArrayOf(.52f, .86f, .15f, 0f), floatArrayOf(.66f, .80f, .11f, .3f), floatArrayOf(.80f, .88f, .16f, .6f), floatArrayOf(.36f, .82f, .10f, .15f), floatArrayOf(.9f, .80f, .09f, .45f), floatArrayOf(.2f, .9f, .17f, .75f)).forEach { sheep(w * it[0], h * it[1], h * it[2] * .7f, it[3]) }
                col(tint(.95f, 0xFF6A4A2E.toInt()))
                for (k in 0 until 14) rectF(w * (k / 13f), h * .80f + sin(k.toFloat()) * u, w * (k / 13f) + u * 2.6f, h * .88f)
                rectF(0f, h * .82f, w, h * .82f + u * 2f); rectF(0f, h * .855f, w, h * .855f + u * 2f)
            }
            "plateau2" -> {
                // a camp on the highland: two tents, a campfire that flickers and a sky of stars
                peaks(horizon, h * .28f, .9f, 1.3f, tint(.2f, 0xFF6A7C96.toInt(), .4f), true)
                ridge(horizon + h * .05f, h * .14f, 1.1f, 5.2f, tint(.5f, 0xFF4F8A4A.toInt(), 1f))
                ridge(horizon + h * .12f, h * .12f, .9f, 2.1f, tint(.75f, 0xFF3E7A3E.toInt(), 1f))
                ridge(h * 1.01f, h * .24f, .8f, 6.6f, tint(1f, 0xFF2F6A34.toInt(), 1f))
                fun tent(tx: Float, ty: Float, tw2: Float, cc: Int) {
                    col(tint(.9f, cc, .2f)); tri(tx, ty - tw2 * .85f, tx - tw2 * .62f, ty, tx + tw2 * .62f, ty)
                    col(mix(tint(.9f, cc, .2f), Color.BLACK, .45f)); tri(tx, ty - tw2 * .85f, tx - tw2 * .16f, ty, tx + tw2 * .16f, ty)
                    if (night > .15f) { col(0xFFFFB347.toInt(), .55f * night.coerceIn(0f, 1f)); tri(tx, ty - tw2 * .7f, tx - tw2 * .14f, ty, tx + tw2 * .14f, ty) }
                }
                tent(w * .30f, h * .87f, h * .30f, 0xFFE0583A.toInt()); tent(w * .50f, h * .84f, h * .22f, 0xFF3A7DD0.toInt())
                run {
                    val fx = w * .72f; val fy = h * .90f
                    val fl = .7f + .3f * sin(t * 12f * PI.toFloat()) + .15f * sin(t * 22f * PI.toFloat())
                    glow(fx, fy - u * 8f, h * .32f, 0xFFFFA040.toInt(), (255 * .5f * (.4f + night.coerceIn(0f, 1f) * .8f) * fl).toInt().coerceIn(0, 255))
                    col(tint(.95f, 0xFF2A1E16.toInt()))
                    c.save(); c.translate(fx, fy); c.rotate(20f); rectF(-u * 16f, -u * 2f, u * 16f, u * 3f); c.restore(); c.save(); c.translate(fx, fy); c.rotate(-20f); rectF(-u * 16f, -u * 2f, u * 16f, u * 3f); c.restore()
                    for (k in 0 until 3) {
                        val hf = (if (k == 1) 1f else .7f) * fl; col(if (k == 1) 0xFFFFD35A.toInt() else 0xFFFF8A2A.toInt(), .95f)
                        val fp = Path(); fp.moveTo(fx + (k - 1) * u * 5f - u * 4f, fy - u * 2f); fp.quadTo(fx + (k - 1) * u * 5f, fy - u * 26f * hf, fx + (k - 1) * u * 5f + u * 4f, fy - u * 2f); fp.close(); c.drawPath(fp, p)
                    }
                    for (k in 0 until 6) { val ph = frac(t * 2f + k / 6f); col(0xFFFFC060.toInt(), (1f - ph) * .9f); circ(fx + sin(ph * 8f + k) * u * 8f, fy - u * 10f - ph * h * .18f, u * 1.2f) }
                }
            }
            "mosque0" -> {
                // a mosque on a hill above the houses of its town, cypresses in front
                ridge(horizon, h * .08f, 1.1f, 2.4f, tint(.22f, 0xFF4C566E.toInt(), .1f))
                val hy = horizon - h * .03f
                col(tint(.5f, 0xFF394560.toInt(), .3f))
                run { val hill = Path(); hill.moveTo(0f, h); hill.lineTo(0f, hy + h * .18f); hill.quadTo(w * .28f, hy + h * .02f, w * .5f, hy); hill.quadTo(w * .74f, hy + h * .02f, w, hy + h * .2f); hill.lineTo(w, h); hill.close(); c.drawPath(hill, p) }
                for (k in 0 until 26) { val hx = w * (.02f + k * .037f); val slope = abs(hx / w - .5f) * h * .2f + h * .04f; house(hx, hy + slope + h * .05f, w * .03f, h * .035f, tint(.7f, 0xFFE3D2B8.toInt(), .2f), tint(.6f, 0xFFB5533C.toInt()), snow, k) }
                mosq(w * .5f, hy + h * .01f, h * .2f, tint(.62f, 0xFFCFC7B8.toInt(), .15f), 2)
                for (x in floatArrayOf(w * .06f, w * .14f, w * .9f, w * .96f)) cypress(x, h, h * .3f, tint(1f, 0xFF10241C.toInt(), .5f))
                col(tint(1f, 0xFF1A1F2A.toInt(), .4f)); rectF(0f, h * .95f, w, h)
            }
            "mosque1" -> {
                // a grand mosque of cascading domes and six minarets, mirrored in the pool before it
                ridge(horizon - h * .01f, h * .06f, 1.0f, 3.3f, tint(.22f, 0xFF4C566E.toInt(), .1f))
                val by = horizon + h * .04f; val sz = h * .25f; val mc = tint(.58f, 0xFFD8CFC0.toInt(), .15f)
                col(mc); for (sd in intArrayOf(-1, 1)) dome(w * .5f + sd * sz * 1.1f, by - sz * .3f, sz * .2f)
                mosq(w * .5f, by, sz, mc, 6)
                col(tint(.7f, 0xFF3A4256.toInt(), .2f)); rectF(0f, by, w, by + h * .02f)
                run {
                    val py = by + h * .03f
                    waterFill(py, mix(bot, 0xFF2A5F86.toInt(), .35f), mix(0xFF123A5A.toInt(), 0xFF04141F.toInt(), night * .6f + .2f))
                    c.saveLayerAlpha(0f, py, w, h, 102); c.translate(0f, py); c.scale(1f, -.8f); c.translate(0f, -py); mosq(w * .5f, by, sz, mc, 6); c.restore()
                    for (k in 0 until 5) { val y = py + (k + .7f) * (h - py) / 6f; col(Color.WHITE, .18f); ln(w * (.1f + frac(k * .37f) * .5f), y + sin((t + k * .1f) * 2f * PI.toFloat()) * u * .7f, w * (.3f + frac(k * .37f) * .6f), y, u * 1.1f) }
                }
                for (x in floatArrayOf(w * .05f, w * .13f, w * .87f, w * .95f)) cypress(x, h, h * .32f, tint(1f, 0xFF10241C.toInt(), .5f))
                for (k in 0 until 4) { val px = frac(drift * 2f + k * .27f) * w; val py2 = h * (.88f + rnd(k, 2f) * .06f); col(tint(.9f, 0xFFCCCCCC.toInt(), .2f)); oval(px, py2, u * 2.2f, u * 1.4f) }
            }
            "mosque2" -> {
                // a small village mosque in a valley with houses, poplars and a thread of smoke
                peaks(horizon, h * .30f, .9f, 2.0f, tint(.2f, 0xFF6A7C96.toInt(), .4f), true)
                ridge(horizon + h * .05f, h * .12f, 1.2f, 4.5f, tint(.45f, 0xFF4F8A4A.toInt(), 1f))
                ridge(horizon + h * .12f, h * .10f, 1.0f, 1.1f, tint(.7f, 0xFF3E7A3E.toInt(), 1f))
                for (k in 0 until 7) house(w * (.08f + k * .12f) + rnd(k, 1f) * w * .03f, horizon + h * .16f + rnd(k, 2f) * h * .04f, w * .06f, h * .05f, tint(.75f, 0xFFE3D2B8.toInt(), .2f), tint(.65f, 0xFFB5533C.toInt()), snow, k)
                mosq(w * .5f, horizon + h * .2f, h * .1f, tint(.8f, 0xFFDCD0C0.toInt(), .15f), 1)
                for (k in 0 until 4) { val ph = frac(t + k * .25f); col(mix(Color.WHITE, bot, .3f), .4f * (1f - ph)); circ(w * .2f + ph * u * 10f, horizon + h * .12f - ph * h * .1f, u * (2f + ph * 4f)) }
                ridge(h * 1.01f, h * .18f, .8f, 6.6f, tint(1f, 0xFF265A2E.toInt(), 1f))
                for (x in floatArrayOf(w * .1f, w * .2f, w * .78f, w * .9f)) cypress(x, h * .98f, h * .3f, tint(1f, 0xFF1D3A22.toInt(), .6f))
            }
            "mosque3" -> {
                // a mosque on the shore with two minarets, its lights on the water, a far bridge and a boat
                ridge(horizon, h * .05f, 1.2f, 2.6f, tint(.25f, 0xFF4C566E.toInt(), .1f))
                run {
                    val bx = horizon + h * .01f; val bc = tint(.35f, 0xFF3A4560.toInt(), .1f)
                    col(bc); rectF(0f, bx, w, bx + u * 2.4f); for (tx in floatArrayOf(w * .15f, w * .85f)) rectF(tx - u * 2f, bx - h * .1f, tx + u * 2f, bx)
                    p.style = Paint.Style.STROKE; p.strokeWidth = u; p.color = bc; p.alpha = 255
                    val cab = Path(); cab.moveTo(w * .15f, bx - h * .1f); cab.quadTo(w * .5f, bx + h * .02f, w * .85f, bx - h * .1f); c.drawPath(cab, p); p.style = Paint.Style.FILL
                    if (night > .1f) { var x = 0f; while (x < w) { col(0xFFFFE3A0.toInt(), (night * .8f).coerceIn(0f, 1f)); circ(x, bx, u * .9f); x += u * 9f } }
                }
                val sy = horizon + h * .12f
                waterFill(horizon, mix(bot, 0xFF0B4A72.toInt(), .35f), mix(0xFF0A3558.toInt(), 0xFF031829.toInt(), night * .6f + .2f))
                reflect(horizon, h, 10)
                val mc = tint(.55f, 0xFFD8CFC0.toInt(), .15f); val mx = w * .3f; val sz = h * .2f
                col(tint(.6f, 0xFF3A4256.toInt(), .2f)); rectF(0f, sy, w * .62f, sy + h * .03f)
                mosq(mx, sy, sz, mc, 2)
                c.saveLayerAlpha(0f, sy + h * .03f, w, h, 89); c.translate(0f, sy + h * .03f); c.scale(1f, -.7f); c.translate(0f, -sy - h * .03f); mosq(mx, sy, sz, mc, 2); c.restore()
                run {
                    val bx2 = w * .7f + sin(t * 4f * PI.toFloat()) * u * 1.2f; val by2 = horizon + h * .2f
                    col(tint(.9f, 0xFF10202C.toInt())); quadP(bx2 - u * 14f, by2, bx2 + u * 14f, by2, bx2 + u * 9f, by2 + u * 6f, bx2 - u * 9f, by2 + u * 6f)
                    col(mix(Color.WHITE, bot, .35f)); tri(bx2, by2 - u * 20f, bx2 + u * 10f, by2 - u * 2f, bx2, by2 - u * 2f)
                }
                ridge(h * .99f, h * .10f, .9f, 5.5f, tint(1f, 0xFF0C1620.toInt(), .8f))
            }
            "village0" -> {
                // a village under snow (or, without snow, in its autumn colours): gabled houses, chimney smoke, pines
                peaks(horizon, h * .34f, .8f, 3.4f, tint(.2f, 0xFF6A7C96.toInt(), .4f), true)
                ridge(horizon + h * .05f, h * .12f, 1.1f, 6f, tint(.5f, 0xFF5C8A52.toInt(), 1f))
                ridge(horizon + h * .12f, h * .1f, .9f, 2f, tint(.75f, 0xFF4A7A44.toInt(), 1f))
                val cols = intArrayOf(0xFFC9A27E.toInt(), 0xFFB98C6A.toInt(), 0xFFD9B890.toInt(), 0xFFA9805F.toInt())
                for (k in 0 until 9) {
                    val hx = w * (.02f + k * .11f) + rnd(k, 5f) * w * .02f; val hb = horizon + h * (.16f + rnd(k, 7f) * .08f); val bw = w * (.095f + rnd(k, 9f) * .03f)
                    house(hx, hb, bw, bw * .7f, tint(.8f, cols[k % 4], .2f), tint(.7f, 0xFF6A3A2A.toInt()), snow, k)
                    for (q in 0 until 2) { val ph = frac(t + q * .5f + k * .13f); col(mix(Color.WHITE, bot, .3f), .38f * (1f - ph)); circ(hx + bw * .75f + ph * u * 8f, hb - bw * .7f - bw * .5f - ph * h * .08f, u * (1.6f + ph * 3.4f)) }
                }
                ridge(h * 1.01f, h * .16f, .8f, 6.6f, tint(1f, 0xFF2F6A34.toInt(), 1f))
                for (k in 0 until 6) pine(w * (if (k < 3) k * .05f + .01f else .8f + (k - 3) * .06f), h * 1.01f, h * (.2f + rnd(k, 5f) * .06f), tint(1f, 0xFF10241C.toInt(), .7f))
            }
            "village1" -> {
                // a hillside village in terraces: houses stepped up the slope, warm lights, green hills behind
                peaks(horizon, h * .28f, .9f, 1.7f, tint(.2f, 0xFF6A7C96.toInt(), .4f), true)
                ridge(horizon + h * .03f, h * .12f, 1.0f, 3.3f, tint(.4f, 0xFF5C9A52.toInt(), 1f))
                fun slope(x: Float) = horizon + h * .42f - (x / w) * h * .34f
                col(tint(.7f, 0xFF4A8A44.toInt(), 1f))
                run { val hl = Path(); hl.moveTo(0f, h); var x = 0f; while (x <= w) { hl.lineTo(x, slope(x) + h * .03f + sin(x / w * 14f) * u * 2f); x += w / 40f }; hl.lineTo(w, h); hl.close(); c.drawPath(hl, p) }
                val cols2 = intArrayOf(0xFFE3D2B8.toInt(), 0xFFCFA77E.toInt(), 0xFFD9B890.toInt(), 0xFFC9A27E.toInt(), 0xFFE9DCC4.toInt())
                for (r in 0 until 4) for (k in 0 until 9) {
                    val hx = w * (.02f + k * .115f) + r * w * .03f - (r % 2) * w * .02f; val hb = slope(hx) + r * h * .075f + h * .05f
                    if (hb > h) continue
                    house(hx, hb, w * .07f, h * .06f, tint(.7f + r * .08f, cols2[(k + r) % 5], .2f), tint(.6f + r * .08f, 0xFFB5533C.toInt()), snow, k * 4 + r)
                }
                col(tint(1f, 0xFF1A2A1E.toInt(), .5f)); rectF(0f, h * .97f, w, h)
            }
            "village2" -> {
                // farmland with a windmill: crop stripes in perspective, a barn and haystacks
                ridge(horizon, h * .10f, 1.0f, 2.9f, tint(.25f, 0xFF6A7C96.toInt(), .4f))
                val crops = intArrayOf(0xFF7A9A3E.toInt(), 0xFFC9B24A.toInt(), 0xFF5A8A3A.toInt(), 0xFFB59A3A.toInt())
                for (k in 0 until 9) {
                    val y0 = horizon + h * .02f + Math.pow((k / 9f).toDouble(), 1.7).toFloat() * (h - horizon); val y1 = horizon + h * .02f + Math.pow(((k + 1) / 9f).toDouble(), 1.7).toFloat() * (h - horizon)
                    col(tint(.4f + k * .07f, crops[k % 4], .8f)); rectF(0f, y0, w, y1 + 1f)
                }
                run {
                    val mx = w * .68f; val by = horizon + h * .12f; val mh = h * .3f
                    col(tint(.7f, 0xFFDCD0C0.toInt(), .2f)); quadP(mx - u * 12f, by, mx + u * 12f, by, mx + u * 7f, by - mh, mx - u * 7f, by - mh)
                    col(tint(.6f, 0xFF8A4A3A.toInt())); tri(mx - u * 10f, by - mh, mx, by - mh - u * 14f, mx + u * 10f, by - mh)
                    c.save(); c.translate(mx, by - mh); c.rotate(t * 360f)
                    for (k in 0 until 4) { c.rotate(90f); col(tint(.8f, 0xFFF1EAD8.toInt(), .2f)); rectF(-u * 1.4f, 0f, u * 1.4f, mh * .9f); col(tint(.8f, 0xFFE6DCC0.toInt(), .2f)); rectF(u * 1.4f, mh * .2f, u * 10.4f, mh * .7f) }
                    c.restore()
                }
                house(w * .18f, horizon + h * .2f, w * .14f, h * .09f, tint(.75f, 0xFFB5533C.toInt(), .3f), tint(.6f, 0xFF6A3A2A.toInt()), false, 3)
                for (k in 0 until 5) { val hx = w * (.35f + k * .07f); val hy = h * (.86f + rnd(k, 4f) * .06f); col(tint(.9f, 0xFFD9B24A.toInt(), .3f)); dome(hx, hy, u * 11f); rectF(hx - u * 11f, hy, hx + u * 11f, hy + u * 6f) }
            }
            "desert0" -> {
                // dunes in the low sun and a caravan of camels along the crest
                ridge(horizon, h * .10f, 1.0f, 2.1f, tint(.35f, 0xFFC9966A.toInt(), .6f))
                ridge(horizon + h * .06f, h * .16f, .9f, 4.4f, tint(.55f, 0xFFD9A070.toInt(), .7f))
                fun crest(x: Float) = horizon + h * .15f - sin(x / w * 5f + 1.2f) * h * .035f
                ridge(horizon + h * .16f, h * .12f, .7f, .6f, tint(.8f, 0xFFB8764A.toInt(), .7f))
                ridge(h * 1.01f, h * .2f, .8f, 6.6f, tint(1f, 0xFF9A5A3A.toInt(), .6f))
                fun camel(x: Float, y: Float, s2: Float, ph: Float) {
                    val lg = sin((t * 4f + ph) * 2f * PI.toFloat()) * s2 * .08f; val cc = tint(.95f, 0xFF4A2E1E.toInt())
                    col(cc); oval(x, y - s2 * .5f, s2 * .5f, s2 * .22f); circ(x - s2 * .12f, y - s2 * .78f, s2 * .16f); circ(x + s2 * .18f, y - s2 * .74f, s2 * .14f)
                    p.style = Paint.Style.STROKE; p.strokeWidth = s2 * .07f; p.strokeCap = Paint.Cap.ROUND; p.color = cc; p.alpha = 255
                    val neck = Path(); neck.moveTo(x + s2 * .38f, y - s2 * .58f); neck.quadTo(x + s2 * .6f, y - s2 * .8f, x + s2 * .66f, y - s2 * 1.1f); c.drawPath(neck, p)
                    for (lgp in arrayOf(floatArrayOf(-.3f, lg), floatArrayOf(-.15f, -lg), floatArrayOf(.2f, lg), floatArrayOf(.35f, -lg))) c.drawLine(x + lgp[0] * s2, y - s2 * .32f, x + lgp[0] * s2 + lgp[1], y, p)
                    p.style = Paint.Style.FILL; col(cc); oval(x + s2 * .7f, y - s2 * 1.12f, s2 * .12f, s2 * .07f)
                }
                for (k in 0 until 4) { val cx0 = frac(drift * 3f + k * .1f) * (w + h * .8f) - h * .4f; camel(cx0, crest(cx0) + h * .02f, h * .12f - k * .004f, k * .25f) }
            }
            "desert1" -> {
                // an oasis: a small pond with palms among the dunes
                ridge(horizon, h * .08f, 1.0f, 2.5f, tint(.2f, 0xFFC9966A.toInt(), .4f))
                ridge(horizon + h * .07f, h * .13f, .9f, 5.0f, tint(.42f, 0xFFD9A878.toInt(), .5f))
                val py = horizon + h * .1f
                col(tint(.8f, 0xFFC98E5A.toInt(), .5f)); rectF(0f, py - h * .02f, w, h)
                p.shader = LinearGradient(0f, py, 0f, py + h * .12f, mix(bot, 0xFF1F8FA8.toInt(), .5f), mix(0xFF16758A.toInt(), 0xFF05202C.toInt(), night * .7f), Shader.TileMode.CLAMP); oval(w * .5f, py + h * .07f, w * .34f, h * .075f); p.shader = null
                for (k in 0 until 4) { val y = py + h * .035f + k * h * .03f; col(Color.WHITE, .3f); ln(w * (.28f + frac(k * .37f) * .1f), y + sin((t + k * .2f) * 2f * PI.toFloat()) * u * .5f, w * (.5f + frac(k * .37f) * .2f), y, u * 1.1f) }
                palm(w * .2f, py + h * .12f, h * .26f, w * .03f); palm(w * .28f, py + h * .14f, h * .2f, -w * .02f); palm(w * .8f, py + h * .12f, h * .24f, -w * .04f)
                ridge(h * 1.01f, h * .06f, .8f, 6.6f, tint(1f, 0xFFB8764A.toInt(), .4f))
            }
            "desert2" -> {
                // a red canyon: layered mesas with strata, lit from the side
                fun mesa(x: Float, base: Float, bw: Float, bh: Float, cc: Int, d: Float) {
                    val tp = bw * .16f
                    p.shader = LinearGradient(0f, base - bh, 0f, base, tint(d, mix(cc, 0xFFFFE0B0.toInt(), .18f), .3f), mix(tint(d, cc, .3f), bot, .25f), Shader.TileMode.CLAMP)
                    quadP(x - bw / 2f, base, x - bw / 2f + tp, base - bh, x + bw / 2f - tp, base - bh, x + bw / 2f, base); p.shader = null
                    col(0xFF3C140A.toInt(), .18f)
                    for (k in 1 until 6) { val y = base - bh * k / 6f; ln(x - bw / 2f + tp * (1f - k / 6f), y, x + bw / 2f - tp * (1f - k / 6f), y, u) }
                }
                for (k in 0 until 6) mesa(w * (k * .2f), horizon, w * (.22f + rnd(k, 1f) * .1f), h * (.22f + rnd(k, 2f) * .12f), 0xFF9A4A32.toInt(), .3f)
                for (k in 0 until 5) mesa(w * (.1f + k * .22f), horizon + h * .06f, w * (.2f + rnd(k, 3f) * .1f), h * (.16f + rnd(k, 4f) * .1f), 0xFFB5573A.toInt(), .5f)
                for (k in 0 until 4) mesa(w * (.02f + k * .3f), horizon + h * .12f, w * (.22f + rnd(k, 5f) * .12f), h * (.12f + rnd(k, 6f) * .08f), 0xFFC96A42.toInt(), .7f)
                col(tint(.85f, 0xFFA0563A.toInt(), .4f)); rectF(0f, horizon + h * .12f, w, h)
                ridge(h * 1.01f, h * .16f, .8f, 6.6f, tint(1f, 0xFF8A4A32.toInt(), .4f))
                run {
                    val bx = frac(drift * 3f) * (w + 40f * u) - 20f * u; val by = h * .3f + sin(drift * 30f) * u * 5f; val fl = sin(t * 8f * PI.toFloat()) * u * 2f
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * 1.3f; p.color = 0xB31E1414.toInt()
                    val wing = Path(); wing.moveTo(bx - u * 6f, by - u * 2f + fl); wing.quadTo(bx - u * 2f, by - u * 3f, bx, by); wing.quadTo(bx + u * 2f, by - u * 3f, bx + u * 6f, by - u * 2f + fl); c.drawPath(wing, p); p.style = Paint.Style.FILL
                }
            }
            "everest0" -> {
                // the roof of the world: a great snowy peak with snow blowing off its summit, prayer flags and base camp tents
                peaks(horizon - h * .02f, h * .30f, 1.3f, 2.2f, tint(.2f, 0xFF5A6A88.toInt(), .4f), true)
                run {
                    // the massif: a jagged outline with shoulders and a summit, a snow cap with a ragged edge, and the right faces in shadow
                    val pts = floatArrayOf(.02f, .04f, .12f, -.10f, .20f, -.16f, .27f, -.14f, .34f, -.27f, .40f, -.33f, .45f, -.42f, .49f, -.48f, .52f, -.52f, .56f, -.47f, .60f, -.43f, .64f, -.36f, .68f, -.38f, .73f, -.28f, .80f, -.22f, .88f, -.12f, .98f, .04f)
                    fun at(k: Int) = w * pts[k * 2] to horizon + h * pts[k * 2 + 1]
                    val n = pts.size / 2
                    val m = Path(); at(0).let { m.moveTo(it.first, it.second) }; for (k in 1 until n) at(k).let { m.lineTo(it.first, it.second) }; m.close()
                    val rock = tint(.4f, 0xFF4A5670.toInt(), .3f)
                    p.style = Paint.Style.FILL; p.alpha = 255
                    p.shader = LinearGradient(0f, horizon - h * .52f, 0f, horizon + h * .04f, mix(rock, Color.WHITE, .08f), mix(rock, bot, .25f), Shader.TileMode.CLAMP); c.drawPath(m, p); p.shader = null
                    // the snow above a ragged line
                    c.save()
                    val snowLine = Path(); snowLine.moveTo(0f, 0f); var x = 0f
                    while (x <= w) { snowLine.lineTo(x, horizon - h * (.29f + .035f * noise(x / w * 18f, 3f))); x += w / 48f }
                    snowLine.lineTo(w, 0f); snowLine.close(); c.clipPath(snowLine)
                    col(mix(Color.WHITE, bot, .18f)); c.drawPath(m, p)
                    c.restore()
                    // the faces turned away from the light: from the summit down its right side
                    val sh = Path(); at(8).let { sh.moveTo(it.first, it.second) }; for (k in 9 until n) at(k).let { sh.lineTo(it.first, it.second) }
                    sh.lineTo(w * .66f, horizon + h * .04f); sh.lineTo(w * .58f, horizon - h * .18f); sh.lineTo(w * .54f, horizon - h * .36f); sh.close()
                    col(tint(.6f, 0xFF1E2638.toInt(), .2f), .38f); c.drawPath(sh, p)
                    // snow blowing off the summit
                    val (px, py) = at(8)
                    for (k in 0 until 14) { val ph = frac(t + k / 14f); col(Color.WHITE, .5f * (1f - ph)); circ(px + ph * w * .35f, py + sin(ph * 5f + k) * u * 4f - ph * h * .02f, u * (1.5f + ph * 5f)) }
                }
                ridge(horizon + h * .08f, h * .08f, 1.2f, 4f, tint(.75f, 0xFFD8E0EA.toInt(), 1f))
                col(tint(.85f, 0xFFE8EEF4.toInt(), 1f)); rectF(0f, h * .86f, w, h)
                run {
                    val cols = intArrayOf(0xFF2F6FD8.toInt(), 0xFFF2F2F2.toInt(), 0xFFE04A3A.toInt(), 0xFF2FA86B.toInt(), 0xFFF2C02E.toInt())
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * .8f; p.color = 0xB33C3228.toInt()
                    val rope = Path(); rope.moveTo(0f, h * .66f); rope.quadTo(w * .5f, h * .76f, w, h * .64f); c.drawPath(rope, p); p.style = Paint.Style.FILL
                    for (k in 0 until 18) {
                        val tq = (k + .5f) / 18f; val x = tq * w; val y = (1 - tq) * (1 - tq) * h * .66f + 2f * (1 - tq) * tq * h * .76f + tq * tq * h * .64f; val fl = sin(t * 6f * PI.toFloat() + k) * u * 2f
                        col(cols[k % 5], .95f); tri(x - u * 4f, y, x + u * 4f, y, x + fl, y + u * 9f)
                    }
                }
                for ((x, cc) in listOf(w * .22f to 0xFFE0583A.toInt(), w * .32f to 0xFFF2C02E.toInt(), w * .74f to 0xFF3A7DD0.toInt())) { col(tint(.9f, cc, .2f)); tri(x, h * .86f - u * 14f, x - u * 12f, h * .87f, x + u * 12f, h * .87f) }
            }
            "jungle0" -> {
                // a jungle: a waterfall from a green cliff, deep canopy layers, mist and a parrot crossing
                val greens = intArrayOf(0xFF2E6B3A.toInt(), 0xFF24592F.toInt(), 0xFF1B4A25.toInt(), 0xFF123A1C.toInt())
                for (k in 0 until 14) { col(tint(.25f, greens[0], .3f)); circ(w * (k / 13f), horizon + rnd(k, 1f) * h * .04f, h * (.08f + rnd(k, 2f) * .05f)) }
                run {
                    // a waterfall: two rock walls with a gap, a narrow fall that widens a little as it drops, short dense streaks, a foaming pool at its foot
                    val cx = w * .5f; val fw = w * .07f; val top = horizon - h * .10f; val pool = horizon + h * .07f; val foot = pool + h * .05f
                    col(tint(.5f, 0xFF3F5A44.toInt(), .3f)); quadP(w * .26f, foot, w * .28f, top - h * .01f, cx - fw * .7f, top, cx - fw * .85f, foot)
                    quadP(cx + fw * .85f, foot, cx + fw * .7f, top, w * .74f, top - h * .01f, w * .76f, foot)
                    col(tint(.7f, 0xFF6E8F6A.toInt(), .3f), .8f); rectF(w * .28f, top - h * .012f, cx - fw * .7f, top); rectF(cx + fw * .7f, top - h * .012f, w * .74f, top)
                    val fall = Path(); fall.moveTo(cx - fw * .35f, top); fall.lineTo(cx + fw * .35f, top); fall.lineTo(cx + fw * .5f, pool); fall.lineTo(cx - fw * .5f, pool); fall.close()
                    p.shader = LinearGradient(cx - fw / 2f, 0f, cx + fw / 2f, 0f, intArrayOf(0xCCDCF0FF.toInt(), 0xFFFFFFFF.toInt(), 0xCCDCF0FF.toInt()), null, Shader.TileMode.CLAMP); c.drawPath(fall, p); p.shader = null
                    for (k in 0 until 16) { val x = cx + (rnd(k, 5f) - .5f) * fw * .8f; val y = top + frac(t * 2.5f + rnd(k, 6f)) * (pool - top); col(0xFFB4D2E6.toInt(), .6f); ln(x, y, x, minOf(pool, y + h * .022f), u * .8f) }
                    col(0xFFE8F6FF.toInt(), .55f); oval(cx, pool, fw * 1.3f, h * .012f)
                    for (k in 0 until 8) { val ph = frac(t * 1.2f + k / 8f); col(Color.WHITE, .5f * (1f - ph)); circ(cx + (rnd(k, 3f) - .5f) * fw * 1.6f, pool - ph * h * .04f, u * (2f + ph * 5f)) }
                }
                for (r in 0 until 3) for (k in 0 until 9) {
                    val x = w * (k / 8f) + (r % 2) * w * .06f
                    if (x > w * .38f && x < w * .64f && r < 2) continue
                    col(tint(.45f + r * .2f, greens[r + 1], .3f))
                    for (b in arrayOf(floatArrayOf(-.6f, 0f, 1f), floatArrayOf(.6f, .1f, .9f), floatArrayOf(0f, -.6f, 1.1f))) circ(x + b[0] * h * .05f + sin(t * 2f * PI.toFloat() + k) * u * .6f, horizon + h * (.1f + r * .12f) + b[1] * h * .05f, h * .06f * b[2] * (1f + r * .25f))
                }
                p.style = Paint.Style.STROKE; p.strokeWidth = u * .9f; p.color = tint(.9f, 0xFF1B3A1C.toInt(), .3f)
                for (k in 0 until 4) { val x = w * (.1f + k * .26f); val sw = sin(t * 2f * PI.toFloat() + k) * u * 4f; val vine = Path(); vine.moveTo(x, 0f); vine.quadTo(x + sw, h * .05f, x + sw * 1.2f, h * (.07f + rnd(k, 4f) * .05f)); c.drawPath(vine, p) }
                p.style = Paint.Style.FILL
                run {
                    val bx = frac(drift * 4f) * (w + 40f * u) - 20f * u; val by = h * .3f + sin(drift * 20f) * u * 6f; val fl = sin(t * 8f * PI.toFloat()) * u * 3f
                    col(0xFFE53935.toInt()); oval(bx, by, u * 5f, u * 2.6f); col(0xFF1E88E5.toInt()); tri(bx - u * 2f, by, bx - u * 7f, by - u * 3f + fl, bx + u * 2f, by - u); col(0xFFFDD835.toInt()); circ(bx + u * 5f, by - u, u * 1.6f)
                }
            }
            "neon0" -> {
                // a neon city at night in the rain: towers, signs that glow and flicker, the street a mirror of their light
                p.shader = LinearGradient(0f, 0f, 0f, horizon, 0xBF0A061E.toInt(), 0x8C3C0A46.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w, horizon, p); p.shader = null
                val neon = intArrayOf(0xFFFF2D95.toInt(), 0xFF00E5FF.toInt(), 0xFFB14CFF.toInt(), 0xFFFFD600.toInt(), 0xFF39FF14.toInt())
                var x = -w * .02f; var k = 0
                while (x < w) {
                    val bw = w * (.1f + rnd(k, 1f) * .08f); val bh = h * (.3f + rnd(k, 2f) * .4f)
                    col(mix(0xFF0C0A1A.toInt(), 0xFF1A1430.toInt(), rnd(k, 3f))); rectF(x, horizon - bh, x + bw, h)
                    for (r in 0 until (bh / (u * 8f)).toInt()) for (q in 0 until 3) if (rnd(k * 50 + r * 3 + q, 7f) > .7f) { col(neon[(k + q) % 5], .35f); rectF(x + bw * (.15f + q * .28f), horizon - bh + u * 5f + r * u * 8f, x + bw * (.29f + q * .28f), horizon - bh + u * 8f + r * u * 8f) }
                    if (rnd(k, 9f) > .3f) {
                        val sc = neon[k % 5]; val sy = horizon - bh * (.3f + rnd(k, 4f) * .4f); val fl = if (sin(t * 14f * PI.toFloat() + k * 3f) > -.85f) 1f else .25f
                        glow(x + bw / 2f, sy, bw * .9f, sc, (115 * fl).toInt())
                        p.style = Paint.Style.STROKE; p.strokeWidth = u * 2f; p.color = sc; p.alpha = (255 * fl).toInt(); c.drawRect(x + bw * .15f, sy - u * 6f, x + bw * .85f, sy + u * 6f, p); p.style = Paint.Style.FILL
                    }
                    x += bw + u * 2f; k++
                }
                p.shader = LinearGradient(0f, horizon, 0f, h, 0xFF120C24.toInt(), 0xFF05030C.toInt(), Shader.TileMode.CLAMP); c.drawRect(0f, horizon, w, h, p); p.shader = null
                for (q in 0 until 26) { val rx = frac(q * .618f) * w; col(neon[q % 5], (.18f + .1f * sin(t * 4f * PI.toFloat() + q)).coerceIn(0f, 1f)); rectF(rx, horizon + u * 2f, rx + u * (2f + rnd(q, 5f) * 4f), horizon + u * 2f + (h - horizon) * (.4f + rnd(q, 6f) * .5f)) }
                col(Color.WHITE, .08f); for (q in 0 until 6) { val y = horizon + (q + 1) * (h - horizon) / 7f; ln(0f, y, w, y, u) }
            }
            "mushroom0" -> {
                // a village of mushroom houses: red and blue caps with white spots, little doors and windows, flowers in the grass
                ridge(horizon, h * .12f, 1.2f, 3f, tint(.3f, 0xFF5FAF5A.toInt(), 1f))
                ridge(horizon + h * .08f, h * .10f, 1f, 1.5f, tint(.55f, 0xFF4FA04A.toInt(), 1f))
                fun shroom(x: Float, by: Float, s2: Float, cap: Int, seed: Int) {
                    col(tint(.8f, 0xFFF2E6CF.toInt(), .2f))
                    val st = Path(); st.moveTo(x - s2 * .32f, by); st.quadTo(x - s2 * .36f, by - s2 * .6f, x - s2 * .26f, by - s2 * .9f); st.lineTo(x + s2 * .26f, by - s2 * .9f); st.quadTo(x + s2 * .36f, by - s2 * .6f, x + s2 * .32f, by); st.close(); c.drawPath(st, p)
                    col(tint(.8f, 0xFF7A4A2A.toInt())); dome(x, by - s2 * .18f, s2 * .12f); rectF(x - s2 * .12f, by - s2 * .18f, x + s2 * .12f, by)
                    if (night > .12f) col(0xFFFFD27A.toInt(), .95f) else col(mix(0xFF5A86B0.toInt(), bot, .3f)); circ(x + s2 * .16f, by - s2 * .55f, s2 * .07f)
                    col(tint(.8f, cap, .4f)); c.drawArc(RectF(x - s2 * .62f, by - s2 * .88f - s2 * .42f, x + s2 * .62f, by - s2 * .88f + s2 * .42f), 180f, 180f, true, p)
                    col(tint(.8f, 0xFFFFFFFF.toInt(), .2f))
                    for (b in arrayOf(floatArrayOf(-.35f, -.15f, .08f), floatArrayOf(.05f, -.3f, .1f), floatArrayOf(.38f, -.12f, .07f), floatArrayOf(-.1f, -.08f, .06f), floatArrayOf(.25f, -.32f, .05f))) circ(x + b[0] * s2, by - s2 * .88f + b[1] * s2, b[2] * s2)
                    for (q in 0 until 3) { val ph = frac(t + q / 3f + seed * .17f); col(mix(Color.WHITE, bot, .3f), .35f * (1f - ph)); circ(x + s2 * .2f + ph * u * 6f, by - s2 * 1.3f - ph * h * .06f, u * (1.4f + ph * 3f)) }
                }
                arrayOf(floatArrayOf(.16f, .80f, .18f), floatArrayOf(.42f, .84f, .24f), floatArrayOf(.70f, .79f, .17f), floatArrayOf(.88f, .86f, .21f), floatArrayOf(.3f, .94f, .14f))
                    .forEachIndexed { i, f -> shroom(w * f[0], h * f[1], h * f[2], intArrayOf(0xFFD63A2E.toInt(), 0xFF2F6FD8.toInt(), 0xFFD63A2E.toInt(), 0xFFE68A1E.toInt(), 0xFFB03AD6.toInt())[i], i) }
                val fcols = intArrayOf(0xFFFFEB3B.toInt(), 0xFFFF6FB5.toInt(), 0xFFFFFFFF.toInt(), 0xFF7AD7F0.toInt())
                for (k in 0 until 26) { col(fcols[k % 4], .9f); circ(rnd(k, 7f) * w, h * (.88f + rnd(k, 8f) * .1f) + sin(t * 2f * PI.toFloat() + k) * u * .6f, u * 1.8f) }
            }
            "aurora0" -> {
                // the far north: snow, an igloo and dark pines, and at night the northern lights waving across the sky
                if (night > .2f) {
                    val ap = Paint(Paint.ANTI_ALIAS_FLAG); ap.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
                    val acol = intArrayOf(0xFF39FF8A.toInt(), 0xFF2FD6C8.toInt(), 0xFFB46CFF.toInt())
                    for (b in 0 until 3) {
                        var x = 0f
                        while (x <= w) {
                            val y = h * (.12f + b * .07f) + sin(x / w * 6f + t * 2f * PI.toFloat() + b) * h * .05f
                            ap.shader = LinearGradient(0f, y, 0f, y + h * .22f, intArrayOf(ColorUtils.setAlphaComponent(acol[b], 0), ColorUtils.setAlphaComponent(acol[b], (56 * night.coerceIn(0f, 1f)).toInt()), ColorUtils.setAlphaComponent(acol[b], 0)), floatArrayOf(0f, .25f, 1f), Shader.TileMode.CLAMP)
                            c.drawRect(x, y, x + w / 60f + 1f, y + h * .22f, ap); x += w / 60f
                        }
                    }
                }
                ridge(horizon, h * .10f, 1.0f, 2.4f, tint(.3f, 0xFFC8D6E4.toInt(), 1f))
                for (k in 0 until 12) pine(w * (k / 11f), horizon + h * .06f, h * (.12f + rnd(k, 4f) * .06f), tint(.6f, 0xFF1A2A2E.toInt(), .5f))
                col(tint(.8f, 0xFFE8F0F8.toInt(), 1f)); rectF(0f, horizon + h * .06f, w, h)
                run {
                    val ix = w * .62f; val iy = h * .86f; val ir = h * .12f
                    col(tint(.8f, 0xFFF4F8FC.toInt(), 1f)); dome(ix, iy, ir)
                    col(tint(.8f, 0xFFB8C8D8.toInt(), 1f), .8f); for (k in 1 until 4) ln(ix - ir * kotlin.math.sqrt(1f - (k * .25f) * (k * .25f)), iy - ir * k * .25f, ix + ir * kotlin.math.sqrt(1f - (k * .25f) * (k * .25f)), iy - ir * k * .25f, u)
                    if (night > .15f) col(0xFFFFB347.toInt(), .9f) else col(0xFF2A3A4A.toInt()); dome(ix - ir * .5f, iy, ir * .28f)
                }
                for (k in 0 until 5) pine(w * (if (k < 2) k * .08f + .03f else .85f + (k - 2) * .05f), h, h * (.24f + rnd(k, 5f) * .08f), tint(1f, 0xFF10202A.toInt(), .6f))
            }
            "savanna0" -> {
                // the savanna: golden grass, flat acacias and giraffes walking slowly by
                ridge(horizon, h * .05f, 1f, 2.2f, tint(.25f, 0xFF9A7A4A.toInt(), .3f))
                col(tint(.6f, 0xFFD9A84A.toInt(), .3f)); rectF(0f, horizon + h * .02f, w, h)
                fun acacia(x: Float, by: Float, s2: Float) {
                    col(tint(.9f, 0xFF3A2A1A.toInt())); ln(x, by, x, by - s2 * .6f, s2 * .05f); ln(x, by - s2 * .6f, x - s2 * .25f, by - s2 * .9f, s2 * .05f); ln(x, by - s2 * .6f, x + s2 * .28f, by - s2 * .92f, s2 * .05f)
                    col(tint(.9f, 0xFF3A5A2A.toInt(), .3f)); oval(x, by - s2 * .95f, s2 * .62f, s2 * .13f)
                }
                acacia(w * .2f, horizon + h * .12f, h * .32f); acacia(w * .78f, horizon + h * .08f, h * .24f); acacia(w * .52f, horizon + h * .05f, h * .14f)
                fun giraffe(x: Float, by: Float, s2: Float, ph: Float) {
                    val lg = sin((t * 3f + ph) * 2f * PI.toFloat()) * s2 * .05f; val gc = tint(.95f, 0xFF6A4A22.toInt())
                    col(gc); oval(x, by - s2 * .55f, s2 * .28f, s2 * .13f); ln(x + s2 * .2f, by - s2 * .6f, x + s2 * .36f, by - s2 * 1.15f, s2 * .07f); oval(x + s2 * .42f, by - s2 * 1.18f, s2 * .1f, s2 * .05f)
                    for (l in arrayOf(floatArrayOf(-.18f, lg), floatArrayOf(-.08f, -lg), floatArrayOf(.12f, lg), floatArrayOf(.2f, -lg))) ln(x + l[0] * s2, by - s2 * .48f, x + l[0] * s2 + l[1], by, s2 * .05f)
                }
                for (k in 0 until 3) { val gx = frac(drift * 1.5f + k * .22f) * (w + h * .6f) - h * .3f; giraffe(gx, horizon + h * (.2f + k * .03f), h * (.22f - k * .03f), k * .3f) }
                col(tint(.95f, 0xFFB8862E.toInt(), .3f)); for (k in 0 until 40) { val gx = rnd(k, 9f) * w; val gy = h * (.82f + rnd(k, 8f) * .18f); ln(gx, gy, gx + sin(t * 2f * PI.toFloat() + k) * u * 2f, gy - u * 8f, u) }
            }
            "fuji0" -> {
                // Mount Fuji with its white cap over a still lake, a pagoda and a cherry tree dropping petals
                run {
                    val px = w * .5f; val py = horizon - h * .36f
                    col(tint(.5f, 0xFF4A5A80.toInt(), .3f)); val m = Path(); m.moveTo(w * .02f, horizon); m.quadTo(w * .35f, horizon - h * .12f, px - w * .07f, py); m.lineTo(px + w * .07f, py); m.quadTo(w * .65f, horizon - h * .12f, w * .98f, horizon); m.close(); c.drawPath(m, p)
                    col(mix(Color.WHITE, bot, .15f)); val cap = Path(); cap.moveTo(px - w * .07f, py); cap.lineTo(px + w * .07f, py); cap.lineTo(px + w * .15f, py + h * .1f); cap.lineTo(px + w * .09f, py + h * .08f); cap.lineTo(px + w * .04f, py + h * .12f); cap.lineTo(px - w * .02f, py + h * .08f); cap.lineTo(px - w * .08f, py + h * .12f); cap.lineTo(px - w * .15f, py + h * .1f); cap.close(); c.drawPath(cap, p)
                }
                waterFill(horizon, mix(bot, 0xFF2A5F86.toInt(), .35f), mix(0xFF123A5A.toInt(), 0xFF04141F.toInt(), night * .6f + .2f))
                reflect(horizon, h, 8)
                run {
                    val px = w * .82f; val by = horizon + h * .02f; col(tint(.85f, 0xFF3A1A1A.toInt()))
                    for (k in 0 until 4) {
                        val yy = by - k * h * .06f; val ww = w * (.1f - k * .018f)
                        rectF(px - ww * .35f, yy - h * .045f, px + ww * .35f, yy)
                        val roof = Path(); roof.moveTo(px - ww * .6f, yy - h * .04f); roof.quadTo(px, yy - h * .075f, px + ww * .6f, yy - h * .04f); roof.lineTo(px + ww * .4f, yy - h * .03f); roof.lineTo(px - ww * .4f, yy - h * .03f); roof.close(); c.drawPath(roof, p)
                    }
                    rectF(px - u, by - h * .3f, px + u, by - h * .24f)
                }
                run {
                    val tx = w * .12f; val ty = h * .98f
                    p.style = Paint.Style.STROKE; p.strokeWidth = u * 4f; p.strokeCap = Paint.Cap.ROUND; p.color = tint(.95f, 0xFF3A2A22.toInt()); p.alpha = 255
                    val br = Path(); br.moveTo(tx, ty); br.quadTo(tx + w * .05f, h * .75f, tx + w * .18f, h * .55f); c.drawPath(br, p); p.style = Paint.Style.FILL
                    val bl = intArrayOf(0xFFF7B6D2.toInt(), 0xFFF48FB1.toInt(), 0xFFFCE4EC.toInt())
                    for (k in 0 until 16) { col(tint(.9f, bl[k % 3], .2f), .95f); circ(tx + w * (.06f + rnd(k, 1f) * .26f), h * (.45f + rnd(k, 2f) * .2f), h * (.04f + rnd(k, 3f) * .03f)) }
                    for (k in 0 until 14) { val ph = frac(t + rnd(k, 4f)); c.save(); c.translate(tx + w * (.1f + rnd(k, 5f) * .5f) + sin((t + k) * 2f * PI.toFloat()) * u * 8f, h * .5f + ph * h * .5f); c.rotate((ph * 6f + k) * 57.2958f); col(0xFFF8BBD0.toInt(), .9f); oval(0f, 0f, u * 2.4f, u * 1.4f); c.restore() }
                }
            }
            "lavender0" -> {
                // lavender rows running to a stone farmhouse, a cypress beside it, bees in the air
                ridge(horizon, h * .06f, 1f, 2.6f, tint(.25f, 0xFF7A8AA8.toInt(), .3f))
                col(tint(.5f, 0xFF8A6AB0.toInt(), .3f)); rectF(0f, horizon + h * .02f, w, h)
                val vx = w * .5f; val vy = horizon + h * .02f
                for (k in -9..9) {
                    col(tint(.7f, if (k % 2 != 0) 0xFF7A4AB8.toInt() else 0xFF9A6AD0.toInt(), .3f)); ln(vx + k * u * 3f, vy, vx + k * w * .14f, h * 1.02f, u * (3f + abs(k) * .3f))
                    col(tint(.7f, 0xFF4A7A3A.toInt(), .3f), .6f); ln(vx + k * u * 3f + u * 1.5f, vy, vx + k * w * .14f + w * .07f, h * 1.02f, u)
                }
                run {
                    val hx = w * .66f; val hy = vy + u * 2f
                    col(tint(.6f, 0xFFD8C8A8.toInt(), .2f)); rectF(hx - w * .06f, hy - h * .05f, hx + w * .06f, hy)
                    col(tint(.55f, 0xFFB5533C.toInt())); tri(hx - w * .07f, hy - h * .05f, hx, hy - h * .08f, hx + w * .07f, hy - h * .05f)
                    if (night > .12f) { col(0xFFFFD27A.toInt(), .9f); rectF(hx - w * .02f, hy - h * .035f, hx - w * .005f, hy - h * .02f) }
                }
                cypress(w * .76f, vy + u * 2f, h * .14f, tint(.6f, 0xFF1D3A22.toInt(), .4f))
                for (k in 0 until 6) {
                    val bx = w * (.2f + rnd(k, 1f) * .6f) + sin((t * 2f + k) * 2f * PI.toFloat()) * u * 10f; val by = h * (.7f + rnd(k, 2f) * .2f) + kotlin.math.cos((t * 3f + k) * 2f * PI.toFloat()) * u * 5f
                    col(0xFFF2C02E.toInt()); oval(bx, by, u * 2.2f, u * 1.6f); col(Color.WHITE, .7f); oval(bx, by - u * 2f, u * 1.6f, u)
                }
            }
            "volcano0" -> {
                // a volcano: dark slopes, a glowing crater with lava running down and a plume of smoke
                ridge(horizon, h * .08f, 1f, 3f, tint(.3f, 0xFF4A3A3A.toInt(), .2f))
                val cx = w * .5f; val ty = horizon - h * .34f
                col(tint(.5f, 0xFF2A2222.toInt(), .2f)); run { val m = Path(); m.moveTo(0f, horizon + h * .1f); m.lineTo(cx - w * .1f, ty); m.lineTo(cx + w * .1f, ty); m.lineTo(w, horizon + h * .12f); m.lineTo(w, h); m.lineTo(0f, h); m.close(); c.drawPath(m, p) }
                val gl = .7f + .3f * sin(t * 4f * PI.toFloat())
                glow(cx, ty, h * .25f, 0xFFFF6A1A.toInt(), (153 * gl).toInt())
                col(0xFFFF8A2A.toInt(), .95f); oval(cx, ty, w * .09f, h * .012f)
                p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.color = 0xE6FF6A1A.toInt()
                p.strokeWidth = u * 3f; val l1 = Path(); l1.moveTo(cx - w * .02f, ty); l1.quadTo(cx - w * .08f, ty + h * .15f, cx - w * .12f, horizon + h * .1f); c.drawPath(l1, p)
                p.strokeWidth = u * 2f; val l2 = Path(); l2.moveTo(cx + w * .03f, ty); l2.quadTo(cx + w * .06f, ty + h * .12f, cx + w * .15f, horizon + h * .06f); c.drawPath(l2, p)
                p.style = Paint.Style.FILL
                for (k in 0 until 10) { val ph = frac(t * .5f + k / 10f); p.color = Color.argb((127 * (1f - ph)).toInt(), (80 + 40 * (1f - ph)).toInt(), 70, 70); circ(cx + ph * w * .25f + sin(ph * 4f + k) * u * 6f, ty - ph * h * .4f, u * (6f + ph * 22f)) }
                for (k in 0 until 8) { val ph = frac(t * 2f + k / 8f); col(0xFFFFB347.toInt(), 1f - ph); circ(cx + (rnd(k, 1f) - .5f) * w * .12f * ph * 3f, ty - h * .08f * sin(ph * PI.toFloat()), u * 1.4f) }
                ridge(h * 1.01f, h * .12f, .8f, 6f, tint(1f, 0xFF1A1414.toInt(), .2f))
            }
            "island0" -> {
                // a small tropical island in a turquoise lagoon: palms, white sand, a sailboat and gulls
                p.shader = LinearGradient(0f, horizon, 0f, h, intArrayOf(mix(bot, 0xFF1288A8.toInt(), .45f), mix(0xFF22C1C3.toInt(), 0xFF05202C.toInt(), night * .7f), mix(0xFF0A6E8A.toInt(), 0xFF031829.toInt(), night * .7f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, horizon, w, h, p); p.shader = null
                reflect(horizon, h, 10)
                run {
                    val ix = w * .45f; val iy = horizon + h * .18f
                    col(0xFF78E6DC.toInt(), .35f); oval(ix, iy, w * .36f, h * .06f)
                    col(tint(.8f, 0xFFF2DEB0.toInt(), .2f)); c.drawArc(RectF(ix - w * .26f, iy - h * .035f, ix + w * .26f, iy + h * .035f), 180f, 180f, true, p); rectF(ix - w * .26f, iy - u, ix + w * .26f, iy + u)
                    palm(ix - w * .1f, iy, h * .26f, w * .04f); palm(ix + w * .02f, iy, h * .32f, -w * .02f); palm(ix + w * .14f, iy, h * .22f, -w * .05f)
                }
                for (k in 0 until 6) { val y = horizon + (k + .7f) * (h - horizon) / 7f; col(Color.WHITE, .22f); ln(w * (.05f + frac(k * .37f) * .5f), y + sin((t + k * .2f) * 2f * PI.toFloat()) * u, w * (.3f + frac(k * .37f) * .6f), y, u * 1.2f) }
                run {
                    val bx = w * .82f + sin(t * 4f * PI.toFloat()) * u * 1.2f; val by = horizon + h * .08f
                    col(tint(.9f, 0xFF10202C.toInt())); quadP(bx - u * 12f, by, bx + u * 12f, by, bx + u * 8f, by + u * 5f, bx - u * 8f, by + u * 5f)
                    col(mix(Color.WHITE, bot, .3f)); tri(bx, by - u * 18f, bx + u * 9f, by - u * 2f, bx, by - u * 2f)
                }
            }
            else -> {}
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
        if (night < .4f && (sky == Sky.CLEAR || sky == Sky.PARTLY || sky == Sky.CLOUD) && (land == Land.MOUNTAIN || land == Land.SEA || land == Land.PLATEAU || land == Land.FOREST || land == Land.MOSQUE || land == Land.VILLAGE)) {
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
            // rain in three depths: many far fine drops, a middle layer, and a few near long bright ones, all leaning the same way with the wind; drops land as small rings on the ground
            val dz = sky == Sky.DRIZZLE; val storm = sky == Sky.STORM
            val lean = if (storm) .30f else .18f
            val layers = arrayOf(
                floatArrayOf(if (dz) 70f else if (storm) 190f else 140f, 6f, .8f, 1.5f, 95f),
                floatArrayOf(if (dz) 40f else if (storm) 110f else 80f, 11f, 1.1f, 2.4f, 150f),
                floatArrayOf(if (dz) 8f else if (storm) 40f else 24f, 22f, 1.7f, 3.5f, 215f))
            p.color = 0xFFC8E0FF.toInt(); p.strokeCap = Paint.Cap.ROUND; p.style = Paint.Style.STROKE
            for ((li, ly) in layers.withIndex()) {
                val len = w / 400f * ly[1]; p.strokeWidth = maxOf(1f, w / 400f * ly[2])
                for (k in 0 until ly[0].toInt()) {
                    val seed = frac(k * .6180339f + li * .31f); val seed2 = frac(k * .7548777f + li * .17f)
                    val sy = ((t * ly[3] + seed2 * 3f) % 1f) * (h + len) - len
                    val sx = seed * (w + h * lean) - sy * lean * .5f
                    p.alpha = (ly[4] * (.7f + .3f * seed2)).toInt()
                    c.drawLine(sx, sy, sx - len * lean, sy + len, p)
                }
            }
            // rings where drops land on the ground (the lower fifth of the scene), each growing and fading
            run {
                p.strokeWidth = maxOf(1f, w / 400f)
                val rings = if (dz) 18 else if (storm) 46 else 32
                for (k in 0 until rings) {
                    val ph = frac(t * 2.2f + k * .37f); val rx = frac(k * .6180339f + .11f) * w
                    val ry = horizon + (h - horizon) * (.25f + .7f * frac(k * .3141f))
                    val rr = w / 400f * (2f + ph * 11f)
                    p.color = 0xFFDDEBFF.toInt(); p.alpha = ((1f - ph) * 120f).toInt()
                    c.drawOval(android.graphics.RectF(rx - rr, ry - rr * .35f, rx + rr, ry + rr * .35f), p)
                }
            }
            p.style = Paint.Style.FILL
            // a light mist rising from the wet ground
            p.shader = LinearGradient(0f, h * .78f, 0f, h, 0x00B8D8FF, 0x33B8D8FF, Shader.TileMode.CLAMP); c.drawRect(0f, h * .78f, w, h, p); p.shader = null
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
    // scenery off: the classic sky (blue, sun, clouds, moon) with no landscape
    if (landPref == "off") { WeatherScene(f.code, modifier, f.isDay); return }
    val land = remember(f.place, landPref, f.code, System.currentTimeMillis() / 86_400_000L) { WeatherLand.pick(landPref, f.place, f.elev, skyOf(f.code)) }
    val ck = remember(f) { WeatherLand.clockOf(f) }
    val vr = remember(f.place, land) { WeatherLand.variantOf(f.place, land) }
    val sky = skyOf(f.code)
    val t by rememberInfiniteTransition(label = "land").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val drift by rememberInfiniteTransition(label = "landDrift").animateFloat(0f, 1f, infiniteRepeatable(tween(60000, easing = LinearEasing), RepeatMode.Restart), label = "d")
    ComposeCanvas(modifier) {
        drawIntoCanvas { WeatherLand.draw(it.nativeCanvas, size.width, size.height, land, sky, ck, t, drift, vr) }
    }
}
