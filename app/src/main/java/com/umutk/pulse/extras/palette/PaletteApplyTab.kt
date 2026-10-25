package com.umutk.pulse.extras.palette

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.extras.palette.iconpack.ShortcutApplier
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the look goes: the Pulse launcher, other launchers, a pack project, flat pictures, a themed shortcut. */
@Composable
fun ApplyTab(st: PaletteState) {
    val ctx = st.ctx
    val scope = rememberCoroutineScope()
    val ex = st.export
    var size by remember { mutableStateOf(PaletteStore.exportSize(ctx)) }
    var saved by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Apply", "Uygula"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("${st.apps.size} " + tr("apps", "uygulama") + " · ${st.spec.palette.name}", color = T.sub, fontSize = 13.sp)

        Card(tr("Pulse launcher", "Pulse başlatıcı"), tr("The Pulse launcher draws its icons in this look, and follows it when you change it.", "Pulse başlatıcı ikonlarını bu görünümde çizer ve değiştirdikçe onu izler.")) {
            ActionChip(tr("Use in the Pulse launcher", "Pulse başlatıcıda kullan")) {
                PaletteStudio.save(ctx, st.spec)
                com.umutk.pulse.extras.launcher.LauncherPrefs.iconPack = "pulse:palette"
                com.umutk.pulse.extras.launcher.LauncherPrefs.save(ctx)
                saved = tr("The Pulse launcher now draws its icons in this look.", "Pulse başlatıcı artık ikonlarını bu görünümde çiziyor.")
            }
        }
        Card(tr("Dynamic pack", "Dinamik paket"), tr("Launchers that read icon packs at run time take the icons live from content://${ctx.packageName}.palette.icons/icon/<package>/<activity>: no rebuild when a colour changes.", "Paketleri çalışırken okuyan başlatıcılar ikonları content://${ctx.packageName}.palette.icons/icon/<paket>/<etkinlik> adresinden canlı alır: renk değişince yeniden derleme gerekmez.")) {
            ChipRow {
                ActionChip(tr("Save the look for launchers", "Görünümü başlatıcılar için kaydet")) { PaletteStudio.save(ctx, st.spec); saved = tr("Saved. Pick \"Pulse Palette\" as the icon pack in a launcher that reads packs from a content provider.", "Kaydedildi. Paketleri içerik sağlayıcıdan okuyan bir başlatıcıda \"Pulse Palette\" paketini seçin.") }
                ActionChip(tr("Copy URI template", "URI şablonunu kopyala")) {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Palette URI", "content://${ctx.packageName}.palette.icons/icon/<package>/<activity>"))
                }
            }
        }
        Card(tr("Icon pack project", "İkon paketi projesi"), tr("A complete Gradle project with every icon, appfilter.xml and a GitHub Actions workflow. Push it, and the workflow returns an installable pack that Nova, Lawnchair, Smart Launcher, Total Launcher, Niagara, Microsoft Launcher, Lynx and Neo accept.", "Her ikonu, appfilter.xml'i ve bir GitHub Actions iş akışını içeren eksiksiz bir Gradle projesi. Gönderince iş akışı Nova, Lawnchair, Smart Launcher, Total Launcher, Niagara, Microsoft Launcher, Lynx ve Neo'nun kabul ettiği kurulabilir paketi verir.")) {
            ActionChip(tr("Export pack project", "Paket projesini dışa aktar"), !ex.running) { st.exportPack(true, size) }
        }
        Card(tr("Pictures", "Resimler"), tr("Flat images named by component, for launchers where you pick each icon by hand.", "Bileşen adıyla düz resimler; ikonları elle seçtiğiniz başlatıcılar için.")) {
            ChipRow {
                ActionChip(tr("Export images", "Resimleri dışa aktar"), !ex.running) { st.exportPack(false, size) }
                ActionChip(tr("Save to Downloads", "İndirilenlere kaydet"), !ex.running) {
                    scope.launch {
                        saved = tr("Drawing the icons…", "İkonlar çiziliyor…")
                        PaletteStudio.save(ctx, st.spec)
                        val n = withContext(Dispatchers.IO) { runCatching { PaletteExport.zip(ctx, st.apps, st.spec) }.getOrDefault(-1) }
                        saved = if (n >= 0) tr("$n icons saved in Downloads/Pulse-Palette.zip", "$n ikon Downloads/Pulse-Palette.zip içine kaydedildi") else tr("Could not save", "Kaydedilemedi")
                    }
                }
            }
        }
        Card(tr("Themed shortcut", "Temalı kısayol"), tr("Pins one app with its new icon to the home screen. Works in any launcher, even one without icon pack support.", "Bir uygulamayı yeni ikonuyla ana ekrana sabitler. İkon paketi desteği olmayan başlatıcılarda bile çalışır.")) {
            ChipRow {
                ActionChip(tr("Pin first app", "İlk uygulamayı sabitle")) {
                    scope.launch {
                        val app = st.apps.firstOrNull() ?: return@launch
                        val bmp = st.engine.render(app, st.spec, 0, 192, st.overrides[app.key])
                        ShortcutApplier.pin(ctx, app, bmp)
                    }
                }
            }
        }
        Text(tr("Icon resolution", "İkon çözünürlüğü"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        ChipRow { listOf(128, 192, 256, 384).forEach { s -> PChip(size == s, "${s}px") { size = s; PaletteStore.setExportSize(ctx, s) } } }

        if (saved.isNotEmpty()) Text(saved, color = T.accent, fontSize = 13.sp)
        if (ex.running || ex.uri != null || ex.message != null) {
            PCard(Modifier.fillMaxWidth()) {
                Text(when { ex.isError -> tr("Something went wrong while exporting. Try again.", "Dışa aktarırken bir şey ters gitti. Tekrar deneyin."); ex.running && ex.message == "pack" -> tr("Packaging", "Paketleniyor"); ex.running -> tr("Rendering icons", "İkonlar çiziliyor"); else -> tr("Saved ", "Kaydedildi ") + (ex.message ?: "") }, color = if (ex.isError) androidx.compose.ui.graphics.Color(0xFFD13438) else T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (ex.running) {
                    Text("${ex.done} / ${ex.total}", color = T.sub, fontSize = 13.sp)
                    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(T.line)) {
                        Box(Modifier.fillMaxWidth(if (ex.total == 0) 0f else (ex.done.toFloat() / ex.total).coerceIn(0f, 1f)).height(6.dp).background(T.accent))
                    }
                } else {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ex.uri?.let { u ->
                            ActionChip(tr("Share", "Paylaş")) {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        ActionChip(tr("Done", "Tamam")) { st.clearExport() }
                    }
                }
            }
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Card(title: String, body: String, actions: @Composable () -> Unit) {
    PCard(Modifier.fillMaxWidth()) {
        Text(title, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        actions()
    }
}
