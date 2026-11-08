package com.umutk.pulse.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val Accent = Color(0xFF00E5FF)
internal val Panel = Color(0xFF0E1116)
internal val Line = Color(0x33FFFFFF)
internal val Sub = Color(0xFF9AA4B2)

/** A frosted card: a faint light from the top, a thin bright edge (the glass look of Pulse's Material). */
@Composable
internal fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(Color(0x1FFFFFFF), Color(0x0AFFFFFF))))
        .border(1.dp, Line, RoundedCornerShape(20.dp)).padding(16.dp), content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color.Black, surface = Panel, onPrimary = Color.Black)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App() }
            }
        }
    }
}

@Composable
private fun App() {
    val c = LocalContext.current
    val sp = remember { c.getSharedPreferences("app", Context.MODE_PRIVATE) }
    var welcomed by remember { mutableStateOf(sp.getBoolean("welcomed", false)) }
    if (!welcomed) { Welcome { sp.edit().putBoolean("welcomed", true).apply(); welcomed = true }; return }
    var tab by remember { mutableStateOf(if (CHAT_MODELS.any { Store.has(c, it) }) "chat" else "models") }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) { "chat" -> ChatScreen { tab = "models" }; "models" -> ModelsScreen(); else -> SpeechScreen() }
        }
        NavigationBar(containerColor = Panel) {
            listOf("chat" to tr("Chat", "Sohbet"), "models" to tr("Models", "Modeller"), "speech" to tr("Speech", "Ses")).forEach { (k, l) ->
                NavigationBarItem(tab == k, { tab = k }, icon = { Text(when (k) { "chat" -> "💬"; "models" -> "⬇"; else -> "🎙" }, fontSize = 18.sp) }, label = { Text(l) })
            }
        }
    }
}

/** The first start: what this app is and where its models go. */
@Composable
private fun Welcome(onDone: () -> Unit) {
    val c = LocalContext.current
    val rec = remember { Device.recommended(c, CHAT_MODELS) }
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("Welcome to Pulse AI", "Pulse AI'ya hoş geldin"), fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
        Glass {
            Text(tr("Everything runs on this phone. Nothing you type or say leaves it.", "Her şey bu telefonda çalışır. Yazdığın ya da söylediğin hiçbir şey dışarı çıkmaz."), color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(tr("The models are downloaded once into Download/PulseAI (0.4 to 4.7 GB each): they stay there, so a reinstall does not download them again, and other apps can use them too.",
                "Modeller bir kez Download/PulseAI klasörüne iner (her biri 0,4 - 4,7 GB): orada kalır, yeniden kurulumda tekrar inmez, başka uygulamalar da kullanabilir."), color = Sub, fontSize = 13.sp)
        }
        Glass {
            Text(tr("What it does", "Neler yapar"), color = Accent, fontWeight = FontWeight.SemiBold)
            listOf(tr("Chat with a local model, with files", "Yerel bir modelle sohbet, dosyalarla"),
                tr("Timed lyrics for songs that have none (Pulse music player)", "Sözü olmayan şarkılara zamanlı sözler (Pulse müzik çalar)"),
                tr("AI fix in the Pulse keyboard: spelling, punctuation, capitals", "Pulse klavyede yapay zekâ düzeltmesi: yazım, noktalama, büyük harf")).forEach { Text("• $it", color = Color.White, fontSize = 14.sp) }
        }
        Glass {
            Text(tr("This phone", "Bu telefon"), color = Accent, fontWeight = FontWeight.SemiBold)
            Text("RAM ${gb(Device.ramBytes(c))} · ${Device.cores()} ${tr("cores", "çekirdek")}", color = Color.White)
            rec?.let { Text(tr("Suggested to start: ", "Başlamak için önerilen: ") + it.name + " (" + gb(it.bytes) + ")", color = Sub, fontSize = 13.sp) }
        }
        Button({ onDone() }, Modifier.fillMaxWidth()) { Text(tr("Continue", "Devam")) }
    }
}

