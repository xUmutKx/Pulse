package com.umutk.pulse.features

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.InfoRows
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Screen resolution and density, scaled together (Toolkit > Resolution). Android keeps the size of everything the same on the screen when the
 * density follows the resolution, so a quarter of the pixels needs a quarter of the density, counted from the phone's OWN density and not from
 * what is set at the moment (a half-size screen at half the density, then a quarter-size one left at that density, made everything twice too big).
 * Changing it needs root or Shizuku (`wm size`, `wm density`). A change is kept only when it is confirmed: unconfirmed, the old values come back after
 * 15 seconds, by three independent ways: a guard process in the shell (still runs when the screen has become unusable and the app is gone), the countdown
 * in the app, and a check when the tool is opened again.
 */
private const val MARK = "/data/local/tmp/pulse_res_pending"
private const val GRACE_S = 15

/** The phone's own display values and what is set now (an override is what `wm` reports on top of the physical value). */
private class Disp(val nW: Int, val nH: Int, val nDpi: Int, val cW: Int, val cH: Int, val cDpi: Int, val ovSize: Boolean, val ovDpi: Boolean)

private fun readDisp(): Disp? {
    val size = Shell.run("wm size"); val dens = Shell.run("wm density")
    val ps = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(size) ?: return null
    val pd = Regex("Physical density:\\s*(\\d+)").find(dens) ?: return null
    val os = Regex("Override size:\\s*(\\d+)x(\\d+)").find(size)
    val od = Regex("Override density:\\s*(\\d+)").find(dens)
    val nW = ps.groupValues[1].toInt(); val nH = ps.groupValues[2].toInt(); val nD = pd.groupValues[1].toInt()
    return Disp(nW, nH, nD, os?.groupValues?.get(1)?.toInt() ?: nW, os?.groupValues?.get(2)?.toInt() ?: nH, od?.groupValues?.get(1)?.toInt() ?: nD, os != null, od != null)
}

/** The shell lines that put the values in [d] back. */
private fun restoreLine(d: Disp): String =
    (if (d.ovSize) "wm size ${d.cW}x${d.cH}" else "wm size reset") + "; " + (if (d.ovDpi) "wm density ${d.cDpi}" else "wm density reset")

private fun even(x: Float) = (x / 2f).roundToInt() * 2

private class Preset(val n: Int, val d: Int)
private val PRESETS = listOf(Preset(3, 4), Preset(2, 3), Preset(5, 8), Preset(1, 2), Preset(3, 8), Preset(1, 3), Preset(1, 4))

