package com.umutk.pulse.features

import com.umutk.pulse.subtitleCompat

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    /** Clock design: one of [CLOCK_STYLES]. */
    fun style(c: Context) = prefs(c).getString("style", "light")?.takeIf { it in CLOCK_STYLES } ?: "light"
    fun setStyle(c: Context, s: String) = prefs(c).edit().putString("style", s).apply()
    /** Where the clock block sits: 0..8, rows top / middle / bottom, columns left / centre / right (4 = the middle of the screen). */
    fun pos(c: Context) = prefs(c).getInt("pos", 4).coerceIn(0, 8)
    fun setPos(c: Context, p: Int) = prefs(c).edit().putInt("pos", p).apply()
    /** Brightness of the screen while the cover is up, 5..100 (100 = the panel's maximum); -1 = never set, the system's own. */
    fun bright(c: Context) = prefs(c).getInt("bright", -1).let { if (it < 0) -1 else it.coerceIn(1, 100) }
    fun setBright(c: Context, v: Int) = prefs(c).edit().putInt("bright", v).apply()
    /** The part of the screen the cover leaves open, in percent of the screen: left, top, right, bottom; null = the cover is closed everywhere. */
    fun hole(c: Context): IntArray? = prefs(c).getString("hole", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 4 }?.toIntArray()
    fun setHole(c: Context, h: IntArray?) = prefs(c).edit().apply { if (h == null) remove("hole") else putString("hole", h.joinToString(",")) }.apply()
    val CLOCK_STYLES = listOf("light", "bold", "digital", "serif", "stacked", "small")
}

/** Quick Settings tile for the screen cover (CoverService). Without the overlay permission a tap opens that permission page instead. */
@androidx.annotation.RequiresApi(24)
class CoverTile : TileService() {
    override fun onStartListening() = paint(CoverService.running || StandBy.showing)

