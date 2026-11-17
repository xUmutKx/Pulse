package com.drdisagree.glasslayer.data.mapper

import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.model.GlassSurface
import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.domain.model.StyleParam

object GlassPrefMapper {

    fun key(toggle: GlassToggle): String = when (toggle) {
        GlassToggle.ENABLED -> PrefKeys.ENABLED
        GlassToggle.TILES -> PrefKeys.TILES
        GlassToggle.FOOTER -> PrefKeys.FOOTER
        GlassToggle.BRIGHTNESS -> PrefKeys.BRIGHTNESS
        GlassToggle.MEDIA -> PrefKeys.MEDIA
        GlassToggle.SHADE_BACKGROUND -> PrefKeys.SHADE_BACKGROUND
        GlassToggle.NOTIFICATIONS -> PrefKeys.NOTIFICATIONS
        GlassToggle.HEADS_UP -> PrefKeys.HEADS_UP
        GlassToggle.LOCKSCREEN_NOTIFICATIONS -> PrefKeys.LOCKSCREEN_NOTIFICATIONS
        GlassToggle.LOCKSCREEN_SHORTCUTS -> PrefKeys.LOCKSCREEN_SHORTCUTS
        GlassToggle.BOUNCER -> PrefKeys.BOUNCER
        GlassToggle.VOLUME -> PrefKeys.VOLUME
        GlassToggle.POWER_MENU -> PrefKeys.POWER_MENU
        GlassToggle.SWITCHES -> PrefKeys.SWITCHES
        GlassToggle.SLIDERS -> PrefKeys.SLIDERS
        GlassToggle.BUTTONS -> PrefKeys.BUTTONS
        GlassToggle.VOLUME_PANEL -> PrefKeys.VOLUME_PANEL
        GlassToggle.DIALOGS -> PrefKeys.DIALOGS
        GlassToggle.TOASTS -> PrefKeys.TOASTS
        GlassToggle.BACK_GESTURE -> PrefKeys.BACK_GESTURE
        GlassToggle.STATUS_BAR_CHIPS -> PrefKeys.STATUS_BAR_CHIPS
        GlassToggle.TRANSITIONS -> PrefKeys.TRANSITIONS
        GlassToggle.SETTINGS_CARDS -> PrefKeys.SETTINGS_CARDS
        GlassToggle.INSTALLER_DIALOGS -> PrefKeys.INSTALLER_DIALOGS
        GlassToggle.SWITCH_PRESS -> PrefKeys.SWITCH_PRESS
        GlassToggle.BUTTON_PRESS -> PrefKeys.BUTTON_PRESS
    }

    fun default(toggle: GlassToggle): Boolean = when (toggle) {
        GlassToggle.ENABLED -> PrefKeys.DEFAULT_ENABLED
        GlassToggle.LOCKSCREEN_NOTIFICATIONS, GlassToggle.LOCKSCREEN_SHORTCUTS, GlassToggle.BOUNCER -> PrefKeys.DEFAULT_LOCKSCREEN
        else -> PrefKeys.DEFAULT_MODULE
    }

