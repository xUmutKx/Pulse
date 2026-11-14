package com.umutk.pulse.extras.launcher

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.IMG_EXTS
import com.umutk.pulse.ImgSrc
import com.umutk.pulse.ImgViewerScreen
import com.umutk.pulse.T
import com.umutk.pulse.features.ExAudio
import com.umutk.pulse.features.EX_AUDIO
import com.umutk.pulse.features.EX_VIDEO
import com.umutk.pulse.imgSiblingsOf
import com.umutk.pulse.ofIsText
import com.umutk.pulse.ofReadText
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Every file opens in a window of the desktop: a picture, a video, music, a PDF and text are shown there; any other kind gets a card with its details and an "Open with" button. */
internal fun opensInWindow(f: File): Boolean = f.isFile

/** A file shown inside its own window of the desktop (the window route is "view:<path>"). */
@Composable
fun FileWindow(path: String) {
    val f = File(path)
    val win = LocalDesktopWin.current
    val ext = f.extension.lowercase()
    when {
        ext in IMG_EXTS -> {
            val items = remember(path) { mutableStateListOf<ImgSrc>().apply { imgSiblingsOf(f.parentFile ?: f).forEach { add(ImgSrc(Uri.fromFile(it), it, it.name)) } } }
            val start = items.indexOfFirst { it.file?.path == path }.coerceAtLeast(0)
            ImgViewerScreen(items, start) { win?.let { DesktopWindows.close(it) } }
        }
        ext in EX_VIDEO -> com.umutk.pulse.extras.video.VideoPlayerScreen(Uri.fromFile(f), { win?.let { DesktopWindows.close(it) } })
        ext in EX_AUDIO -> AudioWindow(f)
        ext == "pdf" -> PdfWindow(f)
        ofIsText(f) -> {
            val text = remember(path) { runCatching { ofReadText(f).take(300_000) }.getOrDefault("") }
            Box(Modifier.fillMaxSize().background(T.bg)) {
                SelectionContainer {
                    Text(text, color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp))
                }
            }
        }
        else -> FileCard(f)
    }
}

@Composable
private fun AudioWindow(f: File) {
    LaunchedEffect(f.path) { ExAudio.play(f) }
    DisposableEffect(f.path) { onDispose { if (ExAudio.file?.path == f.path) ExAudio.stop() } }
    LaunchedEffect(f.path) { while (true) { ExAudio.tick(); delay(250) } }
    fun mmss(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
    Column(Modifier.fillMaxSize().background(T.bg).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(f.nameWithoutExtension, color = T.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Text(mmss(ExAudio.pos) + " / " + mmss(ExAudio.dur), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        WinBtn(if (ExAudio.playing) tr("Pause", "Duraklat") else tr("Play", "Çal")) { ExAudio.toggle() }
    }
}

@Composable
private fun PdfWindow(f: File) {
    val renderer = remember(f.path) { runCatching { PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull() }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) { FileCard(f); return }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(T.panel)) {
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(200)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items((0 until renderer.pageCount).toList()) { i ->
                val page by produceState<ImageBitmap?>(null, i) {
                    value = withContext(Dispatchers.IO) {
                        // the renderer opens one page at a time
                        synchronized(renderer) {
                            runCatching {
                                val p = renderer.openPage(i)
                                try {
                                    val h = (wPx * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(wPx, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE) }
                                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp.asImageBitmap()
                                } finally { p.close() }
                            }.getOrNull()
                        }
                    }
                }
                val bmp = page
                if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                else Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) { Text("…", color = T.sub) }
            }
        }
    }
}

@Composable
private fun FileCard(f: File) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(T.bg).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(f.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
        val kb = f.length() / 1024
        Text(if (kb < 1024) "$kb KB" else "%.1f MB".format(kb / 1024.0), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified())), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text(tr("Pulse has no viewer for this kind of file", "Pulse'ın bu dosya türü için görüntüleyicisi yok"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        WinBtn(tr("Open with…", "Birlikte aç…")) { com.umutk.pulse.features.exHandOver(ctx, f, false) }
    }
}

@Composable
private fun WinBtn(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(T.panel).clickable { onClick() }.padding(horizontal = 22.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, color = T.text, fontSize = 14.sp)
    }
}

/** The pictures of the phone in a window of the desktop (the "Pictures" place of the Start menu): the camera folder as thumbnails, a tap opens the picture in its own window. */
@Composable
fun GalleryWindow() {
    val root = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DCIM)
    val folder = remember { File(root, "Camera").takeIf { it.isDirectory } ?: root }
    Box(Modifier.fillMaxSize().background(T.bg)) {
        com.umutk.pulse.ImageGalleryGrid(folder) { f -> DesktopWindows.open("view:" + f.path, f.name) }
    }
}
