package com.umutk.pulse.extras.music.notif

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.umutk.pulse.R
import com.umutk.pulse.extras.lyrics.LiveLyrics
import com.umutk.pulse.extras.music.MusicSettings

/**
 * Default Media3 notification (title, artist, cover, progress, lock screen) with the buttons the user picked, in the user's order.
 * When live lyrics for the notification are on, its text line is the current lyric line ([LiveLyrics.notifLine]).
 */
class NotifProvider(private val ctx: Context) : MediaNotification.Provider {
    private val base = Buttons(ctx).apply { setSmallIcon(R.drawable.ic_notif_music) }

    override fun createNotification(
        session: MediaSession, customLayout: ImmutableList<CommandButton>, actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val m = base.createNotification(session, customLayout, actionFactory, callback)
        val line = LiveLyrics.notifLine ?: return m
        if (android.os.Build.VERSION.SDK_INT < 24) return m
        val b = Notification.Builder.recoverBuilder(ctx, m.notification).setContentText(line)
        // the song title is underlined when that is the emphasis chosen, so it cannot be taken for the lyric line
        m.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.let { b.setContentTitle(LiveLyrics.titled(ctx, it)) }
        return MediaNotification(m.notificationId, b.build())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle) = base.handleCustomCommand(session, action, extras)

    private class Buttons(private val ctx: Context) : DefaultMediaNotificationProvider(
        ctx, DefaultMediaNotificationProvider.NotificationIdProvider { DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID }, QUIET_CHANNEL.also { ensureQuiet(ctx) }, R.string.app_name,
    ) {
        override fun getMediaButtons(
            session: MediaSession, playerCommands: Player.Commands, customLayout: ImmutableList<CommandButton>, showPauseButton: Boolean,
        ): ImmutableList<CommandButton> {
            var slot = 0
            val out = MusicSettings.notificationButtonIds(ctx).mapNotNull { NotifBtn.of(it) }.mapNotNull { b ->
                if (b.builtin) {
                    val ok = when (b) {
                        NotifBtn.PREV -> playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)
                        NotifBtn.NEXT -> playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)
                        else -> playerCommands.contains(Player.COMMAND_PLAY_PAUSE)
                    }
                    if (ok) NotifLayout.builtin(b, showPauseButton, if (slot < 3) slot++ else -1) else null
                } else customLayout.firstOrNull { it.sessionCommand?.customAction == NotifBtn.action(b) }
            }.take(NotifBtn.MAX)
            return ImmutableList.copyOf(out)
        }
    }
}

/** The media notification's own channel, made here so it carries no icon badge (the library's default channel does). */
private const val QUIET_CHANNEL = "pulse_media_quiet"
private fun ensureQuiet(ctx: Context) {
    if (android.os.Build.VERSION.SDK_INT >= 26) ctx.getSystemService(android.app.NotificationManager::class.java)
        .createNotificationChannel(com.umutk.pulse.QuietChannel(QUIET_CHANNEL, ctx.getString(R.string.app_name), android.app.NotificationManager.IMPORTANCE_LOW)) else Unit
}
