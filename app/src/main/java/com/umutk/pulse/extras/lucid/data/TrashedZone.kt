package com.umutk.pulse.extras.lucid.data

data class TrashedZone(
    val zoneId: String,

    val deletedAt: Long
) {
    companion object {
        const val RECYCLE_BIN_RETENTION_MILLIS = 3L * 24L * 60L * 60L * 1000L
    }
}
