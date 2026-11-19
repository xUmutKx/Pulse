package com.umutk.pulse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Light syntax colouring for text files, chosen by the extension: comments, strings, numbers, keywords and tags. Same length as the text, so it works as a VisualTransformation. */
object SyntaxColor {
    private val KW = setOf("if", "else", "for", "while", "return", "fun", "val", "var", "class", "object", "def", "import", "from", "package", "public", "private", "static", "void", "int", "new", "null", "true", "false", "None", "True", "False", "in", "is", "not", "and", "or", "try", "catch", "except", "finally", "throw", "function", "const", "let", "async", "await", "when", "do", "case", "switch", "break", "continue", "this", "self", "extends", "implements", "interface", "override", "select", "insert", "update", "delete", "where", "set", "table", "echo", "fi", "then", "done", "elif")
    private val TEXTY = setOf("txt", "log", "lrc", "srt", "csv", "tsv", "vcf", "ics", "eml", "")

    fun supported(ext: String) = ext.lowercase() !in TEXTY

    fun color(text: String, ext: String, dark: Boolean): AnnotatedString {
        val e = ext.lowercase()
        if (!supported(e) || text.length > 200_000) return AnnotatedString(text)
        val cCom = if (dark) Color(0xFF7F8C8D) else Color(0xFF6A737D)
        val cStr = if (dark) Color(0xFF98C379) else Color(0xFF22863A)
        val cNum = if (dark) Color(0xFFD19A66) else Color(0xFFB45F06)
        val cKw = if (dark) Color(0xFFC678DD) else Color(0xFF7B3FB5)
        val cTag = if (dark) Color(0xFF61AFEF) else Color(0xFF0B5CAD)
        val markup = e in setOf("html", "htm", "xml", "svg", "gpx", "kml", "xhtml")
        val hash = e in setOf("py", "sh", "yml", "yaml", "properties", "ini", "cfg", "conf", "gradle", "md") && e != "md"
        val sb = StringBuilder(text)
        return buildAnnotatedString {
            append(text)
            var i = 0; val n = text.length
            fun span(c: Color, a: Int, b: Int) = addStyle(SpanStyle(color = c), a, b.coerceAtMost(n))
            while (i < n) {
                val ch = text[i]
                when {
                    markup && text.startsWith("<!--", i) -> { val j = text.indexOf("-->", i).let { if (it < 0) n else it + 3 }; span(cCom, i, j); i = j }
                    markup && ch == '<' -> { val j = text.indexOf('>', i).let { if (it < 0) n else it + 1 }; span(cTag, i, j); i = j }
                    !markup && ch == '/' && text.startsWith("//", i) -> { val j = text.indexOf('\n', i).let { if (it < 0) n else it }; span(cCom, i, j); i = j }
                    !markup && ch == '/' && text.startsWith("/*", i) -> { val j = text.indexOf("*/", i).let { if (it < 0) n else it + 2 }; span(cCom, i, j); i = j }
                    hash && ch == '#' -> { val j = text.indexOf('\n', i).let { if (it < 0) n else it }; span(cCom, i, j); i = j }
                    e == "json" && ch == '"' -> { var j = i + 1; while (j < n && text[j] != '"' && text[j] != '\n') { if (text[j] == '\\') j++; j++ }; j = (j + 1).coerceAtMost(n); span(if (text.substring(j).trimStart(' ').startsWith(":")) cTag else cStr, i, j); i = j }
                    ch == '"' || ch == '\'' -> { var j = i + 1; while (j < n && text[j] != ch && text[j] != '\n') { if (text[j] == '\\') j++; j++ }; j = (j + 1).coerceAtMost(n); span(cStr, i, j); i = j }
                    ch.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit()) -> { var j = i; while (j < n && (text[j].isLetterOrDigit() || text[j] == '.')) j++; span(cNum, i, j); i = j }
                    ch.isLetter() || ch == '_' -> { var j = i; while (j < n && (text[j].isLetterOrDigit() || text[j] == '_')) j++; if (!markup && text.substring(i, j) in KW) span(cKw, i, j); i = j }
                    else -> i++
                }
            }
        }
    }

    fun transform(ext: String, dark: Boolean) = VisualTransformation { t -> TransformedText(color(t.text, ext, dark), OffsetMapping.Identity) }
}
