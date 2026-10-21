package com.umutk.pulse.features

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.tr

/**
 * The network type of the SIM (5G only, LTE only, automatic ...), set through root like the "Force 5G / LTE" apps do.
 * The ids are Android's TelephonyManager.NETWORK_MODE_* values; the choice is written to the global setting the phone app reads, and the radio takes it up
 * when airplane mode is switched on and off for a moment. A Quick Settings tile cycles through the modes picked for it.
 */
object NetMode {
    class Mode(val id: Int, val en: String, val trName: String)
    val all = listOf(
        Mode(23, "5G only", "Yalnızca 5G"),
        Mode(24, "5G / LTE", "5G / LTE"),
        Mode(26, "5G / LTE / 3G / 2G (auto)", "5G / LTE / 3G / 2G (otomatik)"),
        Mode(33, "Global (all networks)", "Global (tüm ağlar)"),
        Mode(11, "LTE only", "Yalnızca LTE"),
        Mode(9, "LTE / 3G / 2G", "LTE / 3G / 2G"),
        Mode(2, "3G only", "Yalnızca 3G"),
        Mode(1, "2G only", "Yalnızca 2G"),
    )
    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    /** The modes the tile cycles through, in the order of [all]; 5G only, LTE only and auto until the user picks. */
    fun cycle(c: Context): List<Int> {
        val saved = sp(c).getString("netmode_cycle", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        val ids = saved?.takeIf { it.isNotEmpty() } ?: listOf(23, 11, 26)
        return all.map { it.id }.filter { it in ids }
    }
    fun setCycle(c: Context, ids: List<Int>) = sp(c).edit().putString("netmode_cycle", ids.joinToString(",")).apply()
    fun viaAirplane(c: Context) = sp(c).getBoolean("netmode_airplane", true)
    fun setViaAirplane(c: Context, on: Boolean) = sp(c).edit().putBoolean("netmode_airplane", on).apply()
    fun label(id: Int): String = all.firstOrNull { it.id == id }?.let { tr(it.en, it.trName) } ?: tr("Unknown ($id)", "Bilinmiyor ($id)")
    /** The mode the phone reports now, or null when it cannot be read. */
    fun current(c: Context): Int? = Settings.Global.getInt(c.contentResolver, "preferred_network_mode", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
    fun next(c: Context): Int {
        val list = cycle(c)
        val cur = current(c)
        return list.getOrNull(list.indexOf(cur) + 1) ?: list.first()
    }

    private fun bit(type: Int) = 1L shl (type - 1)
    private val g2 = bit(1) or bit(2) or bit(16) or bit(4) or bit(7)
    private val g3 = bit(3) or bit(8) or bit(9) or bit(10) or bit(15) or bit(17) or bit(5) or bit(6) or bit(12) or bit(14)
    private val lte = bit(13) or bit(19)
    private val nr = bit(20)

    /** The allowed-network-types bitmask (TelephonyManager.NETWORK_TYPE_BITMASK_*) of a mode; newer phones follow this and not the old setting. */
    fun mask(mode: Int): Long = when (mode) {
        23 -> nr
        24 -> nr or lte
        11 -> lte
        9 -> lte or g3 or g2
        2 -> g3
        1 -> g2
        else -> nr or lte or g3 or g2
    }

    /** Writes the mode for every SIM slot as root and, when asked, flips airplane mode so the radio re-reads it. [done] runs on the main thread. */
    fun apply(c: Context, mode: Int, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        val air = viaAirplane(app)
        Thread {
            // the phone service takes the allowed types directly (Android 13+); older phones only read the global setting above
            val m = mask(mode)
            val cmd = "for k in preferred_network_mode preferred_network_mode1 preferred_network_mode2; do settings put global \$k $mode; done; " +
                "for s in 0 1; do cmd phone set-allowed-network-types-for-users -s \$s $m; done; cmd phone set-allowed-network-types-for-users $m" +
                (if (air) "; cmd connectivity airplane-mode enable; sleep 1; cmd connectivity airplane-mode disable" else "") + "; echo __OK"
            val out = if (Shell.root == true) Shell.runRoot(cmd) else Shell.run(cmd)
            val ok = out.contains("__OK") && current(app) == mode
            android.os.Handler(android.os.Looper.getMainLooper()).post { done(ok) }
        }.start()
    }
}

/** The Toolkit screen of the network type: tap a mode to switch to it now, and mark the ones the Quick Settings tile cycles through. */
@Composable
fun NetworkModeTool() {
    val ctx = LocalContext.current
    var cur by remember { mutableStateOf(NetMode.current(ctx)) }
    var cyc by remember { mutableStateOf(NetMode.cycle(ctx)) }
    var air by remember { mutableStateOf(NetMode.viaAirplane(ctx)) }
    var status by remember { mutableStateOf("") }
    PCard {
        Text(tr("Network type", "Ağ türü") + ": " + (cur?.let { NetMode.label(it) } ?: tr("not readable", "okunamıyor")), color = T.text, fontSize = 15.sp)
        if (Shell.root != true && !Shell.shizukuOk()) Text(tr("Needs root", "Root gerekir"), color = T.sub, fontSize = 12.sp)
        if (status.isNotEmpty()) Text(status, color = T.sub, fontSize = 12.sp)
    }
    PCard {
        NetMode.all.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(m.en, m.trName) + if (cur == m.id) "  ✓" else "", color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f).clickable {
                    status = tr("Switching…", "Değiştiriliyor…")
                    NetMode.apply(ctx, m.id) { ok -> cur = NetMode.current(ctx); status = if (ok) tr("Done", "Tamam") else tr("The phone did not take it. Root is needed, and some phones ignore this setting.", "Telefon kabul etmedi. Root gerekir, bazı telefonlar bu ayarı yok sayar.") }
                }.padding(vertical = 8.dp))
                // the tile cycles through the modes that carry the switch
                PSwitch(m.id in cyc, { on -> cyc = (if (on) cyc + m.id else cyc - m.id).distinct().ifEmpty { listOf(m.id) }; NetMode.setCycle(ctx, cyc) })
            }
        }
    }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon("globe", T.text, 20.dp)
            Text(tr("Switch airplane mode for a moment so the radio takes the change", "Radyonun değişikliği alması için uçak modunu bir an aç-kapat"), color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            PSwitch(air, { air = it; NetMode.setViaAirplane(ctx, it) })
        }
        PButton(tr("Open the phone's radio info", "Telefonun radyo bilgisini aç"), Modifier.fillMaxWidth()) {
            Thread { Shell.runRoot("am start -n com.android.phone/.settings.RadioInfo") }.start()
        }
    }
}
