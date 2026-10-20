package com.umutk.pulse

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsThread(val id: Long, val address: String, val name: String?, val snippet: String, val date: Long, var unread: Int = 0)
class SmsMsg(val body: String, val sent: Boolean, val date: Long)

/** Messages' own preferences (pins, blocks, toggles). Never Cfg/Opt. */
object MsgPrefs {
    private const val FILE = "pulse_messages"
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun pinned(c: Context): Set<String> = p(c).getStringSet("pinned", null).orEmpty().toSet()
    fun blocked(c: Context): Set<String> = p(c).getStringSet("blocked", null).orEmpty().toSet()
    fun setPinned(c: Context, s: Set<String>) { p(c).edit().putStringSet("pinned", s.toSet()).apply() }
    fun setBlocked(c: Context, s: Set<String>) { p(c).edit().putStringSet("blocked", s.toSet()).apply() }
    fun names(c: Context) = p(c).getBoolean("names", true)
    fun setNames(c: Context, v: Boolean) { p(c).edit().putBoolean("names", v).apply() }
    fun unreadFirst(c: Context) = p(c).getBoolean("unreadFirst", false)
    fun setUnreadFirst(c: Context, v: Boolean) { p(c).edit().putBoolean("unreadFirst", v).apply() }
}

private val MESSAGE_PERMS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
private fun granted(c: Context, perm: String) = ContextCompat.checkSelfPermission(c, perm) == PackageManager.PERMISSION_GRANTED
/** Reading and sending need READ_SMS and SEND_SMS; RECEIVE_SMS is not required to show the list. */
fun hasSmsPermissions(c: Context) = granted(c, Manifest.permission.READ_SMS) && granted(c, Manifest.permission.SEND_SMS)
fun hasContactPermission(c: Context) = granted(c, Manifest.permission.READ_CONTACTS)

private fun smsManager(c: Context): SmsManager = c.getSystemService(SmsManager::class.java)

/** One row per thread (the newest message of each), newest first. Unread counts come from the same pass. */
fun loadSmsThreads(c: Context, withNames: Boolean): List<SmsThread> {
    val out = LinkedHashMap<Long, SmsThread>()
    val unread = HashMap<Long, Int>()
    val names = HashMap<String, String?>()
    val canNames = withNames && hasContactPermission(c)
    c.contentResolver.query(Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
        null, null, "${Telephony.Sms.DATE} DESC")?.use { cur ->
        while (cur.moveToNext()) {
            val tid = cur.getLong(0)
            if (cur.getInt(4) == 0 && cur.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX) unread[tid] = (unread[tid] ?: 0) + 1
            if (tid in out) continue
            val addr = cur.getString(1).orEmpty()
            val name = if (canNames && addr.isNotEmpty()) names.getOrPut(addr) { contactName(c, addr) } else null
            out[tid] = SmsThread(tid, addr, name, cur.getString(2).orEmpty(), cur.getLong(3))
        }
    }
    out.values.forEach { it.unread = unread[it.id] ?: 0 }
    return out.values.toList()
}

private fun contactName(c: Context, address: String): String? = runCatching {
    c.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)),
        arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()

/** The messages of one thread (by thread id, or by address for a new conversation), oldest first. */
fun loadSmsThread(c: Context, tid: Long, address: String): List<SmsMsg> {
    val out = mutableListOf<SmsMsg>()
    val byThread = tid >= 0
    c.contentResolver.query(Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.BODY, Telephony.Sms.TYPE, Telephony.Sms.DATE),
        if (byThread) "${Telephony.Sms.THREAD_ID} = ?" else "${Telephony.Sms.ADDRESS} = ?",
        arrayOf(if (byThread) tid.toString() else address), "${Telephony.Sms.DATE} ASC")?.use { cur ->
        while (cur.moveToNext()) out += SmsMsg(cur.getString(0).orEmpty(), cur.getInt(1) != Telephony.Sms.MESSAGE_TYPE_INBOX, cur.getLong(2))
    }
    return out
}

/** Sends through the system SMS stack, which also stores the sent message in the provider. False when the send is refused. */
fun sendSms(c: Context, to: String, body: String): Boolean = runCatching {
    val sm = smsManager(c)
    sm.sendMultipartTextMessage(to, null, sm.divideMessage(body), null, null)
}.isSuccess

private fun timeOf(ms: Long) = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ms))
private fun dayKey(ms: Long) = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))
private fun sameDay(a: Long, b: Long) = dayKey(a) == dayKey(b)
private fun dayLabel(ms: Long) = if (DateUtils.isToday(ms)) tr("Today", "Bugün") else dayKey(ms)
private fun shortStamp(ms: Long) = if (DateUtils.isToday(ms)) timeOf(ms) else dayKey(ms)

private fun copyText(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("sms", text))
    Toast.makeText(ctx, tr("Copied", "Kopyalandı"), Toast.LENGTH_SHORT).show()
}

private fun shareThread(ctx: Context, who: String, msgs: List<SmsMsg>) {
    val me = tr("Me", "Ben")
    val text = msgs.joinToString("\n") { timeOf(it.date) + "  " + (if (it.sent) me else who) + ": " + it.body }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, who).putExtra(Intent.EXTRA_TEXT, text), null))
}

