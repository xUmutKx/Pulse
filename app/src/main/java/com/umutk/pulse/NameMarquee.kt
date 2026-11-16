package com.umutk.pulse

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A long song, artist or album name is shown whole instead of ending in "...": it scrolls once when it first comes up (after a short wait)
 * and then stands still at its beginning.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun nameMarquee(): Modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 1500, velocity = 40.dp)
