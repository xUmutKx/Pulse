package com.drdisagree.glasslayer.data.source

object PrefKeys {
    const val GROUP = "glass_layer"

    const val ENABLED = "enabled"
    const val RESTART_TOKEN = "restart_token"
    const val PERFORMANCE_MODE = "performance_mode"
    const val BATTERY_SAVER_ACTION = "battery_saver_action"
    const val THERMAL_ACTION = "thermal_action"
    const val BATTERY_SAVER_BACKGROUND = "battery_saver_background"
    const val ONBOARDING_DONE = "onboarding_done"
    const val TILES = "tiles"
    const val FOOTER = "footer"
    const val BRIGHTNESS = "brightness"
    const val MEDIA = "media"
    const val SHADE_BACKGROUND = "shade_background"
    const val BLUR_RADIUS = "blur_radius"
    const val REFRACTION = "refraction"
    const val REFLECTION = "reflection"
    const val RIM = "rim"
    const val SHADOW = "shadow"
    const val TILE_TINT = "tile_tint"
    const val ACTIVE_TILE_TINT = "active_tile_tint"
    const val FOOTER_TINT = "footer_tint"
    const val ACTIVE_FOOTER_TINT = "active_footer_tint"
    const val BRIGHTNESS_TINT = "brightness_tint"
    const val ACTIVE_BRIGHTNESS_TINT = "active_brightness_tint"
    const val MEDIA_TINT = "media_tint"
    const val BACKGROUND_DIM = "background_dim"
    const val BACKGROUND_BLUR = "background_blur"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATION_TINT = "notification_tint"
    const val NOTIFICATION_FOOTER_TINT = "notification_footer_tint"
    const val HEADS_UP = "heads_up"
    const val HEADS_UP_TINT = "heads_up_tint"
    const val LOCKSCREEN_NOTIFICATIONS = "lockscreen_notifications"
    const val LOCKSCREEN_NOTIFICATION_TINT = "lockscreen_notification_tint"
    const val LOCKSCREEN_SHORTCUTS = "lockscreen_shortcuts"
    const val LOCKSCREEN_SHORTCUT_TINT = "lockscreen_shortcut_tint"
    const val BOUNCER = "bouncer"
    const val BOUNCER_TINT = "bouncer_tint"
    const val VOLUME = "volume"
    const val VOLUME_TINT = "volume_tint"
    const val VOLUME_SLIDER_TINT = "volume_slider_tint"
    const val ACTIVE_VOLUME_SLIDER_TINT = "active_volume_slider_tint"
    const val POWER_MENU = "power_menu"
    const val POWER_MENU_TINT = "power_menu_tint"
    const val POWER_MENU_BUTTON_TINT = "power_menu_button_tint"
    const val POWER_MENU_ACCENT_TINT = "power_menu_accent_tint"
    const val SWITCHES = "switches"
    const val SWITCH_PRESS = "switch_press"
    const val BUTTON_PRESS = "button_press"
    const val SWITCH_TINT = "switch_tint"
    const val SLIDERS = "sliders"
    const val SLIDER_TINT = "slider_tint"
    const val ACTIVE_SLIDER_TINT = "active_slider_tint"
    const val VOLUME_PANEL = "volume_panel"
    const val VOLUME_PANEL_TINT = "volume_panel_tint"
    const val VOLUME_PANEL_BUTTON_TINT = "volume_panel_button_tint"
    const val ACTIVE_VOLUME_PANEL_BUTTON_TINT = "active_volume_panel_button_tint"
    const val VOLUME_PANEL_SLIDER_TINT = "volume_panel_slider_tint"
    const val ACTIVE_VOLUME_PANEL_SLIDER_TINT = "active_volume_panel_slider_tint"
    const val DIALOGS = "dialogs"
    const val DIALOG_TINT = "dialog_tint"
    const val DIALOG_BUTTON_TINT = "dialog_button_tint"
    const val DIALOG_ACCENT_TINT = "dialog_accent_tint"
    const val DIALOG_ACTIVE_TINT = "dialog_active_tint"
    const val TOASTS = "toasts"
    const val TOAST_TINT = "toast_tint"
    const val BACK_GESTURE = "back_gesture"
    const val BACK_GESTURE_TINT = "back_gesture_tint"
    const val BUTTONS = "buttons"
    const val BUTTON_TINT = "button_tint"
    const val STATUS_BAR_CHIPS = "status_bar_chips"
    const val STATUS_BAR_CHIP_TINT = "status_bar_chip_tint"
    const val TRANSITIONS = "transitions"
    const val TRANSITION_TINT = "transition_tint"
    const val SETTINGS_CARDS = "settings_cards"
    const val INSTALLER_DIALOGS = "installer_dialogs"
    const val INSTALLER_DIALOG_TINT = "installer_dialog_tint"
    const val SETTINGS_CARD_TINT = "settings_card_tint"
    const val SETTINGS_ICON_TINT = "settings_icon_tint"
    const val SETTINGS_ACCENT_TINT = "settings_accent_tint"
    const val HOOK_REPORT = "hook_report"

