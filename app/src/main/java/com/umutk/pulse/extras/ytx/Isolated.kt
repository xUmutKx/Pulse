package com.umutk.pulse.extras.ytx

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

// A value that changes often (the playback position, read four times a second) is read inside one of these, so only this small part is drawn
// again when it changes, not the whole player around it (the video page and the music player were recomposed whole four times a second).
@Composable
internal fun Isolated(content: @Composable () -> Unit) { content() }

@Composable
internal fun ColumnScope.IsolatedCol(content: @Composable ColumnScope.() -> Unit) { content() }
