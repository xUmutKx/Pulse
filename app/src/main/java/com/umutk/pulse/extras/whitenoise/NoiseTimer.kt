package com.umutk.pulse.extras.whitenoise

import com.umutk.pulse.extras.ambient.AmbientMixer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Sleep timer: after [start] minutes the master volume fades to zero over fadeSec, then everything stops and the volume returns. */
object NoiseTimer {
    val minutes = MutableStateFlow(0)
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(m: Int, fadeSec: Int) {
        job?.cancel()
        minutes.value = m
        if (m <= 0) return
        job = scope.launch {
            val base = AmbientMixer.master.value
            var fading = false
            try {
                delay((m * 60_000L - fadeSec * 1000L).coerceAtLeast(0))
                fading = true
                val steps = 30
                for (i in 1..steps) { AmbientMixer.setMaster(base * (steps - i) / steps); delay(fadeSec * 1000L / steps) }
                AmbientMixer.stopAll()
                delay(500)
                AmbientMixer.setMaster(base)
                minutes.value = 0
            } catch (e: CancellationException) {
                if (fading) AmbientMixer.setMaster(base)
                throw e
            }
        }
    }
}
