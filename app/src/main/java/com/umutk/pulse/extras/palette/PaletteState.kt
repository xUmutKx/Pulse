package com.umutk.pulse.extras.palette

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.IconEngine
import com.umutk.pulse.extras.palette.core.icon.IconOverride
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import com.umutk.pulse.extras.palette.core.palette.MoodPresets
import com.umutk.pulse.extras.palette.core.palette.PaletteExtractor
import com.umutk.pulse.extras.palette.core.palette.PaletteGenerator
import com.umutk.pulse.extras.palette.core.palette.closestTo
import com.umutk.pulse.extras.palette.core.wallpaper.WallpaperGenerator
import com.umutk.pulse.extras.palette.iconpack.IconPackExporter
import com.umutk.pulse.extras.palette.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportState(val running: Boolean = false, val done: Int = 0, val total: Int = 0, val message: String? = null, val uri: Uri? = null, val isError: Boolean = false)

/**
 * Everything the Palette pages share: the look in use (every change goes through [update], which keeps the undo history and saves it), the app list, the
 * per-app rules, the saved palettes and looks, the picture that is the preview wallpaper, and the state of an export. Plain Compose state, no ViewModel.
 */
class PaletteState(val ctx: Context, private val scope: CoroutineScope) {
    val engine: IconEngine = PaletteStudio.engine(ctx)

    var spec by mutableStateOf(PaletteStore.active(ctx)); private set
    var apps by mutableStateOf(emptyList<AppInfo>()); private set
    var overrides by mutableStateOf(PaletteStore.overrides(ctx)); private set
    var wallpaper by mutableStateOf<Bitmap?>(null); private set
    var savedPalettes by mutableStateOf(PaletteStore.palettes(ctx)); private set
    var savedLooks by mutableStateOf(PaletteStore.looks(ctx)); private set
    var suggested by mutableStateOf(emptyList<ColorPalette>()); private set
    var export by mutableStateOf(ExportState()); private set
    var note by mutableStateOf("")
    var undoCount by mutableIntStateOf(0); private set
    var redoCount by mutableIntStateOf(0); private set

    private val undoStack = ArrayDeque<ThemeSpec>()
    private val redoStack = ArrayDeque<ThemeSpec>()
    private var lastLabel = ""
    private var lastAt = 0L

    init {
        scope.launch {
            apps = withContext(Dispatchers.IO) { PaletteStudio.apps(ctx).load() }
            withContext(Dispatchers.Default) { engine.prepareLuminanceRanks(apps) }
        }
    }

    // ---- the look -----------------------------------------------------------------------------------------------------------------------------
    /** The single way to change the look: history (changes of one slider within a moment count as one step), save, widgets. */
    fun update(label: String = "edit", transform: (ThemeSpec) -> ThemeSpec) {
        val before = spec
        val after = transform(before)
        if (after == before) return
        val now = System.currentTimeMillis()
        if (!(label == lastLabel && now - lastAt < 700)) { undoStack.addLast(before); if (undoStack.size > 80) undoStack.removeFirst() }
        lastLabel = label; lastAt = now
        redoStack.clear(); undoCount = undoStack.size; redoCount = 0
        spec = after
        scope.launch(Dispatchers.Default) {
            PaletteStore.saveActive(ctx, after)
            if (after.palette != before.palette || after.widgetStyle != before.widgetStyle || after.widgetLabelVisible != before.widgetLabelVisible || after.wallpaperStyle != before.wallpaperStyle) WidgetRefresher.refresh(ctx, after)
        }
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(spec); spec = prev; lastLabel = ""
        undoCount = undoStack.size; redoCount = redoStack.size
        scope.launch(Dispatchers.Default) { PaletteStore.saveActive(ctx, prev) }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(spec); spec = next; lastLabel = ""
        undoCount = undoStack.size; redoCount = redoStack.size
        scope.launch(Dispatchers.Default) { PaletteStore.saveActive(ctx, next) }
    }

