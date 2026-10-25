package com.umutk.pulse.extras.palette.core.palette

/** Curated moods. Every entry is a real, hand-checked ramp — no random hues. */
object MoodPresets {

    val all: List<ColorPalette> = listOf(
        p("Lavender", "mood", 0xFF3C2F52, 0xFF5B4B79, 0xFF8B7AAE, 0xFFC9BBE0, 0xFFEDE6F7),
        p("Forest", "mood", 0xFF1B2B22, 0xFF2F4A38, 0xFF5A7F5F, 0xFF9CBF9A, 0xFFE2EEDD),
        p("Ocean", "mood", 0xFF0B2432, 0xFF14495F, 0xFF2E7E96, 0xFF7FBFCF, 0xFFDDF0F4),
        p("Nature", "mood", 0xFF33291E, 0xFF5C4A32, 0xFF8C7A4F, 0xFFBFB183, 0xFFEFE9D4),
        p("Minimal", "mood", 0xFF1A1A1A, 0xFF4A4A4A, 0xFF8A8A8A, 0xFFC9C9C9, 0xFFF2F2F2),
        p("Cyberpunk", "mood", 0xFF0D0221, 0xFF261447, 0xFFFF2A6D, 0xFF05D9E8, 0xFFD1F7FF),
        p("Vintage", "mood", 0xFF3E2C29, 0xFF7A4E3B, 0xFFB98056, 0xFFDCB68A, 0xFFF3E4CE),
        p("Pastel", "mood", 0xFFB5B9E8, 0xFFC9E4DE, 0xFFF7D9C4, 0xFFFAEDCB, 0xFFF2C6DE),
        p("Cream", "mood", 0xFF4B4034, 0xFF8A7A64, 0xFFC0AF97, 0xFFE4D8C6, 0xFFFBF5EC),
        p("Monochrome", "mood", 0xFF000000, 0xFF3D3D3D, 0xFF7A7A7A, 0xFFB8B8B8, 0xFFFFFFFF),
        p("Sakura", "mood", 0xFF4A2C3A, 0xFF8C5570, 0xFFD087A5, 0xFFF2C2D3, 0xFFFCEBF1),
        p("Sunset", "mood", 0xFF2B1B33, 0xFF7A2E4E, 0xFFD9534F, 0xFFF2A65A, 0xFFFFE0B5),
        p("Porcelain", "mood", 0xFFD8D2C8, 0xFFE6E1D8, 0xFFF0ECE4, 0xFFF8F5F0, 0xFFFDFCFA),
        p("Mint", "mood", 0xFF16332B, 0xFF215145, 0xFF3E8E75, 0xFF8FCBB8, 0xFFE3F5EE),
        p("Coral Reef", "mood", 0xFF29191C, 0xFF6B3138, 0xFFC15C5C, 0xFFE8968A, 0xFFFBE4DC),
        p("Slate", "mood", 0xFF1E2328, 0xFF3A424A, 0xFF66727D, 0xFFA3AEB6, 0xFFE7EBEE),
        p("Blush", "mood", 0xFF3A2530, 0xFF6B3F52, 0xFFB2748C, 0xFFDCB4C4, 0xFFF6E6ED),
        p("Desert", "mood", 0xFF3D2E1F, 0xFF6E543A, 0xFFAD8558, 0xFFD8B78C, 0xFFF2E2C9),
        p("Midnight", "mood", 0xFF06090F, 0xFF141B29, 0xFF29405E, 0xFF5C7EA3, 0xFFB9CBDD),
        p("Water Lily", "mood", 0xFF2E3436, 0xFF4D5457, 0xFF6E7679, 0xFF8B95A1, 0xFFD9CFE0),
        p("Dusty Rose", "mood", 0xFF3A2A2C, 0xFF6E4F52, 0xFF9C7B7A, 0xFFBFA39D, 0xFFE3D4CC),
        p("Wisteria", "mood", 0xFF2A1E3D, 0xFF4E3A6B, 0xFF8267A8, 0xFFB6A0D9, 0xFFEDE3F7),
        p("Peach Fuzz", "mood", 0xFF4A2E22, 0xFF8C5A42, 0xFFD98E6B, 0xFFF2B991, 0xFFFCE3D0),
        p("Terracotta", "mood", 0xFF3A1F16, 0xFF6E3423, 0xFFB25839, 0xFFD98A65, 0xFFF0C4A8),
        p("Moss", "mood", 0xFF232B1D, 0xFF3E4B30, 0xFF6B7F4E, 0xFFA3B67C, 0xFFDDE6C4),
        p("Champagne", "mood", 0xFF3D3424, 0xFF6E5F3F, 0xFFA68E5E, 0xFFD4BF8E, 0xFFF6ECD4),
        p("Amber Glow", "mood", 0xFF3A2410, 0xFF6E4A1A, 0xFFC17A1F, 0xFFE8A947, 0xFFFCDDA0),
        p("Cobalt", "mood", 0xFF0C1A3A, 0xFF16326E, 0xFF2255B0, 0xFF5C8AE0, 0xFFC2D7F7),
        p("Arctic", "mood", 0xFF1C2830, 0xFF3A5566, 0xFF6E97A8, 0xFFAFD1DC, 0xFFEAF6F8),
        p("Autumn", "mood", 0xFF33170F, 0xFF6E2E17, 0xFFB2531F, 0xFFD98A3D, 0xFFF2C879),
        p("Steel", "mood", 0xFF20242B, 0xFF3E454F, 0xFF636D78, 0xFF9AA5AF, 0xFFDDE2E7),
        p("Plum", "mood", 0xFF2A0F26, 0xFF551C4C, 0xFF8C3A7D, 0xFFBB6FAD, 0xFFE8C6DE),
        p("Honeydew", "mood", 0xFF2A331A, 0xFF4E5E2E, 0xFF8AA34F, 0xFFC3D68C, 0xFFEFF5D9),
        p("Rosewood", "mood", 0xFF33161B, 0xFF5E2932, 0xFF8C4650, 0xFFB87F84, 0xFFE3C6C8),
        p("Seafoam", "mood", 0xFF12302C, 0xFF1F5750, 0xFF3E9086, 0xFF87C4BA, 0xFFDCF0EA),
        p("Lilac Mist", "mood", 0xFF2B2733, 0xFF4E4863, 0xFF817A9E, 0xFFB6AFCC, 0xFFE9E5F2),
        // --- developer palettes ---
        p("Gruvbox", "dev", 0xFF282828, 0xFF504945, 0xFFD79921, 0xFF98971A, 0xFFEBDBB2),
        p("Nord", "dev", 0xFF2E3440, 0xFF434C5E, 0xFF5E81AC, 0xFF88C0D0, 0xFFECEFF4),
        p("Tokyo Night", "dev", 0xFF1A1B26, 0xFF292E42, 0xFF7AA2F7, 0xFFBB9AF7, 0xFFC0CAF5),
        p("Catppuccin", "dev", 0xFF1E1E2E, 0xFF45475A, 0xFFCBA6F7, 0xFFF5C2E7, 0xFFCDD6F4),
        p("Dracula", "dev", 0xFF282A36, 0xFF44475A, 0xFFBD93F9, 0xFFFF79C6, 0xFFF8F8F2),
        p("Solarized", "dev", 0xFF002B36, 0xFF073642, 0xFF268BD2, 0xFF2AA198, 0xFFFDF6E3),
        p("Rose Pine", "dev", 0xFF191724, 0xFF26233A, 0xFFEB6F92, 0xFFEBBCBA, 0xFFE0DEF4),
        p("Everforest", "dev", 0xFF2D353B, 0xFF475258, 0xFFA7C080, 0xFFDBBC7F, 0xFFD3C6AA),
        p("Ayu", "dev", 0xFF0F1419, 0xFF1F2430, 0xFFFFB454, 0xFF39BAE6, 0xFFE6E1CF),
        p("Kanagawa", "dev", 0xFF1F1F28, 0xFF2A2A37, 0xFF7E9CD8, 0xFFE6C384, 0xFFDCD7BA),
        // --- OEM design languages ---
        p("Material", "oem", 0xFF1C1B1F, 0xFF49454F, 0xFF6750A4, 0xFFD0BCFF, 0xFFEADDFF),
        p("Apple", "oem", 0xFF1C1C1E, 0xFF48484A, 0xFF0A84FF, 0xFF64D2FF, 0xFFF2F2F7),
        p("Nothing", "oem", 0xFF000000, 0xFF1F1F1F, 0xFF4A4A4A, 0xFFD71921, 0xFFFFFFFF),
        p("Samsung", "oem", 0xFF11203E, 0xFF1B3A6B, 0xFF3E7BFA, 0xFF9EC4FF, 0xFFEAF1FF),
        p("Pixel", "oem", 0xFF202124, 0xFF3C4043, 0xFF1A73E8, 0xFFEA4335, 0xFFF1F3F4),
        p("One UI Cream", "oem", 0xFF3A322C, 0xFF6E6055, 0xFFA79483, 0xFFD8C9B8, 0xFFF7F0E6),
        p("Google", "oem", 0xFF174EA6, 0xFF1A73E8, 0xFF34A853, 0xFFFBBC05, 0xFFEA4335),
        p("MIUI", "oem", 0xFF1A1A1A, 0xFF3D3D3D, 0xFFFF6900, 0xFFFF9A3E, 0xFFF5F5F5),
        p("ColorOS", "oem", 0xFF121317, 0xFF2E3038, 0xFF1DB8A8, 0xFF5FE0CE, 0xFFEFFCFA)
    )

    val moods get() = all.filter { it.source == "mood" }
    val dev get() = all.filter { it.source == "dev" }
    val oem get() = all.filter { it.source == "oem" }

    fun byName(name: String) = all.firstOrNull { it.name.equals(name, true) }

    private fun p(name: String, src: String, vararg c: Long) =
        ColorPalette(id = "preset_" + name.lowercase().replace(' ', '_'),
            name = name, colors = c.map { it.toInt() }, source = src)
}
