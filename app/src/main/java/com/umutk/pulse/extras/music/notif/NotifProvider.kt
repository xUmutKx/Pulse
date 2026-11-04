package com.umutk.pulse.extras.music.notif

import android.app.Notification
import android.content.Context
import android.content.Intent
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
/** A Winamp card button: an intent for the playback service, which runs the command (see MusicPlaybackService). */
internal fun winampAction(ctx: Context, cmd: String): android.app.PendingIntent =
    android.app.PendingIntent.getService(ctx, cmd.hashCode(),
        Intent(ctx, com.umutk.pulse.extras.music.MusicPlaybackService::class.java).setAction("pulse.winamp.$cmd"),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)

class NotifProvider(private val ctx: Context) : MediaNotification.Provider {
    private val base = Buttons(ctx).apply { setSmallIcon(R.drawable.ic_notif_music) }

    override fun createNotification(
        session: MediaSession, customLayout: ImmutableList<CommandButton>, actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val m = base.createNotification(session, customLayout, actionFactory, callback)
        // the Winamp look: its own window with pixel buttons instead of the standard media card
        val hub = ctx.getSharedPreferences("music_hub", Context.MODE_PRIVATE)
        val look = hub.getString("look", "")
        if (look == "WINAMP") return winampCard(session, m)
        if (look == "CASSETTE" || look == "IPOD" || look == "TURNTABLE") return skinCard(session, m, look, hub.getString("extra_theme", "")?.substringAfter(':', "").orEmpty())
        val line = LiveLyrics.notifLine ?: return m
        if (android.os.Build.VERSION.SDK_INT < 24) return m
        val b = Notification.Builder.recoverBuilder(ctx, m.notification).setContentText(line)
        // the song title is underlined when that is the emphasis chosen, so it cannot be taken for the lyric line
        m.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.let { b.setContentTitle(LiveLyrics.titled(ctx, it)) }
        return MediaNotification(m.notificationId, b.build())
    }

    /** The cassette, turntable and iPod looks: a picture of the player with the song on it and three keys in the same style. */
    private fun skinCard(session: MediaSession, m: MediaNotification, look: String, variant: String): MediaNotification {
        val p = session.player
        val rv = android.widget.RemoteViews(ctx.packageName, R.layout.notif_skin)
        val dur = p.duration.takeIf { it > 0 } ?: 0L
        val progress = if (dur > 0) (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
        // a notification cannot spin by itself: the reels and the record are turned by the song's position each time the card is refreshed
        val angle = (p.currentPosition / 25L % 360L).toFloat()
        rv.setImageViewBitmap(R.id.ns_art, NotifSkinArt.art(look, variant, p.mediaMetadata.title?.toString().orEmpty(), p.mediaMetadata.artist?.toString().orEmpty(), angle, progress))
        rv.setImageViewBitmap(R.id.ns_prev, NotifSkinArt.button(look, variant, "prev"))
        rv.setImageViewBitmap(R.id.ns_play, NotifSkinArt.button(look, variant, if (p.isPlaying) "pause" else "play"))
        rv.setImageViewBitmap(R.id.ns_next, NotifSkinArt.button(look, variant, "next"))
        rv.setOnClickPendingIntent(R.id.ns_prev, winampAction(ctx, "prev"))
        rv.setOnClickPendingIntent(R.id.ns_play, winampAction(ctx, "play"))
        rv.setOnClickPendingIntent(R.id.ns_next, winampAction(ctx, "next"))
        val b = Notification.Builder.recoverBuilder(ctx, m.notification).setStyle(null).setCustomContentView(rv).setCustomBigContentView(rv)
        return MediaNotification(m.notificationId, b.build())
    }

    private fun winampCard(session: MediaSession, m: MediaNotification): MediaNotification {
        val p = session.player
        val rv = android.widget.RemoteViews(ctx.packageName, R.layout.notif_winamp)
        rv.setTextViewText(R.id.nw_title, p.mediaMetadata.title?.toString().orEmpty())
        rv.setTextViewText(R.id.nw_artist, p.mediaMetadata.artist?.toString().orEmpty())
        rv.setImageViewResource(R.id.nw_play, if (p.isPlaying) R.drawable.ic_winamp_pause else R.drawable.ic_winamp_play)
        rv.setOnClickPendingIntent(R.id.nw_prev, winampAction(ctx, "prev"))
        rv.setOnClickPendingIntent(R.id.nw_play, winampAction(ctx, "play"))
        rv.setOnClickPendingIntent(R.id.nw_next, winampAction(ctx, "next"))
        val b = Notification.Builder.recoverBuilder(ctx, m.notification).setStyle(null)
            .setCustomContentView(rv).setCustomBigContentView(rv)
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
            // the notification has room for MAX buttons; when more are chosen the Close button stays and the last others go
            val chosen = MusicSettings.notificationButtonIds(ctx).mapNotNull { NotifBtn.of(it) }.distinct().let { l ->
                if (l.size > NotifBtn.MAX && NotifBtn.CLOSE in l) l.filter { it != NotifBtn.CLOSE }.take(NotifBtn.MAX - 1) + NotifBtn.CLOSE else l
            }
            val out = chosen.mapNotNull { b ->
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
