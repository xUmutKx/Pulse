package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** The editor of the Win+X menu (the Start button held, Windows 10 and 11): which items it has and in which order. */
@Composable
fun WinXEditor(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val all = WINX_KEYS.map { it.first }
    var order by remember { mutableStateOf(LauncherPrefs.winX.split(',').filter { it in all }.ifEmpty { all }) }
    fun store(l: List<String>) {
        if (l.isEmpty()) return
        order = l; LauncherPrefs.winX = l.joinToString(","); LauncherPrefs.save(ctx)
    }
    fun name(k: String) = WINX_KEYS.first { it.first == k }.let { tr(it.second, it.third) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(T.panel).padding(16.dp)) {
            Text(tr("Win+X menu", "Win+X menüsü"), color = T.text, fontSize = 17.sp)
            Column(Modifier.padding(top = 10.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                order.forEachIndexed { i, k ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name(k), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        Box(Modifier.size(34.dp).clickable { if (i > 0) store(order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, contentAlignment = Alignment.Center) { PIcon("up", if (i > 0) T.text else T.sub.copy(alpha = .3f), 18.dp) }
                        Box(Modifier.size(34.dp).clickable { if (i < order.lastIndex) store(order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) }, contentAlignment = Alignment.Center) { PIcon("down", if (i < order.lastIndex) T.text else T.sub.copy(alpha = .3f), 18.dp) }
                        Box(Modifier.size(34.dp).clickable { store(order - k) }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 18.dp) }
                    }
                }
                val off = all - order.toSet()
                if (off.isNotEmpty()) Text(tr("Hidden", "Gizli"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                off.forEach { k ->
                    Row(Modifier.fillMaxWidth().clickable { store(order + k) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(name(k), color = T.sub, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) { PIcon("add", T.accent, 18.dp) }
                    }
                }
            }
            Text(tr("Done", "Tamam"), color = T.accent, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp).clickable { onClose() })
        }
    }
}
