package com.umutk.pulse.extras.ai.cc

/**
 * Colours of the pill and the chat header for the build, bash, thinking and done states, chosen in Customize.
 * A stored value is "current" (the theme's own colour), "#RRGGBB" from the bar, or an old preset name.
 * "rainbow" cycles through the hues; every other preset is one fixed colour.
 */
object StateColors {
    private val FIXED = mapOf(
        "pink" to 0xFFFF4D9D.toInt(), "green" to 0xFF4CAF50.toInt(), "cyan" to 0xFF3CF0FF.toInt(),
        "blue" to 0xFF2196F3.toInt(), "gold" to 0xFFFFC107.toInt(), "orange" to 0xFFFF9800.toInt(),
        "red" to 0xFFF44336.toInt(), "white" to 0xFFF2F2F5.toInt(),
    )
    private val HEX = Regex("#[0-9A-Fa-f]{6}")

    fun isHex(k: String): Boolean = HEX.matches(k)

    /** The saved form of a picked colour. */
    fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    /** The hue (0..360) of a colour. */
    fun hueOf(argb: Int): Float = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }[0]

    /** The colour for [key]; "current" means [theme], [hue] (0..360) is used only by "rainbow". */
    fun argb(key: String, hue: Float, theme: String): Int {
        val k = if (key == "current") theme else key
        return when {
            isHex(k) -> android.graphics.Color.parseColor(k)
            k == "rainbow" -> android.graphics.Color.HSVToColor(floatArrayOf(hue % 360f, .85f, 1f))
            else -> FIXED[k] ?: FIXED.getValue("pink")
        }
    }
}
