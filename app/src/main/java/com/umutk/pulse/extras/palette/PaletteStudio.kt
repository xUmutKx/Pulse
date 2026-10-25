package com.umutk.pulse.extras.palette

import android.content.Context
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.Finish
import com.umutk.pulse.extras.palette.core.icon.IconCache
import com.umutk.pulse.extras.palette.core.icon.IconEngine
import com.umutk.pulse.extras.palette.core.icon.IconMode
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.icon.TileShape
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import org.json.JSONArray
import org.json.JSONObject

/**
 * Palette (an icon pack studio): one palette and a style turn the icons of every app into a matching set. The chosen look is kept as a small piece of
 * JSON, and the engine, the app list and the icon cache live here once for the whole process.
 */
object PaletteStudio {
    @Volatile private var repo: AppRepository? = null
    @Volatile private var eng: IconEngine? = null

    fun apps(c: Context): AppRepository = repo ?: synchronized(this) { repo ?: AppRepository(c.applicationContext).also { repo = it } }
    fun engine(c: Context): IconEngine = eng ?: synchronized(this) { eng ?: IconEngine(c.applicationContext, apps(c), IconCache(c.applicationContext)).also { eng = it } }

    private fun sp(c: Context) = c.getSharedPreferences("palette_studio", Context.MODE_PRIVATE)

    /** The saved look, or the default one (lavender squircles). */
    fun spec(c: Context): ThemeSpec {
        val s = sp(c).getString("spec", null) ?: return ThemeSpec()
        return runCatching {
            val o = JSONObject(s)
            val cols = o.getJSONArray("colors").let { a -> (0 until a.length()).map { a.getInt(it) } }
            ThemeSpec(
                palette = ColorPalette(name = o.optString("name", "Palette"), colors = cols),
                mode = enumOf(o.optString("mode"), IconMode.TILES), style = o.optString("style", "material_you"),
                shape = enumOf(o.optString("shape"), TileShape.SQUIRCLE), finish = enumOf(o.optString("finish"), Finish.MATTE),
                assignment = enumOf(o.optString("assign"), ColorAssignment.LUMINANCE), seed = o.optLong("seed", 42L),
            )
        }.getOrDefault(ThemeSpec())
    }

    fun save(c: Context, t: ThemeSpec) {
        val o = JSONObject().put("name", t.palette.name).put("colors", JSONArray(t.palette.colors)).put("mode", t.mode.name).put("style", t.style)
            .put("shape", t.shape.name).put("finish", t.finish.name).put("assign", t.assignment.name).put("seed", t.seed)
        sp(c).edit().putString("spec", o.toString()).apply()
        eng?.cache?.clearMemory()
    }

    private inline fun <reified E : Enum<E>> enumOf(s: String, d: E): E = enumValues<E>().firstOrNull { it.name == s } ?: d
}
