package com.umutk.pulse.extras.launcher

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.abs

/*
 * Chess, Checkers and Power of 4 of the Games folder: against the computer (four levels), two players on one phone, or two phones over Bluetooth (no internet, no account). The
 * rules and the computer player are in TinyEngines.kt; this file is the board, the menu and the Bluetooth link.
 */

internal fun tinyStart(kind: String): GPos = when (kind) { "chess" -> ChessPos.start(); "checkers" -> CheckersPos.start(); else -> Power4Pos.start() }

/** The picture of a game in the Games folder: its own logo, drawn (not a letter). */
@Composable
internal fun TinyLogo(kind: String, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .2f)), contentAlignment = Alignment.Center) {
        when (kind) {
            "chess" -> {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A2A1A), Color(0xFF1B130C)))))
                Text("♞", color = Color(0xFFF0D9B5), fontSize = (size.value * .72f).sp)
            }
            "checkers" -> Canvas(Modifier.fillMaxSize()) {
                val c = this.size.width / 4
                for (r in 0..3) for (q in 0..3) drawRect(if ((r + q) % 2 == 0) Color(0xFFF0D9B5) else Color(0xFFB58863), Offset(q * c, r * c), Size(c, c))
                drawCircle(Color(0xFFC62828), c * .8f, Offset(c * 1.5f, c * 2.5f))
                drawCircle(Color(0xFF212121), c * .8f, Offset(c * 2.5f, c * 1.5f))
                drawCircle(Color(0xFFFFD54F), c * .3f, Offset(c * 1.5f, c * 2.5f))
            }
            else -> Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFF1E4FA8))
                val c = this.size.width / 4
                val cols = listOf(listOf(0, 0, 0, 0), listOf(0, 0, 1, 2), listOf(0, 1, 2, 1), listOf(1, 2, 1, 2))
                for (r in 0..3) for (q in 0..3) drawCircle(when (cols[r][q]) { 1 -> Color(0xFFE53935); 2 -> Color(0xFFFFD600); else -> Color(0xFF0E2D63) }, c * .36f, Offset(q * c + c / 2, r * c + c / 2))
            }
        }
    }
}

/** The Bluetooth link of two phones: one hosts (waits), the other joins a paired phone. Lines of text go both ways ("G:" the game, "M:" a move, "N" a new game). */
@SuppressLint("MissingPermission")
internal class BtLink(private val onLine: (String) -> Unit, private val onState: (String) -> Unit) {
    companion object { val ID: UUID = UUID.fromString("7c1b6a50-3f5e-4b1e-9a55-5075616c7365") }
    private var sock: BluetoothSocket? = null
    private var server: BluetoothServerSocket? = null
    private val sender = Executors.newSingleThreadExecutor()
    @Volatile var connected = false

    fun host(ad: BluetoothAdapter) {
        Thread {
            try { server = ad.listenUsingRfcommWithServiceRecord("Pulse games", ID); onState("waiting"); val s = server!!.accept(); runCatching { server?.close() }; attach(s) }
            catch (_: Throwable) { onState("closed") }
        }.start()
    }

    fun join(dev: BluetoothDevice) {
        Thread {
            try { onState("connecting"); val s = dev.createRfcommSocketToServiceRecord(ID); s.connect(); attach(s) }
            catch (_: Throwable) { onState("failed") }
        }.start()
    }

    private fun attach(s: BluetoothSocket) {
        sock = s; connected = true; onState("connected")
        runCatching { s.inputStream.bufferedReader().forEachLine { onLine(it) } }
        connected = false; onState("closed")
    }

    fun send(line: String) { sender.execute { runCatching { sock?.outputStream?.let { it.write((line + "\n").toByteArray()); it.flush() } } } }

    fun close() { connected = false; runCatching { server?.close() }; runCatching { sock?.close() }; sender.shutdown() }
}

