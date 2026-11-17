package com.drdisagree.glasslayer.ui.main

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.drdisagree.glasslayer.GlassLayerApp
import com.drdisagree.glasslayer.ui.navigation.AppNavigation
import com.drdisagree.glasslayer.ui.theme.GlassLayerTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { (application as GlassLayerApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        window.isNavigationBarContrastEnforced = false
        setContent {
            val dark = isSystemInDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.isNavigationBarContrastEnforced = false
            }
            GlassLayerTheme(dark) {
                AppNavigation(container)
            }
        }
    }
}
