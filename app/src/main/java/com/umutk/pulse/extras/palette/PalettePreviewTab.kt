package com.umutk.pulse.extras.palette

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.PButton
import com.umutk.pulse.extras.palette.core.icon.LauncherSkin
import com.umutk.pulse.extras.palette.core.icon.WallpaperStyle
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle
import com.umutk.pulse.extras.palette.widget.WidgetRefresher
import com.umutk.pulse.extras.palette.widget.WidgetRenderer
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.launch

private fun nice(n: String) = n.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Three pages in one: the home screen, the wallpaper and the widgets, each with only its own controls. */
@Composable
fun PreviewTab(st: PaletteState) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = pageGutter, vertical = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(tab == 0, tr("Home screen", "Ana ekran")) { tab = 0 }
            PChip(tab == 1, tr("Wallpaper", "Duvar kağıdı")) { tab = 1 }
            PChip(tab == 2, tr("Widgets", "Widget'lar")) { tab = 2 }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) { 0 -> HomeTab(st); 1 -> WallpaperTab(st); else -> WidgetsTab(st) }
            Box(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun HomeTab(st: PaletteState) {
    val spec = st.spec
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val gridPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) st.paletteFromGrid(uri) }
    ChipRow { LauncherSkin.entries.forEach { s -> PChip(spec.skin == s, nice(s.name)) { st.update("skin") { it.copy(skin = s) } } } }
    ChipRow {
        ActionChip(tr("Colour icons from my home screen", "İkonları ana ekranımdan renklendir")) { gridPicker.launch(image) }
        ActionChip(if (spec.labelVisible) tr("Hide labels", "Etiketleri gizle") else tr("Show labels", "Etiketleri göster")) { st.update("labels") { it.copy(labelVisible = !it.labelVisible) } }
    }
    HintText(tr("Real generated icons on a launcher mock-up.", "Gerçek üretilmiş ikonlar, başlatıcı taslağında."))
    LauncherPreview(spec = spec, apps = st.apps, engine = st.engine, wallpaper = st.wallpaper, overrides = st.overrides, modifier = Modifier.fillMaxWidth().aspectRatio(0.46f))
}

