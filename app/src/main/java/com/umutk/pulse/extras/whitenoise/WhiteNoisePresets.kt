package com.umutk.pulse.extras.whitenoise

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AmbientPreset(val name: String, val en: String, val tr: String, val levels: Map<String, Int>, val user: Boolean = false)

/** Calm sounds first; ids are the built-in loops plus the standard pack ids (a missing one is skipped when applied). */
val CALM_ORDER = listOf("rain", "waves", "wind", "dere", "damlama", "orman", "bell", "white", "coffee", "fire", "steam", "heartbeat", "vinyl", "traffic")

private fun p(en: String, tr: String, vararg l: Pair<String, Int>) = AmbientPreset(en, en, tr, mapOf(*l))

val BuiltInPresets = listOf(
    p("Calm", "Sakin", "rain" to 25, "waves" to 30, "wind" to 15, "dere" to 20),
    p("Gentle rain", "Hafif yağmur", "rain" to 40, "damlama" to 20, "wind" to 10),
    p("Seaside", "Sahil", "waves" to 55, "wind" to 20),
    p("Riverside", "Nehir kenarı", "dere" to 45, "orman" to 30, "wind" to 15),
    p("Sleep", "Uyku", "rain" to 30, "white" to 30, "damlama" to 15),
    p("Meditation", "Meditasyon", "bell" to 35, "wind" to 20, "dere" to 25),
    p("Night forest", "Gece ormanı", "orman" to 45, "wind" to 20, "damlama" to 10),
    p("Focus", "Odak", "coffee" to 25, "rain" to 20, "white" to 15),
    p("Rainforest", "Yağmur ormanı", "rain" to 45, "orman" to 45, "damlama" to 25),
    p("Drip and chime", "Damla ve çan", "damlama" to 35, "bell" to 30),
    p("Deep space", "Uzay sessizliği", "pembe_gurultu" to 30, "bell" to 15),
    p("Lazy afternoon", "Tembel öğleden sonra", "coffee" to 35, "wind" to 15, "bell" to 15),
    p("Morning coffee", "Sabah kahvesi", "coffee" to 50, "bell" to 15, "rain" to 15),
    p("Mountain wind", "Dağ rüzgarı", "wind" to 60, "bell" to 25, "orman" to 25),
    p("Rainy cafe", "Yağmurlu kafe", "rain" to 45, "coffee" to 55, "konusma" to 10),
    p("Winter night", "Kış gecesi", "wind" to 45, "fire" to 40),
    p("Fireplace", "Şömine", "fire" to 60, "wind" to 10),
    p("Campfire", "Kamp ateşi", "fire" to 65, "orman" to 35, "wind" to 20),
    p("Train ride", "Tren yolculuğu", "tren" to 55, "wind" to 15),
    p("Stormy night", "Fırtınalı gece", "gok_gultusu" to 60, "rain" to 45, "wind" to 40),
    p("Kettle and rain", "Çay ve yağmur", "steam" to 25, "rain" to 35),
    p("Heartbeat sleep", "Kalp atışı uykusu", "heartbeat" to 40, "rain" to 20, "wind" to 10),
    p("Vinyl evening", "Plak akşamı", "vinyl" to 40, "coffee" to 25, "bell" to 10),
    p("City calm", "Sakin şehir", "traffic" to 45, "rain" to 25),
    p("Quiet study", "Sessiz çalışma", "white" to 20, "vinyl" to 20, "coffee" to 15),
    p("Stormy cabin", "Fırtınalı kulübe", "fire" to 45, "gok_gultusu" to 35, "rain" to 25),
    p("Ocean drive", "Sahil yolu", "waves" to 40, "traffic" to 20, "wind" to 15),
    p("Tea time", "Çay vakti", "steam" to 30, "coffee" to 35, "bell" to 15),
)

val DefaultPreset get() = BuiltInPresets[0]

/** User presets as JSON in SharedPreferences: [{"name":..,"levels":{id:level}}]. */
object UserPresets {
    private fun sp(c: Context) = c.getSharedPreferences("ambient_presets", Context.MODE_PRIVATE)

    fun load(c: Context): List<AmbientPreset> {
        val a = runCatching { JSONArray(sp(c).getString("list", "[]")) }.getOrNull() ?: return emptyList()
        return (0 until a.length()).mapNotNull {
            runCatching {
                val o = a.getJSONObject(it); val l = o.getJSONObject("levels")
                val n = o.getString("name")
                AmbientPreset(n, n, n, l.keys().asSequence().associateWith { k -> l.getInt(k) }, true)
            }.getOrNull()
        }
    }

    fun save(c: Context, list: List<AmbientPreset>) {
        val a = JSONArray()
        list.forEach { pr -> a.put(JSONObject().put("name", pr.name).put("levels", JSONObject().also { o -> pr.levels.forEach { (k, v) -> o.put(k, v) } })) }
        sp(c).edit().putString("list", a.toString()).apply()
    }
}
