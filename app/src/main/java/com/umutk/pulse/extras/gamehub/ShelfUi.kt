package com.umutk.pulse.extras.gamehub

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The shelf look of Game Hub. The idea (a shelf of game boxes you can turn in your hands, open, and take the disc out of) comes from Boxshelf
 * (https://github.com/sassiestrhihno/boxshelf-source, GPL-3.0); this is Pulse's own drawing of it in Compose, with no code taken over:
 * the shelf is a pager of box art, the case is two layers that turn on their own edges, and every moving part is one graphics layer, so a
 * long library stays smooth.
 */

/** The wood of the shelf plank. */
private val WOOD = Brush.verticalGradient(listOf(Color(0xFF7A5638), Color(0xFF4A3322), Color(0xFF2E1F15)))

@Composable
internal fun ShelfView(games: List<GameRow>, hidden: Set<String>, pal: Pal, onPlay: (GameRow) -> Unit, onDetails: (GameRow) -> Unit) {
    val ctx = LocalContext.current
    var racks by remember { mutableStateOf(prefs(ctx).getBoolean("shelfRacks", false)) }
    var inspect by remember { mutableStateOf<GameRow?>(null) }
    val times by produceState<Map<String, Long>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { runCatching { playTimes(ctx, 7) }.getOrDefault(emptyMap()) }
    }
    Box(Modifier.fillMaxSize()) {
        if (racks) Racks(games, hidden, pal) { inspect = it }
        else Coverflow(games, hidden, pal, times, onOpen = { inspect = it }, onPlay = onPlay, onDetails = onDetails)
        // the two ways to see the shelf: one box at a time, or the racks of three
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 98.dp).size(40.dp).clip(CircleShape).background(pal.card)
                .clickable { racks = !racks; prefs(ctx).edit().putBoolean("shelfRacks", racks).apply() },
            contentAlignment = Alignment.Center,
        ) { FluentIcon(if (racks) "listView" else "gridView", pal.text, 20.dp) }
    }
    inspect?.let { g -> CaseInspect(g, pal, onClose = { inspect = null }, onPlay = { onPlay(g) }, onDetails = { inspect = null; onDetails(g) }) }
}

@Composable
private fun ShelfPill(label: String, filled: Boolean, pal: Pal, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (filled) pal.accent else pal.card).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled) pal.bg else pal.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

/** One box art standing on the shelf: a lit hinge strip down the left and a gloss over the top, as on a real case. */
@Composable
private fun BoxArt(g: GameRow, pal: Pal, hidden: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(pal.card)) {
        GameCover(g.pkg, pal, wide = false)
        Box(Modifier.fillMaxHeight().width(9.dp).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .5f), Color.White.copy(alpha = .16f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent, Color.Transparent))))
        if (hidden) Text(tr("hidden", "gizli"), color = pal.text.copy(alpha = .85f), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** The shelf as a row you swipe along: the box in the middle stands square, the ones beside it turn away and sink back. */
@Composable
private fun Coverflow(games: List<GameRow>, hidden: Set<String>, pal: Pal, times: Map<String, Long>, onOpen: (GameRow) -> Unit, onPlay: (GameRow) -> Unit, onDetails: (GameRow) -> Unit) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { games.size }
    Column(Modifier.fillMaxSize().padding(top = 56.dp, bottom = 92.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cardW = minOf(maxWidth * 0.5f, maxHeight * 0.64f)
            HorizontalPager(
                pager, Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = (maxWidth - cardW) / 2), pageSpacing = 6.dp,
                verticalAlignment = Alignment.CenterVertically, key = { games[it].pkg },
            ) { page ->
                val g = games[page]
                Box(
                    Modifier.width(cardW).aspectRatio(2f / 3f)
                        .graphicsLayer {
                            // how far this page is from the middle, in pages (negative on the right)
                            val d = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-2f, 2f)
                            val far = kotlin.math.abs(d).coerceAtMost(1.5f)
                            rotationY = d * 38f
                            scaleX = 1f - 0.18f * far; scaleY = scaleX
                            alpha = 1f - 0.22f * far
                            translationX = d * cardW.toPx() * 0.2f
                            cameraDistance = 18f * density
                        }
                        .clickable { if (page == pager.currentPage) onOpen(g) else scope.launch { pager.animateScrollToPage(page) } },
                ) { BoxArt(g, pal, g.pkg in hidden, Modifier.fillMaxSize()) }
            }
        }
        // the plank, with a soft shadow under the boxes
        Box(Modifier.fillMaxWidth().height(14.dp).background(WOOD))
        val cur = games.getOrNull(pager.currentPage)
        if (cur != null) Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(cur.name, color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                times[cur.pkg]?.takeIf { it > 0 }?.let { hm(it) + " " + tr("this week", "bu hafta") } ?: tr("not played this week", "bu hafta oynanmadı"),
                color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShelfPill(tr("Play", "Oyna"), true, pal) { onPlay(cur) }
                ShelfPill(tr("Open the case", "Kutuyu aç"), false, pal) { onOpen(cur) }
                ShelfPill(tr("Details", "Ayrıntılar"), false, pal) { onDetails(cur) }
            }
        }
    }
}

