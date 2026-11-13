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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
