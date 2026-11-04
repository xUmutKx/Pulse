package com.umutk.pulse.extras.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCheck

private val CHECK = Regex("^\\s*(?:[-*] )?\\[( |x|X)] (.*)$")

/** Markdown-lite: "#" and "##" headings, "-" bullets, **bold**, and "[ ]" / "[x]" checklist lines that tick on tap. */
@Composable
fun NoteBody(body: String, size: Int, ink: Color, accent: Color, onBody: (String) -> Unit) {
    val lines = body.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { i, raw ->
            val line = raw.trimEnd()
            val check = CHECK.find(line)
            when {
                check != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val on = check.groupValues[1] != " "
                    PCheck(on, { onBody(toggle(lines, i)) })
                    Text(mdInline(check.groupValues[2]), color = if (on) ink.copy(alpha = .5f) else ink, fontSize = size.sp)
                }
                line.startsWith("## ") -> Text(mdInline(line.removePrefix("## ")), color = ink, fontSize = (size + 3).sp, fontWeight = FontWeight.SemiBold)
                line.startsWith("# ") -> Text(mdInline(line.removePrefix("# ")), color = ink, fontSize = (size + 7).sp, fontWeight = FontWeight.Bold)
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•  ", color = accent, fontSize = size.sp)
                    Text(mdInline(line.drop(2)), color = ink, fontSize = size.sp, modifier = Modifier.weight(1f, fill = false))
                }
                line.isBlank() -> Spacer(Modifier.height((size / 2).dp))
                else -> Text(mdInline(line), color = ink, fontSize = size.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The body with the checkbox on line [index] flipped. */
fun toggle(lines: List<String>, index: Int): String = lines.mapIndexed { j, l ->
    if (j != index) l
    else if (l.contains("[ ]")) l.replaceFirst("[ ]", "[x]")
    else l.replaceFirst(Regex("\\[[xX]]"), "[ ]")
}.joinToString("\n")

/** The colour of a highlighter stroke behind text. */
private val HIGHLIGHT = Color(0x80FFEB3B)

/** **bold** and ==highlighted== runs in one line; the markers are removed. */
fun mdInline(s: String): AnnotatedString = buildAnnotatedString {
    var hi = false
    s.split("==").forEachIndexed { h, hpart ->
        if (h > 0) hi = !hi
        var bold = false
        hpart.split("**").forEachIndexed { k, part ->
            if (k > 0) bold = !bold
            if (part.isNotEmpty()) withStyle(SpanStyle(fontWeight = if (bold) FontWeight.Bold else null, background = if (hi) HIGHLIGHT else Color.Unspecified)) { append(part) }
        }
    }
}

/**
 * The editor's view of ==highlights==: the words get the highlighter colour, and the two markers turn invisible. The text keeps its length,
 * so the cursor and the selection stay where they are.
 */
object HighlightView : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val s = text.text
        val b = AnnotatedString.Builder(text)
        val marks = ArrayList<Int>()
        var i = s.indexOf("==")
        while (i >= 0) { marks += i; i = s.indexOf("==", i + 2) }
        var k = 0
        while (k + 1 < marks.size) {
            val a = marks[k]; val c = marks[k + 1]
            b.addStyle(SpanStyle(background = HIGHLIGHT), a + 2, c)
            b.addStyle(SpanStyle(color = Color.Transparent), a, a + 2)
            b.addStyle(SpanStyle(color = Color.Transparent), c, c + 2)
            k += 2
        }
        return androidx.compose.ui.text.input.TransformedText(b.toAnnotatedString(), androidx.compose.ui.text.input.OffsetMapping.Identity)
    }
}

/** The text with every match of [q] in bold accent colour (search highlight). */
fun highlight(text: String, q: String, accent: Color): AnnotatedString {
    if (q.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var at = 0
        while (true) {
            val hit = text.indexOf(q, at, ignoreCase = true)
            if (hit < 0) { append(text.substring(at)); break }
            append(text.substring(at, hit))
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(text.substring(hit, hit + q.length)) }
            at = hit + q.length
        }
    }
}