    /** One tap out of a mangled look: every setting back to its default, the palette stays. */
    fun resetTheme() {
        undoStack.addLast(spec); redoStack.clear(); undoCount = undoStack.size; redoCount = 0
        spec = ThemeSpec(palette = spec.palette)
        scope.launch(Dispatchers.Default) { PaletteStore.saveActive(ctx, spec) }
    }

    // ---- palettes -----------------------------------------------------------------------------------------------------------------------------
    private fun suggestFrom(p: ColorPalette) { suggested = MoodPresets.all.closestTo(p, 6) }

    fun applyPalette(p: ColorPalette) = update("palette") { it.copy(palette = p) }
    fun generatePalette(h: PaletteGenerator.Harmony = PaletteGenerator.Harmony.ANALOGOUS, count: Int = spec.palette.size) = update("generate") { it.copy(palette = PaletteGenerator.generate(PaletteGenerator.Recipe(harmony = h, count = count))) }
    fun paletteFromSeed(color: Int) = update("seed") { it.copy(palette = PaletteGenerator.fromSeedColor(color, it.palette.size.coerceAtLeast(3))) }

    fun paletteFromWallpaper() { scope.launch { val p = withContext(Dispatchers.IO) { PaletteExtractor.fromWallpaper(ctx, spec.palette.size) }; if (p != null) { update("wallpaper") { it.copy(palette = p) }; suggestFrom(p) } else note = "no-wallpaper" } }
    fun paletteFromMaterialYou() { scope.launch { val p = withContext(Dispatchers.IO) { PaletteExtractor.materialYou(ctx, spec.palette.size) }; if (p != null) update("material you") { it.copy(palette = p) } else note = "no-you" } }