/** The shelf as racks of three boxes on planks; a tap opens the case. */
@Composable
private fun Racks(games: List<GameRow>, hidden: Set<String>, pal: Pal, onOpen: (GameRow) -> Unit) {
    val rows = games.chunked(3)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 90.dp)) {
        itemsIndexed(rows) { _, row ->
            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    row.forEach { g -> BoxArt(g, pal, g.pkg in hidden, Modifier.weight(1f).aspectRatio(2f / 3f).clickable { onOpen(g) }) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(WOOD))
            }
        }
    }
}

/**
 * The case in your hands: drag to turn it (the back shows after half a turn), Open swings the lid on its spine edge to show the tray, the manual
 * on the inside of the lid and the disc; Take the disc out lifts it and lets it spin. Play starts the game.
 */
@Composable
private fun CaseInspect(g: GameRow, pal: Pal, onClose: () -> Unit, onPlay: () -> Unit, onDetails: () -> Unit) {
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    var open by remember { mutableStateOf(false) }
    var out by remember { mutableStateOf(false) }
    val lid by animateFloatAsState(if (open) 148f else 0f, tween(650, easing = FastOutSlowInEasing), label = "lid")
    val lift by animateFloatAsState(if (out) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 120f), label = "lift")
    val spinT = rememberInfiniteTransition(label = "spin")
    val spin by spinT.animateFloat(0f, 360f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "spin")
    val showFront by remember { derivedStateOf { turn.value in -90f..90f } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF2B2119), Color(0xFF0A0807))))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { scope.launch { turn.animateTo(if (turn.value > 90f) 180f else if (turn.value < -90f) -180f else 0f, spring(dampingRatio = 0.8f, stiffness = 200f)) } },
                    ) { _, dx -> scope.launch { turn.snapTo((turn.value + dx * 0.45f).coerceIn(-200f, 200f)) } }
                },
        ) {
            BoxWithConstraints(Modifier.align(Alignment.Center).padding(bottom = 80.dp)) {
                val cw = minOf(maxWidth * 0.62f, maxHeight * 0.5f)
                val ch = cw * 1.45f
                Box(Modifier.width(cw).height(ch).graphicsLayer { rotationY = turn.value; cameraDistance = 20f * density }) {
                    if (showFront) {
                        // the tray: the inside of the case, with the disc in its well
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Color(0xFF14181E))) {
                            Canvas(Modifier.align(Alignment.Center).size(cw * 0.9f)) {
                                drawCircle(Color.White.copy(alpha = .06f), size.minDimension / 2f)
                                drawCircle(Color.White.copy(alpha = .12f), size.minDimension / 2f, style = Stroke(3f))
                            }
                            Disc(g.pkg, Modifier.align(Alignment.Center).size(cw * 0.86f).graphicsLayer {
                                translationY = -lift * ch.toPx() * 0.5f
                                scaleX = 1f + 0.14f * lift; scaleY = scaleX
                                rotationZ = if (lift > 0.5f) spin else 0f
                            })
                        }
                        // the lid, hinged on the left (spine) edge: the cover outside, the manual inside once it is past the quarter turn
                        Box(Modifier.fillMaxSize().graphicsLayer { transformOrigin = TransformOrigin(0f, 0.5f); rotationY = -lid; cameraDistance = 22f * density }) {
                            if (lid <= 90f) BoxArt(g, pal, false, Modifier.fillMaxSize())
                            else Manual(g, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
                        }
                    } else {
                        CaseBack(g, pal, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).statusPad().padding(10.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                FluentIcon("close", Color.White, 20.dp)
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(g.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShelfPill(if (open) tr("Close", "Kapat") else tr("Open", "Aç"), false, pal) { open = !open; if (!open) out = false }
                    ShelfPill(if (out) tr("Put the disc back", "Diski yerine koy") else tr("Take the disc out", "Diski çıkar"), false, pal) { if (!open) open = true; out = !out }
                    ShelfPill(tr("Play", "Oyna"), true, pal) { onPlay() }
                    ShelfPill(tr("Details", "Ayrıntılar"), false, pal) { onDetails() }
                }
            }
        }
    }
}

