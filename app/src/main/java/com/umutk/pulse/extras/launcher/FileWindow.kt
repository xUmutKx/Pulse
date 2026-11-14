package com.umutk.pulse.extras.launcher

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.IMG_EXTS
import com.umutk.pulse.ImgSrc
import com.umutk.pulse.ImgViewerScreen
import com.umutk.pulse.T
import com.umutk.pulse.imgSiblingsOf
import com.umutk.pulse.ofIsText
import com.umutk.pulse.ofReadText
import com.umutk.pulse.tr
import java.io.File

/** True for a file that opens in a window of the launcher (pictures and text); the others still go to another app. */
internal fun opensInWindow(f: File): Boolean = f.isFile && (f.extension.lowercase() in IMG_EXTS || ofIsText(f))

/** A file shown inside its own window of the desktop: a picture (the others of its folder can be swiped to) or text. The window route is "view:<path>". */
@Composable
fun FileWindow(path: String) {
    val f = File(path)
    val win = LocalDesktopWin.current
    when {
        f.extension.lowercase() in IMG_EXTS -> {
            val items = remember(path) { mutableStateListOf<ImgSrc>().apply { imgSiblingsOf(f.parentFile ?: f).forEach { add(ImgSrc(Uri.fromFile(it), it, it.name)) } } }
            val start = items.indexOfFirst { it.file?.path == path }.coerceAtLeast(0)
            ImgViewerScreen(items, start) { win?.let { DesktopWindows.close(it) } }
        }
        ofIsText(f) -> {
            val text = remember(path) { runCatching { ofReadText(f).take(300_000) }.getOrDefault("") }
            Box(Modifier.fillMaxSize().background(T.bg)) {
                SelectionContainer {
                    Text(text, color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp))
                }
            }
        }
        else -> Box(Modifier.fillMaxSize().background(T.bg), contentAlignment = Alignment.Center) { Text(tr("This file cannot be shown here", "Bu dosya burada gösterilemiyor"), color = T.sub, fontSize = 13.sp) }
    }
}
