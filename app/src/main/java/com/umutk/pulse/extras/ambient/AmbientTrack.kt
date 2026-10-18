package com.umutk.pulse.extras.ambient

/** One ambient layer. [level] is 0..100; [path] points at a file in the downloaded module dir. */
data class AmbientTrack(
    val id: String,
    val path: String,
    val level: Int = 100,
    val enabled: Boolean = false,
)
