package com.umutk.pulse.extras.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A picture as text: its light parts are blank, its dark parts dense characters (or the other way round when inverted). */
object AsciiMaker {
    private const val RAMP = " .:-=+*#%@"

    /** Decodes a picture the user picked; a large one is read at a quarter of its size, which is plenty for text. */
    fun load(ctx: Context, uri: Uri): Bitmap? = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }
    }.getOrNull()

    /** [columns] characters across; a character is about twice as tall as it is wide, so the rows are halved to keep the shape. */
    fun convert(src: Bitmap, columns: Int, invert: Boolean): String {
        val cols = columns.coerceIn(10, 200)
        val rows = (src.height.toFloat() / src.width * cols / 2f).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(src, cols, rows, true)
        val px = IntArray(cols * rows)
        small.getPixels(px, 0, cols, 0, 0, cols, rows)
        val sb = StringBuilder()
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val c = px[y * cols + x]
                val alpha = (c ushr 24) and 0xff
                val light = (0.299 * ((c shr 16) and 0xff) + 0.587 * ((c shr 8) and 0xff) + 0.114 * (c and 0xff)) / 255.0
                // a see-through pixel counts as dark: it stays empty
                val k = (((if (alpha < 40) 0.0 else light) * (RAMP.length - 1)).toInt()).coerceIn(0, RAMP.length - 1)
                sb.append(if (invert) RAMP[k] else RAMP[RAMP.length - 1 - k])
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }
}

/** Picks a picture, sets the quality, and copies the text picture. Opened from the keyboard's "Text and art" page. */
class AsciiMakerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { AsciiMakerScreen(onBack = { finish() }) } }
    }
}

@Composable
private fun AsciiMakerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    var columns by remember { mutableIntStateOf(60) }
    var invert by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) picture = AsciiMaker.load(ctx, uri)
    }
    val art = remember(picture, columns, invert) { picture?.let { AsciiMaker.convert(it, columns, invert) }.orEmpty() }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Picture to ASCII", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { picker.launch("image/*") }) { Text("Choose picture") }
        Text("Quality", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Low" to 30, "Medium" to 60, "High" to 120).forEach { (name, cols) ->
                FilterChip(selected = columns == cols, onClick = { columns = cols }, label = { Text(name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Invert", modifier = Modifier.weight(1f))
            Switch(checked = invert, onCheckedChange = { invert = it })
        }
        if (art.isNotEmpty()) {
            Text(
                art,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                lineHeight = 9.sp,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
            Button(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ascii", art))
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
            }) { Text("Copy") }
        }
        Button(onClick = onBack) { Text("Back") }
    }
}
