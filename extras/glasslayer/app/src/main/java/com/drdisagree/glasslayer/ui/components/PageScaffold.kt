package com.drdisagree.glasslayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun PageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val list = rememberLazyListState()
    val ramp = with(LocalDensity.current) { BLUR_RAMP.toPx() }
    val collapse by remember(list) {
        derivedStateOf {
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == TITLE_KEY }
            if (item == null || item.size <= 0) 1f else (-item.offset.toFloat() / item.size).coerceIn(0f, 1f)
        }
    }
    BackdropScreen(
        content = {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = statusTop + 64.dp,
                    bottom = navBottom + 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = TITLE_KEY) { ScreenTitle(title, Modifier.graphicsLayer { alpha = 1f - collapse }) }
                content()
            }
        },
        overlay = {
            val barBackdrop = rememberLayerBackdrop()
            ProgressiveBlurBar(
                statusTop + 120.dp,
                barBackdrop,
                progress = {
                    if (list.firstVisibleItemIndex > 0) 1f else list.firstVisibleItemScrollOffset / ramp
                }
            )
            CompositionLocalProvider(LocalBackdrop provides barBackdrop) {
                if (onBack != null) {
                    Box(Modifier.padding(start = 12.dp, top = statusTop + 8.dp)) {
                        GlassIconButton(R.drawable.ic_back, onBack, description = stringResource(R.string.action_back))
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = TITLE_INSET, end = TITLE_INSET, top = statusTop + 8.dp)
                        .height(BAR_HEIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = OnInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            val shown = collapse * collapse * (3f - 2f * collapse)
                            alpha = shown
                            translationY = (1f - shown) * TITLE_SLIDE.toPx()
                        }
                    )
                }
                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = statusTop + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions
                )
            }
            overlay()
        }
    )
}

private val BLUR_RAMP = 48.dp
private val BAR_HEIGHT = 44.dp
private val TITLE_INSET = 72.dp
private val TITLE_SLIDE = 8.dp
private const val TITLE_KEY = "title"