@Composable
private fun ModelsScreen() {
    val c = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) { runCatching { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; Store.setCustomUri(c, if (u.toString().endsWith(".bin")) "speech" else "chat", u); tick++ }
    }
    val rec = remember { Device.recommended(c, CHAT_MODELS) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text(tr("Models", "Modeller"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("RAM ${gb(Device.ramBytes(c))} · Download/PulseAI", color = Sub, fontSize = 13.sp)
            if (!Environment.isExternalStorageManager()) TextButton({ c.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${c.packageName}"))) }) {
                Text(tr("Allow file access (models already in Download/PulseAI are found)", "Dosya erişimine izin ver (Download/PulseAI'daki modeller bulunur)"), fontSize = 12.sp)
            }
            OutlinedButton({ pick.launch(arrayOf("*/*")) }) { Text(tr("Use a model file from the phone (.gguf / .bin)", "Telefondaki bir model dosyasını kullan (.gguf / .bin)")) }
        }
        item { Text(tr("Chat", "Sohbet"), color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        items(CHAT_MODELS, key = { it.id }) { m -> ModelRow(m, tick, m == rec) { tick++ } }
        item { Text(tr("Speech to text (lyrics)", "Konuşmadan yazıya (şarkı sözleri)"), color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        items(SPEECH_MODELS, key = { it.id }) { m -> ModelRow(m, tick, m.id == "whisper-medium") { tick++ } }
        item {
            Text(tr("Licences: each model keeps its own (shown on its row). \"Abliterated\" models answer without the usual refusals; you are responsible for how you use them.",
                "Lisanslar: her model kendi lisansını taşır (satırında yazar). \"Abliterated\" modeller olağan retler olmadan yanıt verir; nasıl kullandığından sen sorumlusun."), color = Sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun ModelRow(m: AiModel, tick: Int, suggested: Boolean, changed: () -> Unit) {
    val c = LocalContext.current
    val p = remember(tick) { Store.progress(c, m) }
    val fit = remember { Device.fit(c, m) }
    val active = remember(tick) { Store.active(c, m.kind) == m.id }
    Glass(Modifier.alpha(if (fit == 0) .55f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.name + if (active) "  ✓" else "", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(gb(m.bytes), m.license, if (m.uncensored) tr("uncensored", "sansürsüz") else null,
                    when (fit) { 2 -> tr("runs well here", "burada iyi çalışır"); 1 -> tr("runs, slowly", "çalışır, yavaş"); else -> tr("too big for this phone", "bu telefon için büyük") },
                    if (suggested) tr("suggested", "önerilen") else null, m.note.ifEmpty { null }).joinToString(" · "), color = Sub, fontSize = 12.sp)
            }
            when {
                p >= 1f -> Row {
                    if (!active) TextButton({ Store.setActive(c, m.kind, m.id); changed() }) { Text(tr("Use", "Kullan")) }
                    TextButton({ Store.delete(c, m); changed() }) { Text(tr("Delete", "Sil"), color = Color(0xFFFF6B6B)) }
                }
                p >= 0f -> Text("${(p * 100).toInt()}%", color = Accent)
                else -> TextButton({ Store.download(c, m); changed() }, enabled = fit > 0) { Text(tr("Download", "İndir")) }
            }
        }
        if (p in 0f..0.999f) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(top = 8.dp), color = Accent)
    }
}

private class Msg(val user: Boolean, text: String) { var text by mutableStateOf(text) }

@Composable
private fun ChatScreen(toModels: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var attached by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val list = rememberLazyListState()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) attached = readAttachment(c, u)
    }
    if (CHAT_MODELS.none { Store.has(c, it) } && Store.customUri(c, "chat") == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("No chat model yet", "Henüz sohbet modeli yok"), color = Color.White, fontSize = 20.sp)
            Button(toModels, Modifier.padding(top = 12.dp)) { Text(tr("Pick one", "Bir model seç")) }
        }
        return
    }
    fun send() {
        val q = input.trim(); if (q.isEmpty() || busy) return
        val full = attached?.let { (name, text) -> "$q\n\n[${tr("File", "Dosya")}: $name]\n$text" } ?: q
        msgs += Msg(true, if (attached != null) "$q\n📎 ${attached!!.first}" else q); input = ""; attached = null
        val a = Msg(false, ""); msgs += a; busy = true
        job = scope.launch {
            try {
                if (!started) { a.text = tr("Loading the model…", "Model yükleniyor…"); started = Engine.start(c, systemPrompt(c)); a.text = "" }
                if (!started) { a.text = tr("The model could not be opened.", "Model açılamadı."); return@launch }
                Engine.ask(c, full).collect { t -> a.text += t; list.scrollToItem(msgs.size - 1) }
            } catch (e: Exception) { if (a.text.isEmpty()) a.text = tr("Error: ", "Hata: ") + (e.message ?: e.javaClass.simpleName) }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pulse AI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            TextButton({ job?.cancel(); msgs.clear(); started = false }) { Text(tr("New chat", "Yeni sohbet")) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.user) Alignment.CenterEnd else Alignment.CenterStart) {
                    SelectionContainer {
                        Text(m.text.ifEmpty { "…" }, color = if (m.user) Color.Black else Color.White, fontSize = 15.sp,
                            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp)).background(if (m.user) Accent else Color(0x22FFFFFF)).padding(12.dp))
                    }
                }
            }
        }
        attached?.let { (n, _) -> Text("📎 $n", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp).clickable { attached = null }) }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ pickFile.launch(arrayOf("text/*", "application/json", "application/xml", "application/x-subrip", "*/*")) }) { Text("📎", fontSize = 20.sp) }
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text(tr("Message", "Mesaj")) }, maxLines = 5)
            if (busy) TextButton({ job?.cancel(); busy = false }) { Text("■", fontSize = 20.sp, color = Accent) }
            else TextButton({ send() }) { Text("➤", fontSize = 22.sp, color = Accent) }
        }
    }
}

/** A text file for the chat: its name and up to 12 000 characters of it. */
private fun readAttachment(c: Context, u: Uri): Pair<String, String>? = runCatching {
    val name = c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "file"
    val text = c.contentResolver.openInputStream(u)!!.use { it.readBytes().copyOf(200_000) }.toString(Charsets.UTF_8).replace("\u0000", "").take(12_000)
    name to text
}.getOrNull()
