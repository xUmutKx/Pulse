package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.shownArtist
import com.umutk.pulse.extras.music.trackArt
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The pages of the advanced cover: single widgets full screen, and "duo" pages with two widgets side by side (stacked when the phone stands upright). */
val STANDBY_PAGES = listOf(
    "blank" to ("Blank (black)" to "Boş (siyah)"),
    "clock" to ("Digital clock" to "Dijital saat"), "analog" to ("Analog clock" to "Analog saat"), "date" to ("Clock with date" to "Tarihli saat"),
    "calendar" to ("Calendar" to "Takvim"), "battery" to ("Battery" to "Pil"), "music" to ("Now playing" to "Çalan şarkı"),
    "duo_clock_battery" to ("Clock + battery" to "Saat + pil"), "duo_clock_calendar" to ("Clock + calendar" to "Saat + takvim"),
    "duo_analog_digital" to ("Analog + digital" to "Analog + dijital"), "duo_music_clock" to ("Music + clock" to "Müzik + saat"),
    // the skins of the iOS StandBy app
    "seg" to ("Segmented Clock" to "Segmentli saat"), "seg_wide" to ("Segmented Clock Horizontal" to "Yatay segmentli saat"), "crop" to ("Big Crop Clock" to "Büyük kırpık saat"),
    "flip" to ("Retroflip Clock" to "Retro çevirmeli saat"), "neon" to ("Neon Clock" to "Neon saat"), "matrix" to ("Matrix Clock" to "Matrix saat"), "pixel" to ("Pixel Art Clock" to "Piksel saat"),
    "roman" to ("Roman Clock" to "Romen rakamlı saat"), "word" to ("Word Clock" to "Yazıyla saat"), "solar" to ("Solar Watch" to "Güneş saati"), "radial" to ("Radial Invert Clock" to "Halkalı saat"),
    "analog_clean" to ("Analog Clock Clean" to "Sade analog saat"), "analog_nomark" to ("Analog Clock Clean No Markers" to "İşaretsiz analog saat"), "analog_square" to ("Analog Clock Square" to "Kare analog saat"),
    "clocks" to ("Clock Of Clocks" to "Saatlerden saat"), "world" to ("World Clock" to "Dünya saati"), "dementia" to ("Dementia Clock" to "Büyük yazılı saat"), "sereno" to ("Sereno" to "Sereno"),
    "tick_digital" to ("Ticks Digital" to "Çizgili dijital saat"), "tick_analog" to ("Ticks Analog" to "Çizgili analog saat"),
    "bat_dots" to ("Battery Dots" to "Pil noktaları"), "bat_wave" to ("Battery Liquid Wave" to "Pil dalgası"), "charging" to ("Charging" to "Şarj"),
    "stopwatch" to ("Stopwatch" to "Kronometre"), "timer" to ("Timer" to "Zamanlayıcı"), "pomodoro" to ("Pomodoro" to "Pomodoro"), "quotes" to ("Quotes" to "Sözler"), "cal_striking" to ("Striking Calendar" to "Büyük günlü takvim"),
)

/** Settings of the advanced screen cover ("StandBy" mode). Own prefs file "standby"; the basic cover keeps its own (CoverStore). */
object StandByPrefs {
    private fun p(c: Context) = c.getSharedPreferences("standby", Context.MODE_PRIVATE)
    /** True: the cover tile and the switch start this screen; false (the default): the basic black cover. */
    fun advanced(c: Context) = true
    fun setAdvanced(c: Context, on: Boolean) = p(c).edit().putBoolean("advanced", on).apply()
    fun pages(c: Context): List<String> = (p(c).getString("pages", null) ?: "blank").split(',').filter { id -> STANDBY_PAGES.any { it.first == id } }
    fun setPages(c: Context, l: List<String>) = p(c).edit().putString("pages", l.joinToString(",")).apply()
    fun font(c: Context) = p(c).getString("font", "light") ?: "light"
    fun setFont(c: Context, v: String) = p(c).edit().putString("font", v).apply()
    /** The palette picked for a skin with several ("crop", "flip", "solar"). */
    fun style(c: Context, key: String) = p(c).getInt("style_$key", 0)
    fun setStyle(c: Context, key: String, v: Int) = p(c).edit().putInt("style_$key", v).apply()
    /** 0..COLOURS.size-1 is one of the swatches; COLOURS.size is the colour mixed by hand ([custom]). */
    fun colour(c: Context) = p(c).getInt("colour", 0).coerceIn(0, COLOURS.size)
    fun custom(c: Context) = p(c).getInt("custom", 0xFF64D2FF.toInt())
    fun setCustom(c: Context, v: Int) = p(c).edit().putInt("custom", v).apply()
    /** The colour the cover draws in. */
    fun ink(c: Context): Color = colour(c).let { if (it >= COLOURS.size) Color(custom(c)) else COLOURS[it] }
    /** Digits drawn as an outline only (hollow), not filled. */
    fun outline(c: Context) = p(c).getBoolean("outline", false)
    fun setOutline(c: Context, v: Boolean) = p(c).edit().putBoolean("outline", v).apply()
    /** A font file of the user's own (any .ttf / .otf, e.g. Apple's San Francisco if they own it), copied into the app's folder. */
    var customFamily: FontFamily? = null
    fun loadCustom(c: Context) { customFamily = runCatching { java.io.File(c.filesDir, "standby_font").takeIf { it.isFile }?.let { FontFamily(androidx.compose.ui.text.font.Font(it)) } }.getOrNull() }
    fun hasCustom(c: Context) = java.io.File(c.filesDir, "standby_font").isFile
    fun setColour(c: Context, v: Int) = p(c).edit().putInt("colour", v).apply()
    fun h24(c: Context) = p(c).getBoolean("h24", true)
    fun setH24(c: Context, v: Boolean) = p(c).edit().putBoolean("h24", v).apply()
    fun seconds(c: Context) = p(c).getBoolean("seconds", false)
    fun setSeconds(c: Context, v: Boolean) = p(c).edit().putBoolean("seconds", v).apply()
    /** The picture creeps a few pixels every minute, so no pixel shows the same thing for hours. */
    /** The curtain that opens when the cover starts and closes when it ends; off by default. */
    fun curtain(c: Context) = p(c).getBoolean("curtain", false)
    fun setCurtain(c: Context, v: Boolean) = p(c).edit().putBoolean("curtain", v).apply()
    fun drift(c: Context) = p(c).getBoolean("drift2", false)
    fun setDrift(c: Context, v: Boolean) = p(c).edit().putBoolean("drift2", v).apply()
    /** Between 22:00 and 06:00 everything goes dim red. */
    fun night(c: Context) = p(c).getBoolean("night", true)
    fun setNight(c: Context, v: Boolean) = p(c).edit().putBoolean("night", v).apply()
    /** Automatic trigger: start by itself while the phone charges. */
    fun autoCharge(c: Context) = p(c).getBoolean("autoCharge", false)
    fun setAutoCharge(c: Context, v: Boolean) = p(c).edit().putBoolean("autoCharge", v).apply()
    /** How big the clock and the widgets are on the cover, in percent. */
    fun size(c: Context) = p(c).getInt("size", 100)
    fun setSize(c: Context, v: Int) = p(c).edit().putInt("size", v).apply()
    /** Automatic trigger: start after this many seconds without a touch (0 = off). */
    fun idleSec(c: Context) = p(c).getInt("idleSec", 0)
    fun setIdleSec(c: Context, v: Int) = p(c).edit().putInt("idleSec", v).apply()
    /** ... and only while it lies sideways, like a bedside clock on its charger. */
    fun sideways(c: Context) = p(c).getBoolean("sideways", true)
    fun setSideways(c: Context, v: Boolean) = p(c).edit().putBoolean("sideways", v).apply()
    val COLOURS = listOf(0xFFFFFFFF, 0xFFFF9F0A, 0xFFFF453A, 0xFF30D158, 0xFF0A84FF, 0xFFFF6BD6).map { Color(it) }
}

