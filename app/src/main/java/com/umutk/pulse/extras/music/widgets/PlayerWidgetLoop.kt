package com.umutk.pulse.extras.music.widgets

import android.content.Context
import com.umutk.pulse.extras.music.MusicPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps a bitmap player widget live: redraws on every playback state change, and every [everyMs] while playing
 * (the engine publishes state on events, not on each position tick). One loop per provider, process-wide.
 */
internal class LiveLoop(private val everyMs: Long, private val redraw: (Context) -> Unit) {
    private var job: Job? = null

    fun start(c: Context) {
        if (job?.isActive == true) return
        val app = c.applicationContext
        job = CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            // a new state cancels the playing loop and starts a fresh one, so a pause stops the redraws
            MusicPlayerEngine.get(app).state.collectLatest { st ->
                redraw(app)
                while (st.isPlaying) {
                    delay(everyMs)
                    redraw(app)
                }
            }
        }
    }
}
