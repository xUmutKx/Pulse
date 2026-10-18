package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.Equalizer
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** UI state that must survive switching tabs: the equalizer reads back nothing, so the screen keeps the levels. */
class EqState {
    val base = mutableStateListOf<Int>()
    var bass by mutableFloatStateOf(0f)
    var treble by mutableFloatStateOf(0f)
    var tempo by mutableFloatStateOf(1f)
    var on by mutableStateOf(true)
    var preset by mutableIntStateOf(0)
    var page by mutableIntStateOf(0)
}

private class EqPreset(val en: String, val tr: String, val shape: (Float) -> Float)

private val presets = listOf(
    EqPreset("Flat", "Düz") { 0f },
    EqPreset("Bass", "Bas") { t -> .7f * (1 - t) * (1 - t) },
    EqPreset("Treble", "Tiz") { t -> .7f * t * t },
    EqPreset("Vocal", "Vokal") { t -> (.6f * (1f - abs(2 * t - 1) * 1.6f)).coerceAtLeast(-.2f) },
    EqPreset("Rock", "Rock") { t -> .55f * (2 * t - 1) * (2 * t - 1) - .1f },
)

private fun effective(st: EqState, i: Int, n: Int, min: Int, max: Int): Int {
    val t = if (n > 1) i / (n - 1f) else .5f
    val shaped = (st.bass * (1 - t) * (1 - t) + st.treble * t * t) * max
    return (st.base[i] + shaped).roundToInt().coerceIn(min, max)
}

private fun push(eq: Equalizer, st: EqState, min: Int, max: Int) {
    val n = st.base.size
    for (i in 0 until n) eq.setBandLevel(i, effective(st, i, n, min, max))
}

internal class VolumeCtl(ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    fun fraction(): Float = am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
    fun set(f: Float) = am.setStreamVolume(AudioManager.STREAM_MUSIC, (f * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
}

/** Music stream volume as 0..1 plus a setter. Polled, so the hardware keys show up too. */
@Composable
internal fun rememberVolume(): Pair<Float, (Float) -> Unit> {
    val appCtx = LocalContext.current
    val ctl = remember { VolumeCtl(appCtx) }
    var v by remember { mutableFloatStateOf(ctl.fraction()) }
    LaunchedEffect(ctl) { while (true) { v = ctl.fraction(); delay(500) } }
    return v to { f: Float -> v = f; ctl.set(f) }
}

/** Equalizer page (vertical bands, presets, bass/treble) and a knob page (tempo, volume). */
@Composable
fun EffectsScreen(
    eq: Equalizer,
    style: PowerampStyle,
    modifier: Modifier = Modifier,
    engine: MusicPlayerEngine? = null,
    ui: EqState = remember { EqState() },
) {
    val fg = style.onBackdrop
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(CircleShape).background(style.panel).padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf(Icons.Rounded.Equalizer to tr("Equalizer", "Ekolayzır"), Icons.Rounded.Tune to tr("Tempo and volume", "Tempo ve ses")).forEachIndexed { i, (ic, label) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(CircleShape)
                            .background(if (ui.page == i) Color.White.copy(alpha = .14f) else Color.Transparent)
                            .semantics { contentDescription = label; role = Role.Tab }
                            .clickable { ui.page = i },
                        contentAlignment = Alignment.Center,
                    ) { Icon(ic, null, tint = if (ui.page == i) fg else style.textDim, modifier = Modifier.size(24.dp)) }
                }
            }
            if (ui.page == 0 && eq.isSupported) {
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = ui.on,
                    onCheckedChange = { ui.on = it; eq.setEnabled(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = style.accent, checkedThumbColor = style.onAccent),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ui.page == 0) BandsPage(eq, style, ui) else KnobsPage(style, ui, engine)
    }
}

