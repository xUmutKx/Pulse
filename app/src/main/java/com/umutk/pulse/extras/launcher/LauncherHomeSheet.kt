package com.umutk.pulse.extras.launcher

import com.umutk.pulse.ExtraToolActivity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.frame
import com.umutk.pulse.tr

/** Open flags of the home sheet. The long press on empty home space sets [sheet]; its entries set the rest. */
object HomeSheetState {
    var sheet by mutableStateOf(false)
    var widgets by mutableStateOf(false)
}

/**
 * The home sheet (long press on empty space) with Settings and Widgets, drawn in the active look, and the widget picker
 * it can open. Composes the picker's add callback every time, so it sits at the top level of the home screen.
 */
@Composable
fun HomeSheet() {
    val ctx = LocalContext.current
    val addWidget = rememberWidgetAdd { }
    if (HomeSheetState.sheet) ContextPopup(220.dp, 240.dp, onDismiss = { HomeSheetState.sheet = false }) {
        val w = com.umutk.pulse.LocalWindowLook.current
        ActionRow("settings", tr("Settings", "Ayarlar"), w.text) {
            HomeSheetState.sheet = false
            // Windows 10 Mobile's Settings page for the phone looks
            if (LauncherPrefs.look.startsWith("winphone")) ctx.startActivity(Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "wpsettings"))
            else ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java))
        }
        ActionRow("light", tr("Display settings", "Ekran ayarları"), w.text) { HomeSheetState.sheet = false; ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)) }
        ActionRow("palette", tr("Personalize", "Kişiselleştir"), w.text) { HomeSheetState.sheet = false; ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)) }
        if (LauncherPrefs.look in setOf("win11", "win10", "win7", "xp", "win95")) ActionRow("notes", tr("Notes gadget", "Not gadget'ı"), w.text) { HomeSheetState.sheet = false; LauncherPrefs.desktopNote = !LauncherPrefs.desktopNote; LauncherPrefs.save(ctx) }
        ActionRow("gridView", tr("Widgets", "Widget'lar"), w.text) { HomeSheetState.sheet = false; HomeSheetState.widgets = true }
    }
    if (HomeSheetState.widgets) WidgetPickDialog(
        onPick = { HomeSheetState.widgets = false; addWidget(it) },
        onDismiss = { HomeSheetState.widgets = false })
}

@Composable
private fun SheetEntry(icon: String, label: String, onClick: () -> Unit) {
    Column(Modifier.size(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        PIcon(icon, T.text, 32.dp)
        Spacer(Modifier.height(6.dp))
        Text(label, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
