package com.umutk.pulse.extras.lucid.data

import kotlinx.coroutines.flow.Flow

/**
 * The alarm table. Lucid keeps it in Room; Pulse keeps the same rows as JSON in one private file (the same encoding as Lucid's own backup), so no database
 * library is needed. Every method of Lucid's DAO is here with the same name and meaning, so the code above it is unchanged.
 */
class AlarmDao internal constructor(private val db: AppDatabase) {
    private fun live(): List<AlarmEntity> = db.alarms.value.filter { it.deletedAt == 0L }
    private fun ordered(l: List<AlarmEntity>) = l.sortedWith(compareBy({ it.sortOrder }, { it.hour }, { it.minute }))

    fun getAllAlarms(): Flow<List<AlarmEntity>> = db.alarmFlow { ordered(it.filter { a -> a.deletedAt == 0L }) }
    fun getLiveAlarmsByEnabled(enabled: Boolean?): Flow<List<AlarmEntity>> = db.alarmFlow { l -> ordered(l.filter { it.deletedAt == 0L && (enabled == null || it.isEnabled == enabled) }) }
    fun getActiveAlarmsSync(): List<AlarmEntity> = db.alarms.value.filter { it.isEnabled && it.deletedAt == 0L }
    fun getAlarmById(id: Int): AlarmEntity? = db.alarms.value.firstOrNull { it.id == id }
    fun getDistinctGroups(): Flow<List<String>> = db.alarmFlowOf { l -> l.filter { it.deletedAt == 0L && it.groupName != "" }.map { it.groupName }.distinct().sorted() }
    fun renameGroup(oldName: String, newName: String) = db.editAlarms { l -> l.map { if (it.deletedAt == 0L && it.groupName == oldName) it.copy(groupName = newName) else it } }
    fun idsInGroup(groupName: String): List<Int> = live().filter { it.groupName == groupName }.map { it.id }
    fun trashGroup(groupName: String, timestamp: Long) = db.editAlarms { l -> l.map { if (it.deletedAt == 0L && it.groupName == groupName) it.copy(deletedAt = timestamp, trashedGroup = groupName) else it } }
    fun trashGroupsTagged(groupNames: List<String>, timestamp: Long) = db.editAlarms { l -> l.map { if (it.deletedAt == 0L && it.groupName in groupNames) it.copy(deletedAt = timestamp, trashedGroup = it.groupName) else it } }
    fun idsInGroups(groupNames: List<String>): List<Int> = live().filter { it.groupName in groupNames }.map { it.id }
    fun trashIndividualExceptGroups(ids: List<Int>, excludedGroups: List<String>, timestamp: Long) =
        db.editAlarms { l -> l.map { if (it.deletedAt == 0L && it.id in ids && it.groupName !in excludedGroups) it.copy(deletedAt = timestamp, trashedGroup = "") else it } }
    fun getTrashedAlarms(): Flow<List<AlarmEntity>> = db.alarmFlowOf { l -> l.filter { it.deletedAt > 0L }.sortedByDescending { it.deletedAt } }
    fun getExpiredTrashedAlarms(threshold: Long): List<AlarmEntity> = db.alarms.value.filter { it.deletedAt > 0L && it.deletedAt < threshold }
    fun moveToTrash(ids: List<Int>, timestamp: Long) = db.editAlarms { l -> l.map { if (it.id in ids) it.copy(deletedAt = timestamp, trashedGroup = "") else it } }
    fun restoreFromTrash(ids: List<Int>) = db.editAlarms { l -> l.map { if (it.id in ids) it.copy(deletedAt = 0L, trashedGroup = "") else it } }
    fun restoreTrashedGroup(groupName: String) = db.editAlarms { l -> l.map { if (it.deletedAt > 0L && it.trashedGroup == groupName) it.copy(deletedAt = 0L, trashedGroup = "") else it } }
    fun trashedGroupIds(groupName: String): List<Int> = db.alarms.value.filter { it.deletedAt > 0L && it.trashedGroup == groupName }.map { it.id }
    fun deleteTrashedGroupPermanently(groupName: String) = db.editAlarms { l -> l.filterNot { it.deletedAt > 0L && it.trashedGroup == groupName } }
    fun emptyTrash() = db.editAlarms { l -> l.filterNot { it.deletedAt > 0L } }
    fun assignGroup(ids: List<Int>, groupName: String) = db.editAlarms { l -> l.map { if (it.id in ids) it.copy(groupName = groupName) else it } }
    fun setEnabled(ids: List<Int>, enabled: Boolean) = db.editAlarms { l -> l.map { if (it.id in ids) it.copy(isEnabled = enabled) else it } }
    fun updateSortOrder(id: Int, order: Int) = db.editAlarms { l -> l.map { if (it.id == id) it.copy(sortOrder = order) else it } }
    fun maxSortOrder(): Int? = live().maxOfOrNull { it.sortOrder }

    /** Adds the alarm (a new id when it has none) or replaces the one with its id; returns the id. */
    fun insert(alarm: AlarmEntity): Long {
        var id = alarm.id
        db.editAlarms { l ->
            if (id == 0) id = (l.maxOfOrNull { it.id } ?: 0) + 1
            l.filterNot { it.id == id } + alarm.copy(id = id)
        }
        return id.toLong()
    }

    fun update(alarm: AlarmEntity) = db.editAlarms { l -> l.map { if (it.id == alarm.id) alarm else it } }
    fun delete(alarm: AlarmEntity) = db.editAlarms { l -> l.filterNot { it.id == alarm.id } }
    fun deleteByIds(ids: List<Int>) = db.editAlarms { l -> l.filterNot { it.id in ids } }
    fun getAllAlarmsForBackup(): List<AlarmEntity> = ordered(db.alarms.value)
    fun deleteAllAlarms() = db.editAlarms { emptyList() }
}
