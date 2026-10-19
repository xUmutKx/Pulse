package com.umutk.pulse.widgets.music

import com.umutk.pulse.bevel
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

/** Picks the theme of one placed music widget (any of the four shapes): a gallery of live miniatures, no captions beyond the names. */
class MusicWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        fun pick(theme: String) {
            MusicWidget.prefs(this).edit().apply { if (theme.isEmpty()) remove("theme_$id") else putString("theme_$id", theme) }.apply()
            AppWidgetManager.getInstance(this).updateAppWidget(id, MusicWidget.build(this, id))
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                var sel by remember { mutableStateOf(MusicWidget.prefs(this@MusicWidgetConfig).getString("theme_$id", "") ?: "") }
                val items = remember {
                    listOf(ThemeItem("", "System") { MiniWidget(null) }) + musicWidgetThemes.map { th -> ThemeItem(th.id, th.name) { MiniWidget(th) } }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    ThemeGrid(items, sel, { sel = it; pick(it) })
                }
            } }
        }
    }
}

/** Miniature of the widget in a theme: cover, two text bars, play circle. */
@Composable
private fun MiniWidget(t: MusicWidgetTheme?) {
    val bg = t?.let { Color(it.bg) } ?: Color(0xFFF3EDF7)
    val fg = t?.let { Color(it.text) } ?: Color(0xFF1D1B20)
    val ac = t?.let { Color(it.accent) } ?: Color(0xFF6750A4)
    val shape = when (t?.shape) { WidgetShape.SHARP, WidgetShape.BEVEL -> RoundedCornerShape(2.dp); WidgetShape.PILL -> RoundedCornerShape(50); else -> RoundedCornerShape(14.dp) }
    Box(Modifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth().fillMaxHeight(.8f).clip(shape).background(bg).then(if (t?.shape == WidgetShape.BEVEL) Modifier.bevel(raised = true) else Modifier).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.fillMaxHeight(.7f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(ac.copy(alpha = .35f)))
            Column(Modifier.weight(1f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(.8f).height(5.dp).clip(CircleShape).background(fg))
                Box(Modifier.fillMaxWidth(.5f).height(4.dp).clip(CircleShape).background(fg.copy(alpha = .5f)))
            }
            Box(Modifier.size(16.dp).clip(CircleShape).background(ac))
        }
    }
}
