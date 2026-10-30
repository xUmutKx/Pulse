package com.umutk.pulse.extras.charging

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/**
 * The power going into or out of the battery, live: voltage times current once a second, from Android's public BatteryManager (no root).
 * The reading and its two vendor quirks (current in mA instead of µA, the sign) follow WattFlow by William (EzyAppCo), MIT License, Copyright (c) 2026;
 * the screen is Pulse's own. The number is battery-side power: the wall adapter's figure is higher (conversion losses, and what the phone itself uses).
 */
class BatterySample(val watts: Double, val voltage: Double, val amps: Double, val plugged: Int, val level: Int, val tempC: Double, val charging: Boolean)

class WattReader(private val c: Context) {
    private val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val sp = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private var nativeUa = sp.getBoolean("watt_native_ua", false)

    fun read(): BatterySample? {
        val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val mv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Int.MIN_VALUE || mv <= 0) return null
        var v = raw.toLong()
        // a raw value of 10 000 or more is proof of true microamps; smaller ones are taken as milliamps until that proof is seen
        if (abs(v) >= 10_000) { if (!nativeUa) { nativeUa = true; sp.edit().putBoolean("watt_native_ua", true).apply() } }
        else if (abs(v) in 1 until 10_000 && !nativeUa) v *= 1000
        if (charging && v < 0) v = -v
        if (!charging && v > 0) v = -v
        val volts = mv / 1000.0; val amps = v / 1_000_000.0; val watts = volts * amps
        if (abs(watts) > (if (charging) 150.0 else 20.0)) return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return BatterySample(watts, volts, amps, i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0), if (level >= 0) level * 100 / scale else -1, i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0, charging)
    }

    /** Minutes until full as the phone itself estimates it (Android 9+), or -1. */
    fun minutesToFull(): Int = if (android.os.Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining().let { if (it > 0) (it / 60_000L).toInt() else -1 } else -1
}

/** Charging power as a screen of its own (kept for the old route). */
@Composable
fun ChargingWattsScreen(back: () -> Unit) {
    BackHandler { back() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Charging power", "Şarj gücü"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        ChargingPowerSection()
        Spacer(Modifier.height(16.dp))
    }
}

