package com.umutk.pulse.extras.calculator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/**
 * The expression evaluator of the scientific and the graphing calculator: + - * / ^ ( ), "!" and "%", functions (sin cos tan asin acos atan sinh cosh tanh
 * ln log sqrt cbrt abs exp floor ceil round), the constants pi and e, the variable x, and implicit products (2x, 3(4+1), 2pi). An expression is compiled once
 * with [compile] and can then be evaluated for many x.
 */
object Sci {
    /** A compiled expression; [at] gives its value for x (and the angle unit: degrees or radians). */
    class Fn internal constructor(private val f: (Double, Boolean) -> Double) {
        fun at(x: Double, deg: Boolean = false): Double = f(x, deg)
    }

    /** The compiled expression, or null when it cannot be read. */
    fun compile(src: String): Fn? = runCatching {
        val p = Parser(src.replace('×', '*').replace('÷', '/').replace('−', '-').replace('π', 'p').replace("pi", "p").replace(" ", "").replace(',', '.').lowercase())
        val f = p.parseAll()
        Fn(f)
    }.getOrNull()

    /** The value of an expression without a variable; null when it cannot be read or is not a number. */
    fun eval(src: String, deg: Boolean = false): Double? = compile(src)?.at(0.0, deg)?.takeIf { it.isFinite() }

    private class Parser(val s: String) {
        var i = 0
        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }

        fun parseAll(): (Double, Boolean) -> Double {
            if (s.isEmpty()) error("empty")
            val e = expr()
            if (i != s.length) error("junk")
            return e
        }

        fun expr(): (Double, Boolean) -> Double {
            var l = term()
            while (true) {
                when {
                    eat('+') -> { val a = l; val b = term(); l = { x, d -> a(x, d) + b(x, d) } }
                    eat('-') -> { val a = l; val b = term(); l = { x, d -> a(x, d) - b(x, d) } }
                    else -> return l
                }
            }
        }

        fun startsPrimary(): Boolean = peek().let { it.isDigit() || it == '.' || it == '(' || it.isLetter() }

        fun term(): (Double, Boolean) -> Double {
            var l = unary()
            while (true) {
                when {
                    eat('*') -> { val a = l; val b = unary(); l = { x, d -> a(x, d) * b(x, d) } }
                    eat('/') -> { val a = l; val b = unary(); l = { x, d -> a(x, d) / b(x, d) } }
                    // 2x, 3(4+1), 2pi: a product without a sign
                    startsPrimary() -> { val a = l; val b = unary(); l = { x, d -> a(x, d) * b(x, d) } }
                    else -> return l
                }
            }
        }

        fun unary(): (Double, Boolean) -> Double {
            if (eat('-')) { val a = unary(); return { x, d -> -a(x, d) } }
            if (eat('+')) return unary()
            return power()
        }

        fun power(): (Double, Boolean) -> Double {
            val base = postfix()
            if (eat('^')) { val ex = unary(); return { x, d -> base(x, d).pow(ex(x, d)) } }
            return base
        }

        fun postfix(): (Double, Boolean) -> Double {
            var v = primary()
            while (true) {
                when {
                    eat('!') -> { val a = v; v = { x, d -> fact(a(x, d)) } }
                    eat('%') -> { val a = v; v = { x, d -> a(x, d) / 100.0 } }
                    else -> return v
                }
            }
        }

        fun primary(): (Double, Boolean) -> Double {
            val c = peek()
            if (c.isDigit() || c == '.') {
                val st = i
                while (peek().isDigit() || peek() == '.') i++
                // 1e5, 2.5e-3 (the letter e is also a constant: it is an exponent only when a digit or a sign follows)
                if (peek() == 'e' && (s.getOrNull(i + 1)?.isDigit() == true || ((s.getOrNull(i + 1) == '-' || s.getOrNull(i + 1) == '+') && s.getOrNull(i + 2)?.isDigit() == true))) {
                    i++; if (peek() == '-' || peek() == '+') i++
                    while (peek().isDigit()) i++
                }
                val n = s.substring(st, i).toDouble()
                return { _, _ -> n }
            }
            if (eat('(')) { val e = expr(); eat(')'); return e }
            if (c == '|') { i++; val e = expr(); eat('|'); return { x, d -> abs(e(x, d)) } }
            if (c.isLetter()) {
                val st = i
                while (peek().isLetter()) i++
                val name = s.substring(st, i)
                when (name) {
                    "x" -> return { x, _ -> x }
                    "p" -> return { _, _ -> PI }
                    "e" -> return { _, _ -> Math.E }
                }
                // a function name is followed by its argument in brackets (or directly: sin30)
                val arg = if (peek() == '(') { i++; val e = expr(); eat(')'); e } else unary()
                return fn(name, arg)
            }
            error("unexpected")
        }

        fun fn(name: String, a: (Double, Boolean) -> Double): (Double, Boolean) -> Double {
            fun rad(v: Double, d: Boolean) = if (d) Math.toRadians(v) else v
            fun deg(v: Double, d: Boolean) = if (d) Math.toDegrees(v) else v
            // the function applied to the argument, which may need to know whether angles are in degrees
            fun f(op: (Double, Boolean) -> Double): (Double, Boolean) -> Double = { x, d -> op(a(x, d), d) }
            return when (name) {
                "sin" -> f { v, d -> sin(rad(v, d)) }
                "cos" -> f { v, d -> cos(rad(v, d)) }
                "tan" -> f { v, d -> tan(rad(v, d)) }
                "asin" -> f { v, d -> deg(asin(v), d) }
                "acos" -> f { v, d -> deg(acos(v), d) }
                "atan" -> f { v, d -> deg(atan(v), d) }
                "sinh" -> f { v, _ -> sinh(v) }
                "cosh" -> f { v, _ -> cosh(v) }
                "tanh" -> f { v, _ -> tanh(v) }
                "ln" -> f { v, _ -> ln(v) }
                "log" -> f { v, _ -> log10(v) }
                "sqrt" -> f { v, _ -> sqrt(v) }
                "cbrt" -> f { v, _ -> cbrt(v) }
                "abs" -> f { v, _ -> abs(v) }
                "exp" -> f { v, _ -> exp(v) }
                "floor" -> f { v, _ -> floor(v) }
                "ceil" -> f { v, _ -> ceil(v) }
                "round" -> f { v, _ -> round(v) }
                else -> error("unknown function $name")
            }
        }
    }

    private fun fact(v: Double): Double {
        if (v < 0 || v != floor(v) || v > 170) return Double.NaN
        var r = 1.0
        for (k in 2..v.toInt()) r *= k
        return r
    }

    /** A result as the display shows it: no ".0", at most 12 significant digits, scientific form for very big or small values. */
    fun show(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "—"
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        val a = abs(v)
        if (a >= 1e15 || a < 1e-9) return "%.9g".format(v).replace(Regex("\\.?0+(?=e)"), "")
        return java.math.BigDecimal(v).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
    }
}