internal class StandByOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry
    fun start() { ctl.performRestore(Bundle()); reg.currentState = Lifecycle.State.RESUMED }
    fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
}

/** The advanced cover window: a full-screen black overlay that swipes between the chosen pages; a double tap closes it. */
object StandBy {
    private var view: ComposeView? = null
    private var owner: StandByOwner? = null
    private var host: Context? = null
    /** True when the automatic trigger started it (so the trigger may also close it again). */
    var autoStarted = false
    val showing get() = view != null

    fun toggle(ctx: Context): Boolean = if (showing) { hide(ctx); false } else show(ctx)

    fun show(ctx: Context, auto: Boolean = false): Boolean {
        if (view != null) return true
        val acc = CoverAccessibilityService.instance
        val app = ctx.applicationContext
        // the accessibility layer draws above the bars and the keyboard; otherwise the overlay permission is needed
        if (acc == null && !com.umutk.pulse.Compat.canOverlay(app)) {
            app.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val base: Context = acc ?: app
        val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            val b = CoverStore.bright(app); if (b > 0) screenBrightness = b / 100f
        }
        StandByPrefs.loadCustom(app)
        val o = StandByOwner().also { it.start() }
        val v = ComposeView(base).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            // hides the system bars of the overlay as well
            systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            setContent { StandByUi(app) { hide(app) } }
            // the cover takes the keys: Back does nothing behind it, only the double tap closes it
            isFocusable = true; isFocusableInTouchMode = true
            setOnKeyListener { _, code, _ -> code == android.view.KeyEvent.KEYCODE_BACK }
        }
        view = v; owner = o; host = base; autoStarted = auto
        runCatching { wm.addView(v, lp) }.onFailure { view = null; o.stop(); owner = null; host = null; return false }
        v.post { v.requestFocus() }
        return true
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        runCatching { ((host ?: ctx.applicationContext).getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        owner?.stop(); owner = null; view = null; host = null; autoStarted = false
        // the two tiles re-read their state, so a cover closed by a double tap does not stay lit
        val app = ctx.applicationContext
        runCatching { android.service.quicksettings.TileService.requestListeningState(app, android.content.ComponentName(app, CoverTile::class.java)) }
        runCatching { android.service.quicksettings.TileService.requestListeningState(app, android.content.ComponentName(app, StandByTile::class.java)) }
    }
}

private fun isNight(c: Context): Boolean { val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return StandByPrefs.night(c) && (h >= 22 || h < 6) }

@Composable
private fun StandByUi(ctx: Context, onClose: () -> Unit) {
    val pages = remember { StandByPrefs.pages(ctx).ifEmpty { listOf("blank") } }
    var page by remember { mutableIntStateOf(0) }
    val colour = StandByPrefs.ink(ctx)
    val night = remember { isNight(ctx) }
    val ink = if (night) Color(0xFFB3261E) else colour
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    // burn-in: the whole picture moves a few dp every minute
    val minute = (System.currentTimeMillis() / 60_000L).toInt() + (tick.toInt() * 0)
    val drift = StandByPrefs.drift(ctx)
    val dx = if (drift) ((minute % 5) - 2) else 0
    val dy = if (drift) ((minute % 3) - 1) else 0
    // the curtain: black closes in from the top and the bottom to the middle, then the face fades in; closing runs it backwards
    val curtainOn = remember { StandByPrefs.curtain(ctx) }
    val reveal = remember { androidx.compose.animation.core.Animatable(if (curtainOn) 0f else 1f) }
    val sc = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) { if (curtainOn) reveal.animateTo(1f, androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val closeIt: () -> Unit = { sc.launch { if (curtainOn) reveal.animateTo(0f, androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.FastOutSlowInEasing)); onClose() } }
    val faceAlpha = ((reveal.value - .55f) / .45f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize()
            .drawBehind {
                val half = size.height / 2f * reveal.value
                drawRect(Color.Black, androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Size(size.width, half))
                drawRect(Color.Black, androidx.compose.ui.geometry.Offset(0f, size.height - half), androidx.compose.ui.geometry.Size(size.width, half))
            }
            .pointerInput(pages) { detectTapGestures(onDoubleTap = { closeIt() }) }
            .pointerInput(pages) {
                var acc = 0f
                detectHorizontalDragGestures(onDragStart = { acc = 0f }, onDragEnd = {
                    if (acc < -80f) page = (page + 1) % pages.size else if (acc > 80f) page = (page - 1 + pages.size) % pages.size
                }) { _, d -> acc += d }
            },
    ) {
        AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "standby", modifier = Modifier.fillMaxSize().offset(dx.dp, dy.dp).graphicsLayer { alpha = faceAlpha }) { i ->
            Box(Modifier.fillMaxSize().padding(24.dp).then(if (night) Modifier.background(Color.Transparent) else Modifier)) { PageView(ctx, pages[i.coerceIn(0, pages.lastIndex)], ink, tick) }
        }
        if (pages.size > 1) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.indices.forEach { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(ink.copy(alpha = if (it == page) .8f else .25f))) }
        }
    }
}

