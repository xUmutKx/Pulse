package com.umutk.pulse.lean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import com.umutk.pulse.LeanLookHost

/** The one screen of a lean app: the app's own screen (by BuildConfig.APP) in the look of the app (Material by default; every look of Pulse can be chosen). */
class LeanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LeanLookHost {
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    LeanApp { finish() }
                }
            }
        }
    }
}
