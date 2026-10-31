package com.elcesarmaat.lockart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    link: Link,
    playing: NowPlaying,
    framework: String?,
    spotifyScoped: Boolean?,
    onRequestSpotify: () -> Unit,
    onChange: (Settings) -> Unit,
    onRestart: suspend () -> Boolean,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restartingText = stringResource(R.string.restarting)
    val noRootText = stringResource(R.string.no_root)
    val restart: () -> Unit = {
        scope.launch { snackbar.showSnackbar(if (onRestart()) restartingText else noRootText) }
    }
    val background = MaterialTheme.colorScheme.surfaceContainer

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.subtitle)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item { StatusCard(link, framework, restart) }
            item { LockPreview(settings, link, playing) }
            item {
                Group(
                    stringResource(R.string.group_art),
                    {
                        SwitchItem(
                            Icons.Rounded.Album, stringResource(R.string.enabled_title),
                            stringResource(R.string.enabled_hint), settings.enabled,
                        ) { onChange(settings.copy(enabled = it)) }
                    },
                    {
                        SliderItem(
                            Icons.Rounded.BlurOn, stringResource(R.string.blur_title),
                            if (settings.blur == 0) stringResource(R.string.blur_none) else settings.blur.toString(),
                            settings.blur, Config.MAX_BLUR, settings.enabled,
                        ) { onChange(settings.copy(blur = it)) }
                    },
                    {
                        SliderItem(
                            Icons.Rounded.Contrast, stringResource(R.string.dim_title),
                            stringResource(R.string.dim_value, settings.dim), settings.dim, Config.MAX_DIM,
                            settings.enabled,
                        ) { onChange(settings.copy(dim = it)) }
                    },
                    {
                        SwitchItem(
                            Icons.Rounded.BrightnessAuto, stringResource(R.string.auto_veil_title),
                            stringResource(R.string.auto_veil_hint), settings.autoVeil, settings.enabled,
                        ) { onChange(settings.copy(autoVeil = it)) }
                    },
                )
            }
            item {
                Group(
                    stringResource(R.string.group_playback),
                    {
                        SwitchItem(
                            Icons.Rounded.Headphones, stringResource(R.string.music_mode_title),
                            stringResource(R.string.music_mode_hint), settings.musicMode,
                        ) { onChange(settings.copy(musicMode = it)) }
                    },
                    {
                        SwitchItem(
                            Icons.Rounded.PauseCircle, stringResource(R.string.paused_title),
                            stringResource(R.string.paused_hint), settings.keepPaused,
                        ) { onChange(settings.copy(keepPaused = it)) }
                    },
                )
            }
            item {
                Group(
                    stringResource(R.string.group_lyrics),
                    {
                        SwitchItem(
                            Icons.Rounded.Lyrics, stringResource(R.string.lyrics_title),
                            stringResource(R.string.lyrics_hint), settings.lyrics,
                        ) { onChange(settings.copy(lyrics = it)) }
                    },
                    *(if (settings.lyrics && spotifyScoped == false) arrayOf<@Composable () -> Unit>({
                        ListItem(
                            modifier = Modifier.clickable(onClick = onRequestSpotify),
                            headlineContent = { Text(stringResource(R.string.scope_title)) },
                            supportingContent = { Text(stringResource(R.string.scope_hint)) },
                            leadingContent = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }) else emptyArray()),
                    {
                        AnimationItem(settings.lyricsAnim, settings.lyrics) { onChange(settings.copy(lyricsAnim = it)) }
                    },
                )
            }
            item {
                Group(
                    stringResource(R.string.group_aod),
                    {
                        SwitchItem(
                            Icons.Rounded.Nightlight, stringResource(R.string.aod_title),
                            stringResource(R.string.aod_hint), settings.aodLyrics, settings.lyrics,
                        ) { onChange(settings.copy(aodLyrics = it)) }
                    },
                    {
                        SwitchItem(
                            Icons.Rounded.ToggleOn, stringResource(R.string.aod_toggle_title),
                            stringResource(R.string.aod_toggle_hint), settings.aodToggle, settings.lyrics,
                        ) { onChange(settings.copy(aodToggle = it)) }
                    },
                )
            }
            item {
                Group(
                    stringResource(R.string.group_system),
                    {
                        ListItem(
                            modifier = Modifier.clickable(onClick = restart),
                            headlineContent = { Text(stringResource(R.string.restart)) },
                            supportingContent = { Text(stringResource(R.string.restart_hint)) },
                            leadingContent = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    },
                )
            }
            item { Credits() }
        }
    }
}

