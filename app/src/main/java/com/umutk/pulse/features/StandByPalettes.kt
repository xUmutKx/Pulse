package com.umutk.pulse.features

import androidx.compose.ui.graphics.Color

/** Colour palettes of the iOS StandBy app's clock skins (read from its Big Crop Clock, Solar Watch and Retro Flip styles). */
class CropStyle(val name: String, val text: List<Color>, val bg: List<Color>, val dark: Boolean)
class SolarStyle(val name: String, val bg: Color, val text: Color, val stops: List<Color>)
class FlipStyle(val name: String, val text: Color, val card: Color, val bg: Color, val glow: List<Color> = emptyList())

val CROP_STYLES = listOf(
    CropStyle("Neon Lights", listOf(Color(0xFF00E676L)), listOf(Color(0xFF000000L), Color(0xFF1B1B1BL)), true),
    CropStyle("Elegant Red", listOf(Color(0xFFFFFFFFL)), listOf(Color(0xFF1A1A1AL), Color(0xFFC0392BL), Color(0xFFE74C3CL), Color(0xFFD91E18L)), false),
    CropStyle("Modern Blue", listOf(Color(0xFFFFFFFFL)), listOf(Color(0xFF0E1621L), Color(0xFF2C3E50L), Color(0xFF3498DBL), Color(0xFF2980B9L)), false),
    CropStyle("Black And White", listOf(Color(0xFFFFFFFFL)), listOf(Color(0xFF000000L)), true),
    CropStyle("Primary Light", listOf(Color(0xFF6750A4L)), listOf(Color(0xFFFFFBFEL)), false),
    CropStyle("Samurai Spirit", listOf(Color(0xFFEF5350L)), listOf(Color(0xFF1A237EL)), false),
    CropStyle("Secondary Light", listOf(Color(0xFF625B71L)), listOf(Color(0xFFFFFBFEL)), false),
    CropStyle("Oceanic Vibes", listOf(Color(0xFF00BFFFL), Color(0xFF1E90FFL)), listOf(Color(0xFF000000L)), true),
    CropStyle("Elegant Gold", listOf(Color(0xFFF5D061L)), listOf(Color(0xFF1D1F20L)), false),
    CropStyle("Ocean Sunset", listOf(Color(0xFFFFFFFFL), Color(0xFF0099CCL)), listOf(Color(0xFFEF5350L), Color(0xFFFFD600L)), false),
    CropStyle("Purple And Pink", listOf(Color(0xFF9C27B0L), Color(0xFFFF4081L)), listOf(Color(0xFF000000L)), true),
    CropStyle("Vibrant Green", listOf(Color(0xFFFFFFFFL)), listOf(Color(0xFF222222L), Color(0xFF27AE60L), Color(0xFF2ECC71L), Color(0xFF1F8A13L)), false),
    CropStyle("Sunset Hues", listOf(Color(0xFFDC143CL), Color(0xFFFF4500L)), listOf(Color(0xFF000000L)), true),
    CropStyle("Cyber Punk", listOf(Color(0xFF29B6F6L)), listOf(Color(0xFF000000L), Color(0xFF212121L)), true),
    CropStyle("Orange And Yellow", listOf(Color(0xFFFF5722L), Color(0xFFFFC107L)), listOf(Color(0xFF000000L)), true),
    CropStyle("Secondary Dark", listOf(Color(0xFFCCC2DCL)), listOf(Color(0xFF1C1B1FL)), true),
    CropStyle("Teal And Cyan", listOf(Color(0xFF009688L), Color(0xFF00BCD4L)), listOf(Color(0xFF000000L)), true),
    CropStyle("Tertiary Light", listOf(Color(0xFF7D5260L)), listOf(Color(0xFFFFFBFEL)), false),
    CropStyle("Neon Night", listOf(Color(0xFF6EFFA0L), Color(0xFF9EFFD6L)), listOf(Color(0xFF1B1B2FL), Color(0xFF2C2C4DL)), true),
    CropStyle("Xiaomi Style2", listOf(Color(0xFFE65A53L), Color(0xFF5C4B51L), Color(0xFF8F6B66L), Color(0xFF4B3832L)), listOf(Color(0xFFEBB3A9L), Color(0xFFB5A99EL), Color(0xFFEFD9CEL), Color(0xFFB39C7DL)), false),
    CropStyle("Starry Sky", listOf(Color(0xFFA8D0E6L), Color(0xFFB6CCFEL)), listOf(Color(0xFF0D0C1DL), Color(0xFF1C1A30L)), true),
    CropStyle("Lunar Eclipse", listOf(Color(0xFFBBDEFBL), Color(0xFF90CAF9L)), listOf(Color(0xFF121212L), Color(0xFF263238L)), true),
    CropStyle("Midnight Glow", listOf(Color(0xFFFFFFFFL)), listOf(Color(0xFF2C3E50L), Color(0xFF34495EL)), true),
    CropStyle("Night Carnival", listOf(Color(0xFFE57373L), Color(0xFFFFD54FL)), listOf(Color(0xFF212121L), Color(0xFF424242L)), true),
    CropStyle("Ethereal Elegance", listOf(Color(0xFF607D8BL)), listOf(Color(0xFF0F2027L)), true),
    CropStyle("Ocean Mist", listOf(Color(0xFF80CBC4L), Color(0xFF26A69AL)), listOf(Color(0xFF0F1D25L), Color(0xFF1A2C33L)), true),
    CropStyle("Royal Night", listOf(Color(0xFF8F00FFL)), listOf(Color(0xFF111725L)), true),
    CropStyle("Ruby Noir", listOf(Color(0xFFE91E63L)), listOf(Color(0xFF212121L)), true),
    CropStyle("Dark Amber", listOf(Color(0xFFFBC02DL), Color(0xFFFFA000L)), listOf(Color(0xFF1E1E1EL), Color(0xFF2C2C2CL)), true),
    CropStyle("Tertiary Dark", listOf(Color(0xFFEFB8C8L)), listOf(Color(0xFF1C1B1FL)), true),
    CropStyle("Enchanted Night", listOf(Color(0xFF8E24AAL), Color(0xFF7B1FA2L)), listOf(Color(0xFF0E121AL), Color(0xFF1D2330L)), true),
    CropStyle("Persian Mosaic", listOf(Color(0xFF3498DBL)), listOf(Color(0xFF273746L)), false),
    CropStyle("Monochrome Minimal", listOf(Color(0xFF333333L), Color(0xFF666666L), Color(0xFF999999L), Color(0xFFCCCCCCL)), listOf(Color(0xFFF2F2F2L)), false),
    CropStyle("Golden Nights", listOf(Color(0xFFFFD700L)), listOf(Color(0xFF1A1B25L)), true),
    CropStyle("Mystical Moon", listOf(Color(0xFF9575CDL), Color(0xFF673AB7L)), listOf(Color(0xFF17171EL), Color(0xFF2A2A37L)), true),
    CropStyle("Brazilian Carnival", listOf(Color(0xFFFFFFFFL)), listOf(Color(0xFFD32F2FL)), false),
    CropStyle("Golden Hour", listOf(Color(0xFFDAA520L), Color(0xFFFFD700L), Color(0xFFFFA500L), Color(0xFFFF8C00L)), listOf(Color(0xFFFAFAD2L), Color(0xFFFFF8DCL), Color(0xFFFAFAD2L), Color(0xFFFFF8DCL)), false),
    CropStyle("Indian Silk", listOf(Color(0xFFE74C3CL)), listOf(Color(0xFF2C3E50L)), true),
)

