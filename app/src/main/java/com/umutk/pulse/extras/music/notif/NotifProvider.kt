package com.umutk.pulse.extras.music.notif

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicSettings

/** Default Media3 notification (title, artist, cover, progress, lock screen) with the buttons the user picked, in the user's order. */
class NotifProvider(private val ctx: Context) : DefaultMediaNotificationProvider(ctx) {
    init { setSmallIcon(R.drawable.ic_notif_music) }

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
