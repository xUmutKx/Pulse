package com.umutk.pulse.extras.athenaeum

import android.content.Context
import java.io.File

/** Athenaeum's shared state: the Rust core and the user's edits. It starts once, the first time the app (or its widget) needs it. */
object TarotApp {
    lateinit var core: TarotCore
        private set
    lateinit var overrides: UserOverrides

    @Synchronized
    fun init(context: Context) {
        if (::core.isInitialized) return
        val app = context.applicationContext
        val dbFile = File(app.filesDir, "tarot.db").absolutePath
        core = TarotCore(app).apply { init(dbFile) }
        overrides = UserOverrides(app)
    }
}
