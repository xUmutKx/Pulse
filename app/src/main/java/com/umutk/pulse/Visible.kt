package com.umutk.pulse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope

/*
 * A loop of the screen (a clock, a poll, the sampler) must not go on while the app is in the background: the activity and its composition stay alive there, and a `delay` loop of a
 * LaunchedEffect keeps waking the phone every second or two (the frame clock stops with the window, a `delay` does not). These run their block only while the activity is on the
 * screen (started): it is cancelled when the app goes to the background and starts again when it comes back.
 */

/** Whether the activity of this composition is on the screen (started). */
@Composable
private fun rememberVisible(): Boolean {
    val owner = LocalLifecycleOwner.current
    var visible by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) visible = true else if (e == Lifecycle.Event.ON_STOP) visible = false
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return visible
}

/** [LaunchedEffect] that runs only while the app is visible. */
@Composable
fun LaunchedVisible(key1: Any?, block: suspend CoroutineScope.() -> Unit) {
    val visible = rememberVisible()
    LaunchedEffect(key1, visible) { if (visible) block() }
}

@Composable
fun LaunchedVisible(key1: Any?, key2: Any?, block: suspend CoroutineScope.() -> Unit) {
    val visible = rememberVisible()
    LaunchedEffect(key1, key2, visible) { if (visible) block() }
}

/** [produceState] that runs its producer only while the app is visible (the value stays as it was while it is away). */
@Composable
fun <T> produceStateVisible(initialValue: T, vararg keys: Any?, producer: suspend ProduceStateScope<T>.() -> Unit): State<T> {
    val visible = rememberVisible()
    return produceState(initialValue, visible, *keys) { if (visible) producer() }
}

/** While this is in the composition, the sampler takes a reading every second (a live graph is on the screen); otherwise every few seconds. */
@Composable
fun NeedFastSamples() {
    DisposableEffect(Unit) { Sampler.fast++; onDispose { Sampler.fast-- } }
}
