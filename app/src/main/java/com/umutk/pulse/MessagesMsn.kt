package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.messages.MsgCore

/**
 * Messages in the old Windows looks (95, XP, 7) is MSN Messenger: the menu bar, the banner with the status, the contact list in groups (Online, Not Online) with
 * the little green and red people, the "I want to..." panel at the bottom, and a conversation written as lines ("Dan says:"), not as bubbles.
 * An SMS conversation is a contact: one with a message today is "Online", the others "Not Online".
 */
internal fun msnLook(): Boolean = T.id == "win95" || T.id == "xp" || T.id == "win7"

private val MSN_BLUE = Color(0xFF1D2B8F)
private val MSN_SUB = Color(0xFF7B7F8C)

private fun msnBg(): Color = if (T.id == "win95") Color(0xFFC0C0C0) else Color(0xFFEEF1F8)
internal fun msnPage(): Color = msnBg()

/** The little MSN person: green when online, red-brown when not. */
@Composable
internal fun MsnBuddy(online: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val dark = if (online) Color(0xFF1F7A2E) else Color(0xFF8E2F2A)
        val mid = if (online) Color(0xFF45B552) else Color(0xFFC95A54)
        val light = if (online) Color(0xFFA6E6AE) else Color(0xFFF0A9A4)
        // the body: a half round shape
        drawArc(Brush.verticalGradient(listOf(light, mid, dark)), 180f, 180f, true, Offset(w * .12f, w * .52f), Size(w * .76f, w * .84f))
        // the head
        drawCircle(Brush.radialGradient(listOf(light, mid, dark), Offset(w * .44f, w * .22f), w * .26f), w * .21f, Offset(w * .5f, w * .3f))
    }
}

/** The butterfly of the logo, drawn from four petals. */
@Composable
private fun MsnButterfly(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        drawCircle(Color(0xFF2E7BE0), w * .2f, Offset(w * .36f, w * .34f))
        drawCircle(Color(0xFFE8531F), w * .17f, Offset(w * .66f, w * .3f))
        drawCircle(Color(0xFF7CB82F), w * .17f, Offset(w * .34f, w * .66f))
        drawCircle(Color(0xFFF5B400), w * .15f, Offset(w * .64f, w * .64f))
    }
}

/** The menu bar and the banner at the top of the contact list. */
@Composable
internal fun MsnTop(unread: Int, onFile: () -> Unit, onNew: () -> Unit, onTools: () -> Unit) {
    val classic = T.id == "win95"
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().background(if (classic) Color(0xFFC0C0C0) else Color(0xFFECE9D8)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(tr("File", "Dosya") to onFile, tr("Contacts", "Kişiler") to onNew, tr("Actions", "Eylemler") to onNew, tr("Tools", "Araçlar") to onTools).forEach { (l, a) ->
                Text(l, color = Color.Black, fontSize = 13.sp, modifier = Modifier.clickable(onClick = a).padding(horizontal = 8.dp, vertical = 7.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(6.dp).border(1.dp, Color(0xFFB7C3E6), androidx.compose.foundation.shape.RoundedCornerShape(if (classic) 0.dp else 6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF7F9FF), Color(0xFFD3DDF6), Color(0xFFEEF1FA)))).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MsnButterfly(22.dp)
                Spacer(Modifier.width(4.dp))
                Text("msn", color = MSN_BLUE, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)
                Text(" Messenger", color = MSN_BLUE, fontSize = 14.sp)
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).border(1.dp, Color(0xFF8FA0D0)).background(Brush.verticalGradient(listOf(Color(0xFFBFE1FF), Color(0xFFF3E7C4)))), contentAlignment = Alignment.BottomCenter) { MsnBuddy(true, 52.dp) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Pulse", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("  (${tr("Online", "Çevrimiçi")}) ▾", color = MSN_SUB, fontSize = 12.sp)
                    }
                    Text("<${tr("Type a personal message", "Kişisel bir ileti yaz")}> ▾", color = MSN_SUB, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("mail", MSN_BLUE, 18.dp)
                Text("  ($unread)", color = MSN_BLUE, fontSize = 13.sp)
                Spacer(Modifier.width(14.dp))
                MsnButterfly(16.dp)
                Text("  ${tr("MSN Today", "MSN Bugün")}", color = MSN_BLUE, fontSize = 13.sp)
            }
        }
    }
}

