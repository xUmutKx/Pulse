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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.umutk.pulse.extras.messages.MsgCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsThread(val id: Long, val address: String, val name: String?, val snippet: String, val date: Long, var unread: Int = 0)
class SmsMsg(val body: String, val sent: Boolean, val date: Long, val images: List<Uri> = emptyList(), val mms: Boolean = false)

/** Messages' own preferences (pins, blocks, toggles). Never Cfg/Opt. */
object MsgPrefs {
    private const val FILE = "pulse_messages"
    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun pinned(c: Context): Set<String> = p(c).getStringSet("pinned", null).orEmpty().toSet()
    fun blocked(c: Context): Set<String> = p(c).getStringSet("blocked", null).orEmpty().toSet()
    fun setPinned(c: Context, s: Set<String>) { p(c).edit().putStringSet("pinned", s.toSet()).apply() }
    fun setBlocked(c: Context, s: Set<String>) { p(c).edit().putStringSet("blocked", s.toSet()).apply() }
    fun archived(c: Context): Set<String> = p(c).getStringSet("archived", null).orEmpty().toSet()
    fun setArchived(c: Context, s: Set<String>) { p(c).edit().putStringSet("archived", s.toSet()).apply() }
    fun names(c: Context) = p(c).getBoolean("names", true)
    fun setNames(c: Context, v: Boolean) { p(c).edit().putBoolean("names", v).apply() }
    /** Notifications of Pulse's own for incoming messages (also when another app is the default SMS app). */
    fun notify(c: Context) = p(c).getBoolean("notify", true)
    fun setNotify(c: Context, v: Boolean) { p(c).edit().putBoolean("notify", v).apply() }
    /** The message text in the notification (off: only "New message"). */
    fun showText(c: Context) = p(c).getBoolean("showText", true)
    fun setShowText(c: Context, v: Boolean) { p(c).edit().putBoolean("showText", v).apply() }
    /** A verification code in a message is copied to the clipboard at once. */
    fun autoCopy(c: Context) = p(c).getBoolean("autoCopy", true)
    fun setAutoCopy(c: Context, v: Boolean) { p(c).edit().putBoolean("autoCopy", v).apply() }
    /** A conversation notification can open as a chat bubble. */
    fun bubbles(c: Context) = p(c).getBoolean("bubbles", true)
    fun setBubbles(c: Context, v: Boolean) { p(c).edit().putBoolean("bubbles", v).apply() }
    fun sound(c: Context) = p(c).getBoolean("sound", true)
    fun setSound(c: Context, v: Boolean) { p(c).edit().putBoolean("sound", v).apply() }
    fun vibrate(c: Context) = p(c).getBoolean("vibrate", true)
    fun setVibrate(c: Context, v: Boolean) { p(c).edit().putBoolean("vibrate", v).apply() }
    fun unreadFirst(c: Context) = p(c).getBoolean("unreadFirst", false)
    fun setUnreadFirst(c: Context, v: Boolean) { p(c).edit().putBoolean("unreadFirst", v).apply() }
}

private val MESSAGE_PERMS = buildList {
    add(Manifest.permission.READ_SMS); add(Manifest.permission.SEND_SMS); add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.READ_CONTACTS)
    if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()
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
    // a picture message can be the newest of a thread
    for (m in MsgCore.newestMms(c)) {
        val old = out[m.id]
        if (old == null || m.date > old.date) {
            val addr = old?.address?.ifEmpty { null } ?: m.address
            val name = old?.name ?: if (canNames && addr.isNotEmpty()) names.getOrPut(addr) { contactName(c, addr) } else null
            out[m.id] = SmsThread(m.id, addr, name, m.snippet, m.date)
        }
    }
    out.values.forEach { it.unread = unread[it.id] ?: 0 }
    return out.values.sortedByDescending { it.date }
}

internal fun contactName(c: Context, address: String): String? = runCatching {
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
    if (byThread) out += MsgCore.loadMms(c, tid)
    out.sortBy { it.date }
    return out
}

/** Sends through the system SMS stack. The system stores the message, except when Pulse is the default SMS app (then Pulse does). False when the send is refused. */
fun sendSms(c: Context, to: String, body: String): Boolean = runCatching {
    val sm = smsManager(c)
    sm.sendMultipartTextMessage(to, null, sm.divideMessage(body), null, null)
    MsgCore.storeSent(c, to, body)
}.isSuccess

/** Sends the file at [uri] (with [text] as its text) as MMS. The library builds the MMS PDU; the system MMS stack sends it.
 *  shortcut: the whole file is read into memory, and most carriers refuse MMS above about 300 KB. False when refused. */
