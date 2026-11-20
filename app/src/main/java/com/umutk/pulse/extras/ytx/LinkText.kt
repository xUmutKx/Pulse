package com.umutk.pulse.extras.ytx

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit

private val LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

/**
 * A text whose web addresses are marked (underlined, in [linkColor]) and open when tapped: a YouTube address opens in YTXTube itself, any other in the browser.
 * (The marks were drawn before but a tap did nothing: they were plain text.)
 */
@Composable
fun LinkText(text: String, color: Color, linkColor: Color, modifier: Modifier = Modifier, fontSize: TextUnit = TextUnit.Unspecified, lineHeight: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE) {
    val ctx = LocalContext.current
    val styled = remember(text, linkColor) {
        buildAnnotatedString {
            var at = 0
            for (m in LINK.findAll(text)) {
                append(text.substring(at, m.range.first))
                // a sentence's last mark is not part of the address
                val raw = m.value.trimEnd('.', ',', ';', ':', ')', '!', '?')
                pushStringAnnotation("url", if (raw.startsWith("www.", true)) "https://$raw" else raw)
                pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)); append(raw); pop(); pop()
                append(m.value.substring(raw.length))
                at = m.range.last + 1
            }
            append(text.substring(at))
        }
    }
    ClickableText(styled, modifier = modifier, style = TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight), maxLines = maxLines) { off ->
        styled.getStringAnnotations("url", off, off).firstOrNull()?.let { a ->
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.item)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}