/** One group heading of the list, with the folding mark of the real client. */
@Composable
private fun MsnGroup(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(15.dp).border(1.dp, Color(0xFF8E96AE), androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(Color(0xFFF4F6FC)), contentAlignment = Alignment.Center) {
            Text(if (open) "−" else "+", color = Color(0xFF4A5278), fontSize = 12.sp)
        }
        Text(title, color = MSN_BLUE, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The contact list: Online (the conversations with a message today or unread) and Not Online, each folding; a tap opens the conversation, a long press picks it.
 * The "I want to..." panel closes the page, as in the old client.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MsnContactList(
    threads: List<SmsThread>, picked: Set<String>, pins: Set<String>, modifier: Modifier,
    onOpen: (SmsThread) -> Unit, onPick: (SmsThread) -> Unit, onNew: () -> Unit, onTools: () -> Unit, onDial: () -> Unit, onAttach: () -> Unit,
) {
    val (online, offline) = threads.partition { it.unread > 0 || android.text.format.DateUtils.isToday(it.date) }
    var openOn by rememberSaveable { mutableStateOf(true) }
    var openOff by rememberSaveable { mutableStateOf(true) }
    Column(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF1F4FB))) {
            for ((title, list, open, toggle) in listOf(
                Quad(tr("Online", "Çevrimiçi") + " (${online.size})", online, openOn, { openOn = !openOn }),
                Quad(tr("Not Online", "Çevrimdışı") + " (${offline.size})", offline, openOff, { openOff = !openOff }),
            )) {
                if (list.isEmpty()) continue
                item(key = "h_$title") { MsnGroup(title, open, toggle) }
                if (open) items(list.size, key = { list[it].id }) { i ->
                    val th = list[i]
                    val isOn = th in online
                    val label = th.name ?: th.address.ifEmpty { tr("Unknown", "Bilinmiyor") }
                    Row(Modifier.fillMaxWidth().background(if (th.address in picked) Color(0x553E7BE0) else Color.Transparent)
                        .combinedClickable(onClick = { onOpen(th) }, onLongClick = { onPick(th) }).padding(start = 38.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        MsnBuddy(isOn, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = Color.Black, fontSize = 14.sp, fontWeight = if (th.unread > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (th.unread > 0) Text("  (${th.unread} ${tr("new", "yeni")})", color = MSN_SUB, fontSize = 12.sp)
                                else if (th.address in pins) Text("  (${tr("pinned", "sabit")})", color = MSN_SUB, fontSize = 12.sp)
                            }
                            if (th.snippet.isNotBlank()) Text("— " + th.snippet, color = MSN_SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        // the "I want to..." panel
        Column(Modifier.fillMaxWidth().background(Color(0xFFEAF0FB))) {
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF8CA7E8), Color(0xFFCBD7F6)))).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⌄", color = Color.White, fontSize = 13.sp)
                Text("  " + tr("I want to...", "Şunu yapmak istiyorum..."), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            listOf(
                Triple("add", tr("Add a Contact", "Kişi ekle"), onNew), Triple("send", tr("Send an Instant Message", "Anlık ileti gönder"), onNew),
                Triple("attach", tr("Send a File or Photo", "Dosya veya fotoğraf gönder"), onAttach), Triple("call", tr("Make a Phone Call", "Telefon görüşmesi yap"), onDial),
                Triple("settings", tr("More", "Daha fazla"), onTools),
            ).forEach { (ic, l, a) ->
                Row(Modifier.fillMaxWidth().clickable(onClick = a).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(ic, MSN_BLUE, 18.dp)
                    Text(l, color = Color.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** A message of a conversation, written the way the client does: "Dan says:" and the text under it, a little in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MsnMessageItem(list: List<SmsMsg>, k: Int, ctx: Context, who: String) {
    val m = list[k]
    var menu by remember { mutableStateOf(false) }
    var num by remember { mutableStateOf<String?>(null) }
    val newDay = k == 0 || !sameDay(list[k - 1].date, m.date)
    val sameSender = k > 0 && list[k - 1].sent == m.sent && !newDay
    Column(Modifier.fillMaxWidth()) {
        if (newDay) Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFC3CBE0)))
            Text(dayLabel(m.date), color = MSN_SUB, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
        }
        if (!sameSender) Text((if (m.sent) tr("Me", "Ben") else who) + " " + tr("says", "diyor ki") + ":", color = MSN_SUB, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { menu = true }).padding(start = 14.dp)) {
            Column(Modifier.weight(1f)) {
                m.images.forEach { u -> MsgPicture(u, ctx); Spacer(Modifier.height(4.dp)) }
                if (m.body.isNotEmpty()) MsgLinkedText(m.body, Color.Black, MSN_BLUE, onLong = { menu = true }) { num = it }
                ThemedMenu(menu, { menu = false }) {
                    ThemedMenuItem(tr("Copy", "Kopyala"), { menu = false; copyText(ctx, m.body) }, iconKey = "copy")
                    MsgCore.otp(m.body)?.let { code -> ThemedMenuItem(tr("Copy code $code", "$code kodunu kopyala"), { menu = false; copyText(ctx, code) }, iconKey = "copy") }
                }
                if (num != null) MsgNumberMenu(true, { num = null }, num!!, ctx)
            }
            Text(timeOf(m.date), color = MSN_SUB, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp, end = 2.dp))
        }
    }
}
