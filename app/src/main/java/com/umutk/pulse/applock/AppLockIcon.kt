package com.umutk.pulse.applock

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

// 24 px grid, filled outlines like FluentPaths: an app tile (ring) holding a padlock. Same drawing in every look, it takes the tint.
private val PATHS = listOf(
    "M7 3H17A4 4 0 0 1 21 7V17A4 4 0 0 1 17 21H7A4 4 0 0 1 3 17V7A4 4 0 0 1 7 3ZM7 4.5A2.5 2.5 0 0 0 4.5 7V17A2.5 2.5 0 0 0 7 19.5H17A2.5 2.5 0 0 0 19.5 17V7A2.5 2.5 0 0 0 17 4.5Z",
    "M9.5 11H14.5A1 1 0 0 1 15.5 12V15.5A1 1 0 0 1 14.5 16.5H9.5A1 1 0 0 1 8.5 15.5V12A1 1 0 0 1 9.5 11Z",
    "M9.75 11V9.5A2.25 2.25 0 0 1 14.25 9.5V11H13V9.5A1 1 0 0 0 11 9.5V11Z",
)

/** The App lock icon: an app tile with a padlock. Add `"applock" -> AppLockIcon(...)` at the top of PIcon (Ui.kt). */
@Composable
fun AppLockIcon(tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val paths = remember { PATHS.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) { scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } } }
}
