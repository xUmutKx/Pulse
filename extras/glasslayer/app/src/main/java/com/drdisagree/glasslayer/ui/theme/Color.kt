package com.drdisagree.glasslayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Positive = Color(0xFF30D158)
val Negative = Color(0xFFFF453A)
val Warning = Color(0xFFFFB800)
val Caution = Color(0xFFFF9F0A)

val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.ink

val OnInk: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.onInk

val OnInkMuted: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.onInkMuted

val OnInkFaint: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.onInkFaint

val Surface: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.container

val Hairline: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.hairline

val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accent

val ToggleTrack: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.toggleTrack

val ToggleOn: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.toggleOn

@Composable
@ReadOnlyComposable
fun onTint(tint: Color): Color = if (LocalPalette.current.isDark) Color.White else lerp(tint, Color.Black, ON_TINT_DEPTH)

private const val ON_TINT_DEPTH = 0.3f
