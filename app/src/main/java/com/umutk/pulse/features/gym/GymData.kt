package com.umutk.pulse.features.gym

import android.content.Context
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import com.umutk.pulse.tr

/**
 * The exercise catalogue, the programs and the food table of the gym log. They come from the GymMane project (github.com/InlitX/GymMane, code GPL-3.0, exercise art
 * CC BY-SA 4.0 from Workout Guide and Everkinetic, see THIRD_PARTY.md), packed small by tools/gym_import.py into app/src/main/assets/gym.
 */
class GymEx(val id: String, val name: String, val primary: String, val secondary: List<String>, val equipment: String, val difficulty: String, val art: String, val steps: List<String>)

/** A day of a ready-made program: its name, the weekday (1 = Monday, 0 when free) and the exercises (by name) with their sets. */
class GymProgramDay(val program: String, val programName: String, val day: String, val weekday: Int, val items: List<Pair<String, Int>>)

/** A food with its values per 100 g, and the grams of one piece when it has one. */
class GymFood(val en: String, val trName: String, val kcal: Float, val protein: Float, val carb: Float, val fat: Float, val unitG: Int) {
    val name get() = tr(en, trName)
}

object GymData {
    @Volatile private var exs: List<GymEx>? = null
    @Volatile private var progs: List<GymProgramDay>? = null
    @Volatile private var foodList: List<GymFood>? = null
    private val artCache = HashMap<String, List<Path>>()

    private fun lines(c: Context, name: String): List<String> = runCatching { c.assets.open("gym/$name").bufferedReader().readLines() }.getOrDefault(emptyList())

    fun exercises(c: Context): List<GymEx> = exs ?: synchronized(this) {
        exs ?: lines(c, "exercises.tsv").mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 9) null else GymEx(f[0], f[1], f[2], f[3].split(',').filter { it.isNotBlank() }, f[4], f[5], f[6], f[8].split('|').filter { it.isNotBlank() })
        }.also { exs = it }
    }

    fun byId(c: Context, id: String): GymEx? = exercises(c).firstOrNull { it.id == id }
    fun byName(c: Context, name: String): GymEx? = exercises(c).firstOrNull { it.name.equals(name, true) }

    fun programs(c: Context): List<GymProgramDay> = progs ?: synchronized(this) {
        progs ?: lines(c, "programs.tsv").mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 5) null else GymProgramDay(f[0], f[1], f[2], f[3].toIntOrNull() ?: 0, f[4].split('|').mapNotNull { p -> p.substringAfterLast(':').toIntOrNull()?.let { n -> p.substringBeforeLast(':') to n } })
        }.also { progs = it }
    }

    fun foods(c: Context): List<GymFood> = foodList ?: synchronized(this) {
        foodList ?: lines(c, "foods.tsv").mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 7 || f[0].isBlank()) null else GymFood(f[0], f[1], f[2].toFloatOrNull() ?: 0f, f[3].toFloatOrNull() ?: 0f, f[4].toFloatOrNull() ?: 0f, f[5].toFloatOrNull() ?: 0f, f[6].toIntOrNull() ?: 0)
        }.also { foodList = it }
    }

    /** The frames (paths in a 512 x 512 box) of an exercise's picture; empty when it has none. */
    fun art(c: Context, id: String): List<Path> {
        if (id.isBlank()) return emptyList()
        synchronized(artCache) { artCache[id]?.let { return it } }
        val frames = lines(c, "art/$id.txt").filter { it.isNotBlank() }.mapNotNull { runCatching { PathParser().parsePathString(it).toPath() }.getOrNull() }
        synchronized(artCache) { artCache[id] = frames }
        return frames
    }

    /** The muscles in the order of the catalogue's size, with their names. */
    val muscles: List<Pair<String, String>> get() = listOf(
        "chest" to tr("Chest", "Göğüs"), "back" to tr("Back", "Sırt"), "shoulders" to tr("Shoulders", "Omuz"), "biceps" to tr("Biceps", "Biseps"), "triceps" to tr("Triceps", "Triseps"),
        "forearm" to tr("Forearm", "Ön kol"), "abdomen" to tr("Abs", "Karın"), "obliques" to tr("Obliques", "Yan karın"), "lowerback" to tr("Lower back", "Bel"), "trapezius" to tr("Traps", "Trapez"),
        "quads" to tr("Quads", "Ön bacak"), "hamstrings" to tr("Hamstrings", "Arka bacak"), "glutes" to tr("Glutes", "Kalça"), "calves" to tr("Calves", "Baldır"),
    )
    fun muscleName(id: String) = muscles.firstOrNull { it.first == id }?.second ?: id
}