    fun key(level: GlassLevel): String = when (level) {
        GlassLevel.TILE_TINT -> PrefKeys.TILE_TINT
        GlassLevel.ACTIVE_TILE_TINT -> PrefKeys.ACTIVE_TILE_TINT
        GlassLevel.FOOTER_TINT -> PrefKeys.FOOTER_TINT
        GlassLevel.ACTIVE_FOOTER_TINT -> PrefKeys.ACTIVE_FOOTER_TINT
        GlassLevel.BRIGHTNESS_TINT -> PrefKeys.BRIGHTNESS_TINT
        GlassLevel.ACTIVE_BRIGHTNESS_TINT -> PrefKeys.ACTIVE_BRIGHTNESS_TINT
        GlassLevel.MEDIA_TINT -> PrefKeys.MEDIA_TINT
        GlassLevel.BACKGROUND_DIM -> PrefKeys.BACKGROUND_DIM
        GlassLevel.BACKGROUND_BLUR -> PrefKeys.BACKGROUND_BLUR
        GlassLevel.NOTIFICATION_TINT -> PrefKeys.NOTIFICATION_TINT
        GlassLevel.NOTIFICATION_FOOTER_TINT -> PrefKeys.NOTIFICATION_FOOTER_TINT
        GlassLevel.HEADS_UP_TINT -> PrefKeys.HEADS_UP_TINT
        GlassLevel.LOCKSCREEN_NOTIFICATION_TINT -> PrefKeys.LOCKSCREEN_NOTIFICATION_TINT
        GlassLevel.LOCKSCREEN_SHORTCUT_TINT -> PrefKeys.LOCKSCREEN_SHORTCUT_TINT
        GlassLevel.BOUNCER_TINT -> PrefKeys.BOUNCER_TINT
        GlassLevel.VOLUME_TINT -> PrefKeys.VOLUME_TINT
        GlassLevel.VOLUME_SLIDER_TINT -> PrefKeys.VOLUME_SLIDER_TINT
        GlassLevel.ACTIVE_VOLUME_SLIDER_TINT -> PrefKeys.ACTIVE_VOLUME_SLIDER_TINT
        GlassLevel.POWER_MENU_TINT -> PrefKeys.POWER_MENU_TINT
        GlassLevel.POWER_MENU_BUTTON_TINT -> PrefKeys.POWER_MENU_BUTTON_TINT
        GlassLevel.POWER_MENU_ACCENT_TINT -> PrefKeys.POWER_MENU_ACCENT_TINT
        GlassLevel.SWITCH_TINT -> PrefKeys.SWITCH_TINT
        GlassLevel.SLIDER_TINT -> PrefKeys.SLIDER_TINT
        GlassLevel.ACTIVE_SLIDER_TINT -> PrefKeys.ACTIVE_SLIDER_TINT
        GlassLevel.VOLUME_PANEL_TINT -> PrefKeys.VOLUME_PANEL_TINT
        GlassLevel.VOLUME_PANEL_BUTTON_TINT -> PrefKeys.VOLUME_PANEL_BUTTON_TINT
        GlassLevel.ACTIVE_VOLUME_PANEL_BUTTON_TINT -> PrefKeys.ACTIVE_VOLUME_PANEL_BUTTON_TINT
        GlassLevel.VOLUME_PANEL_SLIDER_TINT -> PrefKeys.VOLUME_PANEL_SLIDER_TINT
        GlassLevel.ACTIVE_VOLUME_PANEL_SLIDER_TINT -> PrefKeys.ACTIVE_VOLUME_PANEL_SLIDER_TINT
        GlassLevel.DIALOG_TINT -> PrefKeys.DIALOG_TINT
        GlassLevel.DIALOG_BUTTON_TINT -> PrefKeys.DIALOG_BUTTON_TINT
        GlassLevel.DIALOG_ACCENT_TINT -> PrefKeys.DIALOG_ACCENT_TINT
        GlassLevel.DIALOG_ACTIVE_TINT -> PrefKeys.DIALOG_ACTIVE_TINT
        GlassLevel.TOAST_TINT -> PrefKeys.TOAST_TINT
        GlassLevel.BACK_GESTURE_TINT -> PrefKeys.BACK_GESTURE_TINT
        GlassLevel.BUTTON_TINT -> PrefKeys.BUTTON_TINT
        GlassLevel.STATUS_BAR_CHIP_TINT -> PrefKeys.STATUS_BAR_CHIP_TINT
        GlassLevel.TRANSITION_TINT -> PrefKeys.TRANSITION_TINT
        GlassLevel.SETTINGS_CARD_TINT -> PrefKeys.SETTINGS_CARD_TINT
        GlassLevel.SETTINGS_ICON_TINT -> PrefKeys.SETTINGS_ICON_TINT
        GlassLevel.SETTINGS_ACCENT_TINT -> PrefKeys.SETTINGS_ACCENT_TINT
        GlassLevel.INSTALLER_DIALOG_TINT -> PrefKeys.INSTALLER_DIALOG_TINT
    }

