package com.umutk.pulse.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Window size class by screen width, like WindowSizeClass (the window-size-class artifact is not in the build). */
enum class WinClass { Compact, Medium, Expanded }

@Composable
fun winClass(): WinClass {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> WinClass.Expanded
        w >= 600 -> WinClass.Medium
        else -> WinClass.Compact
    }
}

@Composable
fun isLandscape(): Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Content capped at [max] and centred; on phones (narrower than [max]) it is simply full width. */
@Composable
fun MaxWidthBox(modifier: Modifier = Modifier, max: Dp = 720.dp, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max).fillMaxSize(), content = content)
    }
}

/** Grid cells at least 320 dp wide: one column on a phone, several on a tablet or in landscape. */
fun adaptiveCells(min: Dp = 320.dp): GridCells = GridCells.Adaptive(min)

/**
 * List on the left and detail on the right when the window is Expanded; on smaller windows one pane at a time,
 * the detail replacing the list while [hasDetail] is true.
 */
@Composable
fun ListDetail(hasDetail: Boolean, listWidth: Dp = 340.dp, list: @Composable () -> Unit, detail: @Composable () -> Unit) {
    if (winClass() == WinClass.Expanded) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) { list() }
            VerticalDivider(Modifier.fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) { detail() }
        }
    } else if (hasDetail) detail() else list()
}
