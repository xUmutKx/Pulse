package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.bevel
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

// ---- Minesweeper ----------------------------------------------------------------------------------------------

private const val MS_W = 9
private const val MS_H = 9
private const val MS_MINES = 10
private val MS_COLORS = listOf(Color.Unspecified, Color(0xFF0000FF), Color(0xFF008000), Color(0xFFFF0000), Color(0xFF000080), Color(0xFF800000), Color(0xFF008080), Color.Black, Color(0xFF808080))

private class MsBoard {
    val mine = BooleanArray(MS_W * MS_H)
    val open = BooleanArray(MS_W * MS_H)
    val flag = BooleanArray(MS_W * MS_H)
    var placed = false
    var lost = false
    fun near(i: Int): List<Int> {
        val x = i % MS_W; val y = i / MS_W
        return buildList { for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && x + dx in 0 until MS_W && y + dy in 0 until MS_H) add((y + dy) * MS_W + x + dx) }
    }
    fun count(i: Int) = near(i).count { mine[it] }
    val won get() = !lost && placed && (0 until MS_W * MS_H).all { mine[it] || open[it] }
    val flagsLeft get() = MS_MINES - flag.count { it }
    fun reveal(i: Int) {
        if (lost || open[i] || flag[i]) return
        if (!placed) {
            // the first tap is never a mine: the mines go in afterwards, away from it and its neighbours
            val free = (0 until MS_W * MS_H).filter { it != i && it !in near(i) }.shuffled()
            free.take(MS_MINES).forEach { mine[it] = true }
            placed = true
        }
        if (mine[i]) { lost = true; for (k in mine.indices) if (mine[k]) open[k] = true; return }
        val stack = ArrayDeque<Int>(); stack.add(i)
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            if (open[c] || flag[c]) continue
            open[c] = true
            if (count(c) == 0) near(c).forEach { if (!open[it]) stack.add(it) }
        }
    }
}

