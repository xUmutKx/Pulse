package com.drdisagree.glasslayer.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.drdisagree.glasslayer.R

enum class ModGroup(@param:StringRes val title: Int, @param:DrawableRes val icon: Int, val pages: List<GlassPage>) {
    QUICK_SETTINGS(
        R.string.group_quick_settings,
        R.drawable.ic_section_quick_settings,
        listOf(GlassPage.BACKGROUND, GlassPage.TILES, GlassPage.FOOTER, GlassPage.BRIGHTNESS, GlassPage.MEDIA)
    ),
    NOTIFICATIONS(
        R.string.group_notifications,
        R.drawable.ic_section_notifications,
        listOf(GlassPage.NOTIFICATIONS, GlassPage.HEADS_UP, GlassPage.STATUS_BAR_CHIPS, GlassPage.TOASTS)
    ),
    LOCKSCREEN(
        R.string.group_lockscreen,
        R.drawable.ic_section_lockscreen,
        listOf(GlassPage.LOCKSCREEN_NOTIFICATIONS, GlassPage.LOCKSCREEN_SHORTCUTS, GlassPage.BOUNCER)
    ),
    VOLUME(R.string.group_volume, R.drawable.ic_section_volume, listOf(GlassPage.VOLUME, GlassPage.VOLUME_PANEL)),
    DIALOGS(
        R.string.group_dialogs,
        R.drawable.ic_section_dialogs,
        listOf(GlassPage.DIALOGS, GlassPage.POWER_MENU, GlassPage.TRANSITIONS)
    ),
    NAVIGATION(
        R.string.group_navigation,
        R.drawable.ic_section_navigation,
        listOf(GlassPage.BACK_GESTURE)
    ),
    CONTROLS(
        R.string.group_controls,
        R.drawable.ic_section_controls,
        listOf(GlassPage.BUTTONS, GlassPage.SWITCHES, GlassPage.SLIDERS)
    ),
    SETTINGS_APP(
        R.string.group_settings_app,
        R.drawable.ic_section_system_apps,
        listOf(GlassPage.SETTINGS_CARDS, GlassPage.INSTALLER_DIALOGS)
    )
}
