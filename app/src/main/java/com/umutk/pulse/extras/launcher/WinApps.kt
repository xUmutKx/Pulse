package com.umutk.pulse.extras.launcher

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

// Small apps in the manner of the old desktops (Notepad, Snake). They are Pulse's own and draw themselves in whatever look is on.

/**
 * A plain text editor. What is typed is kept by itself a moment after the typing stops (the same text is there next time); Save writes a .txt file to
 * Download/Pulse/Notepad, New starts an empty page.
 */
@Composable
fun NotepadApp() {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("win_notepad", Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(sp.getString("text", "") ?: "") }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(text) { delay(600); sp.edit().putString("text", text).apply() }
    Column(Modifier.fillMaxSize().background(T.bg)) {
        Row(Modifier.fillMaxWidth().background(T.panel).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun Item(label: String, onClick: () -> Unit) {
                Text(label, color = T.text, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
            }
            Item(tr("New", "Yeni")) { text = ""; status = "" }
            Item(tr("Save", "Kaydet")) {
                status = runCatching {
                    val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Pulse/Notepad").apply { mkdirs() }
                    val name = (text.lineSequence().firstOrNull { it.isNotBlank() }?.take(24)?.replace(Regex("[^A-Za-z0-9 _-]"), "")?.trim().orEmpty().ifEmpty { "note" }) + "-" + System.currentTimeMillis() / 1000 + ".txt"
                    File(dir, name).writeText(text)
                    tr("Saved: ", "Kaydedildi: ") + name
                }.getOrElse { tr("Could not save", "Kaydedilemedi") }
            }
            Item(tr("Copy all", "Tümünü kopyala")) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Notepad", text)); status = tr("Copied", "Kopyalandı")
            }
            Box(Modifier.weight(1f))
            Text("${text.length}", color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
        }
        if (status.isNotEmpty()) Text(status, color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
        BasicTextField(
            value = text, onValueChange = { text = it },
            textStyle = TextStyle(color = T.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(T.accent),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp),
        )
    }
}

private const val SN = 16

/** Snake on a 16 x 16 board: a drag turns the snake, a tap starts again once it has died; it gets quicker with every apple. */
@Composable
fun SnakeGame() {
    var snake by remember { mutableStateOf(listOf(8 to 8, 7 to 8, 6 to 8)) }
    var dir by remember { mutableStateOf(1 to 0) }
    var want by remember { mutableStateOf(1 to 0) }
    var food by remember { mutableStateOf(12 to 8) }
    var dead by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    LaunchedEffect(round) {
        while (!dead) {
            delay((170L - score * 5L).coerceAtLeast(70L))
            // a turn straight back on itself is ignored
            if (want.first != -dir.first || want.second != -dir.second) dir = want
            val h = snake.first()
            val n = (h.first + dir.first) to (h.second + dir.second)
            val eat = n == food
            val body = if (eat) snake else snake.dropLast(1)
            if (n.first !in 0 until SN || n.second !in 0 until SN || n in body) { dead = true; break }
            snake = listOf(n) + body
            if (eat) {
                score++
                val free = (0 until SN * SN).map { it % SN to it / SN }.filter { it !in snake }
                food = free.randomOrNull() ?: food
            }
        }
    }
    fun restart() {
        snake = listOf(8 to 8, 7 to 8, 6 to 8); dir = 1 to 0; want = 1 to 0; food = 12 to 8; score = 0; dead = false; round++
    }
    Column(Modifier.fillMaxSize().background(T.bg).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (dead) tr("Game over: $score", "Oyun bitti: $score") else tr("Score: $score", "Puan: $score"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF101410))
                .pointerInput(Unit) { detectTapGestures { if (dead) restart() } }
                .pointerInput(Unit) {
                    detectDragGestures { c, d ->
                        c.consume()
                        want = if (abs(d.x) > abs(d.y)) (if (d.x > 0) 1 to 0 else -1 to 0) else (if (d.y > 0) 0 to 1 else 0 to -1)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / SN
                snake.forEachIndexed { i, p -> drawRect(if (i == 0) Color(0xFF8BC34A) else Color(0xFF4CAF50), Offset(p.first * cell, p.second * cell), Size(cell - 1.5f, cell - 1.5f)) }
                drawRect(Color(0xFFE53935), Offset(food.first * cell, food.second * cell), Size(cell - 1.5f, cell - 1.5f))
            }
        }
        Text(tr("Drag to turn. Tap to play again after a crash.", "Çevirmek için kaydır. Çarpınca tekrar oynamak için dokun."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---- 2048 ---------------------------------------------------------------------------------------------------------

private fun slide2048(line: List<Int>): Pair<List<Int>, Int> {
    val nz = line.filter { it != 0 }
    val out = ArrayList<Int>(); var gain = 0; var i = 0
    while (i < nz.size) {
        if (i + 1 < nz.size && nz[i] == nz[i + 1]) { out += nz[i] * 2; gain += nz[i] * 2; i += 2 } else { out += nz[i]; i++ }
    }
    while (out.size < 4) out += 0
    return out to gain
}

/** One move of the 4 x 4 board: [dir] 0 left, 1 right, 2 up, 3 down. Returns the new board and the points gained. */
private fun move2048(b: List<Int>, dir: Int): Pair<List<Int>, Int> {
    val res = MutableList(16) { 0 }; var gain = 0
    for (k in 0 until 4) {
        val idx = (0 until 4).map { j -> when (dir) { 0 -> k * 4 + j; 1 -> k * 4 + (3 - j); 2 -> j * 4 + k; else -> (3 - j) * 4 + k } }
        val (line, g) = slide2048(idx.map { b[it] }); gain += g
        idx.forEachIndexed { j, pos -> res[pos] = line[j] }
    }
    return res to gain
}

private fun addTile2048(b: List<Int>): List<Int> {
    val free = b.indices.filter { b[it] == 0 }
    if (free.isEmpty()) return b
    val m = b.toMutableList(); m[free.random()] = if (Math.random() < .9) 2 else 4
    return m
}

private fun tileColor(v: Int): Color = when (v) {
    0 -> Color(0xFFCDC1B4); 2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8); 8 -> Color(0xFFF2B179); 16 -> Color(0xFFF59563); 32 -> Color(0xFFF67C5F)
    64 -> Color(0xFFF65E3B); 128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61); 512 -> Color(0xFFEDC850); 1024 -> Color(0xFFEDC53F); else -> Color(0xFFEDC22E)
}

/** 2048: drag to slide every tile; equal tiles that meet join. */
@Composable
fun Game2048() {
    var board by remember { mutableStateOf(addTile2048(addTile2048(List(16) { 0 }))) }
    var score by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    fun go(dir: Int) {
        val (nb, g) = move2048(board, dir)
        if (nb == board) return
        board = addTile2048(nb); score += g
        if ((0..3).none { move2048(board, it).first != board }) over = true
    }
    Column(Modifier.fillMaxSize().background(T.bg).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (over) tr("Game over: $score", "Oyun bitti: $score") else tr("Score: $score", "Puan: $score"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(tr("New", "Yeni"), color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { board = addTile2048(addTile2048(List(16) { 0 })); score = 0; over = false }.padding(8.dp))
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFFBBADA0), RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    var acc = Offset.Zero
                    detectDragGestures(
                        onDragStart = { acc = Offset.Zero },
                        onDragEnd = {
                            if (!over && maxOf(abs(acc.x), abs(acc.y)) > 24f) go(if (abs(acc.x) > abs(acc.y)) (if (acc.x > 0) 1 else 0) else (if (acc.y > 0) 3 else 2))
                        },
                        onDrag = { c, d -> c.consume(); acc += d },
                    )
                },
        ) {
            Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (r in 0 until 4) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (c in 0 until 4) {
                        val v = board[r * 4 + c]
                        Box(Modifier.weight(1f).fillMaxHeight().background(tileColor(v), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                            if (v > 0) Text("$v", color = if (v <= 4) Color(0xFF776E65) else Color.White, fontWeight = FontWeight.Bold, fontSize = if (v < 100) 24.sp else if (v < 1000) 20.sp else 15.sp)
                        }
                    }
                }
            }
        }
        Text(tr("Drag to slide the tiles.", "Karoları kaydırmak için sürükle."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---- Tic-tac-toe --------------------------------------------------------------------------------------------------

private val TTT_LINES = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))

private fun tttWinner(b: List<Char>): Char? = TTT_LINES.firstOrNull { l -> b[l[0]] != ' ' && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]] }?.let { b[it[0]] }

