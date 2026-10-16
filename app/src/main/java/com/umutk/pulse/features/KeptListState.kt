package com.umutk.pulse.features

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private val keptLists = HashMap<String, LazyListState>()

// Process-level: the list keeps its scroll position when its screen is left for a detail page and re-entered.
@Composable
fun rememberKeptListState(key: String): LazyListState = remember(key) { keptLists.getOrPut(key) { LazyListState() } }
