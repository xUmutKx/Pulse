package com.umutk.pulse.extras.mediatools

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Cut a part out of a video or an audio file, or keep only its sound. The streams are copied as they are (no decoding, no encoding), so it is quick and
 * loses no quality; the cut starts at the key frame before the start time, so a video may begin a moment early.
 */
@Composable
internal fun CutTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Uri?>(null) }
    var from by remember { mutableStateOf("0") }
    var to by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { file = u; note = "" } }

    fun run(soundOnly: Boolean) {
        val u = file ?: return
        if (busy) return
        val startUs = ((from.replace(',', '.').toDoubleOrNull() ?: 0.0) * 1_000_000).toLong().coerceAtLeast(0L)
        val endUs = to.replace(',', '.').toDoubleOrNull()?.let { (it * 1_000_000).toLong() }
        if (endUs != null && endUs <= startUs) { note = tr("The end must come after the start", "Bitiş başlangıçtan sonra olmalı"); return }
        busy = true; note = tr("Working…", "Çalışıyor…")
        scope.launch {
            note = withContext(Dispatchers.IO) {
                val name = baseOf(nameOf(ctx, u)) + if (soundOnly) "-sound.m4a" else "-cut.mp4"
                val out = File(ctx.cacheDir, "mediatools/${System.nanoTime()}_$name").apply { parentFile?.mkdirs() }
                val err = cutStreams(ctx, u, startUs, endUs, soundOnly, out)
                if (err != null) tr("Could not cut: $err", "Kesilemedi: $err")
                else tr("Saved as ", "Kaydedildi: ") + saveFile(ctx, name, if (soundOnly) "audio/mp4" else "video/mp4", out)
            }
            busy = false
        }
    }

    PCard {
        Text(tr("Cut or extract", "Kes veya ayır"), color = T.text, fontSize = 16.sp)
        PButton(tr("Choose a video or audio file", "Video veya ses dosyası seç"), compact = true) { pick.launch(arrayOf("video/*", "audio/*")) }
        file?.let { Text(nameOf(ctx, it) + "  (" + fmt(sizeOf(ctx, it)) + ")", color = T.sub, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PField(from, { from = it }, tr("From (seconds)", "Başlangıç (saniye)"), Modifier.weight(1f))
            PField(to, { to = it }, tr("To (empty = the end)", "Bitiş (boş = sonu)"), Modifier.weight(1f))
        }
        Text(tr("The streams are copied as they are: no re-encoding, so it is quick and loses no quality.", "Akışlar olduğu gibi kopyalanır: yeniden kodlama yok, hızlı ve kalite kaybı yok."), color = T.sub, fontSize = 11.sp)
        PButton(tr("Cut", "Kes"), Modifier.fillMaxWidth(), compact = true) { run(false) }
        PButton(tr("Keep only the sound", "Sadece sesi kaydet"), Modifier.fillMaxWidth(), compact = true) { run(true) }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
    }
}

/**
 * Copies the tracks of [input] between [startUs] and [endUs] into [out] (an MP4 file). With [soundOnly] only the audio is kept.
 * Returns null when it worked, else the reason.
 */
internal fun cutStreams(ctx: Context, input: Uri, startUs: Long, endUs: Long?, soundOnly: Boolean, out: File): String? {
    val ex = MediaExtractor()
    var mux: MediaMuxer? = null
    return try {
        ex.setDataSource(ctx, input, null)
        mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val map = HashMap<Int, Int>()
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            val keep = if (soundOnly) mime.startsWith("audio/") else mime.startsWith("video/") || mime.startsWith("audio/")
            if (keep) { map[i] = mux.addTrack(f); ex.selectTrack(i) }
        }
        if (map.isEmpty()) return tr("no track to copy", "kopyalanacak akış yok")
        ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        mux.start()
        val buf = ByteBuffer.allocate(4 shl 20)
        val info = MediaCodec.BufferInfo()
        var base = -1L
        while (true) {
            val track = ex.sampleTrackIndex
            if (track < 0) break
            val t = ex.sampleTime
            if (endUs != null && t > endUs) break
            val size = ex.readSampleData(buf, 0)
            if (size < 0) break
            if (base < 0) base = t
            info.set(0, size, (t - base).coerceAtLeast(0L), if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            mux.writeSampleData(map[track] ?: 0, buf, info)
            ex.advance()
        }
        null
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    } finally {
        runCatching { mux?.stop() }
        runCatching { mux?.release() }
        ex.release()
    }
}
