package com.umutk.pulse.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import com.umutk.pulse.PIcon
import com.umutk.pulse.PButton
import com.umutk.pulse.PSlider
import com.umutk.pulse.WinAlert
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** How see-through the ad strip at the foot of every page is (0.2 to 1), kept on the phone. */
object AdBar {
    var alpha by mutableFloatStateOf(0.8f)
    private var loaded = false
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        alpha = c.getSharedPreferences("p", Context.MODE_PRIVATE).getFloat("ad_bar_alpha", 0.8f).coerceIn(0.2f, 1f)
    }
    fun set(c: Context, v: Float) {
        alpha = v.coerceIn(0.2f, 1f)
        c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putFloat("ad_bar_alpha", alpha).apply()
    }
}

/** The small button over the ad strip: opens the see-through slider. */
@Composable
fun AdBarTune(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AdBar.load(ctx) }
    Box(modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)).clickable { open = true }, contentAlignment = Alignment.Center) {
        PIcon("settings", Color.White, 16.dp)
    }
    if (open) WinAlert(
        onDismissRequest = { open = false },
        title = { Text(tr("Ad transparency", "Reklam saydamlığı"), color = T.text) },
        text = { PSlider(AdBar.alpha, { AdBar.set(ctx, it) }, 0.2f..1f, onDone = { }) },
        confirmButton = { PButton(tr("Close", "Kapat")) { open = false } },
    )
}
