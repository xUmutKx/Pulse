package com.umutk.pulse.extras.lucid

import android.content.Context
import com.umutk.pulse.extras.lucid.data.AppDatabase
import com.umutk.pulse.extras.lucid.repository.AlarmRepository
import com.umutk.pulse.extras.lucid.repository.WorldClockRepository

/** What Lucid's Application class held (the database and the two repositories), as one object, since Pulse has its own Application. */
class AlarmApplication private constructor(context: Context) {
    val database by lazy { AppDatabase.getDatabase(context) }
    val repository by lazy { AlarmRepository(database.alarmDao()) }
    val worldClockRepository by lazy { WorldClockRepository(database.zoneDao()) }

    companion object {
        @Volatile private var INSTANCE: AlarmApplication? = null
        fun of(context: Context): AlarmApplication = INSTANCE ?: synchronized(this) { INSTANCE ?: AlarmApplication(context.applicationContext).also { INSTANCE = it } }
    }
}