// ---- Estado del gancho ----

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(link: Link, framework: String?, onRestart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ok = link is Link.On && link.attached
    val problem = link == Link.Off || (link is Link.On && !link.attached)
    val title: String
    val body: String
    when (link) {
        Link.Waiting -> {
            title = stringResource(R.string.status_waiting_title)
            body = stringResource(R.string.status_waiting_body)
        }
        Link.Off -> {
            title = stringResource(R.string.status_off_title)
            body = stringResource(R.string.status_off_body)
        }
        is Link.On -> if (link.attached) {
            title = stringResource(R.string.status_on_title)
            body = pluralStringResource(R.plurals.status_on_body, link.sessions, link.sessions)
        } else {
            title = stringResource(R.string.status_no_view_title)
            body = stringResource(R.string.status_no_view_body)
        }
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = when {
            ok -> colors.primaryContainer
            problem -> colors.errorContainer
            else -> colors.surfaceBright
        },
        contentColor = when {
            ok -> colors.onPrimaryContainer
            problem -> colors.onErrorContainer
            else -> colors.onSurface
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    when {
                        link == Link.Waiting -> LoadingIndicator(Modifier.size(40.dp))
                        ok -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, Modifier.size(32.dp))
                        else -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null, Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    if (framework != null) {
                        Text(framework, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (problem) {
                FilledTonalButton(onClick = onRestart, modifier = Modifier.padding(top = 16.dp).align(Alignment.End)) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.restart))
                }
            }
        }
    }
}

// ---- Vista previa del bloqueo ----

/** Un Pixel 9 Pro XL en miniatura (1344 x 2992) con la carátula, el velo y el reloj del bloqueo. */
@Composable
private fun LockPreview(settings: Settings, link: Link, playing: NowPlaying) {
    val colors = MaterialTheme.colorScheme
    val sample = remember(colors.primary) { sampleArt(colors.primary.toArgb()) }
    val source = playing.art ?: sample
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, settings.blur) {
        shown = withContext(Dispatchers.Default) { Blur.apply(source, settings.blur) }
    }
    val veil = if (playing.veilLight) Color.White else Color.Black
    // El reloj del bloqueo toma el color del fondo de pantalla, igual que este esquema dinámico.
    val ink = if (playing.veilLight && settings.enabled) colors.onPrimaryFixed else colors.primaryFixed
    val shape = RoundedCornerShape(36.dp)

    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth(0.6f)
                .aspectRatio(1344f / 2992f)
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, colors.outlineVariant, shape),
        ) {
            shown?.let {
                Image(
                    it.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alpha = if (settings.enabled) 1f else 0.15f,
                )
            }
            Box(Modifier.fillMaxSize().background(veil.copy(alpha = if (settings.enabled) settings.dim / 100f else 0f)))
            LockClock(ink, maxWidth, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = maxHeight * 0.1f))
            val track = if (playing.art != null) {
                listOfNotNull(playing.title, playing.artist).filter { it.isNotEmpty() }.joinToString(" · ")
            } else {
                stringResource(R.string.sample_track)
            }
            Text(
                "♪  $track", color = ink, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = maxHeight * 0.06f),
            )
        }
        Text(
            when {
                playing.app != null && playing.art != null && playing.lyricLines > 0 ->
                    stringResource(R.string.caption_now_lyrics, playing.app, playing.lyricLines)
                playing.app != null && playing.art != null -> stringResource(R.string.caption_now, playing.app)
                link is Link.On -> stringResource(R.string.caption_silent)
                else -> stringResource(R.string.caption_sample)
            },
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun LockClock(color: Color, width: Dp, modifier: Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    val locale = Locale.getDefault()
    val hours = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH" else "h", locale).format(Date(now))
    val minutes = SimpleDateFormat("mm", locale).format(Date(now))
    val date = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale).format(Date(now))
        .replaceFirstChar { it.titlecase(locale) }
    val density = LocalDensity.current
    val clockSize = with(density) { (width * 0.21f).toSp() }
    Column(modifier) {
        // Horas y minutos por separado: el espacio de la fuente del reloj es mucho más ancho que el hueco del bloqueo.
        Row {
            Text(hours, color = color, fontFamily = ClockFamily, fontWeight = FontWeight.Medium, fontSize = clockSize)
            Spacer(Modifier.width(width * 0.035f))
            Text(minutes, color = color, fontFamily = ClockFamily, fontWeight = FontWeight.Medium, fontSize = clockSize)
        }
        Text(
            date, color = color, style = MaterialTheme.typography.labelMedium,
            fontSize = with(density) { (width * 0.055f).toSp() },
        )
    }
}

// ---- Créditos ----