@Composable
fun ResolutionTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sp = remember { ctx.getSharedPreferences("res_tool", Context.MODE_PRIVATE) }
    var disp by remember { mutableStateOf<Disp?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf("") }
    var wT by remember { mutableStateOf("") }
    var hT by remember { mutableStateOf("") }
    var dT by remember { mutableStateOf("") }
    var keepRatio by remember { mutableStateOf(true) }
    // an unconfirmed change (kept across a restart of the screen: the change itself re-creates the app's window): the values to put back and when
    var pendRestore by remember { mutableStateOf(sp.getString("restore", "") ?: "") }
    var deadline by remember { mutableStateOf(sp.getLong("deadline", 0L)) }
    var left by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) { disp = withContext(Dispatchers.IO) { runCatching { readDisp() }.getOrNull() } }

    fun putBack() {
        val line = pendRestore
        pendRestore = ""; deadline = 0L; sp.edit().remove("restore").remove("deadline").apply()
        if (line.isNotEmpty()) scope.launch { withContext(Dispatchers.IO) { Shell.run("$line; rm -f $MARK") }; reload++ }
    }
    // the third guard: opened again with a change still unconfirmed (its time is over): the old values come back now
    LaunchedEffect(Unit) { if (pendRestore.isNotEmpty() && System.currentTimeMillis() > deadline) putBack() }
    LaunchedEffect(pendRestore, deadline) {
        while (pendRestore.isNotEmpty()) {
            left = ((deadline - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
            if (left <= 0) { putBack(); break }
            delay(250)
        }
    }

    val d = disp
    if (d == null) {
        PCard { Text(tr("Reading the display ...", "Ekran okunuyor ..."), color = T.sub, fontSize = 14.sp) }
        AccessNeededCard(tr("Resolution", "Çözünürlük"), tr("Changing the resolution needs root or Shizuku.", "Çözünürlüğü değiştirmek için root veya Shizuku gerekir."))
        return
    }
    AccessNeededCard(tr("Resolution", "Çözünürlük"), tr("Changing the resolution needs root or Shizuku.", "Çözünürlüğü değiştirmek için root veya Shizuku gerekir."))

    val nativeDp = d.nW * 160f / d.nDpi
    fun fill(w: Int, h: Int, dpi: Int) { wT = w.toString(); hT = h.toString(); dT = dpi.toString() }
    val w = wT.toIntOrNull(); val h = hT.toIntOrNull(); val dpi = dT.toIntOrNull()
    val valid = w != null && h != null && dpi != null && w in 240..d.nW && h in 240..d.nH && dpi in 72..800
    val dpWide = if (w != null && dpi != null && dpi > 0) w * 160f / dpi else 0f

    PCard {
        InfoRows(listOf(
            tr("Phone's own", "Telefonun kendisi") to "${d.nW} × ${d.nH} px · ${d.nDpi} dpi",
            tr("Now", "Şu an") to "${d.cW} × ${d.cH} px · ${d.cDpi} dpi" + if (d.ovSize || d.ovDpi) "  (" + tr("changed", "değiştirilmiş") + ")" else "",
            tr("Width of the layout", "Düzen genişliği") to "%.0f dp".format(d.cW * 160f / d.cDpi),
        ))
    }
    PCard {
        Text(tr("A share of the phone's own screen. The density goes with it, so everything keeps its size on the screen.", "Telefonun kendi ekranının bir oranı. Yoğunluk da onunla gider, böylece her şey ekranda aynı boyutta kalır."), color = T.text, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(false, tr("Own (reset)", "Kendi (sıfırla)")) { fill(d.nW, d.nH, d.nDpi) }
            PRESETS.forEach { p ->
                val pw = even(d.nW * p.n / p.d.toFloat()); val ph = even(d.nH * p.n / p.d.toFloat()); val pd = (d.nDpi * p.n / p.d.toFloat()).roundToInt()
                if (pd >= 72 && pw >= 240) PChip(w == pw && h == ph && dpi == pd, "${p.n}/${p.d}") { fill(pw, ph, pd) }
            }
        }
    }
    PCard {
        Text(tr("Your own values", "Kendi değerlerin"), color = T.text, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PField(wT, { v ->
                wT = v.filter { it.isDigit() }
                val nw = wT.toIntOrNull()
                if (keepRatio && nw != null) { hT = even(nw * d.nH / d.nW.toFloat()).toString(); dT = (d.nDpi * nw / d.nW.toFloat()).roundToInt().toString() }
            }, tr("Width", "Genişlik"), Modifier.weight(1f))
            PField(hT, { hT = it.filter { c -> c.isDigit() } }, tr("Height", "Yükseklik"), Modifier.weight(1f))
            PField(dT, { dT = it.filter { c -> c.isDigit() } }, "dpi", Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(keepRatio, tr("Keep the ratio and the density in step", "Oran ve yoğunluk birlikte gitsin")) { keepRatio = !keepRatio }
        }
        if (w != null && dpi != null) {
            val warn = when {
                !valid -> tr("Outside what the screen takes (240 px up to the phone's own size, 72 to 800 dpi).", "Ekranın alabileceğinin dışında (240 px'ten telefonun kendi boyutuna, 72 ile 800 dpi).")
                dpWide < 320f -> tr("A layout under 320 dp wide: some apps will not fit.", "320 dp'den dar düzen: bazı uygulamalar sığmaz.")
                else -> ""
            }
            Text(tr("Layout width", "Düzen genişliği") + "  %.0f dp  (".format(dpWide) + tr("own", "kendi") + " %.0f dp)".format(nativeDp), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            if (warn.isNotEmpty()) Text(warn, color = T.sub, fontSize = 13.sp)
        }
    }
    if (pendRestore.isNotEmpty()) {
        PCard {
            Text(tr("Keep these settings?", "Bu ayarlar kalsın mı?") + "  ($left s)", color = T.text, fontSize = 16.sp)
            Text(tr("Without an answer the old values come back by themselves.", "Cevap verilmezse eski değerler kendiliğinden geri gelir."), color = T.sub, fontSize = 13.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Keep", "Kalsın")) {
                    pendRestore = ""; deadline = 0L; sp.edit().remove("restore").remove("deadline").apply()
                    scope.launch { withContext(Dispatchers.IO) { Shell.run("rm -f $MARK") }; reload++ }
                }
                PButton(tr("Go back now", "Şimdi geri dön"), danger = true) { putBack() }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Apply", "Uygula"), Modifier.weight(1f)) {
                if (!valid || w == null || h == null || dpi == null) return@PButton
                val restore = restoreLine(d)
                val until = System.currentTimeMillis() + GRACE_S * 1000L
                sp.edit().putString("restore", restore).putLong("deadline", until).apply()
                pendRestore = restore; deadline = until
                scope.launch {
                    withContext(Dispatchers.IO) {
                        // the guard first: a separate process that puts the old values back after the grace time unless the mark file was taken away by "Keep"
                        Shell.run("touch $MARK; nohup sh -c 'sleep ${GRACE_S + 3}; if [ -f $MARK ]; then $restore; rm -f $MARK; fi' >/dev/null 2>&1 &")
                        Shell.run("wm size ${w}x$h; wm density $dpi")
                    }
                    reload++
                }
            }
            PButton(tr("Reset to own", "Kendine sıfırla"), Modifier.weight(1f), danger = true) {
                scope.launch { withContext(Dispatchers.IO) { Shell.run("wm size reset; wm density reset; rm -f $MARK") }; fill(d.nW, d.nH, d.nDpi); reload++ }
            }
        }
    }
    if (failed.isNotEmpty()) Text(failed, color = T.sub, fontSize = 13.sp)
}
