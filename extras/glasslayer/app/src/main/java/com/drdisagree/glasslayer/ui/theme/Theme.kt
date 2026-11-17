package com.drdisagree.glasslayer.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun GlassLayerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val target = if (dark) DarkPalette else LightPalette
    val palette = target.copy(
        ink = animated(target.ink),
        onInk = animated(target.onInk),
        onInkMuted = animated(target.onInkMuted),
        onInkFaint = animated(target.onInkFaint),
        container = animated(target.container),
        hairline = animated(target.hairline),
        accent = animated(target.accent),
        toggleTrack = animated(target.toggleTrack),
        toggleOn = animated(target.toggleOn),
        mesh = target.mesh.map { animated(it) }
    )
    val context = LocalContext.current
    val scheme = (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).copy(
        background = palette.ink,
        surface = palette.ink,
        onBackground = palette.onInk,
        onSurface = palette.onInk
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}

@Composable
private fun animated(color: Color): Color = animateColorAsState(color, tween(THEME_FADE), label = "palette").value

private const val THEME_FADE = 450
