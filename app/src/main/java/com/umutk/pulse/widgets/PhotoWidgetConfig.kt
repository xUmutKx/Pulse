package com.umutk.pulse.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PulsePick
import com.umutk.pulse.tr

/** The photo frame's settings: the photo, whether it fills the tile or fits whole, the corner roundness and a border. Opens when the widget is placed and when it is tapped. */
class PhotoWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val p = PhotoWidget.prefs(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize(), color = Color.Black) {
                var crop by remember { mutableStateOf(p.getBoolean("c$id", true)) }
                var radius by remember { mutableFloatStateOf(p.getFloat("r$id", .18f)) }
                var border by remember { mutableStateOf(p.getBoolean("b$id", false)) }
                var version by remember { mutableIntStateOf(0) }
                val pick = rememberLauncherForActivityResult(PulsePick.OpenDocument()) { u -> if (u != null && PhotoWidget.adopt(this@PhotoWidgetConfig, id, u)) version++ }
                val photo = remember(version) { runCatching { BitmapFactory.decodeFile(java.io.File(filesDir, "photo_widget/$id.src").path) }.getOrNull() }
                val shown = remember(photo, crop, radius, border) { PhotoWidget.render(photo, crop, radius, border).asImageBitmap() }
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(shown, null, Modifier.size(220.dp))
                    TextButton({ pick.launch(arrayOf("image/*")) }) { Text(if (photo == null) tr("Choose a photo", "Fotoğraf seç") else tr("Change the photo", "Fotoğrafı değiştir")) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Fill the tile (off: show the whole photo)", "Kareyi doldur (kapalı: fotoğrafın tamamı)"), Modifier.weight(1f), color = Color.White)
                        Switch(crop, { crop = it })
                    }
                    Text(tr("Corners", "Köşeler"), Modifier.fillMaxWidth(), color = Color.White)
                    Slider(radius, { radius = it })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Border", "Çerçeve"), Modifier.weight(1f), color = Color.White)
                        Switch(border, { border = it })
                    }
                    TextButton({
                        p.edit().putBoolean("c$id", crop).putFloat("r$id", radius).putBoolean("b$id", border).apply()
                        PhotoWidget.refresh(this@PhotoWidgetConfig, id)
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }) { Text(tr("Save", "Kaydet")) }
                }
            } }
        }
    }
}
