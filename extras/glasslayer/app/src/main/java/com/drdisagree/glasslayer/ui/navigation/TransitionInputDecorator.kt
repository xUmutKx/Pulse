package com.drdisagree.glasslayer.ui.navigation

import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@Composable
fun <T : Any> rememberTransitionInputDecorator(): NavEntryDecorator<T> = remember {
    NavEntryDecorator { entry ->
        val transition = LocalNavAnimatedContentScope.current.transition
        val moving by rememberUpdatedState(
            transition.currentState != EnterExitState.Visible || transition.targetState != EnterExitState.Visible
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (moving) event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            entry.Content()
        }
    }
}
