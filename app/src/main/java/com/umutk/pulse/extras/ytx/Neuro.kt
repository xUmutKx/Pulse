package com.umutk.pulse.extras.ytx

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * YTXTube's taste engine, kept on the phone and modelled on Flow's FlowNeuro (GPL-3.0): the words of what is watched, searched and rejected
 * weigh the topics, the channels weigh up or down, and five traits (pacing, complexity, duration, live, breadth) describe the person, for the
 * whole profile and for the time of day. The feed is put in that order; nothing leaves the phone.
 */
object Neuro {
    data class Traits(val pacing: Float, val complexity: Float, val duration: Float, val live: Float, val breadth: Float)
    enum class Persona(val icon: String, val en: String, val tr: String, val descEn: String, val descTr: String) {
        INITIATE("🌱", "The Initiate", "Çaylak", "Still learning what you like.", "Neyi sevdiğini hâlâ öğreniyor."),
        AUDIOPHILE("🎧", "The Audiophile", "Müzik Tutkunu", "Music first, everything else second.", "Önce müzik, gerisi sonra."),
        LIVEWIRE("🔴", "The Livewire", "Canlı Yayıncı", "Live streams are your thing.", "Canlı yayınlar senin işin."),
        NIGHT_OWL("🦉", "The Night Owl", "Gece Kuşu", "Most of your watching happens after dark.", "İzlediklerinin çoğu karanlıktan sonra."),
        BINGER("🍿", "The Binger", "Maratoncu", "One more video, then another.", "Bir video daha, bir tane daha."),
        SCHOLAR("🎓", "The Scholar", "Bilgin", "Long, dense and thoughtful.", "Uzun, yoğun ve düşünceli."),
        DEEP_DIVER("🤿", "The Deep Diver", "Derine Dalan", "You like to settle into long videos.", "Uzun videolara gömülmeyi seviyor."),
        SKIMMER("⚡", "The Skimmer", "Gezgin", "Quick, short and fast.", "Hızlı, kısa ve çabuk."),
        SPECIALIST("🎯", "The Specialist", "Uzman", "A few topics, followed closely.", "Birkaç konu, yakından takip."),
        EXPLORER("🧭", "The Explorer", "Kâşif", "Chaotic and beautiful. A bit of everything.", "Karmaşık ve güzel. Her şeyden biraz."),
    }

    private fun p() = runCatching { YTXTubeApplication.appContext.getSharedPreferences("ytx_neuro", Context.MODE_PRIVATE) }.getOrNull()
    private var loaded = false
    private var topics = HashMap<String, Float>()
    private var channels = HashMap<String, Float>()
    private var blocked = HashSet<String>()
    private var profile = floatArrayOf(.5f, .5f, .5f, 0f)
    private val buckets = Array(4) { floatArrayOf(.5f, .5f, .5f, 0f, 0f) } // the last number counts what the bucket learned from
    private var interactions = 0
    private var lastDecay = 0L
    private var lastPersona = ""
    private var stability = 0
    /** Changes with every lesson, so the screens that show the taste refresh. */
    var rev by mutableStateOf(0)
        private set
    /** Learning off: nothing new is remembered (what is already known still orders the feed). */
    var paused by mutableStateOf(false)
        private set

    private val stop = ("the and for with you your this that from are was were have has had not but all can will just what when how why who our out new "
        + "official video music lyrics audio full part episode hd 4k live ft feat bir ve bu için ile da de ne mi mı çok daha gibi olan en ama veya ya "
        + "resmi video klip şarkı sözleri sözlü bölüm").split(' ').toHashSet()

    private val splitter = Regex("[^\\p{L}\\p{Nd}]+")

    private fun load() {
        if (loaded) return
        val sp = p() ?: return
        loaded = true
        paused = sp.getBoolean("paused", false)
        topics = parse(sp.getString("t", ""))
        channels = parse(sp.getString("c", ""))
        blocked = (sp.getStringSet("b", emptySet()) ?: emptySet()).toHashSet()
        interactions = sp.getInt("n", 0)
        lastDecay = sp.getLong("d", 0L)
        lastPersona = sp.getString("lp", "") ?: ""
        stability = sp.getInt("ps", 0)
        sp.getString("pr", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.let { profile = it.toFloatArray() }
        sp.getString("bk", null)?.split(';')?.forEachIndexed { i, s -> s.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 5 && i < 4 }?.let { v -> buckets[i] = v.toFloatArray() } }
        decay()
    }

    private fun parse(s: String?): HashMap<String, Float> {
        val m = HashMap<String, Float>()
        s?.split('\n')?.forEach { l -> l.split('\t').takeIf { it.size == 2 }?.let { m[it[0]] = it[1].toFloatOrNull() ?: 0f } }
        return m
    }

