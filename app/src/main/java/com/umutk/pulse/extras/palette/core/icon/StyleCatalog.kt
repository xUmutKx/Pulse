package com.umutk.pulse.extras.palette.core.icon

enum class GlyphKind { SOLID, OUTLINE, DOT_MATRIX, PIXEL, EXTRUDE, RETRO, DUOTONE, NONE }

/**
 * A style is a declarative recipe, not a code path. Adding a style is one line;
 * the renderer stays generic, which is how 20+ looks stay consistent.
 * [bgTone]/[fgTone] are positions on the palette ramp (0 = darkest, 1 = lightest).
 */
data class IconStyle(
    val id: String,
    val label: String,
    val shape: TileShape = TileShape.SQUIRCLE,
    val corner: Float = 0.30f,
    val finish: Finish = Finish.MATTE,
    val glyph: GlyphKind = GlyphKind.SOLID,
    val glyphScale: Float = 0.52f,
    val gradient: Float = 0f,
    val shadow: Float = 0.18f,
    val stroke: Float = 0f,
    val noise: Float = 0f,
    val innerShadow: Float = 0f,
    val glow: Float = 0f,
    val squircleN: Float = 4f,
    val iconScale: Float = 0.88f,
    val bgTone: Float = 0.25f,
    val fgTone: Float = 0.95f
)

object StyleCatalog {

