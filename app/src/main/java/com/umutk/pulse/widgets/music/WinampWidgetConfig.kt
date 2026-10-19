package com.umutk.pulse.widgets.music

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import com.umutk.pulse.extras.ui.ThemeGrid
import com.umutk.pulse.extras.ui.ThemeItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.skin.InstalledSkin
import com.umutk.pulse.extras.music.skin.WINAMP_H
import com.umutk.pulse.extras.music.skin.installedSkins
import com.umutk.pulse.extras.music.skin.loadWinampSkin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Picks the skin for one placed Winamp widget (4x2 or 4x1): the drawn classic one or any bundled / installed .wsz.
 * Each row is a live render of the widget with that skin; the search box narrows the 60+ list.
 */
class WinampWidgetConfig : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        // the previews show the shape this widget has: the window, or the 4x1 strip
        val strip = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className == WinampWidgetCompact::class.java.name
        fun pick(name: String?) {
            WinampWidget.prefs(this).edit().apply { if (name == null) remove("skin_$id") else putString("skin_$id", name) }.apply()
            AppWidgetManager.getInstance(this).updateAppWidget(id, WinampWidget.build(this, id))
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
            finish()
        }
        setContent {
            MaterialTheme { Surface(Modifier.fillMaxSize()) {
                var skins by remember { mutableStateOf(emptyList<InstalledSkin>()) }
                var q by remember { mutableStateOf("") }
                LaunchedEffect(Unit) { skins = withContext(Dispatchers.IO) { installedSkins(this@WinampWidgetConfig) } }
                val shown = remember(skins, q) { if (q.isBlank()) skins else skins.filter { it.name.contains(q.trim(), ignoreCase = true) } }
                Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Winamp") })
                    val items = (if (q.isBlank()) listOf(ThemeItem("", "Classic") { Preview(null, strip) }) else emptyList()) +
                        shown.map { s -> ThemeItem(s.file.name, s.name) { Preview(s, strip) } }
                    ThemeGrid(items, "", { pick(it.ifEmpty { null }) })
                }
            } }
        }
    }

    @Composable
    private fun Preview(skin: InstalledSkin?, strip: Boolean) {
        val img by produceState<ImageBitmap?>(null, skin, strip) {
            value = withContext(Dispatchers.IO) {
                val sk = skin?.let { loadWinampSkin(it.file) }
                runCatching { WinampWidget.render(sk, "Pulse", 0.4f, true, 83, boxH = 2 * (if (strip) WinampWidget.STRIP_H else WINAMP_H), strip = strip).asImageBitmap() }.getOrNull()
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None) }
        }
    }
}