    /** What was learned fades: half after about two months, so an old phase gives way. */
    private fun decay() {
        val now = System.currentTimeMillis()
        if (lastDecay == 0L) { lastDecay = now; return }
        val days = (now - lastDecay) / 86_400_000.0
        if (days < 1) return
        val f = .5.pow(days / 60.0).toFloat()
        topics = HashMap(topics.mapValues { it.value * f }.filterValues { abs(it) >= .15f })
        channels = HashMap(channels.mapValues { it.value * f }.filterValues { abs(it) >= .15f })
        lastDecay = now
        save()
    }

    private fun save() {
        rev++
        val t = topics.entries.sortedByDescending { abs(it.value) }.take(600)
        val c = channels.entries.sortedByDescending { abs(it.value) }.take(300)
        p()?.edit()?.putBoolean("paused", paused)?.putString("t", t.joinToString("\n") { it.key + "\t" + "%.2f".format(java.util.Locale.US, it.value) })
            ?.putString("c", c.joinToString("\n") { it.key + "\t" + "%.2f".format(java.util.Locale.US, it.value) })?.putStringSet("b", blocked)
            ?.putInt("n", interactions)?.putLong("d", lastDecay)?.putString("lp", lastPersona)?.putInt("ps", stability)
            ?.putString("pr", profile.joinToString(",") { "%.3f".format(java.util.Locale.US, it) })
            ?.putString("bk", buckets.joinToString(";") { b -> b.joinToString(",") { "%.3f".format(java.util.Locale.US, it) } })?.apply()
    }

    // ───────── words ─────────
    /** The topic words of a title: lower case, no filler words, no numbers alone. */
    fun words(title: String): List<String> =
        title.lowercase().split(splitter).filter { it.length >= 3 && it !in stop && !it.all(Char::isDigit) }.distinct()

    private fun chKey(v: VideoItem) = (v.channelUrl ?: v.channel).trim()

    // ───────── learning ─────────
    private fun bucket(): Int {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (h) { in 5..10 -> 0; in 11..16 -> 1; in 17..21 -> 2; else -> 3 }
    }

    private fun sample(v: VideoItem): FloatArray {
        val dur = (ln(1.0 + v.durationSec / 60.0) / ln(61.0)).toFloat().coerceIn(0f, 1f)
        val ws = v.title.split(' ').filter { it.isNotBlank() }
        val caps = ws.count { w -> w.length > 2 && w.all { it.isUpperCase() || !it.isLetter() } }
        val marks = v.title.count { it == '!' || it == '?' || it == '|' || it == '[' }
        val pacing = (.55f * (1f - dur) + .45f * min(1f, (caps + marks) / 3f)).coerceIn(0f, 1f)
        val avg = if (ws.isEmpty()) 4f else ws.sumOf { it.length }.toFloat() / ws.size
        val complexity = (.5f * ((avg - 3f) / 5f).coerceIn(0f, 1f) + .5f * dur).coerceIn(0f, 1f)
        return floatArrayOf(pacing, complexity, dur, if (v.isLive) 1f else 0f)
    }

    private fun breadth(): Float {
        val top = topics.values.maxOrNull() ?: return 0f
        if (top <= 0f) return 0f
        return (topics.values.count { it >= top * .25f } / 12f).coerceIn(0f, 1f)
    }

    /** A lesson: [w] positive for something watched, searched or liked, negative for something rejected. */
    fun learn(v: VideoItem, w: Float) {
        load()
        if (paused) return
        val ws = words(v.title)
        for (t in ws) topics[t] = ((topics[t] ?: 0f) + w).coerceIn(-12f, 40f)
        channels[chKey(v)] = ((channels[chKey(v)] ?: 0f) + w).coerceIn(-12f, 40f)
        channelNames[chKey(v)] = v.channel
        if (w > 0) {
            val s = sample(v)
            val a = if (interactions < 5) .3f else .06f
            for (i in 0..3) profile[i] += (s[i] - profile[i]) * a
            val b = buckets[bucket()]
            val ab = if (b[4] < 3f) .3f else .08f
            for (i in 0..3) b[i] += (s[i] - b[i]) * ab
            b[4] += 1f
            interactions++
        }
        save()
    }

    /** What was searched for counts like a small watch of each word. */
    fun searched(query: String) {
        load()
        if (paused) return
        for (t in words(query)) topics[t] = ((topics[t] ?: 0f) + 1.5f).coerceIn(-12f, 40f)
        save()
    }

    private val channelNames = HashMap<String, String>()
    private fun chName(k: String) = channelNames[k] ?: k.substringAfterLast('/').removePrefix("@")

