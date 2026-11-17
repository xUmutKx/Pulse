package com.drdisagree.glasslayer.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop

val LocalBackdrop = staticCompositionLocalOf<Backdrop> { error("No backdrop") }
