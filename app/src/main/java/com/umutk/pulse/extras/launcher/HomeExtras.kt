package com.umutk.pulse.extras.launcher

import android.app.WallpaperManager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView

/** The phone's wallpaper follows the swipe between pages (Wallpaper scrolling in the settings): page 1 at the left edge of the picture, the last page at the right edge. */
@Composable
fun WallpaperScrollEffect(pager: PagerState, count: Int, loop: Boolean) {
    if (!LauncherPrefs.wallScroll || count < 2) return
    val view = LocalView.current
    LaunchedEffect(pager, count, loop) {
        val wm = WallpaperManager.getInstance(view.context)
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { pos ->
            val n = count.toFloat()
            val x = if (loop) (((pos % n) + n) % n) / (n - 1f) else pos / (n - 1f)
            runCatching { wm.setWallpaperOffsets(view.windowToken, x.coerceIn(0f, 1f), .5f) }
        }
    }
}
