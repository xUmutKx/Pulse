package com.umutk.pulse.extras.whitenoise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.umutk.pulse.T
import com.umutk.pulse.win7Authentic
import com.umutk.pulse.win95Authentic
import com.umutk.pulse.xpAuthentic

// 24x24 glyphs as SVG path data. Only subpaths ending in Z get a fill; open ones are lines.
private const val DROP = "M12 3 L6.5 11 a5.5 5.5 0 1 0 11 0 Z"
private const val CLOUD = "M7 14 a3.5 3.5 0 0 1 -.4 -7 a5 5 0 0 1 9.6 1 a3 3 0 0 1 -.2 6 Z"
private const val FLAME = "M12 2 C13 7 18 9 18 15 a6 6 0 0 1 -12 0 C6 12 8 10 9 8 C9.5 10 11 10 11 8 C11 5 12 2 12 2 Z"
private const val BOWL = "M3 11 H21 a9 9 0 0 1 -18 0 Z M8 21 H16"
private const val BARS = "M3 12 H4 M7 8 V16 M10 4 V20 M13 9 V15 M16 6 V18 M19 10 V14 M21 12 H22"
private const val WAVES = "M2 9 q2.5 -3 5 0 t5 0 t5 0 t5 0 M2 15 q2.5 -3 5 0 t5 0 t5 0 t5 0"
private const val MOON = "M16 3 a9 9 0 1 0 5 13 a7 7 0 0 1 -5 -13 Z"
private const val WIND = "M3 8 H14 a3 3 0 1 0 -3 -3 M3 12 H19 a3 3 0 1 1 -3 3 M3 16 H10"

private val SHAPES = mapOf(
    "rain" to "$CLOUD M8 17 l-1.5 3 M12 17 l-1.5 3 M16 17 l-1.5 3",
    "heavy_rain" to "M7 4 L5 10 M12 4 L10 10 M17 4 L15 10 M9 13 L7 19 M14 13 L12 19 M19 13 L17 19",
    "drizzle" to "$CLOUD M8 17 l-1 3 M12 17 l-1 3 M16 17 l-1 3",
    "rain_roof" to "M3 12 L12 4 L21 12 Z M8 15 l-1 3 M12 15 l-1 3 M16 15 l-1 3",
    "gok_gultusu" to "M13 2 L5 13 H11 L10 22 L19 10 H13 Z",
    "wind" to WIND, "wind_trees" to WIND,
    "orman" to "M12 3 L5 13 H9 L4 20 H20 L15 13 H19 Z M12 20 V22",
    "forest_night" to "$MOON M6 4 l.6 1.4 1.4 .6 -1.4 .6 -.6 1.4 -.6 -1.4 -1.4 -.6 1.4 -.6 Z",
    "crickets" to "M5 19 C5 9 11 5 20 4 C20 13 16 19 5 19 Z M5 19 L14 10",
    "waves" to WAVES,
    "beach" to "M12 4 a9 8 0 0 0 -9 8 H21 a9 8 0 0 0 -9 -8 Z M12 12 V21 M9 21 H15",
    "selale" to "M5 3 H19 M8 3 V9 M12 3 V13 M16 3 V9 M5 21 q2 -3 4 0 t4 0 t4 0 t2 0",
    "dere" to "M5 3 C15 6 5 11 12 14 C17 16 12 19 19 21 M9 3 C19 6 9 11 16 14 C21 16 16 19 22 21",
    "damlama" to "M12 3 L8 10 a4 4 0 1 0 8 0 Z M8 19 q4 3 8 0",
    "fire" to FLAME,
    "fireplace" to "M4 21 V8 H20 V21 Z M8 21 V12 H16 V21 M12 14 c1 2 2 2.5 2 4 a2 2 0 0 1 -4 0 c0 -1.5 2 -2 2 -4 Z",
    "coffee" to "M4 9 H16 V15 a5 5 0 0 1 -5 5 H9 a5 5 0 0 1 -5 -5 Z M16 10 H18 a2.5 2.5 0 0 1 0 5 H16 M8 3 q1 2 0 3 M12 3 q1 2 0 3",
    "cafe_jazz" to "M9 18 V5 L19 3 V16 M9 18 a3 3 0 1 1 -3 -3 a3 3 0 0 1 3 3 Z M19 16 a3 3 0 1 1 -3 -3 a3 3 0 0 1 3 3 Z",
    "restaurant" to "M5 3 V9 a2 2 0 0 0 4 0 V3 M7 3 V21 M17 21 V3 C14 6 14 12 17 13",
    "tren" to "M7 3 H17 a2 2 0 0 1 2 2 V16 a2 2 0 0 1 -2 2 H7 a2 2 0 0 1 -2 -2 V5 a2 2 0 0 1 2 -2 Z M5 11 H19 M8 18 L6 22 M16 18 L18 22",
    "station" to "M12 3 a9 9 0 1 0 .01 0 Z M12 7 V12 L15 14",
    "crowd" to "M9 5 a3 3 0 1 0 .01 0 Z M17 7 a2.5 2.5 0 1 0 .01 0 Z M3 20 a6 6 0 0 1 12 0 Z M16 14.5 a5 5 0 0 1 5 5.5 H17",
    "cinema" to "M3 5 H21 V19 H3 Z M8 5 V19 M16 5 V19 M3 9.5 H8 M3 14.5 H8 M16 9.5 H21 M16 14.5 H21",
    "konusma" to "M4 4 H20 V16 H10 L5 21 V16 H4 Z",
    "typing" to "M3 7 H21 V17 H3 Z M6 10 h1 M10 10 h1 M14 10 h1 M17 10 h1 M7 14 H17",
    "fan" to "M12 11 C10 4 15 3 15 6 C15 8 13 10 12 11 Z M13 12 C20 10 21 15 18 15 C16 15 14 13 13 12 Z M11 12 C8 18 3 15 5 13 C6 12 9 12 11 12 Z M12 12 V21 M8 21 H16",
    "white" to BARS,
    "pembe_gurultu" to "M3 12 H4 M7 6 V18 M10 9 V15 M13 3 V21 M16 8 V16 M19 10 V14 M21 12 H22",
    "brown" to "M2 12 q3 -8 6 0 t6 0 t6 0",
    "bell" to "M12 3 C7 3 6 8 6 12 L4 17 H20 L18 12 C18 8 17 3 12 3 Z M10 20 a2 2 0 0 0 4 0",
    "bowl" to "$BOWL M9 6 q1 -2 0 -3 M15 6 q1 -2 0 -3",
    "bowl_rub" to "$BOWL M6 7 q6 -3 12 0",
    "owl" to "M6 4 L9 6 H15 L18 4 V14 a6 7 0 0 1 -12 0 Z M9 11 a1.5 1.5 0 1 0 .01 0 Z M15 11 a1.5 1.5 0 1 0 .01 0 Z M12 13 L11 15 H13 Z",
    "bird" to "M3 15 C6 10 10 9 12 9 C13 6 16 5 18 7 L21 8 L18 9 C18 14 14 19 8 19 L11 15 Z M9 21 H14",
    "speaker" to "M4 9 H8 L13 5 V19 L8 15 H4 Z M16 9 q3 3 0 6 M18.5 6.5 q5 5.5 0 11",
    "steam" to "M8 4 q-2 2 0 4 t0 4 M12 3 q-2 2 0 4 t0 4 M16 4 q-2 2 0 4 t0 4",
    "heartbeat" to "M2 12 H7 L9 8 L12 17 L14 10 L15 12 H22",
    "vinyl" to "M12 3 a9 9 0 1 0 .01 0 Z M12 10 a2 2 0 1 0 .01 0 Z",
    "traffic" to "M3 13 L5 9 H17 L20 13 V17 H3 Z M6 17 V19 M17 17 V19",
)