private fun hasBt(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** The three games, one composable: [kind] is "chess", "checkers" or "connect4". */
@Composable
fun TinyGameHost(kind: String) {
    var mode by remember { mutableStateOf("") }          // "", "bot", "two", "host", "join"
    var level by remember { mutableIntStateOf(2) }
    var humanWhite by remember { mutableStateOf(true) }
    if (mode.isEmpty()) TinyMenu(kind, level, { level = it }, humanWhite, { humanWhite = it }) { mode = it }
    else TinyPlay(kind, mode, level, humanWhite) { mode = "" }
}

@Composable
private fun TinyMenu(kind: String, level: Int, setLevel: (Int) -> Unit, white: Boolean, setWhite: (Boolean) -> Unit, start: (String) -> Unit) {
    val name = when (kind) { "chess" -> tr("Chess", "Satranç"); "checkers" -> tr("Checkers", "Dama"); else -> tr("Power of 4", "Dörtlü") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TinyLogo(kind, 88.dp)
        Text(name, color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("Computer", "Bilgisayar"), color = T.sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..4).forEach { n -> PChip(level == n, "$n") { setLevel(n) } } }
        if (kind != "connect4") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(white, tr("White", "Beyaz")) { setWhite(true) }
            PChip(!white, tr("Black", "Siyah")) { setWhite(false) }
        }
        PButton(tr("Play the computer", "Bilgisayara karşı oyna"), Modifier.fillMaxWidth()) { start("bot") }
        PButton(tr("Two players, one phone", "İki kişi, tek telefon"), Modifier.fillMaxWidth()) { start("two") }
        Text(tr("Two phones, Bluetooth", "İki telefon, Bluetooth"), color = T.sub, fontSize = 13.sp)
        PButton(tr("Host a game", "Oyun kur"), Modifier.fillMaxWidth()) { start("host") }
        PButton(tr("Join a game", "Oyuna katıl"), Modifier.fillMaxWidth()) { start("join") }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun TinyPlay(kind: String, mode: String, level: Int, humanWhite0: Boolean, back: () -> Unit) {
    val ctx = LocalContext.current
    val chess = kind == "chess"
    val btMode = mode == "host" || mode == "join"
    // over Bluetooth the host has white, the one who joins black
    val humanWhite = if (btMode) mode == "host" else humanWhite0
    var pos by remember { mutableStateOf(tinyStart(kind)) }
    val history = remember { ArrayList<GPos>() }
    var last by remember { mutableStateOf<Mv?>(null) }
    var sel by remember { mutableIntStateOf(-1) }
    var btState by remember { mutableStateOf(if (btMode) "ask" else "") }
    var incoming by remember { mutableStateOf<String?>(null) }
    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var link by remember { mutableStateOf<BtLink?>(null) }
    var note by remember { mutableStateOf("") }

    fun doMove(m: Mv, send: Boolean) {
        history.add(pos); pos = pos.play(m); last = m; sel = -1
        if (send) link?.send("M:" + m.encode())
    }
    val humanTurn = mode == "two" || (if (btMode) btState == "connected" else true) && pos.whiteTurn == humanWhite
    val out = pos.outcome()

    // the computer's move
    LaunchedEffect(pos, mode) {
        if (mode == "bot" && out < 0 && pos.whiteTurn != humanWhite) {
            delay(350)
            val snap = pos
            val m = withContext(Dispatchers.Default) { TinyBot.best(snap, level, chess) }
            if (m != null && pos === snap) doMove(m, false)
        }
    }
    // a move from the other phone
    LaunchedEffect(incoming) {
        val line = incoming ?: return@LaunchedEffect
        incoming = null
        when {
            line.startsWith("M:") -> Mv.decode(line.removePrefix("M:"))?.let { d -> pos.moves().firstOrNull { it.same(d) }?.let { doMove(it, false) } }
            line.startsWith("G:") -> if (line.removePrefix("G:") != kind) note = tr("The other phone plays another game", "Öbür telefon başka bir oyun oynuyor")
            line == "N" -> { history.clear(); pos = tinyStart(kind); last = null; sel = -1 }
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) btState = "ready" else note = tr("Bluetooth permission is needed", "Bluetooth izni gerekli") }
    LaunchedEffect(btMode) { if (btMode) { if (hasBt(ctx)) btState = "ready" else ask.launch(android.Manifest.permission.BLUETOOTH_CONNECT) } }
    DisposableEffect(Unit) { onDispose { link?.close() } }

    fun startLink() {
        val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (ad == null || !ad.isEnabled) { note = tr("Switch Bluetooth on", "Bluetooth'u aç"); return }
        val l = BtLink({ incoming = it }, { st -> btState = st; if (st == "connected") link?.send("G:$kind") })
        link = l
        if (mode == "host") l.host(ad) else devices = ad.bondedDevices.toList()
    }
    LaunchedEffect(btState) { if (btState == "ready") startLink() }

    val targets = remember(pos, sel) { if (sel < 0) emptySet() else pos.moves().filter { it.from == sel }.map { it.to }.toSet() }
    fun tap(sq: Int) {
        if (out >= 0 || !humanTurn) return
        if (kind == "connect4") { pos.moves().firstOrNull { it.to == sq % Power4Pos.W }?.let { doMove(it, true) }; return }
        if (sel >= 0) {
            val m = pos.moves().firstOrNull { it.from == sel && it.to == sq }
            if (m != null) { doMove(m, true); return }
        }
        val own = pos.moves().any { it.from == sq }
        sel = if (own) sq else -1
    }

    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = T.text, fontSize = 22.sp, modifier = Modifier.clickable { back() }.padding(end = 12.dp))
            val status = when {
                btMode && btState != "connected" && btState != "closed" -> when (btState) { "waiting" -> tr("Waiting for the other phone…", "Öbür telefon bekleniyor…"); "connecting" -> tr("Connecting…", "Bağlanıyor…"); "failed" -> tr("Could not connect", "Bağlanamadı"); else -> "" }
                btMode && btState == "closed" -> tr("The link was lost", "Bağlantı koptu")
                out == 0 -> tr("White wins", "Beyaz kazandı")
                out == 1 -> tr("Black wins", "Siyah kazandı")
                out == 2 -> tr("Draw", "Berabere")
                (pos as? ChessPos)?.inCheck(pos.whiteTurn) == true -> tr("Check", "Şah") + " · " + (if (pos.whiteTurn) tr("White", "Beyaz") else tr("Black", "Siyah"))
                else -> if (pos.whiteTurn) tr("White to move", "Beyaz oynuyor") else tr("Black to move", "Siyah oynuyor")
            }
            Text(status.ifEmpty { note }, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (history.isNotEmpty() && mode != "host" && mode != "join") Text("↶", color = T.text, fontSize = 22.sp, modifier = Modifier.clickable {
                // against the computer an undo takes back the computer's move and the one before
                val n = if (mode == "bot" && history.size >= 2 && pos.whiteTurn == humanWhite) 2 else 1
                repeat(n) { if (history.isNotEmpty()) pos = history.removeAt(history.lastIndex) }
                last = null; sel = -1
            }.padding(8.dp))
            Text("⟳", color = T.text, fontSize = 22.sp, modifier = Modifier.clickable { history.clear(); pos = tinyStart(kind); last = null; sel = -1; link?.send("N") }.padding(8.dp))
        }
        if (mode == "join" && (btState == "ready" || btState == "failed") && devices.isNotEmpty()) {
            Text(tr("Pick the phone that hosts (pair the phones in Bluetooth first)", "Kuran telefonu seç (önce telefonları Bluetooth'ta eşleştir)"), color = T.sub, fontSize = 13.sp)
            devices.forEach { d -> PButton(d.name ?: d.address, Modifier.fillMaxWidth()) { link?.join(d) } }
        }
        if (btMode && mode == "host" && btState == "waiting") PButton(tr("Make this phone visible", "Bu telefonu görünür yap"), Modifier.fillMaxWidth()) {
            runCatching { ctx.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)) }
        }
        if (!btMode || btState == "connected") {
            when (kind) {
                "connect4" -> Power4Board(pos as Power4Pos, last) { tap(it) }
                else -> SquareBoard(kind, pos, !humanWhite && mode != "two", sel, targets, last) { tap(it) }
            }
        }
    }
}

