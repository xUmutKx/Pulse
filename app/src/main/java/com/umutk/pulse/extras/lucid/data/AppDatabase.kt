package com.umutk.pulse.extras.lucid.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lucid's storage without Room: the alarms and the trashed cities are kept in memory and written to one JSON file ("lucid_alarms.json") after every change.
 * The encoding is the one of Lucid's own backup ([com.umutk.pulse.extras.lucid.BackupManager]), so a backup file and this file read the same.
 */
class AppDatabase private constructor(private val file: File) {
    internal val alarms = MutableStateFlow<List<AlarmEntity>>(emptyList())
    internal val zones = MutableStateFlow<List<TrashedZone>>(emptyList())
    private val lock = Any()

    init {
        runCatching {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                alarms.value = com.umutk.pulse.extras.lucid.BackupManager.parseAlarms(o.optJSONArray("alarms") ?: JSONArray())
                zones.value = com.umutk.pulse.extras.lucid.BackupManager.parseZones(o.optJSONArray("zones") ?: JSONArray())
            }
        }
    }

    private fun write() {
        runCatching {
            val o = JSONObject()
            o.put("alarms", JSONArray().also { a -> alarms.value.forEach { a.put(com.umutk.pulse.extras.lucid.BackupManager.encodeAlarm(it)) } })
            o.put("zones", JSONArray().also { a -> zones.value.forEach { a.put(com.umutk.pulse.extras.lucid.BackupManager.encodeZone(it)) } })
            val tmp = File(file.path + ".tmp"); tmp.writeText(o.toString()); tmp.renameTo(file)
        }
    }

    internal fun editAlarms(f: (List<AlarmEntity>) -> List<AlarmEntity>) { synchronized(lock) { alarms.value = f(alarms.value); write() } }
    internal fun editZones(f: (List<TrashedZone>) -> List<TrashedZone>) { synchronized(lock) { zones.value = f(zones.value); write() } }
    internal fun alarmFlow(f: (List<AlarmEntity>) -> List<AlarmEntity>): Flow<List<AlarmEntity>> = alarms.map(f)
    internal fun <T> alarmFlowOf(f: (List<AlarmEntity>) -> List<T>): Flow<List<T>> = alarms.map(f)
    internal fun zoneFlow(f: (List<TrashedZone>) -> List<TrashedZone>): Flow<List<TrashedZone>> = zones.map(f)

    fun alarmDao() = AlarmDao(this)
    fun zoneDao() = ZoneDao(this)

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: AppDatabase(File(context.applicationContext.filesDir, "lucid_alarms.json")).also { INSTANCE = it }
        }
    }
}
