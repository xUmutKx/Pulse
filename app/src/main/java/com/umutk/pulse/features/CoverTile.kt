package com.umutk.pulse.features

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** What the screen cover keeps visible. Its own prefs file "cover"; clock and bell default to on, the rest to off. */
object CoverStore {
    private fun prefs(c: Context) = c.getSharedPreferences("cover", Context.MODE_PRIVATE)
    fun clock(c: Context) = prefs(c).getBoolean("clock", true)
    fun bell(c: Context) = prefs(c).getBoolean("bell", true)
    fun date(c: Context) = prefs(c).getBoolean("date", false)
    fun battery(c: Context) = prefs(c).getBoolean("battery", false)
    fun text(c: Context) = prefs(c).getString("text", "").orEmpty()
    fun setClock(c: Context, on: Boolean) = prefs(c).edit().putBoolean("clock", on).apply()
    fun setBell(c: Context, on: Boolean) = prefs(c).edit().putBoolean("bell", on).apply()
    fun setDate(c: Context, on: Boolean) = prefs(c).edit().putBoolean("date", on).apply()
    fun setBattery(c: Context, on: Boolean) = prefs(c).edit().putBoolean("battery", on).apply()
    fun setText(c: Context, s: String) = prefs(c).edit().putString("text", s).apply()
}

/** Quick Settings tile for the screen cover (CoverService). Without the overlay permission a tap opens that permission page instead. */
class CoverTile : TileService() {
    override fun onStartListening() = paint(CoverService.running)

    private fun paint(on: Boolean) {
        qsTile?.apply {
            label = tr("Screen cover", "Ekran kapağı")
            val allowed = Settings.canDrawOverlays(this@CoverTile)
            state = if (allowed && on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = when { !allowed -> tr("Tap to allow overlay", "İzin vermek için dokun"); on -> tr("On", "Açık"); else -> tr("Off", "Kapalı") }
            updateTile()
        }
    }

    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) { openOverlayPermission(); return }
        if (CoverService.running) { CoverService.stop(this); paint(false) } else { CoverService.start(this); paint(true) }
    }

    /** Opens "display over other apps" for Pulse; the cover does not start on this tap. */
    private fun openOverlayPermission() {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else @Suppress("DEPRECATION") startActivityAndCollapse(i)
    }
}

/** Toolkit tool: turns the cover on and off, the switch follows the service (double-tap on the cover turns it off too), and the settings sit below. */
@Composable
fun CoverTool() {
    val ctx = LocalContext.current
    val running by produceState(CoverService.running) { while (true) { value = CoverService.running; delay(500) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Screen cover", "Ekran kapağı"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(running, { want ->
            when {
                !Settings.canDrawOverlays(ctx) -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                want -> CoverService.start(ctx)
                else -> CoverService.stop(ctx)
            }
        })
    }
    CoverSettings()
}

/** Screen cover settings: the permission and the items that stay visible on the cover (like an always-on display). Opened by a long press on the tile. */
@Composable
fun CoverSettings() {
    val ctx = LocalContext.current
    var clock by remember { mutableStateOf(CoverStore.clock(ctx)) }
    var date by remember { mutableStateOf(CoverStore.date(ctx)) }
    var battery by remember { mutableStateOf(CoverStore.battery(ctx)) }
    var text by remember { mutableStateOf(CoverStore.text(ctx)) }
    Text(tr("Screen cover", "Ekran kapağı"), color = T.text, fontSize = 15.sp)
    Text(tr("A black cover over the whole screen", "Tüm ekranı kaplayan siyah bir kapak"), color = T.sub, fontSize = 12.sp)
    if (!Settings.canDrawOverlays(ctx)) {
        Text(tr("Needs the 'display over other apps' permission.", "'Diğer uygulamaların üstünde göster' izni gerekir."), color = T.sub, fontSize = 12.sp)
        PButton(tr("Allow", "İzin ver")) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Text(tr("Stays visible on the cover", "Kapakta görünür kalanlar"), color = T.sub, fontSize = 12.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Clock", "Saat"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(clock, { clock = it; CoverStore.setClock(ctx, it) })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Date", "Tarih"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(date, { date = it; CoverStore.setDate(ctx, it) })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Battery", "Pil"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(battery, { battery = it; CoverStore.setBattery(ctx, it) })
    }
    Text(tr("Own text", "Kendi yazın"), color = T.text, fontSize = 15.sp)
    PField(text, { text = it; CoverStore.setText(ctx, it) }, tr("Shown under the clock", "Saatin altında görünür"))
}