private fun Modifier.statusPad(): Modifier = this.padding(top = 24.dp)

/** A disc: a silver face with a sweep of colour, the game's icon on its label, and the centre hole. */
@Composable
private fun Disc(pkg: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg) { value = withContext(Dispatchers.IO) { GameArt.icon(ctx, pkg, 128) } }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Brush.sweepGradient(listOf(Color(0xFFE9E9EF), Color(0xFF9DB3C9), Color(0xFFEAD6EF), Color(0xFFC6E9D3), Color(0xFFE9E9EF))), r)
            drawCircle(Color.Black.copy(alpha = .25f), r * 0.98f, style = Stroke(r * 0.04f))
            drawCircle(Color(0xFF1A1A1F), r * 0.36f)
        }
        icon?.let { Image(it, null, Modifier.fillMaxSize(0.5f).clip(CircleShape), contentScale = ContentScale.Crop) }
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF0B0B0E)))
    }
}

/** The booklet on the inside of the lid. */
@Composable
private fun Manual(g: GameRow, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFEDE6D8)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(tr("MANUAL", "KILAVUZ"), color = Color(0xFF6B5D4A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(g.name, color = Color(0xFF2B2218), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        repeat(9) { i -> Box(Modifier.fillMaxWidth(if (i % 4 == 3) 0.55f else 1f).height(5.dp).background(Color(0x26000000), RoundedCornerShape(3.dp))) }
    }
}

/** The back of the case: the name and what the phone calls the game. */
@Composable
private fun CaseBack(g: GameRow, pal: Pal, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(6.dp)).background(pal.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(g.name, color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(g.pkg, color = pal.sub, fontSize = 11.sp, maxLines = 2)
        repeat(7) { i -> Box(Modifier.fillMaxWidth(if (i % 3 == 2) 0.5f else 1f).height(5.dp).background(pal.sub.copy(alpha = .25f), RoundedCornerShape(3.dp))) }
    }
}

/**
 * The setup, in the steps of Boxshelf's setup wizard (games, look, box art, access), cut down to what a phone has: it opens by itself the first time
 * Game Hub is ever used, and any time from Settings > Setup. Each choice is kept at once; Done only closes it.
 */
@Composable
internal fun ShelfSetup(pal: Pal, theme: String, onTheme: (String) -> Unit, onAddMore: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val games = remember { loadGames(ctx) }
    var step by remember { mutableStateOf(0) }
    val last = 4
    var art by remember { mutableStateOf(prefs(ctx).getBoolean("playCovers", true)) }
    var iconMode by remember { mutableStateOf(prefs(ctx).getInt("iconMode", 0)) }
    var busy by remember { mutableStateOf(false) }
    var got by remember { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(pal.card).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // where you are: one bar per step
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(last + 1) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) pal.accent else pal.sub.copy(alpha = .3f))) }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> {
                        Text(tr("Welcome to your shelf", "Rafına hoş geldin"), color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(tr("Your games stand on a shelf as boxes you can turn, open and play. A few quick choices first; you can change them any time in Settings.",
                            "Oyunların bir rafta, çevirip açıp oynayabileceğin kutular olarak durur. Önce birkaç kısa seçim; hepsini istediğin zaman Ayarlar'dan değiştirebilirsin."), color = pal.sub, fontSize = 14.sp)
                    }
                    1 -> {
                        Text(tr("Games", "Oyunlar"), color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(tr("${games.size} games were found on this phone.", "Bu telefonda ${games.size} oyun bulundu."), color = pal.sub, fontSize = 14.sp)
                        Text(games.take(6).joinToString(", ") { it.name } + if (games.size > 6) "…" else "", color = pal.text, fontSize = 13.sp)
                        ShelfPill(tr("Add more apps", "Başka uygulama ekle"), false, pal) { onAddMore() }
                    }
                    2 -> {
                        Text(tr("Look", "Görünüm"), color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        THEMES.forEach { k ->
                            val tp = palOf(k, ctx)
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.bg).clickable { onTheme(k) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(tp.accent))
                                Text(themeName(k), color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                if (k == theme) FluentIcon("check", pal.accent, 18.dp)
                            }
                        }
                    }
                    3 -> {
                        Text(tr("Box art", "Kutu resimleri"), color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        SettingRow(pal, tr("Box art from the store", "Mağazadan kutu resimleri"), art, true) { art = it; prefs(ctx).edit().putBoolean("playCovers", it).apply() }
                        if (art) {
                            ShelfPill(if (busy) tr("Fetching…", "Getiriliyor…") else tr("Fetch the box art now", "Kutu resimlerini şimdi getir"), false, pal) {
                                if (!busy) { busy = true; scope.launch {
                                    got = withContext(Dispatchers.IO) { var n = 0; for (g in games) if (!coverFile(ctx, g.pkg).isFile && !playCoverFile(ctx, g.pkg).isFile && fetchPlayCover(ctx, g.pkg)) n++; n }
                                    GameArt.version.intValue++; busy = false
                                } }
                            }
                            got?.let { Text(tr("$it covers fetched", "$it kapak getirildi"), color = pal.sub, fontSize = 12.sp) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShelfPill(tr("Cover, else app icon", "Kapak, yoksa uygulama simgesi"), iconMode == 0, pal) { iconMode = 0; prefs(ctx).edit().putInt("iconMode", 0).apply(); GameArt.version.intValue++ }
                            ShelfPill(tr("App icon only", "Yalnız uygulama simgesi"), iconMode == 1, pal) { iconMode = 1; prefs(ctx).edit().putInt("iconMode", 1).apply(); GameArt.version.intValue++ }
                        }
                    }
                    else -> {
                        Text(tr("Play time and access", "Oynama süresi ve erişim"), color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(tr("Play time needs usage access. Hiding games and per-game settings need root or Shizuku (optional).", "Oynama süresi kullanım erişimi ister. Oyun gizleme ve oyun başına ayarlar root ya da Shizuku ister (isteğe bağlı)."), color = pal.sub, fontSize = 13.sp)
                        ShelfPill(tr("Allow play time", "Oynama süresine izin ver"), false, pal) {
                            if (Privilege.mode != Privilege.Mode.None) grantUsageAccess(ctx)
                            else runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) ShelfPill(tr("Back", "Geri"), false, pal) { step-- }
                Spacer(Modifier.weight(1f))
                ShelfPill(if (step == last) tr("Done", "Bitti") else tr("Next", "İleri"), true, pal) { if (step == last) onDone() else step++ }
            }
        }
    }
}