    // ───────── ordering ─────────
    /** How well a video fits: its words, its channel, and how close its length is to the person's. */
    fun score(v: VideoItem): Float {
        load()
        val ws = words(v.title)
        var s = 0f
        for (t in ws) s += topics[t] ?: 0f
        if (ws.isNotEmpty()) s /= sqrt(ws.size.toFloat())
        s += .6f * (channels[chKey(v)] ?: 0f)
        if (interactions >= 8) {
            val dur = (ln(1.0 + v.durationSec / 60.0) / ln(61.0)).toFloat().coerceIn(0f, 1f)
            s += 1.2f * (1f - abs(dur - nowTraits().duration) * 2f)
        }
        return s
    }

    fun isBlocked(v: VideoItem): Boolean {
        load()
        if (blocked.isEmpty()) return false
        val ws = words(v.title)
        return ws.any { it in blocked }
    }

    /** The feed in the person's order. The feed's own order stays between videos that weigh the same (a small lift for the early ones). */
    fun rank(list: List<VideoItem>): List<VideoItem> {
        load()
        if (interactions < 3 && topics.isEmpty()) return list
        val n = list.size.coerceAtLeast(1)
        return list.withIndex().sortedByDescending { (i, v) -> score(v).coerceIn(-10f, 25f) + 1.5f * (1f - i.toFloat() / n) }.map { it.value }
    }

    // ───────── what the screens show ─────────
    fun maturity(): Float { load(); return (interactions / 60f).coerceIn(0f, 1f) }
    fun interactions(): Int { load(); return interactions }
    fun traits(): Traits { load(); return Traits(profile[0], profile[1], profile[2], profile[3], breadth()) }
    fun nowTraits(): Traits {
        load()
        val b = buckets[bucket()]
        return if (b[4] >= 3f) Traits(b[0], b[1], b[2], b[3], breadth()) else traits()
    }

    fun persona(): Persona {
        load()
        if (interactions < 15) return Persona.INITIATE
        val ranked = topics.values.filter { it > 0 }.sortedDescending()
        val top = ranked.firstOrNull() ?: 0f
        val diversity = if (ranked.size >= 5 && top > 0) ranked[4] / top else 0f
        val total = ranked.sum()
        val music = topics.filterKeys { it in setOf("song", "remix", "lofi", "playlist", "audio", "mix", "dj", "beat") || it.startsWith("feat") }.values.filter { it > 0 }.sum()
        val night = buckets[3][4]; val morning = buckets[0][4]
        val raw = when {
            total > 0 && music > total * .4f -> Persona.AUDIOPHILE
            profile[3] > .6f -> Persona.LIVEWIRE
            night > morning * 1.5f && night > 5f -> Persona.NIGHT_OWL
            interactions > 500 && profile[0] > .65f -> Persona.BINGER
            profile[1] > .75f -> Persona.SCHOLAR
            profile[2] > .70f -> Persona.DEEP_DIVER
            profile[2] < .35f && profile[0] > .6f -> Persona.SKIMMER
            diversity in 0.001f..0.25f -> Persona.SPECIALIST
            else -> Persona.EXPLORER
        }
        // a new persona must hold for a while before it replaces the old one
        val last = Persona.entries.firstOrNull { it.name == lastPersona }
        if (last == null) { lastPersona = raw.name; return raw }
        if (raw == last) { stability = 0; return last }
        stability++
        if (stability >= 5) { lastPersona = raw.name; stability = 0; save(); return raw }
        return last
    }

    data class Interest(val topic: String, val weight: Float)
    fun interests(n: Int = 30): List<Interest> { load(); return topics.filter { it.value >= 1.5f && it.key !in blocked }.entries.sortedByDescending { it.value }.take(n).map { Interest(it.key, it.value) } }
    data class Chan(val key: String, val name: String, val weight: Float)
    fun favouriteChannels(n: Int = 12): List<Chan> { load(); return channels.filter { it.value >= 2f }.entries.sortedByDescending { it.value }.take(n).map { Chan(it.key, chName(it.key), it.value) } }
    fun blockedTopics(): List<String> { load(); return blocked.sorted() }

    // ───────── what the person changes ─────────
    fun forget(topic: String) { load(); topics.remove(topic); save() }
    fun block(topic: String) { load(); topics.remove(topic); blocked.add(topic.lowercase()); save() }
    fun unblock(topic: String) { load(); blocked.remove(topic); save() }
    fun forgetChannel(key: String) { load(); channels.remove(key); save() }
    fun setPaused(v: Boolean) { load(); paused = v; save() }
    fun reset() {
        load()
        topics.clear(); channels.clear(); interactions = 0; profile = floatArrayOf(.5f, .5f, .5f, 0f); lastPersona = ""; stability = 0
        for (i in buckets.indices) buckets[i] = floatArrayOf(.5f, .5f, .5f, 0f, 0f)
        save()
    }
}
