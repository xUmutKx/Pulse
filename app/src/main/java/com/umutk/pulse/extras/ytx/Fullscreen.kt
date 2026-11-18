package com.umutk.pulse.extras.ytx

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat










@Stable
class OrientationController(private val activity: Activity) {
    private enum class Forced { Landscape, Portrait }

    private var forced by mutableStateOf<Forced?>(null)
    private var playerOpen = false

    private val listener = object : OrientationEventListener(activity) {
        override fun onOrientationChanged(degrees: Int) {
            if (degrees == ORIENTATION_UNKNOWN) return
            val physicallyLandscape = degrees in 60..120 || degrees in 240..300
            val physicallyPortrait = degrees <= 30 || degrees >= 330
            when {


                forced == Forced.Landscape && physicallyLandscape && autoRotateOn() -> forced = null
                // with the phone's rotation lock on, the forced portrait stays (the lock may be on landscape and would turn the page sideways again)
                forced == Forced.Portrait && physicallyPortrait && autoRotateOn() -> forced = null
            }
        }
    }

    private fun autoRotateOn() =
        Settings.System.getInt(activity.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    fun enterFullscreen() { forced = Forced.Landscape }

    /** Leave the player's forced orientation and follow the device again. */
    fun exitFullscreen() {
        // leaving full screen goes back to upright even when the rotation is locked (to landscape or portrait); with auto-rotate on the device is followed
        forced = if (autoRotateOn()) null else Forced.Portrait
        apply()
    }

    fun setPlayerOpen(open: Boolean) {
        playerOpen = open
        if (!open) forced = null
        apply()
    }

    fun apply() {
        if (TvMode.isTv) {
            // TVs are physically landscape and must never be rotated into portrait.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            return
        }
        activity.requestedOrientation = when {
            // Browsing must follow the device: tablets and foldables should be able
            // to stay in landscape instead of being locked to portrait.
            !playerOpen -> ActivityInfo.SCREEN_ORIENTATION_USER
            forced == Forced.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            forced == Forced.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    @Composable
    fun Bind(playerOpen: Boolean) {
        DisposableEffect(Unit) {
            if (listener.canDetectOrientation()) listener.enable()
            onDispose { listener.disable() }
        }
        LaunchedEffect(playerOpen, forced) { setPlayerOpen(playerOpen) }
    }
}

@Composable
fun rememberOrientationController(activity: Activity) = remember(activity) { OrientationController(activity) }


@Composable
fun SystemBarsHidden(hidden: Boolean) {
    val view = LocalView.current
    val dark = isDarkTheme()
    DisposableEffect(hidden, dark) {
        val activity = view.context as? ComponentActivity ?: (view.context as? Activity)
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        fun restoreBars() {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            controller?.isAppearanceLightStatusBars = !dark
            controller?.isAppearanceLightNavigationBars = !dark
            if (activity is ComponentActivity) {
                val barStyle = if (dark) {
                    androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                runCatching {
                    activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                    controller?.isAppearanceLightStatusBars = !dark
                    controller?.isAppearanceLightNavigationBars = !dark
                }
            }
        }

        if (hidden) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            restoreBars()
        }
        onDispose { restoreBars() }
    }
}






@Stable
class PipController(private val activity: ComponentActivity) {
    var active by mutableStateOf(activity.isInPictureInPictureMode)
        private set
    val supported = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    var videoBounds: Rect? = null

    /** Called with "toggle", "next" or "audio" when a button of the picture-in-picture window is pressed. */
    var onAction: (String) -> Unit = {}

    /** Whether the video plays now: the middle button shows pause or play. */
    var playing by mutableStateOf(false)

    /** The buttons of the window: the headphones (play on in the background, the window closes), play / pause and next. They take the place of the phone's own media buttons. */
    private fun remoteActions(): List<android.app.RemoteAction> {
        fun act(op: String, icon: Int, title: String, code: Int): android.app.RemoteAction {
            val pi = android.app.PendingIntent.getBroadcast(activity, code, android.content.Intent(PIP_ACTION).setPackage(activity.packageName).putExtra("op", op),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            return android.app.RemoteAction(android.graphics.drawable.Icon.createWithResource(activity, icon), title, title, pi)
        }
        return listOf(
            act("audio", com.umutk.pulse.R.drawable.ytx_pip_headphones, "Play in the background", 1),
            act("toggle", if (playing) com.umutk.pulse.R.drawable.ytx_pip_pause else com.umutk.pulse.R.drawable.ytx_pip_play, if (playing) "Pause" else "Play", 2),
            act("next", com.umutk.pulse.R.drawable.ytx_pip_next, "Next", 3),
        )
    }

    /** The window is open: its buttons are drawn again (the middle one follows play and pause). */
    fun refresh() {
        if (!supported) return
        runCatching { activity.setPictureInPictureParams(PictureInPictureParams.Builder().setActions(remoteActions()).build()) }
    }


    fun enter(videoAspect: Float): Boolean {
        if (!supported) return false

        val ratio = videoAspect.coerceIn(0.42f, 2.38f)
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
            .setActions(remoteActions())
            .apply {
                videoBounds?.let { b -> setSourceRectHint(android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())) }
            }
            .build()
        return activity.enterPictureInPictureMode(params)
    }

    fun updateAutoPip(enabled: Boolean, videoAspect: Float = 16f / 9f) {
        if (!supported) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val builder = PictureInPictureParams.Builder()
                .setAutoEnterEnabled(enabled)
                .setActions(remoteActions())
            if (enabled) {
                val ratio = videoAspect.coerceIn(0.42f, 2.38f)
                builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
                videoBounds?.let { b ->
                    builder.setSourceRectHint(android.graphics.Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
                }
            }
            activity.setPictureInPictureParams(builder.build())
        }
    }

    @Composable
    fun Bind(onClosed: () -> Unit) {
        // the buttons of the window come back as broadcasts of this app
        DisposableEffect(activity) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) { i?.getStringExtra("op")?.let(onAction) }
            }
            androidx.core.content.ContextCompat.registerReceiver(activity, receiver, android.content.IntentFilter(PIP_ACTION), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { runCatching { activity.unregisterReceiver(receiver) } }
        }
        DisposableEffect(activity) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                active = info.isInPictureInPictureMode

                if (!info.isInPictureInPictureMode && !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onClosed()
            }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
        }
    }
}


private const val PIP_ACTION = "com.umutk.pulse.ytx.PIP_ACTION"

val LocalPictureInPicture = staticCompositionLocalOf<PipController?> { null }
