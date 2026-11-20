package com.umutk.pulse.ai

/** The sum written in a photographed text: the line with the most operators and digits, read the way people write sums (x for times, a comma for the decimal point, "= result" cut off). */
object OcrMath {
    fun extract(text: String): String? {
        val best = text.lines().map { clean(it) }
            .filter { l -> l.any { it.isDigit() } && l.length >= 3 }
            .maxByOrNull { l -> l.count { it in "+-*/^%()" } * 3 + l.count { it.isDigit() } } ?: return null
        return best
    }

    private fun clean(line: String): String {
        var t = line.substringBefore('=').trim()
        t = t.replace('x', '*').replace('X', '*').replace('×', '*').replace('·', '*').replace('÷', '/').replace(':', '/').replace('−', '-').replace('–', '-').replace('—', '-')
            .replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1').replace('S', '5')
        // 3,5 is a decimal number: a comma between digits with one or two digits after it
        t = Regex("(\\d),(\\d{1,2})(?!\\d)").replace(t) { it.groupValues[1] + "." + it.groupValues[2] }.replace(",", "")
        return t.filter { it.isDigit() || it in "+-*/^%()." }
    }
}
