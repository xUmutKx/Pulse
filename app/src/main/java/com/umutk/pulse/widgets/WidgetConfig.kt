package com.umutk.pulse.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.Cfg
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PSlider
import com.umutk.pulse.PTheme
import com.umutk.pulse.Themes
import com.umutk.pulse.WinAlert
import com.umutk.pulse.amoledWindow
import com.umutk.pulse.darkTwin

/**
 * Opened when a widget is added or reconfigured: a window over the home screen in its own look (Material You by default, or a Windows version),
 * with the real widget, the style (plain widgets) or accent (Material, Glass and iOS widgets), and the window look itself. Done saves.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // the window itself is see-through and undimmed: the dialog dims (and on Windows 11 blurs) the home screen
        window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        window.setDimAmount(0f)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        val kind = WidgetData.all.firstOrNull { it.javaClass.name == provider }
        val skin = SkinWidgets.all.firstOrNull { it.javaClass.name == provider }
        val face = FaceWidgets.all.firstOrNull { it.javaClass.name == provider }
        if (kind == null && skin == null && face == null) { finish(); return }
        // the app's accent and look settings, for when Pulse itself has not run yet
        Cfg.load(this)
        val tinted = skin?.skin?.family.let { it == SkinFamily.MATERIAL || it == SkinFamily.GLASS || it == SkinFamily.IOS } || (face != null && face.look in 12..16)

        WidgetLook.load(this)
        if (WidgetLook.windowLook == "blur") window.setBackgroundDrawable(android.graphics.drawable.BitmapDrawable(resources, com.umutk.pulse.features.BlurBackdrop.blurredWallpaper(this, WidgetLook.blur * 40f, (WidgetLook.tint * 255).toInt() shl 24)))
        setContent {
            val c = this@WidgetConfigActivity
            val chosen = remember { mutableStateOf(WidgetStyles.of(c, widgetId)) }
            val own = remember { mutableStateOf(WidgetAccent.own(c, widgetId)) }
            // this widget's own look: saved per appWidgetId at once and the placed widget redrawn; [rev] redraws the preview below
            // opaque art families start fully opaque: only Windows 11 tiles and the text widgets default to the global translucency
            // null = untouched: the slider shows the design's default (XP and 95 opaque) and nothing is stored until it moves
            val opacity = remember { mutableStateOf<Float?>(if (WidgetOver.has(c, widgetId)) WidgetOver.opacity(c, widgetId) else null) }
            val blur = remember { mutableStateOf(WidgetOver.blur(c, widgetId)) }
            val material = remember { mutableStateOf(WidgetOver.material(c, widgetId)) }
            val tint = remember { mutableStateOf(WidgetOver.tint(c, widgetId)) }
            val rev = remember { mutableStateOf(0) }
            fun apply() {
                WidgetOver.save(c, widgetId, opacity.value, blur.value, material.value, tint.value)
                WidgetAccent.save(c, widgetId, own.value)
                if (kind != null) WidgetStyles.save(c, widgetId, chosen.value)
                refresh(kind, skin, face, chosen.value)
                rev.value++
            }
            val win11 = skin?.skin?.family == SkinFamily.WIN11 || (kind != null && (chosen.value == WidgetStyle.WIN11 || chosen.value == WidgetStyle.WIN11_DARK))
            // the window takes the widget's own design: its title bar, caption buttons and buttons
            WidgetLook.load(c)
            val own1 = when { kind != null -> styleLogo(chosen.value); skin != null -> skin.skin.family.theme; else -> FaceWidgets.theme(face!!.look) }
            val design = when (WidgetLook.windowLook) { "own" -> own1; "blur" -> "win11"; else -> "material" }
            val wl = windowFor(c, if (design == "glass" || design == "ios") "material" else design)
            val ring = if (wl.material) wl.accent else wl.text
            CompositionLocalProvider(LocalWindowLook provides wl) {
                WinAlert(
                    onDismissRequest = { finish() },
                    title = { Text("Widget settings") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val accent = own.value.takeIf { it != 0 } ?: WidgetAccent.app(c)
                            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                if (skin != null) {
                                    val bmp = remember(accent, rev.value) { runCatching { Skins.draw(c, skin.skin, SkinData.read(c), accent, widgetId) }.getOrNull() }
                                    if (bmp != null) Image(bmp.asImageBitmap(), skin.skin.label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { FrameLayout(it) },
                                    update = { frame ->
                                        // the real widget; the overlay stops taps from opening Pulse
                                        frame.removeAllViews()
                                        rev.value   // read: redraw when the look changes
                                        val rv = if (kind != null) kind.contentFor(c, widgetId, chosen.value) else face!!.views(c, face.look, accent, widgetId)
                                        val v = try { rv.apply(c, frame) } catch (e: Exception) { null }
                                        if (v != null) frame.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                                        frame.addView(View(c).apply { setOnClickListener {} }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                                    },
                                )
                            }
                            if (kind != null) {
                                // the styles as logos, five to a row, the picked one's name above them
                                Text(chosen.value.label, color = wl.text, fontSize = 14.sp)
                                WidgetStyle.values().toList().chunked(5).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        row.forEach { s -> LogoChip(styleLogo(s), styleShort(s), chosen.value == s, Modifier.weight(1f), ring = ring, text = wl.text) { chosen.value = s; apply() } }
                                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                            if (face == null) {
                                Text("Opacity", color = wl.text, fontSize = 14.sp)
                                val shown = opacity.value ?: if (skin != null && skin.skin.family != SkinFamily.WIN11) 1f else WidgetOver.opacity(c, widgetId, if (skin != null) skin.skin.family.theme else chosen.value.design)
                                PSlider(shown, { opacity.value = it }, 0f..1f) { apply() }
                            }
                            if (win11) {
                                Text("Blur", color = wl.text, fontSize = 14.sp)
                                PSlider(blur.value, { blur.value = it }, 0f..1f) { apply() }
                                if (skin != null || chosen.value == WidgetStyle.WIN11) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("acrylic" to "Acrylic", "mica" to "Mica", "blur" to "Blur").forEach { (m, n) -> PChip(material.value == m, n) { material.value = m; apply() } }
                                    }
                                    if (material.value == "blur") {
                                        Text("Tint", color = wl.text, fontSize = 14.sp)
                                        PSlider(tint.value, { tint.value = it }, 0f..1f) { apply() }
                                    }
                                }
                            }
                            if (tinted) {
                                Text("Accent", color = wl.text, fontSize = 14.sp)
                                // the app's accent (followed), then every swatch, eight to a row
                                (listOf(0) + WidgetAccent.swatches).chunked(8).forEach { row ->
                                    Row(Modifier.fillMaxWidth()) {
                                        row.forEach { col -> Swatch(col, WidgetAccent.app(c), own.value == col, ring, Modifier.weight(1f)) { own.value = col; apply() } }
                                        repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { PButton("Done") { apply(); finishOk() } },
                    dismissButton = { PButton("Cancel") { finish() } },
                )
            }
        }
    }

    /** Redraws this one placed widget with what is saved for its id. */
    private fun refresh(kind: PulseWidget?, skin: SkinWidget?, face: FaceWidget?, s: WidgetStyle) {
        val mgr = AppWidgetManager.getInstance(this)
        when {
            kind != null -> mgr.updateAppWidget(widgetId, kind.contentFor(this, widgetId, s))
            skin != null -> skin.push(this, mgr, intArrayOf(widgetId))
            face != null -> mgr.updateAppWidget(widgetId, face.views(this, face.look, WidgetAccent.of(this, widgetId), widgetId))
        }
    }

    private fun finishOk() {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

/** One accent swatch; 0 is "follow the app", drawn in the app's accent with a refresh mark. */
@Composable
internal fun Swatch(color: Int, app: Int, picked: Boolean, ring: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(1f).padding(2.dp).then(if (picked) Modifier.border(2.dp, ring, CircleShape) else Modifier).padding(4.dp)
        .clip(CircleShape).background(Color(if (color == 0) app else color)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (color == 0) Icon(Icons.Filled.Refresh, "Follow app", tint = Color.Black.copy(alpha = .6f), modifier = Modifier.size(16.dp))
    }
}

/** The window look for the pop-up: Windows 11 is its dark acrylic twin, Material You takes the wallpaper colours in the system's light or dark mode. */
private fun windowFor(c: Context, id: String): PTheme = when (id) {
    "amoled" -> amoledWindow()
    "win11" -> Themes.first { it.id == "win11" }.let { darkTwin(it) ?: it }
    "material" -> {
        fun k(res: Int) = Color(c.getColor(res))
        val dark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (dark) PTheme("material", "Material You", true, k(android.R.color.system_neutral1_900), k(android.R.color.system_neutral1_800), k(android.R.color.system_neutral1_50),
            k(android.R.color.system_neutral2_200), k(android.R.color.system_accent1_200), k(android.R.color.system_neutral2_700), 28.dp, material = true)
        else PTheme("material", "Material You", false, k(android.R.color.system_neutral1_10), k(android.R.color.system_neutral2_50), k(android.R.color.system_neutral1_900),
            k(android.R.color.system_neutral2_700), k(android.R.color.system_accent1_600), k(android.R.color.system_neutral2_200), 28.dp, material = true)
    }
    else -> Themes.firstOrNull { it.id == id } ?: Themes[0]
}
