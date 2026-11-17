package com.drdisagree.glasslayer.xposed.systemui

import android.content.res.Resources
import com.drdisagree.glasslayer.data.source.PrefKeys
import com.drdisagree.glasslayer.domain.model.BatterySaverAction
import com.drdisagree.glasslayer.domain.model.PerformanceMode
import com.drdisagree.glasslayer.domain.model.BatterySaverBackground
import com.drdisagree.glasslayer.domain.model.ThermalAction
import com.drdisagree.glasslayer.xposed.glass.ComposeLiquidGlass
import com.drdisagree.glasslayer.xposed.glass.LiquidGlassStyle
import com.drdisagree.glasslayer.xposed.prefs.ModulePrefs

object GlassConfig {

    private var masterEnabled = PrefKeys.DEFAULT_ENABLED

    @Volatile
    var powerPaused = false

    val enabled: Boolean
        get() = masterEnabled && !powerPaused
    var tileTint = PrefKeys.DEFAULT_TILE_TINT / 100f
        private set
    var activeTileTint = PrefKeys.DEFAULT_ACTIVE_TILE_TINT / 100f
        private set
    var footerTint = PrefKeys.DEFAULT_FOOTER_TINT / 100f
        private set
    var activeFooterTint = PrefKeys.DEFAULT_ACTIVE_FOOTER_TINT / 100f
        private set
    var brightnessTint = PrefKeys.DEFAULT_BRIGHTNESS_TINT / 100f
        private set
    var activeBrightnessTint = PrefKeys.DEFAULT_ACTIVE_BRIGHTNESS_TINT / 100f
        private set
    var mediaTint = PrefKeys.DEFAULT_MEDIA_TINT / 100f
        private set
    var backgroundDim = PrefKeys.DEFAULT_BACKGROUND_DIM / 100f
        private set
    var backgroundBlur = PrefKeys.DEFAULT_BACKGROUND_BLUR / 100f
        private set
    var tiles = PrefKeys.DEFAULT_MODULE
        private set
    var footer = PrefKeys.DEFAULT_MODULE
        private set
    var brightness = PrefKeys.DEFAULT_MODULE
        private set
    var media = PrefKeys.DEFAULT_MODULE
        private set
    var shadeBackground = PrefKeys.DEFAULT_MODULE
        private set
    var notifications = PrefKeys.DEFAULT_MODULE
        private set
    var notificationTint = PrefKeys.DEFAULT_NOTIFICATION_TINT / 100f
        private set
    var notificationFooterTint = PrefKeys.DEFAULT_NOTIFICATION_FOOTER_TINT / 100f
        private set
    var headsUp = PrefKeys.DEFAULT_MODULE
        private set
    var headsUpTint = PrefKeys.DEFAULT_HEADS_UP_TINT / 100f
        private set
    var lockscreenNotifications = PrefKeys.DEFAULT_LOCKSCREEN
        private set
    var lockscreenNotificationTint = PrefKeys.DEFAULT_LOCKSCREEN_NOTIFICATION_TINT / 100f
        private set
    var lockscreenShortcuts = PrefKeys.DEFAULT_LOCKSCREEN
        private set
    var lockscreenShortcutTint = PrefKeys.DEFAULT_LOCKSCREEN_SHORTCUT_TINT / 100f
        private set
    var bouncer = PrefKeys.DEFAULT_LOCKSCREEN
        private set
    var bouncerTint = PrefKeys.DEFAULT_BOUNCER_TINT / 100f
        private set
    var volume = PrefKeys.DEFAULT_MODULE
        private set
    var volumeTint = PrefKeys.DEFAULT_VOLUME_TINT / 100f
        private set
    var volumeSliderTint = PrefKeys.DEFAULT_VOLUME_SLIDER_TINT / 100f
        private set
    var activeVolumeSliderTint = PrefKeys.DEFAULT_ACTIVE_VOLUME_SLIDER_TINT / 100f
        private set
    var powerMenu = PrefKeys.DEFAULT_MODULE
        private set
    var powerMenuTint = PrefKeys.DEFAULT_POWER_MENU_TINT / 100f
        private set
    var powerMenuButtonTint = PrefKeys.DEFAULT_POWER_MENU_BUTTON_TINT / 100f
        private set
    var powerMenuAccentTint = PrefKeys.DEFAULT_POWER_MENU_ACCENT_TINT / 100f
        private set
    var switches = PrefKeys.DEFAULT_MODULE
        private set
    var switchPress = PrefKeys.DEFAULT_MODULE
        private set
    var buttonPress = PrefKeys.DEFAULT_MODULE
        private set
    var captureFps = 60

