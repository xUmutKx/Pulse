package com.umutk.pulse.extras.keyboard

/** The little calculator of the keyboard: "12*5=" types "= 60". Numbers with a comma or a dot, + - * / x × ÷ ^ %, and brackets. */
object KbCalc {
    /** The sum at the end of [before] (a number, then operators and numbers), or null. */
    fun tail(before: String): String? = Regex("""([0-9(][0-9.,()\s]*(?:[-+*/x×÷^%]\s*[0-9.,()]+[0-9.,()\s]*)+)$""").find(before)?.groupValues?.get(1)?.trim()

    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    /** Powers written in small raised digits ("2⁶") read as "2^6". */
    fun norm(t: String): String {
        val b = StringBuilder(); var inSup = false
        for (ch in t) {
            val k = SUP.indexOf(ch)
            if (k >= 0 || (ch == '⁻')) { if (!inSup) b.append('^'); inSup = true; b.append(if (k >= 0) ('0' + k) else '-') }
            else { inSup = false; b.append(ch) }
        }
        return b.toString()
    }

    /** The answer for the sum at the end of [before], written the way the sum is: "12*5" gets "=60", "12 * 5" gets " = 60"; null when there is none. */
    fun answerFor(before: String): String? {
        val nb = norm(before)
        if (nb.trimEnd().endsWith("=")) return null
        val ex = tail(nb) ?: return null
        // a date or a phone number ("2024-11-20", "0532-123") is not a sum: a minus alone counts only with spaces round it
        if (ex.none { it in "+*/x×÷^%" } && !ex.contains(" - ")) return null
        val v = eval(ex) ?: return null
        val r = format(v)
        return if (' ' in ex) (if (before.endsWith(" ")) "= " else " = ") + r else "=" + r
    }

    private class P(val s: String) {
        var i = 0
        fun peek() = if (i < s.length) s[i] else '\u0000'
        fun num(): Double {
            if (peek() == '(') { i++; val v = sum(); if (peek() == ')') i++; return v }
            if (peek() == '-') { i++; return -num() }
            val st = i; while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            return s.substring(st, i).toDouble()
        }
        fun pow(): Double { var v = num(); while (peek() == '^') { i++; v = Math.pow(v, num()) }; return v }
        fun term(): Double { var v = pow(); while (peek() == '*' || peek() == '/' || peek() == '%') { val o = s[i++]; val r = pow(); v = when (o) { '*' -> v * r; '/' -> v / r; else -> v % r } }; return v }
        fun sum(): Double { var v = term(); while (peek() == '+' || peek() == '-') { val o = s[i++]; val r = term(); v = if (o == '+') v + r else v - r }; return v }
    }

    fun eval(src: String): Double? = runCatching {
        val p = P(src.replace('×', '*').replace('÷', '/').replace('x', '*').replace(" ", "").replace(',', '.'))
        val v = p.sum()
        if (p.i < p.s.length || v.isNaN() || v.isInfinite()) null else v
    }.getOrNull()

    fun format(v: Double): String = if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString() else java.math.BigDecimal(v).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
