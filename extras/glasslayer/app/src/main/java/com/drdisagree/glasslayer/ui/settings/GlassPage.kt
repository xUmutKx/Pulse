package com.drdisagree.glasslayer.ui.settings

import androidx.annotation.StringRes
import com.drdisagree.glasslayer.R
import com.drdisagree.glasslayer.domain.model.GlassLevel
import com.drdisagree.glasslayer.domain.model.GlassSurface
import com.drdisagree.glasslayer.domain.model.GlassToggle
import com.drdisagree.glasslayer.domain.model.StyleParam

enum class GlassPage(
    @param:StringRes val title: Int,
    @param:StringRes val pageTitle: Int,
    @param:StringRes val summary: Int,
    val toggle: GlassToggle?,
    val surface: GlassSurface?,
    val hasStyle: Boolean,
    val levels: List<GlassLevel>,
    val styleParams: List<StyleParam> = StyleParam.entries,
    val options: List<GlassToggle> = emptyList()
) {

    STYLE(R.string.page_style, R.string.page_style_title, R.string.page_style_summary, null, null, true, emptyList()),
    PERFORMANCE(R.string.page_performance, R.string.page_performance, R.string.page_performance_summary, null, null, false, emptyList()),
    BACKGROUND(
        R.string.mod_background,
        R.string.page_background_title,
        R.string.mod_background_summary,
        GlassToggle.SHADE_BACKGROUND,
        null,
        false,
        listOf(GlassLevel.BACKGROUND_DIM, GlassLevel.BACKGROUND_BLUR)
    ),
    TILES(
        R.string.mod_tiles,
        R.string.page_tiles_title,
        R.string.mod_tiles_summary,
        GlassToggle.TILES,
        GlassSurface.TILES,
        true,
        listOf(GlassLevel.TILE_TINT, GlassLevel.ACTIVE_TILE_TINT)
    ),
    FOOTER(
        R.string.mod_footer,
        R.string.page_footer_title,
        R.string.mod_footer_summary,
        GlassToggle.FOOTER,
        GlassSurface.FOOTER,
        true,
        listOf(GlassLevel.FOOTER_TINT, GlassLevel.ACTIVE_FOOTER_TINT)
    ),
    BRIGHTNESS(
        R.string.mod_brightness,
        R.string.page_brightness_title,
        R.string.mod_brightness_summary,
        GlassToggle.BRIGHTNESS,
        GlassSurface.BRIGHTNESS,
        true,
        listOf(GlassLevel.BRIGHTNESS_TINT, GlassLevel.ACTIVE_BRIGHTNESS_TINT)
    ),
    MEDIA(
        R.string.mod_media,
        R.string.page_media_title,
        R.string.mod_media_summary,
        GlassToggle.MEDIA,
        GlassSurface.MEDIA,
        true,
        listOf(GlassLevel.MEDIA_TINT)
    ),
    NOTIFICATIONS(
        R.string.mod_notifications,
        R.string.page_notifications_title,
        R.string.mod_notifications_summary,
        GlassToggle.NOTIFICATIONS,
        GlassSurface.NOTIFICATIONS,
        true,
        listOf(GlassLevel.NOTIFICATION_TINT, GlassLevel.NOTIFICATION_FOOTER_TINT)
    ),
    HEADS_UP(
        R.string.mod_heads_up,
        R.string.page_heads_up_title,
        R.string.mod_heads_up_summary,
        GlassToggle.HEADS_UP,
        GlassSurface.HEADS_UP,
        true,
        listOf(GlassLevel.HEADS_UP_TINT)
    ),
    LOCKSCREEN_NOTIFICATIONS(
        R.string.mod_lockscreen_notifications,
        R.string.page_lockscreen_notifications_title,
        R.string.mod_lockscreen_notifications_summary,
        GlassToggle.LOCKSCREEN_NOTIFICATIONS,
        GlassSurface.LOCKSCREEN_NOTIFICATIONS,
        true,
        listOf(GlassLevel.LOCKSCREEN_NOTIFICATION_TINT)
    ),
    LOCKSCREEN_SHORTCUTS(
        R.string.mod_lockscreen_shortcuts,
        R.string.page_lockscreen_shortcuts_title,
        R.string.mod_lockscreen_shortcuts_summary,
        GlassToggle.LOCKSCREEN_SHORTCUTS,
        GlassSurface.LOCKSCREEN_SHORTCUTS,
        true,
        listOf(GlassLevel.LOCKSCREEN_SHORTCUT_TINT)
    ),
    BOUNCER(
        R.string.mod_bouncer,
        R.string.page_bouncer_title,
        R.string.mod_bouncer_summary,
        GlassToggle.BOUNCER,
        GlassSurface.BOUNCER,
        true,
        listOf(GlassLevel.BOUNCER_TINT)
    ),
    VOLUME(
        R.string.mod_volume,
        R.string.page_volume_title,
        R.string.mod_volume_summary,
        GlassToggle.VOLUME,
        GlassSurface.VOLUME,
        true,
        listOf(GlassLevel.VOLUME_TINT, GlassLevel.VOLUME_SLIDER_TINT, GlassLevel.ACTIVE_VOLUME_SLIDER_TINT)
    ),
    POWER_MENU(
        R.string.mod_power_menu,
        R.string.page_power_menu_title,
        R.string.mod_power_menu_summary,
        GlassToggle.POWER_MENU,
        GlassSurface.POWER_MENU,
        true,
        listOf(GlassLevel.POWER_MENU_TINT, GlassLevel.POWER_MENU_BUTTON_TINT, GlassLevel.POWER_MENU_ACCENT_TINT)
    ),
    SWITCHES(
        R.string.mod_switches,
        R.string.page_switches_title,
        R.string.mod_switches_summary,
        GlassToggle.SWITCHES,
        GlassSurface.SWITCHES,
        true,
        listOf(GlassLevel.SWITCH_TINT),
        options = listOf(GlassToggle.SWITCH_PRESS)
    ),
    SLIDERS(
        R.string.mod_sliders,
        R.string.page_sliders_title,
        R.string.mod_sliders_summary,
        GlassToggle.SLIDERS,
        GlassSurface.SLIDERS,
        false,
        emptyList()
    ),
    VOLUME_PANEL(
        R.string.mod_volume_panel,
        R.string.page_volume_panel_title,
        R.string.mod_volume_panel_summary,
        GlassToggle.VOLUME_PANEL,
        GlassSurface.VOLUME_PANEL,
        true,
        listOf(GlassLevel.VOLUME_PANEL_TINT, GlassLevel.VOLUME_PANEL_BUTTON_TINT, GlassLevel.ACTIVE_VOLUME_PANEL_BUTTON_TINT, GlassLevel.VOLUME_PANEL_SLIDER_TINT, GlassLevel.ACTIVE_VOLUME_PANEL_SLIDER_TINT)
    ),
    DIALOGS(
        R.string.mod_dialogs,
        R.string.page_dialogs_title,
        R.string.mod_dialogs_summary,
        GlassToggle.DIALOGS,
        GlassSurface.DIALOGS,
        true,
        listOf(GlassLevel.DIALOG_TINT, GlassLevel.DIALOG_BUTTON_TINT, GlassLevel.DIALOG_ACCENT_TINT, GlassLevel.DIALOG_ACTIVE_TINT)
    ),
    BUTTONS(
        R.string.mod_buttons,
        R.string.page_buttons_title,
        R.string.mod_buttons_summary,
        GlassToggle.BUTTONS,
        GlassSurface.BUTTONS,
        true,
        listOf(GlassLevel.BUTTON_TINT),
        options = listOf(GlassToggle.BUTTON_PRESS)
    ),
    BACK_GESTURE(
        R.string.mod_back_gesture,
        R.string.page_back_gesture_title,
        R.string.mod_back_gesture_summary,
        GlassToggle.BACK_GESTURE,
        GlassSurface.BACK_GESTURE,
        true,
        listOf(GlassLevel.BACK_GESTURE_TINT)
    ),
    TOASTS(
        R.string.mod_toasts,
        R.string.page_toasts_title,
        R.string.mod_toasts_summary,
        GlassToggle.TOASTS,
        GlassSurface.TOASTS,
        true,
        listOf(GlassLevel.TOAST_TINT)
    ),
    STATUS_BAR_CHIPS(
        R.string.mod_status_bar_chips,
        R.string.page_status_bar_chips_title,
        R.string.mod_status_bar_chips_summary,
        GlassToggle.STATUS_BAR_CHIPS,
        GlassSurface.STATUS_BAR_CHIPS,
        true,
        listOf(GlassLevel.STATUS_BAR_CHIP_TINT)
    ),
    TRANSITIONS(
        R.string.mod_transitions,
        R.string.page_transitions_title,
        R.string.mod_transitions_summary,
        GlassToggle.TRANSITIONS,
        GlassSurface.TRANSITIONS,
        true,
        listOf(GlassLevel.TRANSITION_TINT)
    ),
    SETTINGS_CARDS(
        R.string.mod_settings_cards,
        R.string.page_settings_cards_title,
        R.string.mod_settings_cards_summary,
        GlassToggle.SETTINGS_CARDS,
        GlassSurface.SETTINGS_CARDS,
        true,
        listOf(GlassLevel.SETTINGS_CARD_TINT, GlassLevel.SETTINGS_ACCENT_TINT, GlassLevel.SETTINGS_ICON_TINT),
        listOf(StyleParam.REFLECTION, StyleParam.RIM, StyleParam.SHADOW)
    ),
    INSTALLER_DIALOGS(
        R.string.mod_installer_dialogs,
        R.string.page_installer_dialogs_title,
        R.string.mod_installer_dialogs_summary,
        GlassToggle.INSTALLER_DIALOGS,
        GlassSurface.INSTALLER_DIALOGS,
        true,
        listOf(GlassLevel.INSTALLER_DIALOG_TINT),
        listOf(StyleParam.BLUR_RADIUS, StyleParam.REFLECTION, StyleParam.RIM)
    );

    val hasSettings: Boolean
        get() = hasStyle || levels.isNotEmpty() || options.isNotEmpty()
}