val SOLAR_STYLES = listOf(
    SolarStyle("Style 1", Color(0xFFA88056L), Color(0xFFFFB54EL), listOf(Color(0xFFDF98CDL), Color(0xFFFFB54EL))),
    SolarStyle("Style 2", Color(0xFF8FA4A8L), Color(0xFFA4DDDDL), listOf(Color(0xFFB5E4ABL), Color(0xFFA4DDDDL))),
    SolarStyle("Style 3", Color(0xFFA1A870L), Color(0xFFCEDE55L), listOf(Color(0xFFE38C63L), Color(0xFFCEDE55L))),
    SolarStyle("Style 4", Color(0xFF9893A8L), Color(0xFFD0C4F9L), listOf(Color(0xFFB5DCE5L), Color(0xFFD0C4F9L))),
    SolarStyle("Style 5", Color(0xFFCD8CD0L), Color(0xFFFFA1FFL), listOf(Color(0xFF3B283CL), Color(0xFFFFA1FFL))),
    SolarStyle("Style 6", Color(0xFFA8396CL), Color(0xFFFF67E6L), listOf(Color(0xFFDC89D4L), Color(0xFFFF67E6L))),
    SolarStyle("Style 7", Color(0xFF7FA0FDL), Color(0xFFB0BEF9L), listOf(Color(0xFFE0E0F7L), Color(0xFFB0BEF9L))),
    SolarStyle("Style 8", Color(0xFF5B6D8AL), Color(0xFFC4E3FFL), listOf(Color(0xFF384A6BL), Color(0xFFC4E3FFL))),
    SolarStyle("Style 10", Color(0xFF625138L), Color(0xFFFFE18CL), listOf(Color(0xFF8D734DL), Color(0xFFFFE18CL))),
    SolarStyle("Style 11", Color(0xFF33A1B4L), Color(0xFFF8D7A4L), listOf(Color(0xFF66C3D8L), Color(0xFFF8D7A4L))),
    SolarStyle("Style 12", Color(0xFF4C4C4CL), Color(0xFFE0E0E0L), listOf(Color(0xFF332727L), Color(0xFFE0E0E0L))),
    SolarStyle("Style 13", Color(0xFF5E7E80L), Color(0xFFDFE1E5L), listOf(Color(0xFF5E7E80L), Color(0xFFDFE1E5L))),
)