@Composable
private fun WallpaperTab(st: PaletteState) {
    val spec = st.spec
    val ctx = st.ctx
    val scope = rememberCoroutineScope()
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    var art by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var videoUri by remember { mutableStateOf(WidgetRefresher.currentVideoUri(ctx)) }
    var useVideo by remember { mutableStateOf(WidgetRefresher.useVideoWallpaper(ctx)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { runCatching { st.decode(uri, 1024) }.getOrNull()?.let { st.setWallpaper(it) }; st.paletteFromImage(uri) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { st.setWallpaperVideo(uri); videoUri = uri; useVideo = true } }

    if (confirm) WinAlert(onDismissRequest = { confirm = false }, title = { Text(tr("Set as wallpaper?", "Duvar kağıdı yapılsın mı?")) },
        text = { Text(tr("This replaces your current home screen wallpaper.", "Bu, mevcut ana ekran duvar kağıdınızın yerine geçer.")) },
        confirmButton = { PButton(tr("Set wallpaper", "Duvar kağıdı yap"), compact = true) { confirm = false; st.applyAsDeviceWallpaper() } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç"), compact = true) { confirm = false } })

    st.wallpaper?.let { w -> Image(w.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
    ChipRow {
        ActionChip(tr("Pick wallpaper", "Duvar kağıdı seç")) { picker.launch(image) }
        ActionChip(tr("Wallpaper palette", "Duvar kağıdı paleti")) { st.paletteFromWallpaper() }
        ActionChip(if (art) tr("Hide abstract art", "Soyut sanatı gizle") else tr("Abstract art", "Soyut sanat")) { art = !art }
        ActionChip(if (samples) tr("Hide sample photos", "Örnek fotoğrafları gizle") else tr("Sample photos", "Örnek fotoğraflar")) { samples = !samples }
        if (st.wallpaper != null) ActionChip(tr("Set as wallpaper", "Duvar kağıdı yap")) {
            scope.launch {
                val uri = st.cacheWallpaperForCrop()
                val opened = uri != null && runCatching {
                    val intent = android.app.WallpaperManager.getInstance(ctx).getCropAndSetWallpaperIntent(uri)
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(intent)
                }.isSuccess
                // no crop screen on this phone: a plain confirmation, then the picture goes on as it is
                if (!opened) confirm = true
            }
        }
    }
    if (art) AbstractPaletteArtRow(spec.palette) { b -> st.setWallpaper(b) }
    if (samples) {
        SamplePhotoRow(spec.palette) { b -> st.setWallpaper(b) }
        HintText(tr("Sample photos come from loremflickr.com.", "Örnek fotoğraflar loremflickr.com'dan gelir."))
    }

    SectionTitle(tr("Wallpaper style", "Duvar kağıdı stili"))
    ChipRow { WallpaperStyle.entries.forEach { s -> PChip(spec.wallpaperStyle == s, nice(s.name)) { st.update("wallpaper style") { it.copy(wallpaperStyle = s) } } } }
    ChipRow {
        ActionChip(tr("Generate", "Üret")) { st.generateWallpaper(1080, 2340) }
        ActionChip(tr("Set as live wallpaper", "Canlı duvar kağıdı yap")) {
            runCatching {
                ctx.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, android.content.ComponentName(ctx, com.umutk.pulse.extras.palette.wallpaper.LivePaletteWallpaperService::class.java))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    HintText(tr("The live wallpaper animates in the style above and follows this look by itself.", "Canlı duvar kağıdı yukarıdaki stilde hareket eder ve bu görünümü kendiliğinden izler."))
    ChipRow {
        ActionChip(if (videoUri == null) tr("Pick video", "Video seç") else tr("Change video", "Videoyu değiştir")) { videoPicker.launch(arrayOf("video/*")) }
        if (videoUri != null) PChip(useVideo, if (useVideo) tr("Using video", "Video kullanılıyor") else tr("Use video", "Videoyu kullan")) { useVideo = !useVideo; st.setUseVideoWallpaper(useVideo) }
    }
    HintText(tr("A video plays only as the live wallpaper: muted, looping, stretched to fill the screen.", "Video yalnız canlı duvar kağıdı olarak oynar: sessiz, döngüde, ekranı dolduracak şekilde."))
}

@Composable
private fun WidgetsTab(st: PaletteState) {
    val spec = st.spec
    val image = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    var art by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) runCatching { st.decode(uri, 1024) }.getOrNull()?.let { st.setWidgetPhoto(it) } }
    SectionTitle(tr("Widgets", "Widget'lar"))
    HintText(tr("Add Palette and Palette swatch from the widget list of your launcher. A tap on the square preview picks a photo.", "Başlatıcınızın widget listesinden Palette ve Palette swatch ekleyin. Kare önizlemeye dokunmak fotoğraf seçer."))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WidgetPreview(spec.palette.colors, spec.widgetStyle, 140.dp, 140.dp, st.wallpaper) { photoPicker.launch(image) }
        WidgetPreview(spec.palette.colors, WidgetStyle.SWATCH, 220.dp, 72.dp)
    }
    ChipRow { WidgetStyle.entries.forEach { s -> PChip(spec.widgetStyle == s, nice(s.name)) { st.update("widget style") { it.copy(widgetStyle = s) } } } }
    ChipRow {
        ActionChip(if (spec.widgetLabelVisible) tr("Hide widget label", "Widget etiketini gizle") else tr("Show widget label", "Widget etiketini göster")) { st.update("widget label") { it.copy(widgetLabelVisible = !it.widgetLabelVisible) } }
        ActionChip(tr("Pick photo", "Fotoğraf seç")) { photoPicker.launch(image) }
        if (st.wallpaper != null) ActionChip(tr("Use wallpaper photo", "Duvar kağıdı fotoğrafını kullan")) { st.setWidgetPhoto(st.wallpaper) }
        ActionChip(if (art) tr("Hide abstract art", "Soyut sanatı gizle") else tr("Abstract art", "Soyut sanat")) { art = !art }
        ActionChip(if (samples) tr("Hide sample photos", "Örnek fotoğrafları gizle") else tr("Sample photos", "Örnek fotoğraflar")) { samples = !samples }
    }
    if (art) AbstractPaletteArtRow(spec.palette) { b -> st.setWidgetPhoto(b) }
    if (samples) SamplePhotoRow(spec.palette) { b -> st.setWidgetPhoto(b) }
    HintText(tr("Placed on the home screen the widgets can be resized by long pressing; this only sets what they show.", "Ana ekrana konunca widget'lar uzun basarak yeniden boyutlandırılabilir; burası yalnız ne gösterdiklerini belirler."))
}

/** A live bitmap of the widget background, so a change of style or palette shows at once. */
@Composable
private fun WidgetPreview(colors: List<Int>, style: WidgetStyle, width: Dp, height: Dp, photo: android.graphics.Bitmap? = null, onClick: (() -> Unit)? = null) {
    val density = LocalDensity.current
    val palette = remember(colors) { com.umutk.pulse.extras.palette.core.palette.ColorPalette(colors = colors) }
    val bmp = remember(colors, style, width, height, photo) {
        WidgetRenderer.background(palette, style, with(density) { width.roundToPx() }, with(density) { height.roundToPx() }, with(density) { 16.dp.toPx() }, photo).asImageBitmap()
    }
    Image(bmp, null, contentScale = ContentScale.FillBounds, modifier = Modifier.width(width).height(height).clip(RoundedCornerShape(16.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier))
}
