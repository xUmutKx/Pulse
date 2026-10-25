package com.umutk.pulse.extras.palette

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.extras.palette.automation.ThemeWorker
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr

/** Automation, upkeep and the credits. How the page looks is the look button at the top (Pulse's own look system). */
@Composable
fun PaletteSettingsTab(st: PaletteState) {
    val ctx = st.ctx
    var dayNight by remember { mutableStateOf(PaletteStore.dayNightAuto(ctx)) }
    var follow by remember { mutableStateOf(PaletteStore.followWallpaper(ctx)) }
    var done by remember { mutableStateOf("") }
    fun sync() { if (dayNight || follow) ThemeWorker.schedule(ctx, if (follow) 3 else 6) else ThemeWorker.cancel(ctx) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Settings", "Ayarlar"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        SectionTitle(tr("Automation", "Otomasyon"))
        PCard(Modifier.fillMaxWidth()) {
            Switch(tr("Day and night look", "Gündüz ve gece görünümü"), tr("Darker from 19:00 to 07:00, lighter by day", "19:00 ile 07:00 arası daha koyu, gündüz daha açık"), dayNight) {
                dayNight = it; PaletteStore.setDayNightAuto(ctx, it); st.update("daynight") { s -> s.copy(dayNight = it) }; sync()
            }
            Switch(tr("Follow the wallpaper colours", "Duvar kağıdı renklerini izle"), tr("Takes a new palette from the wallpaper every few hours", "Birkaç saatte bir duvar kağıdından yeni palet alır"), follow) { follow = it; PaletteStore.setFollowWallpaper(ctx, it); sync() }
            Text(tr("For Tasker, Modes and Routines or adb:", "Tasker, Modlar ve Rutinler ya da adb için:") + "\nam broadcast -a com.umutk.pulse.palette.APPLY_THEME --es theme \"Nord\"\nam broadcast -a com.umutk.pulse.palette.NEXT_PALETTE\nam broadcast -a com.umutk.pulse.palette.REGENERATE --ez wallpaper true",
                color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        SectionTitle(tr("Upkeep", "Bakım"))
        PCard(Modifier.fillMaxWidth()) {
            ChipRow {
                ActionChip(tr("Clear icon cache", "İkon önbelleğini temizle")) { st.clearCache(); done = tr("Cache cleared.", "Önbellek temizlendi.") }
                ActionChip(tr("Reset per-app rules", "Uygulama kurallarını sıfırla")) { st.clearOverrides(); done = tr("Per-app rules reset.", "Uygulama kuralları sıfırlandı.") }
            }
            if (done.isNotEmpty()) Text(done, color = T.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Text(tr("Everything is drawn on the phone. The only network use is the sample photos you ask for.", "Her şey telefonda çizilir. Tek ağ kullanımı, istediğiniz örnek fotoğraflardır."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        SectionTitle(tr("This look", "Bu görünüm"))
        PCard(Modifier.fillMaxWidth()) {
            Text("${st.spec.name} · ${st.spec.palette.name} · ${st.spec.palette.size} " + tr("colours", "renk"), color = T.text, fontSize = 14.sp)
            Text("Palette, GPL-3.0 · github.com/xUmutKx/Palette", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun Switch(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = T.text, fontSize = 15.sp); Text(sub, color = T.sub, fontSize = 12.sp) }
        PSwitch(on, onChange)
    }
}
