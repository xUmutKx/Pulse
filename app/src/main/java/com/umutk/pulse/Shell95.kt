package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

private val Grey95 = Color(0xFFC0C0C0)
private val Dark95 = Color(0xFF808080)
private val Navy95 = Color(0xFF000080)

/** Windows 95 drawn like the real one (Opt.authentic): teal desktop, grey bevelled window, navy caption,
 *  menu bar with underlined access letters, property-sheet tabs and a status bar of sunken panels. */
@Composable
fun Win95Shell(page: String, go: (String) -> Unit, menus: MenuHost, pages: List<Pg>, body: @Composable () -> Unit) {
    val s by Sampler.snap.collectAsState()
    val memPct = if (s.ramTotalMb == 0) 0 else 100 * s.ramUsedMb / s.ramTotalMb
    val small = androidx.compose.ui.text.TextStyle(color = Color.Black, fontSize = 12.sp, fontFamily = FontFamily.SansSerif)
    Box(Modifier.fillMaxSize().background(Color(0xFF008080)).statusBarsPadding().navigationBarsPadding().padding(4.dp)) {
        // window: 2 px raised bevel, then the grey frame
        Column(Modifier.fillMaxSize().background(Grey95).bevel(true).padding(3.dp)) {
            // caption: navy, app icon, bold white title, − □ × buttons (× set 2 px apart)
            Row(Modifier.fillMaxWidth().height(22.dp).background(Navy95).padding(start = 3.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TitleIcon95(); Spacer(Modifier.width(4.dp))
                Text("Pulse", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif, maxLines = 1, modifier = Modifier.weight(1f))
                Cap95(0); Cap95(1); Spacer(Modifier.width(2.dp)); Cap95(2)
            }
            // menu bar
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                menus.tops.forEach { m ->
                    var open by remember { mutableStateOf(false) }
                    Box {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(m.label.take(1)) }
                            append(m.label.drop(1))
                        }, style = small, modifier = Modifier.background(if (open) Navy95 else Color.Transparent).clickable { open = true }.padding(horizontal = 8.dp, vertical = 8.dp),
                            color = if (open) Color.White else Color.Black)
                        ThemedMenu(open, { open = false }) {
                            m.items.forEach { e -> ThemedMenuItem(e.label, { open = false; e.onClick() }, iconKey = e.key, icon = e.icon, checked = e.checked) }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                // property-sheet tabs: the selected one is taller and covers the sheet's top edge
                Row(Modifier.zIndex(1f).fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 2.dp), verticalAlignment = Alignment.Bottom) {
                    pages.forEach { p ->
                        val on = page == p.id
                        Box(Modifier.offset(y = if (on) 2.dp else 0.dp).padding(horizontal = if (on) 0.dp else 1.dp).height(if (on) 34.dp else 30.dp).background(Grey95).drawBehind { tab95(on) }
                            .clickable { go(p.id) }.padding(horizontal = if (on) 11.dp else 10.dp), contentAlignment = Alignment.Center) {
                            Text(pageName(p.id, p.label), style = small, maxLines = 1)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().background(Grey95).bevel(true).padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) { body() }
            }
            // status bar: sunken panels and the size grip
            Row(Modifier.fillMaxWidth().padding(top = 2.dp).height(22.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("Processes: ${s.procs}", "CPU Usage: ${s.cpu}%", "Mem Usage: $memPct%").forEach { txt ->
                    Box(Modifier.weight(1f).fillMaxHeight().drawBehind {
                        val p = 1.dp.toPx()
                        drawRect(Dark95, Offset.Zero, Size(size.width, p)); drawRect(Dark95, Offset.Zero, Size(p, size.height))
                        drawRect(Color.White, Offset(0f, size.height - p), Size(size.width, p)); drawRect(Color.White, Offset(size.width - p, 0f), Size(p, size.height))
                    }.padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                        Text(txt, style = small.copy(fontSize = 11.sp), maxLines = 1)
                    }
                }
                Canvas(Modifier.size(12.dp).align(Alignment.Bottom)) {
                    val p = 1.dp.toPx(); val w = size.width
                    for (i in 0..2) { val d = (i * 4 + 2) * p
                        drawLine(Color.White, Offset(w - d - p, w), Offset(w, w - d - p), p)
                        drawLine(Dark95, Offset(w - d, w), Offset(w, w - d), p * 1.5f) }
                }
            }
        }
    }
}

/** One property-sheet tab edge: white left and top with clipped corners, black and grey right; unselected ones sit on the sheet's white line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.tab95(on: Boolean) {
    val p = 1.dp.toPx(); val w = size.width; val h = size.height
    drawRect(Color.White, Offset(0f, 2 * p), Size(p, h - 2 * p)); drawRect(Color.White, Offset(p, p), Size(p, p)); drawRect(Color.White, Offset(2 * p, 0f), Size(w - 4 * p, p))
    drawRect(Color.Black, Offset(w - p, 2 * p), Size(p, h - 2 * p)); drawRect(Color.Black, Offset(w - 2 * p, p), Size(p, p))
    drawRect(Dark95, Offset(w - 2 * p, 2 * p), Size(p, h - 2 * p))
    if (!on) drawRect(Color.White, Offset(0f, h - p), Size(w, p))
}

/** Caption button: 16x14 grey bevel with the − (0), □ (1) or × (2) glyph drawn on the pixel grid. */
@Composable
private fun Cap95(kind: Int) {
    Box(Modifier.size(16.dp, 14.dp).background(Grey95).bevel(true), contentAlignment = Alignment.Center) {
        when (kind) {
            2 -> CapGlyph95(true)
            0 -> CapGlyph95(false)
            else -> Canvas(Modifier.size(16.dp, 14.dp)) {
                val u = size.width / 16f
                drawRect(Color.Black, Offset(4 * u, 3 * u), Size(9 * u, 8 * u), style = Stroke(u))
                drawRect(Color.Black, Offset(4 * u, 3 * u), Size(9 * u, 2 * u))
            }
        }
    }
}
