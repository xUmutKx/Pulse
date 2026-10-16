package com.umutk.pulse

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One UI: a big, very round, soft card with tall airy rows and no edge. */
fun menuStyleOneUi(w: PTheme) = MenuStyle(
    bg = w.panel, ink = w.text, sub = w.sub, shape = RoundedCornerShape(28.dp),
    rowV = 14.dp, rowH = 22.dp, font = 16.sp, iconSize = 22.dp, rowMin = 52.dp,
    hiBg = w.text.copy(alpha = .07f), hiInk = w.text,
)
