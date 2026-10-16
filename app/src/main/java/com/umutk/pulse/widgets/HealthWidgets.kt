package com.umutk.pulse.widgets

import android.content.Context
import android.widget.RemoteViews
import com.umutk.pulse.Opt
import com.umutk.pulse.R
import com.umutk.pulse.features.ActivityType
import com.umutk.pulse.features.HealthStore
import com.umutk.pulse.features.fmtDur
import com.umutk.pulse.features.loadActLog
import com.umutk.pulse.tr

/** The health widgets read the "health" prefs the Health screen fills (steps, goals, heart rate, activity log); no loop of their own. No water or sleep: nothing on the phone measures them. */
object HealthWidgets {
    val all: List<PulseWidget> by lazy { listOf(StepsWidget(), DistanceWidget(), HeartWidget(), ActivityWidget(), WeekStepsWidget()) }

    /** Prepares the shared prefs; a real placed widget (not the in-app preview) also takes one fresh counter reading. */
    internal fun prefs(ctx: Context): android.content.SharedPreferences {
        Opt.load(ctx)
        if (textId != 0) HealthStore.peek(ctx)
        return HealthStore.prefs(ctx)
    }
}

class StepsWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = HealthWidgets.prefs(ctx)
        val t = HealthStore.today(p); val g = HealthStore.goalSteps(p)
        val big = "%,d".format(t)
        return views(ctx, s, R.layout.widget_health, tr("Steps today", "Bugünkü adımlar"), big, "health",
            Tile(big, listOf(tr("of %,d steps", "%,d adım hedefi").format(g), "${t * 100 / g}%" + HealthStore.streak(p).let { if (it > 1) " · " + tr("$it day streak", "$it gün seri") else "" }),
                (t.toFloat() / g).coerceIn(0f, 1f), tone = if (t >= g) 0xFF43A047.toInt() else 0))
    }
}

class DistanceWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = HealthWidgets.prefs(ctx)
        val t = HealthStore.today(p); val kcal = HealthStore.kcal(p, t); val g = HealthStore.goalKcal(p)
        val km = "%.2f km".format(HealthStore.km(p, t))
        return views(ctx, s, R.layout.widget_health, tr("Distance and calories", "Mesafe ve kalori"), km, "health",
            Tile(km, listOf("$kcal kcal", tr("of $g kcal goal (estimate)", "$g kcal hedef (tahmin)")), (kcal.toFloat() / g).coerceIn(0f, 1f)))
    }
}

class HeartWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = HealthWidgets.prefs(ctx)
        val bpm = p.getInt("bpm", 0); val at = p.getLong("bpm_t", 0L)
        val big = if (bpm > 0) "$bpm bpm" else "—"
        val ago = if (at > 0) (System.currentTimeMillis() - at) / 60_000 else -1L
        val when_ = when { ago < 0 -> tr("No reading yet", "Henüz ölçüm yok"); ago < 60 -> tr("$ago min ago", "$ago dk önce"); ago < 1440 -> tr("${ago / 60} h ago", "${ago / 60} sa önce"); else -> tr("${ago / 1440} d ago", "${ago / 1440} g önce") }
        return views(ctx, s, R.layout.widget_health, tr("Heart rate", "Nabız"), big, "health",
            Tile(big, listOf(when_), series = HealthStore.hrLog(p).map { it.second.toFloat() }, seriesMax = 0f))
    }
}

class ActivityWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = HealthWidgets.prefs(ctx)
        val m = HealthStore.activeMinutesToday(p); val g = HealthStore.goalMinutes(p)
        val last = loadActLog(p).lastOrNull()
        val big = tr("$m min", "$m dk")
        return views(ctx, s, R.layout.widget_health, tr("Activity today", "Bugünkü aktivite"), big, "health",
            Tile(big, listOf(tr("of $g min goal", "$g dk hedef"), last?.let { ActivityType.fromName(it.key).label + " · " + fmtDur(it.dur) } ?: tr("No activity yet", "Henüz aktivite yok")),
                (m.toFloat() / g).coerceIn(0f, 1f), tone = if (m >= g) 0xFF43A047.toInt() else 0))
    }
}

class WeekStepsWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val p = HealthWidgets.prefs(ctx)
        val d = HealthStore.days(p, 7).map { it.second }
        val top = maxOf(HealthStore.goalSteps(p).toLong(), (d.maxOrNull() ?: 0L)).toFloat()
        val avg = "%,d".format(d.average().toLong())
        return views(ctx, s, R.layout.widget_health, tr("Steps, 7 days", "Adımlar, 7 gün"), avg, "health",
            Tile(avg, listOf(tr("average a day", "günlük ortalama"), tr("Today %,d", "Bugün %,d").format(d.last())), bars = d.map { it / top }))
    }
}
