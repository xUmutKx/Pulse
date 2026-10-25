package com.umutk.pulse.extras.keyboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr

/**
 * Emoji mix as a page of its own, the height of the whole keyboard: the picture it makes on top, the two picks, the ways to put them together, a grid of emojis to pick from
 * (the next tap fills the pick that is selected), and the send button. Nothing here needs the bar above the keys, which is hidden meanwhile.
 */
@Composable
internal fun MixPanel(th: KbTheme, st: KbState, act: KbActions, height: Dp) {
    val ctx = LocalContext.current
    val all by androidx.compose.runtime.produceState(emptyList<EmojiItem>()) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { EmojiData.load(ctx) } }
    var slot by remember { mutableStateOf(0) }       // 0: the next tap fills the first pick, 1: the second
    var group by remember { mutableStateOf(1) }      // 0 recent, 1..9 the Unicode groups
    val ready = st.mixA.isNotEmpty() && st.mixB.isNotEmpty()
    val pic = remember(st.mixA, st.mixB, st.mixStyle) { if (ready) runCatching { EmojiMix.render(st.mixA, st.mixB, st.mixStyle, 320).asImageBitmap() }.getOrNull() else null }
    fun pick(e: String) {
        if (slot == 0) { st.mixA = e; slot = 1 } else { st.mixB = e; slot = 0 }
    }
    Column(Modifier.fillMaxWidth().height(height).background(th.menu)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { act.mixOp("close") }, contentAlignment = Alignment.Center) { Icon(kbIcon(th, "ArrowBack"), null, tint = th.text, modifier = Modifier.size(22.dp)) }
            Text(tr("Emoji mix", "Emoji birleştir"), color = th.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(tr("Clear", "Temizle"), color = th.sub, fontSize = 14.sp, modifier = Modifier.clickable { act.mixOp("clear"); slot = 0 }.padding(12.dp))
        }
        // two picks, a plus, the result: a tap on a circle selects which pick the next emoji fills; a tap on the result gives the picture to the field
        Row(Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            @Composable fun pickCircle(e: String, i: Int) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(if (slot == i) th.accent.copy(alpha = .35f) else th.special).border(if (slot == i) 2.dp else 0.dp, th.accent, CircleShape).clickable { slot = i }, contentAlignment = Alignment.Center) {
                    if (e.isEmpty()) Icon(Icons.Rounded.EmojiEmotions, null, tint = th.sub, modifier = Modifier.size(38.dp)) else Text(e, fontSize = 42.sp, color = th.text)
                }
            }
            pickCircle(st.mixA, 0)
            Text("+", color = th.sub, fontSize = 28.sp)
            pickCircle(st.mixB, 1)
            Text("=", color = th.sub, fontSize = 28.sp)
            Box(Modifier.size(96.dp).clip(CircleShape).background(if (ready) th.accent.copy(alpha = .25f) else th.special).border(if (ready) 2.dp else 0.dp, th.accent, CircleShape).clickable(enabled = ready) { act.mixOp("send") }, contentAlignment = Alignment.Center) {
                val p = pic
                if (p != null) Image(p, null, Modifier.size(84.dp)) else Icon(Icons.Rounded.AutoAwesome, null, tint = th.sub, modifier = Modifier.size(38.dp))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until EmojiMix.styles) {
                Text(EmojiMix.name(i), color = th.text, fontSize = 13.sp, fontWeight = if (st.mixStyle == i) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (st.mixStyle == i) th.accent.copy(alpha = .35f) else th.special).clickable { st.mixStyle = i }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
            emojiTabIconsPublic.forEachIndexed { i, ic ->
                Box(Modifier.width(44.dp).height(36.dp).clickable { group = i }, contentAlignment = Alignment.Center) {
                    Icon(ic, null, tint = if (i == group) th.text else th.sub, modifier = Modifier.size(22.dp))
                }
            }
        }
        val list = if (group == 0) st.recents.map { e -> all.firstOrNull { it.e == e } ?: EmojiItem(e, 0, "") } else all.filter { it.group == group - 1 }
        LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.fillMaxWidth().weight(1f)) {
            items(list) { it2 -> Box(Modifier.height(52.dp).clickable { pick(it2.e) }, contentAlignment = Alignment.Center) { Text(it2.e, fontSize = 30.sp, color = th.text, maxLines = 1) } }
        }
    }
}