@Composable
internal fun PageView(ctx: Context, id: String, ink: Color, tick: Long) {
    // the size of everything on the cover (50 to 130 %)
    val k = StandByPrefs.size(ctx) / 100f
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = k; scaleY = k }) { PageViewBody(ctx, id, ink, tick) }
}

@Composable
private fun PageViewBody(ctx: Context, id: String, ink: Color, tick: Long) {
    if (id.startsWith("duo_")) {
        val land = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val (a, b) = when (id) {
            "duo_clock_battery" -> "clock" to "battery"; "duo_clock_calendar" -> "clock" to "calendar"
            "duo_analog_digital" -> "analog" to "clock"; else -> "music" to "clock"
        }
        if (land) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight()) { Widget(ctx, a, ink, tick) }
            Box(Modifier.weight(1f).fillMaxHeight()) { Widget(ctx, b, ink, tick) }
        } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { Widget(ctx, a, ink, tick) }
            Box(Modifier.weight(1f).fillMaxWidth()) { Widget(ctx, b, ink, tick) }
        }
    } else Widget(ctx, id, ink, tick)
}

@Composable
internal fun Widget(ctx: Context, id: String, ink: Color, tick: Long) = when (id) {
    "analog" -> AnalogFace(ink, tick)
    "date" -> DigitalFace(ctx, ink, tick, withDate = true)
    "calendar" -> MonthFace(ink)
    "battery" -> BatteryFace(ctx, ink, tick)
    "music" -> MusicFace(ctx, ink)
    "seg" -> SegmentedFace(ctx, ink, tick, false)
    "seg_wide" -> SegmentedFace(ctx, ink, tick, true)
    "crop" -> BigCropFace(ctx, tick, StandByPrefs.style(ctx, "crop"))
    "flip" -> RetroFlipFace(ctx, tick, StandByPrefs.style(ctx, "flip"))
    "neon" -> NeonFace(ctx, ink, tick)
    "matrix" -> MatrixFace(ctx, tick)
    "pixel" -> PixelFace(ctx, ink, tick)
    "roman" -> RomanFace(ctx, ink, tick)
    "word" -> WordFace(ctx, ink, tick)
    "solar" -> SolarFace(ctx, tick, StandByPrefs.style(ctx, "solar"))
    "radial" -> RadialFace(ctx, ink, tick)
    "analog_clean" -> AnalogVariantFace(ink, tick, "clean")
    "analog_nomark" -> AnalogVariantFace(ink, tick, "nomark")
    "analog_square" -> AnalogVariantFace(ink, tick, "square")
    "clocks" -> ClockOfClocksFace(ctx, ink, tick)
    "world" -> WorldClockFace(ctx, ink, tick)
    "dementia" -> DementiaFace(ctx, ink, tick)
    "sereno" -> SerenoFace(ctx, ink, tick)
    "blank" -> androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
    "tick_digital" -> TickDigitalFace(ctx, ink, tick)
    "tick_analog" -> TickAnalogFace(ctx, ink, tick)
    "bat_dots" -> BatteryDotsFace(ctx, ink, tick)
    "bat_wave" -> BatteryWaveFace(ctx, ink, tick)
    "charging" -> ChargingFace(ctx, ink, tick)
    "stopwatch" -> StopwatchFace(ink)
    "timer" -> TimerFace(ctx, ink)
    "pomodoro" -> PomodoroFace(ink)
    "quotes" -> QuotesFace(ink, tick)
    "cal_striking" -> StrikingCalendarFace(ink)
    else -> DigitalFace(ctx, ink, tick, withDate = false)
}

