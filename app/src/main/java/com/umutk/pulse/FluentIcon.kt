package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Whether icons are drawn as Fluent glyphs: null follows the look (Windows 11), true / false is set by a window that has its own look (the Office 365 window). */
val LocalFluentIcons = compositionLocalOf<Boolean?> { null }

/**
 * Drop-in for the Material Icon: in the Windows 11 look (and the Office 365 window) an icon that has a Fluent twin is drawn as the Fluent
 * glyph (filled, from the Fluent System Icons), every other look keeps the Material one. Files import this instead of material3.Icon.
 */
@Composable
fun Icon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val on = LocalFluentIcons.current ?: (T.id == "win11")
    val key = imageVector.name.substringAfterLast('.')
    val raw = if (on) FluentMat.map[key] else null
    if (raw == null) { androidx.compose.material3.Icon(imageVector, contentDescription, modifier, tint); return }
    val paths = remember(key) { raw.map { PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd } } }
    val sem = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.then(sem).size(24.dp)) { scale(size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } } }
}

@Composable
fun Icon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)

@Composable
fun Icon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) =
    androidx.compose.material3.Icon(bitmap, contentDescription, modifier, tint)
