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
    var pickApp by mutableStateOf(false)
    var newFolder by mutableStateOf(false)
}

/** The file behind a document the picker returned ("primary:Download/x.pdf" is /storage/emulated/0/Download/x.pdf), or null. */
private fun pathOf(ctx: android.content.Context, uri: android.net.Uri): String? = runCatching {
    val id = android.provider.DocumentsContract.getDocumentId(uri)
    val (vol, rel) = id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val root = if (vol.equals("primary", true)) android.os.Environment.getExternalStorageDirectory().path else "/storage/$vol"
    java.io.File(root, rel).path.takeIf { java.io.File(it).exists() }
}.getOrNull()

/**
 * The home sheet (long press on empty space) with Settings and Widgets, drawn in the active look, and the widget picker
 * it can open. Composes the picker's add callback every time, so it sits at the top level of the home screen.
 */
@Composable
fun HomeSheet() {
    val ctx = LocalContext.current
    val addWidget = rememberWidgetAdd { }
    // a file or a picture for the home screen: its place on the phone is kept, the file itself is not copied
    fun addLink(path: String?) {
        if (path == null) { android.widget.Toast.makeText(ctx, tr("This file cannot be put on the home screen", "Bu dosya ana ekrana konamaz"), android.widget.Toast.LENGTH_SHORT).show(); return }
        LauncherPrefs.links = (LauncherPrefs.links + "file:$path").distinct(); LauncherPrefs.save(ctx)
    }
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u -> if (u != null) addLink(pathOf(ctx, u)) }
    var folderName by androidx.compose.runtime.remember { mutableStateOf("") }
    if (HomeSheetState.sheet) ContextPopup(220.dp, 400.dp, onDismiss = { HomeSheetState.sheet = false }) {
        val w = com.umutk.pulse.LocalWindowLook.current
        ActionRow("settings", tr("Settings", "Ayarlar"), w.text) {
            HomeSheetState.sheet = false
            // Windows 10 Mobile's Settings page for the phone looks
            if (LauncherPrefs.look.startsWith("winphone")) ctx.startActivity(Intent(ctx, ExtraToolActivity::class.java).putExtra("route", "wpsettings"))
            else ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java))
        }
        ActionRow("light", tr("Display settings", "Ekran ayarları"), w.text) { HomeSheetState.sheet = false; ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)) }
        ActionRow("palette", tr("Personalize", "Kişiselleştir"), w.text) { HomeSheetState.sheet = false; ctx.startActivity(Intent(ctx, LauncherSettingsActivity::class.java)) }
        if (LauncherPrefs.look in setOf("win11", "win10", "win7", "xp", "win95")) ActionRow("notes", tr("Sticky note", "Yapışkan not"), w.text) { HomeSheetState.sheet = false; stickyNoteProvider(ctx)?.let(addWidget) }
        ActionRow("gridView", tr("Widgets", "Widget'lar"), w.text) { HomeSheetState.sheet = false; HomeSheetState.widgets = true }
        var addOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        ActionRow("add", tr("New", "Yeni"), w.text) { addOpen = !addOpen }
        if (addOpen) androidx.compose.foundation.layout.Column(Modifier.padding(start = 24.dp)) {
            ActionRow("apps", tr("App", "Uygulama"), w.text) { HomeSheetState.sheet = false; HomeSheetState.pickApp = true }
            ActionRow("document", tr("File", "Dosya"), w.text) { HomeSheetState.sheet = false; pickFile.launch(arrayOf("*/*")) }
            ActionRow("image", tr("Picture", "Resim"), w.text) { HomeSheetState.sheet = false; pickFile.launch(arrayOf("image/*")) }
            ActionRow("folder", tr("Folder", "Klasör"), w.text) { HomeSheetState.sheet = false; folderName = ""; HomeSheetState.newFolder = true }
        }
    }
    if (HomeSheetState.pickApp) AppCheckDialog(tr("Add an app", "Uygulama ekle"), emptyList(), Int.MAX_VALUE, onDismiss = { HomeSheetState.pickApp = false }) { pkgs ->
        LauncherPrefs.links = (LauncherPrefs.links + pkgs.map { "app:$it" }).distinct(); LauncherPrefs.save(ctx); HomeSheetState.pickApp = false
    }
    if (HomeSheetState.newFolder) com.umutk.pulse.WinAlert(
        onDismissRequest = { HomeSheetState.newFolder = false },
        title = { Text(tr("New folder", "Yeni klasör")) },
        text = { com.umutk.pulse.PField(folderName, { folderName = it }, tr("Name", "Ad"), Modifier.fillMaxWidth()) },
        confirmButton = { com.umutk.pulse.PButton(tr("Create", "Oluştur")) {
            val n = folderName.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
            if (n.isNotEmpty()) {
                val d = java.io.File(android.os.Environment.getExternalStorageDirectory(), "Documents/$n")
                if (d.exists() || d.mkdirs()) addLink(d.path) else android.widget.Toast.makeText(ctx, tr("Could not create the folder", "Klasör oluşturulamadı"), android.widget.Toast.LENGTH_SHORT).show()
            }
            HomeSheetState.newFolder = false
        } },
        dismissButton = { com.umutk.pulse.PButton(tr("Cancel", "Vazgeç")) { HomeSheetState.newFolder = false } },
    )
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
