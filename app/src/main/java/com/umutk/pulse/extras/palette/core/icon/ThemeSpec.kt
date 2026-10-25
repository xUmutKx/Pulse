package com.umutk.pulse.extras.palette.core.icon

import com.umutk.pulse.extras.palette.core.palette.ColorPalette

enum class IconMode { ORIGINAL, STYLED, TILES }

enum class TileShape { ROUNDED_SQUARE, CIRCLE, SQUIRCLE, PEBBLE, PILL, SUPERELLIPSE, TEARDROP, HEXAGON }

enum class Finish { MATTE, GLOSSY, GLASS, FROSTED, LIQUID_GLASS, CLAY, NEUMORPHIC, METAL }

/** How each app is assigned a colour from the palette. */
enum class ColorAssignment {
    HASH,        // stable pseudo-random per package — the classic aesthetic grid
    DOMINANT,    // nearest palette colour to the app's real brand colour
    ORDER,       // alphabetical cycling, produces gradients across the grid
    CATEGORY,    // apps of the same Play category share a tone
    LUMINANCE,   // brand luminance -> ramp position
    SINGLE,      // everything one colour
    GRID         // colour comes from the matching cell of an uploaded grid image
}

/** Plain-language label for [ColorAssignment] — the enum name is an implementation detail. */
fun ColorAssignment.label(): String = when (this) {
    ColorAssignment.HASH -> "Random per app"
    ColorAssignment.DOMINANT -> "Match brand colour"
    ColorAssignment.ORDER -> "Alphabetical"
    ColorAssignment.CATEGORY -> "By app category"
    ColorAssignment.LUMINANCE -> "By brightness"
    ColorAssignment.SINGLE -> "One colour"
    ColorAssignment.GRID -> "From my home screen"
}

enum class LauncherSkin { PIXEL, SAMSUNG, NOTHING, STOCK }

/** Home-screen widget backgrounds — same palette, different surface treatment. */
enum class WidgetStyle { GRADIENT, SWATCH, GLASS, MINIMAL, PHOTO }

/** Full-bleed wallpapers procedurally generated from the active palette. */
enum class WallpaperStyle { GRADIENT_MESH, ANGLED_BANDS, SOFT_BLOBS, MINIMAL_FADE, AURORA_WAVES, CONFETTI_DOTS }

/**
 * The complete, serialisable description of a theme.
 * This object alone is enough to regenerate every icon deterministically, which
 * is what makes undo/redo, sharing, backup and automation trivial.
 */
data class ThemeSpec(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Untitled theme",
    val palette: ColorPalette = ColorPalette(name = "Lavender", colors = listOf(
        0xFF3C2F52.toInt(), 0xFF5B4B79.toInt(), 0xFF8B7AAE.toInt(),
        0xFFC9BBE0.toInt(), 0xFFEDE6F7.toInt()
    )),

    val mode: IconMode = IconMode.TILES,
    val style: String = "material_you",

    // --- geometry ---
    val shape: TileShape = TileShape.SQUIRCLE,
    val cornerRadius: Float = 0.30f,       // fraction of icon size
    val iconScale: Float = 0.86f,          // tile size inside the 1x1 canvas
    val glyphScale: Float = 0.52f,         // glyph size inside the tile
    val squircleExponent: Float = 4.0f,

    // --- surface ---
    val finish: Finish = Finish.MATTE,
    val opacity: Float = 1f,
    val gradientIntensity: Float = 0f,
    val gradientAngle: Float = 135f,
    val noise: Float = 0f,
    val shadow: Float = 0.22f,
    val innerShadow: Float = 0f,
    val glow: Float = 0f,
    val stroke: Float = 0f,
    val strokeLightness: Float = 0.25f,

    // --- colour behaviour ---
    val assignment: ColorAssignment = ColorAssignment.LUMINANCE,
    val singleColorIndex: Int = 2,
    val hueRetention: Float = 0f,          // ORIGINAL mode: how much brand hue survives
    val chroma: Float = 1f,
    val contrast: Float = 1f,
    val brightness: Float = 0f,
    val randomness: Float = 0.25f,
    val backgroundIndex: Int = -1,          // -1 = auto

    // --- launcher/theme extras ---
    val skin: LauncherSkin = LauncherSkin.PIXEL,
    val gridColumns: Int = 4,
    val tileSpacing: Float = 1f,
    val labelVisible: Boolean = true,
    val dayNight: Boolean = false,
    val seed: Long = 42L,

    // --- widgets & wallpaper ---
    val widgetStyle: WidgetStyle = WidgetStyle.GRADIENT,
    val widgetLabelVisible: Boolean = true,
    val wallpaperStyle: WallpaperStyle = WallpaperStyle.GRADIENT_MESH
) {
    /** Cheap identity used for cache keys — any change invalidates the bitmap. */
    fun renderKey(): Int = listOf(
        palette.colors, mode, style, shape, cornerRadius, iconScale, glyphScale,
        squircleExponent, finish, opacity, gradientIntensity, gradientAngle, noise,
        shadow, innerShadow, glow, stroke, strokeLightness, assignment,
        singleColorIndex, hueRetention, chroma, contrast, brightness, randomness,
        backgroundIndex, seed
    ).hashCode()
}
