package com.umutk.pulse.widgets.music

import com.umutk.pulse.bevel
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.umutk.pulse.extras.music.musicWidgetTheme
import com.umutk.pulse.tr
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.WidgetShape
import com.umutk.pulse.extras.music.MusicWidgetTheme
import com.umutk.pulse.extras.music.musicWidgetThemes
import com.umutk.pulse.extras.ui.ThemeGrid
import com.umutk.pulse.extras.ui.ThemeItem

/**
 * The settings of one placed music widget, as a sheet over the home screen (the screen behind stays visible, nothing opaque fills the window).
 * A live preview of the widget sits on top; below it the groups fold: theme, cover, text, what is shown, background. Every change reaches the placed widget at once.
 */
class MusicWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        val prefs = MusicWidget.prefs(this)
        fun refresh() {
            AppWidgetManager.getInstance(this).updateAppWidget(id, MusicWidget.build(this, id))
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var sel by remember { mutableStateOf(prefs.getString("theme_$id", "") ?: "") }
                var alpha by remember { mutableStateOf(prefs.getInt("alpha_$id", 100)) }
                var o by remember { mutableStateOf(WidgetOptions.of(this@MusicWidgetConfig, id)) }
                var open by remember { mutableStateOf("theme") }
                fun put(f: (android.content.SharedPreferences.Editor) -> Unit) { prefs.edit().also(f).apply(); o = WidgetOptions.of(this@MusicWidgetConfig, id); refresh() }
                val items = remember {
                    // "" is the default: the design of the music look (system colours for Material)
                    listOf(ThemeItem("", "Look") { MiniWidget(MusicWidget.lookTheme(this@MusicWidgetConfig), WidgetOptions.of(this@MusicWidgetConfig, id), 100) }) +
                        musicWidgetThemes.map { th -> ThemeItem(th.id, th.name) { MiniWidget(th, WidgetOptions.of(this@MusicWidgetConfig, id), 100) } }
                }
                // outside the sheet: a tap closes the settings, the widget already has every change
                Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { finish() }, contentAlignment = Alignment.BottomCenter) {
                    Surface(
                        Modifier.fillMaxWidth().fillMaxHeight(.82f).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), color = Color(0xF2141416), shadowElevation = 12.dp,
                    ) {
                        Column(Modifier.fillMaxSize().padding(14.dp)) {
                            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = .5f)))
                            Spacer(Modifier.height(10.dp))
                            // live preview: the real design with the chosen options
                            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                                MiniWidget(musicWidgetTheme(sel.ifEmpty { null }) ?: MusicWidget.lookTheme(this@MusicWidgetConfig), o, alpha)
                            }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                @Composable fun Group(key: String, title: String, body: @Composable ColumnScope.() -> Unit) {
                                    Row(Modifier.fillMaxWidth().clickable { open = if (open == key) "" else key }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text(if (open == key) "−" else "+", color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (open == key) Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = body)
                                }
                                @Composable fun Chips(items: List<Pair<String, String>>, cur: String, on: (String) -> Unit) = Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items.forEach { (k, label) -> FilterChip(cur == k, { on(k) }, { Text(label) }) }
                                }
                                @Composable fun Flag(label: String, on: Boolean, set: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, modifier = Modifier.weight(1f)); Switch(on, set)
                                }
                                @Composable fun Amount(label: String, v: Int, range: IntRange, set: (Int) -> Unit) {
                                    Row { Text(label, modifier = Modifier.weight(1f)); Text("$v%", color = MaterialTheme.colorScheme.primary) }
                                    Slider(v.toFloat(), { set(it.toInt()) }, valueRange = range.first.toFloat()..range.last.toFloat())
                                }
                                Group("theme", tr("Theme", "Tema")) { ThemeGrid(items, sel, { sel = it; put { e -> if (it.isEmpty()) e.remove("theme_$id") else e.putString("theme_$id", it) } }) }
                                Group("cover", tr("Cover", "Kapak")) {
                                    Flag(tr("Show the cover", "Kapağı göster"), o.art) { v -> put { it.putBoolean("art_$id", v) } }
                                    Amount(tr("Cover size", "Kapak boyutu"), o.artSize, 60..140) { v -> put { it.putInt("artsize_$id", v) } }
                                    Chips(listOf("l" to tr("Cover left", "Kapak solda"), "r" to tr("Cover right", "Kapak sağda")), if (o.right) "r" else "l") { v -> put { it.putBoolean("right_$id", v == "r") } }
                                }
                                Group("text", tr("Text", "Yazı")) {
                                    Amount(tr("Text size", "Yazı boyutu"), o.text, 70..150) { v -> put { it.putInt("text_$id", v) } }
                                    Flag(tr("Show the artist", "Sanatçıyı göster"), o.artist) { v -> put { it.putBoolean("artist_$id", v) } }
                                }
                                Group("shown", tr("Controls", "Düğmeler")) {
                                    Flag(tr("Progress bar", "İlerleme çubuğu"), o.progress) { v -> put { it.putBoolean("prog_$id", v) } }
                                    Flag(tr("Previous and next", "Önceki ve sonraki"), o.skip) { v -> put { it.putBoolean("skip_$id", v) } }
                                    Flag(tr("Shuffle and repeat", "Karıştır ve tekrarla"), o.modes ?: false) { v -> put { it.putBoolean("modes_$id", v) } }
                                    Text(tr("Accent colour", "Vurgu rengi"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0, 0xFF6750A4.toInt(), 0xFF0078D4.toInt(), 0xFFE74856.toInt(), 0xFF13A10E.toInt(), 0xFFFF8C00.toInt(), 0xFFE3008C.toInt(), 0xFFFFFFFF.toInt()).forEach { c ->
                                            Box(Modifier.size(34.dp).clip(CircleShape).background(if (c == 0) MaterialTheme.colorScheme.surfaceVariant else Color(c))
                                                .border(if (o.accent == c) 3.dp else 1.dp, if (o.accent == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                                .clickable { put { it.putInt("accent_$id", c) } }, contentAlignment = Alignment.Center) { if (c == 0) Text("A", fontSize = 12.sp) }
                                        }
                                    }
                                }
                                Group("bg", tr("Background", "Arka plan")) {
                                    Amount(tr("Opacity", "Opaklık"), alpha, 0..100) { v -> alpha = v; put { it.putInt("alpha_$id", v) } }
                                    Chips(listOf("" to tr("Theme", "Tema"), "round" to tr("Round", "Yuvarlak"), "sharp" to tr("Square", "Köşeli"), "pill" to tr("Pill", "Hap")), o.shape) { v -> put { it.putString("shape_$id", v) } }
                                }
                            }
                            Button({ finish() }, Modifier.fillMaxWidth()) { Text(tr("Done", "Tamam")) }
                        }
                    }
                }
            }
        }
    }
}

