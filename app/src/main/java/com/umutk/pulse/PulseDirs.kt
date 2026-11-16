package com.umutk.pulse

import android.os.Environment
import java.io.File

/**
 * Everything Pulse puts in the shared Downloads folder lives under one folder, Download/Pulse: Share (what was received), AI (the models), Backup,
 * Crash (the crash and freeze logs), Notepad. An older folder of the same use is moved there once.
 */
object PulseDirs {
    fun root(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Pulse")
    fun sub(name: String): File = File(root(), name)
    private fun downloads(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** Moves [old] to [new] when [new] is not there yet (a folder with what the person already has stays theirs). */
    fun migrate(old: File, new: File) {
        runCatching { if (old.isDirectory && !new.exists()) { new.parentFile?.mkdirs(); old.renameTo(new) } }
    }

    /** The folder of what other devices sent (LocalSend and Relay both): Download/Pulse/Share. */
    fun share(): File {
        migrate(File(downloads(), "LocalSend"), sub("Share"))
        migrate(sub("Relay"), sub("Share"))
        return sub("Share")
    }

    /** The AI models: Download/Pulse/AI (the old Download/PulseAI is moved there). */
    fun ai(): File {
        migrate(File(downloads(), "PulseAI"), sub("AI"))
        return sub("AI")
    }
}