    fun default(level: GlassLevel): Float = when (level) {
        GlassLevel.TILE_TINT -> PrefKeys.DEFAULT_TILE_TINT
        GlassLevel.ACTIVE_TILE_TINT -> PrefKeys.DEFAULT_ACTIVE_TILE_TINT
        GlassLevel.FOOTER_TINT -> PrefKeys.DEFAULT_FOOTER_TINT
        GlassLevel.ACTIVE_FOOTER_TINT -> PrefKeys.DEFAULT_ACTIVE_FOOTER_TINT
        GlassLevel.BRIGHTNESS_TINT -> PrefKeys.DEFAULT_BRIGHTNESS_TINT
        GlassLevel.ACTIVE_BRIGHTNESS_TINT -> PrefKeys.DEFAULT_ACTIVE_BRIGHTNESS_TINT
        GlassLevel.MEDIA_TINT -> PrefKeys.DEFAULT_MEDIA_TINT
        GlassLevel.BACKGROUND_DIM -> PrefKeys.DEFAULT_BACKGROUND_DIM
        GlassLevel.BACKGROUND_BLUR -> PrefKeys.DEFAULT_BACKGROUND_BLUR
        GlassLevel.NOTIFICATION_TINT -> PrefKeys.DEFAULT_NOTIFICATION_TINT
        GlassLevel.NOTIFICATION_FOOTER_TINT -> PrefKeys.DEFAULT_NOTIFICATION_FOOTER_TINT
        GlassLevel.HEADS_UP_TINT -> PrefKeys.DEFAULT_HEADS_UP_TINT
        GlassLevel.LOCKSCREEN_NOTIFICATION_TINT -> PrefKeys.DEFAULT_LOCKSCREEN_NOTIFICATION_TINT
        GlassLevel.LOCKSCREEN_SHORTCUT_TINT -> PrefKeys.DEFAULT_LOCKSCREEN_SHORTCUT_TINT
        GlassLevel.BOUNCER_TINT -> PrefKeys.DEFAULT_BOUNCER_TINT
        GlassLevel.VOLUME_TINT -> PrefKeys.DEFAULT_VOLUME_TINT
        GlassLevel.VOLUME_SLIDER_TINT -> PrefKeys.DEFAULT_VOLUME_SLIDER_TINT
        GlassLevel.ACTIVE_VOLUME_SLIDER_TINT -> PrefKeys.DEFAULT_ACTIVE_VOLUME_SLIDER_TINT
        GlassLevel.POWER_MENU_TINT -> PrefKeys.DEFAULT_POWER_MENU_TINT
        GlassLevel.POWER_MENU_BUTTON_TINT -> PrefKeys.DEFAULT_POWER_MENU_BUTTON_TINT
        GlassLevel.POWER_MENU_ACCENT_TINT -> PrefKeys.DEFAULT_POWER_MENU_ACCENT_TINT
        GlassLevel.SWITCH_TINT -> PrefKeys.DEFAULT_SWITCH_TINT
        GlassLevel.SLIDER_TINT -> PrefKeys.DEFAULT_SLIDER_TINT
        GlassLevel.ACTIVE_SLIDER_TINT -> PrefKeys.DEFAULT_ACTIVE_SLIDER_TINT
        GlassLevel.VOLUME_PANEL_TINT -> PrefKeys.DEFAULT_VOLUME_PANEL_TINT
        GlassLevel.VOLUME_PANEL_BUTTON_TINT -> PrefKeys.DEFAULT_VOLUME_PANEL_BUTTON_TINT
        GlassLevel.ACTIVE_VOLUME_PANEL_BUTTON_TINT -> PrefKeys.DEFAULT_ACTIVE_VOLUME_PANEL_BUTTON_TINT
        GlassLevel.VOLUME_PANEL_SLIDER_TINT -> PrefKeys.DEFAULT_VOLUME_PANEL_SLIDER_TINT
        GlassLevel.ACTIVE_VOLUME_PANEL_SLIDER_TINT -> PrefKeys.DEFAULT_ACTIVE_VOLUME_PANEL_SLIDER_TINT
        GlassLevel.DIALOG_TINT -> PrefKeys.DEFAULT_DIALOG_TINT
        GlassLevel.DIALOG_BUTTON_TINT -> PrefKeys.DEFAULT_DIALOG_BUTTON_TINT
        GlassLevel.DIALOG_ACCENT_TINT -> PrefKeys.DEFAULT_DIALOG_ACCENT_TINT
        GlassLevel.DIALOG_ACTIVE_TINT -> PrefKeys.DEFAULT_DIALOG_ACTIVE_TINT
        GlassLevel.TOAST_TINT -> PrefKeys.DEFAULT_TOAST_TINT
        GlassLevel.BACK_GESTURE_TINT -> PrefKeys.DEFAULT_BACK_GESTURE_TINT
        GlassLevel.BUTTON_TINT -> PrefKeys.DEFAULT_BUTTON_TINT
        GlassLevel.STATUS_BAR_CHIP_TINT -> PrefKeys.DEFAULT_STATUS_BAR_CHIP_TINT
        GlassLevel.TRANSITION_TINT -> PrefKeys.DEFAULT_TRANSITION_TINT
        GlassLevel.SETTINGS_CARD_TINT -> PrefKeys.DEFAULT_SETTINGS_CARD_TINT
        GlassLevel.SETTINGS_ICON_TINT -> PrefKeys.DEFAULT_SETTINGS_ICON_TINT
        GlassLevel.SETTINGS_ACCENT_TINT -> PrefKeys.DEFAULT_SETTINGS_ACCENT_TINT
        GlassLevel.INSTALLER_DIALOG_TINT -> PrefKeys.DEFAULT_INSTALLER_DIALOG_TINT
    }

