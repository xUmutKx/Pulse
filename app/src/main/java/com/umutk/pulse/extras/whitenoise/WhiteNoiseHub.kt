package com.umutk.pulse.extras.whitenoise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Size
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.extras.ui.SliderRow
import com.umutk.pulse.extras.ui.SwitchRow
import com.umutk.pulse.extras.ui.ThemeGrid
import com.umutk.pulse.extras.ui.ThemeItem
import com.umutk.pulse.widgets.music.NoiseWidget
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.bevel
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import com.umutk.pulse.win7Authentic
import com.umutk.pulse.win95Authentic
import com.umutk.pulse.xpAuthentic
import com.umutk.pulse.extras.ambient.AmbientLast
import com.umutk.pulse.extras.ambient.AmbientMixer
import com.umutk.pulse.extras.ambient.Snd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** White noise and ambient mixer. The sound lives in [AmbientMixer] + the foreground service, so closing the page does not stop it. */
@Composable
fun WhiteNoiseHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val sounds by AmbientMixer.sounds.collectAsState()
    val levels by AmbientMixer.levels.collectAsState()
    val master by AmbientMixer.master.collectAsState()
    val running by AmbientMixer.running.collectAsState()
    val timerMin by NoiseTimer.minutes.collectAsState()
    var user by remember { mutableStateOf(UserPresets.load(ctx)) }
    var selected by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var themeId by remember { mutableStateOf(NoiseThemes.selectedId(ctx)) }
    var fade by remember { mutableStateOf(NoiseThemes.bool(ctx, "fade", true)) }
    var fadeSec by remember { mutableStateOf(NoiseThemes.int(ctx, "fadeSec", 20)) }
    var bg by remember { mutableStateOf(NoiseThemes.bool(ctx, "bg", true)) }
    val look = NoiseThemes.byId(themeId)
    val fg = look?.cText ?: T.text
    val ac = look?.cAccent ?: T.accent
    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) { AmbientMixer.scan(ctx) }
        AmbientMixer.ensure(ctx, found)
        // First run: the calm preset is the pre-selected mix (levels only, nothing starts until play).
        if (AmbientMixer.levels.value.isEmpty()) {
            val last = AmbientLast.load(ctx)
            if (last.isEmpty()) selected = DefaultPreset.name
            AmbientMixer.levels.value = last.ifEmpty { DefaultPreset.levels }
        }
    }
    BackHandler(onBack = onBack)
    // shortcut: also fires on a configuration change; only matters when the user turned background play off
    DisposableEffect(Unit) { onDispose { if (!NoiseThemes.bool(ctx, "bg", true)) AmbientMixer.stopAll() } }

    val shown = sounds.sortedBy { CALM_ORDER.indexOf(it.id).let { i -> if (i < 0) 99 else i } }
    val playing = running.isNotEmpty()
    val nameTaken = name.isBlank() || (BuiltInPresets + user).any { it.name.equals(name.trim(), true) }
    var settingsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    fun apply(p: AmbientPreset) { selected = p.name; AmbientMixer.applyMix(p.levels) }
    fun saveUser(list: List<AmbientPreset>) { user = list; UserPresets.save(ctx, list) }

    LazyColumn(
        Modifier.fillMaxSize().then(if (look != null) Modifier.background(look.brush) else Modifier.background(T.bg)).padding(horizontal = pageGutter),
        verticalArrangement = Arrangement.spacedBy(if (look != null) 8.dp else 12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PIcon("back", fg, 22.dp, Modifier.clickable { if (settingsOpen) settingsOpen = false else onBack() })
                Text(
                    if (settingsOpen) tr("Settings", "Ayarlar") else tr("White noise", "Beyaz gürültü"),
                    color = fg, fontSize = 20.sp, modifier = Modifier.weight(1f),
                )
                com.umutk.pulse.ExtraLookIcon(fg)
                com.umutk.pulse.extras.watchdog.BatteryExemptionButton()
                PIcon(if (playing) "pause" else "play", ac, 28.dp, Modifier.clickable {
                    // stop is immediate (the fade setting only shapes the start); a 3 s fade-out felt like lag
                    if (playing) AmbientMixer.stopAll()
                    else AmbientMixer.startFaded(
                        (BuiltInPresets + user).firstOrNull { it.name == selected }?.levels
                            ?: AmbientLast.load(ctx).ifEmpty { DefaultPreset.levels }, if (fade) 3 else 0
                    )
                })
            }
        }
        if (settingsOpen) {
        item {
            // own opaque panel so the app-look controls stay readable on any theme background
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(T.bg).padding(horizontal = 14.dp)) {
                SettingsSection(tr("Settings", "Ayarlar")) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PIcon("clock", T.text, 22.dp)
                        listOf(0, 15, 30, 60, 90, 120).forEach { m ->
                            PChip(timerMin == m, if (m == 0) tr("Off", "Kapalı") else "$m") { NoiseTimer.start(m, if (fade) fadeSec else 0) }
                        }
                    }
                    SwitchRow(tr("Fade out", "Yavaş kıs"), fade) { fade = it; NoiseThemes.setBool(ctx, "fade", it) }
                    if (fade) SliderRow(tr("Fade seconds", "Kısma süresi") + " $fadeSec", fadeSec.toFloat(), 5f..120f) { fadeSec = it.roundToInt(); NoiseThemes.setInt(ctx, "fadeSec", fadeSec) }
                    SliderRow(tr("Master volume", "Ana ses") + " $master", master.toFloat(), 0f..100f) { AmbientMixer.setMaster(it.roundToInt()) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val sel = user.firstOrNull { it.name == selected }
                        BasicTextField(
                            name, { name = it.take(40) }, singleLine = true,
                            textStyle = TextStyle(color = T.text, fontSize = 14.sp), cursorBrush = SolidColor(T.text),
                            modifier = Modifier.weight(1f).border(1.dp, T.line).padding(horizontal = 8.dp, vertical = 8.dp),
                            decorationBox = { inner ->
                                if (name.isEmpty()) Text(tr("Mix name", "Karışım adı"), color = T.sub, fontSize = 14.sp)
                                inner()
                            },
                        )
                        // save the playing mix (or the current levels when nothing plays) under the typed name
                        PIcon("check", if (nameTaken || user.size >= 50) T.sub else T.accent, 24.dp, Modifier.clickable(enabled = !nameTaken && user.size < 50) {
                            val mix = AmbientMixer.mix().ifEmpty { levels.filterValues { it > 0 } }
                            val n = name.trim()
                            if (mix.isNotEmpty()) { saveUser(user + AmbientPreset(n, n, n, mix, true)); selected = n; name = "" }
                        })
                        if (sel != null) {
                            PIcon("rename", if (nameTaken) T.sub else T.text, 22.dp, Modifier.clickable(enabled = !nameTaken) {
                                val n = name.trim()
                                saveUser(user.map { if (it.name == sel.name) AmbientPreset(n, n, n, it.levels, true) else it }); selected = n; name = ""
                            })
                            PIcon("delete", T.text, 22.dp, Modifier.clickable { saveUser(user - sel); selected = null })
                        }
                    }
                    Row(Modifier.fillMaxWidth().clickable { sounds.forEach { AmbientMixer.setLevel(it.id, 50) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PIcon("recycle", T.text, 22.dp)
                        Text(tr("Reset volumes", "Sesleri sıfırla"), color = T.text, fontSize = 15.sp)
                    }
                    SwitchRow(tr("Play in background", "Arka planda çal"), bg) { bg = it; NoiseThemes.setBool(ctx, "bg", it) }
                }
                SettingsSection(tr("Theme", "Tema")) {
                    val items = remember {
                        listOf(ThemeItem(NoiseThemes.APP, tr("App look", "Uygulama")) { NoiseLookPreview(null) }) +
                            NoiseThemes.all.map { l -> ThemeItem(l.id, l.name) { NoiseLookPreview(l) } }
                    }
                    ThemeGrid(items, themeId, { themeId = it; NoiseThemes.select(ctx, it); NoiseWidget.refresh(ctx) })
                }
            }
        }
        } else {
            item {
                // presets first, built-in then the user's; horizontal scroll keeps one row on a phone
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (BuiltInPresets + user).forEach { p -> NChip(selected == p.name, if (p.user) p.name else tr(p.en, p.tr), look) { apply(p) } }
                }
            }
            items(shown.chunked(2), key = { it.first().id }) { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { s -> Box(Modifier.weight(1f)) { SoundCard(s, levels[s.id] ?: 50, s.id in running, look) } }
                    if (pair.size == 1) Box(Modifier.weight(1f)) {}
                }
            }
            // no gear in the top bar: the settings are reached from the bottom of the list
            item {
                Row(Modifier.fillMaxWidth().clickable { settingsOpen = true }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Settings", "Ayarlar"), color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
    }
}

