package com.umutk.pulse.features

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.tr

/**
 * Big text: write a message and show it across the whole screen in huge letters, in the colours you pick, still or running across like a signboard
 * (for a concert, a pick-up sign, a note to someone far away). The screen stays on while the message shows; a tap closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BigTextTool() {
    var text by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableStateOf(120f) }
    var scheme by rememberSaveable { mutableStateOf(0) }
    var run by rememberSaveable { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    val schemes = listOf(Color.White to Color.Black, Color.Black to Color.White, Color(0xFFFFD60A) to Color.Black, Color(0xFF00E676) to Color.Black, Color(0xFFFF5252) to Color.White, Color(0xFF2F6FDB) to Color.White)
    PField(text, { text = it }, tr("Your message", "Mesajın"), Modifier.fillMaxWidth())
    Text(tr("Letter size", "Harf boyutu") + " ${size.toInt()}", color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    Slider(size, { size = it }, valueRange = 40f..320f)
    Text(tr("Colours", "Renkler"), color = T.sub, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        schemes.forEachIndexed { i, (bg, fg) ->
            Box(Modifier.size(40.dp).clip(CircleShape).background(bg).border(if (scheme == i) 3.dp else 1.dp, if (scheme == i) Look.accent else Color.Gray.copy(alpha = .5f), CircleShape).clickable { scheme = i }, contentAlignment = Alignment.Center) {
                Text("A", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Run across the screen", "Ekranda kay"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        PSwitch(run, { run = it })
    }
    PButton(tr("Show", "Göster"), Modifier.fillMaxWidth()) { if (text.isNotBlank()) show = true }
    if (show) {
        val (bg, fg) = schemes[scheme]
        Dialog(onDismissRequest = { show = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            // the screen stays on while the message shows
            val view = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
            Box(Modifier.fillMaxSize().background(bg).clickable { show = false }, contentAlignment = Alignment.Center) {
                if (run) Text(text.trim(), color = fg, fontSize = size.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false, modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = (size * .9f).dp))
                else Text(text.trim(), color = fg, fontSize = (size * .6f).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
    }
}
