package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.features.ThemeIcons

/** The real Recycle Bin icon of 95 / XP / 7 (assets/icons/<theme>/delete.png), the Fluent bin on 10 / 11, Material outlined. */
@Composable
fun BinIcon(tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val id = T.id
    if (T.material) { Icon(Icons.Outlined.Delete, null, modifier.size(size), tint); return }
    if (id == "win95" || id == "xp" || id == "win7") ThemeIcons.bitmap(ctx, id, "delete")?.let { Image(it, null, modifier.size(size)); return }
    // Win10 / Win11: the Fluent line bin
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { FluentPaths.map["delete"]!!.forEach { drawPath(PathParser().parsePathString(it).toPath(), tint) } }
    }
}

/** The "more" button in the look's own drawing: 95 square dots, XP ringed dots, 7 and 10 horizontal, 11 small vertical, Material's icon. */
@Composable
fun MoreIcon(tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    if (T.material) { Icon(Icons.Outlined.MoreVert, null, modifier.size(size), tint); return }
    val id = T.id
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val horiz = id == "win7" || id == "win10"
        for (i in 0..2) {
            val c = if (horiz) Offset(w * (.2f + .3f * i), h / 2) else Offset(w / 2, h * (.2f + .3f * i))
            when (id) {
                "win95" -> drawRect(tint, c - Offset(w * .08f, h * .08f), Size(w * .16f, h * .16f))
                "xp" -> { drawCircle(tint, w * .13f, c); drawCircle(Color.White.copy(alpha = .6f), w * .05f, c - Offset(w * .03f, h * .03f)) }
                "win7" -> { drawCircle(tint, w * .1f, c); drawCircle(tint.copy(alpha = .35f), w * .15f, c, style = Stroke(w * .03f)) }
                "win10" -> drawRect(tint, c - Offset(w * .06f, h * .06f), Size(w * .12f, h * .12f))
                else -> drawCircle(tint, w * .075f, c)
            }
        }
    }
}