/** Proyectos en los que se basa el módulo, con su licencia. */
@Composable
private fun Credits() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp)) {
        Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.titleSmall, color = colors.primary)
        Text(
            stringResource(R.string.credits_body),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---- Listas agrupadas (como Ajustes en Android 16+) ----

/** Elementos en tarjetas segmentadas: esquinas grandes en los extremos del grupo y pequeñas entre elementos. */
@Composable
private fun Group(title: String, vararg items: @Composable () -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { i, item ->
                Box(Modifier.clip(segment(i, items.size)).background(MaterialTheme.colorScheme.surfaceBright)) {
                    item()
                }
            }
        }
    }
}

private fun segment(index: Int, count: Int): Shape {
    val top = if (index == 0) 24.dp else 4.dp
    val bottom = if (index == count - 1) 24.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SliderItem(
    icon: ImageVector,
    title: String,
    value: String,
    current: Int,
    max: Int,
    enabled: Boolean = true,
    onValue: (Int) -> Unit,
) {
    ListItem(
        modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
        headlineContent = { Text(title) },
        supportingContent = {
            Slider(
                value = current.toFloat(),
                onValueChange = { onValue(it.roundToInt()) },
                valueRange = 0f..max.toFloat(),
                enabled = enabled,
            )
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private const val DISABLED_ALPHA = 0.38f

// ---- Animación de las letras ----

private val ANIMATIONS = listOf(R.string.anim_scroll, R.string.anim_fade, R.string.anim_zoom, R.string.anim_none)

/** Selector de la animación al cambiar de línea, con una vista previa que usa la misma vista que el bloqueo. */
@Composable
private fun AnimationItem(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.anim_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.anim_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            ANIMATIONS.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ANIMATIONS.size),
                    enabled = enabled,
                    icon = {},
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        LyricsPreview(selected, Modifier.padding(top = 14.dp))
    }
}

private const val SAMPLE_LINE_MS = 2400L

/**
 * Letra de muestra (texto propio, en el idioma de la app: R.array.sample_lyrics) con tiempos por palabra
 * repartidos, para ver también el relleno.
 */
private fun sampleLyrics(lines: List<String>): Lyrics {
    val n = lines.size
    val start = LongArray(n) { it * SAMPLE_LINE_MS }
    val end = LongArray(n) { (it + 1) * SAMPLE_LINE_MS }
    val wordFrom = IntArray(n + 1)
    val ws = ArrayList<Long>()
    val we = ArrayList<Long>()
    val wc = ArrayList<Int>()
    lines.forEachIndexed { i, text ->
        wordFrom[i] = ws.size
        val words = Regex("\\S+\\s*").findAll(text).map { it.value }.toList()
        val step = (SAMPLE_LINE_MS - 500) / words.size
        words.forEachIndexed { k, w ->
            ws += start[i] + k * step
            we += start[i] + (k + 1) * step
            wc += w.length
        }
    }
    wordFrom[n] = ws.size
    return Lyrics(
        "muestra", lines.toTypedArray(), start, end, wordFrom,
        ws.toLongArray(), we.toLongArray(), wc.toIntArray(),
    )
}

@Composable
private fun LyricsPreview(animation: Int, modifier: Modifier) {
    val lines = stringArrayResource(R.array.sample_lyrics).toList()
    val sample = remember(lines) { sampleLyrics(lines) }
    val loop = SAMPLE_LINE_MS * lines.size
    val origin = remember { SystemClock.elapsedRealtime() }
    val tint = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.35f).compositeOver(Color(0xFF17161C)), Color(0xFF0F0F13)),
                ),
            ),
    ) {
        AndroidView(
            factory = { ctx ->
                LyricsView(ctx, object : LyricsView.Clock {
                    override fun position(): Long = (SystemClock.elapsedRealtime() - origin) % loop
                    override fun playing(): Boolean = true
                }).apply {
                    setBandTop(0.26f)
                    setTextScale(0.82f)
                    setLyrics(sample)
                }
            },
            update = { it.setAnimation(animation) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Un vinilo con la etiqueta del color primario, para la vista previa cuando no suena nada. */
private fun sampleArt(label: Int): Bitmap {
    val n = 480
    val c = n / 2f
    val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(b)
    canvas.drawColor(0xFF1C1A18.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF0B0A09.toInt()
    canvas.drawCircle(c, c, n * 0.44f, p)
    p.style = Paint.Style.STROKE
    p.strokeWidth = 1.5f
    p.color = 0xFF2A2724.toInt()
    var r = n * 0.17f
    while (r < n * 0.43f) {
        canvas.drawCircle(c, c, r, p)
        r += 5f
    }
    p.style = Paint.Style.FILL
    p.shader = SweepGradient(c, c, intArrayOf(0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF), null)
    canvas.drawCircle(c, c, n * 0.44f, p)
    p.shader = null
    p.color = label
    canvas.drawCircle(c, c, n * 0.15f, p)
    p.color = 0xFF1C1A18.toInt()
    canvas.drawCircle(c, c, n * 0.018f, p)
    return b
}
