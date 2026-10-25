package com.umutk.pulse.extras.palette

import android.content.Context
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.Finish
import com.umutk.pulse.extras.palette.core.icon.IconMode
import com.umutk.pulse.extras.palette.core.icon.IconOverride
import com.umutk.pulse.extras.palette.core.icon.LauncherSkin
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.icon.TileShape
import com.umutk.pulse.extras.palette.core.icon.WallpaperStyle
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import org.json.JSONArray
import org.json.JSONObject

/**
 * What Palette keeps on the phone, as small JSON strings in one preferences file: the active look, the saved palettes and looks, the per-app rules and the
 * settings. (The original app keeps these in a Room database; one file of JSON does the same for this size.)
 */
object PaletteStore {
    private fun sp(c: Context) = c.getSharedPreferences("palette_studio", Context.MODE_PRIVATE)

    // ---- JSON of the pieces ------------------------------------------------------------------------------------------------------------------
    fun paletteJson(p: ColorPalette) = JSONObject().put("id", p.id).put("name", p.name).put("colors", JSONArray(p.colors)).put("source", p.source).put("fav", p.favorite)

    fun palette(o: JSONObject): ColorPalette {
        val a = o.getJSONArray("colors")
        return ColorPalette(id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() }, name = o.optString("name", "Palette"), colors = (0 until a.length()).map { a.getInt(it) },
            source = o.optString("source", "manual"), favorite = o.optBoolean("fav", false))
    }

    fun specJson(t: ThemeSpec): JSONObject = JSONObject()
        .put("id", t.id).put("name", t.name).put("palette", paletteJson(t.palette)).put("mode", t.mode.name).put("style", t.style).put("shape", t.shape.name)
        .put("cornerRadius", t.cornerRadius.toDouble()).put("iconScale", t.iconScale.toDouble()).put("glyphScale", t.glyphScale.toDouble()).put("squircleExponent", t.squircleExponent.toDouble())
        .put("finish", t.finish.name).put("opacity", t.opacity.toDouble()).put("gradientIntensity", t.gradientIntensity.toDouble()).put("gradientAngle", t.gradientAngle.toDouble())
        .put("noise", t.noise.toDouble()).put("shadow", t.shadow.toDouble()).put("innerShadow", t.innerShadow.toDouble()).put("glow", t.glow.toDouble())
        .put("stroke", t.stroke.toDouble()).put("strokeLightness", t.strokeLightness.toDouble())
        .put("assign", t.assignment.name).put("singleColorIndex", t.singleColorIndex).put("hueRetention", t.hueRetention.toDouble()).put("chroma", t.chroma.toDouble())
        .put("contrast", t.contrast.toDouble()).put("brightness", t.brightness.toDouble()).put("randomness", t.randomness.toDouble()).put("backgroundIndex", t.backgroundIndex)
        .put("skin", t.skin.name).put("gridColumns", t.gridColumns).put("tileSpacing", t.tileSpacing.toDouble()).put("labelVisible", t.labelVisible).put("dayNight", t.dayNight)
        .put("seed", t.seed).put("widgetStyle", t.widgetStyle.name).put("widgetLabelVisible", t.widgetLabelVisible).put("wallpaperStyle", t.wallpaperStyle.name)

    fun spec(o: JSONObject): ThemeSpec {
        val d = ThemeSpec()
        // a look saved by an older Pulse has the palette as "colors" and "name" at the top
        val pal = o.optJSONObject("palette")?.let { palette(it) } ?: o.optJSONArray("colors")?.let { a ->
            ColorPalette(name = o.optString("name", "Palette"), colors = (0 until a.length()).map { a.getInt(it) })
        } ?: d.palette
        fun f(k: String, v: Float) = o.optDouble(k, v.toDouble()).toFloat()
        return ThemeSpec(
            id = o.optString("id").ifEmpty { d.id }, name = o.optString("name", d.name).takeIf { o.optJSONObject("palette") != null } ?: d.name, palette = pal,
            mode = en(o.optString("mode"), d.mode), style = o.optString("style", d.style), shape = en(o.optString("shape"), d.shape),
            cornerRadius = f("cornerRadius", d.cornerRadius), iconScale = f("iconScale", d.iconScale), glyphScale = f("glyphScale", d.glyphScale), squircleExponent = f("squircleExponent", d.squircleExponent),
            finish = en(o.optString("finish"), d.finish), opacity = f("opacity", d.opacity), gradientIntensity = f("gradientIntensity", d.gradientIntensity), gradientAngle = f("gradientAngle", d.gradientAngle),
            noise = f("noise", d.noise), shadow = f("shadow", d.shadow), innerShadow = f("innerShadow", d.innerShadow), glow = f("glow", d.glow), stroke = f("stroke", d.stroke), strokeLightness = f("strokeLightness", d.strokeLightness),
            assignment = en(o.optString("assign"), d.assignment), singleColorIndex = o.optInt("singleColorIndex", d.singleColorIndex), hueRetention = f("hueRetention", d.hueRetention), chroma = f("chroma", d.chroma),
            contrast = f("contrast", d.contrast), brightness = f("brightness", d.brightness), randomness = f("randomness", d.randomness), backgroundIndex = o.optInt("backgroundIndex", d.backgroundIndex),
            skin = en(o.optString("skin"), d.skin), gridColumns = o.optInt("gridColumns", d.gridColumns), tileSpacing = f("tileSpacing", d.tileSpacing), labelVisible = o.optBoolean("labelVisible", d.labelVisible),
            dayNight = o.optBoolean("dayNight", d.dayNight), seed = o.optLong("seed", d.seed), widgetStyle = en(o.optString("widgetStyle"), d.widgetStyle),
            widgetLabelVisible = o.optBoolean("widgetLabelVisible", d.widgetLabelVisible), wallpaperStyle = en(o.optString("wallpaperStyle"), d.wallpaperStyle),
        )
    }

    private inline fun <reified E : Enum<E>> en(s: String, d: E): E = enumValues<E>().firstOrNull { it.name == s } ?: d

    // ---- the active look ---------------------------------------------------------------------------------------------------------------------
    fun active(c: Context): ThemeSpec = runCatching { spec(JSONObject(sp(c).getString("spec", null)!!)) }.getOrDefault(ThemeSpec())
    fun saveActive(c: Context, t: ThemeSpec) { sp(c).edit().putString("spec", specJson(t).toString()).apply() }

    // ---- saved palettes and looks ------------------------------------------------------------------------------------------------------------
    fun palettes(c: Context): List<ColorPalette> = runCatching {
        val a = JSONArray(sp(c).getString("palettes", "[]")); (0 until a.length()).map { palette(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun savePalettes(c: Context, l: List<ColorPalette>) { sp(c).edit().putString("palettes", JSONArray(l.map { paletteJson(it) }).toString()).apply() }

    fun looks(c: Context): List<ThemeSpec> = runCatching {
        val a = JSONArray(sp(c).getString("looks", "[]")); (0 until a.length()).map { spec(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveLooks(c: Context, l: List<ThemeSpec>) { sp(c).edit().putString("looks", JSONArray(l.map { specJson(it) }).toString()).apply() }

    // ---- the per-app rules -------------------------------------------------------------------------------------------------------------------
    fun overrides(c: Context): Map<String, IconOverride> = runCatching {
        val a = JSONArray(sp(c).getString("overrides", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
            o.getString("key") to IconOverride(o.getString("key"), o.optBoolean("excluded"), o.optBoolean("locked"), o.optInt("colorIndex", -1), o.optString("styleId").ifEmpty { null }, o.optBoolean("favorite"))
        }
    }.getOrDefault(emptyMap())

    fun saveOverrides(c: Context, m: Map<String, IconOverride>) {
        // a rule that says nothing is not kept
        val a = JSONArray()
        m.values.filter { it.excluded || it.locked || it.colorIndex >= 0 || it.styleId != null || it.favorite }.forEach {
            a.put(JSONObject().put("key", it.key).put("excluded", it.excluded).put("locked", it.locked).put("colorIndex", it.colorIndex).put("styleId", it.styleId ?: "").put("favorite", it.favorite))
        }
        sp(c).edit().putString("overrides", a.toString()).apply()
    }

    // ---- settings ----------------------------------------------------------------------------------------------------------------------------
    fun exportSize(c: Context) = sp(c).getInt("exportSize", 192)
    fun setExportSize(c: Context, v: Int) { sp(c).edit().putInt("exportSize", v).apply() }
    fun dayNightAuto(c: Context) = sp(c).getBoolean("dayNightAuto", false)
    fun setDayNightAuto(c: Context, v: Boolean) { sp(c).edit().putBoolean("dayNightAuto", v).apply() }
    fun followWallpaper(c: Context) = sp(c).getBoolean("followWall", false)
    fun setFollowWallpaper(c: Context, v: Boolean) { sp(c).edit().putBoolean("followWall", v).apply() }
}
