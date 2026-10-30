@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.umutk.pulse.extras.mediatools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Media tools: shrink pictures, videos and music, make and edit GIFs, and change a file into another kind. Everything is saved in Download/Pulse. */
@Composable
fun MediaToolsScreen(back: () -> Unit) {
    BackHandler { back() }
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Media tools", "Medya araçları"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolTile("compress", tr("Compress", "Sıkıştır"), tab == 0, Modifier.weight(1f)) { tab = 0 }
            ToolTile("gif", "GIF", tab == 1, Modifier.weight(1f)) { tab = 1 }
            ToolTile("convert", tr("Convert", "Dönüştür"), tab == 2, Modifier.weight(1f)) { tab = 2 }
            ToolTile("document", "PDF", tab == 3, Modifier.weight(1f)) { tab = 3 }
            ToolTile("play", tr("Cut", "Kes"), tab == 4, Modifier.weight(1f)) { tab = 4 }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) { 0 -> CompressTab(); 1 -> GifTab(); 3 -> PdfTab(); 4 -> CutTab(); else -> ConvertTab() }
            Box(Modifier.size(24.dp))
        }
    }
}

/** An icon over a short name, the same size for every tile of a row; the picked one wears the look's accent. */
@Composable
internal fun ToolTile(icon: String, label: String, picked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(68.dp).clip(RoundedCornerShape(10.dp)).background(if (picked) T.accent.copy(alpha = .16f) else T.panel).border(1.dp, if (picked) T.accent else T.line, RoundedCornerShape(10.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        PIcon(icon, if (picked) T.accent else T.text, 26.dp)
        Text(label, color = if (picked) T.accent else T.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

internal fun nameOf(ctx: Context, u: Uri): String = runCatching {
    ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: u.lastPathSegment.orEmpty().substringAfterLast('/').ifEmpty { "file" }

internal fun sizeOf(ctx: Context, u: Uri): Long = runCatching {
    ctx.contentResolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L }
}.getOrNull() ?: 0L

internal fun baseOf(name: String) = name.substringBeforeLast('.', name)
internal fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()
internal fun fmt(b: Long): String = com.umutk.pulse.features.exSize(b)

/** The kind of a file by its name: "image", "video", "audio", "pdf", "docx", "xlsx", "text", "gif" or "other". */
internal fun kindOf(name: String): String = when (extOf(name)) {
    "jpg", "jpeg", "png", "webp", "bmp", "heic", "heif" -> "image"
    "gif" -> "gif"
    "mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v" -> "video"
    "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr" -> "audio"
    "pdf" -> "pdf"
    "docx" -> "docx"
    "xlsx" -> "xlsx"
    "txt", "md", "csv", "log", "json", "xml", "html", "htm" -> "text"
    else -> "other"
}

/** Copies a picked file into the cache so a library that wants a path can read it. */
internal fun cacheCopy(ctx: Context, u: Uri, name: String): File {
    val f = File(File(ctx.cacheDir, "mediatools").apply { mkdirs() }, "${System.nanoTime()}_" + name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { i.copyTo(it) } }
    return f
}

/** Writes [out] (a finished file in the cache) to Download/Pulse and returns the saved name. */
internal suspend fun saveFile(ctx: Context, name: String, mime: String, out: File): String =
    com.umutk.pulse.docScanSave(ctx, name, mime) { o -> out.inputStream().use { it.copyTo(o) } }

/**
 * Runs one Media3 transformer export of [input] into [out]: [height] > 0 scales a video down to that height, [audioOnly] drops the picture.
 * Returns null when it worked, else the reason. Runs on the main thread, as Transformer wants.
 */
internal suspend fun transform(ctx: Context, input: Uri, out: File, height: Int, videoBps: Int, @Suppress("UNUSED_PARAMETER") audioBps: Int, audioOnly: Boolean): String? = withContext(Dispatchers.Main) {
    out.delete()
    suspendCancellableCoroutine { k ->
        val enc = DefaultEncoderFactory.Builder(ctx)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBps).build())
            .build()
        val t = Transformer.Builder(ctx).setEncoderFactory(enc).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (k.isActive) k.resume(null) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { if (k.isActive) k.resume(exportException.message ?: "export failed") }
            }).build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
        if (audioOnly) item.setRemoveVideo(true)
        else if (height > 0) item.setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(height))))
        runCatching { t.start(item.build(), out.path) }.onFailure { if (k.isActive) k.resume(it.message ?: "export failed") }
        k.invokeOnCancellation { runCatching { t.cancel() } }
    }
}

/** Height of a video in pixels (0 when unknown). */
internal fun videoHeight(ctx: Context, u: Uri): Int = runCatching {
    val r = android.media.MediaMetadataRetriever()
    try { r.setDataSource(ctx, u); r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0 } finally { r.release() }
}.getOrDefault(0)
