package com.umutk.pulse.extras.lucid.data

import kotlinx.coroutines.flow.Flow

/** The trashed world-clock cities, kept like the alarms (see [AlarmDao]). */
class ZoneDao internal constructor(private val db: AppDatabase) {
    fun getTrashedZones(): Flow<List<TrashedZone>> = db.zoneFlow { l -> l.sortedByDescending { it.deletedAt } }
    fun getExpiredTrashedZones(threshold: Long): List<TrashedZone> = db.zones.value.filter { it.deletedAt < threshold }
    fun insertAll(zones: List<TrashedZone>) = db.editZones { l -> l.filterNot { z -> zones.any { it.zoneId == z.zoneId } } + zones }
    fun deleteByIds(ids: List<String>) = db.editZones { l -> l.filterNot { it.zoneId in ids } }
    fun emptyTrash() = db.editZones { emptyList() }
    fun getAllTrashedZonesForBackup(): List<TrashedZone> = db.zones.value.sortedByDescending { it.deletedAt }
    fun deleteAllTrashedZones() = db.editZones { emptyList() }
}
