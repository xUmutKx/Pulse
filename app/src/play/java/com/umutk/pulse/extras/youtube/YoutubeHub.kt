package com.umutk.pulse.extras.youtube

import androidx.compose.runtime.Composable

/** The Google Play build has no YouTube client (it is in the open-source build only): the route is never listed here. */
@Composable
fun YoutubeHub(onBack: () -> Unit) { onBack() }
