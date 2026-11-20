package com.umutk.pulse.extras.keyboard

/*
 * Tools taken from WM Keyboard (https://github.com/wasi-master/wmkeyboard, MIT License, Copyright (c) 2026 Wasi Master):
 * the unit table of core/tools/UnitConvert.kt and the digit sets of core/script/Numerals.kt, with Pulse's own short names instead of WM's string resources.
 */

/** Units: "10 km to mi=" (or "in", "->") types "= 6.21 mi". */
object KbUnits {
    class U(val symbol: String, val factor: Double, val offset: Double = 0.0)
    private val cats: List<List<U>> = listOf(
        listOf(U("mm", 0.001), U("cm", 0.01), U("m", 1.0), U("km", 1000.0), U("in", 0.0254), U("ft", 0.3048), U("yd", 0.9144), U("mi", 1609.344), U("nmi", 1852.0)),
        listOf(U("mg", 1e-6), U("g", 0.001), U("kg", 1.0), U("t", 1000.0), U("oz", 0.028349523125), U("lb", 0.45359237), U("st", 6.35029318)),
        listOf(U("°c", 1.0, 273.15), U("c", 1.0, 273.15), U("°f", 5.0 / 9.0, 273.15 - 32 * 5.0 / 9.0), U("f", 5.0 / 9.0, 273.15 - 32 * 5.0 / 9.0), U("k", 1.0)),
        listOf(U("m²", 1.0), U("m2", 1.0), U("km²", 1e6), U("km2", 1e6), U("ha", 10_000.0), U("ac", 4046.8564224), U("ft²", 0.09290304), U("ft2", 0.09290304), U("dönüm", 1000.0)),
        listOf(U("ml", 0.001), U("l", 1.0), U("lt", 1.0), U("m³", 1000.0), U("m3", 1000.0), U("tsp", 0.00492892159375), U("tbsp", 0.01478676478125), U("floz", 0.0295735295625), U("cup", 0.2365882365), U("pt", 0.473176473), U("qt", 0.946352946), U("gal", 3.785411784)),
        listOf(U("m/s", 1.0), U("km/h", 1 / 3.6), U("kmh", 1 / 3.6), U("mph", 0.44704), U("kn", 1852.0 / 3600.0)),
        listOf(U("ms", 0.001), U("s", 1.0), U("sn", 1.0), U("min", 60.0), U("dk", 60.0), U("h", 3600.0), U("sa", 3600.0), U("d", 86_400.0), U("gün", 86_400.0), U("wk", 604_800.0), U("hafta", 604_800.0), U("yr", 31_556_952.0), U("yıl", 31_556_952.0)),
        listOf(U("bit", 0.125), U("b", 1.0), U("kb", 1e3), U("mb", 1e6), U("gb", 1e9), U("tb", 1e12), U("kib", 1024.0), U("mib", 1_048_576.0), U("gib", 1_073_741_824.0)),
    )
    private fun find(sym: String): Pair<Int, U>? { val s = sym.lowercase(); cats.forEachIndexed { i, c -> c.firstOrNull { it.symbol == s }?.let { return i to it } }; return null }

    /** The conversion at the end of [before] ("10 km to mi", "5 kg in lb", "30 c -> f"), as the text to type, or null. */
    fun tail(before: String): String? {
        val m = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([^\s0-9]+)\s*(?:to|in|->|>|=>|den|dan)\s*([^\s0-9]+)\s*$""", RegexOption.IGNORE_CASE).find(before) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val (ca, a) = find(m.groupValues[2]) ?: return null
        val (cb, b) = find(m.groupValues[3]) ?: return null
        if (ca != cb) return null
        val r = ((v * a.factor + a.offset) - b.offset) / b.factor
        return KbCalc.format(Math.round(r * 1e4) / 1e4) + " " + m.groupValues[3]
    }
}

/** Other digit sets for the digit keys (a setting; Latin by default). */
object KbNumerals {
    val SETS = listOf("latin" to "0123456789", "arabic" to "٠١٢٣٤٥٦٧٨٩", "persian" to "۰۱۲۳۴۵۶۷۸۹", "devanagari" to "०१२३४५६७८९", "bengali" to "০১২৩৪৫৬৭৮৯", "thai" to "๐๑๒๓๔๕๖๗๘๙")
    fun map(s: String, set: String): String {
        val digits = SETS.firstOrNull { it.first == set }?.second ?: return s
        if (set == "latin") return s
        return buildString { s.forEach { c -> if (c in '0'..'9') append(digits[c - '0']) else append(c) } }
    }
}

/** A one-time code read from a new notification (when the setting is on and Pulse's notification access is given): offered at the right of the bar for two minutes. */
object KbOtp {
    @Volatile var code: String? = null
    @Volatile var at = 0L
    @Volatile var from = ""
    private val near = Regex("(?i)(code|kod|otp|doğrulama|dogrulama|verification|onay|şifre|sifre|password|pin|tek kullanımlık|giriş|login|güvenlik|security)")
    private val digits = Regex("""(?<![0-9])([0-9]{4,8})(?![0-9])""")

    fun onNotification(c: android.content.Context, pkg: String, text: String) {
        if (!KeyboardPrefs.otpCodes(c)) return
        if (!near.containsMatchIn(text)) return
        val m = digits.findAll(text).map { it.value }.firstOrNull { it.length in 4..8 && !(it.length == 4 && (it.startsWith("19") || it.startsWith("20"))) } ?: return
        code = m; at = System.currentTimeMillis(); from = pkg
    }

    fun fresh(): String? = code?.takeIf { System.currentTimeMillis() - at < 120_000L }
}