    /** One UI edition: the glass frame rate while nothing moves (0 = no idle slow-down). */
    var idleFps = com.drdisagree.glasslayer.data.source.OneUiKeys.DEFAULT_IDLE_FPS
        private set
    var batterySaverAction = BatterySaverAction.ULTRA
        private set
    var thermalAction = ThermalAction.SAVER
    var batterySaverBackground = BatterySaverBackground.SEMI_TRANSPARENT
        private set
    var switchTint = PrefKeys.DEFAULT_SWITCH_TINT / 100f
        private set
    var sliders = PrefKeys.DEFAULT_MODULE
        private set
    var sliderTint = PrefKeys.DEFAULT_SLIDER_TINT / 100f
        private set
    var activeSliderTint = PrefKeys.DEFAULT_ACTIVE_SLIDER_TINT / 100f
        private set
    var buttons = PrefKeys.DEFAULT_MODULE
        private set
    var buttonTint = PrefKeys.DEFAULT_BUTTON_TINT / 100f
        private set
    var volumePanel = PrefKeys.DEFAULT_MODULE
        private set
    var volumePanelTint = PrefKeys.DEFAULT_VOLUME_PANEL_TINT / 100f
        private set
    var volumePanelButtonTint = PrefKeys.DEFAULT_VOLUME_PANEL_BUTTON_TINT / 100f
        private set
    var activeVolumePanelButtonTint = PrefKeys.DEFAULT_ACTIVE_VOLUME_PANEL_BUTTON_TINT / 100f
        private set
    var volumePanelSliderTint = PrefKeys.DEFAULT_VOLUME_PANEL_SLIDER_TINT / 100f
        private set
    var activeVolumePanelSliderTint = PrefKeys.DEFAULT_ACTIVE_VOLUME_PANEL_SLIDER_TINT / 100f
        private set
    var dialogs = PrefKeys.DEFAULT_MODULE
        private set
    var dialogTint = PrefKeys.DEFAULT_DIALOG_TINT / 100f
        private set
    var dialogButtonTint = PrefKeys.DEFAULT_DIALOG_BUTTON_TINT / 100f
        private set
    var dialogAccentTint = PrefKeys.DEFAULT_DIALOG_ACCENT_TINT / 100f
        private set
    var dialogActiveTint = PrefKeys.DEFAULT_DIALOG_ACTIVE_TINT / 100f
        private set
    var toasts = PrefKeys.DEFAULT_MODULE
        private set
    var toastTint = PrefKeys.DEFAULT_TOAST_TINT / 100f
    var backGesture = PrefKeys.DEFAULT_MODULE
    var backGestureTint = PrefKeys.DEFAULT_BACK_GESTURE_TINT / 100f
        private set
    var statusBarChips = PrefKeys.DEFAULT_MODULE
        private set
    var statusBarChipTint = PrefKeys.DEFAULT_STATUS_BAR_CHIP_TINT / 100f
        private set
    var transitions = PrefKeys.DEFAULT_MODULE
        private set
    var transitionTint = PrefKeys.DEFAULT_TRANSITION_TINT / 100f
        private set
    var settingsCards = PrefKeys.DEFAULT_MODULE
        private set
    var settingsCardTint = PrefKeys.DEFAULT_SETTINGS_CARD_TINT / 100f
        private set
    var settingsIconTint = PrefKeys.DEFAULT_SETTINGS_ICON_TINT / 100f
        private set
    var settingsAccentTint = PrefKeys.DEFAULT_SETTINGS_ACCENT_TINT / 100f
        private set
    var installerDialogs = PrefKeys.DEFAULT_MODULE
        private set
    var installerDialogTint = PrefKeys.DEFAULT_INSTALLER_DIALOG_TINT / 100f
        private set