    val STYLE_SURFACES = listOf(
        TILES,
        FOOTER,
        BRIGHTNESS,
        MEDIA,
        NOTIFICATIONS,
        HEADS_UP,
        LOCKSCREEN_NOTIFICATIONS,
        LOCKSCREEN_SHORTCUTS,
        BOUNCER,
        VOLUME,
        POWER_MENU,
        SWITCHES,
        SLIDERS,
        BUTTONS,
        VOLUME_PANEL,
        DIALOGS,
        TOASTS,
        BACK_GESTURE,
        STATUS_BAR_CHIPS,
        TRANSITIONS,
        SETTINGS_CARDS,
        INSTALLER_DIALOGS
    )
    val STYLE_PARAMS = listOf(BLUR_RADIUS, REFRACTION, REFLECTION, RIM, SHADOW)

    fun styleOverride(surface: String, param: String): String = surface + "_" + param

    const val INHERIT = -1f
    const val DEFAULT_ENABLED = false
    const val DEFAULT_MODULE = true
    const val DEFAULT_BLUR_RADIUS = 12f
    const val DEFAULT_STYLE = 100f
    const val DEFAULT_TILE_TINT = 30f
    const val DEFAULT_ACTIVE_TILE_TINT = 65f
    const val DEFAULT_FOOTER_TINT = 30f
    const val DEFAULT_ACTIVE_FOOTER_TINT = 65f
    const val DEFAULT_BRIGHTNESS_TINT = 30f
    const val DEFAULT_ACTIVE_BRIGHTNESS_TINT = 65f
    const val DEFAULT_MEDIA_TINT = 60f
    const val DEFAULT_BACKGROUND_DIM = 40f
    const val DEFAULT_BACKGROUND_BLUR = 40f
    const val DEFAULT_NOTIFICATION_TINT = 55f
    const val DEFAULT_NOTIFICATION_FOOTER_TINT = 30f
    const val DEFAULT_HEADS_UP_TINT = 55f
    const val DEFAULT_LOCKSCREEN_NOTIFICATION_TINT = 55f
    const val DEFAULT_LOCKSCREEN = true
    const val DEFAULT_LOCKSCREEN_SHORTCUT_TINT = 30f
    const val DEFAULT_BOUNCER_TINT = 30f
    const val DEFAULT_VOLUME_TINT = 40f
    const val DEFAULT_VOLUME_SLIDER_TINT = 30f
    const val DEFAULT_ACTIVE_VOLUME_SLIDER_TINT = 65f
    const val DEFAULT_POWER_MENU_TINT = 40f
    const val DEFAULT_POWER_MENU_BUTTON_TINT = 30f
    const val DEFAULT_POWER_MENU_ACCENT_TINT = 70f
    const val DEFAULT_SWITCH_TINT = 55f
    const val DEFAULT_SLIDER_TINT = 30f
    const val DEFAULT_ACTIVE_SLIDER_TINT = 65f
    const val DEFAULT_VOLUME_PANEL_TINT = 40f
    const val DEFAULT_VOLUME_PANEL_BUTTON_TINT = 30f
    const val DEFAULT_ACTIVE_VOLUME_PANEL_BUTTON_TINT = 65f
    const val DEFAULT_VOLUME_PANEL_SLIDER_TINT = 30f
    const val DEFAULT_ACTIVE_VOLUME_PANEL_SLIDER_TINT = 65f
    const val DEFAULT_DIALOG_TINT = 60f
    const val DEFAULT_DIALOG_BUTTON_TINT = 50f
    const val DEFAULT_DIALOG_ACCENT_TINT = 70f
    const val DEFAULT_DIALOG_ACTIVE_TINT = 85f
    const val DEFAULT_TOAST_TINT = 50f
    const val DEFAULT_BACK_GESTURE_TINT = 35f
    const val DEFAULT_BUTTON_TINT = 60f
    const val DEFAULT_STATUS_BAR_CHIP_TINT = 70f
    const val DEFAULT_TRANSITION_TINT = 50f
    const val DEFAULT_SETTINGS_CARD_TINT = 55f
    const val DEFAULT_SETTINGS_ICON_TINT = 75f
    const val DEFAULT_SETTINGS_ACCENT_TINT = 85f
    const val DEFAULT_INSTALLER_DIALOG_TINT = 65f
}