@Composable
private fun BandsPage(eq: Equalizer, style: PowerampStyle, ui: EqState) {
    if (!eq.isSupported) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Block, null, tint = style.textDim, modifier = Modifier.size(48.dp))
        }
        return
    }
    val fg = style.onBackdrop
    val freqs = eq.centerFrequenciesHz
    val (min, max) = eq.levelRange
    if (ui.base.size != freqs.size) { ui.base.clear(); repeat(freqs.size) { ui.base.add(0) } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(presets) { i, p ->
            val sel = ui.preset == i
            Box(
                Modifier.clip(CircleShape).background(if (sel) style.accent else style.panel)
                    .clickable {
                        ui.preset = i; ui.bass = 0f; ui.treble = 0f
                        for (b in freqs.indices) ui.base[b] = (p.shape(if (freqs.size > 1) b / (freqs.size - 1f) else .5f) * max).roundToInt().coerceIn(min, max)
                        push(eq, ui, min, max)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text(tr(p.en, p.tr), color = if (sel) style.onAccent else fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth().height(220.dp)) {
        freqs.forEachIndexed { b, hz ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                VBand(
                    value = effective(ui, b, freqs.size, min, max), min = min, max = max, enabled = ui.on, style = style,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { v ->
                    val t = if (freqs.size > 1) b / (freqs.size - 1f) else .5f
                    ui.base[b] = (v - (ui.bass * (1 - t) * (1 - t) + ui.treble * t * t) * max).roundToInt().coerceIn(min, max)
                    push(eq, ui, min, max)
                }
                Text(if (hz >= 1000) "${hz / 1000}k" else "$hz", color = style.textDim, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ToneSlider(tr("Bass", "Bas"), ui.bass, style, ui.on) { ui.bass = it; push(eq, ui, min, max) }
    ToneSlider(tr("Treble", "Tiz"), ui.treble, style, ui.on) { ui.treble = it; push(eq, ui, min, max) }
}

@Composable
private fun ToneSlider(label: String, value: Float, style: PowerampStyle, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = style.onBackdrop, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(56.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = -1f..1f, enabled = enabled, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent, inactiveTrackColor = style.panel),
        )
    }
}

/** Vertical band slider: groove, centre mark, accent fill from the centre to the thumb. */
@Composable
private fun VBand(value: Int, min: Int, max: Int, enabled: Boolean, style: PowerampStyle, modifier: Modifier, onChange: (Int) -> Unit) {
    val cur by rememberUpdatedState(onChange)
    val on = if (enabled) 1f else .4f
    Canvas(
        modifier.pointerInput(min, max, enabled) {
            if (!enabled) return@pointerInput
            fun at(y: Float): Int = (max - (y / size.height).coerceIn(0f, 1f) * (max - min)).roundToInt()
            awaitEachGesture {
                val d = awaitFirstDown()
                cur(at(d.position.y))
                drag(d.id) { cur(at(it.position.y)); it.consume() }
            }
        },
    ) {
        val cx = size.width / 2
        val w = 6.dp.toPx()
        val r = 9.dp.toPx()
        val top = r; val bot = size.height - r
        fun y(v: Int) = bot - (v - min).toFloat() / (max - min).coerceAtLeast(1) * (bot - top)
        drawLine(style.panel.copy(alpha = 1f).let { Color.White.copy(alpha = .14f) }, Offset(cx, top), Offset(cx, bot), w, StrokeCap.Round)
        val mid = y((min + max) / 2)
        drawLine(Color.White.copy(alpha = .3f), Offset(cx - 10.dp.toPx(), mid), Offset(cx + 10.dp.toPx(), mid), 1.dp.toPx())
        drawLine(style.accent.copy(alpha = on), Offset(cx, mid), Offset(cx, y(value)), w, StrokeCap.Round)
        drawCircle(style.accent.copy(alpha = on), r, Offset(cx, y(value)))
    }
}

@Composable
private fun KnobsPage(style: PowerampStyle, ui: EqState, engine: MusicPlayerEngine?) {
    val fg = style.onBackdrop
    val (vol, setVol) = rememberVolume()
    fun setTempo(t: Float) { ui.tempo = t.coerceIn(.5f, 2f); engine?.player?.setPlaybackSpeed(ui.tempo) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (engine != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Pill2(tr("Tempo", "Tempo"), style, false) {}
                Spacer(Modifier.weight(1f))
                Pill2(tr("Reset", "Sıfırla"), style, true) { setTempo(1f) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(56.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Knob((ui.tempo - .5f) / 1.5f, { setTempo(.5f + it * 1.5f) }, 140.dp, style, false, tr("Tempo", "Tempo"))
                }
                Column(Modifier.width(56.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill2("+", style, true) { setTempo(ui.tempo + .05f) }
                    Pill2("-", style, true) { setTempo(ui.tempo - .05f) }
                }
            }
            Text("%.2fx".format(ui.tempo), color = style.textDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
        }
        Knob(vol, setVol, 170.dp, style, true, tr("Volume", "Ses"))
        Spacer(Modifier.height(10.dp))
        Text(tr("Volume", "Ses"), color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("${(vol * 100).roundToInt()}%", color = style.textDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Pill2(text: String, style: PowerampStyle, outlined: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).let { if (outlined) it.border(1.5.dp, style.textDim.copy(alpha = .6f), CircleShape) else it.background(style.panel) }
            .semantics { role = Role.Button }.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = style.onBackdrop, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

/** Round knob turned by dragging up or down. [value] is 0..1; [arc] draws the accent ring. */
@Composable
internal fun Knob(value: Float, onChange: (Float) -> Unit, diameter: Dp, style: PowerampStyle, arc: Boolean, label: String) {
    val v by rememberUpdatedState(value)
    val cb by rememberUpdatedState(onChange)
    Canvas(
        Modifier.size(diameter).semantics { contentDescription = "$label ${(value * 100).roundToInt()}%" }.pointerInput(Unit) {
            detectDragGestures { c, d -> c.consume(); cb((v - d.y / (size.width * 1.2f)).coerceIn(0f, 1f)) }
        },
    ) {
        val ring = 5.dp.toPx()
        val inset = if (arc) 14.dp.toPx() else 0f
        val d = this.size.width - inset * 2
        drawCircle(Color(0xFF242424), d / 2 - 1.dp.toPx(), center)
        drawCircle(Color.White.copy(alpha = .22f), d / 2 - 1.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        if (arc) {
            val o = Offset(ring / 2, ring / 2)
            val s = Size(this.size.width - ring, this.size.height - ring)
            drawArc(Color.White.copy(alpha = .12f), 135f, 270f, false, o, s, style = Stroke(ring, cap = StrokeCap.Round))
            drawArc(style.accent, 135f, 270f * value, false, o, s, style = Stroke(ring, cap = StrokeCap.Round))
        }
        val a = Math.toRadians((135 + 270.0 * value))
        val r1 = d / 2 * .62f; val r2 = d / 2 * .82f
        drawLine(
            Color(0xFFBDBDBD),
            Offset(center.x + (cos(a) * r1).toFloat(), center.y + (sin(a) * r1).toFloat()),
            Offset(center.x + (cos(a) * r2).toFloat(), center.y + (sin(a) * r2).toFloat()),
            5.dp.toPx(), StrokeCap.Round,
        )
    }
}