    @Volatile
    private var global = SurfaceStyle(PrefKeys.DEFAULT_BLUR_RADIUS * density(), LiquidGlassStyle())

    @Volatile
    private var surfaces: Map<String, SurfaceStyle> = emptyMap()

    val isActive: Boolean
        get() = enabled && ComposeLiquidGlass.isAvailable

    val tilesActive: Boolean
        get() = isActive && tiles

    val footerActive: Boolean
        get() = isActive && footer

    val brightnessActive: Boolean
        get() = isActive && brightness

    val mediaActive: Boolean
        get() = isActive && media

    val shadeBackgroundActive: Boolean
        get() = isActive && shadeBackground

    val notificationsActive: Boolean
        get() = isActive && notifications

    val headsUpActive: Boolean
        get() = isActive && headsUp

    val lockscreenNotificationsActive: Boolean
        get() = isActive && lockscreenNotifications && !DozeTracker.isDozing

    val lockscreenShortcutsActive: Boolean
        get() = isActive && lockscreenShortcuts && !DozeTracker.isDozing

    val bouncerActive: Boolean
        get() = isActive && bouncer

    val volumeActive: Boolean
        get() = isActive && volume

    val powerMenuActive: Boolean
        get() = isActive && powerMenu

    val switchesActive: Boolean
        get() = enabled && switches

    val slidersActive: Boolean
        get() = enabled && sliders

    val buttonsActive: Boolean
        get() = enabled && buttons

    val volumePanelActive: Boolean
        get() = isActive && volumePanel

    val dialogsActive: Boolean
        get() = isActive && dialogs

    val toastsActive: Boolean
        get() = isActive && toasts

    val backGestureActive: Boolean
        get() = isActive && backGesture

    val statusBarChipsActive: Boolean
        get() = isActive && statusBarChips

    val transitionsActive: Boolean
        get() = isActive && transitions

    val settingsCardsActive: Boolean
        get() = enabled && settingsCards

    val installerDialogsActive: Boolean
        get() = enabled && installerDialogs

    val glassAvailable: Boolean
        get() = liveGlass && isActive

    var liveGlass = true

    val lockscreenShortcutsEnabled: Boolean
        get() = isActive && lockscreenShortcuts

    fun surface(key: String): SurfaceStyle = surfaces[key] ?: global

