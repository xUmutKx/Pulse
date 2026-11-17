package com.drdisagree.glasslayer.ui.settings

import androidx.annotation.StringRes
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.model.StyleParam

@StringRes
internal fun levelLabel(level: GlassLevel): Int = when (level) {
    GlassLevel.TILE_TINT -> R.string.level_tile_tint
    GlassLevel.ACTIVE_TILE_TINT -> R.string.level_active_tile_tint
    GlassLevel.FOOTER_TINT -> R.string.level_footer_tint
    GlassLevel.ACTIVE_FOOTER_TINT -> R.string.level_active_footer_tint
    GlassLevel.BRIGHTNESS_TINT -> R.string.level_brightness_tint
    GlassLevel.ACTIVE_BRIGHTNESS_TINT -> R.string.level_active_brightness_tint
    GlassLevel.MEDIA_TINT -> R.string.level_media_tint
    GlassLevel.BACKGROUND_DIM -> R.string.level_background_dim
    GlassLevel.BACKGROUND_BLUR -> R.string.level_background_blur
    GlassLevel.NOTIFICATION_TINT -> R.string.level_notification_tint
    GlassLevel.NOTIFICATION_FOOTER_TINT -> R.string.level_notification_footer_tint
    GlassLevel.HEADS_UP_TINT -> R.string.level_heads_up_tint
    GlassLevel.LOCKSCREEN_NOTIFICATION_TINT -> R.string.level_notification_tint
    GlassLevel.LOCKSCREEN_SHORTCUT_TINT -> R.string.level_shortcut_tint
    GlassLevel.BOUNCER_TINT -> R.string.level_bouncer_tint
    GlassLevel.VOLUME_TINT -> R.string.level_volume_tint
    GlassLevel.VOLUME_SLIDER_TINT -> R.string.level_brightness_tint
    GlassLevel.ACTIVE_VOLUME_SLIDER_TINT -> R.string.level_active_volume_tint
    GlassLevel.POWER_MENU_TINT -> R.string.level_volume_tint
    GlassLevel.POWER_MENU_BUTTON_TINT -> R.string.level_power_menu_button_tint
    GlassLevel.POWER_MENU_ACCENT_TINT -> R.string.level_power_menu_accent_tint
    GlassLevel.SWITCH_TINT -> R.string.level_brightness_tint
    GlassLevel.SLIDER_TINT -> R.string.level_brightness_tint
    GlassLevel.ACTIVE_SLIDER_TINT -> R.string.level_active_brightness_tint
    GlassLevel.VOLUME_PANEL_TINT -> R.string.level_volume_tint
    GlassLevel.VOLUME_PANEL_BUTTON_TINT -> R.string.level_power_menu_button_tint
    GlassLevel.ACTIVE_VOLUME_PANEL_BUTTON_TINT -> R.string.level_active_button_tint
    GlassLevel.VOLUME_PANEL_SLIDER_TINT -> R.string.level_brightness_tint
    GlassLevel.ACTIVE_VOLUME_PANEL_SLIDER_TINT -> R.string.level_active_brightness_tint
    GlassLevel.DIALOG_TINT -> R.string.level_volume_tint
    GlassLevel.DIALOG_BUTTON_TINT -> R.string.level_power_menu_button_tint
    GlassLevel.DIALOG_ACCENT_TINT -> R.string.level_power_menu_accent_tint
    GlassLevel.DIALOG_ACTIVE_TINT -> R.string.level_active_item_tint
    GlassLevel.TOAST_TINT -> R.string.level_toast_tint
    GlassLevel.BACK_GESTURE_TINT -> R.string.level_back_gesture_tint
    GlassLevel.BUTTON_TINT -> R.string.level_button_tint
    GlassLevel.STATUS_BAR_CHIP_TINT -> R.string.level_chip_tint
    GlassLevel.TRANSITION_TINT -> R.string.level_transition_tint
    GlassLevel.SETTINGS_CARD_TINT -> R.string.level_settings_card_tint
    GlassLevel.SETTINGS_ICON_TINT -> R.string.level_settings_icon_tint
    GlassLevel.SETTINGS_ACCENT_TINT -> R.string.level_settings_accent_tint
    GlassLevel.INSTALLER_DIALOG_TINT -> R.string.level_installer_dialog_tint
}

@StringRes
internal fun styleLabel(param: StyleParam): Int = when (param) {
    StyleParam.BLUR_RADIUS -> R.string.level_blur
    StyleParam.REFRACTION -> R.string.level_refraction
    StyleParam.REFLECTION -> R.string.level_reflection
    StyleParam.RIM -> R.string.level_rim
    StyleParam.SHADOW -> R.string.level_shadow
}
