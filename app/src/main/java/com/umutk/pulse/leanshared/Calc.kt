package com.umutk.pulse.features

/** The calculator's arithmetic on what the keypad types: + − × ÷ with the usual precedence, a minus in front of a number,
 *  and % ("50%" is 0.5; after + or − it is that share of the left side, so 200+10% is 220). Null when [s] is incomplete or divides by zero. */
internal object Calc {
    fun eval(s: String): Double? {
        var i = 0
        fun peek() = s.getOrNull(i)
        // a number, signed, with an optional %; the flag says it was a bare percentage
        fun factor(): Pair<Double, Boolean>? {
            if (peek() == '−') { i++; return factor()?.let { -it.first to it.second } }
            val st = i
            while (peek()?.let { it.isDigit() || it == '.' } == true) i++
            val n = s.substring(st, i).toDoubleOrNull() ?: return null
            if (peek() == '%') { i++; return n / 100 to true }
            return n to false
        }
        fun term(): Pair<Double, Boolean>? {
            var (v, pct) = factor() ?: return null
            while (peek() == '×' || peek() == '÷') {
                val op = s[i++]
                val r = factor()?.first ?: return null
                v = if (op == '×') v * r else v / r
                pct = false
            }
            return v to pct
        }
        var v = term()?.first ?: return null
        while (peek() == '+' || peek() == '−') {
            val op = s[i++]
            val (r, pct) = term() ?: return null
            val d = if (pct) v * r else r
            v = if (op == '+') v + d else v - d
        }
        return v.takeIf { i == s.length && it.isFinite() }
    }

    /** 12 significant digits, no exponent, the keypad's minus sign. */
    fun show(v: Double): String = BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }.replace('-', '−')
}
