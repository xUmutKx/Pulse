package com.drdisagree.glasslayer.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

object ExpressiveMotion {

    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(0.8f, 380f)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(0.6f, 800f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(0.8f, 200f)

    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(1f, 1600f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(1f, 3800f)

    fun <T> slowEffects(): FiniteAnimationSpec<T> = spring(1f, 800f)
}
