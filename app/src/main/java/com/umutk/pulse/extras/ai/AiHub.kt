package com.umutk.pulse.extras.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PLoader
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PProgress
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.onAccent
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pulse AI: local chat (llama.cpp) and speech to text (whisper.cpp), an extra app inside Pulse. Only the models are outside: they are downloaded
 * into Download/PulseAI (never into the app's data), or any GGUF already on the phone is used where it is. Drawn in the look of the app
 * (Material, AMOLED black, the Windows looks) with Pulse's own controls. Ideas from Pumpkin: picks by the phone's memory with "runs well /
 * slowly / too big", a search of every GGUF on Hugging Face, models already on the phone, a memory of the person, the chat kept between starts.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AiHub(onBack: () -> Unit) {
    val c = LocalContext.current
    val sp = remember { c.getSharedPreferences("ai_app", Context.MODE_PRIVATE) }
    var welcomed by remember { mutableStateOf(sp.getBoolean("welcomed", false)) }
    BackHandler { onBack() }
    if (!aiSupported) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon("info", T.sub, 40.dp)
            Text(tr("Pulse AI needs a 64-bit ARM phone with Android 9 or newer.", "Pulse AI, Android 9 ve üstü 64 bit ARM bir telefon ister."), color = T.text, fontSize = 15.sp, modifier = Modifier.padding(top = 12.dp))
        }
        return
    }
    if (!welcomed) { AiWelcome { sp.edit().putBoolean("welcomed", true).apply(); welcomed = true }; return }
    // the chats kept on the phone: a drawer (the menu at the top left) lists them; the + at its bottom starts a new one
    val chats = remember { mutableStateListOf<ChatRec>().apply { addAll(chatsOf(c)); if (isEmpty()) add(ChatRec(newChatId(), mutableStateListOf())) } }
    var cur by rememberSaveable { mutableStateOf(chats.first().id) }
    var models by rememberSaveable { mutableStateOf(false) }
    var mascotOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler { if (drawer.isOpen) scope.launch { drawer.close() } else if (models) models = false else onBack() }
    val chat = chats.firstOrNull { it.id == cur } ?: chats.first().also { cur = it.id }
    fun openChat(id: String) { cur = id; models = false; scope.launch { drawer.close() } }
    if (settingsOpen) AiSettingsDialog { settingsOpen = false }
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = AiPanel, drawerContentColor = AiInk, modifier = Modifier.width(300.dp)) {
                Column(Modifier.fillMaxHeight().statusBarsPadding().padding(vertical = 12.dp)) {
                    Text("Pulse AI", color = AiInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(chats.toList(), key = { it.id }) { ch ->
                            val on = ch.id == cur && !models
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) AiInk.copy(alpha = .08f) else Color.Transparent).clickable { openChat(ch.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(ch.title.ifEmpty { tr("New chat", "Yeni sohbet") }, color = if (ch.title.isEmpty()) AiSub else AiInk, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Box(Modifier.size(32.dp).clickable {
                                    chats.remove(ch)
                                    if (chats.isEmpty()) chats.add(ChatRec(newChatId(), mutableStateListOf()))
                                    if (cur == ch.id) cur = chats.first().id
                                    saveChats(c, chats)
                                }, contentAlignment = Alignment.Center) { PIcon("delete", AiSub, 16.dp) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp)).clickable { settingsOpen = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("settings", AiInk, 20.dp); Text(tr("Settings", "Ayarlar"), color = AiInk, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                    // the + at the bottom left: a new chat
                    Box(Modifier.padding(start = 20.dp, top = 12.dp).size(50.dp).clip(CircleShape).background(AiAccent).clickable {
                        val n = ChatRec(newChatId(), mutableStateListOf()); chats.add(0, n); openChat(n.id)
                    }, contentAlignment = Alignment.Center) { PIcon("add", Color.Black, 24.dp) }
                }
            }
        },
    ) {
        var working by remember { mutableStateOf(false) }
        var look by remember { mutableStateOf(MascotStyle.load(c)) }
        if (mascotOpen) MascotSheet(look, { look = it; MascotStyle.save(c, it); AiBuddyService.live?.refresh() }) { mascotOpen = false }
        // the screen is AMOLED black, as in the Claude chat
        Column(Modifier.fillMaxSize().background(AiBg).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { scope.launch { drawer.open() } }, contentAlignment = Alignment.Center) { PIcon("menu", AiInk, 22.dp) }
                // the mascot: a tap opens its sheet (the look, the scene); it dances only while the model works
                Box(Modifier.padding(start = 2.dp).size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A))
                    .clickable { mascotOpen = true }, contentAlignment = Alignment.Center) {
                    // the scene only behind the mascot: the mascot keeps its size with or without a scene
                    MascotStyle.sceneRes(c, look.scene).takeIf { it != 0 }?.let { Image(painterResource(it), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    AiMascot(look, working, null, Modifier.size(32.dp, 29.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(if (models) tr("Models", "Modeller") else chat.title.ifEmpty { "Pulse AI" }, color = AiInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (working) tr("Working…", "Çalışıyor…") else tr("Idle", "Boşta"), color = AiSub, fontSize = 12.sp, maxLines = 1)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (models) AiModelsPage()
                else key(chat.id) { AiChat(chat, onSaved = { saveChats(c, chats) }, onBusy = { working = it; AiLive.set(c, it) }, toModels = { models = true }) }
            }
        }
    }
}

private val AiBg = Color.Black
private val AiInk = Color(0xFFFFFFFF)
private val AiSub = Color(0xFF9A9A9A)
private val AiPanel = Color.Black
/** Pulse AI's accent: pure white, with black on it (the + , the send button, the active bar style). */
private val AiAccent = Color.White

private fun newChatId() = System.currentTimeMillis().toString(36) + (0..999).random().toString(36)

/** A chat: its id, its title (its first question) and its messages. */
private class ChatRec(val id: String, val msgs: androidx.compose.runtime.snapshots.SnapshotStateList<Msg>) {
    var title by mutableStateOf(msgs.firstOrNull { it.user }?.text?.take(40).orEmpty())
}

@Composable
private fun AiWelcome(onDone: () -> Unit) {
    val c = LocalContext.current
    val rec = remember { Device.recommended(c, CHAT_MODELS) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PIcon("star", AiAccent, 48.dp)
        Text(tr("Welcome to Pulse AI", "Pulse AI'ya hoş geldin"), color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        PCard {
            Text(tr("Everything runs on this phone. Nothing you type or say leaves it.", "Her şey bu telefonda çalışır. Yazdığın ya da söylediğin hiçbir şey dışarı çıkmaz."), color = T.text, fontSize = 14.sp)
            Text(tr("The models are downloaded once into Download/PulseAI (0.4 to 4.7 GB), not into the app's data: they stay when Pulse is reinstalled, and models already on the phone are used where they are.",
                "Modeller bir kez Download/PulseAI klasörüne iner (0,4 - 4,7 GB), uygulama verisine değil: Pulse yeniden kurulunca da kalır, telefonda zaten olan modeller yerinde kullanılır."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        PCard {
            listOf(tr("Chat with a local model, with files", "Yerel modelle sohbet, dosyalarla"), tr("Timed lyrics for songs that have none", "Sözü olmayan şarkılara zamanlı söz"),
                tr("AI fix in the Pulse keyboard", "Pulse klavyede yapay zekâ düzeltmesi"), tr("A memory of you that every chat knows", "Her sohbetin bildiği bir hafıza")).forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) { PIcon("check", AiAccent, 18.dp); Text(it, color = T.text, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
            }
        }
        PCard {
            Text("RAM ${gb(Device.ramBytes(c))} · ${Device.cores()} ${tr("cores", "çekirdek")}", color = T.text, fontSize = 14.sp)
            rec?.let { Text(tr("Suggested to start: ", "Başlamak için önerilen: ") + it.name + " (" + gb(it.bytes) + ")", color = T.sub, fontSize = 13.sp) }
        }
        PButton(tr("Continue", "Devam"), Modifier.fillMaxWidth()) { onDone() }
    }
}

@Composable
private fun AiModelsPage() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (isActive) { delay(1000); tick++ } }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<AiModel>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val onPhone by produceState(emptyList<AiModel>(), tick / 30) { value = withContext(Dispatchers.IO) { runCatching { phoneModels() }.getOrDefault(emptyList()) } }
    val pick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { u: Uri? ->
        if (u != null) { runCatching { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; Store.setCustomUri(c, if (u.toString().endsWith(".bin")) "speech" else "chat", u); tick++ }
    }
    val rec = remember { Device.recommended(c, CHAT_MODELS) }
    val extras = remember(tick) { Store.extras(c) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("RAM ${gb(Device.ramBytes(c))} · Download/PulseAI", color = AiSub, fontSize = 13.sp)
            if (!Environment.isExternalStorageManager()) TextButton({ runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${c.packageName}"))) } }) {
                Text(tr("Allow file access: models already on the phone are found", "Dosya erişimine izin ver: telefondaki modeller bulunur"), color = AiAccent, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(query, { query = it }, tr("Search every GGUF on Hugging Face", "Hugging Face'teki tüm GGUF'larda ara"), Modifier.weight(1f), onDone = {
                    if (query.isNotBlank()) { searching = true; scope.launch { found = withContext(Dispatchers.IO) { runCatching { searchHf(query) }.getOrDefault(emptyList()) }; searching = false } }
                })
                Box(Modifier.size(44.dp).clickable { if (query.isNotBlank()) { searching = true; scope.launch { found = withContext(Dispatchers.IO) { runCatching { searchHf(query) }.getOrDefault(emptyList()) }; searching = false } } }, contentAlignment = Alignment.Center) { PIcon("search", AiInk, 22.dp) }
            }
            if (searching) PProgress(null, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        if (found.isNotEmpty()) {
            item { Section(tr("Search results", "Arama sonuçları")) }
            items(found, key = { "f" + it.id }) { m -> ModelRow(m, tick, false) { tick++ } }
        }
        if (onPhone.isNotEmpty()) {
            item { Section(tr("Already on the phone", "Telefonda zaten olanlar")) }
            items(onPhone, key = { "p" + it.id }) { m -> ModelRow(m, tick, false) { tick++ } }
        }
        item { Section(tr("Chat", "Sohbet")) }
        items(CHAT_MODELS + extras.filter { it.kind == "chat" && it.path == null }, key = { "c" + it.id }) { m -> ModelRow(m, tick, m == rec) { tick++ } }
        item { Section(tr("Speech to text (lyrics)", "Konuşmadan yazıya (şarkı sözleri)")) }
        items(SPEECH_MODELS, key = { "s" + it.id }) { m -> ModelRow(m, tick, m.id == "whisper-medium") { tick++ } }
        item {
            TextButton({ pick.launch(arrayOf("*/*")) }) { Text(tr("Use a model file from the phone (.gguf / .bin)", "Telefondaki bir model dosyasını kullan (.gguf / .bin)"), color = AiAccent, fontSize = 13.sp) }
            Text(tr("Each model keeps its own licence (on its row). Abliterated models answer without the usual refusals; you are responsible for how you use them.",
                "Her model kendi lisansını taşır (satırında). Abliterated modeller olağan retler olmadan yanıt verir; nasıl kullandığından sen sorumlusun."), color = AiSub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 14.dp))
        }
    }
}

@Composable
private fun Section(t: String) = Text(t, color = AiAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun ModelRow(m: AiModel, tick: Int, suggested: Boolean, changed: () -> Unit) {
    val c = LocalContext.current
    // the percentage moves while the model downloads (the download goes on in Pulse's downloader, so it is read again and again)
    var p by remember { mutableFloatStateOf(Store.progress(c, m)) }
    LaunchedEffect(tick, m.id) { while (true) { p = Store.progress(c, m); delay(400) } }
    val fit = remember(m.bytes) { if (m.bytes > 0) Device.fit(c, m) else 1 }
    val active = remember(tick) { Store.active(c, m.kind) == m.id }
    PCard(Modifier.fillMaxWidth().alpha(if (fit == 0) .55f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, color = AiInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (active) PIcon("check", AiAccent, 18.dp, Modifier.padding(start = 6.dp))
                }
                Text(listOfNotNull(gb(m.bytes), m.license.takeIf { it != "?" }, if (m.uncensored) tr("uncensored", "sansürsüz") else null,
                    when (fit) { 2 -> tr("runs well here", "burada iyi çalışır"); 1 -> tr("runs, slowly", "çalışır, yavaş"); else -> tr("too big for this phone", "bu telefon için büyük") },
                    if (suggested) tr("suggested", "önerilen") else null, m.note.ifEmpty { null }).joinToString(" · "), color = AiSub, fontSize = 12.sp)
            }
            when {
                p >= 1f -> Row {
                    if (!active) PButton(tr("Use", "Kullan"), compact = true) { Store.remember(c, m); Store.setActive(c, m.kind, m.id); changed() }
                    if (m.path == null) Box(Modifier.size(40.dp).clickable { Store.delete(c, m); changed() }, contentAlignment = Alignment.Center) { PIcon("delete", AiSub, 20.dp) }
                }
                p >= 0f -> Text("${(p * 100).toInt()}%", color = AiAccent, fontSize = 14.sp)
                else -> PButton(tr("Download", "İndir"), compact = true) { if (fit > 0) { Store.remember(c, m); Store.download(c, m); p = 0f; changed() } }
            }
        }
        if (p in 0f..0.999f) PProgress(p, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** One message; the chat is kept on the phone between starts (the last 60). */
private class Msg(val user: Boolean, text: String) { var text by mutableStateOf(text) }

/** Every chat kept on the phone (the last 40, each with its last 60 messages). The chat kept from the earlier single-chat version comes first. */
private fun chatsOf(c: Context): List<ChatRec> {
    val out = mutableListOf<ChatRec>()
    runCatching {
        val a = JSONArray(c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).getString("chats", "[]"))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); val m = o.getJSONArray("m")
            out += ChatRec(o.getString("id"), (0 until m.length()).map { k -> m.getJSONObject(k).let { Msg(it.getBoolean("u"), it.getString("t")) } }.toMutableStateList())
        }
    }
    if (out.isEmpty()) runCatching { loadChat(c) }.getOrDefault(emptyList()).takeIf { it.isNotEmpty() }?.let { old -> out += ChatRec(newChatId(), old.toMutableStateList()) }
    return out
}

private fun saveChats(c: Context, l: List<ChatRec>) {
    val a = JSONArray()
    l.take(40).forEach { ch ->
        val m = JSONArray(); ch.msgs.takeLast(60).forEach { m.put(JSONObject().put("u", it.user).put("t", it.text)) }
        a.put(JSONObject().put("id", ch.id).put("t", ch.title).put("m", m))
    }
    c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().putString("chats", a.toString()).apply()
}
private fun loadChat(c: Context): List<Msg> = runCatching {
    val a = JSONArray(c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).getString("last", "[]")); (0 until a.length()).map { Msg(a.getJSONObject(it).getBoolean("u"), a.getJSONObject(it).getString("t")) }
}.getOrDefault(emptyList())

/** The chat screen: messages, the box to write in, and a file to attach. No tabs, no speech: only the chat. */
@Composable
private fun AiChat(chat: ChatRec, onSaved: () -> Unit, onBusy: (Boolean) -> Unit, toModels: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val msgs = chat.msgs
    var input by remember { mutableStateOf("") }
    var attached by remember { mutableStateOf<Pair<String, String>?>(null) }
    var picture by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()
    val pickFile = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { u: Uri? ->
        if (u != null) { if ((c.contentResolver.getType(u) ?: "").startsWith("image/")) picture = pictureFrom(c, u) else attached = readAttachment(c, u) }
    }
    if (Store.known(c, "chat").none { Store.has(c, it) } && Store.customUri(c, "chat") == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon("download", AiSub, 40.dp)
            Text(tr("No chat model yet", "Henüz sohbet modeli yok"), color = AiInk, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
            PButton(tr("Pick one", "Bir model seç"), Modifier.padding(top = 12.dp)) { toModels() }
        }
        return
    }
    fun send() {
        val q = input.trim(); if (q.isEmpty() || busy) return
        val pic = picture; picture = null
        val full = attached?.let { (name, text) -> "$q\n\n[${tr("File", "Dosya")}: $name]\n$text" } ?: q
        if (chat.title.isEmpty()) chat.title = q.take(40)
        msgs += Msg(true, if (pic != null) "$q\n[${pic.first}]" else if (attached != null) "$q\n[${attached!!.first}]" else q); input = ""; attached = null
        val a = Msg(false, ""); msgs += a; busy = true; onBusy(true)
        job = scope.launch {
            try {
                if (!started) {
                    a.text = tr("Loading the model…", "Model yükleniyor…")
                    // a chat kept from an earlier start: its last turns go into the instructions, so the model knows where it was
                    val earlier = msgs.dropLast(2).takeLast(8).joinToString("\n") { (if (it.user) "User: " else "Assistant: ") + it.text.take(400) }
                    val sys = systemPrompt(c) + if (earlier.isNotBlank()) "\n\n" + tr("Earlier in this chat:", "Bu sohbette daha önce:") + "\n" + earlier else ""
                    started = withContext(Dispatchers.Default) { runCatching { AiEngine.start(c, sys) }.getOrDefault(false) }
                    a.text = ""
                }
                if (!started) { a.text = tr("The model could not be opened. Pick another one in Models.", "Model açılamadı. Modeller'den başka birini seç."); return@launch }
                if (pic != null && !AiEngine.vision) { a.text = tr("This model cannot see pictures. Pick a vision model in Models.", "Bu model resim göremiyor. Modeller'den görsel bir model seç."); return@launch }
                val answer = if (pic != null) AiEngine.askPicture(c, full, pic.second) else AiEngine.ask(c, full)
                answer.collect { t -> a.text += t; list.scrollToItem(0) }
            } catch (e: Exception) { if (a.text.isEmpty()) a.text = tr("Error: ", "Hata: ") + (e.message ?: e.javaClass.simpleName) }
            finally { busy = false; AiLive.lastAnswer = a.text; onBusy(false); onSaved() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (msgs.isEmpty()) Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            AiMascot(MascotStyle.load(c), busy, null, Modifier.size(120.dp, 108.dp))
            Text(tr("Ask anything", "Ne istersen sor"), color = AiInk, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 18.dp))
            Text(if (busy) tr("Working…", "Çalışıyor…") else tr("Idle", "Boşta"), color = AiSub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        else LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // reversed: the newest message sits at the bottom and stays in view while the answer grows
            items(msgs.reversed()) { m ->
                if (m.user) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    SelectionContainer {
                        Text(m.text, color = Color.Black, fontSize = 15.sp,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(AiAccent).padding(12.dp))
                    }
                } else Column(Modifier.fillMaxWidth()) {
                    if (m.text.isEmpty()) Text("…", color = AiSub, fontSize = 15.sp)
                    else {
                        SelectionContainer { AiMarkdown(cleanAnswer(m.text), AiInk) }
                        if (!(busy && m === msgs.last())) AiCopyRow(cleanAnswer(m.text))
                    }
                }
            }
        }
        picture?.let { (n, _) -> Row(Modifier.padding(horizontal = 16.dp).clickable { picture = null }, verticalAlignment = Alignment.CenterVertically) { PIcon("document", AiSub, 16.dp); Text(n, color = AiSub, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)) } }
        attached?.let { (n, _) -> Row(Modifier.padding(horizontal = 16.dp).clickable { attached = null }, verticalAlignment = Alignment.CenterVertically) { PIcon("document", AiSub, 16.dp); Text(n, color = AiSub, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp)) } }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(AiInk.copy(alpha = .08f)).clickable { pickFile.launch(arrayOf("text/*", "application/json", "application/xml", "*/*")) }, contentAlignment = Alignment.Center) { PIcon("add", AiInk, 22.dp) }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).clip(RoundedCornerShape(22.dp)).background(AiInk.copy(alpha = .06f)).padding(horizontal = 16.dp, vertical = 11.dp)) {
                androidx.compose.foundation.text.BasicTextField(input, { input = it }, Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(color = AiInk, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AiAccent), maxLines = 5,
                    decorationBox = { inner -> Box { if (input.isEmpty()) Text(tr("Message", "Mesaj"), color = AiSub, fontSize = 16.sp); inner() } })
            }
            if (busy) Box(Modifier.size(44.dp).clip(CircleShape).background(AiAccent).clickable { job?.cancel(); busy = false; onBusy(false) }, contentAlignment = Alignment.Center) { PIcon("stop", Color.Black, 18.dp) }
            else Box(Modifier.size(44.dp).clip(CircleShape).background(AiAccent).clickable { send() }, contentAlignment = Alignment.Center) { PIcon("forward", Color.Black, 20.dp) }
        }
    }
}

