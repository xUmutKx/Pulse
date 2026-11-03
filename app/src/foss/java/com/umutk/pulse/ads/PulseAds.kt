package com.umutk.pulse.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The foss build has no ads and no Google SDK: the same calls do nothing, and a rewarded ad is never shown. */
object PulseAds {
    const val ENABLED = false

    fun init(c: Context) { }

    @Composable
    fun Banner(modifier: Modifier = Modifier) { }

    @Composable
    fun NativeCard(modifier: Modifier = Modifier, bg: Color, text: Color, sub: Color, accent: Color) { }

    fun showRewarded(a: Activity, onReward: () -> Unit, onNone: () -> Unit) = onNone()
}
