package com.umutk.pulse.extras.music.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.poweramp.NeutralAccent
import com.umutk.pulse.extras.music.poweramp.PowerampPrefs
import com.umutk.pulse.extras.music.poweramp.powerampSkin
import com.umutk.pulse.tr

/**
 * The settings of the Poweramp widget, opened when it is placed (and again from the widget's reconfigure corner on launchers that offer it): a live picture of the
 * widget on top and, as in Poweramp's own widget settings, the background, the size of the cover, the corners, the shadow under the cover and the round cover.
 * The choices are shared by every Poweramp widget.
 */
class PowerampWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        fun finishOk() {
            WidgetOpts.refresh(this)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF101010)) {
                    var bg by remember { mutableStateOf(WidgetOpts.bg(this)) }
                    var art by remember { mutableFloatStateOf(WidgetOpts.artPct(this).toFloat()) }
                    var corners by remember { mutableIntStateOf(WidgetOpts.corners(this)) }
                    var shadow by remember { mutableStateOf(WidgetOpts.artShadow(this)) }
                    var circle by remember { mutableStateOf(WidgetOpts.artCircle(this)) }
                    val style = remember { powerampSkin(PowerampPrefs.skin(this)).build(NeutralAccent, androidx.compose.ui.graphics.Color.Black) }
                    // the widget as it will look with these choices
                    val bmp = PowerampWidget.render(style, tr("Song title", "Şarkı adı"), tr("Artist", "Sanatçı"), tr("Album", "Albüm"), .4f, true, 60_000L, 150_000L, null, 2, 12, false, 0, 640, 400,
                        bg != "none", bg, art.toInt(), corners, shadow, circle)
                    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Poweramp", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().height(200.dp).background(Color(0xFF2B2B2B)), contentScale = ContentScale.Fit)
                        Text(tr("Background", "Arka plan"), color = Color(0xFFB0B0B0))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("none" to tr("Transparent", "Şeffaf"), "black" to tr("Black", "Siyah"), "stars" to tr("Starry", "Yıldızlı")).forEach { (k, n) ->
                                FilterChip(bg == k, { bg = k; WidgetOpts.setBg(this@PowerampWidgetConfig, k) }, { Text(n) })
                            }
                        }
                        Text(tr("Cover size", "Kapak boyutu") + "  ${art.toInt()}%", color = Color(0xFFB0B0B0))
                        Slider(art, { art = it; WidgetOpts.setArtPct(this@PowerampWidgetConfig, it.toInt()) }, valueRange = 30f..100f)
                        Text(tr("Corners", "Köşeler"), color = Color(0xFFB0B0B0))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to tr("Square", "Köşeli"), 1 to tr("Rounded", "Yuvarlak köşe"), 2 to tr("Round", "Tam yuvarlak")).forEach { (k, n) ->
                                FilterChip(corners == k, { corners = k; WidgetOpts.setCorners(this@PowerampWidgetConfig, k) }, { Text(n) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Cover shadow", "Kapak gölgesi"), color = Color.White, modifier = Modifier.weight(1f))
                            Switch(shadow, { shadow = it; WidgetOpts.setArtShadow(this@PowerampWidgetConfig, it) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Round cover", "Yuvarlak kapak"), color = Color.White, modifier = Modifier.weight(1f))
                            Switch(circle, { circle = it; WidgetOpts.setArtCircle(this@PowerampWidgetConfig, it) })
                        }
                        Button({ finishOk() }, Modifier.fillMaxWidth()) { Text(tr("Done", "Tamam")) }
                    }
                }
            }
        }
    }
}
