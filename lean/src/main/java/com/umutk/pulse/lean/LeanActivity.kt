package com.umutk.pulse.lean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.T

/** The one screen of a lean app: the app's own screen (by BuildConfig.APP) on the look's background. */
class LeanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val dark = isSystemInDarkTheme()
            SideEffect { T.dark = dark; window.statusBarColor = T.bg.toArgb(); window.navigationBarColor = T.bg.toArgb()
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark } }
            Box(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
                LeanApp { finish() }
            }
        }
    }
}
