package com.umutk.pulse.features.gym

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** One muscle with the days since it was last trained (-1: never), and the exercises that are suggested for it. */
class GymSuggestion(val muscle: String, val days: Int, val exercises: List<GymEx>)

/** The coach of the gym log: what to train next (the muscles rested the longest), the weight to try next for an exercise, the medals. Everything is worked out from the log. */
object GymCoach {
    private const val DAY = 86_400_000L

    /** Days since each muscle (primary muscle of a logged exercise) was last trained, -1 when never. */
    fun recovery(c: Context): Map<String, Int> {
        val last = HashMap<String, Long>()
        GymStore.sessions(c).forEach { s -> s.sets.map { it.first }.distinct().forEach { id -> GymData.byId(c, id)?.primary?.let { m -> if ((last[m] ?: 0L) < s.t) last[m] = s.t } } }
        val now = System.currentTimeMillis()
        return GymData.muscles.associate { (m, _) -> m to (last[m]?.let { ((now - it) / DAY).toInt() } ?: -1) }
    }

    /** The muscles to train today: never trained first, then the longest rested (at least two days), three of them, each with four exercises (the ones logged most, then the first of the catalogue). */
    fun suggest(c: Context): List<GymSuggestion> {
        val rec = recovery(c)
        val main = listOf("chest", "back", "shoulders", "quads", "hamstrings", "glutes", "biceps", "triceps", "abdomen")
        val logged = GymStore.logged(c)
        return main.map { it to (rec[it] ?: -1) }.filter { it.second == -1 || it.second >= 2 }.sortedByDescending { if (it.second < 0) 1000 else it.second }.take(3).map { (m, d) ->
            val mine = GymData.exercises(c).filter { it.primary == m }
            val picks = (logged.mapNotNull { id -> mine.firstOrNull { it.id == id } } + mine.filter { it.art.isNotBlank() }).distinctBy { it.id }.take(4)
            GymSuggestion(m, d, picks)
        }
    }

    /** The weight and repetitions to try next time for an exercise, from its last session: more reps up to 10, then a heavier weight with 8. Null when it was never logged. */
    fun target(c: Context, ex: String): Pair<Float, Int>? {
        val h = GymStore.history(c, ex)
        val last = h.lastOrNull() ?: return null
        val w = last[1]; val r = last[2].toInt()
        return if (w <= 0f) 0f to (r + 1) else if (r >= 10) (w + if (w >= 40f) 2.5f else 1.25f) to 8 else w to (r + 1)
    }

    /** A session item whose sets already hold the target (if there is one). */
    fun newItem(c: Context, ex: String, sets: Int): GItem {
        val t = target(c, ex)
        return GItem(ex, MutableList(sets.coerceAtLeast(1)) { if (t != null) GSet(t.first, t.second) else GSet() })
    }

    /** A medal: its name, what it asks and whether it is earned. */
    class Medal(val name: String, val ask: String, val got: Boolean)

    fun medals(c: Context): List<Medal> {
        val ss = GymStore.sessions(c)
        val vol = ss.sumOf { it.volume.toDouble() }
        val sets = ss.sumOf { it.sets.size }
        // the longest run of weeks with at least one session
        val weeks = ss.map { it.t / (7 * DAY) }.distinct().sorted()
        var best = 0; var run = 0; var prev = -10L
        weeks.forEach { w -> run = if (w == prev + 1) run + 1 else 1; prev = w; if (run > best) best = run }
        val prs = GymStore.logged(c).count { id -> GymStore.history(c, id).let { h -> h.size >= 2 && h.last()[1] > h.dropLast(1).maxOf { it[1] } } }
        fun m(en: String, trn: String, askEn: String, askTr: String, ok: Boolean) = Medal(tr(en, trn), tr(askEn, askTr), ok)
        return listOf(
            m("First rep", "İlk tekrar", "Log a session", "Bir antrenman kaydet", ss.isNotEmpty()),
            m("Regular", "Düzenli", "10 sessions", "10 antrenman", ss.size >= 10),
            m("Committed", "Kararlı", "50 sessions", "50 antrenman", ss.size >= 50),
            m("Centurion", "Yüzbaşı", "100 sessions", "100 antrenman", ss.size >= 100),
            m("One tonne", "Bir ton", "Lift 1,000 kg in total", "Toplam 1.000 kg kaldır", vol >= 1_000),
            m("Ten tonnes", "On ton", "Lift 10,000 kg in total", "Toplam 10.000 kg kaldır", vol >= 10_000),
            m("Hundred tonnes", "Yüz ton", "Lift 100,000 kg in total", "Toplam 100.000 kg kaldır", vol >= 100_000),
            m("500 sets", "500 set", "Log 500 sets", "500 set kaydet", sets >= 500),
            m("Four weeks", "Dört hafta", "Train in 4 weeks in a row", "Üst üste 4 hafta antrenman yap", best >= 4),
            m("Twelve weeks", "On iki hafta", "Train in 12 weeks in a row", "Üst üste 12 hafta antrenman yap", best >= 12),
            m("Record breaker", "Rekortmen", "Beat the record of 3 exercises", "3 egzersizde rekor kır", prs >= 3),
        )
    }
}

