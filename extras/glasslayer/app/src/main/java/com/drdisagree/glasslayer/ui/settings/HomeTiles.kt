package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.GlassSettings
import com.drdisagree.glasslayer.domain.model.StyleParam
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.components.pressable
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkMuted
import kotlin.math.roundToInt

@Composable
internal fun HomeTiles(
    settings: GlassSettings,
    enabled: Boolean,
    onOpenStyle: () -> Unit,
    onOpenPerformance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(
            title = stringResource(R.string.page_style),
            value = stringResource(R.string.tile_style_value, blurPercent(settings)),
            enabled = enabled,
            onClick = onOpenStyle,
            modifier = Modifier.weight(1f)
        ) {
            MiniGlassPreview(
                blurDp = settings.style(StyleParam.BLUR_RADIUS),
                refraction = settings.style(StyleParam.REFRACTION) / PERCENT,
                reflection = settings.style(StyleParam.REFLECTION) / PERCENT,
                rim = settings.style(StyleParam.RIM) / PERCENT,
                shadow = settings.style(StyleParam.SHADOW) / PERCENT,
                modifier = Modifier.fillMaxSize()
            )
        }
        val rate = rememberDisplayRate()
        Tile(
            title = stringResource(R.string.page_performance),
            value = stringResource(R.string.tile_performance_value, stringResource(modeTitle(settings.performance)), settings.performance.fps(rate)),
            enabled = enabled,
            onClick = onOpenPerformance,
            modifier = Modifier.weight(1f)
        ) {
            FpsBalls(
                fps = settings.performance.fps(rate),
                displayRate = rate,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun Tile(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit
) {
    GlassContainer(CardShape, modifier.height(TILE_HEIGHT), enabled = enabled) {
        Column(
            Modifier
                .fillMaxSize()
                .pressable(Role.Button, onClick)
                .padding(8.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { art() }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = OnInk,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = OnInkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            )
        }
    }
}

private fun blurPercent(settings: GlassSettings): Int =
    (settings.style(StyleParam.BLUR_RADIUS) / StyleParam.BLUR_RADIUS.max * PERCENT).roundToInt()

private val TILE_HEIGHT = 148.dp
private const val PERCENT = 100f