private val LIGHT = Color(0xFFF0D9B5)
private val DARK = Color(0xFFB58863)

@Composable
private fun SquareBoard(kind: String, pos: GPos, flip: Boolean, sel: Int, targets: Set<Int>, last: Mv?, onTap: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = maxWidth / 8
        Column(Modifier.fillMaxSize()) {
            for (rr in 0..7) Row(Modifier.weight(1f).fillMaxWidth()) {
                for (cc in 0..7) {
                    val r = if (flip) 7 - rr else rr; val c = if (flip) 7 - cc else cc
                    val sq = r * 8 + c
                    val darkSq = (r + c) % 2 == 1
                    val hot = sq == sel
                    val lastHit = last != null && (sq == last.from || sq == last.to)
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (hot) Color(0xFFF6F669) else if (lastHit) (if (darkSq) Color(0xFFCDBB52) else Color(0xFFE4DC6E)) else if (darkSq) DARK else LIGHT).clickable { onTap(sq) }, contentAlignment = Alignment.Center) {
                        val p = if (pos is ChessPos) pos.b[sq] else (pos as CheckersPos).b[sq]
                        if (p != 0) {
                            if (kind == "chess") Text("♟♞♝♜♛♚"[abs(p) - 1].toString(), fontSize = (cell.value * .78f).sp, color = if (p > 0) Color.White else Color(0xFF151515),
                                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(if (p > 0) Color(0xFF000000) else Color(0x66FFFFFF), Offset(0f, 0f), 6f)), textAlign = TextAlign.Center)
                            else Box(Modifier.fillMaxSize(.78f).clip(CircleShape).background(if (p > 0) Color(0xFFC62828) else Color(0xFF212121)).border(2.dp, if (p > 0) Color(0xFF8E1B1B) else Color(0xFF555555), CircleShape), contentAlignment = Alignment.Center) {
                                if (abs(p) == 2) Text("♛", color = Color(0xFFFFD54F), fontSize = (cell.value * .45f).sp)
                            }
                        }
                        if (sq in targets) Box(Modifier.fillMaxSize(if (p != 0) .9f else .3f).clip(CircleShape).background(Color(0x55208040)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Power4Board(pos: Power4Pos, last: Mv?, onTap: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().aspectRatio(Power4Pos.W / Power4Pos.H.toFloat()).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E4FA8)).padding(6.dp)) {
        for (r in 0 until Power4Pos.H) Row(Modifier.weight(1f).fillMaxWidth()) {
            for (c in 0 until Power4Pos.W) {
                val p = pos.b[r * Power4Pos.W + c]
                val newest = last != null && c == last.to && r == pos.rowOf(c).let { if (it < 0) 0 else it + 1 }
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTap(c) }.padding(3.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(when { p > 0 -> Color(0xFFE53935); p < 0 -> Color(0xFFFFD600); else -> Color(0xFF0E2D63) }).then(if (newest) Modifier.border(3.dp, Color.White, CircleShape) else Modifier))
                }
            }
        }
    }
}