/** Pulse's own messages screen: conversation list, one thread, a send box, settings. Drawn from T like every other page. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasSmsPermissions(ctx)) }
    var contacts by remember { mutableStateOf(hasContactPermission(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasSmsPermissions(ctx); contacts = hasContactPermission(ctx)
    }
    var nameOn by remember { mutableStateOf(MsgPrefs.names(ctx)) }
    var unreadFirst by remember { mutableStateOf(MsgPrefs.unreadFirst(ctx)) }
    var pins by remember { mutableStateOf(MsgPrefs.pinned(ctx)) }
    var blocks by remember { mutableStateOf(MsgPrefs.blocked(ctx)) }
    var open by remember { mutableStateOf<SmsThread?>(null) }
    var newMode by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var newTo by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var threadQuery by remember { mutableStateOf("") }
    var threadSearch by remember { mutableStateOf(false) }
    val drafts = remember { mutableStateMapOf<String, String>() }
    var reload by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    fun goBack() { if (settings) settings = false else if (newMode) newMode = false else if (open != null) open = null else back() }

    LaunchedEffect(Unit) { if (!granted) ask.launch(MESSAGE_PERMS) }
    BackHandler { goBack() }
    // a new incoming or sent SMS reloads the lists without the user doing anything
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reload++ }
        }
        ctx.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, obs)
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    val threads by produceState<List<SmsThread>?>(null, granted, nameOn, contacts, reload) {
        value = if (granted) withContext(Dispatchers.IO) { loadSmsThreads(ctx, nameOn) } else emptyList()
    }
    val t = open
    val msgs by produceState<List<SmsMsg>?>(null, t?.id, t?.address, reload) {
        value = if (t == null) emptyList() else withContext(Dispatchers.IO) { loadSmsThread(ctx, t.id, t.address) }
    }
    LaunchedEffect(t?.address, msgs?.size) { listState.scrollToItem(0) }

    val shown = remember(threads, query, pins, blocks, unreadFirst) {
        val all = threads.orEmpty().filter { it.address !in blocks }
        val hit = if (query.isBlank()) all else all.filter { th ->
            th.name?.contains(query, true) == true || th.address.contains(query) || th.snippet.contains(query, true) }
        hit.sortedWith(compareByDescending<SmsThread> { it.address in pins }.thenByDescending { unreadFirst && it.unread > 0 })
    }
    val title = when {
        settings -> tr("Settings", "Ayarlar")
        newMode -> tr("New message", "Yeni mesaj")
        t != null -> t.name ?: t.address.ifEmpty { tr("Unknown", "Bilinmiyor") }
        else -> tr("Messages", "Mesajlar")
    }

    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MessagesIcon("back") { goBack() }
            Text(title, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (t != null && !settings && !newMode) {
                if (t.address.isNotEmpty()) MessagesIcon("phone") { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(t.address)))) }
                MessagesIcon("search") { threadSearch = !threadSearch; threadQuery = "" }
                MessagesIcon("share") { shareThread(ctx, t.name ?: t.address, msgs.orEmpty()) }
            } else if (granted && !settings && !newMode) {
                MessagesIcon("add") { newMode = true; newTo = "" }
                MessagesIcon("settings") { settings = true }
            }
        }
        when {
            !granted -> MessagesEmpty(Modifier.weight(1f), tr("SMS access is needed to show your messages", "Mesajları göstermek için SMS erişimi gerekir")) {
                PButton(tr("Allow SMS", "SMS izni ver"), compact = true) { ask.launch(MESSAGE_PERMS) }
            }
            settings -> MessagesSettings(nameOn, unreadFirst, blocks,
                onNames = { nameOn = it; MsgPrefs.setNames(ctx, it) },
                onUnread = { unreadFirst = it; MsgPrefs.setUnreadFirst(ctx, it) },
                onUnblock = { a -> blocks = blocks - a; MsgPrefs.setBlocked(ctx, blocks) })
            newMode -> Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PField(newTo, { newTo = it }, tr("To", "Kime"), Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                PButton(tr("Next", "İleri"), compact = true) {
                    if (newTo.isNotBlank()) { open = SmsThread(-1L, newTo.trim(), null, "", 0L); newMode = false }
                }
            }
            t == null -> {
                val all = threads
                when {
                    all == null -> Spacer(Modifier.weight(1f))
                    all.isEmpty() -> MessagesEmpty(Modifier.weight(1f), tr("No messages yet", "Henüz mesaj yok"))
                    else -> {
                        PField(query, { query = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                        if (shown.isEmpty()) MessagesEmpty(Modifier.weight(1f), tr("No matches", "Eşleşme yok"))
                        else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(shown, key = { it.id }) { th -> MessagesThreadRow(th, pins) { open = th } }
                        }
                    }
                }
            }
            else -> {
                val addr = t.address
                val pinned = addr in pins
                val blocked = addr in blocks
                if (addr.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PButton(if (pinned) tr("Unpin", "Sabitlemeyi kaldır") else tr("Pin", "Sabitle"), compact = true) {
                        pins = if (pinned) pins - addr else pins + addr; MsgPrefs.setPinned(ctx, pins)
                    }
                    Spacer(Modifier.width(8.dp))
                    PButton(if (blocked) tr("Unblock", "Engeli kaldır") else tr("Block", "Engelle"), compact = true) {
                        blocks = if (blocked) blocks - addr else blocks + addr; MsgPrefs.setBlocked(ctx, blocks)
                        if (!blocked) open = null
                    }
                }
                if (threadSearch) PField(threadQuery, { threadQuery = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                val list = msgs.orEmpty().filter { threadQuery.isBlank() || it.body.contains(threadQuery, true) }
                if (msgs != null && list.isEmpty()) MessagesEmpty(Modifier.weight(1f), tr("No messages", "Mesaj yok"))
                else LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = listState,
                    reverseLayout = true, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list.indices.reversed().toList()) { k -> MessagesItem(list, k, ctx) }
                }
                val draft = drafts[addr].orEmpty()
                val parts = runCatching { smsManager(ctx).divideMessage(draft).size }.getOrDefault(1)
                fun sendNow() {
                    val body = (drafts[addr] ?: "").trim()
                    if (body.isEmpty() || addr.isEmpty()) return
                    if (sendSms(ctx, addr, body)) { drafts.remove(addr); reload++ }
                    else Toast.makeText(ctx, tr("Not sent", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
                }
                if (draft.isNotEmpty()) Text(tr("${draft.length} chars, $parts SMS", "${draft.length} karakter, $parts SMS"),
                    color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PField(draft, { drafts[addr] = it }, tr("Message", "Mesaj"), Modifier.weight(1f), onDone = { sendNow() })
                    Spacer(Modifier.width(8.dp))
                    PButton(tr("Send", "Gönder"), compact = true) { sendNow() }
                }
            }
        }
    }
}

@Composable
private fun MessagesIcon(key: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { PIcon(key, T.text, 22.dp) }
}

@Composable
private fun MessagesEmpty(modifier: Modifier, text: String, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PIcon("chat", T.sub, 48.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = T.sub, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        action()
    }
}

@Composable
private fun MessagesAvatar(label: String) {
    val c = label.trim().firstOrNull()
    Box(Modifier.size(40.dp).clip(CircleShape).background(T.accent), contentAlignment = Alignment.Center) {
        if (c != null && c.isLetter()) Text(c.uppercase(), color = onAccent(T.accent), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        else PIcon("person", onAccent(T.accent), 22.dp)
    }
}

@Composable
private fun MessagesThreadRow(th: SmsThread, pins: Set<String>, onOpen: () -> Unit) {
    val label = th.name ?: th.address.ifEmpty { tr("Unknown", "Bilinmiyor") }
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MessagesAvatar(label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (th.address in pins) { PIcon("pin", T.sub, 14.dp); Spacer(Modifier.width(4.dp)) }
                Text(label, color = T.text, fontSize = 16.sp,
                    fontWeight = if (th.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Text(th.snippet, color = T.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(shortStamp(th.date), color = T.sub, fontSize = 12.sp, maxLines = 1)
            if (th.unread > 0) Box(Modifier.padding(top = 4.dp).clip(CircleShape).background(T.accent).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text("${th.unread}", color = onAccent(T.accent), fontSize = 11.sp)
            }
        }
    }
}

/** One bubble; [k] indexes the oldest-first [list]. A day label sits above the first message of each day. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessagesItem(list: List<SmsMsg>, k: Int, ctx: Context) {
    val m = list[k]
    val newDay = k == 0 || !sameDay(list[k - 1].date, m.date)
    Column(Modifier.fillMaxWidth()) {
        if (newDay) Text(dayLabel(m.date), color = T.sub, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.sent) Arrangement.End else Arrangement.Start) {
            Column(horizontalAlignment = if (m.sent) Alignment.End else Alignment.Start) {
                Box(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (m.sent) T.accent else T.panel)
                    .combinedClickable(onClick = {}, onLongClick = { copyText(ctx, m.body) })
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(m.body, color = if (m.sent) onAccent(T.accent) else T.text, fontSize = 15.sp)
                }
                Text(timeOf(m.date), color = T.sub, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MessagesSettings(names: Boolean, unreadFirst: Boolean, blocks: Set<String>,
                             onNames: (Boolean) -> Unit, onUnread: (Boolean) -> Unit, onUnblock: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MessagesSettingRow("users", tr("Contact names", "Kişi adları"), names, onNames)
        MessagesSettingRow("sort", tr("Unread first", "Okunmamış önce"), unreadFirst, onUnread)
        Text(tr("Blocked numbers", "Engellenen numaralar"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        if (blocks.isEmpty()) Text(tr("None", "Yok"), color = T.sub, fontSize = 14.sp)
        blocks.sorted().forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("phone", T.sub, 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(a, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                PButton(tr("Unblock", "Engeli kaldır"), compact = true) { onUnblock(a) }
            }
        }
    }
}

@Composable
private fun MessagesSettingRow(icon: String, title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, T.text, 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(title, color = T.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        PSwitch(on, onChange)
    }
}
