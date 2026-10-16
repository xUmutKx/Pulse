package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ColorOS: a flat, compact panel with a hairline edge; the pressed row turns accent green. */
fun menuStyleColorOs(w: PTheme) = MenuStyle(
    bg = w.panel, ink = w.text, sub = w.sub, shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, w.line),
    rowV = 9.dp, rowH = 16.dp, font = 15.sp, iconSize = 20.dp, rowMin = 44.dp,
    hiBg = w.accent.copy(alpha = .14f), hiInk = w.accent,
)
