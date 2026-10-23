package com.umutk.pulse

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Bundled fonts (res/font, all OFL): the ArtCraft title and Word's font list. The first one is Newsreader, a serif. */
val BundledFonts: List<Pair<String, FontFamily>> = listOf(
    "Newsreader" to FontFamily(Font(R.font.newsreader_regular)),
    "Inter" to FontFamily(Font(R.font.inter_regular)),
    "Bebas Neue" to FontFamily(Font(R.font.bebasneue_regular)),
    "Pacifico" to FontFamily(Font(R.font.pacifico_regular)),
    "Lobster" to FontFamily(Font(R.font.lobster_regular)),
    "Press Start 2P" to FontFamily(Font(R.font.pressstart2p_regular)),
    "Righteous" to FontFamily(Font(R.font.righteous_regular)),
)

/**
 * The font of the music player and the lyrics: Inter, a variable font with every weight (Bold really is bold) and the Latin, Turkish, Greek and Cyrillic letters,
 * so titles and lyrics in other languages are not drawn with missing-character boxes. Below Android 8 the system picks the weight itself.
 */
val PlayerFont: FontFamily = FontFamily(listOf(400, 500, 600, 700, 800).map { w ->
    Font(R.font.inter_regular, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})

/** Draws [content] with [PlayerFont] as the text font. */
@androidx.compose.runtime.Composable
fun WithPlayerFont(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(androidx.compose.ui.text.TextStyle(fontFamily = PlayerFont), content)
}
