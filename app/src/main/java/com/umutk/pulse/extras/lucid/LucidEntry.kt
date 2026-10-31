package com.umutk.pulse.extras.lucid

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umutk.pulse.extras.lucid.data.prefs.AccentColor
import com.umutk.pulse.extras.lucid.data.prefs.AppFontStyle
import com.umutk.pulse.extras.lucid.data.prefs.BackgroundPalette
import com.umutk.pulse.extras.lucid.data.prefs.SettingsDataStore
import com.umutk.pulse.extras.lucid.data.prefs.ThemeMode
import com.umutk.pulse.extras.lucid.receiver.AlarmReceiver
import com.umutk.pulse.extras.lucid.receiver.AlarmScheduler
import com.umutk.pulse.extras.lucid.receiver.AwakeCheckReceiver
import com.umutk.pulse.extras.lucid.service.AlarmAudioService
import com.umutk.pulse.extras.lucid.ui.ClockAppScaffold
import com.umutk.pulse.extras.lucid.ui.theme.LucidAlarmTheme
import com.umutk.pulse.extras.lucid.ui.viewmodel.AlarmViewModel
import com.umutk.pulse.extras.lucid.ui.viewmodel.WorldClockViewModel

/**
 * Lucid Alarm inside Pulse. The alarms, the stopwatch, the timer, the world clock and the whole look (its own themes, fonts, wallpapers, ring screen) come from
 * Lucid Alarm by DarkAspect1 (GPL-3.0, https://github.com/DarkAspect1/Lucid-Alarm), copied into this package with the package name changed. Two things differ:
 * the alarms are kept in a JSON file instead of Room (see data/AppDatabase.kt), and the window is Pulse's own tool window instead of Lucid's own activity.
 */
@Composable
fun LucidClockScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    // registered first, so the screens of Lucid that handle Back themselves come before it
    BackHandler { back() }
    val settingsDataStore = remember { SettingsDataStore(ctx.applicationContext) }
    val scheduler = remember { AlarmScheduler(ctx.applicationContext) }
    val alarmViewModel: AlarmViewModel = viewModel()
    val worldClockViewModel: WorldClockViewModel = viewModel()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        // with root the alarm permissions are granted here, first; the settings pages below only open for what is still missing
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { scheduler.grantByRoot() }
        registerLucidChannels(ctx)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!scheduler.canScheduleExactAlarms()) runCatching { ctx.startActivity(scheduler.requestExactAlarmPermissionIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (Build.VERSION.SDK_INT >= 34 && !scheduler.canUseFullScreenIntent()) runCatching { scheduler.requestFullScreenIntentPermissionIntent()?.let { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        // with "display over other apps" the alarm screen also opens while the phone is in use
        if (!scheduler.canDrawOverApps()) runCatching { ctx.startActivity(scheduler.requestDrawOverAppsIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val appFontStyle by settingsDataStore.appFontStyleFlow.collectAsState(initial = AppFontStyle.SYSTEM_DEFAULT)
    val accentColor by settingsDataStore.accentColorFlow.collectAsState(initial = AccentColor.DEFAULT)
    androidx.compose.runtime.LaunchedEffect(Unit) { com.umutk.pulse.extras.lucid.ui.theme.LucidLookState.load(androidx.compose.ui.platform.LocalContext.current) }
    val backgroundPalette by settingsDataStore.backgroundPaletteFlow.collectAsState(initial = BackgroundPalette.OBSIDIAN)
    val uiScale by settingsDataStore.uiScaleFlow.collectAsState(initial = SettingsDataStore.DEFAULT_UI_SCALE)
    val themeMode by settingsDataStore.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> systemDark }
    var nav by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density = base.density * uiScale, fontScale = base.fontScale * uiScale)) {
        LucidAlarmTheme(accentColor = accentColor, backgroundPalette = backgroundPalette, appFontStyle = appFontStyle, dark = dark, settingsDataStore = settingsDataStore) {
            ClockAppScaffold(alarmViewModel = alarmViewModel, worldClockViewModel = worldClockViewModel, settingsDataStore = settingsDataStore, navRequest = nav, onNavRequestHandled = { nav = null })
        }
    }
}

/** The three notification channels Lucid's alarms use (firing, upcoming, awake check). */
fun registerLucidChannels(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    runCatching { nm.deleteNotificationChannel("lucid_high_priority_alarm_channel") }
    val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    val sound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM) ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
    if (Build.VERSION.SDK_INT < 26) return
    val firing = NotificationChannel(AlarmAudioService.ALARM_CHANNEL_ID, "Firing Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "High priority alarm notifications and lock screen wakeups"; enableVibration(true); vibrationPattern = longArrayOf(0, 500, 250, 500)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC; setBypassDnd(true); setSound(sound, attrs)
    }
    val upcoming = NotificationChannel(AlarmReceiver.UPCOMING_CHANNEL_ID, "Upcoming Alarms", NotificationManager.IMPORTANCE_LOW).apply { description = "Heads-up notification before scheduled alarms fire"; setShowBadge(false) }
    val awake = NotificationChannel(AwakeCheckReceiver.CHANNEL_ID, "Awake Check", NotificationManager.IMPORTANCE_HIGH).apply { description = "Post-dismiss check confirming you are awake"; setBypassDnd(true) }
    nm.createNotificationChannels(listOf(firing, upcoming, awake))
}
