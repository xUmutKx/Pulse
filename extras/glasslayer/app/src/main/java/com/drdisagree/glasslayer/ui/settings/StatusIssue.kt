package com.drdisagree.glasslayer.ui.settings

import androidx.compose.ui.graphics.Color
import com.drdisagree.glasslayer.domain.model.ModuleStatus
import com.drdisagree.glasslayer.ui.theme.Negative
import com.drdisagree.glasslayer.ui.theme.Warning

internal fun ModuleStatus.issueColor(): Color? = when {
    !frameworkActive || safeMode -> Negative
    restartNeeded -> Warning
    else -> null
}
