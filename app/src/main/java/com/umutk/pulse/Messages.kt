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
    /** When the conversation with this address was last opened: its messages up to this time are read. Kept here because the phone
     *  only lets the default SMS app mark messages read. */
    fun readUpTo(c: Context, addr: String): Long = p(c).getLong("read_$addr", 0L)
    fun setReadUpTo(c: Context, addr: String, t: Long) { p(c).edit().putLong("read_$addr", t).apply() }
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
    /** Text added to the end of every message sent from here (empty: none). */
    fun signature(c: Context) = p(c).getString("signature", "") ?: ""
    fun setSignature(c: Context, v: String) { p(c).edit().putString("signature", v).apply() }
    /** Unwanted (advertising, gambling, loan...) messages from numbers that are not in the contacts are blocked on their own. */
    fun spamBlock(c: Context) = p(c).getBoolean("spamBlock", true)
    fun setSpamBlock(c: Context, v: Boolean) { p(c).edit().putBoolean("spamBlock", v).apply() }
    /** Seconds a message waits before it goes out, so it can still be taken back (0: at once). */
    fun sendDelay(c: Context) = p(c).getInt("sendDelay", 0)
    fun setSendDelay(c: Context, v: Int) { p(c).edit().putInt("sendDelay", v).apply() }
    /** Conversations (by number) whose messages make no notification. */
    fun muted(c: Context): Set<String> = p(c).getStringSet("muted", null).orEmpty().toSet()
    fun setMuted(c: Context, s: Set<String>) { p(c).edit().putStringSet("muted", s.toSet()).apply() }
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

