package com.umutk.pulse.features.gym

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** One set of a session: the exercise, the weight in kg (0 for body weight) and the repetitions; [done] once it is ticked. */
class GSet(var w: Float = 0f, var r: Int = 0, var done: Boolean = false)
/** One exercise of the session being built, with its sets. */
class GItem(val ex: String, val sets: MutableList<GSet> = mutableListOf(GSet()))
/** A session that was finished. [sets] are (exercise id, weight, reps). */
class GSession(val t: Long, val durSec: Int, val name: String, val sets: List<Triple<String, Float, Int>>) {
    val volume get() = sets.sumOf { (it.second * it.third).toDouble() }.toFloat()
}
class GMeasure(val t: Long, val kind: String, val v: Float)
class GFood(val t: Long, val name: String, val grams: Int, val kcal: Int, val p: Float, val c: Float, val f: Float)

/** The gym log, kept in Pulse's own preferences (nothing leaves the phone): sessions, the session in progress, body numbers, foods and routines. */
object GymStore {
    private fun sp(c: Context) = c.getSharedPreferences("pulse_gym", Context.MODE_PRIVATE)
    private fun arr(c: Context, k: String) = runCatching { JSONArray(sp(c).getString(k, "[]")) }.getOrDefault(JSONArray())

    // ---- finished sessions
    fun sessions(c: Context): List<GSession> = arr(c, "sessions").let { a ->
        (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i); val s = o.getJSONArray("s")
                GSession(o.getLong("t"), o.optInt("d"), o.optString("n"), (0 until s.length()).map { j -> val x = s.getJSONArray(j); Triple(x.getString(0), x.getDouble(1).toFloat(), x.getInt(2)) })
            }.getOrNull()
        }.sortedByDescending { it.t }
    }
    fun addSession(c: Context, s: GSession) {
        val a = arr(c, "sessions")
        a.put(JSONObject().put("t", s.t).put("d", s.durSec).put("n", s.name).put("s", JSONArray().also { j -> s.sets.forEach { j.put(JSONArray().put(it.first).put(it.second.toDouble()).put(it.third)) } }))
        sp(c).edit().putString("sessions", a.toString()).apply()
    }
    fun deleteSession(c: Context, t: Long) {
        val a = arr(c, "sessions"); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optLong("t") != t) n.put(a.getJSONObject(i))
        sp(c).edit().putString("sessions", n.toString()).apply()
    }

    // ---- the session in progress (kept, so leaving the page loses nothing)
    fun draft(c: Context): Pair<Long, List<GItem>> {
        val o = runCatching { JSONObject(sp(c).getString("draft", "{}")) }.getOrDefault(JSONObject())
        val a = o.optJSONArray("i") ?: JSONArray()
        val items = (0 until a.length()).mapNotNull { i ->
            runCatching {
                val x = a.getJSONObject(i); val s = x.getJSONArray("s")
                GItem(x.getString("e"), (0 until s.length()).map { j -> val y = s.getJSONArray(j); GSet(y.getDouble(0).toFloat(), y.getInt(1), y.getBoolean(2)) }.toMutableList())
            }.getOrNull()
        }
        return o.optLong("t") to items
    }
    fun saveDraft(c: Context, start: Long, items: List<GItem>) {
        val a = JSONArray()
        items.forEach { it0 -> a.put(JSONObject().put("e", it0.ex).put("s", JSONArray().also { j -> it0.sets.forEach { s -> j.put(JSONArray().put(s.w.toDouble()).put(s.r).put(s.done)) } })) }
        sp(c).edit().putString("draft", JSONObject().put("t", start).put("i", a).toString()).apply()
    }
    fun clearDraft(c: Context) { sp(c).edit().remove("draft").apply() }

    // ---- numbers
    /** Estimated one-rep max (Epley); 0 for a set with no weight. */
    fun oneRm(w: Float, r: Int): Float = if (w <= 0f || r <= 0) 0f else w * (1f + r / 30f)

    /** The best set (by estimated one-rep max) of an exercise in each session, oldest first: (time, weight, reps, 1RM). */
    fun history(c: Context, ex: String): List<Array<Float>> = sessions(c).sortedBy { it.t }.mapNotNull { s ->
        val best = s.sets.filter { it.first == ex && it.third > 0 }.maxByOrNull { if (it.second > 0f) oneRm(it.second, it.third) else it.third.toFloat() } ?: return@mapNotNull null
        arrayOf(s.t.toFloat(), best.second, best.third.toFloat(), oneRm(best.second, best.third))
    }
    /** The heaviest weight ever lifted for an exercise (a record to beat), 0 when there is none. */
    fun record(c: Context, ex: String): Float = sessions(c).flatMap { it.sets }.filter { it.first == ex }.maxOfOrNull { it.second } ?: 0f
    /** The exercises that were ever logged, most often first. */
    fun logged(c: Context): List<String> = sessions(c).flatMap { it.sets }.groupingBy { it.first }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    // ---- body measures
    fun measures(c: Context): List<GMeasure> = arr(c, "measures").let { a -> (0 until a.length()).mapNotNull { i -> runCatching { val o = a.getJSONObject(i); GMeasure(o.getLong("t"), o.getString("k"), o.getDouble("v").toFloat()) }.getOrNull() }.sortedBy { it.t } }
    fun addMeasure(c: Context, kind: String, v: Float) {
        val a = arr(c, "measures"); a.put(JSONObject().put("t", System.currentTimeMillis()).put("k", kind).put("v", v.toDouble()))
        sp(c).edit().putString("measures", a.toString()).apply()
    }
    fun deleteMeasure(c: Context, t: Long) {
        val a = arr(c, "measures"); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optLong("t") != t) n.put(a.getJSONObject(i))
        sp(c).edit().putString("measures", n.toString()).apply()
    }

    // ---- food log
    fun foods(c: Context): List<GFood> = arr(c, "food").let { a -> (0 until a.length()).mapNotNull { i -> runCatching { val o = a.getJSONObject(i); GFood(o.getLong("t"), o.getString("n"), o.getInt("g"), o.getInt("k"), o.getDouble("p").toFloat(), o.getDouble("c").toFloat(), o.getDouble("f").toFloat()) }.getOrNull() } }
    fun addFood(c: Context, f: GymFood, grams: Int) {
        val k = grams / 100f
        val a = arr(c, "food")
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("n", f.name).put("g", grams).put("k", (f.kcal * k).roundToInt()).put("p", (f.protein * k).toDouble()).put("c", (f.carb * k).toDouble()).put("f", (f.fat * k).toDouble()))
        sp(c).edit().putString("food", a.toString()).apply()
    }
    fun deleteFood(c: Context, t: Long) {
        val a = arr(c, "food"); val n = JSONArray()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optLong("t") != t) n.put(a.getJSONObject(i))
        sp(c).edit().putString("food", n.toString()).apply()
    }
    fun kcalGoal(c: Context) = sp(c).getInt("kcalGoal", 2200)
    fun setKcalGoal(c: Context, v: Int) = sp(c).edit().putInt("kcalGoal", v).apply()
    fun restSec(c: Context) = sp(c).getInt("rest", 90)
    fun setRestSec(c: Context, v: Int) = sp(c).edit().putInt("rest", v).apply()

    // ---- routines saved from a session: name and (exercise id, sets)
    fun routines(c: Context): List<Pair<String, List<Pair<String, Int>>>> = arr(c, "routines").let { a ->
        (0 until a.length()).mapNotNull { i -> runCatching { val o = a.getJSONObject(i); val s = o.getJSONArray("i"); o.getString("n") to (0 until s.length()).map { j -> val x = s.getJSONArray(j); x.getString(0) to x.getInt(1) } }.getOrNull() }
    }
    fun addRoutine(c: Context, name: String, items: List<Pair<String, Int>>) {
        val a = arr(c, "routines"); a.put(JSONObject().put("n", name).put("i", JSONArray().also { j -> items.forEach { j.put(JSONArray().put(it.first).put(it.second)) } }))
        sp(c).edit().putString("routines", a.toString()).apply()
    }
    fun deleteRoutine(c: Context, index: Int) {
        val a = arr(c, "routines"); val n = JSONArray()
        for (i in 0 until a.length()) if (i != index) n.put(a.getJSONObject(i))
        sp(c).edit().putString("routines", n.toString()).apply()
    }
}
