package com.drdisagree.glasslayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun BackdropScreen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit
) {
    val background = rememberLayerBackdrop()
    val layer = rememberLayerBackdrop()
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(layer)
        ) {
            MeshBackground(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(background)
            )
            CompositionLocalProvider(LocalBackdrop provides background) {
                content()
            }
        }
        CompositionLocalProvider(LocalBackdrop provides layer) {
            overlay()
        }
    }
}
