package com.drdisagree.glasslayer.ui.onboarding

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.ui.components.AndroidGlassScene

@Composable
internal fun OnboardingHero(modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    AndroidGlassScene(
        modifier
            .fillMaxWidth()
            .height(statusTop + HERO_HEIGHT),
        topInset = statusTop
    )
}

private val HERO_HEIGHT = 220.dp