    fun reload() {
        masterEnabled = ModulePrefs.boolean(PrefKeys.ENABLED, PrefKeys.DEFAULT_ENABLED)
        val blurDp = ModulePrefs.float(PrefKeys.BLUR_RADIUS, PrefKeys.DEFAULT_BLUR_RADIUS)
        val refraction = ModulePrefs.float(PrefKeys.REFRACTION, PrefKeys.DEFAULT_STYLE)
        val reflection = ModulePrefs.float(PrefKeys.REFLECTION, PrefKeys.DEFAULT_STYLE)
        val rim = ModulePrefs.float(PrefKeys.RIM, PrefKeys.DEFAULT_STYLE)
        val shadow = ModulePrefs.float(PrefKeys.SHADOW, PrefKeys.DEFAULT_STYLE)
        global = styleOf(blurDp, refraction, reflection, rim, shadow)
        surfaces = PrefKeys.STYLE_SURFACES.associateWith { surface ->
            styleOf(
                override(surface, PrefKeys.BLUR_RADIUS, blurDp),
                override(surface, PrefKeys.REFRACTION, refraction),
                override(surface, PrefKeys.REFLECTION, reflection),
                override(surface, PrefKeys.RIM, rim),
                override(surface, PrefKeys.SHADOW, shadow)
            )
        }
        tileTint = percent(PrefKeys.TILE_TINT, PrefKeys.DEFAULT_TILE_TINT)
        activeTileTint = percent(PrefKeys.ACTIVE_TILE_TINT, PrefKeys.DEFAULT_ACTIVE_TILE_TINT)
        footerTint = percent(PrefKeys.FOOTER_TINT, PrefKeys.DEFAULT_FOOTER_TINT)
        activeFooterTint = percent(PrefKeys.ACTIVE_FOOTER_TINT, PrefKeys.DEFAULT_ACTIVE_FOOTER_TINT)
        brightnessTint = percent(PrefKeys.BRIGHTNESS_TINT, PrefKeys.DEFAULT_BRIGHTNESS_TINT)
        activeBrightnessTint = percent(PrefKeys.ACTIVE_BRIGHTNESS_TINT, PrefKeys.DEFAULT_ACTIVE_BRIGHTNESS_TINT)
        mediaTint = percent(PrefKeys.MEDIA_TINT, PrefKeys.DEFAULT_MEDIA_TINT)
        backgroundDim = percent(PrefKeys.BACKGROUND_DIM, PrefKeys.DEFAULT_BACKGROUND_DIM)
        backgroundBlur = percent(PrefKeys.BACKGROUND_BLUR, PrefKeys.DEFAULT_BACKGROUND_BLUR)
        tiles = ModulePrefs.boolean(PrefKeys.TILES, PrefKeys.DEFAULT_MODULE)
        footer = ModulePrefs.boolean(PrefKeys.FOOTER, PrefKeys.DEFAULT_MODULE)
        brightness = ModulePrefs.boolean(PrefKeys.BRIGHTNESS, PrefKeys.DEFAULT_MODULE)
        media = ModulePrefs.boolean(PrefKeys.MEDIA, PrefKeys.DEFAULT_MODULE)
        shadeBackground = ModulePrefs.boolean(PrefKeys.SHADE_BACKGROUND, PrefKeys.DEFAULT_MODULE)
        notifications = ModulePrefs.boolean(PrefKeys.NOTIFICATIONS, PrefKeys.DEFAULT_MODULE)
        notificationTint = percent(PrefKeys.NOTIFICATION_TINT, PrefKeys.DEFAULT_NOTIFICATION_TINT)
        notificationFooterTint =
            percent(PrefKeys.NOTIFICATION_FOOTER_TINT, PrefKeys.DEFAULT_NOTIFICATION_FOOTER_TINT)
        headsUp = ModulePrefs.boolean(PrefKeys.HEADS_UP, PrefKeys.DEFAULT_MODULE)
        headsUpTint = percent(PrefKeys.HEADS_UP_TINT, PrefKeys.DEFAULT_HEADS_UP_TINT)
        lockscreenNotifications =
            ModulePrefs.boolean(PrefKeys.LOCKSCREEN_NOTIFICATIONS, PrefKeys.DEFAULT_LOCKSCREEN)
        lockscreenNotificationTint =
            percent(PrefKeys.LOCKSCREEN_NOTIFICATION_TINT, PrefKeys.DEFAULT_LOCKSCREEN_NOTIFICATION_TINT)
        lockscreenShortcuts = ModulePrefs.boolean(PrefKeys.LOCKSCREEN_SHORTCUTS, PrefKeys.DEFAULT_LOCKSCREEN)
        lockscreenShortcutTint =
            percent(PrefKeys.LOCKSCREEN_SHORTCUT_TINT, PrefKeys.DEFAULT_LOCKSCREEN_SHORTCUT_TINT)
        bouncer = ModulePrefs.boolean(PrefKeys.BOUNCER, PrefKeys.DEFAULT_LOCKSCREEN)
        bouncerTint = percent(PrefKeys.BOUNCER_TINT, PrefKeys.DEFAULT_BOUNCER_TINT)
        volume = ModulePrefs.boolean(PrefKeys.VOLUME, PrefKeys.DEFAULT_MODULE)
        volumeTint = percent(PrefKeys.VOLUME_TINT, PrefKeys.DEFAULT_VOLUME_TINT)
        volumeSliderTint = percent(PrefKeys.VOLUME_SLIDER_TINT, PrefKeys.DEFAULT_VOLUME_SLIDER_TINT)
        activeVolumeSliderTint = percent(PrefKeys.ACTIVE_VOLUME_SLIDER_TINT, PrefKeys.DEFAULT_ACTIVE_VOLUME_SLIDER_TINT)
        powerMenu = ModulePrefs.boolean(PrefKeys.POWER_MENU, PrefKeys.DEFAULT_MODULE)
        powerMenuTint = percent(PrefKeys.POWER_MENU_TINT, PrefKeys.DEFAULT_POWER_MENU_TINT)
        powerMenuButtonTint = percent(PrefKeys.POWER_MENU_BUTTON_TINT, PrefKeys.DEFAULT_POWER_MENU_BUTTON_TINT)
        powerMenuAccentTint = percent(PrefKeys.POWER_MENU_ACCENT_TINT, PrefKeys.DEFAULT_POWER_MENU_ACCENT_TINT)
        switches = ModulePrefs.boolean(PrefKeys.SWITCHES, PrefKeys.DEFAULT_MODULE)
        switchPress = ModulePrefs.boolean(PrefKeys.SWITCH_PRESS, PrefKeys.DEFAULT_MODULE)
        buttonPress = ModulePrefs.boolean(PrefKeys.BUTTON_PRESS, PrefKeys.DEFAULT_MODULE)
        // Balanced (60 fps) by default in the One UI edition: Full follows a 120 Hz screen and was the lag
        captureFps = PerformanceMode.entries.getOrNull(ModulePrefs.float(PrefKeys.PERFORMANCE_MODE, 1f).toInt())?.maxFps ?: 60
        idleFps = ModulePrefs.float(com.drdisagree.glasslayer.data.source.OneUiKeys.IDLE_FPS, com.drdisagree.glasslayer.data.source.OneUiKeys.DEFAULT_IDLE_FPS.toFloat()).toInt().coerceIn(0, 30)
        batterySaverAction = ModulePrefs.float(PrefKeys.BATTERY_SAVER_ACTION, BatterySaverAction.ULTRA.code.toFloat()).toInt()
            .let { code -> BatterySaverAction.entries.firstOrNull { it.code == code } } ?: BatterySaverAction.ULTRA
        thermalAction = ThermalAction.entries.getOrNull(
            ModulePrefs.float(PrefKeys.THERMAL_ACTION, ThermalAction.SAVER.ordinal.toFloat()).toInt()
        ) ?: ThermalAction.SAVER
        batterySaverBackground = BatterySaverBackground.entries.getOrNull(
            ModulePrefs.float(PrefKeys.BATTERY_SAVER_BACKGROUND, BatterySaverBackground.SEMI_TRANSPARENT.ordinal.toFloat()).toInt()
        ) ?: BatterySaverBackground.SEMI_TRANSPARENT
        switchTint = percent(PrefKeys.SWITCH_TINT, PrefKeys.DEFAULT_SWITCH_TINT)
        sliders = ModulePrefs.boolean(PrefKeys.SLIDERS, PrefKeys.DEFAULT_MODULE)
        sliderTint = percent(PrefKeys.SLIDER_TINT, PrefKeys.DEFAULT_SLIDER_TINT)
        activeSliderTint = percent(PrefKeys.ACTIVE_SLIDER_TINT, PrefKeys.DEFAULT_ACTIVE_SLIDER_TINT)
        buttons = ModulePrefs.boolean(PrefKeys.BUTTONS, PrefKeys.DEFAULT_MODULE)
        buttonTint = percent(PrefKeys.BUTTON_TINT, PrefKeys.DEFAULT_BUTTON_TINT)
        volumePanel = ModulePrefs.boolean(PrefKeys.VOLUME_PANEL, PrefKeys.DEFAULT_MODULE)
        volumePanelTint = percent(PrefKeys.VOLUME_PANEL_TINT, PrefKeys.DEFAULT_VOLUME_PANEL_TINT)
        volumePanelButtonTint = percent(PrefKeys.VOLUME_PANEL_BUTTON_TINT, PrefKeys.DEFAULT_VOLUME_PANEL_BUTTON_TINT)
        activeVolumePanelButtonTint = percent(PrefKeys.ACTIVE_VOLUME_PANEL_BUTTON_TINT, PrefKeys.DEFAULT_ACTIVE_VOLUME_PANEL_BUTTON_TINT)
        volumePanelSliderTint = percent(PrefKeys.VOLUME_PANEL_SLIDER_TINT, PrefKeys.DEFAULT_VOLUME_PANEL_SLIDER_TINT)
        activeVolumePanelSliderTint = percent(PrefKeys.ACTIVE_VOLUME_PANEL_SLIDER_TINT, PrefKeys.DEFAULT_ACTIVE_VOLUME_PANEL_SLIDER_TINT)
        dialogs = ModulePrefs.boolean(PrefKeys.DIALOGS, PrefKeys.DEFAULT_MODULE)
        dialogTint = percent(PrefKeys.DIALOG_TINT, PrefKeys.DEFAULT_DIALOG_TINT)
        dialogButtonTint = percent(PrefKeys.DIALOG_BUTTON_TINT, PrefKeys.DEFAULT_DIALOG_BUTTON_TINT)
        dialogAccentTint = percent(PrefKeys.DIALOG_ACCENT_TINT, PrefKeys.DEFAULT_DIALOG_ACCENT_TINT)
        dialogActiveTint = percent(PrefKeys.DIALOG_ACTIVE_TINT, PrefKeys.DEFAULT_DIALOG_ACTIVE_TINT)
        toasts = ModulePrefs.boolean(PrefKeys.TOASTS, PrefKeys.DEFAULT_MODULE)
        toastTint = percent(PrefKeys.TOAST_TINT, PrefKeys.DEFAULT_TOAST_TINT)
        backGesture = ModulePrefs.boolean(PrefKeys.BACK_GESTURE, PrefKeys.DEFAULT_MODULE)
        backGestureTint = percent(PrefKeys.BACK_GESTURE_TINT, PrefKeys.DEFAULT_BACK_GESTURE_TINT)
        statusBarChips = ModulePrefs.boolean(PrefKeys.STATUS_BAR_CHIPS, PrefKeys.DEFAULT_MODULE)
        statusBarChipTint = percent(PrefKeys.STATUS_BAR_CHIP_TINT, PrefKeys.DEFAULT_STATUS_BAR_CHIP_TINT)
        transitions = ModulePrefs.boolean(PrefKeys.TRANSITIONS, PrefKeys.DEFAULT_MODULE)
        transitionTint = percent(PrefKeys.TRANSITION_TINT, PrefKeys.DEFAULT_TRANSITION_TINT)
        settingsCards = ModulePrefs.boolean(PrefKeys.SETTINGS_CARDS, PrefKeys.DEFAULT_MODULE)
        settingsCardTint = percent(PrefKeys.SETTINGS_CARD_TINT, PrefKeys.DEFAULT_SETTINGS_CARD_TINT)
        settingsIconTint = percent(PrefKeys.SETTINGS_ICON_TINT, PrefKeys.DEFAULT_SETTINGS_ICON_TINT)
        settingsAccentTint = percent(PrefKeys.SETTINGS_ACCENT_TINT, PrefKeys.DEFAULT_SETTINGS_ACCENT_TINT)
        installerDialogs = ModulePrefs.boolean(PrefKeys.INSTALLER_DIALOGS, PrefKeys.DEFAULT_MODULE)
        installerDialogTint = percent(PrefKeys.INSTALLER_DIALOG_TINT, PrefKeys.DEFAULT_INSTALLER_DIALOG_TINT)
    }

    private fun styleOf(blurDp: Float, refraction: Float, reflection: Float, rim: Float, shadow: Float) =
        SurfaceStyle(
            blurRadiusPx = blurDp * density(),
            style = LiquidGlassStyle(
                refraction = refraction / 100f,
                reflection = reflection / 100f,
                rim = rim / 100f,
                shadow = shadow / 100f
            )
        )

    private fun override(surface: String, param: String, inherited: Float): Float {
        val value = ModulePrefs.float(PrefKeys.styleOverride(surface, param), PrefKeys.INHERIT)
        return if (value < 0f) inherited else value
    }

    private fun density(): Float = Resources.getSystem().displayMetrics.density

    private fun percent(key: String, default: Float): Float = ModulePrefs.float(key, default) / 100f
}
