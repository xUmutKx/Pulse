package com.umutk.pulse.extras.music

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.cassette.CASSETTE_THEMES
import com.umutk.pulse.extras.music.cassette.IPOD_THEMES
import com.umutk.pulse.extras.music.cassette.TURNTABLE_THEMES
import com.umutk.pulse.extras.ui.ThemeItem
import com.umutk.pulse.tr

/**
 * The extra themes of the music player: skeuomorphic players with colour variants, listed together in the look picker. Each has a kind (which player is
 * drawn) and a variant (its colours); the choice is "kind:variant" and is kept in the music page's preferences.
 */
object ExtraThemes {
    enum class Kind { CASSETTE, IPOD, TURNTABLE }

    private fun sp(c: Context) = c.getSharedPreferences("music_hub", Context.MODE_PRIVATE)

    /** "cassette:yellow", "ipod:white", "turntable:walnut". */
    var key by mutableStateOf("cassette:yellow")
        private set

    fun load(c: Context) { key = sp(c).getString("extra_theme", key) ?: key }
    fun set(c: Context, k: String) { key = k; sp(c).edit().putString("extra_theme", k).apply() }

    val kind get() = when (key.substringBefore(':')) { "ipod" -> Kind.IPOD; "turntable" -> Kind.TURNTABLE; else -> Kind.CASSETTE }
    val variant get() = key.substringAfter(':', "")

    /** Every theme of the list: its key, its name and the colours of its little picture. */
    class Entry(val key: String, val name: String, val a: Color, val b: Color, val c: Color)
    val all: List<Entry> by lazy {
        CASSETTE_THEMES.map { Entry("cassette:" + it.id, tr(it.en, it.tr), it.main, it.paper, it.ink) } +
            IPOD_THEMES.map { Entry("ipod:" + it.id, tr(it.en, it.tr), it.body, it.wheel, it.bg) } +
            TURNTABLE_THEMES.map { Entry("turntable:" + it.id, tr(it.en, it.tr), it.base, it.label, it.baseDark) }
    }

    fun items(): List<ThemeItem> = all.map { e -> ThemeItem(e.key, e.name) { Preview(e) } }

    @Composable
    private fun Preview(e: Entry) {
        val kind = e.key.substringBefore(':')
        Box(Modifier.fillMaxSize().background(if (kind == "ipod") Color(0xFF101010) else e.a), contentAlignment = Alignment.Center) {
            when (kind) {
                // a cassette: the cream label, two reels
                "cassette" -> Box(Modifier.fillMaxWidth(.78f).fillMaxHeight(.56f).clip(RoundedCornerShape(4.dp)).background(e.b), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxWidth(.5f).fillMaxHeight(.4f).clip(RoundedCornerShape(3.dp)).background(e.c.copy(alpha = .55f)))
                }
                // an iPod: the body, the screen, the wheel
                "ipod" -> Box(Modifier.fillMaxHeight(.84f).fillMaxWidth(.4f).clip(RoundedCornerShape(5.dp)).background(e.a), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth(.8f).fillMaxHeight(.3f).background(Color(0xFFB9C7B4)))
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).fillMaxWidth(.7f).fillMaxHeight(.4f).clip(CircleShape).background(e.b))
                }
                // a turntable: the record with its label
                else -> Box(Modifier.fillMaxHeight(.78f).fillMaxWidth(.5f).clip(CircleShape).background(Color(0xFF0C0C0D)), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxHeight(.38f).fillMaxWidth(.38f).clip(CircleShape).background(e.b))
                }
            }
        }
    }
}
