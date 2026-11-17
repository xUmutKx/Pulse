package com.drdisagree.glasslayer.domain.model

data class GlassSettings(
    val toggles: Map<GlassToggle, Boolean> = emptyMap(),
    val levels: Map<GlassLevel, Float> = emptyMap(),
    val style: Map<StyleParam, Float> = emptyMap(),
    val overrides: Map<GlassSurface, Map<StyleParam, Float>> = emptyMap(),
    val performance: PerformanceMode = PerformanceMode.BALANCED,
    val batterySaver: BatterySaverAction = BatterySaverAction.ULTRA,
    val thermal: ThermalAction = ThermalAction.SAVER,
    val batterySaverBackground: BatterySaverBackground = BatterySaverBackground.SEMI_TRANSPARENT
) {
    fun isOn(toggle: GlassToggle): Boolean = toggles[toggle] ?: false

    fun level(level: GlassLevel): Float = levels[level] ?: 0f

    fun style(param: StyleParam): Float = style[param] ?: 0f

    fun override(surface: GlassSurface, param: StyleParam): Float? = overrides[surface]?.get(param)

    fun effective(surface: GlassSurface, param: StyleParam): Float = override(surface, param) ?: style(param)
}