    val styles: List<IconStyle> = listOf(
        IconStyle("material_you", "Material You", TileShape.SQUIRCLE, 0.34f, Finish.MATTE,
            bgTone = 0.30f, fgTone = 0.96f, glyphScale = 0.50f),
        IconStyle("one_ui", "One UI", TileShape.SQUIRCLE, 0.42f, Finish.GLOSSY,
            gradient = 0.55f, squircleN = 5f, bgTone = 0.35f, fgTone = 0.98f),
        IconStyle("pixel", "Pixel", TileShape.CIRCLE, 0.5f, Finish.MATTE,
            bgTone = 0.22f, fgTone = 0.97f, glyphScale = 0.48f),
        IconStyle("nothing_os", "Nothing OS", TileShape.SQUIRCLE, 0.30f, Finish.MATTE,
            glyph = GlyphKind.DOT_MATRIX, bgTone = 0.05f, fgTone = 0.92f, glyphScale = 0.62f),
        IconStyle("liquid_glass", "Liquid Glass", TileShape.SQUIRCLE, 0.36f, Finish.LIQUID_GLASS,
            gradient = 0.4f, stroke = 0.4f, glyphScale = 0.46f, bgTone = 0.55f, fgTone = 1f),
        IconStyle("apple_glyph", "Apple Glyph", TileShape.SQUIRCLE, 0.32f, Finish.GLOSSY,
            gradient = 0.6f, glyphScale = 0.50f, bgTone = 0.40f, fgTone = 1f),
        IconStyle("material_glass", "Material glass", TileShape.SQUIRCLE, 0.36f, Finish.GLASS,
            gradient = 0.3f, shadow = 0.22f, stroke = 0.45f, glyphScale = 0.50f, bgTone = 0.5f, fgTone = 1f),
        // the Windows looks of Pulse as icons: flat tiles, aero gloss, Luna blue, grey bevel
        IconStyle("win11", "Windows 11", TileShape.SQUIRCLE, 0.22f, Finish.MATTE,
            gradient = 0.18f, shadow = 0.22f, stroke = 0.2f, glyphScale = 0.52f, bgTone = 0.3f, fgTone = 0.98f),
        IconStyle("win10", "Windows 10", TileShape.ROUNDED_SQUARE, 0.02f, Finish.MATTE,
            shadow = 0f, glyphScale = 0.56f, bgTone = 0.32f, fgTone = 1f, iconScale = 0.94f),
        IconStyle("win7", "Windows 7", TileShape.ROUNDED_SQUARE, 0.18f, Finish.GLOSSY,
            gradient = 0.8f, stroke = 0.6f, shadow = 0.3f, glyphScale = 0.5f, bgTone = 0.45f, fgTone = 1f),
        IconStyle("xp", "Windows XP", TileShape.ROUNDED_SQUARE, 0.16f, Finish.GLOSSY,
            gradient = 0.9f, stroke = 0.3f, shadow = 0.25f, glyphScale = 0.5f, bgTone = 0.4f, fgTone = 1f),
        IconStyle("win95", "Windows 95", TileShape.ROUNDED_SQUARE, 0.02f, Finish.METAL,
            stroke = 0.6f, shadow = 0.1f, glyphScale = 0.5f, bgTone = 0.72f, fgTone = 0.08f, iconScale = 0.94f),
        IconStyle("glass", "Glass", TileShape.SQUIRCLE, 0.34f, Finish.GLASS,
            stroke = 0.5f, glyphScale = 0.46f, bgTone = 0.6f, fgTone = 1f),
        IconStyle("frosted", "Frosted Glass", TileShape.SQUIRCLE, 0.34f, Finish.FROSTED,
            noise = 0.35f, stroke = 0.35f, bgTone = 0.62f, fgTone = 0.1f),
        IconStyle("soft_ui", "Soft UI", TileShape.SQUIRCLE, 0.38f, Finish.NEUMORPHIC,
            shadow = 0.05f, innerShadow = 0.2f, bgTone = 0.78f, fgTone = 0.18f),
        IconStyle("clay", "Clay", TileShape.PEBBLE, 0.40f, Finish.CLAY,
            shadow = 0.5f, glyphScale = 0.46f, bgTone = 0.45f, fgTone = 1f),
        IconStyle("outline", "Outline", TileShape.SQUIRCLE, 0.34f, Finish.MATTE,
            glyph = GlyphKind.OUTLINE, stroke = 0.5f, bgTone = 0.92f, fgTone = 0.08f),
        IconStyle("filled", "Filled", TileShape.ROUNDED_SQUARE, 0.28f, Finish.MATTE,
            bgTone = 0.20f, fgTone = 0.98f, glyphScale = 0.56f),
        IconStyle("monochrome", "Monochrome", TileShape.SQUIRCLE, 0.34f, Finish.MATTE,
            bgTone = 0.10f, fgTone = 0.90f),
        IconStyle("pastel", "Pastel", TileShape.SQUIRCLE, 0.40f, Finish.MATTE,
            bgTone = 0.80f, fgTone = 0.15f, shadow = 0.10f),
        IconStyle("gradient", "Gradient", TileShape.SQUIRCLE, 0.34f, Finish.MATTE,
            gradient = 1f, bgTone = 0.35f, fgTone = 1f),
        IconStyle("glyph", "Glyph", TileShape.CIRCLE, 0.5f, Finish.MATTE,
            glyphScale = 0.44f, bgTone = 0.88f, fgTone = 0.10f),
        IconStyle("neumorphism", "Neumorphism", TileShape.CIRCLE, 0.5f, Finish.NEUMORPHIC,
            shadow = 0.02f, bgTone = 0.82f, fgTone = 0.25f),
        IconStyle("retro", "Retro", TileShape.ROUNDED_SQUARE, 0.16f, Finish.MATTE,
            glyph = GlyphKind.RETRO, noise = 0.5f, bgTone = 0.72f, fgTone = 0.12f),
        IconStyle("pixel_art", "Pixel Art", TileShape.ROUNDED_SQUARE, 0.10f, Finish.MATTE,
            glyph = GlyphKind.PIXEL, bgTone = 0.15f, fgTone = 0.95f),
        IconStyle("three_d", "3D", TileShape.SQUIRCLE, 0.34f, Finish.GLOSSY,
            glyph = GlyphKind.EXTRUDE, gradient = 0.5f, shadow = 0.35f, bgTone = 0.40f, fgTone = 1f),
        IconStyle("liquid", "Liquid", TileShape.PEBBLE, 0.46f, Finish.GLOSSY,
            gradient = 0.7f, glyphScale = 0.44f, bgTone = 0.42f, fgTone = 1f),
        IconStyle("minimal", "Minimal", TileShape.SQUIRCLE, 0.36f, Finish.MATTE,
            shadow = 0f, glyphScale = 0.40f, bgTone = 0.90f, fgTone = 0.20f),
        IconStyle("modern", "Modern", TileShape.SUPERELLIPSE, 0.38f, Finish.MATTE,
            gradient = 0.25f, shadow = 0.24f, bgTone = 0.28f, fgTone = 0.98f),
        IconStyle("duotone", "Duotone", TileShape.SQUIRCLE, 0.34f, Finish.MATTE,
            glyph = GlyphKind.DUOTONE, gradient = 0.2f, bgTone = 0.30f, fgTone = 1f)
    )

    val byId: Map<String, IconStyle> = styles.associateBy { it.id }

    fun get(id: String): IconStyle = byId[id] ?: styles.first()
}
