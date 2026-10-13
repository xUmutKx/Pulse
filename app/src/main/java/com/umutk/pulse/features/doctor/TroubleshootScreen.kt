package com.umutk.pulse.features.doctor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Device troubleshoot: search the catalog, open a problem, run its read-only check. Fixes are shown, never run. */
@Composable
fun TroubleshootScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf(setOf<TGroup>()) }
    var openId by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf(mapOf<String, String>()) }
    val q = query.trim().lowercase()
    val shown = troubles.filter { q.isEmpty() || (it.titleEn + " " + it.titleTr + " " + it.keywords).lowercase().contains(q) }
    val groups = TGroup.entries.mapNotNull { g ->
        shown.filter { troubleGroup.getValue(it.id) == g }.takeIf { it.isNotEmpty() }?.let { g to it }
    }

    fun runCheck(p: Trouble) = scope.launch {
        val r = withContext(Dispatchers.IO) { TroubleShell.run(p.check) }
        output = output + (p.id to r)
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "head") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.rotate(90f)) }
                PHead(tx("Device troubleshoot", "Cihaz sorun giderme"))
            }
        }
        item(key = "search") {
            PField(query, { query = it }, tx("What is wrong? e.g. camera, wi-fi", "Ne sorun var? örn. kamera, wi-fi"), Modifier.fillMaxWidth())
        }
        if (groups.isEmpty()) item(key = "none") {
            Text(tx("Nothing found. Try another word.", "Bulunamadı. Başka bir kelime dene."), color = T.sub, fontSize = 14.sp)
        }
        groups.forEach { (g, list) ->
            // A search opens every matching section, so the hits are visible without a tap.
            val isOpen = q.isNotEmpty() || g in opened
            item(key = "group-${g.name}") {
                PCard(Modifier.clickable { opened = if (g in opened) opened - g else opened + g }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PIcon(groupIcon(g), Look.accent, 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(tx(g.en, g.tr), Modifier.weight(1f), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("${list.size}", color = T.sub, fontSize = 12.sp)
                        Spacer(Modifier.width(8.dp))
                        PIcon("chevron", T.sub, 20.dp, Modifier.rotate(if (isOpen) 180f else 0f))
                    }
                }
            }
            if (isOpen) items(list, key = { "trouble-${it.id}" }) { p ->
                PCard(Modifier.clickable { openId = if (openId == p.id) null else p.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PIcon(groupIcon(troubleGroup.getValue(p.id)), T.sub, 18.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(tx(p.titleEn, p.titleTr), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (openId == p.id) {
                        Text(tx(p.whyEn, p.whyTr), color = T.sub, fontSize = 13.sp)
                        Text(p.check, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PButton(tx("Check", "Kontrol et")) { runCheck(p) }
                        }
                        output[p.id]?.let { Text(it, color = T.sub, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                        p.fix?.let { fix ->
                            Text(tx("Fix (not run here): ", "Düzeltme (burada çalıştırılmaz): ") + tx(p.fixEn, p.fixTr), color = T.sub, fontSize = 13.sp)
                            Text(fix, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

/** The icon of a troubleshoot group, from the same icon set as the rest of Pulse. */
private fun groupIcon(g: TGroup): String = when (g) {
    TGroup.CAMERA -> "view"
    TGroup.AUDIO -> "play"
    TGroup.PHONE -> "phone"
    TGroup.NETWORK -> "wifi"
    TGroup.LOCATION -> "details"
    TGroup.INPUT -> "copy"
    TGroup.DISPLAY -> "palette"
    TGroup.NOTIFY -> "info"
    TGroup.BATTERY -> "battery"
    TGroup.MEMORY -> "memory"
    TGroup.STORAGE -> "folder"
    TGroup.SECURITY -> "settings"
    TGroup.SYSTEM -> "processes"
}
