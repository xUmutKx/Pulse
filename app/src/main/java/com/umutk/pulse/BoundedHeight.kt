package com.umutk.pulse

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/** Compose crashes when a verticalScroll is measured with unbounded height (issue #14). This caps an infinite
 *  max height at [fallback] px so the scroll below always gets a finite size; finite constraints pass through. */
fun Modifier.boundedHeight(fallback: Int = 4000): Modifier = layout { m, c ->
    val cc = if (c.hasBoundedHeight) c else Constraints(c.minWidth, c.maxWidth, c.minHeight.coerceAtMost(fallback), fallback)
    val p = m.measure(cc)
    layout(p.width, p.height) { p.place(0, 0) }
}