/** The charging power card: the battery's live watts, the voltage and the graph. Used in Device > Battery. */
@Composable
fun ChargingPowerSection() {
    val ctx = LocalContext.current
    val reader = remember { WattReader(ctx) }
    var s by remember { mutableStateOf<BatterySample?>(null) }
    var peakIn by remember { mutableDoubleStateOf(0.0) }
    var peakOut by remember { mutableDoubleStateOf(0.0) }
    var toFull by remember { mutableStateOf(-1) }
    var graph by remember { mutableStateOf(listOf<Double>()) }
    LaunchedEffect(Unit) {
        while (true) {
            val r = reader.read()
            s = r
            if (r != null) {
                if (r.watts > peakIn) peakIn = r.watts
                if (-r.watts > peakOut) peakOut = -r.watts
                graph = (graph + r.watts).takeLast(60)
                toFull = if (r.charging) reader.minutesToFull() else -1
            }
            delay(1000)
        }
    }
    val cur = s
    Column(Modifier.fillMaxWidth()) {
        if (cur == null) {
            Text(tr("This phone does not report the battery current, so the power cannot be shown.", "Bu telefon pil akımını bildirmiyor, güç gösterilemiyor."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            return@Column
        }
        PowerPicture(cur)
        val source = when (cur.plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> tr("Wired • AC", "Kablolu • AC")
            BatteryManager.BATTERY_PLUGGED_USB -> tr("Wired • USB", "Kablolu • USB")
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> tr("Wireless", "Kablosuz")
            else -> if (cur.charging) tr("Charging", "Şarj oluyor") else tr("On battery", "Pilde")
        }
        Text(source, color = Look.accent, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("%.1f W".format(abs(cur.watts)), color = T.text, fontSize = 56.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(if (cur.watts >= 0) tr("into the battery", "pile giren") else tr("out of the battery", "pilden çıkan"), color = T.sub, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (toFull > 0) Text("≈ " + (if (toFull >= 60) "${toFull / 60}h ${toFull % 60}m" else "${toFull}m") + " " + tr("to full", "dolmasına"), color = Look.accent, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        PCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Stat("%.2f V".format(cur.voltage), tr("Voltage", "Gerilim"))
                Stat("%.2f A".format(abs(cur.amps)), tr("Current", "Akım"))
                Stat("%.1f°C".format(cur.tempC), tr("Temp", "Sıcaklık"))
                Stat("%.1f W".format(peakIn), tr("Peak in", "En çok giren"))
                Stat("%.1f W".format(peakOut), tr("Peak out", "En çok çıkan"))
            }
        }
        PCard {
            Text(tr("Last 60 seconds", "Son 60 saniye"), color = T.sub, fontSize = 12.sp)
            PowerGraph(graph)
        }
        Text(tr("Battery-side power: the wall adapter's figure is higher (conversion losses, and what the phone uses while it charges).", "Pil tarafındaki güç: duvar adaptörünün değeri daha yüksektir (dönüşüm kayıpları ve şarjda telefonun kendi harcaması)."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
    }
}


@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = T.sub, fontSize = 10.sp, maxLines = 1)
    }
}

/** A plug, a cable of dots flowing towards the battery (or away from it), and the battery with its level. */
@Composable
private fun PowerPicture(s: BatterySample) {
    val phase by rememberInfiniteTransition(label = "flow").animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val accent = Look.accent
    val ink = T.text
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 8.dp)) {
        val w = size.width; val h = size.height; val mid = h / 2f
        // the plug
        drawRoundRect(ink.copy(alpha = .55f), Offset(w * .04f, mid - 16.dp.toPx()), Size(24.dp.toPx(), 32.dp.toPx()), CornerRadius(5.dp.toPx()))
        // the cable
        val x0 = w * .04f + 24.dp.toPx(); val x1 = w * .58f
        val path = Path().apply { moveTo(x0, mid); var x = x0; while (x <= x1) { lineTo(x, mid + sin((x - x0) / (x1 - x0) * 6.28318f * 1.2f) * 14.dp.toPx()); x += 4f } }
        drawPath(path, ink.copy(alpha = .35f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        // the dots move with the current: towards the battery while it charges, back while it empties
        val flowing = abs(s.watts) > 0.2
        if (flowing) for (k in 0 until 4) {
            var t = (k / 4f + (if (s.watts >= 0) phase else 1f - phase)) % 1f
            val x = x0 + (x1 - x0) * t
            drawCircle(accent, 5.dp.toPx(), Offset(x, mid + sin((x - x0) / (x1 - x0) * 6.28318f * 1.2f) * 14.dp.toPx()))
        }
        // the battery
        val bx = w * .62f; val bw = w * .32f; val bh = 52.dp.toPx()
        drawRoundRect(ink.copy(alpha = .7f), Offset(bx, mid - bh / 2), Size(bw, bh), CornerRadius(8.dp.toPx()), style = Stroke(3.dp.toPx()))
        drawRoundRect(ink.copy(alpha = .7f), Offset(bx + bw + 2.dp.toPx(), mid - 8.dp.toPx()), Size(6.dp.toPx(), 16.dp.toPx()), CornerRadius(2.dp.toPx()))
        val fill = (s.level.coerceIn(0, 100) / 100f)
        drawRoundRect(accent.copy(alpha = .85f), Offset(bx + 5.dp.toPx(), mid - bh / 2 + 5.dp.toPx()), Size((bw - 10.dp.toPx()) * fill, bh - 10.dp.toPx()), CornerRadius(4.dp.toPx()))
    }
    Text("${s.level}%", color = T.sub, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
}

/** The power of the last minute: charging above the middle line, discharging below it. */
@Composable
private fun PowerGraph(values: List<Double>) {
    val accent = Look.accent
    val line = T.sub.copy(alpha = .4f)
    Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp)) {
        val maxAbs = (values.maxOfOrNull { abs(it) } ?: 1.0).coerceAtLeast(1.0) * 1.15
        val mid = size.height / 2f
        drawLine(line, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / 59f
            val y = (mid - (v / maxAbs).toFloat() * mid)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, accent, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round))
    }
}
