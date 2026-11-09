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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
    var tab by rememberSaveable { mutableStateOf(if (Store.known(c, "chat").any { Store.has(c, it) } || Store.customUri(c, "chat") != null) "chat" else "models") }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("Pulse AI", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("chat" to tr("Chat", "Sohbet"), "models" to tr("Models", "Modeller"), "speech" to tr("Lyrics and speech", "Söz ve ses"), "memory" to tr("Memory", "Hafıza")).forEach { (k, l) -> PChip(tab == k, l) { tab = k } }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
            when (tab) { "chat" -> AiChat { tab = "models" }; "models" -> AiModelsPage(); "speech" -> AiSpeechPage(); else -> AiMemoryPage() }
        }
    }
}

@Composable
private fun AiWelcome(onDone: () -> Unit) {
    val c = LocalContext.current
    val rec = remember { Device.recommended(c, CHAT_MODELS) }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PIcon("star", Look.accent, 48.dp)
        Text(tr("Welcome to Pulse AI", "Pulse AI'ya hoş geldin"), color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        PCard {
            Text(tr("Everything runs on this phone. Nothing you type or say leaves it.", "Her şey bu telefonda çalışır. Yazdığın ya da söylediğin hiçbir şey dışarı çıkmaz."), color = T.text, fontSize = 14.sp)
            Text(tr("The models are downloaded once into Download/PulseAI (0.4 to 4.7 GB), not into the app's data: they stay when Pulse is reinstalled, and models already on the phone are used where they are.",
                "Modeller bir kez Download/PulseAI klasörüne iner (0,4 - 4,7 GB), uygulama verisine değil: Pulse yeniden kurulunca da kalır, telefonda zaten olan modeller yerinde kullanılır."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        PCard {
            listOf(tr("Chat with a local model, with files", "Yerel modelle sohbet, dosyalarla"), tr("Timed lyrics for songs that have none", "Sözü olmayan şarkılara zamanlı söz"),
                tr("AI fix in the Pulse keyboard", "Pulse klavyede yapay zekâ düzeltmesi"), tr("A memory of you that every chat knows", "Her sohbetin bildiği bir hafıza")).forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) { PIcon("check", Look.accent, 18.dp); Text(it, color = T.text, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) }
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
            Text("RAM ${gb(Device.ramBytes(c))} · Download/PulseAI", color = T.sub, fontSize = 13.sp)
            if (!Environment.isExternalStorageManager()) TextButton({ runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${c.packageName}"))) } }) {
                Text(tr("Allow file access: models already on the phone are found", "Dosya erişimine izin ver: telefondaki modeller bulunur"), color = Look.accent, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PField(query, { query = it }, tr("Search every GGUF on Hugging Face", "Hugging Face'teki tüm GGUF'larda ara"), Modifier.weight(1f), onDone = {
                    if (query.isNotBlank()) { searching = true; scope.launch { found = withContext(Dispatchers.IO) { runCatching { searchHf(query) }.getOrDefault(emptyList()) }; searching = false } }
                })
                Box(Modifier.size(44.dp).clickable { if (query.isNotBlank()) { searching = true; scope.launch { found = withContext(Dispatchers.IO) { runCatching { searchHf(query) }.getOrDefault(emptyList()) }; searching = false } } }, contentAlignment = Alignment.Center) { PIcon("search", T.text, 22.dp) }
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
            TextButton({ pick.launch(arrayOf("*/*")) }) { Text(tr("Use a model file from the phone (.gguf / .bin)", "Telefondaki bir model dosyasını kullan (.gguf / .bin)"), color = Look.accent, fontSize = 13.sp) }
            Text(tr("Each model keeps its own licence (on its row). Abliterated models answer without the usual refusals; you are responsible for how you use them.",
                "Her model kendi lisansını taşır (satırında). Abliterated modeller olağan retler olmadan yanıt verir; nasıl kullandığından sen sorumlusun."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 14.dp))
        }
    }
}

@Composable
private fun Section(t: String) = Text(t, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))

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
                    Text(m.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (active) PIcon("check", Look.accent, 18.dp, Modifier.padding(start = 6.dp))
                }
                Text(listOfNotNull(gb(m.bytes), m.license.takeIf { it != "?" }, if (m.uncensored) tr("uncensored", "sansürsüz") else null,
                    when (fit) { 2 -> tr("runs well here", "burada iyi çalışır"); 1 -> tr("runs, slowly", "çalışır, yavaş"); else -> tr("too big for this phone", "bu telefon için büyük") },
                    if (suggested) tr("suggested", "önerilen") else null, m.note.ifEmpty { null }).joinToString(" · "), color = T.sub, fontSize = 12.sp)
            }
            when {
                p >= 1f -> Row {
                    if (!active) PButton(tr("Use", "Kullan"), compact = true) { Store.remember(c, m); Store.setActive(c, m.kind, m.id); changed() }
                    if (m.path == null) Box(Modifier.size(40.dp).clickable { Store.delete(c, m); changed() }, contentAlignment = Alignment.Center) { PIcon("delete", T.sub, 20.dp) }
                }
                p >= 0f -> Text("${(p * 100).toInt()}%", color = Look.accent, fontSize = 14.sp)
                else -> PButton(tr("Download", "İndir"), compact = true) { if (fit > 0) { Store.remember(c, m); Store.download(c, m); p = 0f; changed() } }
            }
        }
        if (p in 0f..0.999f) PProgress(p, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/** One message; the chat is kept on the phone between starts (the last 60). */
private class Msg(val user: Boolean, text: String) { var text by mutableStateOf(text) }

private fun saveChat(c: Context, l: List<Msg>) {
    val a = JSONArray(); l.takeLast(60).forEach { a.put(JSONObject().put("u", it.user).put("t", it.text)) }
    c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().putString("last", a.toString()).apply()
}
private fun loadChat(c: Context): List<Msg> = runCatching {
    val a = JSONArray(c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).getString("last", "[]")); (0 until a.length()).map { Msg(a.getJSONObject(it).getBoolean("u"), a.getJSONObject(it).getString("t")) }
}.getOrDefault(emptyList())

@Composable
private fun AiChat(toModels: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<Msg>().apply { addAll(loadChat(c)) } }
    var input by remember { mutableStateOf("") }
    var attached by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()
    val pickFile = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { u: Uri? -> if (u != null) attached = readAttachment(c, u) }
    if (Store.known(c, "chat").none { Store.has(c, it) } && Store.customUri(c, "chat") == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PIcon("download", T.sub, 40.dp)
            Text(tr("No chat model yet", "Henüz sohbet modeli yok"), color = T.text, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp))
            PButton(tr("Pick one", "Bir model seç"), Modifier.padding(top = 12.dp)) { toModels() }
        }
        return
    }
    fun send() {
        val q = input.trim(); if (q.isEmpty() || busy) return
        val full = attached?.let { (name, text) -> "$q\n\n[${tr("File", "Dosya")}: $name]\n$text" } ?: q
        msgs += Msg(true, if (attached != null) "$q\n[${attached!!.first}]" else q); input = ""; attached = null
        val a = Msg(false, ""); msgs += a; busy = true
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
                AiEngine.ask(c, full).collect { t -> a.text += t; list.scrollToItem(msgs.size - 1) }
            } catch (e: Exception) { if (a.text.isEmpty()) a.text = tr("Error: ", "Hata: ") + (e.message ?: e.javaClass.simpleName) }
            finally { busy = false; saveChat(c, msgs) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton({ job?.cancel(); msgs.clear(); started = false; saveChat(c, msgs) }) { Text(tr("New chat", "Yeni sohbet"), color = Look.accent) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.user) Alignment.CenterEnd else Alignment.CenterStart) {
                    SelectionContainer {
                        Text(m.text.ifEmpty { "…" }, color = if (m.user) onAccent(Look.accent) else T.text, fontSize = 15.sp,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(16.dp)).background(if (m.user) Look.accent else T.text.copy(alpha = .08f)).padding(12.dp))
                    }
                }
            }
        }
        attached?.let { (n, _) -> Row(Modifier.padding(horizontal = 16.dp).clickable { attached = null }, verticalAlignment = Alignment.CenterVertically) { PIcon("document", T.sub, 16.dp); Text(n, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp)) } }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { pickFile.launch(arrayOf("text/*", "application/json", "application/xml", "*/*")) }, contentAlignment = Alignment.Center) { PIcon("document", T.text, 22.dp) }
            PField(input, { input = it }, tr("Message", "Mesaj"), Modifier.weight(1f), onDone = { send() })
            if (busy) Box(Modifier.size(44.dp).clickable { job?.cancel(); busy = false }, contentAlignment = Alignment.Center) { PIcon("stop", Look.accent, 22.dp) }
            else Box(Modifier.size(44.dp).clickable { send() }, contentAlignment = Alignment.Center) { PIcon("forward", Look.accent, 22.dp) }
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
private fun AiSpeechPage() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var frac by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf("auto") }
    val pick = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        busy = true; result = ""
        scope.launch {
            val poll = launch { while (isActive) { if (stage == Transcriber.LISTENING) frac = Transcriber.progress(); delay(500) } }
            val r = runCatching { Transcriber.lrc(c, u, lang, null, null) { s, f -> stage = s; frac = f } }
            poll.cancel(); busy = false
            result = r.getOrNull() ?: r.exceptionOrNull()?.message ?: tr("Download a speech model first (Models).", "Önce bir ses modeli indir (Modeller).")
        }
    }
    // a song from the list: the background service takes it (the lyrics search first, then the AI); it goes on when this page is left, and a second song waits its turn
    fun makeFor(t: com.umutk.pulse.extras.music.Track) {
        if (!Transcriber.ready(c)) { result = tr("Download a speech model first (Models).", "Önce bir ses modeli indir (Modeller)."); return }
        result = ""
        com.umutk.pulse.extras.lyrics.LyricsBatchJob.startOne(c, t.path, t.title ?: java.io.File(t.path).nameWithoutExtension, t.artist.orEmpty())
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PCard {
            Text(tr("A song or a recording becomes timed lyrics (LRC). In the music player the AI button of the lyrics does this for the song that is playing.",
                "Bir şarkı ya da kayıt zamanlı sözlere (LRC) dönüşür. Müzik çalarda sözlerin AI tuşu bunu çalan şarkı için yapar."), color = T.sub, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("auto" to tr("Auto", "Otomatik"), "tr" to "Türkçe", "en" to "English").forEach { (k, l) -> PChip(lang == k, l) { lang = k } }
            }
            PButton(tr("Pick a song", "Şarkı seç"), Modifier.padding(top = 8.dp)) { if (!busy) pick.launch(arrayOf("audio/*", "video/*")) }
        }
        // what the background service is doing for the songs of the list (and the batch): how far, how long, the lines found so far
        if (com.umutk.pulse.extras.lyrics.LyricsBatchJob.busy) PCard { LyricsJobProgress(showStop = true) }
        if (busy) PCard {
            Text(stage, color = T.text, fontSize = 14.sp)
            PProgress(frac, Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton({ Transcriber.stop() }) { Text(tr("Stop", "Durdur"), color = Look.accent) }
        }
        if (result.isNotEmpty()) PCard {
            SelectionContainer { Text(result, color = T.text, fontSize = 13.sp) }
            TextButton({ c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text(tr("Share", "Paylaş"), color = Look.accent) }
        }
        SongsWithoutLyrics(false, lang) { t -> makeFor(t) }
    }
}

/** The songs that have no lyrics yet, at the bottom of the speech page: a search box, and a tap makes the lyrics of that song. */
@Composable
private fun SongsWithoutLyrics(busy: Boolean, lang: String, onMake: (com.umutk.pulse.extras.music.Track) -> Unit) {
    val c = LocalContext.current
    val tracks by com.umutk.pulse.extras.music.MusicLibrary.tracks.collectAsState()
    var rows by remember { mutableStateOf<List<com.umutk.pulse.extras.music.Track>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(tracks, com.umutk.pulse.extras.lyrics.LyricsBatchJob.doneTick) {
        rows = withContext(Dispatchers.IO) { tracks.filter { !it.path.contains("://") && !com.umutk.pulse.extras.lyrics.LyricsStatus.has(c, it.path, it.artist.orEmpty(), it.title.orEmpty()) } }
    }
    val q = query.trim()
    val shown = rows?.filter { q.isEmpty() || (it.title ?: "").contains(q, true) || it.artist.orEmpty().contains(q, true) || it.path.contains(q, true) }
    PCard {
        Text(tr("Songs without lyrics", "Sözü olmayan şarkılar"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("${rows?.size ?: 0} songs", "${rows?.size ?: 0} şarkı"), color = T.sub, fontSize = 12.sp)
        com.umutk.pulse.PField(query, { query = it }, tr("Search", "Ara"), Modifier.padding(top = 8.dp))
        if (shown == null) PLoader(Modifier.padding(top = 10.dp).size(24.dp))
        else shown.take(300).forEach { t ->
            Column(Modifier.fillMaxWidth().clickable(enabled = !busy) { onMake(t) }.padding(vertical = 8.dp)) {
                Text(t.title ?: java.io.File(t.path).nameWithoutExtension, color = T.text, fontSize = 14.sp, maxLines = 1)
                Text(t.artist.orEmpty(), color = T.sub, fontSize = 12.sp, maxLines = 1)
            }
        }
        if (shown != null && shown.size > 300) Text(tr("Search to find more", "Daha fazlası için ara"), color = T.sub, fontSize = 12.sp)
    }
}

@Composable
private fun AiMemoryPage() {
    val c = LocalContext.current
    var facts by remember { mutableStateOf(AiMemory.facts(c)) }
    var on by remember { mutableStateOf(AiMemory.on(c)) }
    var add by remember { mutableStateOf("") }
    var sys by remember { mutableStateOf(c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).getString("system", "") ?: "") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Every new chat knows these", "Her yeni sohbet bunları bilir"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                PSwitch(on, { on = it; AiMemory.setOn(c, it) })
            }
            facts.forEachIndexed { i, f ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clickable { facts = facts.filterIndexed { j, _ -> j != i }; AiMemory.set(c, facts) }, contentAlignment = Alignment.Center) { PIcon("delete", T.sub, 18.dp) }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PField(add, { add = it }, tr("My name is…, I work as…, answer briefly…", "Adım…, işim…, kısa yanıt ver…"), Modifier.weight(1f), onDone = { if (add.isNotBlank()) { facts = facts + add.trim(); AiMemory.set(c, facts); add = "" } })
                Box(Modifier.size(44.dp).clickable { if (add.isNotBlank()) { facts = facts + add.trim(); AiMemory.set(c, facts); add = "" } }, contentAlignment = Alignment.Center) { PIcon("add", Look.accent, 22.dp) }
            }
        }
        PCard {
            Text(tr("Instructions of every chat (empty: the default)", "Her sohbetin talimatı (boş: varsayılan)"), color = T.text, fontSize = 15.sp)
            PField(sys, { sys = it; c.getSharedPreferences("ai_chat", Context.MODE_PRIVATE).edit().putString("system", it.ifBlank { null }).apply() }, tr("You are…", "Sen…"), Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

/**
 * What the lyrics service is doing now, to be seen: the song, how far it is (the whole song, not only the minute being listened to), how long it
 * has been going, and the lines found so far (the newest at the bottom; the first ones come after the first minute of the song).
 */
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
        Text("${(job.frac * 100).toInt()}%  ·  %d:%02d".format(el / 60, el % 60) + (if (job.total > 1) "  ·  ${job.done} / ${job.total}" else ""), color = T.sub, fontSize = 12.sp)
        val lines = job.live.lines().filter { it.startsWith("[") && it.length > 3 && it[1].isDigit() }.takeLast(6)
        if (lines.isEmpty()) Text(tr("The first lines appear after the first minute of the song.", "İlk satırlar şarkının ilk dakikasından sonra çıkar."), color = T.sub, fontSize = 12.sp)
        else Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { lines.forEach { Text(it, color = T.text, fontSize = 12.sp, maxLines = 1) } }
        if (showStop) TextButton({ com.umutk.pulse.extras.lyrics.LyricsBatchJob.cancel(c) }) { Text(tr("Stop", "Durdur"), color = Look.accent) }
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
