package com.umutk.pulse.extras.music.imgskin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.min

/**
 * A picture skin as the player page: the whole window, fitted in the room it has and never cut. A tap on a key calls [onHit] with its id, a tap or a drag on
 * the seek bar calls [onSeek] with the place (0..1).
 */
@Composable
fun ImgSkinPlayer(skin: ImgSkin, st: SkinState, onHit: (String) -> Unit, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val hitNow by rememberUpdatedState(onHit)
    val seekNow by rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .pointerInput(skin) {
                detectTapGestures { p ->
                    val k = min(size.width / skin.w.toFloat(), size.height / skin.h.toFloat())
                    val x = (p.x - (size.width - skin.w * k) / 2f) / k; val y = (p.y - (size.height - skin.h * k) / 2f) / k
                    val h = skin.hit(x, y) ?: return@detectTapGestures
                    if (h.id == "seek") seekNow(((x - h.r.left) / h.r.width()).coerceIn(0f, 1f)) else hitNow(h.id)
                }
            }
            .pointerInput(skin) {
                var seeking = false
                detectDragGestures(onDragStart = { p ->
                    val k = min(size.width / skin.w.toFloat(), size.height / skin.h.toFloat())
                    val x = (p.x - (size.width - skin.w * k) / 2f) / k; val y = (p.y - (size.height - skin.h * k) / 2f) / k
                    seeking = skin.hit(x, y)?.id == "seek"
                }, onDragEnd = { seeking = false }, onDragCancel = { seeking = false }) { c, _ ->
                    if (seeking) {
                        val r = skin.seek ?: return@detectDragGestures
                        val k = min(size.width / skin.w.toFloat(), size.height / skin.h.toFloat())
                        val x = (c.position.x - (size.width - skin.w * k) / 2f) / k
                        c.consume(); seekNow(((x - r.left) / r.width()).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        val k = min(size.width / skin.w, size.height / skin.h)
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            n.save(); n.translate((size.width - skin.w * k) / 2f, (size.height - skin.h * k) / 2f); n.scale(k, k)
            ImgSkins.paint(n, ctx, skin, st)
            n.restore()
        }
    }
}
