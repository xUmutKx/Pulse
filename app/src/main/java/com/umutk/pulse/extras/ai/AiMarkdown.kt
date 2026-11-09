package com.umutk.pulse.extras.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr

/** Text cleaned for the bubble: no return characters, no run of empty lines, no spaces at the ends. */
internal fun cleanAnswer(s: String): String = s.replace("\r", "").replace(Regex("\n{3,}"), "\n\n").trim()

private sealed class MdBlock {
    class Para(val lines: List<String>) : MdBlock()
    class Head(val level: Int, val text: String) : MdBlock()
    class Bullet(val items: List<String>, val ordered: Boolean) : MdBlock()
    class Code(val lang: String, val code: String) : MdBlock()
}

/** A small Markdown reader: headings, bullet and numbered lists, fenced code, bold, italic and inline code. Anything else is text. */
private fun parseMd(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = cleanAnswer(src).lines()
    var i = 0
    val para = mutableListOf<String>()
    fun flush() { if (para.isNotEmpty()) { out += MdBlock.Para(para.toList()); para.clear() } }
    while (i < lines.size) {
        val l = lines[i]; val t = l.trimStart()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder(); i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                out += MdBlock.Code(lang, code.toString().trimEnd('\n'))
                i++; continue
            }
            t.isEmpty() -> flush()
            t.startsWith("#") && t.trimStart('#').startsWith(" ") -> {
                flush(); val level = t.takeWhile { it == '#' }.length.coerceIn(1, 3)
                out += MdBlock.Head(level, t.trimStart('#').trim())
            }
            t.startsWith("- ") || t.startsWith("* ") || Regex("""\d+[.)] .*""").matches(t) -> {
                flush()
                val ordered = !(t.startsWith("- ") || t.startsWith("* "))
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val u = lines[i].trimStart()
                    val body = when {
                        u.startsWith("- ") || u.startsWith("* ") -> u.substring(2)
                        Regex("""\d+[.)] .*""").matches(u) -> u.substringAfter(' ')
                        else -> break
                    }
                    items += body; i++
                }
                out += MdBlock.Bullet(items, ordered)
                continue
            }
            else -> para += l
        }
        i++
    }
    flush()
    return out
}

/** Bold (**x**), italic (*x*) and code (`x`) inside one line. */
private fun inlineMd(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > i + 2 -> {
                val e = s.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(s.substring(i + 2, e)) }
                i = e + 2
            }
            s[i] == '`' && s.indexOf('`', i + 1) > i -> {
                val e = s.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(s.substring(i + 1, e)) }
                i = e + 1
            }
            s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ' && s.indexOf('*', i + 1) > i + 1 -> {
                val e = s.indexOf('*', i + 1)
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(s.substring(i + 1, e)) }
                i = e + 1
            }
            else -> { append(s[i]); i++ }
        }
    }
}

private fun copyText(c: Context, text: String) {
    runCatching { (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pulse AI", text)) }
}

/** The answer drawn as Markdown, in the chat's colours. Code blocks scroll sideways and have their own copy button. */
@Composable
internal fun AiMarkdown(text: String, color: Color, modifier: Modifier = Modifier) {
    val c = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMd(text).forEach { b ->
            when (b) {
                is MdBlock.Head -> Text(inlineMd(b.text), color = color,
                    fontSize = when (b.level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp }, fontWeight = FontWeight.SemiBold)
                is MdBlock.Para -> Text(inlineMd(b.lines.joinToString("\n")), color = color, fontSize = 15.sp, lineHeight = 22.sp)
                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.items.forEachIndexed { k, it ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text(if (b.ordered) "${k + 1}." else "•", color = color, fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp))
                            Text(inlineMd(it), color = color, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
                is MdBlock.Code -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1E1E1E))) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(b.lang.ifEmpty { "code" }, color = Color(0xFF9A9A9A), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Box(Modifier.size(36.dp).clickable { copyText(c, b.code) }, contentAlignment = Alignment.Center) { PIcon("copy", Color(0xFF9A9A9A), 18.dp) }
                    }
                    SelectionContainer {
                        Text(b.code, color = Color(0xFFEDEDED), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp))
                    }
                }
            }
        }
    }
}

/** The copy button under an answer: the whole answer goes to the clipboard. */
@Composable
internal fun AiCopyRow(text: String) {
    val c = LocalContext.current
    Row(Modifier.clickable { copyText(c, text) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon("copy", Color(0xFF9A9A9A), 16.dp)
        Text(tr("Copy", "Kopyala"), color = Color(0xFF9A9A9A), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
