package com.umutk.pulse.extras.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Open as Music" from a video of YTXTube (which runs in its own process): the song is played in Pulse's music player, as one of YouTube Music's (its audio stream, its
 * own queue, notification and look). The video's id, title, channel and picture come in the broadcast.
 */
class OpenAsMusicReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val hit = OnlineMusic.Hit(i.getStringExtra("title").orEmpty(), i.getStringExtra("channel").orEmpty().replace(" - Topic", "", true).trim(), i.getStringExtra("thumb"), "yt", id)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { OnlineMusic.playHere(ctx.applicationContext, listOf(hit), 0) }
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.umutk.pulse.OPEN_AS_MUSIC"
    }
}
