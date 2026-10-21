package com.umutk.pulse.features

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import com.umutk.pulse.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** One sport: [key] is stored in prefs and names the icon; [gps] activities ask for location when they start. [words] are extra search terms. */
class ActivityType(val key: String, private val en: String, private val tur: String, val gps: Boolean, private val words: String) {
    val label: String get() = tr(en, tur)
    val name: String get() = key
    internal val haystack = fold("$key $en $tur $words".replace('_', ' '))

    companion object {
        val all: List<ActivityType> = listOf(
            ActivityType("walking", "Walking", "Yürüyüş", true, "walk stroll yuruyus"),
            ActivityType("running", "Running", "Koşu", true, "run jog jogging kosu"),
            ActivityType("cycling", "Cycling", "Bisiklet", true, "bike bicycle biking mountain bike"),
            ActivityType("hiking", "Hiking", "Doğa yürüyüşü", true, "trek trekking trail yuruyus"),
            ActivityType("swimming", "Swimming", "Yüzme", false, "swim pool havuz"),
            ActivityType("gym", "Gym", "Spor salonu", false, "strength weights weightlifting fitness dumbbell agirlik"),
            ActivityType("yoga", "Yoga", "Yoga", false, "stretch meditation"),
            ActivityType("pilates", "Pilates", "Pilates", false, "core"),
            ActivityType("rowing", "Rowing", "Kürek", false, "row rower kurek"),
            ActivityType("elliptical", "Elliptical", "Eliptik", false, "cross trainer"),
            ActivityType("stairs", "Stairs", "Merdiven", false, "stair climbing stepper"),
            ActivityType("dancing", "Dancing", "Dans", false, "dance zumba"),
            ActivityType("aerobics", "Aerobics", "Aerobik", false, "cardio hiit workout"),
            ActivityType("football", "Football", "Futbol", false, "soccer"),
            ActivityType("basketball", "Basketball", "Basketbol", false, "hoop"),
            ActivityType("tennis", "Tennis", "Tenis", false, "racket"),
            ActivityType("squash", "Squash", "Squash", false, "racket"),
            ActivityType("volleyball", "Volleyball", "Voleybol", false, "beach volley"),
            ActivityType("handball", "Handball", "Hentbol", false, ""),
            ActivityType("baseball", "Baseball", "Beyzbol", false, "softball"),
            ActivityType("rugby", "Rugby", "Ragbi", false, "american football"),
            ActivityType("cricket", "Cricket", "Kriket", false, ""),
            ActivityType("hockey", "Hockey", "Hokey", false, "ice hockey buz hokeyi"),
            ActivityType("golf", "Golf", "Golf", true, ""),
            ActivityType("skiing", "Skiing", "Kayak", true, "ski alpine cross country"),
            ActivityType("snowboarding", "Snowboarding", "Snowboard", true, "snow board kar"),
            ActivityType("ice_skating", "Ice skating", "Buz pateni", false, "skate skating ice patinaj"),
            ActivityType("curling", "Ice curling", "Buz curling", false, "curling stone ice buz kurling"),
            ActivityType("sledding", "Sledding", "Kızak", false, "sled sledge sleigh kizak"),
            ActivityType("orienteering", "Orienteering", "Oryantiring", true, "orient compass map pusula"),
            ActivityType("climbing", "Climbing", "Tırmanış", false, "rock climbing boulder bouldering wall tirmanis"),
            ActivityType("boxing", "Boxing", "Boks", false, "kickboxing punch"),
            ActivityType("martial_arts", "Martial arts", "Dövüş sanatları", false, "karate judo taekwondo mma kung fu dovus"),
            ActivityType("jump_rope", "Jump rope", "İp atlama", false, "skipping rope skip ip atlama"),
            ActivityType("horse_riding", "Horse riding", "Binicilik", true, "horse equestrian at"),
            ActivityType("kayaking", "Paddling / kayaking", "Kano / kayak", true, "paddle canoe kano kurek"),
            ActivityType("sailing", "Sailing", "Yelken", true, "boat yacht sail"),
            ActivityType("surfing", "Surfing", "Sörf", true, "surf wave sorf"),
            ActivityType("kitesurfing", "Kitesurfing", "Uçurtma sörfü", true, "kite surf windsurf ucurtma"),
            ActivityType("archery", "Archery", "Okçuluk", false, "bow arrow target okculuk"),
            ActivityType("fishing", "Fishing", "Balıkçılık", false, "fish angling balik"),
            ActivityType("nordic_walking", "Nordic walking", "Nordik yürüyüş", true, "poles"),
            ActivityType("roller_skating", "Roller skating", "Paten", true, "inline skate rollerblade"),
            ActivityType("skateboarding", "Skateboarding", "Kaykay", true, "skate skateboard"),
            ActivityType("scooter", "Scooter", "Scooter", true, "kick scooter"),
            ActivityType("scuba_diving", "Scuba diving", "Tüplü dalış", false, "diving dive snorkel dalis"),
            ActivityType("paragliding", "Paragliding", "Yamaç paraşütü", true, "glider parachute parasut"),
            ActivityType("badminton", "Badminton", "Badminton", false, "shuttlecock racket"),
            ActivityType("table_tennis", "Table tennis", "Masa tenisi", false, "ping pong pingpong"),
        )
        /** The six shown before "More" is opened. */
        val common: List<ActivityType> get() = listOf("walking", "running", "cycling", "hiking", "swimming", "gym").map { k -> all.first { it.key == k } }
        /** Also reads the old enum names ("Walking", "Gym"). */
        fun fromName(name: String?): ActivityType = all.firstOrNull { it.key.equals(name, true) } ?: all[0]
        fun fold(s: String): String = s.lowercase(java.util.Locale.ROOT).replace("\u0307", "")
            .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')
        /** Every word typed must appear somewhere in the English / Turkish name, key or synonyms. */
        fun search(q: String): List<ActivityType> {
            val parts = fold(q).trim().split(' ').filter { it.isNotEmpty() }
            return if (parts.isEmpty()) all else all.filter { a -> parts.all { a.haystack.contains(it) } }
        }
    }
}