    fun paletteFromImage(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            val bmp = decode(uri, 1024) ?: return@launch
            wallpaper = bmp
            val p = PaletteExtractor.fromBitmap(bmp, spec.palette.size)
            withContext(Dispatchers.Main) { update("image") { it.copy(palette = p) }; suggestFrom(p) }
        }
    }

    /** A photo of a home screen: its grid of colours becomes the palette and the Nth app gets the Nth colour. */
    fun paletteFromGrid(uri: Uri, columns: Int = 5, rows: Int = 6, order: PaletteExtractor.GridOrder = PaletteExtractor.GridOrder.READING) {
        scope.launch { PaletteExtractor.fromGrid(ctx, uri, columns, rows, order)?.let { p -> update("home screen photo") { it.copy(palette = p, assignment = ColorAssignment.GRID) } } }
    }

    fun savePalette(p: ColorPalette) { savedPalettes = (savedPalettes.filter { it.id != p.id } + p); PaletteStore.savePalettes(ctx, savedPalettes) }
    fun deletePalette(id: String) { savedPalettes = savedPalettes.filter { it.id != id }; PaletteStore.savePalettes(ctx, savedPalettes) }

    fun setColor(index: Int, color: Int) = update("color") { s ->
        val l = s.palette.colors.toMutableList(); if (index in l.indices) l[index] = color else l.add(color); s.copy(palette = s.palette.copy(colors = l))
    }
    fun addColor(color: Int) = update("add colour") { s -> s.copy(palette = s.palette.copy(colors = s.palette.colors + color)) }
    fun removeColor(index: Int) = update("remove colour") { s -> if (s.palette.size <= 2) s else s.copy(palette = s.palette.copy(colors = s.palette.colors.filterIndexed { i, _ -> i != index })) }
    fun resizePalette(count: Int) = update("colour count") { s -> val n = count.coerceIn(2, 12); if (n == s.palette.size) s else s.copy(palette = s.palette.copy(colors = s.palette.expanded(n))) }

    // ---- saved looks --------------------------------------------------------------------------------------------------------------------------
    fun saveCurrentAsLook(name: String) { savedLooks = savedLooks + spec.copy(id = java.util.UUID.randomUUID().toString(), name = name); PaletteStore.saveLooks(ctx, savedLooks) }
    fun deleteLook(id: String) { savedLooks = savedLooks.filter { it.id != id }; PaletteStore.saveLooks(ctx, savedLooks) }

    /** One tap switches the icons, the widgets and the wallpaper (drawn from that look's own palette and style) together. */
    fun applyLook(look: ThemeSpec) {
        update("look: ${look.name}") { look }
        scope.launch(Dispatchers.Default) {
            val dm = ctx.resources.displayMetrics
            val bmp = WallpaperGenerator.generate(look.palette, look.wallpaperStyle, dm.widthPixels, dm.heightPixels, seed = look.seed)
            wallpaper = bmp
            runCatching { WallpaperManager.getInstance(ctx).setBitmap(bmp) }
        }
    }

    // ---- per-app rules ------------------------------------------------------------------------------------------------------------------------
    fun setOverride(o: IconOverride) { overrides = overrides + (o.key to o); PaletteStore.saveOverrides(ctx, overrides) }
    fun clearOverrides() { overrides = emptyMap(); PaletteStore.saveOverrides(ctx, overrides) }

    // ---- export -------------------------------------------------------------------------------------------------------------------------------
    fun exportPack(asProject: Boolean, size: Int = PaletteStore.exportSize(ctx)) {
        if (export.running) return
        scope.launch(Dispatchers.Default) {
            val list = apps
            export = ExportState(running = true, total = list.size, message = "render")
            try {
                val icons = engine.renderAll(list, spec, size, overrides) { d, t -> export = ExportState(running = true, done = d, total = t, message = "render") }
                export = ExportState(running = true, done = icons.size, total = icons.size, message = "pack")
                val r = if (asProject) IconPackExporter.exportPackProject(ctx, icons, spec) else IconPackExporter.exportImages(ctx, icons, spec)
                export = ExportState(false, icons.size, icons.size, r.file.name, r.uri)
            } catch (e: Exception) {
                export = ExportState(message = "error", isError = true)
            }
        }
    }

    fun clearExport() { export = ExportState() }
    fun clearCache() { scope.launch(Dispatchers.IO) { PaletteStudio.engine(ctx).cache.clearAll() } }

    // ---- wallpaper and widget picture ---------------------------------------------------------------------------------------------------------
    fun setWallpaper(bmp: Bitmap?) { wallpaper = bmp }

    fun generateWallpaper(w: Int, h: Int) {
        val s = spec
        scope.launch(Dispatchers.Default) { wallpaper = WallpaperGenerator.generate(s.palette, s.wallpaperStyle, w, h, seed = s.seed) }
    }

    fun applyAsDeviceWallpaper() { val b = wallpaper ?: return; scope.launch(Dispatchers.IO) { runCatching { WallpaperManager.getInstance(ctx).setBitmap(b) } } }

    /** The picture written where the system's own crop-and-set screen can read it. */
    suspend fun cacheWallpaperForCrop(): Uri? = withContext(Dispatchers.IO) {
        val b = wallpaper ?: return@withContext null
        val f = java.io.File(java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }, "wallpaper_crop_source.png")
        runCatching { f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }.onFailure { return@withContext null }
        androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    fun setWidgetPhoto(bmp: Bitmap?) {
        scope.launch(Dispatchers.IO) { WidgetRefresher.setPhoto(ctx, bmp) }
        if (bmp != null) update("widget photo") { it.copy(widgetStyle = WidgetStyle.PHOTO) }
    }

    fun setWallpaperVideo(uri: Uri?) {
        if (uri != null) runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        WidgetRefresher.setWallpaperVideo(ctx, uri, enabled = uri != null)
    }

    fun setUseVideoWallpaper(on: Boolean) { WidgetRefresher.setWallpaperVideo(ctx, WidgetRefresher.currentVideoUri(ctx), on) }

    /** A photo shrunk while it is read, so a full-size picture cannot run the phone out of memory. */
    fun decode(uri: Uri, max: Int): Bitmap? {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
        var s = 1
        while (b.outWidth / (s * 2) >= max || b.outHeight / (s * 2) >= max) s *= 2
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }
}