/** Settings of Pulse AI: the floating mascot, the bar over the screen (in Pulse AI's orange), and the mascot's own settings. */
/** A switch of Pulse AI: white when on (its track and thumb), grey when off. */
@Composable
private fun AiSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange, colors = androidx.compose.material3.SwitchDefaults.colors(
        checkedThumbColor = Color.Black, checkedTrackColor = Color.White, checkedBorderColor = Color.White,
        uncheckedThumbColor = Color(0xFF9A9A9A), uncheckedTrackColor = Color(0xFF2A2A2A), uncheckedBorderColor = Color(0xFF2A2A2A)))
}

@Composable
private fun AiSettingsDialog(onClose: () -> Unit) {
    val c = LocalContext.current
    var buddyOn by remember { mutableStateOf(AiBuddyService.enabled(c)) }
    var bar by remember { mutableStateOf(AiBuddyService.barStyle(c)) }
    var buddySettings by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    if (buddySettings) AiBuddySettingsDialog { buddySettings = false; AiBuddyService.live?.refresh() }
    if (sortOpen) PhotoSortDialog { sortOpen = false }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF161616)).padding(vertical = 20.dp)) {
            Text(tr("Settings", "Ayarlar"), color = AiInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Floating mascot", "Yüzen maskot"), color = AiInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                AiSwitch(buddyOn, { on ->
                    if (on && !com.umutk.pulse.Compat.canOverlay(c)) {
                        runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    } else { buddyOn = on; AiBuddyService.setEnabled(c, on) }
                })
            }
            Text(tr("Bar", "Çubuk"), color = AiSub, fontSize = 12.sp, modifier = Modifier.padding(start = 24.dp, top = 8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("island" to tr("Island", "Ada"), "pill" to tr("Pill", "Hap"), "line" to tr("Line", "Çizgi"), "curtain" to tr("Curtain", "Perde"), "off" to tr("Off", "Kapalı")).forEach { (k, l) ->
                    Text(l, color = if (bar == k) Color.Black else AiInk, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (bar == k) AiAccent else Color(0xFF2A2A2A)).clickable {
                            if (k in setOf("pill", "line", "curtain") && !com.umutk.pulse.Compat.canOverlay(c)) runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            else { bar = k; AiBuddyService.setBarStyle(c, k); AiBuddyService.update(c) }
                        }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Row(Modifier.fillMaxWidth().clickable { buddySettings = true }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("settings", AiInk, 20.dp); Text(tr("Mascot settings", "Maskot ayarları"), color = AiInk, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
            }
            Row(Modifier.fillMaxWidth().clickable { sortOpen = true }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("photos", AiInk, 20.dp); Text(tr("Sort the pictures", "Fotoğrafları sırala"), color = AiInk, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
            }
            TextButton(onClose, Modifier.align(Alignment.End).padding(end = 12.dp)) { Text(tr("Done", "Tamam"), color = AiAccent) }
        }
    }
}

/** A text file for the chat: its name and up to 12 000 characters of it. */
private fun readAttachment(c: Context, u: Uri): Pair<String, String>? = runCatching {
    val name = c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file"
    val bytes = c.contentResolver.openInputStream(u)!!.use { ins -> val b = ByteArray(200_000); var n = 0; while (n < b.size) { val k = ins.read(b, n, b.size - n); if (k < 0) break; n += k }; b.copyOf(n) }
    name to String(bytes, Charsets.UTF_8).replace("\u0000", "").take(12_000)
}.getOrNull()

@Composable
internal fun LyricsJobProgress(showStop: Boolean) {
    val c = LocalContext.current
    val job = com.umutk.pulse.extras.lyrics.LyricsBatchJob
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); delay(1000) } }
    val el = ((tick - job.startedAt) / 1000).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(job.now.ifEmpty { tr("Starting…", "Başlıyor…") }, color = T.text, fontSize = 14.sp, maxLines = 2)
        PProgress(job.frac, Modifier.fillMaxWidth())
        Text("%d%%  ·  %d:%02d".format((job.frac * 100).toInt(), el / 60, el % 60) + (if (job.total > 1) "  ·  ${job.done} / ${job.total}" else ""), color = T.sub, fontSize = 12.sp)
        val lines = job.live.lines().filter { it.startsWith("[") && it.length > 3 && it[1].isDigit() }.takeLast(6)
        if (lines.isEmpty()) Text(tr("The first lines appear after the first minute of the song.", "İlk satırlar şarkının ilk dakikasından sonra çıkar."), color = T.sub, fontSize = 12.sp)
        else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { lines.forEach { Text(it, color = T.text, fontSize = 12.sp, maxLines = 1) } }
        if (showStop) TextButton({ com.umutk.pulse.extras.lyrics.LyricsBatchJob.cancel(c) }) { Text(tr("Stop", "Durdur"), color = AiAccent) }
    }
}