@Suppress("DEPRECATION")
private fun smsManager(c: Context): SmsManager = if (android.os.Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()

/** One row per thread (the newest message of each), newest first. Unread counts come from the same pass. */
fun loadSmsThreads(c: Context, withNames: Boolean): List<SmsThread> {
    val out = LinkedHashMap<Long, SmsThread>()
    val unread = HashMap<Long, Int>()
    val names = HashMap<String, String?>()
    val readTo = HashMap<String, Long>()
    val canNames = withNames && hasContactPermission(c)
    c.contentResolver.query(Telephony.Sms.CONTENT_URI,
        arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
        null, null, "${Telephony.Sms.DATE} DESC")?.use { cur ->
        while (cur.moveToNext()) {
            val tid = cur.getLong(0)
            val addr = cur.getString(1).orEmpty()
            // a message is unread when the phone says so and it is newer than the last time Pulse opened its conversation
            val upTo = readTo.getOrPut(addr) { MsgPrefs.readUpTo(c, addr) }
            if (cur.getInt(4) == 0 && cur.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX && cur.getLong(3) > upTo) unread[tid] = (unread[tid] ?: 0) + 1
            if (tid in out) continue
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
    // the texts Pulse keeps itself: a thread with a newer sent text moves to the top, and a new conversation is listed
    for ((addr, body, date) in Outbox.latestPerAddress(c)) {
        val old = out.values.firstOrNull { it.address == addr }
        if (old == null) {
            val id = -2L - (addr.hashCode().toLong() and 0x7fffffffL)
            out[id] = SmsThread(id, addr, if (canNames) names.getOrPut(addr) { contactName(c, addr) } else null, body, date)
        } else if (date > old.date) out[old.id] = SmsThread(old.id, old.address, old.name, body, date)
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
    out += Outbox.of(c, address)
    out.sortBy { it.date }
    return out
}

/** Deletes every text of the given conversations (the system stores them, so only the default SMS app may). False when refused. */
fun deleteThreads(c: Context, addrs: Set<String>): Boolean = runCatching {
    addrs.forEach { a ->
        c.contentResolver.delete(Telephony.Sms.CONTENT_URI, "${Telephony.Sms.ADDRESS} = ?", arrayOf(a))
        Outbox.forget(c, a)
    }
    true
}.getOrDefault(false)

/** Sends through the system SMS stack. Every part reports back to [com.umutk.pulse.extras.messages.MsgSentReceiver]: the message is kept
 *  (as Pulse's own sent message when Pulse is the default SMS app) only once the radio has really sent it, and a refusal is shown then.
 *  False when the send is refused at once. */
fun sendSms(c: Context, to: String, body: String): Boolean {
    // the text shows in its thread at once; the report removes it again when the radio refuses it
    val key = Outbox.add(c, to, body)
    val ok = runCatching {
        val sm = smsManager(c)
        val parts = sm.divideMessage(body)
        val base = (System.currentTimeMillis() and 0x7fffffffL).toInt()
        val sent = parts.indices.map { i ->
            val report = Intent(com.umutk.pulse.extras.messages.MsgSentReceiver.ACTION)
                .setClass(c, com.umutk.pulse.extras.messages.MsgSentReceiver::class.java)
                .putExtra("address", to).putExtra("body", body).putExtra("key", key).putExtra("last", i == parts.lastIndex)
            android.app.PendingIntent.getBroadcast(c, base + i, report, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }
        sm.sendMultipartTextMessage(to, null, parts, ArrayList(sent), null)
        true
    }.getOrDefault(false)
    if (!ok) Outbox.remove(c, key)
    return ok
}

/** Sent texts Pulse keeps itself: the system does not write a sent text when another app is the default SMS app, so the thread would show nothing. */
object Outbox {
    private const val SEP = '\u0001'
    private fun sp(c: Context) = c.getSharedPreferences("msg_outbox", Context.MODE_PRIVATE)
    private fun rows(c: Context): Set<String> = sp(c).getStringSet("rows", emptySet()).orEmpty()
    private fun save(c: Context, r: Set<String>) { sp(c).edit().putStringSet("rows", r).apply() }
    private fun timeOf(row: String) = row.substringBefore(SEP).toLongOrNull() ?: 0L

    /** Adds a text, keeps the newest 500; returns its key, which [remove] takes. */
    fun add(c: Context, address: String, body: String): Long {
        val now = System.currentTimeMillis()
        save(c, (rows(c).sortedBy { timeOf(it) }.takeLast(499) + "$now$SEP$address$SEP$body").toSet())
        return now
    }
    fun remove(c: Context, key: Long) { save(c, rows(c).filterNot { timeOf(it) == key }.toSet()) }
    fun forget(c: Context, address: String) { save(c, rows(c).filterNot { it.split(SEP, limit = 3).getOrNull(1) == address }.toSet()) }
    /** The newest outbox text of each address: (address, body, time). */
    fun latestPerAddress(c: Context): List<Triple<String, String, Long>> = rows(c).mapNotNull { r ->
        val p = r.split(SEP, limit = 3)
        if (p.size < 3) null else Triple(p[1], p[2], timeOf(r))
    }.groupBy { it.first }.map { (_, v) -> v.maxBy { it.third } }
    fun of(c: Context, address: String): List<SmsMsg> = rows(c).mapNotNull { r ->
        val p = r.split(SEP, limit = 3)
        if (p.size < 3 || p[1] != address) null else SmsMsg(p[2], true, timeOf(r))
    }
}

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
    // conversations picked by a long press, for the bulk actions above the list
    var picked by remember { mutableStateOf(setOf<String>()) }
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
    var sigText by remember { mutableStateOf(MsgPrefs.signature(ctx)) }
    var delaySec by remember { mutableIntStateOf(MsgPrefs.sendDelay(ctx)) }
    var muted by remember { mutableStateOf(MsgPrefs.muted(ctx)) }
    // a message waiting to go out: (number, text, seconds left); Undo puts the text back in the box
    var queued by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    var isDefault by remember { mutableStateOf(MsgCore.isDefault(ctx)) }
    var notifyOn by remember { mutableStateOf(MsgPrefs.notify(ctx)) }
    var showText by remember { mutableStateOf(MsgPrefs.showText(ctx)) }
    var autoCopy by remember { mutableStateOf(MsgPrefs.autoCopy(ctx)) }
    var bubbleOn by remember { mutableStateOf(MsgPrefs.bubbles(ctx)) }
    var soundOn by remember { mutableStateOf(MsgPrefs.sound(ctx)) }
    var vibOn by remember { mutableStateOf(MsgPrefs.vibrate(ctx)) }
    var spamOn by remember { mutableStateOf(MsgPrefs.spamBlock(ctx)) }
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
    // a conversation that is open is read: its notification goes and its unread count is cleared
    LaunchedEffect(open?.address) {
        val addr = open?.address ?: return@LaunchedEffect
        com.umutk.pulse.extras.messages.MsgNotifier.cancel(ctx, addr)
        withContext(Dispatchers.IO) {
            MsgCore.markRead(ctx, addr)
            MsgPrefs.setReadUpTo(ctx, addr, System.currentTimeMillis())
        }
        reload++
    }
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
    // a number typed in "New message" still shows the contact's name
    val openName = remember(t?.address) { t?.let { it.name ?: contactName(ctx, it.address) } }
    val title = when {
        settings -> tr("Settings", "Ayarlar")
        newMode -> tr("New message", "Yeni mesaj")
        t != null -> openName ?: t.address.ifEmpty { tr("Unknown", "Bilinmiyor") }
        else -> tr("Messages", "Mesajlar")
    }

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // the arrow leads out of a thread, New message or Settings; the list has none (the system back still leaves)
            if (t != null || newMode || settings) MessagesIcon("back") { goBack() }
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
                if (t.address.isNotEmpty()) MessagesIcon("call") { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(t.address)))) }
                MessagesIcon("search") { threadSearch = !threadSearch; threadQuery = "" }
                MessagesIcon("share") { shareThread(ctx, t.name ?: t.address, msgs.orEmpty()) }
            } else if (granted && !settings && !newMode) {
                if (threads.isNullOrEmpty()) MessagesIcon("add") { newMode = true; newTo = "" }
                MessagesIcon("settings") { settings = true }
            }
        }
        when {
            !granted -> MessagesEmpty(Modifier.weight(1f), tr("SMS access is needed to show your messages", "Mesajları göstermek için SMS erişimi gerekir")) {
                PButton(tr("Allow SMS", "SMS izni ver"), compact = true) { ask.launch(MESSAGE_PERMS) }
            }
            settings -> MessagesSettings(nameOn, unreadFirst, blocks, archives,
                isDefault, { runCatching { askDefault.launch(MsgCore.defaultRequest(ctx)) } },
                listOf(notifyOn, showText, autoCopy, bubbleOn, soundOn, vibOn, spamOn), sigText, { sigText = it; MsgPrefs.setSignature(ctx, it) },
                delaySec, { delaySec = it; MsgPrefs.setSendDelay(ctx, it) },
                { k, v -> when (k) {
                    0 -> { notifyOn = v; MsgPrefs.setNotify(ctx, v) }; 1 -> { showText = v; MsgPrefs.setShowText(ctx, v) }
                    2 -> { autoCopy = v; MsgPrefs.setAutoCopy(ctx, v) }; 3 -> { bubbleOn = v; MsgPrefs.setBubbles(ctx, v) }
                    4 -> { soundOn = v; MsgPrefs.setSound(ctx, v) }; 6 -> { spamOn = v; MsgPrefs.setSpamBlock(ctx, v) }; else -> { vibOn = v; MsgPrefs.setVibrate(ctx, v) }
                } },
                onNames = { nameOn = it; MsgPrefs.setNames(ctx, it) },
                onUnread = { unreadFirst = it; MsgPrefs.setUnreadFirst(ctx, it) },
                onUnblock = { a -> blocks = blocks - a; MsgPrefs.setBlocked(ctx, blocks) },
                onUnarchive = { a -> archives = archives - a; MsgPrefs.setArchived(ctx, archives) })
            newMode -> Column(Modifier.fillMaxWidth().weight(1f)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PField(newTo, { newTo = it }, tr("Message to…", "Mesaj gönder…"), Modifier.weight(1f).fillMaxWidth())
                    Spacer(Modifier.width(8.dp))
                    PButton(tr("Next", "İleri"), compact = true) {
                        if (newTo.isNotBlank()) { open = SmsThread(-1L, newTo.trim(), null, "", 0L); newMode = false }
                    }
                }
                // the contacts whose name or number matches what is typed (all of them, newest first, while nothing is typed)
                val hits by androidx.compose.runtime.produceState(emptyList<Pair<String, String>>(), newTo) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val q = newTo.trim()
                            val uri = if (q.isEmpty()) ContactsContract.CommonDataKinds.Phone.CONTENT_URI else Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
                            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE")?.use { c ->
                                val out = ArrayList<Pair<String, String>>(); val seen = HashSet<String>()
                                while (c.moveToNext() && out.size < 40) { val n = c.getString(0).orEmpty(); val num = c.getString(1).orEmpty(); if (num.isNotBlank() && seen.add(num.filter { it.isDigit() })) out += n to num }
                                out
                            }.orEmpty()
                        }.getOrDefault(emptyList())
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(hits) { (name, num) ->
                        Row(Modifier.fillMaxWidth().clickable { open = SmsThread(-1L, num, null, "", 0L); newMode = false }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(name.ifBlank { num }, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (name.isNotBlank()) Text(num, color = T.sub, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            t == null -> {
                val all = threads
                when {
                    all == null -> Spacer(Modifier.weight(1f))
                    all.isEmpty() -> MessagesEmpty(Modifier.weight(1f), tr("No messages yet", "Henüz mesaj yok"))
                    else -> {
                        if (picked.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            MessagesIcon("close") { picked = emptySet() }
                            Text("${picked.size}", color = T.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            MessagesIcon("pin") { pins = if (picked.all { it in pins }) pins - picked else pins + picked; MsgPrefs.setPinned(ctx, pins); picked = emptySet() }
                            MessagesIcon("mute") { muted = if (picked.all { it in muted }) muted - picked else muted + picked; MsgPrefs.setMuted(ctx, muted); picked = emptySet() }
                            MessagesIcon("archive") { archives = archives + picked; MsgPrefs.setArchived(ctx, archives); picked = emptySet() }
                            MessagesIcon("recycle") {
                                if (deleteThreads(ctx, picked)) { picked = emptySet(); reload++ }
                                else Toast.makeText(ctx, tr("Pulse must be the default SMS app to delete", "Silmek için Pulse varsayılan SMS uygulaması olmalı"), Toast.LENGTH_SHORT).show()
                            }
                        }
                        PField(query, { query = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                        if (shown.isEmpty()) MessagesEmpty(Modifier.weight(1f), tr("No matches", "Eşleşme yok"))
                        else Box(Modifier.weight(1f).fillMaxWidth()) { LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.id }) { th -> MessagesThreadRow(th, pins, muted, { if (picked.isEmpty()) open = th else picked = if (th.address in picked) picked - th.address else picked + th.address },
                                selected = th.address in picked, onPick = { picked = if (th.address in picked) picked - th.address else picked + th.address },
                                onMute = { muted = if (th.address in muted) muted - th.address else muted + th.address; MsgPrefs.setMuted(ctx, muted) },
                                onPin = { pins = if (th.address in pins) pins - th.address else pins + th.address; MsgPrefs.setPinned(ctx, pins) },
                                onArchive = { archives = archives + th.address; MsgPrefs.setArchived(ctx, archives) },
                                onBlock = { blocks = blocks + th.address; MsgPrefs.setBlocked(ctx, blocks) }) }
                        }
                            Box(Modifier.align(Alignment.BottomEnd).padding(18.dp).size(56.dp).clip(CircleShape).background(T.accent).clickable { newMode = true; newTo = "" }, contentAlignment = Alignment.Center) { PIcon("add", onAccent(T.accent), 26.dp) }
                        }
                    }
                }
            }
            else -> {
                val addr = t.address
                val pinned = addr in pins
                val blocked = addr in blocks
                // one ⋮ for this contact: pin, mute and block are here, not as buttons of their own; block asks first
                var threadMenu by remember { mutableStateOf(false) }
                var blockAsk by remember { mutableStateOf(false) }
                if (addr.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    // the menu hangs from the ⋮ button itself, so it opens on the right, not at the screen's left edge
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().clickable { threadMenu = true }, contentAlignment = Alignment.Center) { PIcon("more", T.sub, 22.dp) }
                    ThemedMenu(threadMenu, { threadMenu = false }) {
                        ThemedMenuItem(if (pinned) tr("Unpin", "Sabitlemeyi kaldır") else tr("Pin", "Sabitle"), { threadMenu = false; pins = if (pinned) pins - addr else pins + addr; MsgPrefs.setPinned(ctx, pins) }, iconKey = "pin")
                        ThemedMenuItem(if (addr in muted) tr("Unmute", "Sesi aç") else tr("Mute", "Sessize al"), { threadMenu = false; muted = if (addr in muted) muted - addr else muted + addr; MsgPrefs.setMuted(ctx, muted) }, iconKey = "mute")
                        ThemedMenuItem(if (blocked) tr("Unblock", "Engeli kaldır") else tr("Block", "Engelle"), { threadMenu = false; if (blocked) { blocks = blocks - addr; MsgPrefs.setBlocked(ctx, blocks) } else blockAsk = true }, iconKey = "block")
                    }
                    }
                    if (blockAsk) WinAlert(onDismissRequest = { blockAsk = false }, title = { Text(tr("Block this number?", "Bu numara engellensin mi?"), color = T.text) },
                        text = { Text(addr, color = T.sub) },
                        confirmButton = { PButton(tr("Block", "Engelle")) { blockAsk = false; blocks = blocks + addr; MsgPrefs.setBlocked(ctx, blocks); open = null } },
                        dismissButton = { PButton(tr("Cancel", "İptal")) { blockAsk = false } })
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
                    val full = if (sigText.isBlank()) body else body + "\n" + sigText.trim()
                    if (delaySec > 0) { queued = Triple(addr, full, delaySec); drafts.remove(addr); return }
                    if (sendSms(ctx, addr, full)) { drafts.remove(addr); reload++ }
                    else Toast.makeText(ctx, tr("Not sent", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
                }
                queued?.let { q ->
                    LaunchedEffect(q.first, q.second, q.third) {
                        kotlinx.coroutines.delay(1000)
                        if (q.third <= 1) {
                            if (sendSms(ctx, q.first, q.second)) reload++ else Toast.makeText(ctx, tr("Not sent", "Gönderilemedi"), Toast.LENGTH_SHORT).show()
                            queued = null
                        } else queued = q.copy(third = q.third - 1)
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Sending in ${q.third}…", "${q.third} sn sonra gidiyor…"), color = T.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        PButton(tr("Undo", "Geri al"), compact = true) { drafts[q.first] = q.second.removeSuffix("\n" + sigText.trim()); queued = null }
                    }
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
                    MessagesIcon("attach", black = true) { pickFor = addr; pickFile.launch("*/*") }
                    PField(draft, { drafts[addr] = it }, tr("Message", "Mesaj"), Modifier.weight(1f), onDone = { sendNow() })
                    Spacer(Modifier.width(8.dp))
                    MessagesIcon("send", black = true) { sendNow() }
                }
            }
        }
    }
}

@Composable
private fun MessagesIcon(key: String, black: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (black) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent).clickable(onClick = onClick), contentAlignment = Alignment.Center) { PIcon(key, if (black) androidx.compose.ui.graphics.Color.White else T.text, 22.dp) }
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
private fun MessagesThreadRow(th: SmsThread, pins: Set<String>, muted: Set<String>, onOpen: () -> Unit, onPin: () -> Unit, onMute: () -> Unit, onArchive: () -> Unit, onBlock: () -> Unit, selected: Boolean = false, onPick: () -> Unit = {}) {
    val label = th.name ?: th.address.ifEmpty { tr("Unknown", "Bilinmiyor") }
    Box(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().background(if (selected) T.accent.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent).combinedClickable(onClick = onOpen, onLongClick = onPick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                             isDefault: Boolean, onDefault: () -> Unit, notif: List<Boolean>, sig: String, onSig: (String) -> Unit, delay: Int, onDelay: (Int) -> Unit, onNotif: (Int, Boolean) -> Unit,
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
        MessagesSettingRow("lock", tr("Block spam automatically", "Spam mesajları otomatik engelle"), notif[6]) { onNotif(6, it) }
        MessagesSettingRow("bell", tr("Notifications", "Bildirimler"), notif[0]) { onNotif(0, it) }
        MessagesSettingRow("eyeOn", tr("Show message text", "Mesaj metnini göster"), notif[1]) { onNotif(1, it) }
        MessagesSettingRow("copy", tr("Copy verification codes", "Doğrulama kodunu kopyala"), notif[2]) { onNotif(2, it) }
        MessagesSettingRow("chat", tr("Chat bubbles", "Sohbet balonları"), notif[3]) { onNotif(3, it) }
        MessagesSettingRow("volume", tr("Sound", "Ses"), notif[4]) { onNotif(4, it) }
        MessagesSettingRow("vibrate", tr("Vibrate", "Titreşim"), notif[5]) { onNotif(5, it) }
        Text(tr("Signature", "İmza"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        PField(sig, onSig, tr("Added to every message", "Her mesajın sonuna eklenir"), Modifier.fillMaxWidth())
        Text(tr("Undo time before sending", "Göndermeden önce geri alma süresi"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 3, 5, 10).forEach { v -> PChip(delay == v, if (v == 0) tr("Off", "Kapalı") else "$v s") { onDelay(v) } } }
        MessagesSettingRow("users", tr("Contact names", "Kişi adları"), names, onNames)
        MessagesSettingRow("sort", tr("Unread first", "Okunmamış önce"), unreadFirst, onUnread)
        Text(tr("Blocked numbers", "Engellenen numaralar"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        if (blocks.isEmpty()) Text(tr("None", "Yok"), color = T.sub, fontSize = 14.sp)
        blocks.sorted().forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("call", T.sub, 20.dp)
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
        ThemedMenuItem(tr("Call", "Ara"), { onDismiss(); runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) } }, iconKey = "call")
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
