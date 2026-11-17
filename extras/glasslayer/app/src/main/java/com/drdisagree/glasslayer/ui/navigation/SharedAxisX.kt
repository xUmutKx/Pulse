package com.drdisagree.glasslayer.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.drdisagree.glasslayer.ui.theme.ExpressiveMotion

fun sharedAxisX(forward: Boolean, slideDistance: Int): ContentTransform {
    val direction = if (forward) 1 else -1
    val enter = slideInHorizontally(ExpressiveMotion.defaultSpatial()) { direction * slideDistance } +
            fadeIn(ExpressiveMotion.defaultEffects())
    val exit = slideOutHorizontally(ExpressiveMotion.defaultSpatial()) { -direction * slideDistance } +
            fadeOut(ExpressiveMotion.fastEffects())
    return enter togetherWith exit
}
