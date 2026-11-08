package com.umutk.pulse.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ai.stt.AudioDecode
import com.umutk.pulse.ai.stt.Whisper
import com.umutk.pulse.ai.stt.toLrc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Speech to text with the chosen whisper model: a song becomes timed lyrics (LRC). */
object Transcriber {
    /** The speech model to use: the one picked, else the biggest one downloaded. Null when none is there. */
    fun modelPath(c: Context, hold: (ParcelFileDescriptor) -> Unit): String? {
        val id = Store.active(c, "speech")
        if (id == "custom") Store.customUri(c, "speech")?.let { return Store.openUri(c, it, hold) }
        val m = SPEECH_MODELS.firstOrNull { it.id == id && Store.has(c, it) } ?: SPEECH_MODELS.filter { Store.has(c, it) }.maxByOrNull { it.bytes } ?: return null
        return Store.openPath(c, m, hold)
    }

    /** [stage] gets the step and 0..1 while it works. Returns the LRC text, or null without a model. */
    suspend fun lrc(c: Context, audio: Uri, lang: String, title: String?, artist: String?, stage: (String, Float) -> Unit): String? = withContext(Dispatchers.Default) {
        var pfd: ParcelFileDescriptor? = null
        try {
            val path = modelPath(c) { pfd = it } ?: return@withContext null
            stage(tr("Reading the song", "Şarkı okunuyor"), 0f)
            val pcm = AudioDecode.pcm16k(c, audio) { stage(tr("Reading the song", "Şarkı okunuyor"), it) }
            stage(tr("Loading the speech model", "Ses modeli yükleniyor"), 0f)
            Whisper.open(path).use { w ->
                // the words of the title help the model with names it would not know
                val prompt = listOfNotNull(title, artist).joinToString(" - ").ifEmpty { null }
                stage(tr("Listening", "Dinleniyor"), 0f)
                val segs = w.transcribe(pcm, lang, prompt)
                toLrc(segs, title, artist)
            }
        } finally { runCatching { pfd?.close() } }
    }
}

@Composable
internal fun SpeechScreen() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var frac by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf("auto") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        busy = true; result = ""
        scope.launch {
            val poll = launch { while (isActive) { if (stage == tr("Listening", "Dinleniyor")) frac = Whisper.progress() / 100f; delay(500) } }
            val r = runCatching { Transcriber.lrc(c, u, lang, null, null) { s, f -> stage = s; frac = f } }
            poll.cancel(); busy = false
            result = r.getOrNull() ?: r.exceptionOrNull()?.message ?: tr("Download a speech model first (Models).", "Önce bir ses modeli indir (Modeller).")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Speech to text", "Konuşmadan yazıya"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Glass {
            Text(tr("A song or a recording becomes timed lyrics (LRC). In Pulse's music player the AI button of the lyrics does this for the song that is playing.",
                "Bir şarkı ya da kayıt zamanlı sözlere (LRC) dönüşür. Pulse müzik çalarda sözlerin AI tuşu bunu çalan şarkı için yapar."), color = Sub, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("auto" to tr("Auto", "Otomatik"), "tr" to "Türkçe", "en" to "English").forEach { (k, l) -> FilterChip(lang == k, { lang = k }, label = { Text(l) }) }
            }
            Button({ pick.launch(arrayOf("audio/*", "video/*")) }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text(tr("Pick a song", "Şarkı seç")) }
        }
        if (busy) Glass {
            Text(stage, color = Color.White)
            LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().padding(top = 8.dp), color = Accent)
            TextButton({ Whisper.abort() }) { Text(tr("Stop", "Durdur")) }
        }
        if (result.isNotEmpty()) Glass {
            SelectionContainer { Text(result, color = Color.White, fontSize = 13.sp) }
            TextButton({ c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result), null)) }) { Text(tr("Share", "Paylaş")) }
        }
    }
}

/**
 * Pulse's lyrics ask here: action com.umutk.pulse.ai.TRANSCRIBE, the song as data (content URI with read permission), extras "title", "artist", "lang".
 * The answer is RESULT_OK with the LRC text in "lrc". The screen stays on while it works.
 */
class TranscribeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val audio = intent.data ?: run { setResult(Activity.RESULT_CANCELED); finish(); return }
        val title = intent.getStringExtra("title"); val artist = intent.getStringExtra("artist"); val lang = intent.getStringExtra("lang") ?: "auto"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color.Black, surface = Panel)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    var stage by remember { mutableStateOf("") }
                    var frac by remember { mutableFloatStateOf(0f) }
                    var err by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        val poll = launch { while (isActive) { if (stage == tr("Listening", "Dinleniyor")) frac = Whisper.progress() / 100f; delay(500) } }
                        val r = runCatching { Transcriber.lrc(this@TranscribeActivity, audio, lang, title, artist) { s, f -> stage = s; frac = f } }
                        poll.cancel()
                        val lrc = r.getOrNull()
                        if (lrc != null) { setResult(Activity.RESULT_OK, Intent().putExtra("lrc", lrc)); finish() }
                        else err = r.exceptionOrNull()?.message ?: tr("No speech model yet: open Pulse AI > Models and download one (Whisper medium is good with songs).",
                            "Henüz ses modeli yok: Pulse AI > Modeller'den birini indir (şarkılarda Whisper medium iyidir).")
                    }
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(tr("Timed lyrics", "Zamanlı sözler"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(listOfNotNull(title, artist).joinToString(" · "), color = Sub)
                        val e = err
                        if (e != null) { Text(e, color = Color.White); Button({ setResult(Activity.RESULT_CANCELED); finish() }) { Text(tr("Close", "Kapat")) } }
                        else {
                            Text(stage, color = Color.White)
                            LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth(), color = Accent)
                            Text(tr("This runs on the phone and can take a few minutes.", "Bu telefonda çalışır, birkaç dakika sürebilir."), color = Sub, fontSize = 12.sp)
                            TextButton({ Whisper.abort(); setResult(Activity.RESULT_CANCELED); finish() }) { Text(tr("Cancel", "İptal")) }
                        }
                    }
                }
            }
        }
    }
}
