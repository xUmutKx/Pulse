package com.umutk.pulse.extras.palette

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.IconMode
import com.umutk.pulse.extras.palette.core.icon.StyleCatalog
import com.umutk.pulse.extras.palette.core.icon.TileShape
import com.umutk.pulse.extras.palette.core.icon.label
import com.umutk.pulse.extras.palette.core.palette.MoodPresets
import com.umutk.pulse.extras.palette.core.palette.PaletteExtractor
import com.umutk.pulse.extras.palette.core.palette.PaletteGenerator
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Palette (an icon pack studio), from the Palette app: five pages. Studio shapes the look, Preview shows it on a home screen, a wallpaper and widgets,
 * Apps sets rules for single apps, Apply sends it to a launcher, Settings holds the automation. Ported from the Palette app (GPL-3.0).
 */
@Composable
fun PaletteHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember { PaletteState(ctx, scope) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler { if (tab != 0) tab = 0 else onBack() }
    val fg = T.text; val sub = T.sub
    val tabs = listOf("palette" to tr("Studio", "Stüdyo"), "phone" to tr("Preview", "Önizleme"), "apps" to tr("Apps", "Uygulamalar"), "download" to tr("Apply", "Uygula"), "settings" to tr("Settings", "Ayarlar"))
    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter).padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", fg, 22.dp, Modifier.clickable { if (tab != 0) tab = 0 else onBack() })
            Text("Palette", color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            com.umutk.pulse.ExtraLookIcon(fg)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) { 0 -> StudioTab(st); 1 -> PreviewTab(st); 2 -> AppsTab(st); 3 -> ApplyTab(st); else -> PaletteSettingsTab(st) }
        }
        // the five pages, along the bottom
        Row(Modifier.fillMaxWidth().background(T.panel).padding(vertical = 6.dp)) {
            tabs.forEachIndexed { i, (icon, name) ->
                val on = tab == i
                Column(Modifier.weight(1f).clickable { tab = i }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PIcon(icon, if (on) T.accent else sub, 22.dp)
                    Text(name, color = if (on) T.accent else sub, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