fun sendMms(c: Context, to: String, text: String, threadId: Long, uri: Uri): Boolean = runCatching {
    val bytes = c.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching false
    val mime = c.contentResolver.getType(uri) ?: "application/octet-stream"
    val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
    val msg = com.klinker.android.send_message.Message(text, to)
    msg.addMedia(bytes, mime, name)
    val settings = com.klinker.android.send_message.Settings().apply { setUseSystemSending(true) }
    com.klinker.android.send_message.Transaction(c, settings)
        .sendNewMessage(msg, threadId.takeIf { it >= 0 } ?: com.klinker.android.send_message.Transaction.NO_THREAD_ID)
    true
}.getOrDefault(false)

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
    var archives by remember { mutableStateOf(MsgPrefs.archived(ctx)) }
    val startIntent = remember { MsgCore.activityOf(ctx)?.intent }
    var open by remember { mutableStateOf<SmsThread?>(MsgCore.addressOf(startIntent)?.let { SmsThread(-1L, it, null, "", 0L) }) }
    var newMode by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var newTo by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var threadQuery by remember { mutableStateOf("") }
    var threadSearch by remember { mutableStateOf(false) }
    val drafts = remember { mutableStateMapOf<String, String>().also { d ->
        val a = MsgCore.addressOf(startIntent); val t0 = MsgCore.draftOf(startIntent)
        if (a != null && t0 != null) d[a] = t0
    } }
    var numMenu by remember { mutableStateOf<String?>(null) }
    var isDefault by remember { mutableStateOf(MsgCore.isDefault(ctx)) }
    var notifyOn by remember { mutableStateOf(MsgPrefs.notify(ctx)) }
    var showText by remember { mutableStateOf(MsgPrefs.showText(ctx)) }
    var autoCopy by remember { mutableStateOf(MsgPrefs.autoCopy(ctx)) }
    var bubbleOn by remember { mutableStateOf(MsgPrefs.bubbles(ctx)) }
    var soundOn by remember { mutableStateOf(MsgPrefs.sound(ctx)) }
    var vibOn by remember { mutableStateOf(MsgPrefs.vibrate(ctx)) }
    val askDefault = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = MsgCore.isDefault(ctx) }
    // the file picked for one conversation (by address); it goes out as MMS with that conversation's text
    val attach = remember { mutableStateMapOf<String, Uri>() }
    var pickFor by remember { mutableStateOf("") }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && pickFor.isNotEmpty()) attach[pickFor] = uri
    }
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

    val shown = remember(threads, query, pins, blocks, archives, unreadFirst) {
        val all = threads.orEmpty().filter { it.address !in blocks && it.address !in archives }
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

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MessagesIcon("back") { goBack() }
            // a long press on the contact or number in a thread copies the number
            Box(Modifier.weight(1f)) {
                Text(title, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.combinedClickable(onClick = {
                        if (t != null && !settings && !newMode && t.address.isNotEmpty()) numMenu = t.address
                    }, onLongClick = {
                        if (t != null && t.address.isNotEmpty()) copyText(ctx, t.address)
                    }))
                if (t != null && !settings && !newMode) MsgNumberMenu(numMenu != null && numMenu == t.address, { numMenu = null }, t.address, ctx)
            }
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
            settings -> MessagesSettings(nameOn, unreadFirst, blocks, archives,
                isDefault, { runCatching { askDefault.launch(MsgCore.defaultRequest(ctx)) } },
                listOf(notifyOn, showText, autoCopy, bubbleOn, soundOn, vibOn),
                { k, v -> when (k) {
                    0 -> { notifyOn = v; MsgPrefs.setNotify(ctx, v) }; 1 -> { showText = v; MsgPrefs.setShowText(ctx, v) }
                    2 -> { autoCopy = v; MsgPrefs.setAutoCopy(ctx, v) }; 3 -> { bubbleOn = v; MsgPrefs.setBubbles(ctx, v) }
                    4 -> { soundOn = v; MsgPrefs.setSound(ctx, v) }; else -> { vibOn = v; MsgPrefs.setVibrate(ctx, v) }
                } },
                onNames = { nameOn = it; MsgPrefs.setNames(ctx, it) },
                onUnread = { unreadFirst = it; MsgPrefs.setUnreadFirst(ctx, it) },
                onUnblock = { a -> blocks = blocks - a; MsgPrefs.setBlocked(ctx, blocks) },
                onUnarchive = { a -> archives = archives - a; MsgPrefs.setArchived(ctx, archives) })
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
                            items(shown, key = { it.id }) { th -> MessagesThreadRow(th, pins, { open = th },
                                onPin = { pins = if (th.address in pins) pins - th.address else pins + th.address; MsgPrefs.setPinned(ctx, pins) },
                                onArchive = { archives = archives + th.address; MsgPrefs.setArchived(ctx, archives) },
                                onBlock = { blocks = blocks + th.address; MsgPrefs.setBlocked(ctx, blocks) }) }
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
                // a picked file goes as MMS with the text; without one it is a plain SMS
                val file = attach[addr]
                fun sendNow() {
                    val body = (drafts[addr] ?: "").trim()
                    if (addr.isEmpty()) return
                    if (file != null) {
                        if (sendMms(ctx, addr, body, t.id, file)) { attach.remove(addr); drafts.remove(addr); reload++ }
                        else Toast.makeText(ctx, tr("Not sent", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
                        return
                    }
                    if (body.isEmpty()) return
                    if (sendSms(ctx, addr, body)) { drafts.remove(addr); reload++ }
                    else Toast.makeText(ctx, tr("Not sent", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
                }
                if (draft.isNotEmpty()) Text(tr("${draft.length} chars, $parts SMS", "${draft.length} karakter, $parts SMS"),
                    color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp))
                if (file != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("attach", T.sub, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(file.lastPathSegment?.substringAfterLast('/') ?: "attachment", color = T.text, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    MessagesIcon("close") { attach.remove(addr) }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MessagesIcon("attach") { pickFor = addr; pickFile.launch("*/*") }
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

/** One conversation row. A long press opens its menu: pin, archive, block. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessagesThreadRow(th: SmsThread, pins: Set<String>, onOpen: () -> Unit, onPin: () -> Unit, onArchive: () -> Unit, onBlock: () -> Unit) {
    val label = th.name ?: th.address.ifEmpty { tr("Unknown", "Bilinmiyor") }
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
    ThemedMenu(menu, { menu = false }) {
        ThemedMenuItem(if (th.address in pins) tr("Unpin", "Sabitlemeyi kaldır") else tr("Pin", "Sabitle"), { menu = false; onPin() })
        ThemedMenuItem(tr("Archive", "Arşivle"), { menu = false; onArchive() })
        ThemedMenuItem(tr("Block", "Engelle"), { menu = false; onBlock() })
    }
    }
}

/** One bubble; [k] indexes the oldest-first [list]. A day label sits above the first message of each day. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessagesItem(list: List<SmsMsg>, k: Int, ctx: Context) {
    val m = list[k]
    var menu by remember { mutableStateOf(false) }
    var num by remember { mutableStateOf<String?>(null) }
    val newDay = k == 0 || !sameDay(list[k - 1].date, m.date)
    Column(Modifier.fillMaxWidth()) {
        if (newDay) Text(dayLabel(m.date), color = T.sub, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.sent) Arrangement.End else Arrangement.Start) {
            Column(horizontalAlignment = if (m.sent) Alignment.End else Alignment.Start) {
                Box(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (m.sent) T.accent else T.panel)
                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                    .padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Column {
                        m.images.forEach { u -> MsgPicture(u, ctx); Spacer(Modifier.height(4.dp)) }
                        if (m.body.isNotEmpty()) MsgLinkedText(m.body, if (m.sent) onAccent(T.accent) else T.text, if (m.sent) onAccent(T.accent) else T.accent, onLong = { menu = true }) { num = it }
                    }
                    ThemedMenu(menu, { menu = false }) {
                        ThemedMenuItem(tr("Copy", "Kopyala"), { menu = false; copyText(ctx, m.body) }, iconKey = "copy")
                        MsgCore.otp(m.body)?.let { code -> ThemedMenuItem(tr("Copy code $code", "$code kodunu kopyala"), { menu = false; copyText(ctx, code) }, iconKey = "copy") }
                    }
                    if (num != null) MsgNumberMenu(true, { num = null }, num!!, ctx)
                }
                Text(timeOf(m.date), color = T.sub, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MessagesSettings(names: Boolean, unreadFirst: Boolean, blocks: Set<String>, archives: Set<String>,
                             isDefault: Boolean, onDefault: () -> Unit, notif: List<Boolean>, onNotif: (Int, Boolean) -> Unit,
                             onNames: (Boolean) -> Unit, onUnread: (Boolean) -> Unit, onUnblock: (String) -> Unit, onUnarchive: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().clickable(enabled = !isDefault, onClick = onDefault).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("chat", T.text, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Default SMS app", "Varsayılan SMS uygulaması"), color = T.text, fontSize = 16.sp)
                Text(if (isDefault) tr("Pulse receives and stores your messages", "Mesajları Pulse alır ve saklar")
                    else tr("Another app is the default; Pulse still notifies", "Başka uygulama varsayılan; Pulse yine bildirir"), color = T.sub, fontSize = 12.sp)
            }
            if (!isDefault) PButton(tr("Set", "Yap"), compact = true) { onDefault() }
        }
        MessagesSettingRow("bell", tr("Notifications", "Bildirimler"), notif[0]) { onNotif(0, it) }
        MessagesSettingRow("eyeOn", tr("Show message text", "Mesaj metnini göster"), notif[1]) { onNotif(1, it) }
        MessagesSettingRow("copy", tr("Copy verification codes", "Doğrulama kodunu kopyala"), notif[2]) { onNotif(2, it) }
        MessagesSettingRow("chat", tr("Chat bubbles", "Sohbet balonları"), notif[3]) { onNotif(3, it) }
        MessagesSettingRow("volume", tr("Sound", "Ses"), notif[4]) { onNotif(4, it) }
        MessagesSettingRow("vibrate", tr("Vibrate", "Titreşim"), notif[5]) { onNotif(5, it) }
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
        Text(tr("Archived", "Arşivlenen"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        if (archives.isEmpty()) Text(tr("None", "Yok"), color = T.sub, fontSize = 14.sp)
        archives.sorted().forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("chat", T.sub, 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(a, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                PButton(tr("Unarchive", "Arşivden çıkar"), compact = true) { onUnarchive(a) }
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


/** The menu of a phone number: open its contact (or add one), call, copy. */
@Composable
private fun MsgNumberMenu(open: Boolean, onDismiss: () -> Unit, number: String, ctx: Context) {
    ThemedMenu(open, onDismiss) {
        val known = remember(number) { contactName(ctx, number) != null }
        ThemedMenuItem(if (known) tr("Open contact", "Kişiyi aç") else tr("Add contact", "Kişi ekle"), {
            onDismiss()
            val i = if (known) {
                val c = ctx.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) to it.getString(1) else null }
                Intent(Intent.ACTION_VIEW, if (c != null) ContactsContract.Contacts.getLookupUri(c.first, c.second) else ContactsContract.Contacts.CONTENT_URI)
            } else Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, number)
            runCatching { ctx.startActivity(i) }
        }, iconKey = "person")
        ThemedMenuItem(tr("Call", "Ara"), { onDismiss(); runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) } }, iconKey = "phone")
        ThemedMenuItem(tr("Copy", "Kopyala"), { onDismiss(); copyText(ctx, number) }, iconKey = "copy")
    }
}

/** Message text whose phone numbers open the number menu and whose links open in the browser. */
@Composable
private fun MsgLinkedText(body: String, color: androidx.compose.ui.graphics.Color, link: androidx.compose.ui.graphics.Color, onLong: () -> Unit, onNumber: (String) -> Unit) {
    val ctx = LocalContext.current
    val links = remember(body) { MsgCore.links(body) }
    val text = remember(body, color, link) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(body)
            addStyle(androidx.compose.ui.text.SpanStyle(color = color), 0, body.length)
            links.forEach { (a, b, _) -> addStyle(androidx.compose.ui.text.SpanStyle(color = link, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), a, b) }
        }
    }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(text, fontSize = 15.sp, onTextLayout = { layout = it }, modifier = Modifier.androidx_pointer(links, body, ctx, onLong, onNumber) { layout })
}

private fun Modifier.androidx_pointer(links: List<Triple<Int, Int, String>>, body: String, ctx: Context, onLong: () -> Unit, onNumber: (String) -> Unit,
                                      layout: () -> androidx.compose.ui.text.TextLayoutResult?): Modifier =
    this.pointerInput(links, body) {
        detectTapGestures(onLongPress = { onLong() }, onTap = { pos ->
            val off = layout()?.getOffsetForPosition(pos) ?: return@detectTapGestures
            val hit = links.firstOrNull { off >= it.first && off < it.second } ?: return@detectTapGestures
            val v = body.substring(hit.first, hit.second)
            if (hit.third == "tel") onNumber(v.trim())
            else runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (v.startsWith("http", true)) v else "https://$v"))) }
        })
    }

/** A picture of an MMS, decoded small; tapping opens it in a viewer. */
@Composable
private fun MsgPicture(uri: Uri, ctx: Context) {
    val bmp by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, o) }
                var ss = 1; while (o.outWidth / ss > 900) ss *= 2
                ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = ss }) }
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(10.dp))
        .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } },
        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
    else Box(Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)).background(T.sub.copy(alpha = .2f)))
}