val FLIP_STYLES = listOf(
    FlipStyle("Light classic", Color(0xFF1B1B1BL), Color(0xFFF9F9F9L), Color(0xFFE4E4E4L)),
    FlipStyle("Light classic 2", Color(0xFF333333L), Color(0xFFF9F9F9L), Color(0xFFEDEDEDL)),
    FlipStyle("Colored modern dark", Color(0xFFFF9933L), Color(0xFF0A0D38L), Color(0xFF080C38L)),
    FlipStyle("Blue modern dark", Color(0xFF66CCFFL), Color(0xFF0A0D38L), Color(0xFF080C38L)),
    FlipStyle("Gradient 1", Color(0xFFFBF7F2L), Color(0xFF000000L), Color(0xFF000000L), listOf(Color(0xFFFFA2DFL), Color(0xFF000000L), Color(0xFFFFF971L))),
    FlipStyle("Gradient 2", Color(0xFFFFFFFFL), Color(0xFF15202BL), Color(0xFF153F32L), listOf(Color(0xFF1D734DL), Color(0xFF164133L), Color(0xFF0F121AL))),
    FlipStyle("Gradient 3", Color(0xFF112223L), Color(0xFF6CF275L), Color(0xFFFEFFFEL), listOf(Color(0xFF74E6C4L), Color(0xFFFAFFFCL), Color(0xFF6CF275L))),
    FlipStyle("Gradient 4", Color(0xFF0E022FL), Color(0xFFFF6421L), Color(0xFF01002EL), listOf(Color(0xFFFD9D6AL), Color(0xFF8352BBL), Color(0xFF5A6CECL), Color(0xFF01002EL))),
    FlipStyle("Gradient 5", Color(0xFFFFFFFFL), Color(0xFF000000L), Color(0xFF000000L), listOf(Color(0xFFC7D461L), Color(0xFFFFC692L), Color(0xFFAD35FEL))),
    FlipStyle("Classic dark", Color(0xFFF2F2F2L), Color(0xFF1B1B1BL), Color(0xFF000000L)),
    FlipStyle("White on red", Color(0xFFFFFFFFL), Color(0xFFB3261EL), Color(0xFF2B0A08L)),
    FlipStyle("White on blue", Color(0xFFFFFFFFL), Color(0xFF1E4DB7L), Color(0xFF0A1633L)),
    FlipStyle("Purple and white", Color(0xFFFFFFFFL), Color(0xFF6A3DB8L), Color(0xFF1D1033L)),
    FlipStyle("Green and white", Color(0xFFFFFFFFL), Color(0xFF1F7A4DL), Color(0xFF0B2A1BL)),
    FlipStyle("Pastel light", Color(0xFF4A4458L), Color(0xFFF3E8FFL), Color(0xFFE8DEF8L)),
)
