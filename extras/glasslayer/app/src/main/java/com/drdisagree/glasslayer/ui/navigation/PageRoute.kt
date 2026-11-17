package com.drdisagree.glasslayer.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.drdisagree.glasslayer.ui.settings.GlassPage
import kotlinx.serialization.Serializable

@Serializable
data class PageRoute(val page: GlassPage) : NavKey
