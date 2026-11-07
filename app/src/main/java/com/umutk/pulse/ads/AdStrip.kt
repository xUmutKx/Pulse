package com.umutk.pulse.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** A native ad strip for a page's foot, in the page's own colours (play build; nothing in the foss build or with Premium). */
@Composable
fun AdStrip(bg: Color, text: Color, sub: Color, accent: Color) {
    if (!PulseAds.ENABLED || PulseAds.off(LocalContext.current)) return
    PulseAds.NativeCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), bg = bg, text = text, sub = sub, accent = accent)
}