private val CACHE = HashMap<String, List<Pair<Path, Boolean>>>()

private fun shapeFor(id: String, cat: String): String {
    val key = when {
        id in SHAPES -> id
        id.contains("owl") || id.contains("baykus") || id.contains("kukumav") -> "owl"
        cat == "birds" -> "bird"
        id.contains("rain") || id.contains("yagmur") -> "rain"
        else -> "speaker"
    }
    return key
}

private fun parts(key: String): List<Pair<Path, Boolean>> = CACHE.getOrPut(key) {
    SHAPES.getValue(key).split("M").filter { it.isNotBlank() }.map { s -> PathParser().parsePathString("M$s").toPath() to s.contains("Z") }
}

/** Sound glyph on a 24x24 grid: Material = rounded outline plus soft fill, 95/XP/7 = flat solid, 10/11 = thin line. Unknown ids get a speaker; [cat] "birds" gives a bird. */
@Composable
fun SoundIcon(id: String, tint: Color, size: Dp, modifier: Modifier = Modifier, cat: String = "") {
    val ps = remember(id, cat) { parts(shapeFor(id, cat)) }
    val flat = win95Authentic || xpAuthentic || win7Authentic
    val thin = T.id == "win10" || T.id == "win11"
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, Offset.Zero) {
            ps.forEach { (p, closed) ->
                if (flat) {
                    if (closed) drawPath(p, tint)
                    drawPath(p, tint, style = Stroke(2f, join = StrokeJoin.Miter))
                } else {
                    if (closed && !thin) drawPath(p, tint.copy(alpha = .16f))
                    drawPath(p, tint, style = Stroke(if (thin) 1.1f else 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