/** Search field plus the six common tiles and a More tile; typing (or More) shows the matching part of the whole list as a grid. */
@Composable
fun ActivityPicker(selected: ActivityType, enabled: Boolean, onSelect: (ActivityType) -> Unit) {
    var query by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    val full = more || query.isNotBlank()
    OutlinedTextField(
        value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(tr("Search activities", "Etkinlik ara"), fontSize = 13.sp) },
        textStyle = TextStyle(color = T.text, fontSize = 14.sp),
    )
    val pick = { a: ActivityType -> onSelect(a); query = ""; more = false }
    if (!full) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val shown = (listOf(selected) + ActivityType.common).distinctBy { it.key }.take(6)
            shown.forEach { a -> Tile(a, a == selected, enabled, Modifier.width(72.dp)) { pick(a) } }
            MoreTile(Modifier.width(72.dp)) { more = true }
        }
    } else {
        val list = ActivityType.search(query)
        if (list.isEmpty()) Text(tr("No match", "Sonuç yok"), color = T.sub, fontSize = 13.sp)
        list.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { a -> Tile(a, a == selected, enabled, Modifier.weight(1f)) { pick(a) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (more && query.isBlank()) MoreTile(Modifier.fillMaxWidth(), less = true) { more = false }
    }
}

@Composable
private fun Tile(a: ActivityType, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).border(1.dp, if (on) Look.accent else T.sub.copy(alpha = 0.3f), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivityIcon(a.key, if (on) Look.accent else T.text, 28.dp)
        Spacer(Modifier.height(4.dp))
        Text(a.label, color = if (on) Look.accent else T.sub, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun MoreTile(modifier: Modifier, less: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).border(1.dp, T.sub.copy(alpha = 0.3f), shape).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.MoreHoriz, null, Modifier.size(28.dp), tint = T.text)
        Spacer(Modifier.height(4.dp))
        Text(if (less) tr("Less", "Daha az") else tr("More", "Daha fazla"), color = T.sub, fontSize = 11.sp, maxLines = 1)
    }
}
