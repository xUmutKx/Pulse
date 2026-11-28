package com.umutk.pulse.lean

import androidx.compose.runtime.Composable

/** The screen of this lean app. */
@Composable
fun LeanApp(back: () -> Unit) { com.umutk.pulse.extras.browser.BrowserHub(back) }
