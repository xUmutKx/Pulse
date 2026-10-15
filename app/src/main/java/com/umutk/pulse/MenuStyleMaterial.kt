package com.umutk.pulse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material You and AMOLED: a rounded tonal Material 3 menu (48 dp rows); AMOLED gets a thin edge on pure black. */
fun menuStyleMaterial(w: PTheme) = MenuStyle(
    bg = w.panel, ink = w.text, sub = w.sub,
    shape = RoundedCornerShape(if (w.id == "amoled") 12.dp else 16.dp),
    border = if (w.id == "amoled") BorderStroke(1.dp, w.line) else null,
    rowV = 12.dp, rowH = 16.dp, font = 14.sp, iconSize = 20.dp,
)