/**
 * The lyrics' AI step inside Pulse: a window that shows what the AI is doing (how far, how long, the lines as they come). The work itself runs in the
 * background service, so Hide (or Back) only closes this window: it goes on, a notification shows it, and the lyrics appear where they were asked for when
 * they are ready. Stop ends it. [onDone] gets the LRC text when the song is finished (empty when the lyrics came from the search), null when this window closes first.
 */
@Composable
fun AiLyricsDialog(path: String, title: String, artist: String, onDone: (String?) -> Unit) {
    val c = LocalContext.current
    var err by remember { mutableStateOf<String?>(null) }
    val startTick = remember { com.umutk.pulse.extras.lyrics.LyricsBatchJob.doneTick }
    LaunchedEffect(path) {
        if (!Transcriber.ready(c)) { err = tr("No speech model yet: Pulse AI > Models (Whisper medium is good with songs).", "Henüz ses modeli yok: Pulse AI > Modeller (şarkılarda Whisper medium iyidir)."); return@LaunchedEffect }
        com.umutk.pulse.extras.lyrics.LyricsBatchJob.startOne(c, path, title, artist)
    }
    val tick = com.umutk.pulse.extras.lyrics.LyricsBatchJob.doneTick
    LaunchedEffect(tick) {
        if (tick > startTick && com.umutk.pulse.extras.lyrics.LyricsBatchJob.lastPath == path) onDone(com.umutk.pulse.extras.lyrics.LyricsBatchJob.lastLrc)
    }
    WinAlert(onDismissRequest = { onDone(null) },
        title = { Text(tr("Timed lyrics", "Zamanlı sözler")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val e = err
            if (e != null) Text(e) else {
                LyricsJobProgress(showStop = false)
                Text(tr("You can close this window: it goes on in the background and the lyrics appear when it is done.", "Bu pencereyi kapatabilirsin: arka planda sürer, bitince sözler gelir."), color = T.sub, fontSize = 12.sp)
            }
        } },
        confirmButton = { TextButton({ onDone(null) }) { Text(if (err != null) tr("Close", "Kapat") else tr("Hide", "Gizle")) } },
        dismissButton = if (err == null) ({ TextButton({ com.umutk.pulse.extras.lyrics.LyricsBatchJob.cancel(c); onDone(null) }) { Text(tr("Stop", "Durdur")) } }) else null)
}

