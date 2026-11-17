package com.drdisagree.glasslayer.ui.status

import androidx.annotation.StringRes
import com.drdisagree.glasslayer.R

@StringRes
internal fun modLabel(name: String): Int? = when (name) {
    "DozeTracker" -> R.string.mod_report_doze
    "DisplayRootTracker" -> R.string.mod_report_capture
    "WindowDimTracker" -> R.string.mod_report_dim
    "ShadeGlass" -> R.string.page_background_title
    "QsTileGlass" -> R.string.page_tiles_title
    "QsFooterGlass" -> R.string.page_footer_title
    "BrightnessGlass" -> R.string.page_brightness_title
    "MediaGlass" -> R.string.page_media_title
    "NotificationGlass" -> R.string.page_notifications_title
    "LockscreenShortcutGlass" -> R.string.page_lockscreen_shortcuts_title
    "BouncerGlass" -> R.string.page_bouncer_title
    "VolumeGlass" -> R.string.page_volume_title
    "PowerMenuGlass" -> R.string.page_power_menu_title
    "SwitchGlass" -> R.string.page_switches_title
    "SliderGlass" -> R.string.page_sliders_title
    "SettingsCardGlass" -> R.string.page_settings_cards_title
    "InstallerDialogGlass" -> R.string.page_installer_dialogs_title
    "VolumePanelGlass" -> R.string.page_volume_panel_title
    "DialogGlass" -> R.string.page_dialogs_title
    "ToastGlass" -> R.string.page_toasts_title
    "BackGestureGlass" -> R.string.page_back_gesture_title
    "ButtonGlass" -> R.string.page_buttons_title
    "StatusBarChipGlass" -> R.string.page_status_bar_chips_title
    "TransitionGlass" -> R.string.page_transitions_title
    else -> null
}