    fun key(param: StyleParam): String = when (param) {
        StyleParam.BLUR_RADIUS -> PrefKeys.BLUR_RADIUS
        StyleParam.REFRACTION -> PrefKeys.REFRACTION
        StyleParam.REFLECTION -> PrefKeys.REFLECTION
        StyleParam.RIM -> PrefKeys.RIM
        StyleParam.SHADOW -> PrefKeys.SHADOW
    }

    fun default(param: StyleParam): Float = when (param) {
        StyleParam.BLUR_RADIUS -> PrefKeys.DEFAULT_BLUR_RADIUS
        else -> PrefKeys.DEFAULT_STYLE
    }

    fun key(surface: GlassSurface): String = when (surface) {
        GlassSurface.TILES -> PrefKeys.TILES
        GlassSurface.FOOTER -> PrefKeys.FOOTER
        GlassSurface.BRIGHTNESS -> PrefKeys.BRIGHTNESS
        GlassSurface.MEDIA -> PrefKeys.MEDIA
        GlassSurface.NOTIFICATIONS -> PrefKeys.NOTIFICATIONS
        GlassSurface.HEADS_UP -> PrefKeys.HEADS_UP
        GlassSurface.LOCKSCREEN_NOTIFICATIONS -> PrefKeys.LOCKSCREEN_NOTIFICATIONS
        GlassSurface.LOCKSCREEN_SHORTCUTS -> PrefKeys.LOCKSCREEN_SHORTCUTS
        GlassSurface.BOUNCER -> PrefKeys.BOUNCER
        GlassSurface.VOLUME -> PrefKeys.VOLUME
        GlassSurface.POWER_MENU -> PrefKeys.POWER_MENU
        GlassSurface.SWITCHES -> PrefKeys.SWITCHES
        GlassSurface.SLIDERS -> PrefKeys.SLIDERS
        GlassSurface.BUTTONS -> PrefKeys.BUTTONS
        GlassSurface.VOLUME_PANEL -> PrefKeys.VOLUME_PANEL
        GlassSurface.DIALOGS -> PrefKeys.DIALOGS
        GlassSurface.TOASTS -> PrefKeys.TOASTS
        GlassSurface.BACK_GESTURE -> PrefKeys.BACK_GESTURE
        GlassSurface.STATUS_BAR_CHIPS -> PrefKeys.STATUS_BAR_CHIPS
        GlassSurface.TRANSITIONS -> PrefKeys.TRANSITIONS
        GlassSurface.SETTINGS_CARDS -> PrefKeys.SETTINGS_CARDS
        GlassSurface.INSTALLER_DIALOGS -> PrefKeys.INSTALLER_DIALOGS
    }

    fun key(surface: GlassSurface, param: StyleParam): String = PrefKeys.styleOverride(key(surface), key(param))
}