/** Preset chip in the look's own shape; the app look keeps the shared [PChip]. */
@Composable
private fun NChip(on: Boolean, label: String, look: NoiseLook?, onClick: () -> Unit) {
    if (look == null) { PChip(on, label, onClick); return }
    val shape = RoundedCornerShape((look.radius * 32f).dp)
    Box(
        Modifier.clip(shape).background(if (on) look.cAccent else look.cText.copy(alpha = .1f))
            .then(if (look.win95) Modifier.bevel(raised = !on) else Modifier.border(1.dp, if (on) look.cAccent else look.cText.copy(alpha = .3f), shape))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, color = if (on) look.cBg1 else look.cText, fontSize = 13.sp, fontFamily = if (look.winamp) FontFamily.Monospace else FontFamily.Default)
    }
}

/** One row: icon, name, one slim slider. Tapping the row toggles the sound; touching the slider sets the level (and switches the sound on). */
@Composable
private fun SoundRow(s: Snd, level: Int, on: Boolean, look: NoiseLook?) {
    val fg = look?.cText ?: T.text
    val flat = win95Authentic || xpAuthentic || win7Authentic
    val ac = look?.cAccent ?: if (flat) T.text else T.accent
    val shape: Shape = look?.rowShape ?: RoundedCornerShape(0.dp)
    val stroke = look?.rowBorder(on)
    val mono = if (look?.winamp == true) FontFamily.Monospace else FontFamily.Default
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(look?.rowBg(on) ?: Color.Transparent)
            .then(if (stroke != null) Modifier.border(stroke, shape) else Modifier)
            .then(if (look?.win95 == true) Modifier.bevel(raised = !on) else Modifier)
            .clickable { AmbientMixer.setEnabled(s.id, !on) }
            .padding(horizontal = if (look != null) 12.dp else 0.dp, vertical = if (look != null) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ic = look?.iconShape
        Box(Modifier.size(40.dp).then(if (ic != null) Modifier.clip(ic).background(ac.copy(alpha = .16f)) else Modifier), contentAlignment = Alignment.Center) {
            SoundIcon(s.id, if (on) ac else fg.copy(alpha = .45f), 24.dp, cat = s.cat)
        }
        Column(Modifier.weight(1f)) {
            Text(tr(s.en, s.tr), color = if (on) fg else fg.copy(alpha = .7f), fontSize = 15.sp, fontFamily = mono)
            NoiseSlider(level, on, look) { AmbientMixer.setLevel(s.id, it); if (!on) AmbientMixer.setEnabled(s.id, true) }
        }
    }
}

