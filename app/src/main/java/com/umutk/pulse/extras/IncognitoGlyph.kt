package com.umutk.pulse.extras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The private (incognito) mark: a hat with a pair of glasses under its brim, drawn in the tint it is given. */
@Composable
fun IncognitoGlyph(tint: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        // the brim, then the crown above it
        drawRoundRect(tint, Offset(w * .04f, h * .50f), Size(w * .92f, h * .14f), CornerRadius(h * .07f))
        drawRoundRect(tint, Offset(w * .24f, h * .10f), Size(w * .52f, h * .44f), CornerRadius(h * .10f))
        // the glasses: two rings and the bridge between them
        val stroke = Stroke(width = w * .09f)
        drawCircle(tint, radius = w * .16f, center = Offset(w * .32f, h * .80f), style = stroke)
        drawCircle(tint, radius = w * .16f, center = Offset(w * .68f, h * .80f), style = stroke)
        drawLine(tint, Offset(w * .48f, h * .78f), Offset(w * .52f, h * .78f), strokeWidth = w * .09f)
    }
}