/** The classic 9 x 9 Minesweeper with ten mines: cells are raised in the Windows looks, flat in the others; the flag button switches what a tap does. */
@Composable
fun MinesweeperGame() {
    var board by remember { mutableStateOf(MsBoard()) }
    var tick by remember { mutableIntStateOf(0) }
    var secs by remember { mutableIntStateOf(0) }
    var flagMode by remember { mutableStateOf(false) }
    val running = board.placed && !board.lost && !board.won
    LaunchedEffect(running, board) { while (running) { delay(1000); secs++ } }
    val classic = T.id == "win95" || T.id == "xp" || T.id == "win7"
    tick.let {}
    Column(Modifier.fillMaxSize().background(if (classic) Color(0xFFC0C0C0) else T.bg).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("%03d".format(board.flagsLeft.coerceAtLeast(0)), color = Color(0xFFFF2020), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.background(Color.Black).padding(horizontal = 6.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).then(if (classic) Modifier.background(Color(0xFFC0C0C0)).bevel(true) else Modifier.clip(RoundedCornerShape(10.dp)).background(T.panel))
                .clickable { board = MsBoard(); secs = 0; tick++ }, contentAlignment = Alignment.Center) {
                Text(if (board.lost) "☹" else if (board.won) "☻" else "☺", fontSize = 24.sp, color = if (classic) Color(0xFF807000) else T.text)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(40.dp).then(if (classic) Modifier.background(Color(0xFFC0C0C0)).bevel(!flagMode) else Modifier.clip(RoundedCornerShape(10.dp)).background(if (flagMode) T.accent.copy(alpha = .3f) else T.panel))
                .clickable { flagMode = !flagMode }, contentAlignment = Alignment.Center) { Text("⚑", fontSize = 20.sp, color = Color(0xFFD02020)) }
            Spacer(Modifier.weight(1f))
            Text("%03d".format(secs.coerceAtMost(999)), color = Color(0xFFFF2020), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.background(Color.Black).padding(horizontal = 6.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val cell = minOf(maxWidth / MS_W, maxHeight / MS_H, 44.dp)
            Column(if (classic) Modifier.bevel(false).padding(3.dp) else Modifier) {
                for (y in 0 until MS_H) Row {
                    for (x in 0 until MS_W) {
                        val i = y * MS_W + x
                        val b = board
                        MsCell(b, i, cell, classic) {
                            if (b.lost || b.won) return@MsCell
                            if (flagMode || false) { if (!b.open[i] && (b.flag[i] || b.flagsLeft > 0)) b.flag[i] = !b.flag[i] } else b.reveal(i)
                            tick++
                        }
                    }
                }
            }
        }
        val msg = when { board.won -> tr("You win!", "Kazandınız!"); board.lost -> tr("Boom!", "Patladı!"); else -> "" }
        if (msg.isNotEmpty()) Text(msg, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MsCell(b: MsBoard, i: Int, size: Dp, classic: Boolean, onTap: () -> Unit) {
    val open = b.open[i]
    val base = Modifier.size(size).clickable(onClick = onTap)
    val mod = when {
        !open && classic -> base.background(Color(0xFFC0C0C0)).bevel(true)
        !open -> base.padding(1.dp).clip(RoundedCornerShape(4.dp)).background(T.panel)
        classic -> base.background(Color(0xFFC0C0C0)).border(.5.dp, Color(0xFF808080))
        else -> base.padding(1.dp).clip(RoundedCornerShape(4.dp)).background(T.bg).border(.5.dp, T.line, RoundedCornerShape(4.dp))
    }
    Box(mod, contentAlignment = Alignment.Center) {
        when {
            open && b.mine[i] -> Text("✸", fontSize = (size.value * .6f).sp, color = Color.Black)
            open -> b.count(i).takeIf { it > 0 }?.let { n -> Text("$n", fontSize = (size.value * .55f).sp, fontWeight = FontWeight.Bold, color = if (classic) MS_COLORS[n] else MS_COLORS[n].let { c -> if (T.dark && n == 7) Color.White else c }) }
            b.flag[i] -> Text("⚑", fontSize = (size.value * .5f).sp, color = Color(0xFFD02020))
        }
    }
}

// ---- Klondike Solitaire ----------------------------------------------------------------------------------------

private class Card(val rank: Int, val suit: Int, var up: Boolean = false) {
    val red get() = suit == 1 || suit == 2
    val label get() = listOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")[rank] + listOf("♠", "♥", "♦", "♣")[suit]
}

private class Deal {
    val stock = ArrayDeque<Card>()
    val waste = ArrayDeque<Card>()
    val found = List(4) { ArrayDeque<Card>() }
    val tab = List(7) { ArrayDeque<Card>() }
    init {
        val deck = (0 until 52).map { Card(it % 13, it / 13) }.shuffled()
        var k = 0
        for (c in 0 until 7) for (r in 0..c) tab[c].addLast(deck[k++].also { it.up = r == c })
        while (k < 52) stock.addLast(deck[k++])
    }
    val won get() = found.all { it.size == 13 }
    fun draw() {
        if (stock.isNotEmpty()) waste.addLast(stock.removeLast().also { it.up = true })
        else while (waste.isNotEmpty()) stock.addLast(waste.removeLast().also { it.up = false })
    }
    fun canFound(c: Card): Boolean = found[c.suit].size == c.rank
    fun canTab(c: Card, col: Int): Boolean { val t = tab[col].lastOrNull(); return if (t == null) c.rank == 12 else t.up && t.rank == c.rank + 1 && t.red != c.red }
    fun flip(col: Int) { tab[col].lastOrNull()?.up = true }
}

/** Where a picked card is: the waste (-1), or a column and the index of the first picked card in it. */
private class Pick(val col: Int, val idx: Int)

/** Klondike Solitaire, draw one: tap a card, then where it goes; a tap on a picked card sends it to the foundation when it fits. */
@Composable
fun SolitaireGame() {
    var deal by remember { mutableStateOf(Deal()) }
    var tick by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<Pick?>(null) }
    tick.let {}
    val classic = T.id == "win95" || T.id == "xp" || T.id == "win7"
    val table = if (classic) Color(0xFF008000) else T.bg
    fun pickedCards(p: Pick): List<Card> = if (p.col < 0) listOfNotNull(deal.waste.lastOrNull()) else deal.tab[p.col].toList().drop(p.idx)
    fun remove(p: Pick) { if (p.col < 0) deal.waste.removeLast() else { val t = deal.tab[p.col]; repeat(t.size - p.idx) { t.removeLast() }; deal.flip(p.col) } }
    fun toFound(p: Pick): Boolean {
        val cs = pickedCards(p); if (cs.size != 1 || !deal.canFound(cs[0])) return false
        remove(p); deal.found[cs[0].suit].addLast(cs[0]); return true
    }
    fun toCol(p: Pick, col: Int): Boolean {
        if (p.col == col) return false
        val cs = pickedCards(p); if (cs.isEmpty() || !deal.canTab(cs[0], col)) return false
        remove(p); cs.forEach { deal.tab[col].addLast(it) }; return true
    }
    Column(Modifier.fillMaxSize().background(table).padding(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = (maxWidth - 6.dp * 6) / 7
            val h = w * 1.4f
            Column {
                // stock, waste and the four foundations
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardSlot(deal.stock.lastOrNull(), w, h, back = true, hint = if (deal.stock.isEmpty()) "↻" else "", selected = false) { deal.draw(); pick = null; tick++ }
                    CardSlot(deal.waste.lastOrNull(), w, h, selected = pick?.col == -1) {
                        if (deal.waste.isEmpty()) return@CardSlot
                        val p = Pick(-1, 0)
                        if (pick?.col == -1) { toFound(p); pick = null } else pick = p
                        tick++
                    }
                    Spacer(Modifier.width(w))
                    for (f in 0 until 4) CardSlot(deal.found[f].lastOrNull(), w, h, hint = listOf("♠", "♥", "♦", "♣")[f], selected = false) {
                        pick?.let { p -> toFound(p) }; pick = null; tick++
                    }
                }
                Spacer(Modifier.height(10.dp))
                // the seven columns
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0 until 7) {
                        val col = deal.tab[c]
                        Box(Modifier.width(w).height(h + (col.size.coerceAtLeast(1) * 22).dp + 90.dp).clickable {
                            val p = pick
                            if (p != null && col.isEmpty()) { toCol(p, c); pick = null; tick++ }
                        }) {
                            if (col.isEmpty()) CardSlot(null, w, h, selected = false) {}
                            var y = 0
                            col.forEachIndexed { i, card ->
                                val yy = y; y += if (card.up) 22 else 10
                                Box(Modifier.offset(y = yy.dp)) {
                                    CardSlot(card, w, h, back = !card.up, selected = pick?.col == c && (pick?.idx ?: 99) <= i) {
                                        val p = pick
                                        when {
                                            !card.up -> {}
                                            p == null -> pick = Pick(c, i)
                                            p.col == c && p.idx == i -> { toFound(p); pick = null }
                                            else -> { if (!toCol(p, c)) pick = Pick(c, i) else pick = null }
                                        }
                                        tick++
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (deal.won) tr("You win!", "Kazandınız!") else "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(tr("New game", "Yeni oyun"), color = if (classic) Color.White else T.accent, fontSize = 14.sp, modifier = Modifier.clickable { deal = Deal(); pick = null; tick++ }.padding(8.dp))
        }
    }
}

@Composable
private fun CardSlot(card: Card?, w: Dp, h: Dp, back: Boolean = false, hint: String = "", selected: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    val m = Modifier.size(w, h).clip(shape).clickable(onClick = onTap)
    when {
        card == null -> Box(m.border(1.dp, Color.White.copy(alpha = .35f), shape), contentAlignment = Alignment.Center) { Text(hint, color = Color.White.copy(alpha = .5f), fontSize = (w.value * .45f).sp) }
        back -> Box(m.background(Color(0xFF1E4DB7)).border(2.dp, Color.White, shape))
        else -> Box(m.background(Color.White).border(if (selected) 3.dp else 1.dp, if (selected) Color(0xFFFFC000) else Color(0xFF808080), shape).padding(3.dp)) {
            Text(card.label, color = if (card.red) Color(0xFFD02020) else Color.Black, fontSize = (w.value * .3f).coerceAtLeast(9f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
