package com.drdisagree.glasslayer.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.model.ThermalAction
import com.drdisagree.glasslayer.ui.components.GlassContainer

@Composable
internal fun PerformanceCard(
    mode: PerformanceMode,
    saver: BatterySaverAction,
    thermal: ThermalAction,
    enabled: Boolean,
    onOpenMode: () -> Unit,
    onOpenSaver: () -> Unit,
    onOpenThermal: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassContainer(CardShape, modifier.fillMaxWidth(), enabled = enabled) {
        ChoiceRow(stringResource(R.string.performance_normal), stringResource(modeTitle(mode)), onOpenMode)
        RowDivider()
        ChoiceRow(stringResource(R.string.section_battery_saver), stringResource(saverTitle(saver)), onOpenSaver)
        RowDivider()
        ChoiceRow(stringResource(R.string.section_thermal), stringResource(thermalTitle(thermal)), onOpenThermal)
    }
}