/** The clock fonts: the system ones, and the fonts of the iOS StandBy app's clock skins (square segment displays, condensed, serif, neon...). [adv] is the width of a digit in em, so the clock fills its box. */
internal class ClockFont(val id: String, val en: String, val tr: String, val family: FontFamily, val weight: FontWeight, val adv: Float = .5f)

private fun res(r: Int) = FontFamily(androidx.compose.ui.text.font.Font(r))

private val CLOCK_FONTS: List<ClockFont> by lazy {
    listOf(
        // the closest free look to Apple's own digits (San Francisco cannot be shipped); load the real font file as "My font"
        ClockFont("inter", "Apple style", "Apple stili", res(com.umutk.pulse.R.font.inter_regular), FontWeight.Normal, .56f),
        ClockFont("interb", "Apple style bold", "Apple stili kalın", res(com.umutk.pulse.R.font.inter_regular), FontWeight.Bold, .6f),
        ClockFont("light", "Light", "İnce", FontFamily.SansSerif, FontWeight.Light),
        ClockFont("bold", "Bold", "Kalın", FontFamily.SansSerif, FontWeight.Bold),
        ClockFont("rounded", "Rounded", "Yuvarlak", FontFamily.SansSerif, FontWeight.Medium),
        ClockFont("serif", "Serif", "Serif", FontFamily.Serif, FontWeight.Normal),
        ClockFont("mono", "Mono", "Mono", FontFamily.Monospace, FontWeight.Normal, .6f),
        // square, segmented digits like a digital clock
        ClockFont("seg7", "Segment 7", "Segment 7", res(com.umutk.pulse.R.font.sb_segments_7_classic_regular), FontWeight.Normal, .62f),
        ClockFont("seg7b", "Segment 7 bold", "Segment 7 kalın", res(com.umutk.pulse.R.font.sb_segments_7_bold), FontWeight.Normal, .62f),
        ClockFont("seg7r", "Segment 7 round", "Segment 7 yuvarlak", res(com.umutk.pulse.R.font.sb_segments_7_round_regular), FontWeight.Normal, .62f),
        ClockFont("seg8", "Segment 8", "Segment 8", res(com.umutk.pulse.R.font.sb_segments_8_regular), FontWeight.Normal, .62f),
        ClockFont("seg9", "Segment 9", "Segment 9", res(com.umutk.pulse.R.font.sb_segments_9_regular), FontWeight.Normal, .62f),
        ClockFont("seg13", "Segment 13", "Segment 13", res(com.umutk.pulse.R.font.sb_segments_13_regular), FontWeight.Normal, .62f),
        ClockFont("seg13r", "Segment 13 round", "Segment 13 yuvarlak", res(com.umutk.pulse.R.font.sb_segments_13_round), FontWeight.Normal, .62f),
        ClockFont("seg15", "Segment 15 cube", "Segment 15 küp", res(com.umutk.pulse.R.font.sb_segments_15_cube), FontWeight.Normal, .62f),
        ClockFont("seg15l", "Segment 15 line", "Segment 15 çizgi", res(com.umutk.pulse.R.font.sb_segments_15_line), FontWeight.Normal, .62f),
        ClockFont("seg15r", "Segment 15 round", "Segment 15 yuvarlak", res(com.umutk.pulse.R.font.sb_segments_15_round), FontWeight.Normal, .62f),
        ClockFont("seg17", "Segment 17", "Segment 17", res(com.umutk.pulse.R.font.sb_segments_17_regular), FontWeight.Normal, .62f),
        ClockFont("pixel", "Pixel", "Piksel", res(com.umutk.pulse.R.font.sb_segments_23_pixel), FontWeight.Normal, .62f),
        ClockFont("segc", "Segmented classic", "Klasik segment", res(com.umutk.pulse.R.font.sb_segmented_classic), FontWeight.Normal, .62f),
        ClockFont("kode", "Kode mono", "Kode mono", res(com.umutk.pulse.R.font.sb_kode_mono), FontWeight.Normal, .6f),
        ClockFont("bebas", "Bebas", "Bebas", res(com.umutk.pulse.R.font.sb_bebas), FontWeight.Normal, .4f),
        ClockFont("antonio", "Antonio", "Antonio", res(com.umutk.pulse.R.font.sb_antonio), FontWeight.Normal, .42f),
        ClockFont("gothic", "League gothic", "League gothic", res(com.umutk.pulse.R.font.sb_league_gothic), FontWeight.Normal, .36f),
        ClockFont("economica", "Economica", "Economica", res(com.umutk.pulse.R.font.sb_economica), FontWeight.Normal, .4f),
        ClockFont("barlow", "Barlow", "Barlow", res(com.umutk.pulse.R.font.sb_barlow), FontWeight.Normal, .5f),
        ClockFont("montserrat", "Montserrat", "Montserrat", res(com.umutk.pulse.R.font.sb_montserrat), FontWeight.Normal, .58f),
        ClockFont("rubik", "Rubik", "Rubik", res(com.umutk.pulse.R.font.sb_rubik), FontWeight.Normal, .55f),
        ClockFont("saira", "Saira", "Saira", res(com.umutk.pulse.R.font.sb_saira), FontWeight.Normal, .52f),
        ClockFont("lilita", "Lilita", "Lilita", res(com.umutk.pulse.R.font.sb_lilita), FontWeight.Normal, .5f),
        ClockFont("numans", "Numans", "Numans", res(com.umutk.pulse.R.font.sb_numans), FontWeight.Normal, .52f),
        ClockFont("stick", "Stick", "Stick", res(com.umutk.pulse.R.font.sb_stick_no_bills), FontWeight.Normal, .5f),
        ClockFont("tourney", "Tourney", "Tourney", res(com.umutk.pulse.R.font.sb_tourney_regular), FontWeight.Normal, .55f),
        ClockFont("jersey", "Jersey", "Jersey", res(com.umutk.pulse.R.font.sb_jersey10), FontWeight.Normal, .45f),
        ClockFont("hachi", "Hachicro", "Hachicro", res(com.umutk.pulse.R.font.sb_hachicro), FontWeight.Normal, .5f),
        ClockFont("super", "Super power", "Super power", res(com.umutk.pulse.R.font.sb_super_power), FontWeight.Normal, .55f),
        ClockFont("tilt", "Tilt neon", "Tilt neon", res(com.umutk.pulse.R.font.sb_tilt_neon), FontWeight.Normal, .6f),
        ClockFont("neon", "Neonderthaw", "Neonderthaw", res(com.umutk.pulse.R.font.sb_neonderthaw), FontWeight.Normal, .4f),
        ClockFont("playfair", "Playfair", "Playfair", res(com.umutk.pulse.R.font.sb_playfair_display), FontWeight.Normal, .55f),
        ClockFont("bodoni", "Bodoni", "Bodoni", res(com.umutk.pulse.R.font.sb_bodoni_moda), FontWeight.Normal, .55f),
        ClockFont("dmserif", "DM serif", "DM serif", res(com.umutk.pulse.R.font.sb_dm_serif_display), FontWeight.Normal, .55f),
        ClockFont("besley", "Besley", "Besley", res(com.umutk.pulse.R.font.sb_besley), FontWeight.Normal, .55f),
        ClockFont("montagu", "Montagu slab", "Montagu slab", res(com.umutk.pulse.R.font.sb_montagu_slab), FontWeight.Normal, .55f),
        ClockFont("juliett", "Juliett", "Juliett", res(com.umutk.pulse.R.font.sb_juliett), FontWeight.Normal, .5f),
        ClockFont("edu", "Hand", "El yazısı", res(com.umutk.pulse.R.font.sb_edu_sa_beginner), FontWeight.Normal, .5f),
    )
}

