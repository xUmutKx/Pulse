package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.ui.components.GlassContainer
import com.drdisagree.glasslayer.ui.theme.OnInk
import com.drdisagree.glasslayer.ui.theme.OnInkMuted

@Composable
internal fun PerformanceHero(mode: PerformanceMode, saver: BatterySaverAction, modifier: Modifier = Modifier) {
    val rate = rememberDisplayRate()
    val fps = mode.fps(rate)
    GlassContainer(CardShape, modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FpsBalls(
                fps = fps,
                displayRate = rate,
                ball = 18.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 12.dp)
            )
            Text(
                stringResource(R.string.performance_hero_fps, fps),
                style = MaterialTheme.typography.titleMedium,
                color = OnInk
            )
            Text(
                stringResource(R.string.performance_hero_saver, stringResource(saverTitle(saver))),
                style = MaterialTheme.typography.bodySmall,
                color = OnInkMuted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
