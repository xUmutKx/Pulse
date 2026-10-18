package com.umutk.pulse

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

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