internal fun clockFont(id: String): ClockFont =
    if (id == "custom") ClockFont("custom", "My font", "Kendi fontum", StandByPrefs.customFamily ?: FontFamily.SansSerif, FontWeight.Normal, .58f)
    else CLOCK_FONTS.firstOrNull { it.id == id } ?: CLOCK_FONTS[0]

@Composable
private fun DigitalFace(ctx: Context, ink: Color, tick: Long, withDate: Boolean) {
    val h24 = StandByPrefs.h24(ctx); val sec = StandByPrefs.seconds(ctx)
    val now = Date(System.currentTimeMillis() + (tick * 0))
    val text = SimpleDateFormat(if (h24) (if (sec) "HH:mm:ss" else "HH:mm") else (if (sec) "h:mm:ss" else "h:mm"), Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    val cf = clockFont(StandByPrefs.font(ctx)); val fam = cf.family; val wt = cf.weight
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val portrait = maxHeight > maxWidth * 1.1f
        val outline = if (StandByPrefs.outline(ctx)) TextStyle(drawStyle = Stroke(width = 4f)) else TextStyle.Default
        if (portrait) {
            // an upright phone: the hours over the minutes (over the seconds), each as big as the width allows
            val lines = listOf(SimpleDateFormat(if (h24) "HH" else "h", Locale.getDefault()).format(now), SimpleDateFormat("mm", Locale.getDefault()).format(now)) + (if (sec) listOf(SimpleDateFormat("ss", Locale.getDefault()).format(now)) else emptyList())
            val size = minOf(maxWidth.value / (2 * cf.adv), maxHeight.value / lines.size * (if (withDate) .62f else .8f)).coerceAtLeast(24f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                lines.forEach { l -> Text(l, color = ink, fontSize = size.sp, fontFamily = fam, fontWeight = wt, maxLines = 1, softWrap = false, textAlign = TextAlign.Center, style = outline, lineHeight = (size * 1.05f).sp) }
                if (withDate) Text(date, color = ink.copy(alpha = .75f), fontSize = (size * 0.14f).coerceAtLeast(14f).sp, fontFamily = fam, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
            }
        } else {
            // the digits take as much room as the box gives
            val size = minOf(maxWidth.value / (text.length * cf.adv), maxHeight.value * (if (withDate) 0.55f else 0.7f)).coerceAtLeast(24f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text, color = ink, fontSize = size.sp, fontFamily = fam, fontWeight = wt, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    style = if (StandByPrefs.outline(ctx)) TextStyle(drawStyle = Stroke(width = size * .045f)) else TextStyle.Default)
                if (withDate) Text(date, color = ink.copy(alpha = .75f), fontSize = (size * 0.22f).coerceAtLeast(14f).sp, fontFamily = fam, fontWeight = FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun AnalogFace(ink: Color, tick: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + tick * 0 }
    val s = cal.get(Calendar.SECOND); val m = cal.get(Calendar.MINUTE) + s / 60f; val h = (cal.get(Calendar.HOUR) % 12) + m / 60f
    Canvas(Modifier.fillMaxSize()) {
        val c = center; val r = size.minDimension / 2f - 6f
        for (i in 0 until 60) {
            val a = i * PI.toFloat() / 30f; val big = i % 5 == 0
            drawLine(ink.copy(alpha = if (big) .95f else .35f), c + Offset(sin(a), -cos(a)) * (r - (if (big) 26f else 12f)), c + Offset(sin(a), -cos(a)) * r, if (big) 6f else 2f, StrokeCap.Round)
        }
        fun hand(frac: Float, len: Float, w: Float, col: Color) { val a = frac * 2f * PI.toFloat(); drawLine(col, c, c + Offset(sin(a), -cos(a)) * len, w, StrokeCap.Round) }
        hand(h / 12f, r * .5f, 12f, ink); hand(m / 60f, r * .78f, 8f, ink); hand(s / 60f, r * .86f, 3f, Color(0xFFFF9F0A))
        drawCircle(Color(0xFFFF9F0A), 8f, c)
    }
}

@Composable
internal fun MonthFace(ink: Color) {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val lead = (first.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val title = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
        val cells = List(lead) { "" } + (1..days).map { it.toString() }
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { d ->
                    val isToday = d == today.toString()
                    Box(Modifier.weight(1f).padding(2.dp).clip(RoundedCornerShape(50)).background(if (isToday) ink else Color.Transparent).padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
                        Text(d, color = if (isToday) Color.Black else ink, fontSize = 16.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                repeat(7 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BatteryFace(ctx: Context, ink: Color, tick: Long) {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val charging = (i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize(.8f)) {
            val st = 22f; val r = size.minDimension / 2f - st
            drawCircle(ink.copy(alpha = .18f), r, center, style = Stroke(st))
            drawArc(ink, -90f, 360f * level / 100f, false, topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(st, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$level%", color = ink, fontSize = 56.sp, fontWeight = FontWeight.Light)
            if (charging) Text("⚡", color = ink, fontSize = 22.sp)
        }
    }
}

@Composable
private fun MusicFace(ctx: Context, ink: Color) {
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val st by engine.state.collectAsState()
    val cur = st.current
    val art by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, cur?.path) { value = cur?.let { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { trackArt(ctx, it) } }.getOrNull() } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = .12f))) {
            art?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
        }
        Text(cur?.let { it.title ?: it.path.substringAfterLast('/') } ?: tr("Nothing playing", "Çalan bir şey yok"), color = ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 12.dp))
        Text(cur?.shownArtist().orEmpty(), color = ink.copy(alpha = .7f), fontSize = 15.sp, maxLines = 1)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { engine.previous() }, contentAlignment = Alignment.Center) { PIcon("back", ink, 28.dp) }
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(27.dp)).background(ink.copy(alpha = .18f)).clickable { engine.togglePlayPause() }, contentAlignment = Alignment.Center) { PIcon(if (st.isPlaying) "pause" else "play", ink, 30.dp) }
            Box(Modifier.size(44.dp).clickable { engine.next() }, contentAlignment = Alignment.Center) { PIcon("chevron", ink, 28.dp) }
        }
    }
}

/** The advanced cover's settings, shown in the screen cover tool: mode, pages, look and the triggers. */
@Composable
fun StandBySettings(adv: Boolean, onAdv: (Boolean) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember { StandByPrefs.loadCustom(ctx) }
    var pages by remember { mutableStateOf(StandByPrefs.pages(ctx)) }
    var font by remember { mutableStateOf(StandByPrefs.font(ctx)) }
    var colour by remember { mutableIntStateOf(StandByPrefs.colour(ctx)) }
    var h24 by remember { mutableStateOf(StandByPrefs.h24(ctx)) }
    var sec by remember { mutableStateOf(StandByPrefs.seconds(ctx)) }
    var drift by remember { mutableStateOf(StandByPrefs.drift(ctx)) }
    var curtain by remember { mutableStateOf(StandByPrefs.curtain(ctx)) }
    var night by remember { mutableStateOf(StandByPrefs.night(ctx)) }
    var auto by remember { mutableStateOf(StandByPrefs.autoCharge(ctx)) }
    var idle by remember { mutableStateOf(StandByPrefs.idleSec(ctx)) }
    var side by remember { mutableStateOf(StandByPrefs.sideways(ctx)) }
    @Composable fun sw(label: String, on: Boolean, f: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); PSwitch(on, f)
    }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val ink = if (colour >= StandByPrefs.COLOURS.size) Color(StandByPrefs.custom(ctx)) else StandByPrefs.COLOURS[colour]
    // the editor: the page being edited is shown big, exactly as the cover draws it, and everything below changes it at once
    var editing by remember { mutableStateOf(pages.firstOrNull() ?: "blank") }
    var tab by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    var outline by remember { mutableStateOf(StandByPrefs.outline(ctx)) }
    var custom by remember { mutableIntStateOf(StandByPrefs.custom(ctx)) }
    val pickFont = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.openInputStream(uri)?.use { i -> java.io.File(ctx.filesDir, "standby_font").outputStream().use { o -> i.copyTo(o) } } }
            StandByPrefs.loadCustom(ctx)
            if (StandByPrefs.customFamily != null) { font = "custom"; StandByPrefs.setFont(ctx, "custom") } else java.io.File(ctx.filesDir, "standby_font").delete()
            rev++
        }
    }
    val hasFont = editing in setOf("clock", "date", "duo_clock_battery", "duo_clock_calendar", "duo_analog_digital", "duo_music_clock")
    val palette = editing in setOf("crop", "flip", "solar")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        // the page as the cover draws it with the phone lying down and standing up
        val mw = maxWidth
        androidx.compose.runtime.key(rev) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                MiniScreen(mw * .62f, true) { PageView(ctx, editing, ink, tick) }
                MiniScreen(mw * .34f, true, portrait = true) { PageView(ctx, editing, ink, tick) }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val n = STANDBY_PAGES.firstOrNull { it.first == editing }?.second
        Text(n?.let { tr(it.first, it.second) }.orEmpty(), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        // in the swipe of the cover or not
        // the order of the swipe: move the page one place earlier or later
        if (editing in pages && pages.size > 1) {
            val at = pages.indexOf(editing)
            fun move(d: Int) { val to = at + d; if (to in pages.indices) { pages = pages.toMutableList().also { val x = it.removeAt(at); it.add(to, x) }; StandByPrefs.setPages(ctx, pages); rev++ } }
            Box(Modifier.size(34.dp).clickable { move(-1) }, contentAlignment = Alignment.Center) { PIcon("back", if (at > 0) T.text else T.sub.copy(alpha = .4f), 20.dp) }
            Text("${at + 1}", color = T.sub, fontSize = 13.sp)
            Box(Modifier.size(34.dp).clickable { move(1) }, contentAlignment = Alignment.Center) { PIcon("back", if (at < pages.lastIndex) T.text else T.sub.copy(alpha = .4f), 20.dp, Modifier.graphicsLayer { rotationZ = 180f }) }
        }
        PIcon("check", if (editing in pages) T.accent else T.sub, 22.dp, Modifier.padding(end = 8.dp))
        PSwitch(editing in pages, { on -> pages = if (on) (if (editing == "blank") listOf("blank") else (pages - "blank") + editing).distinct() else (pages - editing).ifEmpty { listOf("blank") }; StandByPrefs.setPages(ctx, pages) })
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(tr("Pages", "Sayfalar"), tr("Style", "Stil"), tr("Colour", "Renk"), tr("More", "Diğer")).forEachIndexed { i, label -> PChip(tab == i, label) { tab = i } }
    }
    when (tab) {
        0 -> Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            STANDBY_PAGES.forEach { (id, nm) ->
                val on = id in pages
                Column(Modifier.width(140.dp).clickable { editing = id; rev++ }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        MiniScreen(140.dp, id == editing) { PageView(ctx, id, ink, tick) }
                        // the mark of the pages that are in the cover's swipe
                        if (on) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(RoundedCornerShape(9.dp)).background(com.umutk.pulse.Look.accent), contentAlignment = Alignment.Center) { PIcon("check", Color.White, 12.dp) }
                    }
                    Text(tr(nm.first, nm.second), color = if (id == editing) T.text else T.sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        1 -> Column {
            if (editing == "crop") PaletteRow(CROP_STYLES.size, StandByPrefs.style(ctx, "crop"), { StandByPrefs.setStyle(ctx, "crop", it); rev++ }) { i, sel -> MiniScreen(110.dp, sel) { BigCropFace(ctx, tick, i) } }
            if (editing == "flip") PaletteRow(FLIP_STYLES.size, StandByPrefs.style(ctx, "flip"), { StandByPrefs.setStyle(ctx, "flip", it); rev++ }) { i, sel -> MiniScreen(110.dp, sel) { RetroFlipFace(ctx, tick, i) } }
            if (editing == "solar") PaletteRow(SOLAR_STYLES.size, StandByPrefs.style(ctx, "solar"), { StandByPrefs.setStyle(ctx, "solar", it); rev++ }) { i, sel -> MiniScreen(110.dp, sel) { SolarFace(ctx, tick, i) } }
            if (hasFont || editing in setOf("seg", "seg_wide")) sw(tr("Outline only (hollow digits)", "Yalnız kenar çizgisi (içi boş)"), outline) { outline = it; StandByPrefs.setOutline(ctx, it); rev++ }
            if (hasFont) Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // the user's own font file first (Apple's San Francisco cannot be shipped with the app: load the file here)
                Column(Modifier.width(110.dp).clickable { if (StandByPrefs.hasCustom(ctx) && font != "custom") { font = "custom"; StandByPrefs.setFont(ctx, "custom"); rev++ } else pickFont.launch(arrayOf("*/*")) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    MiniScreen(110.dp, font == "custom") {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (StandByPrefs.hasCustom(ctx)) Text("12:45", color = ink, fontSize = 150.sp, fontFamily = clockFont("custom").family, maxLines = 1, softWrap = false)
                            else PIcon("download", ink, 80.dp)
                        }
                    }
                    Text(tr("My font file", "Kendi font dosyam"), color = if (font == "custom") T.text else T.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
                CLOCK_FONTS.forEach { f ->
                    Column(Modifier.width(110.dp).clickable { font = f.id; StandByPrefs.setFont(ctx, f.id); rev++ }, horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniScreen(110.dp, font == f.id) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                val t = remember(tick) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
                                Text(t, color = ink, fontSize = (560f / (t.length * f.adv)).coerceAtMost(330f).sp, fontFamily = f.family, fontWeight = f.weight, maxLines = 1, softWrap = false)
                            }
                        }
                        Text(tr(f.en, f.tr), color = if (font == f.id) T.text else T.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            if (!palette && !hasFont) Text(tr("This page has no style of its own: its colour is the one under Colour.", "Bu sayfanın kendi stili yok: rengi Renk sekmesindekidir."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        }
        2 -> Column {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StandByPrefs.COLOURS.forEachIndexed { i, c ->
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(c).clickable { colour = i; StandByPrefs.setColour(ctx, i); rev++ }, contentAlignment = Alignment.Center) {
                        if (i == colour) Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = .5f)))
                    }
                }
                // the colour mixed by hand
                val mine = StandByPrefs.COLOURS.size
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    .clickable { colour = mine; StandByPrefs.setColour(ctx, mine); rev++ }, contentAlignment = Alignment.Center) {
                    if (colour == mine) Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Color(custom)))
                }
            }
            if (colour == StandByPrefs.COLOURS.size) {
                val c0 = Color(custom)
                listOf(Triple("R", c0.red, 0), Triple("G", c0.green, 1), Triple("B", c0.blue, 2)).forEach { (n, v, k) ->
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, color = T.sub, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                        com.umutk.pulse.PSlider(v * 255f, { x ->
                            val r = if (k == 0) x.toInt() else (c0.red * 255).toInt(); val g = if (k == 1) x.toInt() else (c0.green * 255).toInt(); val b = if (k == 2) x.toInt() else (c0.blue * 255).toInt()
                            custom = (0xFF shl 24) or (r shl 16) or (g shl 8) or b; StandByPrefs.setCustom(ctx, custom); rev++
                        }, 0f..255f) {}
                    }
                }
            }
        }
        else -> Column {
            sw(tr("24-hour clock", "24 saatlik saat"), h24) { h24 = it; StandByPrefs.setH24(ctx, it); rev++ }
            sw(tr("Show seconds", "Saniyeleri göster"), sec) { sec = it; StandByPrefs.setSeconds(ctx, it); rev++ }
            Text("${tr("Size", "Boyut")}: ${StandByPrefs.size(ctx)}%", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            com.umutk.pulse.PSlider((StandByPrefs.size(ctx) - 40) / 100f, { StandByPrefs.setSize(ctx, 40 + (it * 100).toInt()); rev++ }, 0f..1f, onDone = {})
            sw(tr("Curtain animation when it opens and closes", "Açılırken ve kapanırken perde animasyonu"), curtain) { curtain = it; StandByPrefs.setCurtain(ctx, it) }
            sw(tr("Move a little to protect the screen", "Ekranı korumak için hafif kay"), drift) { drift = it; StandByPrefs.setDrift(ctx, it) }
            sw(tr("Night mode: dim red from 22:00 to 06:00", "Gece modu: 22:00-06:00 arası loş kırmızı"), night) { night = it; StandByPrefs.setNight(ctx, it) }
            Text(tr("Triggers", "Tetikleyiciler"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            sw(tr("Start by itself while charging", "Şarjdayken kendiliğinden başla"), auto) { auto = it; StandByPrefs.setAutoCharge(ctx, it) }
            if (auto) sw(tr("Only when the phone lies sideways", "Yalnızca telefon yan yatıkken"), side) { side = it; StandByPrefs.setSideways(ctx, it) }
            Text(tr("Start when the screen is not touched", "Ekrana dokunulmayınca başla"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to tr("Off", "Kapalı"), 15 to "15 s", 30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min").forEach { (v, n) -> PChip(idle == v, n) { idle = v; StandByPrefs.setIdleSec(ctx, v) } }
            }
            Text(tr("The automatic trigger needs the \"Pulse · Screen cover layer\" accessibility service to be on. The Quick Settings tile \"StandBy\" starts it by hand.", "Otomatik tetikleyici için Erişilebilirlik'te \"Pulse · Screen cover layer\" açık olmalı. Hızlı ayarlardaki \"StandBy\" kutucuğu elle başlatır."), color = T.sub, fontSize = 12.sp)
        }
    }
}

/** Quick Settings tile: starts or closes the advanced cover by hand (the manual trigger). */
class StandByTile : android.service.quicksettings.TileService() {
    override fun onStartListening() = paint()
    private fun paint() {
        qsTile?.apply {
            label = "StandBy"
            state = if (StandBy.showing) android.service.quicksettings.Tile.STATE_ACTIVE else android.service.quicksettings.Tile.STATE_INACTIVE
            updateTile()
        }
    }
    override fun onClick() {
        if (!StandBy.toggle(this) && !StandBy.showing && !com.umutk.pulse.Compat.canOverlay(this) && CoverAccessibilityService.instance == null) return
        paint()
        if (StandBy.showing) CoverAccessibilityService.instance?.collapseShade()
    }
}


/** A small black screen showing [content] as the full-screen cover draws it: the content is laid out at a phone-sized landscape screen and scaled down to [width]. */
@Composable
private fun MiniScreen(width: androidx.compose.ui.unit.Dp, picked: Boolean, portrait: Boolean = false, content: @Composable () -> Unit) {
    val ph = if (portrait) width * 16f / 10f else width * 10f / 16f
    val sw = if (portrait) 400.dp else 640.dp; val sh = if (portrait) 640.dp else 400.dp
    // the page lays itself out for the direction of the screen (two widgets side by side or stacked), so the preview says which one it is
    val conf = android.content.res.Configuration(LocalConfiguration.current).apply { orientation = if (portrait) android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE }
    Box(Modifier.size(width, ph).clip(RoundedCornerShape(8.dp)).background(Color.Black)
        .border(if (picked) 2.dp else 1.dp, if (picked) com.umutk.pulse.Look.accent else Color(0x55888888), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        androidx.compose.runtime.CompositionLocalProvider(LocalConfiguration provides conf) {
            Box(Modifier.requiredSize(sw, sh).graphicsLayer { val k = width.toPx() / sw.toPx(); scaleX = k; scaleY = k }.padding(16.dp)) { content() }
        }
    }
}


/** A row of palette pictures; a tap picks one ([onPick]). [tile] draws picture i, selected or not. */
@Composable
private fun PaletteRow(count: Int, picked: Int, onPick: (Int) -> Unit, tile: @Composable (Int, Boolean) -> Unit) {
    var cur by remember { mutableIntStateOf(picked) }
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until count) Box(Modifier.clickable { cur = i; onPick(i) }) { tile(i, i == cur) }
    }
}