/** The computer plays O: win if it can, block if it must, else the centre, a corner, anything. */
private fun tttCpu(b: List<Char>): Int {
    fun finishing(who: Char): Int? = (0..8).firstOrNull { cell -> b[cell] == ' ' && tttWinner(b.toMutableList().also { m -> m[cell] = who }) == who }
    return finishing('O') ?: finishing('X') ?: (if (b[4] == ' ') 4 else null) ?: listOf(0, 2, 6, 8).filter { b[it] == ' ' }.randomOrNull() ?: (0..8).filter { b[it] == ' ' }.random()
}

/** Noughts and crosses against the computer: you are X. */
@Composable
fun TicTacToeGame() {
    var board by remember { mutableStateOf(List(9) { ' ' }) }
    val win = tttWinner(board)
    val full = board.none { it == ' ' }
    Column(Modifier.fillMaxSize().background(T.bg).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when { win == 'X' -> tr("You win!", "Kazandın!"); win == 'O' -> tr("The computer wins", "Bilgisayar kazandı"); full -> tr("A draw", "Berabere"); else -> tr("Your turn (X)", "Sıra sende (X)") },
            color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0 until 3) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(T.panel, RoundedCornerShape(6.dp)).clickable {
                            if (board[i] == ' ' && win == null && !full) {
                                val nb = board.toMutableList().also { it[i] = 'X' }
                                if (tttWinner(nb) == null && nb.any { it == ' ' }) nb[tttCpu(nb)] = 'O'
                                board = nb
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (board[i] != ' ') Text(board[i].toString(), color = if (board[i] == 'X') T.accent else T.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(tr("New game", "Yeni oyun"), color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable { board = List(9) { ' ' } }.padding(12.dp))
    }
}