/** The widget in miniature: the theme's colours, shape and opacity, with the chosen cover, text size, controls and side. */
@Composable
private fun MiniWidget(t: MusicWidgetTheme?, o: WidgetOptions, alpha: Int) {
    val bg = (t?.let { Color(it.bg) } ?: Color(0xFFF3EDF7)).copy(alpha = alpha / 100f)
    val fg = t?.let { Color(it.text) } ?: Color(0xFF1D1B20)
    val ac = if (o.accent != 0) Color(o.accent) else t?.let { Color(it.accent) } ?: Color(0xFF6750A4)
    val shape = when (o.shape.ifEmpty { when (t?.shape) { WidgetShape.SHARP, WidgetShape.BEVEL -> "sharp"; WidgetShape.PILL -> "pill"; else -> "round" } }) { "sharp" -> RoundedCornerShape(2.dp); "pill" -> RoundedCornerShape(50); else -> RoundedCornerShape(16.dp) }
    val ts = o.text / 100f
    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(.85f).clip(shape).background(bg).then(if (t?.shape == WidgetShape.BEVEL) Modifier.bevel(raised = true) else Modifier).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            @Composable fun Cover() { if (o.art) Box(Modifier.fillMaxHeight(.72f * o.artSize / 100f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(ac.copy(alpha = .35f))) }
            if (!o.right) Cover()
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = if (o.right) Alignment.End else Alignment.Start) {
                Box(Modifier.fillMaxWidth(.8f * ts.coerceAtMost(1.1f)).height((6 * ts).dp).clip(CircleShape).background(fg))
                if (o.artist) Box(Modifier.fillMaxWidth(.5f * ts.coerceAtMost(1.2f)).height((4 * ts).dp).clip(CircleShape).background(fg.copy(alpha = .5f)))
                if (o.progress) Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(ac.copy(alpha = .5f)))
            }
            if (o.skip) Box(Modifier.size(10.dp).clip(CircleShape).background(fg.copy(alpha = .6f)))
            Box(Modifier.padding(horizontal = 4.dp).size(22.dp).clip(CircleShape).background(ac))
            if (o.skip) Box(Modifier.size(10.dp).clip(CircleShape).background(fg.copy(alpha = .6f)))
            if (o.right) Cover()
        }
    }
}
