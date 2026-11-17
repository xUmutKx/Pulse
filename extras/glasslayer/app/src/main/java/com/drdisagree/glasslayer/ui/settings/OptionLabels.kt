package com.drdisagree.glasslayer.ui.settings

import androidx.annotation.StringRes
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.GlassToggle

@StringRes
internal fun optionTitle(option: GlassToggle): Int = when (option) {
    GlassToggle.SWITCH_PRESS -> R.string.option_switch_press
    GlassToggle.BUTTON_PRESS -> R.string.option_button_press
    else -> R.string.page_enabled
}

@StringRes
internal fun optionSummary(option: GlassToggle): Int = when (option) {
    GlassToggle.SWITCH_PRESS -> R.string.option_switch_press_summary
    GlassToggle.BUTTON_PRESS -> R.string.option_button_press_summary
    else -> R.string.page_enabled
}