/** Slim slider drawn by hand so every look gets a clean groove and a proper thumb (styles: line, pill, blocks, outline, LED blocks). */
@Composable
private fun NoiseSlider(level: Int, on: Boolean, look: NoiseLook?, onChange: (Int) -> Unit) {
    val fg = look?.cText ?: T.text
    val ac = look?.cAccent ?: T.accent
    val st = look?.style ?: -1
    val seg = st == 2 || st == 5 || st == 6
    val cur by rememberUpdatedState(onChange)
    val fa = if (on) 1f else .45f
    Canvas(
        Modifier.fillMaxWidth().height(24.dp)
            .pointerInput(seg) {
                val r = if (seg) 0f else 8.dp.toPx()
                detectTapGestures { cur((((it.x - r) / (size.width - 2 * r)) * 100).roundToInt().coerceIn(0, 100)) }
            }
            .pointerInput(seg) {
                val r = if (seg) 0f else 8.dp.toPx()
                detectHorizontalDragGestures { c, _ -> c.consume(); cur((((c.position.x - r) / (size.width - 2 * r)) * 100).roundToInt().coerceIn(0, 100)) }
            },
    ) {
        val f = level / 100f
        val cy = size.height / 2
        val track = fg.copy(alpha = .22f)
        val act = ac.copy(alpha = fa)
        if (seg) {
            val n = if (st == 5) 30 else 20
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            val h = if (st == 5) 10.dp.toPx() else 12.dp.toPx()
            val cr = if (st == 5 || st == 6) CornerRadius(0f) else CornerRadius(2.dp.toPx())
            for (i in 0 until n) drawRoundRect(if ((i + .5f) / n <= f) act else track, Offset(i * (w + gap), cy - h / 2), Size(w, h), cr)
            return@Canvas
        }
        val r = 8.dp.toPx()
        val x = r + (size.width - 2 * r) * f
        val th = when (st) { 1 -> 8.dp.toPx(); 3 -> 2.dp.toPx(); else -> 4.dp.toPx() }
        val cr = CornerRadius(th / 2)
        drawRoundRect(track, Offset(r, cy - th / 2), Size(size.width - 2 * r, th), cr)
        drawRoundRect(act, Offset(r, cy - th / 2), Size(x - r, th), cr)
        when (st) {
            3 -> drawRect(act, Offset(x - 6.dp.toPx(), cy - 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()))
            4 -> { drawCircle(look!!.cBg1, r - 1.dp.toPx(), Offset(x, cy)); drawCircle(act, r - 1.dp.toPx(), Offset(x, cy), style = Stroke(2.dp.toPx())) }
            1 -> drawCircle(Color.White.copy(alpha = fa), 6.dp.toPx(), Offset(x, cy))
            else -> drawCircle(act, 7.dp.toPx(), Offset(x, cy))
        }
    }
}

