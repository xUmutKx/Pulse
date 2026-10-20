package com.umutk.pulse.extras.music

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.umutk.pulse.extras.lyrics.LiveLyricsTicker
import com.umutk.pulse.extras.music.notif.NotifBtn
import com.umutk.pulse.extras.music.notif.NotifLayout
import com.umutk.pulse.extras.music.notif.NotifOutput
import com.umutk.pulse.extras.music.notif.NotifProvider
import com.umutk.pulse.extras.music.stats.MusicStats

/**
 * Keeps playback alive when the UI is gone: Media3 runs the foreground service and the media notification
 * while the player plays. The buttons come from [MusicSettings.notificationButtonIds]; custom ones are session commands
 * handled here. Needs the manifest entry (foregroundServiceType="mediaPlayback") in AndroidManifest.xml.
 */
class MusicPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val engine get() = MusicPlayerEngine.get(this)
    private val devices by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private var lyricsTicker: LiveLyricsTicker? = null
    private val btAutoplay = BluetoothAutoplayReceiver()

    private fun layout(): ImmutableList<androidx.media3.session.CommandButton> =
        NotifLayout.customLayout(this, MusicSettings.notificationButtonIds(this), engine.player)

    internal fun refresh() { session?.setCustomLayout(layout()) }

    private val devCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refresh()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) refresh()
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(s: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().also { b ->
                NotifBtn.values().filter { !it.builtin }.forEach { b.add(SessionCommand(NotifBtn.action(it), Bundle.EMPTY)) }
            }.build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(s).setAvailableSessionCommands(cmds).setCustomLayout(layout()).build()
        }

        override fun onCustomCommand(s: MediaSession, controller: MediaSession.ControllerInfo, cmd: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            NotifBtn.values().firstOrNull { NotifBtn.action(it) == cmd.customAction }?.let { run(it) }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun run(b: NotifBtn) {
        val p = engine.player
        when (b) {
            NotifBtn.SHUFFLE -> p.shuffleModeEnabled = !p.shuffleModeEnabled
            NotifBtn.REPEAT -> p.repeatMode = when (p.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            NotifBtn.FAV -> p.currentMediaItem?.mediaId?.let { MusicStats.toggleFavourite(this, it) }
            NotifBtn.CLOSE -> {
                engine.saveResume()
                // the user closed it: the watchdog must not bring it back
                com.umutk.pulse.extras.watchdog.ServiceWatchdog.setWanted(this, com.umutk.pulse.extras.watchdog.MusicPlaybackService::class.java, false)
                p.stop(); p.clearMediaItems()
                pauseAllPlayersAndStopSelf()
            }
            NotifBtn.OUTPUT -> NotifOutput.cycle(this)
            NotifBtn.SLEEP -> if (engine.sleepLeftMs.value != 0L) engine.setSleep(0) else engine.setSleep(30)
            NotifBtn.SPEED -> { MusicSettings.setSpeed(this, NotifLayout.nextSpeed(p.playbackParameters.speed)); engine.reloadSettings() }
            NotifBtn.REW -> p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0L))
            NotifBtn.FWD -> p.seekTo(if (p.duration > 0) minOf(p.currentPosition + 30_000L, p.duration) else p.currentPosition + 30_000L)
            // Needs the app visible or the overlay permission: Android blocks activity starts from a notification action otherwise.
            NotifBtn.LYRICS, NotifBtn.NOISE, NotifBtn.EQ -> runCatching { startActivity(hubIntent(b.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            else -> {}
        }
        refresh()
    }

    /** Opens the Music hub (the launcher alias routes to it); [open] names a sheet for the hub to show. */
    private fun hubIntent(open: String? = null) = Intent().setClassName(packageName, "$packageName.ExtraToolActivity").putExtra("route", "music").also { i -> open?.let { i.putExtra(EXTRA_OPEN, it) } }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(NotifProvider(this))
        val pi = PendingIntent.getActivity(this, 0, hubIntent().addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, engine.player).setCallback(callback).setSessionActivity(pi).setCustomLayout(layout()).build()
        engine.player.addListener(listener)
        devices.registerAudioDeviceCallback(devCb, null)
        ContextCompat.registerReceiver(this, btAutoplay, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED), ContextCompat.RECEIVER_NOT_EXPORTED)
        lyricsTicker = LiveLyricsTicker(this, engine) {
            session?.let { onUpdateNotification(it) }
            com.umutk.pulse.widgets.music.LyricsWidget.refresh(this)
        }.also { it.start() }
        live = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        live = null
        devices.unregisterAudioDeviceCallback(devCb)
        runCatching { unregisterReceiver(btAutoplay) }
        lyricsTicker?.stop(); lyricsTicker = null
        runCatching { engine.player.removeListener(listener) }
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN = "notif_open"
        private var live: MusicPlaybackService? = null
        /** Rebuilds the notification buttons after the settings changed. */
        fun relayout() { live?.refresh() }
    }
}