    private fun paint(on: Boolean) {
        qsTile?.apply {
            label = tr("Screen cover", "Ekran kapağı")
            val allowed = com.umutk.pulse.Compat.canOverlay(this@CoverTile) || CoverAccessibilityService.instance != null
            state = if (allowed && on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitleCompat = when { !allowed -> tr("Tap to allow overlay", "İzin vermek için dokun"); on -> tr("On", "Açık"); else -> tr("Off", "Kapalı") }
            updateTile()
        }
    }

    override fun onClick() { try { click0() } finally { com.umutk.pulse.features.TilePop.play(this) } }
    private fun click0() {
        if (!com.umutk.pulse.Compat.canOverlay(this) && CoverAccessibilityService.instance == null) { openOverlayPermission(); return }
        // the advanced (StandBy) cover is a window of its own; the basic one is the black foreground service
        if (StandByPrefs.advanced(this)) { StandBy.toggle(this); paint(StandBy.showing); if (StandBy.showing) CoverAccessibilityService.instance?.collapseShade(); return }
        if (CoverService.running) { CoverService.stop(this); paint(false) } else { CoverService.start(this); paint(true); CoverAccessibilityService.instance?.collapseShade() }
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
    val running by produceState(CoverService.running || StandBy.showing) { while (true) { value = CoverService.running || StandBy.showing; delay(500) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Screen cover", "Ekran kapağı"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(running, { want ->
            when {
                !com.umutk.pulse.Compat.canOverlay(ctx) && CoverAccessibilityService.instance == null -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                want -> if (StandByPrefs.advanced(ctx)) StandBy.show(ctx) else CoverService.start(ctx)
                else -> { CoverService.stop(ctx); StandBy.hide(ctx) }
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
    var adv by remember { mutableStateOf(StandByPrefs.advanced(ctx)) }
    StandBySettings(adv) { adv = it }
    // one cover: blank black until pages are added above; its brightness
    var bright0 by remember { mutableIntStateOf(CoverStore.bright(ctx).let { if (it < 0) 100 else it }) }
    Text(tr("Brightness", "Parlaklık"), color = T.sub, fontSize = 12.sp)
    com.umutk.pulse.PSlider(bright0.toFloat(), { bright0 = it.toInt().coerceIn(1, 100); CoverStore.setBright(ctx, bright0) }, 1f..100f) {}
    if (!com.umutk.pulse.Compat.canOverlay(ctx) && CoverAccessibilityService.instance == null) {
        Text(tr("Needs the 'display over other apps' permission.", "'Diğer uygulamaların üstünde göster' izni gerekir."), color = T.sub, fontSize = 12.sp)
        PButton(tr("Allow", "İzin ver")) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    // the topmost layer: an accessibility overlay, above the bars and the keyboard
    val layerOn = CoverAccessibilityService.instance != null
    Row(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Cover above everything", "Her şeyin üstünde kapak"), color = T.text, fontSize = 15.sp)
            Text(if (layerOn) tr("On: drawn as the topmost layer", "Açık: en üst katman olarak çizilir") else tr("Off: switch on \"Pulse · Screen cover layer\" in Accessibility", "Kapalı: Erişilebilirlik'te \"Pulse · Screen cover layer\" açılmalı"), color = T.sub, fontSize = 12.sp)
        }
    }
    // the clock, date and battery switches belong to the basic cover only: the advanced one picks its pages in its own editor
    if (!adv) {
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
    // brightness while the cover is up (the basic cover only)
    var bright by remember { mutableIntStateOf(CoverStore.bright(ctx).let { if (it < 0) 100 else it }) }
    Text(tr("Brightness", "Parlaklık"), color = T.sub, fontSize = 12.sp)
    com.umutk.pulse.PSlider(bright.toFloat(), { bright = it.toInt().coerceIn(1, 100); CoverStore.setBright(ctx, bright) }, 1f..100f) {}
    }
    if (!adv) {
    // an open window in the cover: drag its edges
    var hole by remember { mutableStateOf(CoverStore.hole(ctx)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Leave an area open", "Bir alanı açık bırak"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        PSwitch(hole != null, { on -> hole = if (on) intArrayOf(10, 55, 90, 90) else null; CoverStore.setHole(ctx, hole) })
    }
    hole?.let { h -> CoverHoleEditor(h) { n -> hole = n; CoverStore.setHole(ctx, n) } }
    if (clock) {
        // clock designs, each shown as it looks on the black cover
        var style by remember { mutableStateOf(CoverStore.style(ctx)) }
        var pos by remember { mutableIntStateOf(CoverStore.pos(ctx)) }
        Text(tr("Clock style", "Saat stili"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverStore.CLOCK_STYLES.forEach { id ->
                val on = id == style
                Box(Modifier.width(96.dp).height(150.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                    .border(if (on) 3.dp else 1.dp, if (on) com.umutk.pulse.Look.accent else Color(0xFF333333), RoundedCornerShape(14.dp))
                    .clickable { style = id; CoverStore.setStyle(ctx, id) }, contentAlignment = Alignment.Center) { CoverClockSample(id, 1f) }
            }
        }
        // where the clock sits: tap a spot on the small screen
        Text(tr("Clock position", "Saatin yeri"), color = T.sub, fontSize = 12.sp)
        Box(Modifier.width(120.dp).height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))) {
            Column(Modifier.fillMaxSize().padding(6.dp)) {
                repeat(3) { r ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        repeat(3) { col ->
                            val i = r * 3 + col
                            Box(Modifier.weight(1f).fillMaxHeight().clickable { pos = i; CoverStore.setPos(ctx, i) }, contentAlignment = Alignment.Center) {
                                if (i == pos) CoverClockSample(style, .38f)
                                else Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF444444)))
                            }
                        }
                    }
                }
            }
        }
    }
    Text(tr("Own text", "Kendi yazın"), color = T.text, fontSize = 15.sp)
    PField(text, { text = it; CoverStore.setText(ctx, it) }, tr("Shown under the clock", "Saatin altında görünür"))
    }
}

/** The clock of one cover design, at [k] of its preview size (the cover itself draws it in CoverService with the same faces). */
@Composable
private fun CoverClockSample(style: String, k: Float) {
    val (family, weight, sp) = when (style) {
        "bold" -> Triple(FontFamily.SansSerif, FontWeight.Black, 34)
        "digital" -> Triple(FontFamily.Monospace, FontWeight.Normal, 28)
        "serif" -> Triple(FontFamily.Serif, FontWeight.Normal, 32)
        "stacked" -> Triple(FontFamily.SansSerif, FontWeight.Light, 38)
        "small" -> Triple(FontFamily.SansSerif, FontWeight.Normal, 18)
        else -> Triple(FontFamily.SansSerif, FontWeight.Light, 32)
    }
    Text(if (style == "stacked") "12\n45" else "12:45", color = Color.White, fontFamily = family, fontWeight = weight, fontSize = (sp * k).sp,
        lineHeight = (sp * k * 1.02f).sp, textAlign = TextAlign.Center, maxLines = if (style == "stacked") 2 else 1, softWrap = style == "stacked")
}


/** A small screen with the open window drawn as a frame; grab an edge or a corner to resize it, the middle to move it. */
@Composable
private fun CoverHoleEditor(h: IntArray, onChange: (IntArray) -> Unit) {
    val cur by rememberUpdatedState(h)
    val accent = com.umutk.pulse.Look.accent
    Box(Modifier.width(140.dp).height(260.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
        .pointerInput(Unit) {
            var grab = 0   // bits: 1 left, 2 top, 4 right, 8 bottom, 16 = move all
            var last = androidx.compose.ui.geometry.Offset.Zero
            detectDragGestures(onDragStart = { o ->
                last = o
                val a = cur; val w = size.width.toFloat(); val hh = size.height.toFloat(); val slop = 28.dp.toPx()
                val l = a[0] / 100f * w; val t = a[1] / 100f * hh; val r = a[2] / 100f * w; val b = a[3] / 100f * hh
                grab = 0
                if (o.y in (t - slop)..(b + slop) && kotlin.math.abs(o.x - l) < slop) grab = grab or 1
                if (o.x in (l - slop)..(r + slop) && kotlin.math.abs(o.y - t) < slop) grab = grab or 2
                if (o.y in (t - slop)..(b + slop) && kotlin.math.abs(o.x - r) < slop) grab = grab or 4
                if (o.x in (l - slop)..(r + slop) && kotlin.math.abs(o.y - b) < slop) grab = grab or 8
                if (grab == 0 && o.x in l..r && o.y in t..b) grab = 16
            }) { ch, _ ->
                ch.consume()
                val d = ch.position - last; last = ch.position
                val a = cur.copyOf(); val dx = (d.x / size.width * 100).toInt().let { if (it == 0) (if (d.x > 0.5f) 1 else if (d.x < -0.5f) -1 else 0) else it }
                val dy = (d.y / size.height * 100).toInt().let { if (it == 0) (if (d.y > 0.5f) 1 else if (d.y < -0.5f) -1 else 0) else it }
                if (grab and 16 != 0) {
                    val w = a[2] - a[0]; val hh = a[3] - a[1]
                    a[0] = (a[0] + dx).coerceIn(0, 100 - w); a[2] = a[0] + w; a[1] = (a[1] + dy).coerceIn(0, 100 - hh); a[3] = a[1] + hh
                } else {
                    if (grab and 1 != 0) a[0] = (a[0] + dx).coerceIn(0, a[2] - 10)
                    if (grab and 2 != 0) a[1] = (a[1] + dy).coerceIn(0, a[3] - 10)
                    if (grab and 4 != 0) a[2] = (a[2] + dx).coerceIn(a[0] + 10, 100)
                    if (grab and 8 != 0) a[3] = (a[3] + dy).coerceIn(a[1] + 10, 100)
                }
                onChange(a)
            }
        }) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = androidx.compose.ui.geometry.Rect(h[0] / 100f * size.width, h[1] / 100f * size.height, h[2] / 100f * size.width, h[3] / 100f * size.height)
            drawRect(Color(0xFF1E1E1E), r.topLeft, r.size)
            drawRect(accent, r.topLeft, r.size, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
            listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { drawCircle(accent, 6.dp.toPx(), it) }
        }
    }
}
