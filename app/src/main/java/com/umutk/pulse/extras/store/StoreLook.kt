package com.umutk.pulse.extras.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PTheme
import com.umutk.pulse.T
import com.umutk.pulse.bevel

/** How a store row is drawn: a card for the rounded looks, a flat row for Win10, a bordered box for 95 / XP / 7. */
enum class StoreShell { CARD, FLAT, CLASSIC }

/** Default is Material (every look not listed here uses the card shell). */
fun storeShell(t: PTheme = T): StoreShell = when (t.id) {
    "win95", "xp", "win7" -> StoreShell.CLASSIC
    "win10" -> StoreShell.FLAT
    else -> StoreShell.CARD
}

/** The page style: CLASSIC = window with title bar and status bar (95 / XP / 7), FLUENT = Microsoft Store tiles (10 / 11), PLAY = Play-Store-like (Material and the rest). */
enum class StoreStyle { CLASSIC, FLUENT, PLAY }

fun storeStyle(t: PTheme = T): StoreStyle = when {
    storeShell(t) == StoreShell.CLASSIC -> StoreStyle.CLASSIC
    t.id == "win10" || t.id == "win11" -> StoreStyle.FLUENT
    else -> StoreStyle.PLAY
}

/** One store row or card in the current look's shell. The content is the same in every look. */
@Composable
fun StoreRowShell(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = T
    when (storeShell(t)) {
        StoreShell.CARD -> PCard(modifier.fillMaxWidth()) { content() }
        StoreShell.FLAT -> Column(modifier.fillMaxWidth()) {
            Box(Modifier.padding(vertical = 10.dp)) { content() }
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
        }
        StoreShell.CLASSIC -> Box(modifier.fillMaxWidth().background(t.panel).border(1.dp, t.line).padding(10.dp)) { content() }
    }
}

/** Page header. Classic: a window title bar. Fluent: a big plain title. Play: a top app bar. [onSearch] null hides the search icon. */
@Composable
fun StoreHeader(title: String, onBack: () -> Unit, onSearch: (() -> Unit)? = null) {
    val t = T
    when (storeStyle(t)) {
        StoreStyle.CLASSIC -> {
            val bar = t.title ?: Brush.horizontalGradient(listOf(t.accent, t.accent))
            Row(
                Modifier.fillMaxWidth().background(bar).padding(horizontal = 4.dp).height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", t.titleText, 18.dp) }
                PIcon("apps", t.titleText, 16.dp)
                Text(title, color = t.titleText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 6.dp))
                if (onSearch != null) Box(Modifier.size(32.dp).clickable { onSearch() }, contentAlignment = Alignment.Center) { PIcon("search", t.titleText, 18.dp) }
            }
        }
        StoreStyle.FLUENT -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", t.text, 22.dp) }
            Text(title, color = t.text, fontSize = 28.sp, fontWeight = if (t.id == "win11") FontWeight.SemiBold else FontWeight.Light,
                maxLines = 1, modifier = Modifier.weight(1f))
            if (onSearch != null) Box(Modifier.size(40.dp).clickable { onSearch() }, contentAlignment = Alignment.Center) { PIcon("search", t.text, 22.dp) }
        }
        StoreStyle.PLAY -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", t.text, 22.dp) }
            Text(title, color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            if (onSearch != null) Box(Modifier.size(40.dp).clickable { onSearch() }, contentAlignment = Alignment.Center) { PIcon("search", t.text, 22.dp) }
        }
    }
}

/** Store / Installed switch: toggle buttons in the classic and Play looks, a pivot with an accent underline in Win10 / 11. */
@Composable
fun StoreTabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val t = T
    if (storeStyle(t) == StoreStyle.FLUENT) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            labels.forEachIndexed { i, l ->
                Column(Modifier.clickable { onSelect(i) }.padding(vertical = 6.dp)) {
                    Text(l, color = if (i == selected) t.text else t.sub, fontSize = 16.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
                    Box(Modifier.padding(top = 4.dp).height(2.dp).fillMaxWidth().background(if (i == selected) Look.accent.copy(alpha = 1f) else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { labels.forEachIndexed { i, l -> PChip(i == selected, l) { onSelect(i) } } }
    }
}

/** The icon of a store entry: a bare glyph in the classic looks, an accent tile in Win10 / 11, a round tonal badge in the Play style. */
@Composable
fun StoreArt(icon: String, size: Dp) {
    val t = T
    val acc = Look.accent.copy(alpha = 1f)
    when (storeStyle(t)) {
        StoreStyle.CLASSIC -> PIcon(icon, t.text, size)
        StoreStyle.FLUENT -> Box(
            Modifier.size(size + 18.dp).background(acc.copy(alpha = .18f), RoundedCornerShape(if (t.id == "win11") 8.dp else 0.dp)),
            contentAlignment = Alignment.Center,
        ) { PIcon(icon, t.text, size) }
        StoreStyle.PLAY -> Box(Modifier.size(size + 18.dp).background(acc.copy(alpha = .14f), if (t.material) RoundedCornerShape(16.dp) else CircleShape), contentAlignment = Alignment.Center) {
            PIcon(icon, t.text, size)
        }
    }
}

/** A featured tile: an accent hero tile in Win10 / 11, a card in the Play style, a bordered box in the classic looks. */
@Composable
fun StoreTile(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = T
    if (storeStyle(t) == StoreStyle.FLUENT) {
        val shape = RoundedCornerShape(if (t.id == "win11") 8.dp else 0.dp)
        Box(modifier.background(Look.accent.copy(alpha = .16f), shape).border(1.dp, t.line, shape).padding(12.dp)) { content() }
    } else StoreRowShell(modifier, content)
}

/** Classic looks: the sunken status bar of the window. */
@Composable
fun StoreStatusBar(text: String) {
    val t = T
    Box(Modifier.fillMaxWidth().background(t.bg).then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, t.line)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text, color = t.text, fontSize = 12.sp, maxLines = 1)
    }
}