/** The "train today" card: the muscles that rested longest, each with its exercises and a button that puts them in the session. */
@Composable
internal fun SuggestCard(onStart: (List<GymEx>) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val list = remember { GymCoach.suggest(ctx) }
    val rec = remember { GymCoach.recovery(ctx) }
    PCard {
        Text(tr("Suggested today", "Bugün için öneri"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        list.forEach { s ->
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(GymData.muscleName(s.muscle), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (s.days < 0) tr("never trained", "hiç çalışılmadı") else tr("${s.days} days ago", "${s.days} gün önce"), color = T.sub, fontSize = 12.sp)
                }
                Text(s.exercises.joinToString(" · ") { it.name }, color = T.sub, fontSize = 12.sp)
                PButton(tr("Start with these", "Bunlarla başla"), compact = true, modifier = Modifier.padding(top = 4.dp)) { onStart(s.exercises) }
            }
        }
        if (list.isEmpty()) Text(tr("Everything is rested or was trained lately. Pick what you like.", "Hepsi dinlendi ya da yakın zamanda çalışıldı. Canının istediğini seç."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Text(tr("Rest by muscle", "Kasların dinlenmesi"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GymData.muscles.forEach { (m, n) ->
                val d = rec[m] ?: -1
                Text(n + " " + (if (d < 0) "·" else "${d}d"), color = if (d < 0 || d >= 3) Look.accent else T.sub, fontSize = 12.sp)
            }
        }
    }
}

/** The medals of the log, earned ones marked. */
@Composable
internal fun MedalsCard() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val medals = remember { GymCoach.medals(ctx) }
    PCard {
        Text(tr("Medals", "Madalyalar") + " ${medals.count { it.got }}/${medals.size}", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        medals.forEach { m ->
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(if (m.got) "check" else "chevron", if (m.got) Look.accent else T.sub, 18.dp)
                Column(Modifier.padding(start = 10.dp)) {
                    Text(m.name, color = if (m.got) T.text else T.sub, fontSize = 14.sp, fontWeight = if (m.got) FontWeight.Medium else FontWeight.Normal)
                    Text(m.ask, color = T.sub, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * The gym part of the Health page: workouts and the weight lifted this week, today's food against the goal, the muscle to train next, and a way into the log
 * (0 train, 5 food, 6 recap).
 */
@Composable
fun GymSummary(open: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val week = remember { System.currentTimeMillis() - 7 * 86_400_000L }
    val ss = remember { GymStore.sessions(ctx) }
    val thisWeek = ss.filter { it.t >= week }
    val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
    val kcal = remember { GymStore.foods(ctx).filter { it.t >= cal.timeInMillis }.sumOf { it.kcal } }
    val goal = remember { GymStore.kcalGoal(ctx) }
    val next = remember { GymCoach.suggest(ctx).firstOrNull() }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Gym", "Spor"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PButton(tr("Start", "Başla"), compact = true) { open(0) }
        }
        Text("${thisWeek.size} " + tr("workouts this week", "antrenman bu hafta") + " · %,d kg".format(thisWeek.sumOf { it.volume.toDouble() }.toInt()), color = T.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Text(tr("Food today", "Bugün yenen") + ": $kcal / $goal kcal", color = T.sub, fontSize = 13.sp)
        next?.let { Text(tr("Next: ", "Sıradaki: ") + GymData.muscleName(it.muscle), color = T.sub, fontSize = 13.sp) }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Food", "Beslenme"), compact = true) { open(5) }
            PButton(tr("Receipt", "Fiş"), compact = true) { open(6) }
            PButton(tr("Library", "Egzersizler"), compact = true) { open(1) }
        }
    }
}