/** Card: a round icon button toggles the sound, an inverted-U arc under it sets the volume (drag or tap along the arc). */
@Composable
private fun SoundCard(s: Snd, level: Int, on: Boolean, look: NoiseLook?) {
    val fg = look?.cText ?: T.text
    val ac = look?.cAccent ?: T.accent
    val shape = look?.rowShape ?: RoundedCornerShape(20.dp)
    val cur by rememberUpdatedState { v: Int -> AmbientMixer.setLevel(s.id, v); if (!on) AmbientMixer.setEnabled(s.id, true) }
    val f by androidx.compose.animation.core.animateFloatAsState(if (on) level / 100f else 0f, androidx.compose.animation.core.tween(220), label = "arc")
    Column(
        Modifier.fillMaxWidth().clip(shape).background(look?.rowBg(on) ?: fg.copy(alpha = .06f)).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (on) ac.copy(alpha = .18f) else fg.copy(alpha = .08f))
                .border(1.5.dp, if (on) ac else fg.copy(alpha = .25f), androidx.compose.foundation.shape.CircleShape)
                .clickable { AmbientMixer.setEnabled(s.id, !on) },
            contentAlignment = Alignment.Center,
        ) { SoundIcon(s.id, if (on) ac else fg.copy(alpha = .5f), 28.dp, cat = s.cat) }
        Canvas(
            Modifier.padding(top = 6.dp).fillMaxWidth().height(70.dp)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        val a = Math.atan2(maxOf(size.height - 6.dp.toPx() - o.y, 0f).toDouble(), (o.x - size.width / 2f).toDouble())
                        cur(((Math.PI - a) / Math.PI * 100).roundToInt().coerceIn(0, 100))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { c, _ ->
                        c.consume()
                        val a = Math.atan2(maxOf(size.height - 6.dp.toPx() - c.position.y, 0f).toDouble(), (c.position.x - size.width / 2f).toDouble())
                        cur(((Math.PI - a) / Math.PI * 100).roundToInt().coerceIn(0, 100))
                    }
                },
        ) {
            val w = 5.dp.toPx()
            val r = minOf(size.width / 2f, size.height - 6.dp.toPx()) - w
            val cx = size.width / 2f
            val cy = size.height - 6.dp.toPx()
            val tl = Offset(cx - r, cy - r)
            val sz = Size(2 * r, 2 * r)
            drawArc(fg.copy(alpha = .15f), 180f, 180f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            if (f > 0f) drawArc(ac, 180f, 180f * f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val ang = Math.PI * (1 + f)
            drawCircle(if (on) ac else fg.copy(alpha = .4f), 8.dp.toPx(), Offset(cx + r * Math.cos(ang).toFloat(), cy + r * Math.sin(ang).toFloat()))
        }
        Text(tr(s.en, s.tr), color = if (on) fg else fg.copy(alpha = .65f), fontSize = 14.sp, maxLines = 1)
        Text(if (on) "$level%" else tr("Muted", "Kapalı"), color = fg.copy(alpha = .5f), fontSize = 12.sp)
    }
}
